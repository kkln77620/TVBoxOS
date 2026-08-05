package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.Updater;
import com.github.tvbox.osc.BuildConfig;

import org.jetbrains.annotations.NotNull;

public class AboutDialog extends BaseDialog {

    private Updater updater;
    private Context context;

    public AboutDialog(@NonNull @NotNull Context context) {
        super(context);
        this.context = context;
        setContentView(R.layout.dialog_about);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initVersionInfo();
    }

    private void initVersionInfo() {
        // 在 onCreate 中设置版本号，确保 View 已经创建
        TextView versionText = findViewById(R.id.about_version);
        if (versionText != null) {
            String versionName = BuildConfig.VERSION_NAME;
            if (versionName == null || versionName.isEmpty()) {
                versionName = "未知版本";
            }
            String versionInfo = "版本: " + versionName + "\n(点击检查更新)";
            versionText.setText(versionInfo);
            versionText.setOnClickListener(v -> {
                // 点击版本号检查更新
                if (context instanceof Activity) {
                    if (updater == null) {
                        updater = new Updater();
                    }
                    updater.check((Activity) context);
                }
            });
        }
    }
}