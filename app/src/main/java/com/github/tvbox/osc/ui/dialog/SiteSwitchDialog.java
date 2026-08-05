package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.util.HawkConfig;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.orhanobut.hawk.Hawk;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页数据源分组选择对话框
 * 上面: 大配置(地址)可滑动横条; 下面: 该配置下的子数据源网格
 */
public class SiteSwitchDialog extends BaseDialog {

    public interface OnSiteSelectedListener {
        void onSiteSelected(SourceBean sb);
    }

    private final LinearLayout configBar;
    private final TvRecyclerView siteList;
    private final LinkedHashMap<String, List<SourceBean>> groups = new LinkedHashMap<>();
    private final Map<String, TextView> configButtons = new LinkedHashMap<>();
    private final OnSiteSelectedListener listener;
    private String currentConfig = "";

    public SiteSwitchDialog(@NonNull @NotNull Context context, OnSiteSelectedListener listener) {
        super(context);
        setContentView(R.layout.dialog_site_switch);
        setCanceledOnTouchOutside(true);
        this.listener = listener;

        configBar = findViewById(R.id.configBar);
        siteList = findViewById(R.id.siteList);

        // 按配置分组
        for (SourceBean sb : ApiConfig.get().getSourceBeanList()) {
            if (sb.getHide() != 0) continue;
            String cfgName = sb.getConfigName();
            if (cfgName == null || cfgName.isEmpty()) cfgName = "默认";
            List<SourceBean> list = groups.get(cfgName);
            if (list == null) {
                list = new ArrayList<>();
                groups.put(cfgName, list);
            }
            list.add(sb);
        }
        if (groups.isEmpty()) {
            dismiss();
            return;
        }
        buildConfigBar();
        // 只有一组配置时: 隐藏分组横条, 直接显示源网格
        if (groups.size() <= 1) {
            findViewById(R.id.configBar).setVisibility(View.GONE);
            findViewById(R.id.configBar).setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0));
        }
        // 默认选中当前主页源所在配置
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        String homeConfig = home != null && home.getConfigName() != null ? home.getConfigName() : "";
        if (homeConfig.isEmpty() && home != null) homeConfig = "默认";
        if (!groups.containsKey(homeConfig)) homeConfig = groups.keySet().iterator().next();
        switchConfig(homeConfig);
    }

    private void buildConfigBar() {
        configBar.removeAllViews();
        for (String cfgName : groups.keySet()) {
            TextView btn = new TextView(getContext());
            btn.setText(cfgName);
            btn.setTextSize(18);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(dp(20), dp(10), dp(20), dp(10));
            btn.setTextColor(Color.WHITE);
            btn.setBackgroundResource(R.drawable.button_dialog_vod);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(10);
            btn.setLayoutParams(lp);
            btn.setFocusable(true);
            btn.setClickable(true);
            btn.setOnClickListener(v -> switchConfig(cfgName));
            configButtons.put(cfgName, btn);
            configBar.addView(btn);
        }
    }

    private void switchConfig(String cfgName) {
        currentConfig = cfgName;
        // 高亮当前配置按钮
        for (Map.Entry<String, TextView> entry : configButtons.entrySet()) {
            TextView btn = entry.getValue();
            if (entry.getKey().equals(cfgName)) {
                btn.setTextColor(getContext().getResources().getColor(R.color.color_FFB800));
            } else {
                btn.setTextColor(Color.WHITE);
            }
        }
        List<SourceBean> list = groups.get(cfgName);
        if (list == null) list = new ArrayList<>();
        int span = Math.min(3, Math.max(1, (int) Math.ceil(list.size() / 8.0)));
        siteList.setLayoutManager(new V7GridLayoutManager(getContext(), span));
        SiteAdapter adapter = new SiteAdapter(list);
        adapter.setOnItemClickListener((adapter1, view, position) -> {
            SourceBean sb = adapter.getItem(position);
            if (sb != null && listener != null) {
                listener.onSiteSelected(sb);
            }
            dismiss();
        });
        siteList.setAdapter(adapter);
    }

    private int dp(int v) {
        return (int) (getContext().getResources().getDisplayMetrics().density * v);
    }

    class SiteAdapter extends BaseQuickAdapter<SourceBean, BaseViewHolder> {

        SiteAdapter(List<SourceBean> data) {
            super(R.layout.item_site_switch, data);
        }

        @Override
        protected void convert(BaseViewHolder helper, SourceBean item) {
            TextView tv = helper.getView(R.id.tvSiteName);
            tv.setText(item.getName());
            // 当前主页源高亮
            String homeKey = Hawk.get(HawkConfig.HOME_API, "");
            if (homeKey.equals(item.getKey())) {
                tv.setTextColor(getContext().getResources().getColor(R.color.color_FFB800));
            } else {
                tv.setTextColor(Color.WHITE);
            }
        }
    }
}