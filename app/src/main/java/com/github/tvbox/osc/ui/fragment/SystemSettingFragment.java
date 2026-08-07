package com.github.tvbox.osc.ui.fragment;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.ui.activity.AboutDetailActivity;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.dialog.BackupDialog;
import com.github.tvbox.osc.ui.dialog.ContactAuthorDialog;
import com.github.tvbox.osc.ui.dialog.ResetDialog;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.ui.dialog.WallpaperDialog;
import com.github.tvbox.osc.ui.dialog.XWalkInitDialog;
import com.github.tvbox.osc.util.CrashHandler;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.FloatLogManager;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.PlayerHelper;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import okhttp3.HttpUrl;

/**
 * 设置-系统页: 语言/主题/壁纸/渲染/搜索/嗅探/DNS/备份/多配置/重置/关于/聚搜
 */
public class SystemSettingFragment extends BaseLazyFragment {

    // 设置变更标记: 退出设置页面时统一刷新
    private boolean needRefresh = false;
    // 上传壁纸文件选择请求码
    private static final int REQ_UPLOAD_WALLPAPER = 0x77;

    private TextView tvFastSearchText;
    private TextView tvLocale;
    private TextView tvTheme;
    private TextView tvRender;
    private TextView tvParseWebView;
    private TextView tvSearchView;
    private TextView tvDns;

    public static SystemSettingFragment newInstance() {
        return new SystemSettingFragment();
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_system;
    }

    String getSearchView(int type) {
        if (type == 0) {
            return "文字列表";
        } else {
            return "图片列表";
        }
    }

    String getLocaleView(int type) {
        if (type == 0) {
            return "中文";
        } else {
            return "英文";
        }
    }

    String getThemeView(int type) {
        switch (type) {
            case 1:
                return "哆啦A梦";
            case 2:
                return "百事";
            case 3:
                return "鸣人";
            case 4:
                return "小黄人";
            case 5:
                return "夜神月";
            case 6:
                return "樱木";
            default:
                return "奈飞";
        }
    }

    @Override
    protected void init() {
        tvFastSearchText = findViewById(R.id.showFastSearchText);
        tvFastSearchText.setText(Hawk.get(HawkConfig.FAST_SEARCH_MODE, false) ? "已开启" : "已关闭");
        TextView tvFloatLogText = findViewById(R.id.tvFloatLogText);
        tvFloatLogText.setText(FloatLogManager.getInstance().isShowing() ? "已开启" : "已关闭");
        TextView tvCrashLogText = findViewById(R.id.tvCrashLogText);
        boolean crashOn = Hawk.get(HawkConfig.CRASH_LOG_ENABLE, true);
        tvCrashLogText.setText(crashOn ? "已开启" : "已关闭");
        CrashHandler.getInstance().setEnabled(crashOn);
        tvLocale = findViewById(R.id.tvLocale);
        tvLocale.setText(getLocaleView(Hawk.get(HawkConfig.HOME_LOCALE, 0)));
        tvTheme = findViewById(R.id.tvTheme);
        tvTheme.setText(getThemeView(Hawk.get(HawkConfig.THEME_SELECT, 0)));
        tvRender = findViewById(R.id.tvRenderType);
        tvRender.setText(PlayerHelper.getRenderName(Hawk.get(HawkConfig.PLAY_RENDER, 0)));
        tvParseWebView = findViewById(R.id.tvParseWebView);
        tvParseWebView.setText(Hawk.get(HawkConfig.PARSE_WEBVIEW, true) ? "系统自带" : "XWalkView");
        tvSearchView = findViewById(R.id.tvSearchView);
        tvSearchView.setText(getSearchView(Hawk.get(HawkConfig.SEARCH_VIEW, 0)));
        tvDns = findViewById(R.id.tvDns);
        tvDns.setText(OkGoHelper.dnsHttpsList.get(Hawk.get(HawkConfig.DOH_URL, 0)));
        try {
            TextView tvMultiConfig = findViewById(R.id.tvMultiConfig);
            tvMultiConfig.setText(Hawk.get(HawkConfig.MULTI_CONFIG_ENABLE, false) ? "已开启" : "已关闭");
        } catch (Exception ignored) {
        }

        // 语言
        findViewById(R.id.llLocale).setOnClickListener(new View.OnClickListener() {
            private final int chkLang = Hawk.get(HawkConfig.HOME_LOCALE, 0);

            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.HOME_LOCALE, 0);
                ArrayList<Integer> types = new ArrayList<>();
                types.add(0);
                types.add(1);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip(getString(R.string.dia_locale));
                dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.HOME_LOCALE, value);
                        tvLocale.setText(getLocaleView(value));
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return getLocaleView(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, types, defaultPos);
                dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        if (chkLang != Hawk.get(HawkConfig.HOME_LOCALE, 0)) {
                            reloadActivity();
                        }
                    }
                });
                dialog.show();
            }
        });
        // 主题
        findViewById(R.id.llTheme).setOnClickListener(new View.OnClickListener() {
            private final int chkTheme = Hawk.get(HawkConfig.THEME_SELECT, 0);

            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.THEME_SELECT, 0);
                ArrayList<Integer> types = new ArrayList<>();
                types.add(0);
                types.add(1);
                types.add(2);
                types.add(3);
                types.add(4);
                types.add(5);
                types.add(6);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip(getString(R.string.dia_theme));
                dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.THEME_SELECT, value);
                        tvTheme.setText(getThemeView(value));
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return getThemeView(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, types, defaultPos);
                dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        if (chkTheme != Hawk.get(HawkConfig.THEME_SELECT, 0)) {
                            reloadActivity();
                        }
                    }
                });
                dialog.show();
            }
        });
        // 换张壁纸: 二级菜单
        findViewById(R.id.llWp).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                WallpaperDialog dialog = new WallpaperDialog(mContext, SystemSettingFragment.this::openWallpaperPicker);
                dialog.show();
            }
        });
        // 重置壁纸
        findViewById(R.id.llWpRecovery).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                File wp = new File(requireActivity().getFilesDir().getAbsolutePath() + "/wp");
                if (wp.exists()) wp.delete();
                Hawk.put(HawkConfig.WALLPAPER_MODE, "none");
                try {
                    File upload = new File(requireActivity().getFilesDir().getAbsolutePath() + "/wallpaper/upload.img");
                    if (upload.exists()) upload.delete();
                } catch (Throwable ignored) {
                }
                BaseActivity.resetWallpaperCache();
                ((BaseActivity) requireActivity()).changeWallpaper(true);
                Toast.makeText(mContext, "已恢复默认壁纸", Toast.LENGTH_SHORT).show();
            }
        });
        // 渲染方式
        findViewById(R.id.llRender).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.PLAY_RENDER, 0);
                ArrayList<Integer> renders = new ArrayList<>();
                renders.add(0);
                renders.add(1);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip(getString(R.string.dia_render));
                dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.PLAY_RENDER, value);
                        tvRender.setText(PlayerHelper.getRenderName(value));
                        PlayerHelper.init();
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return PlayerHelper.getRenderName(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, renders, defaultPos);
                dialog.show();
            }
        });
        // 搜索展示
        findViewById(R.id.llSearchView).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.SEARCH_VIEW, 0);
                ArrayList<Integer> types = new ArrayList<>();
                types.add(0);
                types.add(1);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip(getString(R.string.dia_search));
                dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.SEARCH_VIEW, value);
                        tvSearchView.setText(getSearchView(value));
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return getSearchView(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, types, defaultPos);
                dialog.show();
            }
        });
        // 嗅探Webview
        findViewById(R.id.llParseWebVew).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                boolean useSystem = !Hawk.get(HawkConfig.PARSE_WEBVIEW, true);
                Hawk.put(HawkConfig.PARSE_WEBVIEW, useSystem);
                tvParseWebView.setText(Hawk.get(HawkConfig.PARSE_WEBVIEW, true) ? "系统自带" : "XWalkView");
                if (!useSystem) {
                    Toast.makeText(mContext, "注意: XWalkView只适用于部分低Android版本，Android5.0以上推荐使用系统自带", Toast.LENGTH_LONG).show();
                    XWalkInitDialog dialog = new XWalkInitDialog(mContext);
                    dialog.setOnListener(new XWalkInitDialog.OnListener() {
                        @Override
                        public void onchange() {
                        }
                    });
                    dialog.show();
                }
            }
        });
        // 安全DNS
        findViewById(R.id.llDns).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int dohUrl = Hawk.get(HawkConfig.DOH_URL, 0);
                SelectDialog<String> dialog = new SelectDialog<>(mActivity);
                dialog.setTip(getString(R.string.dia_dns));
                dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<String>() {
                    @Override
                    public void click(String value, int pos) {
                        tvDns.setText(OkGoHelper.dnsHttpsList.get(pos));
                        Hawk.put(HawkConfig.DOH_URL, pos);
                        String url = OkGoHelper.getDohUrl(pos);
                        OkGoHelper.dnsOverHttps.setUrl(url.isEmpty() ? null : HttpUrl.get(url));
                        IjkMediaPlayer.toggleDotPort(pos > 0);
                    }

                    @Override
                    public String getDisplay(String val) {
                        return val;
                    }
                }, new DiffUtil.ItemCallback<String>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
                        return oldItem.equals(newItem);
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
                        return oldItem.equals(newItem);
                    }
                }, OkGoHelper.dnsHttpsList, dohUrl);
                dialog.show();
            }
        });
        // 备份
        findViewById(R.id.llBackup).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                BackupDialog dialog = new BackupDialog(mActivity);
                dialog.show();
            }
        });
        // 多配置模式开关
        findViewById(R.id.llMultiConfig).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                boolean enabled = !Hawk.get(HawkConfig.MULTI_CONFIG_ENABLE, false);
                Hawk.put(HawkConfig.MULTI_CONFIG_ENABLE, enabled);
                TextView tvMultiConfig = findViewById(R.id.tvMultiConfig);
                tvMultiConfig.setText(enabled ? "已开启" : "已关闭");
                needRefresh = true;
                Toast.makeText(mContext, enabled ? "多配置模式已开启, 退出设置后生效" : "多配置模式已关闭, 退出设置后生效", Toast.LENGTH_SHORT).show();
            }
        });
        // 重置UI
        findViewById(R.id.llReset).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                ResetDialog dialog = new ResetDialog(mActivity);
                dialog.show();
            }
        });
        // 关于
        findViewById(R.id.llAbout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                jumpActivity(AboutDetailActivity.class);
            }
        });
        // 聚搜
        findViewById(R.id.showFastSearch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                Hawk.put(HawkConfig.FAST_SEARCH_MODE, !Hawk.get(HawkConfig.FAST_SEARCH_MODE, false));
                tvFastSearchText.setText(Hawk.get(HawkConfig.FAST_SEARCH_MODE, false) ? "已开启" : "已关闭");
            }
        });
        // 联系作者: 二级菜单显示QQ号, 点击复制
        findViewById(R.id.llContactAuthor).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                ContactAuthorDialog dialog = new ContactAuthorDialog(mActivity);
                dialog.show();
            }
        });
        // 悬浮日志: 观察所有Activity活动, 需要悬浮窗权限
        // 崩溃日志开关
        findViewById(R.id.llCrashLog).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                boolean now = Hawk.get(HawkConfig.CRASH_LOG_ENABLE, true);
                Hawk.put(HawkConfig.CRASH_LOG_ENABLE, !now);
                CrashHandler.getInstance().setEnabled(!now);
                TextView tvCrashLogText = findViewById(R.id.tvCrashLogText);
                tvCrashLogText.setText(!now ? "已开启" : "已关闭");
            }
        });
        findViewById(R.id.llFloatLog).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                TextView tvFloatLogText = findViewById(R.id.tvFloatLogText);
                if (FloatLogManager.getInstance().isShowing()) {
                    FloatLogManager.getInstance().hide();
                    tvFloatLogText.setText("已关闭");
                    Toast.makeText(mContext, "悬浮日志已关闭", Toast.LENGTH_SHORT).show();
                    return;
                }
                // 悬浮窗权限检查
                if (!Settings.canDrawOverlays(mContext)) {
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + mContext.getPackageName()));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mContext.startActivity(intent);
                        Toast.makeText(mContext, "请授予悬浮窗权限后再次开启", Toast.LENGTH_LONG).show();
                    } catch (Throwable th) {
                        Toast.makeText(mContext, "无法打开悬浮窗授权页", Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
                FloatLogManager.getInstance().show(mActivity);
                tvFloatLogText.setText("已开启");
                Toast.makeText(mContext, "悬浮日志已开启", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 打开文件选择器: 上传壁纸(仅图片)
     */
    private void openWallpaperPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, REQ_UPLOAD_WALLPAPER);
        } catch (Throwable th) {
            th.printStackTrace();
            Toast.makeText(mContext, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_UPLOAD_WALLPAPER && resultCode == getActivity().RESULT_OK && data != null && data.getData() != null) {
            handleUploadWallpaper(data.getData());
        }
    }

    /**
     * 处理上传的壁纸文件: 仅支持静态图片(jpeg/png/webp), 不支持GIF/视频
     */
    private void handleUploadWallpaper(Uri uri) {
        InputStream is = null;
        FileOutputStream fos = null;
        try {
            String mime = getContext().getContentResolver().getType(uri);
            boolean ok = false;
            if (mime != null) {
                ok = mime.startsWith("image/") && !"image/gif".equalsIgnoreCase(mime);
            } else {
                String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase();
                ok = path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") || path.endsWith(".webp") || path.endsWith(".bmp");
            }
            if (!ok) {
                Toast.makeText(mContext, "仅支持静态图片(jpg/png/webp/bmp)", Toast.LENGTH_SHORT).show();
                return;
            }
            File dir = new File(requireActivity().getFilesDir(), "wallpaper");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, "upload.img");
            is = getContext().getContentResolver().openInputStream(uri);
            if (is == null) {
                Toast.makeText(mContext, "无法读取所选文件", Toast.LENGTH_SHORT).show();
                return;
            }
            fos = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) > 0) {
                fos.write(buf, 0, len);
            }
            fos.flush();
            Hawk.put(HawkConfig.WALLPAPER_MODE, "upload");
            Hawk.put(HawkConfig.WALLPAPER_PATH, out.getAbsolutePath());
            BaseActivity.resetWallpaperCache();
            ((BaseActivity) requireActivity()).changeWallpaper(true);
            Toast.makeText(mContext, "壁纸已上传", Toast.LENGTH_SHORT).show();
        } catch (Throwable th) {
            th.printStackTrace();
            Toast.makeText(mContext, "壁纸上传失败: " + th.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            try {
                if (is != null) is.close();
            } catch (Throwable ignored) {
            }
            try {
                if (fos != null) fos.close();
            } catch (Throwable ignored) {
            }
        }
    }

    void reloadActivity() {
        Intent intent = getActivity().getApplicationContext().getPackageManager().getLaunchIntentForPackage(getActivity().getApplication().getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Bundle bundle = new Bundle();
        bundle.putBoolean("useCache", true);
        intent.putExtras(bundle);
        getActivity().getApplicationContext().startActivity(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 设置页有变更(多配置开关等): 退出设置页面时统一刷新主页
        if (needRefresh && getActivity() != null) {
            reloadActivity();
        }
    }
}