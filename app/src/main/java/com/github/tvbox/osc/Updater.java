package com.github.tvbox.osc;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;

import com.github.tvbox.osc.BuildConfig;
import com.github.tvbox.osc.ui.dialog.UpdateDialog;
import com.github.tvbox.osc.util.Github;
import com.github.tvbox.osc.util.ToastHelper;
import com.github.tvbox.osc.util.urlhttp.OkHttpUtil;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.FileCallback;
import com.lzy.okgo.model.Progress;
import com.lzy.okgo.model.Response;

import org.json.JSONObject;

import java.io.File;

/**
 * 更新检测和下载管理器
 */
public class Updater {
    
    private UpdateDialog updateDialog;
    private Activity activity;
    private boolean checking = false;
    private boolean silent = false;

    /**
     * 检查更新(手动: 显示"正在检查"/"已是最新")
     */
    public void check(Activity activity) {
        check(activity, false);
    }

    /**
     * 检查更新
     *
     * @param silent true=静默检查(启动自动, 无新版不提示), false=手动(始终反馈结果)
     */
    public void check(Activity activity, boolean silent) {
        if (checking) {
            if (!silent) ToastHelper.show("正在检查更新...");
            return;
        }
        this.activity = activity;
        this.silent = silent;
        checking = true;
        if (!silent) ToastHelper.show("正在检查更新...");

        new CheckUpdateTask().execute();
    }

    /**
     * 异步检查更新任务: 通过 GitHub Releases API 获取最新版本
     */
    private class CheckUpdateTask extends AsyncTask<Void, Void, UpdateInfo> {

        @Override
        protected UpdateInfo doInBackground(Void... voids) {
            try {
                String jsonContent = OkHttpUtil.string(Github.getLatestReleaseApi(), "update", null);
                if (jsonContent == null || jsonContent.isEmpty()) {
                    return null;
                }
                JSONObject json = new JSONObject(jsonContent);
                if (json.optBoolean("draft", false)) {
                    return null;
                }
                UpdateInfo info = new UpdateInfo();
                info.name = json.optString("tag_name", ""); // tag 即版本号
                info.desc = json.optString("body", "");      // 更新说明
                info.apkUrl = "";
                // 取第一个 APK 附件作为下载地址
                try {
                    org.json.JSONArray assets = json.optJSONArray("assets");
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.getJSONObject(i);
                            String aName = asset.optString("name", "").toLowerCase();
                            if (aName.endsWith(".apk")) {
                                info.apkUrl = asset.optString("browser_download_url", "");
                                break;
                            }
                        }
                    }
                } catch (Throwable th) {
                    th.printStackTrace();
                }
                return info;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(UpdateInfo updateInfo) {
            checking = false;
            if (updateInfo == null) {
                if (!silent) ToastHelper.show("检查更新失败");
                return;
            }
            String currentVersion = BuildConfig.VERSION_NAME;
            String newVersion = updateInfo.name == null ? "" : updateInfo.name.trim();
            if (newVersion.isEmpty()) {
                if (!silent) ToastHelper.show("检查更新失败");
                return;
            }
            if (compareVersion(newVersion, currentVersion) > 0) {
                // 有新版本, 显示更新对话框
                showUpdateDialog(updateInfo);
            } else {
                if (!silent) ToastHelper.show("已是最新版本");
            }
        }
    }

    /**
     * 版本号比较: a > b 返回正数, a < b 返回负数, 相等返回0
     * 支持 1.0.20260805_1322 与 v1.0.1 等格式(逐段比较数字)
     */
    private static int compareVersion(String a, String b) {
        String[] sa = a.replace("v", "").split("[^0-9]+");
        String[] sb = b.replace("v", "").split("[^0-9]+");
        int len = Math.max(sa.length, sb.length);
        for (int i = 0; i < len; i++) {
            long na = i < sa.length && !sa[i].isEmpty() ? Long.parseLong(sa[i]) : 0;
            long nb = i < sb.length && !sb[i].isEmpty() ? Long.parseLong(sb[i]) : 0;
            if (na != nb) return Long.compare(na, nb);
        }
        return 0;
    }
    
    /**
     * 显示更新对话框
     */
    private void showUpdateDialog(UpdateInfo info) {
        if (updateDialog != null && updateDialog.isShowing()) {
            updateDialog.dismiss();
        }

        updateDialog = new UpdateDialog(activity);
        updateDialog.setVersion(info.name);
        updateDialog.setDescription(info.desc);
        // 迅雷下载: 点击直接跳转迅雷
        updateDialog.setXunleiUrl("https://pan.xunlei.com/s/VOzJhB3F1XK4oIjODWODOmRQA1?pwd=tdpz");
        updateDialog.setOnConfirmListener(() -> {
            downloadApk(info.apkUrl);
        });
        updateDialog.show();
    }
    
    /**
     * 下载 APK
     */
    private void downloadApk(String apkUrl) {
        if (updateDialog != null) {
            updateDialog.setDownloading(true);
        }
        
        File downloadDir = new File(activity.getExternalFilesDir(null), "download");
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }
        
        File apkFile = new File(downloadDir, "TVBox_update.apk");
        
        OkGo.<File>get(apkUrl)
                .tag("update")
                .execute(new FileCallback(apkFile.getParent(), apkFile.getName()) {
                    @Override
                    public void onSuccess(Response<File> response) {
                        if (updateDialog != null) {
                            updateDialog.dismiss();
                        }
                        // 安装 APK
                        installApk(response.body());
                    }
                    
                    @Override
                    public void onError(Response<File> response) {
                        if (updateDialog != null) {
                            updateDialog.setDownloading(false);
                        }
                        ToastHelper.show("下载失败: " + response.getException().getMessage());
                    }
                    
                    @Override
                    public void downloadProgress(Progress progress) {
                        if (updateDialog != null) {
                            int percent = (int) (progress.fraction * 100);
                            updateDialog.setProgress(percent);
                        }
                    }
                });
    }
    
    /**
     * 安装 APK
     */
    private void installApk(File apkFile) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            Uri apkUri;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                // 使用 FileProvider
                apkUri = androidx.core.content.FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + ".fileprovider",
                    apkFile
                );
            } else {
                apkUri = Uri.fromFile(apkFile);
            }
            
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            activity.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            ToastHelper.show("安装失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新信息数据类
     */
    private static class UpdateInfo {
        String name;
        String desc;
        int code;
        String apkUrl;
    }
}
