package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.net.Uri;
import android.widget.TextView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.Updater;
import com.github.tvbox.osc.base.BaseActivity;

/**
 * 关于详情页: 软件详细信息/定位/功能/源码地址
 */
public class AboutDetailActivity extends BaseActivity {

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_about_detail;
    }

    @Override
    protected void init() {
        try {
            TextView tvVersion = findViewById(R.id.tvVersion);
            tvVersion.setText("版本 " + getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
        } catch (Throwable ignored) {
        }

        // 检测更新: 手动检查 GitHub Release
        findViewById(R.id.btnCheckUpdate).setOnClickListener(v -> {
            try {
                new Updater().check(this, false);
            } catch (Throwable th) {
                th.printStackTrace();
            }
        });

        // 源码地址: 打开浏览器
        findViewById(R.id.tvSourceUrl).setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/"));
                startActivity(intent);
            } catch (Throwable th) {
                th.printStackTrace();
            }
        });

        // 返回
        findViewById(R.id.btnBackAbout).setOnClickListener(v -> finish());
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}