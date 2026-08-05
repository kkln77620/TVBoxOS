package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.CacheTask;
import com.github.tvbox.osc.util.DownloadManager;
import com.owen.tvrecyclerview.widget.TvRecyclerView;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * 缓存管理: 显示下载任务列表(状态/进度/播放/删除)
 */
public class CacheActivity extends BaseActivity {

    private TvRecyclerView cacheList;
    private TextView tvEmpty;
    private CacheAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (adapter != null) {
                adapter.setNewData(DownloadManager.getInstance().getTasks());
                updateEmpty();
            }
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_cache;
    }

    @Override
    protected void init() {
        cacheList = findViewById(R.id.cacheList);
        tvEmpty = findViewById(R.id.tvEmpty);
        cacheList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CacheAdapter(DownloadManager.getInstance().getTasks());
        cacheList.setAdapter(adapter);
        updateEmpty();
        handler.post(refreshRunnable);
    }

    private void updateEmpty() {
        List<CacheTask> tasks = DownloadManager.getInstance().getTasks();
        boolean empty = tasks == null || tasks.isEmpty();
        tvEmpty.setVisibility(empty ? android.view.View.VISIBLE : android.view.View.GONE);
        cacheList.setVisibility(empty ? android.view.View.GONE : android.view.View.VISIBLE);
    }

    class CacheAdapter extends BaseQuickAdapter<CacheTask, BaseViewHolder> {

        CacheAdapter(List<CacheTask> data) {
            super(R.layout.item_cache_task, data);
        }

        @Override
        protected void convert(BaseViewHolder helper, CacheTask item) {
            if (item == null) return;
            helper.setText(R.id.tvTaskName, item.name == null ? "视频" : item.name);
            TextView tvStatus = helper.getView(R.id.tvTaskStatus);
            ProgressBar pb = helper.getView(R.id.taskProgress);
            switch (item.state) {
                case CacheTask.STATE_DOWNLOADING:
                    tvStatus.setText("下载中 " + formatSize(item.progress) + " / " + formatSize(item.total));
                    pb.setVisibility(android.view.View.VISIBLE);
                    pb.setProgress(item.total > 0 ? (int) (item.progress * 100 / item.total) : 0);
                    break;
                case CacheTask.STATE_DONE:
                    tvStatus.setText("已完成 " + formatSize(item.progress));
                    pb.setVisibility(android.view.View.VISIBLE);
                    pb.setProgress(100);
                    break;
                case CacheTask.STATE_FAILED:
                    tvStatus.setText("下载失败");
                    pb.setVisibility(android.view.View.VISIBLE);
                    pb.setProgress(0);
                    break;
                default:
                    tvStatus.setText("等待中");
                    pb.setVisibility(android.view.View.VISIBLE);
                    pb.setProgress(0);
                    break;
            }
            // 播放
            helper.getView(R.id.btnTaskPlay).setOnClickListener(v -> {
                File f = new File(item.filePath);
                if (item.state == CacheTask.STATE_DONE && f.exists()) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(Uri.fromFile(f), "video/*");
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    } catch (Throwable th) {
                        th.printStackTrace();
                        Toast.makeText(CacheActivity.this, "无法播放, 请检查文件", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(CacheActivity.this, "下载完成后方可播放", Toast.LENGTH_SHORT).show();
                }
            });
            // 删除
            helper.getView(R.id.btnTaskDel).setOnClickListener(v -> {
                DownloadManager.getInstance().removeTask(CacheActivity.this, item.id);
                adapter.setNewData(DownloadManager.getInstance().getTasks());
                updateEmpty();
            });
        }
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "未知";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb);
        double gb = mb / 1024.0;
        return String.format(Locale.getDefault(), "%.2f GB", gb);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}