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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 缓存下载管理: 单例, 任务列表持久化, 支持直链视频下载(mp4/mkv等)
 */
public class DownloadManager {

    private static final String KEY_TASKS = "cache_task_list";
    private static volatile DownloadManager instance;

    // 双任务并发下载(避免单线程排队导致大量任务一直等待)
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
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
            for (CacheTask t : saved) {
                // 重启后未完成的任务没有线程在下载, 标记失败避免永久卡在"下载中", 且允许重新入队
                if (t.state == CacheTask.STATE_WAIT || t.state == CacheTask.STATE_DOWNLOADING) {
                    t.state = CacheTask.STATE_FAILED;
                    t.errorMsg = "下载已中断, 请重新缓存";
                    // 清理半成品文件, 避免重启残留大文件占用空间(下载不支持断点续传, 重试会重新下载)
                    try {
                        File f = new File(t.filePath);
                        if (f.exists()) f.delete();
                    } catch (Throwable ignored) {
                    }
                } else if (t.state == CacheTask.STATE_PAUSED) {
                    // 已暂停任务: 保持暂停状态, 文件已清理
                    try {
                        File f = new File(t.filePath);
                        if (f.exists()) f.delete();
                    } catch (Throwable ignored) {
                    }
                }
                tasks.add(t);
            }
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
        return addTask(context, name, url, headers, silent, null, 0);
    }

    /**
     * 添加下载任务(全参数)
     *
     * @param pic            视频缩略图(缓存管理页分组显示)
     * @param bandwidthPref  码率偏好(0=默认最小码率; >0 选不低于该码率的子流, 用于画质选择)
     */
    public boolean addTask(Context context, String name, String url, Map<String, String> headers, boolean silent, String pic, long bandwidthPref) {
        // 清理 TVBox 三合一地址: 真实URL@播放方式@名称@集数, 取@前的http地址
        url = normalizeUrl(url);
        if (url == null || url.trim().isEmpty()) {
            if (!silent) Toast.makeText(context, "播放地址为空, 无法缓存", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            if (!silent) Toast.makeText(context, "该地址需播放器解析, 播放本集后会自动缓存", Toast.LENGTH_SHORT).show();
            return false;
        }
        // 清理同 URL 的历史失败任务, 避免列表堆积(失败后可重新入队); 同时删除其残留文件
        for (int i = tasks.size() - 1; i >= 0; i--) {
            CacheTask t = tasks.get(i);
            if (t.url != null && t.url.equals(url) && t.state == CacheTask.STATE_FAILED) {
                try {
                    File f = new File(t.filePath);
                    if (f.exists()) f.delete();
                } catch (Throwable ignored) {
                }
                tasks.remove(i);
            }
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
        task.pic = pic;
        task.bandwidthPref = bandwidthPref;
        tasks.add(0, task);
        notifyChanged();
        FloatLogManager.getInstance().append("缓存:入队 " + name);
        startDownload(context, task);
        return true;
    }

    /**
     * 是否存在相同地址的未完成任务(用于详情页重复点击检测)
     */
    public boolean isTaskExist(String url) {
        String n = normalizeUrl(url);
        if (n == null) return false;
        for (CacheTask t : tasks) {
            if (t.state == CacheTask.STATE_DONE || t.state == CacheTask.STATE_FAILED) continue;
            String tu = normalizeUrl(t.url);
            if (tu != null && tu.equals(n)) return true;
        }
        return false;
    }

    /**
     * 重试失败任务: 重置状态/进度, 删除半成品文件, 重新入队下载
     */
    public void retryTask(Context context, CacheTask task) {
        if (task == null) return;
        if (task.state == CacheTask.STATE_DOWNLOADING || task.state == CacheTask.STATE_DONE) return;
        task.state = CacheTask.STATE_WAIT;
        task.errorMsg = null;
        task.progress = 0;
        task.total = 0;
        try {
            java.io.File f = new java.io.File(task.filePath);
            if (f.exists()) f.delete();
        } catch (Throwable ignored) {
        }
        notifyChanged();
        FloatLogManager.getInstance().append("缓存:重试 " + task.name);
        startDownload(context, task);
    }

    /**
     * 暂停任务: 标记暂停, 下载循环检测到后中止并删除半成品(不支持断点, 继续将重新下载)
     */
    public void pauseTask(CacheTask task) {
        if (task == null) return;
        if (task.state != CacheTask.STATE_WAIT && task.state != CacheTask.STATE_DOWNLOADING) return;
        task.state = CacheTask.STATE_PAUSED;
        task.errorMsg = null;
        try {
            java.io.File f = new java.io.File(task.filePath);
            if (f.exists()) f.delete();
        } catch (Throwable ignored) {
        }
        FloatLogManager.getInstance().append("缓存:暂停 " + task.name);
        notifyChanged();
    }

    /**
     * 继续暂停的任务: 重新入队下载(从头开始)
     */
    public void resumeTask(Context context, CacheTask task) {
        if (task == null) return;
        if (task.state != CacheTask.STATE_PAUSED) return;
        task.state = CacheTask.STATE_WAIT;
        task.errorMsg = null;
        task.progress = 0;
        task.total = 0;
        FloatLogManager.getInstance().append("缓存:继续 " + task.name);
        startDownload(context, task);
        notifyChanged();
    }

    /**
     * 清理 TVBox 三合一地址(真实URL@播放方式@名称@集数等), 取@前的http地址
     */
    private static String normalizeUrl(String url) {
        if (url == null) return null;
        String u = url.trim();
        int at = u.indexOf('@');
        if (at > 0) {
            String head = u.substring(0, at);
            if (head.startsWith("http://") || head.startsWith("https://")) {
                return head;
            }
        }
        return u;
    }

    /**
     * 下载完成探测文件类型并修正扩展名: 0=无效(文本/HTML), 1=MP4(ftyp), 2=TS(0x47同步), 3=其他二进制
     */
    private int detectFileType(String path) {
        try {
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(path, "r");
            byte[] head = new byte[512];
            int n = raf.read(head);
            raf.close();
            if (n < 8) return 0;
            // 先检测 HTML/文本, 避免假阳性
            if (isHtmlResponse(head, n)) return 0;
            // MP4: ftyp box @ offset 4
            if (head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p') return 1;
            // MKV/WebM: 0x1A45DFA3
            if (head[0] == 0x1A && head[1] == 0x45 && head[2] == (byte)0xDF && head[3] == (byte)0xA3) return 1;
            // TS: 188字节对齐, 连续3个同步字节 0x47
            if (n >= 188 * 3 && head[0] == 0x47 && head[188] == 0x47 && head[376] == 0x47) return 2;
            // FLV: "FLV" 签名
            if (head[0] == 'F' && head[1] == 'L' && head[2] == 'V') return 1;
            // RIFF/AVI: "RIFF" 后跟 "AVI "
            if (head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'A' && head[9] == 'V' && head[10] == 'I' && head[11] == ' ') return 1;
            // 文本内容(HTML/JSON/错误页等)判定为无效
            boolean text = true;
            for (int i = 0; i < n; i++) {
                byte b = head[i];
                if (b == 0 || (b < 9) || (b > 13 && b < 32)) {
                    text = false;
                    break;
                }
            }
            if (text) return 0;
            return 3;
        } catch (Throwable th) {
            return 3;
        }
    }

    /**
     * 下载完成后校验内容并修正扩展名(TS流存为.mp4会导致无法播放)
     */
    private void verifyAndFix(CacheTask task) throws Exception {
        int type = detectFileType(task.filePath);
        String lower = task.filePath.toLowerCase();
        boolean isMp4 = lower.endsWith(".mp4");
        boolean isTs = lower.endsWith(".ts");
        if (type == 0) {
            throw new java.io.IOException("下载内容不是有效视频(可能为防盗链页面或地址已失效)");
        }
        if (type == 2 && isMp4) {
            // TS内容误存为mp4 -> 改扩展名为ts后播放器才能识别
            String newPath = task.filePath.substring(0, task.filePath.lastIndexOf('.')) + ".ts";
            java.io.File f = new java.io.File(task.filePath);
            java.io.File nf = new java.io.File(newPath);
            if (f.renameTo(nf)) {
                task.filePath = newPath;
            }
        } else if (type == 1 && isTs) {
            String newPath = task.filePath.substring(0, task.filePath.lastIndexOf('.')) + ".mp4";
            java.io.File f = new java.io.File(task.filePath);
            java.io.File nf = new java.io.File(newPath);
            if (f.renameTo(nf)) {
                task.filePath = newPath;
            }
        }
    }

    /**
     * 开始下载(后台线程)
     * - 直链(mp4/mkv等): 流式下载
     * - m3u8(HLS): 解析分片并逐个下载合并
     */
    private void startDownload(Context context, CacheTask task) {
        try {
            executor.execute(() -> {
                task.state = CacheTask.STATE_DOWNLOADING;
                task.errorMsg = null;
                FloatLogManager.getInstance().append("缓存:开始 " + task.name);
                notifyChanged();
                try {
                    String lower = task.url == null ? "" : task.url.toLowerCase();
                    // m3u8 判断: 含.m3u8扩展名/查询参数, 或本地净化代理(/m3u8)
                    if (lower.contains(".m3u8") || lower.contains("m3u8?") || lower.contains("/m3u8")) {
                        downloadM3u8(task);
                    } else {
                        downloadDirect(task);
                    }
                } catch (Throwable th) {
                    th.printStackTrace();
                    if (task.state == CacheTask.STATE_PAUSED) {
                        // 用户暂停: 保持暂停状态, 不标记失败
                        task.errorMsg = null;
                        notifyChanged();
                        return;
                    }
                    task.state = CacheTask.STATE_FAILED;
                    // 记录失败原因
                    String msg = th.getMessage();
                    if (msg == null || msg.trim().isEmpty()) {
                        msg = th.getClass().getSimpleName();
                    }
                    task.errorMsg = msg.length() > 120 ? msg.substring(0, 120) : msg;
                    // 附带任务地址便于定位(如 HTTP404 + url)
                    if (task.url != null) {
                        String urlInfo = " | " + task.url;
                        task.errorMsg = (task.errorMsg + urlInfo).length() > 200
                            ? (task.errorMsg + urlInfo).substring(0, 200) : task.errorMsg + urlInfo;
                }
                FloatLogManager.getInstance().append("缓存:失败 " + task.name + " | " + task.errorMsg);
                notifyChanged();
                }
            });
        } catch (Throwable th) {
            // 线程池提交失败(如已关闭): 避免任务永远停留在等待中
            task.state = CacheTask.STATE_FAILED;
            task.errorMsg = "任务提交失败: " + th.getMessage();
            notifyChanged();
        }
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
        // Content-Type 预检: 如果明确返回 text/html，几乎可以肯定是防盗链/错误页
        String contentType = conn.getContentType();
        if (contentType != null && contentType.toLowerCase().contains("text/html")) {
            conn.disconnect();
            throw new java.io.IOException("响应为HTML页面(非视频流), 可能地址已失效或需鉴权");
        }
        long total = conn.getContentLengthLong();
        java.io.InputStream is = conn.getInputStream();
        java.io.FileOutputStream fos = new java.io.FileOutputStream(task.filePath);
        byte[] buf = new byte[256 * 1024];
        int len;
        long done = 0;
        long lastNotify = 0;
        // 前 512 字节先缓冲, 用于检测是否为 HTML 错误页
        byte[] peekBuf = new byte[512];
        int peekLen = 0;
        boolean peekChecked = false;
        // 停滞检测: 每120秒检查窗口增量, 下载量<1MB视为停滞(极慢但不断流), 自动中止释放线程
        long checkTime = System.currentTimeMillis();
        long checkDone = 0;
        while ((len = is.read(buf)) > 0) {
            // 暂停检测: 用户暂停时中止下载
            if (task.state == CacheTask.STATE_PAUSED) {
                throw new java.io.IOException("已暂停");
            }
            // 首块数据检测是否为 HTML 错误页
            if (!peekChecked) {
                int copy = Math.min(len, peekBuf.length - peekLen);
                System.arraycopy(buf, 0, peekBuf, peekLen, copy);
                peekLen += copy;
                if (peekLen >= peekBuf.length || len >= buf.length) {
                    peekChecked = true;
                    if (isHtmlResponse(peekBuf, peekLen)) {
                        fos.close();
                        is.close();
                        conn.disconnect();
                        try { new java.io.File(task.filePath).delete(); } catch (Throwable ig) {}
                        throw new java.io.IOException("响应为HTML页面(非视频流), 可能地址已失效或需鉴权");
                    }
                }
            }
            fos.write(buf, 0, len);
            done += len;
            task.progress = done;
            task.total = total;
            long now = System.currentTimeMillis();
            if (now - checkTime >= 120000) {
                long gained = done - checkDone;
                if (gained < 1024 * 1024) {
                    throw new java.io.IOException("下载停滞(120秒仅下载" + (gained / 1024) + "KB), 已中止释放线程");
                }
                checkTime = now;
                checkDone = done;
            }
            if (now - lastNotify > 400) {
                lastNotify = now;
                notifyChanged();
            }
        }
        fos.flush();
        fos.close();
        is.close();
        conn.disconnect();
        // 校验内容有效性并修正扩展名(TS流误存为mp4会无法播放)
        verifyAndFix(task);
        task.state = CacheTask.STATE_DONE;
        FloatLogManager.getInstance().append("缓存:完成 " + task.name + " " + formatSizeForLog(done));
        task.progress = done;
        task.total = total > 0 ? total : done;
        notifyChanged();
    }

    /** 检测字节数据是否为 HTML 响应(错误页/防盗链页面) */
    private static boolean isHtmlResponse(byte[] data, int len) {
        if (len < 6) return false;
        // 转小写比较
        String s = new String(data, 0, Math.min(len, 256), java.nio.charset.StandardCharsets.ISO_8859_1).toLowerCase();
        // HTML 特征
        if (s.contains("<html") || s.contains("<!doctype") || s.contains("<body") || s.contains("<head")) return true;
        // JSON 错误响应特征 (如 {"code":403})
        if (s.trim().startsWith("{") && (s.contains("\"code\"") || s.contains("\"error\"") || s.contains("\"msg\""))) return true;
        return false;
    }

    /**
     * m3u8(HLS) 下载: 解析分片 -> 逐个下载 -> 合并为单个视频文件
     * 支持 AES-128 加密分片解密(#EXT-X-KEY)
     */
    private void downloadM3u8(CacheTask task) throws Exception {
        String content = httpGetText(task.url, task.headers);
        // 严格校验: 净化代理可能返回空/非清单内容
        if (content == null || !content.startsWith("#EXTM3U")) {
            throw new java.io.IOException("m3u8 解析失败");
        }
        // master playlist(多码率): 解析所有子流, 优先选择 BANDWIDTH 在目标范围内的子流
        // 策略: 下限=0.5Mbps(避免极低画质), 上限=2.5Mbps(5分钟≈100MB), 选范围内最高码率保证画质
        // 范围外则选最小码率(省空间), 失败则回退尝试其他
        if (content.contains("#EXT-X-STREAM-INF")) {
            long BITRATE_TARGET_MAX = 2500000; // 2.5Mbps, 5分钟约100MB
            long BITRATE_TARGET_MIN = 500000;  // 0.5Mbps, 避免极低画质
            // 收集子流: [带宽, 地址]; 无BANDWIDTH属性按原顺序排在后面
            java.util.List<String[]> children = new java.util.ArrayList<>();
            String lastInf = null;
            for (String line : content.split("\n")) {
                line = line.trim();
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    lastInf = line;
                    continue;
                }
                if (line.isEmpty() || line.startsWith("#")) {
                    lastInf = null;
                    continue;
                }
                long bw = -1;
                if (lastInf != null) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("BANDWIDTH=(\\d+)").matcher(lastInf);
                    if (m.find()) bw = Long.parseLong(m.group(1));
                }
                children.add(new String[]{String.valueOf(bw), line});
                lastInf = null;
            }
            // 排序: 无BANDWIDTH排最后, 其余按码率升序(最小码率优先, 省空间)
            children.sort((a, b) -> {
                long ba = Long.parseLong(a[0]), bb = Long.parseLong(b[0]);
                if (ba < 0 && bb < 0) return 0;
                if (ba < 0) return 1;
                if (bb < 0) return -1;
                return Long.compare(ba, bb);
            });
            // 尝试顺序: 默认最小码率; 有码率偏好时从"不低于偏好的最小码率"开始, 更高码率优先, 低码率兜底
            java.util.List<String[]> tryOrder = new java.util.ArrayList<>();
            if (task.bandwidthPref > 0) {
                int start = -1;
                for (int i = 0; i < children.size(); i++) {
                    long bw = Long.parseLong(children.get(i)[0]);
                    if (bw >= task.bandwidthPref) {
                        start = i;
                        break;
                    }
                }
                if (start < 0) start = children.size() - 1; // 全部低于偏好: 取最高码率(最接近)
                for (int i = start; i < children.size(); i++) tryOrder.add(children.get(i));
                for (int i = 0; i < start; i++) tryOrder.add(children.get(i));
            } else {
                tryOrder = children;
            }
            boolean childOk = false;
            for (String[] childInfo : tryOrder) {
                String child = resolveUrl(task.url, childInfo[1]);
                String childContent = httpGetText(child, task.headers);
                if (childContent != null && childContent.startsWith("#EXTM3U")) {
                    long bw = Long.parseLong(childInfo[0]);
                    FloatLogManager.getInstance().append("缓存:选择码率 " + (bw > 0 ? (bw / 1000) + "kbps" : "未知"));
                    content = childContent;
                    task.url = child;
                    childOk = true;
                    break;
                }
            }
            if (!childOk || !content.startsWith("#EXTM3U")) {
                throw new java.io.IOException("m3u8 子流解析失败");
            }
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
        // 合并输出文件: m3u8分片合并后为TS流, 使用.ts扩展名播放器才能识别
        String outPath = task.filePath;
        if (!outPath.toLowerCase().endsWith(".ts")) {
            outPath = outPath.substring(0, outPath.lastIndexOf('.')) + ".ts";
            task.filePath = outPath;
        }
        java.io.FileOutputStream fos = new java.io.FileOutputStream(outPath);
        long done = 0;
        long lastNotify = 0;
        long totalEstimate = 0; // 滚动估算总量
        int skipped = 0; // 失败跳过的分片数
        long doneCount = 0; // 已成功写入的分片数(用于估算总量, 并发下不能按序号i+1计算)
        // PTS连续性状态: pid -> 已修正的最后一个PTS / 当前累计偏移
        Map<Integer, Long> ptsLast = new HashMap<>();
        Map<Integer, Long> ptsOffset = new HashMap<>();
        // 停滞检测: 每120秒检查合并增量, 下载量<1MB视为停滞, 自动中止释放线程
        long checkTime = System.currentTimeMillis();
        long checkDone = 0;
        // 分片并发拉取(4路)后按序合并, 大幅提升下载速度
        int segThreads = Math.min(4, tsUrls.size());
        ExecutorService segPool = Executors.newFixedThreadPool(segThreads);
        try {
            List<Future<byte[]>> futures = new ArrayList<>(tsUrls.size());
            for (String tsUrl : tsUrls) {
                futures.add(segPool.submit(() -> {
                    // 分片重试: 最多3次, 间隔递增(网络抖动自愈)
                    byte[] d = null;
                    for (int attempt = 0; attempt < 3 && (d == null || d.length == 0); attempt++) {
                        try {
                            d = httpGetBytes(tsUrl, task.headers);
                        } catch (Throwable th) {
                            d = null;
                        }
                        if (d == null || d.length == 0) {
                            try {
                                Thread.sleep(300L * (attempt + 1));
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                    return d;
                }));
            for (int i = 0; i < futures.size(); i++) {
                byte[] data;
                try {
                    data = futures.get(i).get(120, TimeUnit.SECONDS);
                } catch (Throwable th) {
                    data = null;
                }
                // 严格校验: 空数据 / HTML错误页 / 非TS分片 → 跳过
                if (data == null || data.length == 0) {
                    skipped++;
                    continue;
                }
                if (isHtmlResponse(data, data.length)) {
                    skipped++;
                    continue;
                }
                // TS 同步字节校验(仅对未加密分片): 首字节0x47 + 188对齐; 长度不足时只查已有偏移(避免越界)
                if (aesKey == null && data.length >= 188) {
                    boolean tsOk;
                    if (data.length >= 376) {
                        tsOk = data[0] == 0x47 && data[188] == 0x47 && data[376] == 0x47;
                    } else {
                        tsOk = data[0] == 0x47 && data[188] == 0x47;
                    }
                    if (!tsOk) {
                        skipped++;
                        continue;
                    }
                }
                if (aesKey != null) {
                    data = aesDecrypt(data, aesKey, aesIV);
                    if (data == null) {
                        throw new java.io.IOException("AES-128 解密失败(第" + (i + 1) + "分片)");
                    }
                }
                // 分片末尾对齐: 若分片不是188字节整倍数(下载截断/源站非标准), 截掉末尾余数
                // 否则拼接处包结构损坏, 所有播放器(包括MT/系统)都会卡在分片边界(约10秒周期)
                int alignOff = data.length % 188;
                if (alignOff != 0) {
                    int newLen = data.length - alignOff;
                    if (newLen > 0) {
                        byte[] aligned = new byte[newLen];
                        System.arraycopy(data, 0, aligned, 0, newLen);
                        data = aligned;
                    }
                }
                // PTS/DTS/PCR 连续性修复: 各分片独立时间戳, 合并后回跳会导致播放器(ffmpeg/Exo)反复重同步/进度回退
                rewriteTsTimestamps(data, ptsLast, ptsOffset);
                // 暂停检测: 用户暂停时中止合并
                if (task.state == CacheTask.STATE_PAUSED) {
                    throw new java.io.IOException("已暂停");
                }
                fos.write(data);
                done += data.length;
                doneCount++;
                task.progress = done;
                // 滚动估算总量(随实际平均分片大小更新), 总量只增不减避免倒挂
                long avg = doneCount > 0 ? done / doneCount : 0;
                totalEstimate = avg * tsUrls.size();
                task.total = Math.max(totalEstimate, done);
                long now = System.currentTimeMillis();
                // 停滞检测: 120秒合并增量<1MB视为停滞, 自动中止释放线程
                if (now - checkTime >= 120000) {
                    long gained = done - checkDone;
                    if (gained < 1024 * 1024) {
                        throw new java.io.IOException("下载停滞(120秒仅下载" + (gained / 1024) + "KB), 已中止释放线程");
                    }
                    checkTime = now;
                    checkDone = done;
                }
                if (now - lastNotify > 400) {
                    lastNotify = now;
                    notifyChanged();
                }
            }
            }
        } finally {
            segPool.shutdownNow();
        }
        fos.flush();
        fos.close();
        // 校验合并结果(内容有效性与扩展名)
        verifyAndFix(task);
        task.state = CacheTask.STATE_DONE;
        FloatLogManager.getInstance().append("缓存:完成 " + task.name + " " + formatSizeForLog(done));
        task.progress = done;
        task.total = done;
        // 分片缺失提示(文件可能不完整)
        if (skipped > 0) {
            task.errorMsg = "有 " + skipped + " 个分片下载失败, 文件可能不完整";
        }
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

    /**
     * TS 分片时间戳连续性修复:
     * HLS 各分片时间戳独立(常从0或随机值开始), 直接拼接后 PTS/DTS 在分片边界回跳,
     * 导致 ffmpeg/IJK/Exo 检测到时间戳回退反复重同步, 表现为播放进度周期性回退/循环。
     * 本方法逐包重写 PES 的 PTS/DTS 与 PCR, 使合并后时间戳单调递增。
     * 90kHz 时基, 回跳阈值 0.5 秒(45000 ticks)。
     */
    private void rewriteTsTimestamps(byte[] data, Map<Integer, Long> ptsLast, Map<Integer, Long> ptsOffset) {
        if (data == null || data.length < 188) return;
        for (int off = 0; off + 188 <= data.length; off += 188) {
            if (data[off] != 0x47) continue;
            int pid = ((data[off + 1] & 0x1F) << 8) | (data[off + 2] & 0xFF);
            boolean isPes = (pid >= 0xC0 && pid <= 0xDF) || (pid >= 0xE0 && pid <= 0xEF) || pid == 0xBD;
            if (!isPes) continue;
            // 仅处理 PES 起始包
            if ((data[off + 1] & 0x40) == 0) continue;
            int payloadOff = off + 4;
            int afc = (data[off + 3] & 0x30) >> 4;
            if (afc == 2 || afc == 3) {
                int afLen = 1 + (data[off + 4] & 0xFF);
                // 重写 PCR(在 adaptation field 内, 同一偏移修正)
                if (afc == 3 && (data[off + 5] & 0x10) != 0 && payloadOff + 6 <= off + 188) {
                    long pcr = readPcr(data, off + 6);
                    Long lastPcr = ptsLast.get(pid * 1000);
                    if (lastPcr != null && pcr + 45000 < lastPcr) {
                        long delta = lastPcr - pcr + 9000;
                        writePcr(data, off + 6, pcr + delta);
                    } else if (lastPcr != null) {
                        // 正常递增, 仅更新
                    }
                    if (lastPcr == null) {
                        ptsLast.put(pid * 1000, pcr);
                    } else {
                        long np = readPcr(data, off + 6);
                        if (np > lastPcr) ptsLast.put(pid * 1000, np);
                    }
                }
                payloadOff += afLen;
            }
            if (payloadOff + 9 > off + 188) continue;
            // PES 头: 00 00 01
            if (data[payloadOff] != 0 || data[payloadOff + 1] != 0 || data[payloadOff + 2] != 1) continue;
            int flags = data[payloadOff + 7] & 0xFF;
            boolean hasPts = (flags & 0x80) != 0;
            if (!hasPts) continue;
            boolean hasDts = (flags & 0x40) != 0;
            int ptsOff = payloadOff + 9;
            if (ptsOff + 5 > off + 188) continue;
            long pts = readPts(data, ptsOff);
            Long last = ptsLast.get(pid);
            long offs = ptsOffset.containsKey(pid) ? ptsOffset.get(pid) : 0L;
            if (last != null && pts + offs + 45000 < last) {
                // 分片边界回跳: 提升偏移, 使修正后 PTS 紧接上一分片末尾
                offs += last - (pts + offs) + 9000;
                ptsOffset.put(pid, offs);
            }
            long newPts = pts + offs;
            if (newPts != pts) writePts(data, ptsOff, newPts);
            if (hasDts && ptsOff + 10 <= off + 188) {
                long dts = readPts(data, ptsOff + 5);
                long newDts = dts + offs;
                if (newDts != dts) writePts(data, ptsOff + 5, newDts);
            }
            ptsLast.put(pid, newPts);
        }
    }

    /** 读取 33 位 PTS/DTS */
    private long readPts(byte[] d, int off) {
        return (((long) (d[off] & 0x0E)) << 29)
                | (((long) d[off + 1] & 0xFF) << 22)
                | (((long) (d[off + 2] & 0xFE)) << 14)
                | (((long) d[off + 3] & 0xFF) << 7)
                | (((long) (d[off + 4] & 0xFE)) >> 1);
    }

    /** 写入 33 位 PTS/DTS */
    private void writePts(byte[] d, int off, long pts) {
        d[off] = (byte) (0x21 | ((pts >> 29) & 0x0E));
        d[off + 1] = (byte) (pts >> 22);
        d[off + 2] = (byte) (((pts >> 14) & 0xFE) | 0x01);
        d[off + 3] = (byte) (pts >> 7);
        d[off + 4] = (byte) (((pts << 1) & 0xFE) | 0x01);
    }

    /** 读取 PCR(33位 base + 9位 ext) */
    private long readPcr(byte[] d, int off) {
        return (((long) d[off] & 0xFF) << 25)
                | (((long) d[off + 1] & 0xFF) << 17)
                | (((long) d[off + 2] & 0xFF) << 9)
                | (((long) d[off + 3] & 0xFF) << 1)
                | (((long) d[off + 4] & 0x80) >> 7);
    }

    /** 写入 PCR */
    private void writePcr(byte[] d, int off, long pcr) {
        d[off] = (byte) (pcr >> 25);
        d[off + 1] = (byte) (pcr >> 17);
        d[off + 2] = (byte) (pcr >> 9);
        d[off + 3] = (byte) (pcr >> 1);
        d[off + 4] = (byte) (((pcr & 1) << 7) | 0x7E);
        d[off + 5] = 0x00;
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
        byte[] buf = new byte[128 * 1024];
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
            FloatLogManager.getInstance().append("缓存:删除 " + found.name);
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

    /**
     * 清空全部任务: 删除所有任务记录及其文件(正在下载的文件可能删除失败, 由下载结束逻辑兜底)
     */
    public void clearAll() {
        for (CacheTask t : tasks) {
            try {
                File f = new File(t.filePath);
                if (f.exists()) f.delete();
            } catch (Throwable ignored) {
            }
        }
        tasks.clear();
        FloatLogManager.getInstance().append("缓存:清空全部");
        notifyChanged();
    }

    /**
     * 当前缓存占用总字节(统计实际存在的文件)
     */
    public long getTotalSize() {
        long total = 0;
        for (CacheTask t : tasks) {
            try {
                File f = new File(t.filePath);
                if (f.exists()) total += f.length();
            } catch (Throwable ignored) {
            }
        }
        return total;
    }

    /** 日志用体积格式化(如 123.4MB) */
    private static String formatSizeForLog(long bytes) {
        if (bytes <= 0) return "0B";
        double mb = bytes / 1024.0 / 1024.0;
        if (mb < 1024) return String.format(java.util.Locale.getDefault(), "%.1fMB", mb);
        return String.format(java.util.Locale.getDefault(), "%.2fGB", mb / 1024.0);
    }
}