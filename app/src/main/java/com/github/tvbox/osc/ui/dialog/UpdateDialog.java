package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;

import org.jetbrains.annotations.NotNull;

/**
 * 更新对话框
 */
public class UpdateDialog extends BaseDialog {
    
    private TextView versionText;
    private TextView descText;
    private ProgressBar progressBar;
    private TextView progressText;
    private Button confirmButton;
    private Button cancelButton;
    private Button xunleiButton;

    private String version;
    private String description;
    private String xunleiUrl;
    private boolean downloading = false;
    private OnConfirmListener onConfirmListener;

    public UpdateDialog(@NonNull @NotNull Activity activity) {
        super(activity);
        setContentView(R.layout.dialog_update);
        initViews();
    }

    private void initViews() {
        versionText = findViewById(R.id.update_version);
        descText = findViewById(R.id.update_desc);
        progressBar = findViewById(R.id.update_progress);
        progressText = findViewById(R.id.update_progress_text);
        confirmButton = findViewById(R.id.update_confirm);
        cancelButton = findViewById(R.id.update_cancel);
        xunleiButton = findViewById(R.id.update_xunlei);

        // 更新内容超长时支持上下滑动
        descText.setMovementMethod(ScrollingMovementMethod.getInstance());

        progressBar.setVisibility(View.GONE);
        progressText.setVisibility(View.GONE);

        confirmButton.setOnClickListener(v -> {
            if (onConfirmListener != null) {
                onConfirmListener.onConfirm();
            }
        });

        // 迅雷下载: 直接跳转迅雷(浏览器/迅雷App打开链接)
        xunleiButton.setOnClickListener(v -> {
            if (xunleiUrl != null && !xunleiUrl.isEmpty()) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(xunleiUrl));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(intent);
                } catch (Throwable th) {
                    th.printStackTrace();
                }
            }
        });

        cancelButton.setOnClickListener(v -> dismiss());
    }
    
    public void setVersion(String version) {
        this.version = version;
        if (versionText != null) {
            versionText.setText("新版本: " + version);
        }
    }
    
    public void setDescription(String description) {
        this.description = description;
        if (descText != null) {
            if (description != null && !description.isEmpty()) {
                descText.setText("更新内容：\n" + description);
            } else {
                descText.setText("更新内容：\n暂无更新说明");
            }
        }
    }
    
    public void setOnConfirmListener(OnConfirmListener listener) {
        this.onConfirmListener = listener;
    }

    /**
     * 设置迅雷下载链接: 点击"迅雷下载"直接跳转(浏览器/迅雷App)
     */
    public void setXunleiUrl(String url) {
        this.xunleiUrl = url;
    }
    
    public void setDownloading(boolean downloading) {
        this.downloading = downloading;
        if (confirmButton != null) {
            confirmButton.setEnabled(!downloading);
            confirmButton.setText(downloading ? "下载中..." : "GitHub下载");
        }
        if (progressBar != null) {
            progressBar.setVisibility(downloading ? View.VISIBLE : View.GONE);
        }
        if (progressText != null) {
            progressText.setVisibility(downloading ? View.VISIBLE : View.GONE);
        }
        if (cancelButton != null) {
            cancelButton.setEnabled(!downloading);
        }
    }
    
    public void setProgress(int progress) {
        if (progressBar != null) {
            progressBar.setProgress(progress);
        }
        if (progressText != null) {
            progressText.setText("下载进度: " + progress + "%");
        }
    }
    
    public interface OnConfirmListener {
        void onConfirm();
    }
}
