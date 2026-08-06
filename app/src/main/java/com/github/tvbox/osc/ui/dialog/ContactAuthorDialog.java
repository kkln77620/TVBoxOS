package com.github.tvbox.osc.ui.dialog;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;

import com.github.tvbox.osc.R;

/**
 * 联系作者对话框: 显示QQ号, 点击复制到剪贴板
 */
public class ContactAuthorDialog extends Dialog {

    private static final String QQ = "3921435329";

    public ContactAuthorDialog(Context context) {
        super(context);
        setContentView(R.layout.dialog_contact_author);
        setCanceledOnTouchOutside(true);
        // 点击QQ内容或复制按钮均复制
        findViewById(R.id.tvContactQq).setOnClickListener(v -> copyAndClose(context));
        findViewById(R.id.btnContactCopy).setOnClickListener(v -> copyAndClose(context));
        findViewById(R.id.btnContactClose).setOnClickListener(v -> dismiss());
    }

    private void copyAndClose(Context context) {
        try {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("qq", QQ));
            Toast.makeText(context, "QQ号已复制: " + QQ, Toast.LENGTH_SHORT).show();
        } catch (Throwable th) {
            Toast.makeText(context, "复制失败", Toast.LENGTH_SHORT).show();
        }
        dismiss();
    }
}
