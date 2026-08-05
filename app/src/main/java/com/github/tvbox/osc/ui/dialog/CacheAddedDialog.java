package com.github.tvbox.osc.ui.dialog;

import android.content.Context;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;

import org.jetbrains.annotations.NotNull;

/**
 * 缓存添加成功弹窗(上下排版): 跳转缓存页 / 知道了
 */
public class CacheAddedDialog extends BaseDialog {

    public interface OnListener {
        void onJump();
    }

    public CacheAddedDialog(@NonNull @NotNull Context context, String tip, OnListener listener) {
        super(context);
        setContentView(R.layout.dialog_cache_added);
        setCanceledOnTouchOutside(false);
        if (tip != null && !tip.isEmpty()) {
            ((android.widget.TextView) findViewById(R.id.tipInfo)).setText(tip);
        }
        // 跳转缓存页
        findViewById(R.id.btnJumpCache).setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onJump();
        });
        // 知道了: 关闭
        findViewById(R.id.btnKnow).setOnClickListener(v -> dismiss());
    }
}