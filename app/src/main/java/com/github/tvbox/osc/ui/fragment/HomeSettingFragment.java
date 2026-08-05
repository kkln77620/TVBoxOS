package com.github.tvbox.osc.ui.fragment;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.ConfigBean;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.ui.activity.ConfigManageActivity;
import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.dialog.HomeIconDialog;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.util.ConfigManager;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.orhanobut.hawk.Hawk;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * 设置-主页页: 配置地址/数据源/显示/图标/推荐/历史/启动直播
 */
public class HomeSettingFragment extends BaseLazyFragment {

    private TextView tvDebugOpen;
    private TextView tvApi;
    private TextView tvHomeApi;
    private TextView tvHomeShow;
    private TextView tvHomeRec;
    private TextView tvHomeNum;
    private TextView tvHomeDefaultShow;

    public static HomeSettingFragment newInstance() {
        return new HomeSettingFragment();
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_home;
    }

    /**
     * 首页数据源显示名: 多配置模式下带所属配置名
     */
    private String getHomeSourceDisplayName() {
        try {
            SourceBean home = ApiConfig.get().getHomeSourceBean();
            if (home == null) return "";
            String configName = home.getConfigName();
            if (configName != null && !configName.isEmpty()) {
                return "[" + configName + "] " + home.getName();
            }
            return home.getName();
        } catch (Throwable th) {
            return "";
        }
    }

    String getHomeRecName(int type) {
        if (type == 1) {
            return "站点推荐";
        } else if (type == 2) {
            return "豆瓣热播";
        }
        return "默认";
    }

    @Override
    public void onResume() {
        super.onResume();
        // 从配置管理返回时刷新显示(主页配置可能已变更)
        if (tvApi != null) refreshApiDisplay();
        if (tvHomeApi != null) tvHomeApi.setText(getHomeSourceDisplayName());
    }

    /**
     * 配置地址显示: 优先显示主页配置(第一条启用配置)的地址
     */
    private void refreshApiDisplay() {
        try {
            List<ConfigBean> enabled = ConfigManager.getEnabledConfigs();
            if (enabled != null && !enabled.isEmpty() && enabled.get(0).apiUrl != null && !enabled.get(0).apiUrl.trim().isEmpty()) {
                tvApi.setText(enabled.get(0).apiUrl.trim());
            } else {
                tvApi.setText(Hawk.get(HawkConfig.API_URL, ""));
            }
        } catch (Exception ignored) {
            tvApi.setText(Hawk.get(HawkConfig.API_URL, ""));
        }
    }

    @Override
    protected void init() {
        tvDebugOpen = findViewById(R.id.tvDebugOpen);
        tvDebugOpen.setText(Hawk.get(HawkConfig.DEBUG_OPEN, false) ? "开启" : "关闭");
        tvApi = findViewById(R.id.tvApi);
        refreshApiDisplay();
        tvHomeApi = findViewById(R.id.tvHomeApi);
        tvHomeApi.setText(getHomeSourceDisplayName());
        tvHomeShow = findViewById(R.id.tvHomeShow);
        tvHomeShow.setText(Hawk.get(HawkConfig.HOME_SHOW_SOURCE, false) ? "开启" : "关闭");
        tvHomeRec = findViewById(R.id.tvHomeRec);
        tvHomeRec.setText(getHomeRecName(Hawk.get(HawkConfig.HOME_REC, 0)));
        tvHomeNum = findViewById(R.id.tvHomeNum);
        tvHomeNum.setText(HistoryHelper.getHomeRecName(Hawk.get(HawkConfig.HOME_NUM, 0)));
        tvHomeDefaultShow = findViewById(R.id.tvHomeDefaultShow);
        tvHomeDefaultShow.setText(Hawk.get(HawkConfig.HOME_DEFAULT_SHOW, false) ? "开启" : "关闭");

        // 默认焦点: 首页数据源
        findViewById(R.id.llHomeApi).requestFocus();

        findViewById(R.id.llDebug).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                Hawk.put(HawkConfig.DEBUG_OPEN, !Hawk.get(HawkConfig.DEBUG_OPEN, false));
                tvDebugOpen.setText(Hawk.get(HawkConfig.DEBUG_OPEN, false) ? "开启" : "关闭");
            }
        });
        // 配置地址: 打开配置管理
        findViewById(R.id.llApi).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                jumpActivity(ConfigManageActivity.class);
            }
        });
        // 首页数据源
        findViewById(R.id.llHomeApi).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                List<SourceBean> sites = new ArrayList<>();
                for (SourceBean sb : ApiConfig.get().getSourceBeanList()) {
                    if (sb.getHide() == 0) sites.add(sb);
                }
                if (sites.size() > 0) {
                    SelectDialog<SourceBean> dialog = new SelectDialog<>(mActivity);
                    int spanCount = (int) Math.floor(sites.size() / 10);
                    if (spanCount <= 1) spanCount = 1;
                    if (spanCount >= 3) spanCount = 3;
                    TvRecyclerView tvRecyclerView = dialog.findViewById(R.id.list);
                    tvRecyclerView.setLayoutManager(new V7GridLayoutManager(dialog.getContext(), spanCount));
                    ConstraintLayout cl_root = dialog.findViewById(R.id.cl_root);
                    ViewGroup.LayoutParams clp = cl_root.getLayoutParams();
                    if (spanCount != 1) {
                        clp.width = AutoSizeUtils.mm2px(dialog.getContext(), 400 + 260 * (spanCount - 1));
                    }
                    dialog.setTip(getString(R.string.dia_source));
                    dialog.setAdapter(tvRecyclerView, new SelectDialogAdapter.SelectDialogInterface<SourceBean>() {
                        @Override
                        public void click(SourceBean value, int pos) {
                            ApiConfig.get().setSourceBean(value);
                            tvHomeApi.setText(getHomeSourceDisplayName());
                        }

                        @Override
                        public String getDisplay(SourceBean val) {
                            String configName = val.getConfigName();
                            if (configName != null && !configName.isEmpty()) {
                                return "[" + configName + "] " + val.getName();
                            }
                            return val.getName();
                        }
                    }, new DiffUtil.ItemCallback<SourceBean>() {
                        @Override
                        public boolean areItemsTheSame(@NonNull @NotNull SourceBean oldItem, @NonNull @NotNull SourceBean newItem) {
                            return oldItem == newItem;
                        }

                        @Override
                        public boolean areContentsTheSame(@NonNull @NotNull SourceBean oldItem, @NonNull @NotNull SourceBean newItem) {
                            return oldItem.getKey().equals(newItem.getKey());
                        }
                    }, sites, sites.indexOf(ApiConfig.get().getHomeSourceBean()));
                    dialog.show();
                }
            }
        });
        // 数据源显示开关
        findViewById(R.id.llHomeShow).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                Hawk.put(HawkConfig.HOME_SHOW_SOURCE, !Hawk.get(HawkConfig.HOME_SHOW_SOURCE, false));
                tvHomeShow.setText(Hawk.get(HawkConfig.HOME_SHOW_SOURCE, true) ? "开启" : "关闭");
            }
        });
        // 首页图标
        findViewById(R.id.llHomeIcon).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                HomeIconDialog dialog = new HomeIconDialog(mActivity);
                dialog.show();
            }
        });
        // 推荐类型
        findViewById(R.id.llHomeRec).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.HOME_REC, 0);
                ArrayList<Integer> types = new ArrayList<>();
                types.add(0);
                types.add(1);
                types.add(2);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip(getString(R.string.dia_hm_type));
                dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.HOME_REC, value);
                        tvHomeRec.setText(getHomeRecName(value));
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return getHomeRecName(val);
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
        // 历史条数
        findViewById(R.id.llHomeNum).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.HOME_NUM, 0);
                ArrayList<Integer> types = new ArrayList<>();
                types.add(0);
                types.add(1);
                types.add(2);
                types.add(3);
                types.add(4);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip(getString(R.string.dia_history));
                dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.HOME_NUM, value);
                        tvHomeNum.setText(HistoryHelper.getHomeRecName(value));
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return HistoryHelper.getHomeRecName(val);
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
        // 启动时进直播
        findViewById(R.id.llHomeLive).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                Hawk.put(HawkConfig.HOME_DEFAULT_SHOW, !Hawk.get(HawkConfig.HOME_DEFAULT_SHOW, false));
                tvHomeDefaultShow.setText(Hawk.get(HawkConfig.HOME_DEFAULT_SHOW, true) ? "开启" : "关闭");
            }
        });

        // 开发模式回调(连点直播入口开调试模式)
        SettingActivity.callback = new SettingActivity.DevModeCallback() {
            @Override
            public void onChange() {
                View debug = findViewById(R.id.llDebug);
                if (debug != null) debug.setVisibility(View.VISIBLE);
            }
        };
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        SettingActivity.callback = null;
    }
}