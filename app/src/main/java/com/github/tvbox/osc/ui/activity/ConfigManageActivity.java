package com.github.tvbox.osc.ui.activity;

import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.ConfigBean;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.dialog.ConfigEditDialog;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.ui.dialog.TipDialog;
import com.github.tvbox.osc.util.ConfigManager;
import com.github.tvbox.osc.util.HawkConfig;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;
import com.orhanobut.hawk.Hawk;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置地址管理: 新增/编辑/删除/启用
 */
public class ConfigManageActivity extends BaseActivity {

    private TvRecyclerView configList;
    private ConfigAdapter adapter;

    /**
     * 内置配置地址: 名称为 内置地址A/B/C
     */
    private static final String[][] BUILTIN_CONFIGS = {
            {"内置地址A", "http://www.饭太硬.cc/tv"},
            {"内置地址B", "http://xn--z7x900a.net/tv"},
            {"内置地址C", "https://9877.kstore.space/one.json"}
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_config_manage;
    }

    @Override
    protected void init() {
        configList = findViewById(R.id.configList);
        configList.setLayoutManager(new LinearLayoutManager(this));
        configList.setAdapter(adapter = new ConfigAdapter(ConfigManager.getConfigs()));

        findViewById(R.id.btnAddConfig).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEditDialog(null);
            }
        });

        // 使用内置配置地址: 检查已导入的地址, 缺哪个补哪个
        findViewById(R.id.btnUseBuiltin).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                importBuiltinConfigs();
            }
        });
    }

    /**
     * 导入内置配置: 已有则不重复导入, 缺几个补几个
     */
    private void importBuiltinConfigs() {
        List<ConfigBean> list = ConfigManager.getConfigs();
        int added = 0;
        for (String[] bc : BUILTIN_CONFIGS) {
            boolean exists = false;
            for (ConfigBean c : list) {
                if (c.apiUrl != null && c.apiUrl.trim().equalsIgnoreCase(bc[1].trim())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                list.add(new ConfigBean(bc[0], bc[1]));
                added++;
            }
        }
        if (added > 0) {
            ConfigManager.saveConfigs(list);
            Toast.makeText(this, "已导入 " + added + " 个内置配置地址", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "内置配置地址已全部存在", Toast.LENGTH_SHORT).show();
        }
        if (adapter != null) {
            adapter.setNewData(ConfigManager.getConfigs());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.setNewData(ConfigManager.getConfigs());
        }
    }

        private boolean homeConfigChanged = false;

    /**
     * 将该配置设为"主页配置": 主页显示该配置(地址)的数据, 点击后立即刷新
     */
    private void setHomeConfig(ConfigBean item) {
        ConfigManager.setHomeConfigId(item.id);
        homeConfigChanged = true;
        adapter.setNewData(ConfigManager.getConfigs());
        Toast.makeText(this, "主页已设为: " + item.name + ", 正在刷新", Toast.LENGTH_SHORT).show();
        // 立即刷新主页
        HomeActivity.reHome(this);
    }

    private void showEditDialog(ConfigBean editBean) {
        ConfigEditDialog dialog = new ConfigEditDialog(this, editBean, bean -> {
            if (editBean == null) {
                ConfigManager.addConfig(bean);
                Toast.makeText(this, "已新增配置: " + bean.name, Toast.LENGTH_SHORT).show();
            } else {
                ConfigManager.updateConfig(bean);
                Toast.makeText(this, "已保存配置: " + bean.name, Toast.LENGTH_SHORT).show();
            }
            adapter.setNewData(ConfigManager.getConfigs());
        });
        dialog.show();
    }

    class ConfigAdapter extends BaseQuickAdapter<ConfigBean, BaseViewHolder> {

        ConfigAdapter(List<ConfigBean> data) {
            super(R.layout.item_config, data);
        }

        @Override
        protected void convert(BaseViewHolder helper, ConfigBean item) {
            TextView tvEnabled = helper.getView(R.id.tvEnabled);
            tvEnabled.setText(item.enabled ? "✓" : "✗");
            tvEnabled.setTextColor(getResources().getColor(item.enabled ? R.color.color_FFFFFF : R.color.color_FFB800));
            // 主页配置标记
            boolean isHome = item.id != null && item.id.equals(ConfigManager.getHomeConfigId());
            String name = item.name == null || item.name.isEmpty() ? "未命名" : item.name;
            helper.setText(R.id.tvConfigName, isHome ? "★ " + name + " (主页)" : name);
            helper.setText(R.id.tvConfigUrl, item.apiUrl == null ? "" : item.apiUrl);
            // 主页源按钮高亮
            TextView btnHome = helper.getView(R.id.btnHomeConfig);
            if (isHome) {
                btnHome.setText("✓主页");
                btnHome.setTextColor(getResources().getColor(R.color.color_FFB800));
            } else {
                btnHome.setText("主页源");
                btnHome.setTextColor(getResources().getColor(android.R.color.white));
            }

            // 点击整行/启用状态: 切换启用
            tvEnabled.setOnClickListener(v -> {
                ConfigManager.toggleEnabled(item.id, !item.enabled);
                adapter.setNewData(ConfigManager.getConfigs());
            });
            // 设置主页配置: 该配置的数据显示在主页
            helper.getView(R.id.btnHomeConfig).setOnClickListener(v -> setHomeConfig(item));
            // 编辑
            helper.getView(R.id.btnEditConfig).setOnClickListener(v -> showEditDialog(item));
            // 删除
            helper.getView(R.id.btnDelConfig).setOnClickListener(v -> {
                TipDialog dialog = new TipDialog(ConfigManageActivity.this,
                        "确定删除配置: " + (item.name == null ? "" : item.name) + " ?",
                        "删除", "取消", new TipDialog.OnListener() {
                    @Override
                    public void left() {
                        ConfigManager.deleteConfig(item.id);
                        adapter.setNewData(ConfigManager.getConfigs());
                        Toast.makeText(ConfigManageActivity.this, "已删除配置", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void right() {
                    }

                    @Override
                    public void cancel() {
                    }
                });
                dialog.show();
            });
        }
    }
}