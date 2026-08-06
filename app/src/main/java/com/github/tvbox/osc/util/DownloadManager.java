package com.github.tvbox.osc.util;
import android.content.Context;
import android.widget.Toast;
import com.github.tvbox.osc.bean.CacheTask;
import com.orhanobut.hawk.Hawk;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 缓存下载管理: 单例, 任务列表持久化, 支持直链视频下载(mp4/mkv等)
 */
public class DownloadManager {

    private static final String KEY_TASKS = "cache_task_list";
    private static volatile DownloadManager instance;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<CacheTask> tasks = new ArrayList<>();

    public interface OnTaskChangeListener {
        void onChanged();
    }

    private OnTaskChangeListener listener;

    public static DownloadManager getInstance() {
        if (instance == null) {
            synchronized (DownloadManager.class) {
                if (instance == null) instance = new DownloadManager();
            }
        }
        return instance;
    }

    private DownloadManager() {
        load();
    }

    private void load() {
        List<CacheTask> saved = Hawk.get(KEY_TASKS, new ArrayList<CacheTask>());
        if (saved != null) {
            tasks.clear();
            tasks.addAll(saved);
        }
    }

    private void save() {
        Hawk.put(KEY_TASKS, new ArrayList<>(tasks));
    }

    public void setOnTaskChangeListener(OnTaskChangeListener l) {
        this.listener = l;
    }

    private void notifyChanged() {
        save();
        if (listener != null) {
            try {
                listener.onChanged();
            } catch (Throwable ignored) {
            }
        }
    }

    public List<CacheTask> getTasks() {
        return new ArrayList<>(tasks);
    }

    /**
     * 添加下载任务(直链视频)
     *
     * @return true=已加入缓存列表, false=未加入(重复/不支持/参数错误)
     */
    public boolean addTask(Context context, String name, String url) {
        return addTask(context, name, url, null, false);
    }

    /**
     * 添加下载任务(带播放参数 headers, 复用播放成功的鉴权)
     */
    public boolean addTask(Context context, String name, String url, Map<String, String> headers) {
        return addTask(context, name, url, headers, false);
    }

    /**
     * 添加下载任务
     *
     * @param silent true=静默入队(重复任务不弹Toast), 用于播放页自动缓存
     */
    public boolean addTask(Context context, String name, String url, Map<String, String> headers, boolean silent) {
        if (url == null || url.trim().isEmpty()) {
            if (!silent) Toast.makeText(context, "播放地址为空, 无法缓存", Toast.LENGTH_SHORT).show();
            return false;
        }
        // 仅支持 http/https 直链: 协议地址(如 noprotocol:/bili:// 等)需播放器解析, 无法直接下载
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            if (!silent) Toast.makeText(context, "该地址需播放器解析, 播放本集后会自动缓存", Toast.LENGTH_SHORT).show();
            return false;
        }
        // 已存在相同任务: 提示
        for (CacheTask t : tasks) {
            if (t.url != null && t.url.equals(url) && t.state != CacheTask.STATE_FAILED) {
                if (!silent) Toast.makeText(context, "该任务已在缓存列表中", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        String id = System.currentTimeMillis() + "_" + (int) (Math.random() * 10000);
        String lower = url.toLowerCase();
        String ext = ".mp4";
        if (lower.contains(".mkv")) ext = ".mkv";
        else if (lower.contains(".avi")) ext = ".avi";
        else if (lower.contains(".flv")) ext = ".flv";
        else if (lower.contains(".ts")) ext = ".ts";
        else if (lower.contains(".mov")) ext = ".mov";
        else if (lower.contains(".webm")) ext = ".webm";
        File dir = getCacheDir(context);
        if (!dir.exists()) dir.mkdirs();
        String safeName = name == null ? "视频" : name.replaceAll("[\\\\/:*?\"<>|]", "_");
        File out = new File(dir, safeName + ext);
        int n = 1;
        while (out.exists()) {
            out = new File(dir, safeName + "_" + (n++) + ext);
        }
        CacheTask task = new CacheTask(id, name == null ? "视频" : name, url, out.getAbsolutePath());
        if (headers != null && !headers.isEmpty()) {
            task.headers = new HashMap<>(headers);
        }
        tasks.add(0, task);
        notifyChanged();
        startDownload(context, task);
        return true;
    }

    /**
     * 开始下载(后台线程)
     * - 直链(mp4/mkv等): 流式下载
     * - m3u8(HLS): 解析分片并逐个下载合并
     */
    private void startDownload(Context context, CacheTask task) {
        executor.execute(() -> {
            task.state = CacheTask.STATE_DOWNLOADING;
            task.errorMsg = null;
            notifyChanged();
            try {
                String lower = task.url == null ? "" : task.url.toLowerCase();
                if (lower.contains(".m3u8") || lower.contains("m3u8?")) {
                    downloadM3u8(task);
                } else {
                    downloadDirect(task);
                }
            } catch (Throwable th) {
                th.printStackTrace();
                task.state = CacheTask.STATE_FAILED;
                // 记录失败原因
                String msg = th.getMessage();
                if (msg == null || msg.trim().isEmpty()) {
                    msg = th.getClass().getSimpleName();
                }
                task.errorMsg = msg.length() > 120 ? msg.substring(0, 120) : msg;
                notifyChanged();
            }
        });
    }

    /**
     * 直链视频流式下载
     */
    private void downloadDirect(CacheTask task) throws Exception {
        java.net.HttpURLConnection conn = openConn(task.url, task.headers);
        int code = conn.getResponseCode();
        if (code / 100 != 2) {
            conn.disconnect();
            throw new java.io.IOException("HTTP " + code);
        }
        long total = conn.getContentLengthLong();
        java.io.InputStream is = conn.getInputStream();
        java.io.FileOutputStream fos = new java.io.FileOutputStream(task.filePath);
        byte[] buf = new byte[64 * 1024];
        int len;
        long done = 0;
        long lastNotify = 0;
        while ((len = is.read(buf)) > 0) {
            fos.write(buf, 0, len);
            done += len;
            task.progress = done;
            task.total = total;
            long now = System.currentTimeMillis();
            if (now - lastNotify > 400) {
                lastNotify = now;
                notifyChanged();
            }
        }
        fos.flush();
        fos.close();
        is.close();
        conn.disconnect();
        task.state = CacheTask.STATE_DONE;
        task.progress = done;
        task.total = total > 0 ? total : done;
        notifyChanged();
    }

    /**
     * m3u8(HLS) 下载: 解析分片 -> 逐个下载 -> 合并为单个视频文件
     * 支持 AES-128 加密分片解密(#EXT-X-KEY)
     */
    private void downloadM3u8(CacheTask task) throws Exception {
        String content = httpGetText(task.url, task.headers);
        if (content == null || !content.contains("#EXT")) {
            throw new java.io.IOException("m3u8 解析失败");
        }
        List<String> tsUrls = new ArrayList<>();
        // AES-128 加密参数
        String keyUri = null;
        String keyIV = null;
        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.startsWith("#EXT-X-KEY:")) {
                String m = line.replace("#EXT-X-KEY:", "");
                String[] attrs = m.split(",");
                for (String attr : attrs) {
                    attr = attr.trim();
                    if (attr.startsWith("METHOD=")) {
                        // METHOD=AES-128 / NONE
                    } else if (attr.startsWith("URI=")) {
                        keyUri = attr.substring(4).replace("\"", "").trim();
                    } else if (attr.startsWith("IV=")) {
                        keyIV = attr.substring(3).trim();
                    }
                }
            } else if (line.isEmpty() || line.startsWith("#")) {
                continue;
            } else {
                tsUrls.add(resolveUrl(task.url, line));
            }
        }
        if (tsUrls.isEmpty()) {
            throw new java.io.IOException("未找到可下载的分片");
        }
        // 下载 AES 密钥
        byte[] aesKey = null;
        byte[] aesIV = null;
        if (keyUri != null && !keyUri.isEmpty()) {
            byte[] keyData = httpGetBytes(resolveUrl(task.url, keyUri), task.headers);
            if (keyData != null && keyData.length >= 16) {
                aesKey = new byte[16];
                System.arraycopy(keyData, 0, aesKey, 0, 16);
                if (keyIV != null && keyIV.startsWith("0x") && keyIV.length() >= 34) {
                    String hex = keyIV.substring(2);
                    aesIV = new byte[16];
                    for (int i = 0; i < 16; i++) {
                        aesIV[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
                    }
                }
            }
        }
        // 合并输出文件
        String outPath = task.filePath;
        if (!outPath.toLowerCase().endsWith(".mp4")) {
            outPath = outPath.substring(0, outPath.lastIndexOf('.')) + ".mp4";
            task.filePath = outPath;
        }
        java.io.FileOutputStream fos = new java.io.FileOutputStream(outPath);
        long done = 0;
        long lastNotify = 0;
        for (int i = 0; i < tsUrls.size(); i++) {
            byte[] data = httpGetBytes(tsUrls.get(i), task.headers);
            if (data == null || data.length == 0) {
                continue;
            }
            if (aesKey != null) {
                data = aesDecrypt(data, aesKey, aesIV);
                if (data == null) {
                    throw new java.io.IOException("AES-128 解密失败(第" + (i + 1) + "分片)");
                }
            }
            fos.write(data);
            done += data.length;
            task.progress = done;
            long avg = done / (i + 1);
            task.total = avg * tsUrls.size();
            long now = System.currentTimeMillis();
            if (now - lastNotify > 400) {
                lastNotify = now;
                notifyChanged();
            }
        }
        fos.flush();
        fos.close();
        task.state = CacheTask.STATE_DONE;
        task.progress = done;
        task.total = done;
        notifyChanged();
    }

    /**
     * AES-128-CBC 解密(标准 HLS 加密分片, PKCS7 填充)
     */
    private byte[] aesDecrypt(byte[] data, byte[] key, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            if (iv != null) {
                cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));
            } else {
                // 未指定 IV 时使用 0 向量
                cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(new byte[16]));
            }
            return cipher.doFinal(data);
        } catch (Throwable th) {
            return null;
        }
    }

    private java.net.HttpURLConnection openConn(String url) throws Exception {
        return openConn(url, null);
    }

    private java.net.HttpURLConnection openConn(String url, Map<String, String> headers) throws Exception {
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        // 防盗链: Referer 取 URL 主机
        try {
            String host = new java.net.URL(url).getHost();
            conn.setRequestProperty("Referer", "https://" + host + "/");
        } catch (Throwable ignored) {
        }
        // 透传播放参数(UA/Cookie/Referer等, 保证与播放一致)
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                String k = e.getKey().toLowerCase();
                if (k.equals("user-agent")) {
                    conn.setRequestProperty("User-Agent", e.getValue());
                } else if (k.equals("referer")) {
                    conn.setRequestProperty("Referer", e.getValue());
                } else if (k.equals("cookie")) {
                    conn.setRequestProperty("Cookie", e.getValue());
                } else {
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
        }
        return conn;
    }

    private String httpGetText(String url) throws Exception {
        return httpGetText(url, null);
    }

    private String httpGetText(String url, Map<String, String> headers) throws Exception {
        java.net.HttpURLConnection conn = openConn(url, headers);
        int code = conn.getResponseCode();
        if (code / 100 != 2) {
            conn.disconnect();
            throw new java.io.IOException("HTTP " + code);
        }
        java.io.InputStream is = conn.getInputStream();
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int len;
        while ((len = is.read(buf)) > 0) {
            bos.write(buf, 0, len);
        }
        is.close();
        conn.disconnect();
        return bos.toString("UTF-8");
    }

    private byte[] httpGetBytes(String url) throws Exception {
        return httpGetBytes(url, null);
    }

    private byte[] httpGetBytes(String url, Map<String, String> headers) throws Exception {
        java.net.HttpURLConnection conn = openConn(url, headers);
        int code = conn.getResponseCode();
        if (code / 100 != 2) {
            conn.disconnect();
            return null;
        }
        java.io.InputStream is = conn.getInputStream();
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[64 * 1024];
        int len;
        while ((len = is.read(buf)) > 0) {
            bos.write(buf, 0, len);
        }
        is.close();
        conn.disconnect();
        return bos.toByteArray();
    }

    /**
     * 相对地址 -> 绝对地址(基于 m3u8 URL)
     */
    private String resolveUrl(String base, String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) return path;
        try {
            return new java.net.URL(new java.net.URL(base), path).toString();
        } catch (Throwable th) {
            return path;
        }
    }

    /**
     * 删除任务: 取消下载并删除文件
     */
    public void removeTask(Context context, String id) {
        CacheTask found = null;
        for (CacheTask t : tasks) {
            if (t.id != null && t.id.equals(id)) {
                found = t;
                break;
            }
        }
        if (found != null) {
            if (found.state == CacheTask.STATE_DOWNLOADING || found.state == CacheTask.STATE_WAIT) {
                // 尝试取消(OkHttp取消不保证, 删除任务记录即可)
            }
            tasks.remove(found);
            try {
                File f = new File(found.filePath);
                if (f.exists()) f.delete();
            } catch (Throwable ignored) {
            }
            notifyChanged();
        }
    }

    /**
     * 缓存目录: 应用外部私有目录, 无需存储权限
     */
    public static File getCacheDir(Context context) {
        File dir = context.getExternalFilesDir("cache_videos");
        if (dir == null) dir = new File(context.getFilesDir(), "cache_videos");
        return dir;
    }
}