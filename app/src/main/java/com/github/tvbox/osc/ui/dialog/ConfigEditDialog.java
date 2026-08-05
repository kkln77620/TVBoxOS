package com.github.tvbox.osc.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.ConfigBean;

/**
 * 配置地址 新增/编辑 对话框
 */
public class ConfigEditDialog extends Dialog {

    public interface OnSaveListener {
        void onSave(ConfigBean bean);
    }

    private final EditText inputName;
    private final EditText inputApi;
    private final EditText inputLive;
    private final EditText inputEpg;
    private final EditText inputProxy;
    private final TextView btnToggle;
    private final ConfigBean bean;
    private boolean enabled = true;

    public ConfigEditDialog(Context context, ConfigBean editBean, OnSaveListener listener) {
        super(context);
        setContentView(R.layout.dialog_config_edit);
        setCanceledOnTouchOutside(true);

        TextView title = findViewById(R.id.tvDialogTitle);
        inputName = findViewById(R.id.inputConfigName);
        inputApi = findViewById(R.id.inputConfigApi);
        inputLive = findViewById(R.id.inputConfigLive);
        inputEpg = findViewById(R.id.inputConfigEpg);
        inputProxy = findViewById(R.id.inputConfigProxy);
        btnToggle = findViewById(R.id.btnToggleEnabled);

        if (editBean != null) {
            this.bean = editBean;
            title.setText("编辑配置");
            inputName.setText(editBean.name);
            inputApi.setText(editBean.apiUrl);
            inputLive.setText(editBean.liveUrl);
            inputEpg.setText(editBean.epgUrl);
            inputProxy.setText(editBean.proxy);
            enabled = editBean.enabled;
        } else {
            this.bean = new ConfigBean();
            title.setText("新增配置");
        }
        updateToggleText();

        btnToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enabled = !enabled;
                updateToggleText();
            }
        });
        findViewById(R.id.btnConfigCancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        findViewById(R.id.btnConfigSave).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = inputName.getText().toString().trim();
                String api = inputApi.getText().toString().trim();
                if (TextUtils.isEmpty(api)) {
                    Toast.makeText(getContext(), "配置地址不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                bean.name = TextUtils.isEmpty(name) ? (api.length() > 20 ? api.substring(0, 20) : api) : name;
                bean.apiUrl = api;
                bean.liveUrl = inputLive.getText().toString().trim();
                bean.epgUrl = inputEpg.getText().toString().trim();
                bean.proxy = inputProxy.getText().toString().trim();
                bean.enabled = enabled;
                if (listener != null) listener.onSave(bean);
                dismiss();
            }
        });
    }

    private void updateToggleText() {
        btnToggle.setText(enabled ? "已启用" : "已停用");
        btnToggle.setTextColor(getContext().getResources().getColor(enabled ? R.color.color_FFFFFF : R.color.color_FFB800));
    }
}