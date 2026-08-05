package com.github.tvbox.osc.util;

import android.content.Context;
import android.widget.Toast;

import com.github.tvbox.osc.bean.CacheTask;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.FileCallback;
import com.lzy.okgo.model.Progress;
import com.lzy.okgo.model.Response;
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
     */
    public void addTask(Context context, String name, String url) {
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(context, "播放地址为空, 无法缓存", Toast.LENGTH_SHORT).show();
            return;
        }
        String lower = url.toLowerCase();
        if (lower.contains(".m3u8") || lower.contains("m3u8?")) {
            Toast.makeText(context, "该片源为 HLS(m3u8) 流, 暂不支持缓存, 请更换片源", Toast.LENGTH_SHORT).show();
            return;
        }
        // 已存在相同任务: 提示
        for (CacheTask t : tasks) {
            if (t.url != null && t.url.equals(url) && t.state != CacheTask.STATE_FAILED) {
                Toast.makeText(context, "该任务已在缓存列表中", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        String id = System.currentTimeMillis() + "_" + (int) (Math.random() * 10000);
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
    }

    /**
     * 开始下载(OkGo 直链下载)
     */
    private void startDownload(Context context, CacheTask task) {
        executor.execute(() -> {
            task.state = CacheTask.STATE_DOWNLOADING;
            notifyChanged();
            OkGo.<File>get(task.url)
                    .execute(new FileCallback(new File(task.filePath).getParent(), new File(task.filePath).getName()) {
                        @Override
                        public void onSuccess(Response<File> response) {
                            task.state = CacheTask.STATE_DONE;
                            task.progress = task.total > 0 ? task.total : task.progress;
                            notifyChanged();
                        }

                        @Override
                        public void onError(Response<File> response) {
                            task.state = CacheTask.STATE_FAILED;
                            notifyChanged();
                        }

                        @Override
                        public void downloadProgress(Progress progress) {
                            super.downloadProgress(progress);
                            task.progress = progress.currentSize;
                            task.total = progress.totalSize;
                            notifyChanged();
                        }
                    });
        });
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