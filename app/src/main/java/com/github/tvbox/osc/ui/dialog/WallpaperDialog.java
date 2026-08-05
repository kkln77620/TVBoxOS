package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import org.jetbrains.annotations.NotNull;

/**
 * 更换壁纸二级菜单: 内置壁纸 / 上传壁纸(仅图片)
 */
public class WallpaperDialog extends BaseDialog {

    public interface OnWallpaperActionListener {
        /** 上传壁纸: 由宿主发起文件选择 */
        void onUploadWallpaper();
    }

    private final OnWallpaperActionListener listener;

    public WallpaperDialog(@NonNull @NotNull Context context, OnWallpaperActionListener listener) {
        super(context);
        setContentView(R.layout.dialog_wallpaper);
        setCanceledOnTouchOutside(true);
        this.listener = listener;

        // 内置壁纸: 按顺序切换
        findViewById(R.id.btnBuiltinWallpaper).setOnClickListener(v -> {
            Hawk.put(HawkConfig.WALLPAPER_MODE, "builtin");
            int idx = Hawk.get(HawkConfig.WALLPAPER_INDEX, 0) + 1;
            Hawk.put(HawkConfig.WALLPAPER_INDEX, idx);
            if (context instanceof BaseActivity) {
                ((BaseActivity) context).changeWallpaper(true);
            }
            Toast.makeText(context, "已切换内置壁纸", Toast.LENGTH_SHORT).show();
            dismiss();
        });

        // 上传壁纸: 交给宿主打开文件选择器
        findViewById(R.id.btnUploadWallpaper).setOnClickListener(v -> {
            if (listener != null) listener.onUploadWallpaper();
            dismiss();
        });
    }
}