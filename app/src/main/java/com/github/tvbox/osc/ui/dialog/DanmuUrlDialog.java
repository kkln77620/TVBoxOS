package com.github.tvbox.osc.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.TextView;

import com.github.tvbox.osc.R;

/**
 * 弹幕地址输入对话框
 */
public class DanmuUrlDialog extends Dialog {

    public interface OnSaveListener {
        void onSave(String url);
    }

    public DanmuUrlDialog(Context context, String currentUrl, OnSaveListener listener) {
        super(context);
        setContentView(R.layout.dialog_danmu_url);
        setCanceledOnTouchOutside(true);

        EditText input = findViewById(R.id.inputDanmuUrl);
        if (!TextUtils.isEmpty(currentUrl)) {
            input.setText(currentUrl);
            input.setSelection(input.length());
        }
        findViewById(R.id.btnDanmuCancel).setOnClickListener(v -> dismiss());
        findViewById(R.id.btnDanmuSave).setOnClickListener(v -> {
            String url = input.getText().toString().trim();
            if (listener != null) {
                listener.onSave(url);
            }
            dismiss();
        });
    }
}
