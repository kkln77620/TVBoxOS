package com.github.tvbox.osc.ui.activity;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.viewpager.widget.ViewPager;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.ui.adapter.SettingMenuAdapter;
import com.github.tvbox.osc.ui.adapter.SettingPageAdapter;
import com.github.tvbox.osc.ui.fragment.HomeSettingFragment;
import com.github.tvbox.osc.ui.fragment.PlaySettingFragment;
import com.github.tvbox.osc.ui.fragment.SystemSettingFragment;
import com.github.tvbox.osc.util.AppManager;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import java.util.ArrayList;
import java.util.List;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class SettingActivity extends BaseActivity {
    private TvRecyclerView mGridView;
    private ViewPager mViewPager;
    private SettingMenuAdapter sortAdapter;
    private SettingPageAdapter pageAdapter;
    private List<BaseLazyFragment> fragments = new ArrayList<>();
    private boolean sortChange = false;
    private int defaultSelected = 0;
    private int sortFocused = 0;
    private Handler mHandler = new Handler();
    private String homeSourceKey;
    private String currentApi;
    private String currentLive;
    private int homeRec;
    private int dnsOpt;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_setting;
    }

    @Override
    protected void init() {
        initView();
        initData();
    }

    private void initView() {
        mGridView = findViewById(R.id.mGridView);
        mViewPager = findViewById(R.id.mViewPager);
        sortAdapter = new SettingMenuAdapter();
        mGridView.setAdapter(sortAdapter);
        mGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        // 底部页数指示器在 initData 后初始化(需先有页面数量)
        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                // 丝滑过渡: 旧点缩小 / 新点放大(按滑动比例插值)
                updateIndicator(position, positionOffset);
            }

            @Override
            public void onPageSelected(int position) {
                // 左栏同步突显当前页
                if (mGridView != null && mGridView.getAdapter() != null && position >= 0 && position < mGridView.getAdapter().getItemCount()) {
                    mGridView.setSelectedPosition(position);
                    mGridView.smoothScrollToPosition(position);
                    // 手动高亮文字颜色(确保左栏菜单同步突显)
                    highlightMenu(position);
                }
                // 指示器复位(点击左栏无滑动动画时也正确)
                updateIndicator(position, 0f);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });
        sortAdapter.setOnItemChildClickListener(new BaseQuickAdapter.OnItemChildClickListener() {
            @Override
            public void onItemChildClick(BaseQuickAdapter adapter, View view, int position) {
                if (view.getId() == R.id.tvName) {
                    if (view.getParent() != null) {
                        ((ViewGroup) view.getParent()).requestFocus();
                        sortFocused = position;
                        if (sortFocused != defaultSelected) {
                            defaultSelected = sortFocused;
                            // 带滑动过渡动画切换页面
                            mViewPager.setCurrentItem(sortFocused, true);
                        }
                    }
                }
            }
        });
        mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                if (itemView != null) {
                    TextView tvName = itemView.findViewById(R.id.tvName);
                    tvName.setTextColor(getResources().getColor(R.color.color_FFFFFF_70));
                }
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                if (itemView != null) {
                    sortChange = true;
                    sortFocused = position;
                    TextView tvName = itemView.findViewById(R.id.tvName);
                    tvName.setTextColor(Color.WHITE);
                }
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {

            }
        });
    }

    private void initData() {
        currentApi = Hawk.get(HawkConfig.API_URL, "");
        currentLive = Hawk.get(HawkConfig.LIVE_URL, "");
        homeSourceKey = ApiConfig.get().getHomeSourceBean().getKey();
        homeRec = Hawk.get(HawkConfig.HOME_REC, 0);
        dnsOpt = Hawk.get(HawkConfig.DOH_URL, 0);
        // 可滑动的多页设置菜单
        List<String> sortList = new ArrayList<>();
        sortList.add("主页设置");
        sortList.add("播放设置");
        sortList.add("系统设置");
        sortAdapter.setNewData(sortList);
        initViewPager();
    }

    private void initViewPager() {
        fragments.add(HomeSettingFragment.newInstance());
        fragments.add(PlaySettingFragment.newInstance());
        fragments.add(SystemSettingFragment.newInstance());
        pageAdapter = new SettingPageAdapter(getSupportFragmentManager(), fragments);
        mViewPager.setAdapter(pageAdapter);
        mViewPager.setCurrentItem(0);
        // 底部页数指示器: 按页数生成白点
        initIndicator();
    }

    // ===== 底部页数指示器 =====
    private final List<View> indicatorDots = new ArrayList<>();
    private static final float DOT_SMALL = 6f;   // 普通点半径dp
    private static final float DOT_LARGE = 10f;  // 当前点放大半径dp

    private void initIndicator() {
        LinearLayout ll = findViewById(R.id.llIndicator);
        if (ll == null) return;
        ll.removeAllViews();
        indicatorDots.clear();
        int pageCount = fragments.size();
        for (int i = 0; i < pageCount; i++) {
            View dot = new View(this);
            dot.setBackgroundResource(R.drawable.shape_dot_indicator);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(DOT_SMALL * 2), dp(DOT_SMALL * 2));
            lp.rightMargin = dp(6);
            dot.setLayoutParams(lp);
            dot.setAlpha(0.5f);
            ll.addView(dot);
            indicatorDots.add(dot);
        }
        if (!indicatorDots.isEmpty()) {
            updateIndicator(0, 0f);
        }
    }

    /**
     * 滑动插值: position为当前页, positionOffset 0~1 为滑动比例
     * 当前页点随滑动缩小, 下一页点随滑动放大(丝滑过渡)
     */
    private void updateIndicator(int position, float positionOffset) {
        int count = indicatorDots.size();
        if (count == 0) return;
        if (position < 0 || position >= count) return;
        View cur = indicatorDots.get(position);
        // 当前页: 由大变小(滑走) / 由小变大(滑入)
        float curSize = DOT_LARGE + (DOT_SMALL - DOT_LARGE) * positionOffset;
        float curAlpha = 1f - 0.5f * positionOffset;
        applyDot(cur, curSize, curAlpha);
        // 下一页: 由小变大
        if (positionOffset > 0 && position + 1 < count) {
            View next = indicatorDots.get(position + 1);
            float nextSize = DOT_SMALL + (DOT_LARGE - DOT_SMALL) * positionOffset;
            float nextAlpha = 0.5f + 0.5f * positionOffset;
            applyDot(next, nextSize, nextAlpha);
        }
    }

    private void applyDot(View dot, float radiusDp, float alpha) {
        if (dot == null) return;
        ViewGroup.LayoutParams lp = dot.getLayoutParams();
        if (lp != null) {
            lp.width = dp(radiusDp * 2);
            lp.height = dp(radiusDp * 2);
            dot.setLayoutParams(lp);
        }
        dot.setAlpha(Math.max(0.2f, Math.min(1f, alpha)));
    }

    private int dp(float v) {
        return (int) (getResources().getDisplayMetrics().density * v);
    }

    /**
     * 左栏菜单文字高亮: 当前页白色, 其他页半透明
     */
    private void highlightMenu(int position) {
        try {
            int count = mGridView.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = mGridView.getChildAt(i);
                if (child == null) continue;
                TextView tvName = child.findViewById(R.id.tvName);
                if (tvName == null) continue;
                int pos = mGridView.getChildAdapterPosition(child);
                tvName.setTextColor(getResources().getColor(pos == position ? R.color.color_FFFFFF : R.color.color_FFFFFF_70));
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    private final Runnable mDataRunnable = new Runnable() {
        @Override
        public void run() {
            if (sortChange) {
                sortChange = false;
                if (sortFocused != defaultSelected) {
                    defaultSelected = sortFocused;
                    mViewPager.setCurrentItem(sortFocused, false);
                }
            }
        }
    };

    private final Runnable mDevModeRun = new Runnable() {
        @Override
        public void run() {
            devMode = "";
        }
    };


    public interface DevModeCallback {
        void onChange();
    }

    public static DevModeCallback callback = null;

    String devMode = "";

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            mHandler.removeCallbacks(mDataRunnable);
            int keyCode = event.getKeyCode();
            switch (keyCode) {
                case KeyEvent.KEYCODE_0:
                    mHandler.removeCallbacks(mDevModeRun);
                    devMode += "0";
                    mHandler.postDelayed(mDevModeRun, 200);
                    if (devMode.length() >= 4) {
                        if (callback != null) {
                            callback.onChange();
                        }
                    }
                    break;
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            mHandler.postDelayed(mDataRunnable, 200);
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if ((homeSourceKey != null && !homeSourceKey.equals(Hawk.get(HawkConfig.HOME_API, ""))) ||
                !currentApi.equals(Hawk.get(HawkConfig.API_URL, "")) || !currentLive.equals(Hawk.get(HawkConfig.LIVE_URL, "")) ||
                homeRec != Hawk.get(HawkConfig.HOME_REC, 0) ||
                dnsOpt != Hawk.get(HawkConfig.DOH_URL, 0)) {
            AppManager.getInstance().finishAllActivity();
            if (currentApi.equals(Hawk.get(HawkConfig.API_URL, "")) & (currentLive.equals(Hawk.get(HawkConfig.LIVE_URL, "")))) {
                Bundle bundle = new Bundle();
                bundle.putBoolean("useCache", true);
                jumpActivity(HomeActivity.class, bundle);
            } else {
                jumpActivity(HomeActivity.class);
            }
        } else {
            super.onBackPressed();
        }
    }
}