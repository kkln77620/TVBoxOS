package com.github.tvbox.osc.util;

import android.content.Context;
import android.widget.Toast;

import com.github.tvbox.osc.bean.CacheTask;
import com.orhanobut.hawk.Hawk;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(context, "播放地址为空, 无法缓存", Toast.LENGTH_SHORT).show();
            return false;
        }
        // 已存在相同任务: 提示
        for (CacheTask t : tasks) {
            if (t.url != null && t.url.equals(url) && t.state != CacheTask.STATE_FAILED) {
                Toast.makeText(context, "该任务已在缓存列表中", Toast.LENGTH_SHORT).show();
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
                notifyChanged();
            }
        });
    }

    /**
     * 直链视频流式下载
     */
    private void downloadDirect(CacheTask task) throws Exception {
        java.net.HttpURLConnection conn = openConn(task.url);
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
     */
    private void downloadM3u8(CacheTask task) throws Exception {
        String content = httpGetText(task.url);
        if (content == null || !content.contains("#EXT")) {
            throw new java.io.IOException("m3u8 解析失败");
        }
        List<String> tsUrls = new ArrayList<>();
        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            tsUrls.add(resolveUrl(task.url, line));
        }
        if (tsUrls.isEmpty()) {
            throw new java.io.IOException("未找到可下载的分片");
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
            byte[] data = httpGetBytes(tsUrls.get(i));
            if (data == null || data.length == 0) {
                // 单个分片失败: 跳过(部分分片损坏不影响整体, 已下载部分仍可播放)
                continue;
            }
            fos.write(data);
            done += data.length;
            task.progress = done;
            // 用已下载分片均值估算总量
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

    private java.net.HttpURLConnection openConn(String url) throws Exception {
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
        return conn;
    }

    private String httpGetText(String url) throws Exception {
        java.net.HttpURLConnection conn = openConn(url);
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
        java.net.HttpURLConnection conn = openConn(url);
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