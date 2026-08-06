package com.github.tvbox.osc.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.util.ImgUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 缓存 A 页: 详情页点击"缓存"弹出
 * 显示 缩略图+名称 / 画质选择 / 线路(源)选择 / 集数列表
 * 点击集数开始缓存, 弹窗不关闭, 退出才消失
 */
public class CacheDialog extends Dialog {

    public interface CacheCallback {
        void onCache(String url, String name, String pic, long bwPref);
    }

    private final VodInfo vodInfo;
    private final String sourceKey;
    private final String pic;
    private final CacheCallback callback;
    private String currentFlag;
    private long bandwidthPref = 0;
    private LinearLayout bwRow;
    private LinearLayout flagRow;
    private RecyclerView epList;
    private EpAdapter epAdapter;
    private TextView tvTitle;

    // 画质档位: 名称 / 码率bps(0=自动最小)
    private static final String[][] BW_LEVELS = {
            {"自动", "0"},
            {"流畅", "800000"},
            {"标清", "1500000"},
            {"高清", "2500000"}
    };

    public CacheDialog(android.app.Activity activity, VodInfo info, String key, String picUrl, CacheCallback cb) {
        super(activity, R.style.CustomDialogStyleDim);
        this.vodInfo = info;
        this.sourceKey = key;
        this.pic = picUrl;
        this.callback = cb;
        setContentView(R.layout.dialog_cache);
        setCanceledOnTouchOutside(true);
        initViews();
        refreshFlagState();
    }

    private void initViews() {
        tvTitle = findViewById(R.id.tvCacheTitle);
        tvTitle.setText(vodInfo != null && vodInfo.name != null ? vodInfo.name : "视频");
        ImageView ivPic = findViewById(R.id.ivCachePic);
        if (pic != null && !pic.isEmpty()) {
            ImgUtil.load(pic, ivPic, 14);
        } else {
            ivPic.setImageResource(R.drawable.img_loading_placeholder);
        }
        bwRow = findViewById(R.id.bwRow);
        flagRow = findViewById(R.id.flagRow);
        epList = findViewById(R.id.epList);
        // 画质按钮
        for (String[] lv : BW_LEVELS) {
            TextView btn = makeBtn(lv[0]);
            btn.setTag(lv[1]);
            btn.setOnClickListener(v -> {
                bandwidthPref = Long.parseLong((String) v.getTag());
                refreshBwState();
                Toast.makeText(getContext(), "画质: " + ((TextView) v).getText() + (bandwidthPref > 0 ? " (" + (bandwidthPref / 1000) + "kbps)" : ""), Toast.LENGTH_SHORT).show();
            });
            bwRow.addView(btn, lp());
        }
        refreshBwState();
        // 线路按钮
        if (vodInfo != null && vodInfo.seriesFlags != null) {
            for (VodInfo.VodSeriesFlag flag : vodInfo.seriesFlags) {
                TextView btn = makeBtn(flag.name);
                btn.setTag(flag.name);
                btn.setOnClickListener(v -> {
                    currentFlag = (String) v.getTag();
                    refreshFlagState();
                });
                flagRow.addView(btn, lp());
            }
        }
        // 集数
        epList.setLayoutManager(new GridLayoutManager(getContext(), 6));
        epAdapter = new EpAdapter(new ArrayList<>());
        epList.setAdapter(epAdapter);
    }

    private void refreshBwState() {
        for (int i = 0; i < bwRow.getChildCount(); i++) {
            TextView btn = (TextView) bwRow.getChildAt(i);
            long pref = Long.parseLong((String) btn.getTag());
            btn.setTextColor(pref == bandwidthPref ? Color.parseColor("#FFB800") : Color.WHITE);
            btn.setBackgroundResource(pref == bandwidthPref ? R.drawable.button_dialog_vod : R.drawable.shape_setting_model_focus);
        }
    }

    private void refreshFlagState() {
        if (currentFlag == null && vodInfo != null) {
            currentFlag = vodInfo.playFlag;
            if (currentFlag == null && vodInfo.seriesFlags != null && !vodInfo.seriesFlags.isEmpty()) {
                currentFlag = vodInfo.seriesFlags.get(0).name;
            }
        }
        // 高亮当前线路
        for (int i = 0; i < flagRow.getChildCount(); i++) {
            TextView btn = (TextView) flagRow.getChildAt(i);
            boolean sel = btn.getTag().equals(currentFlag);
            btn.setTextColor(sel ? Color.parseColor("#FFB800") : Color.WHITE);
            btn.setBackgroundResource(sel ? R.drawable.button_dialog_vod : R.drawable.shape_setting_model_focus);
        }
        // 集数列表
        List<VodInfo.VodSeries> list = new ArrayList<>();
        if (vodInfo != null && vodInfo.seriesMap != null && currentFlag != null) {
            List<VodInfo.VodSeries> src = vodInfo.seriesMap.get(currentFlag);
            if (src != null) list.addAll(src);
        }
        epAdapter.setNewData(list);
        if (vodInfo != null) {
            tvTitle.setText(vodInfo.name != null ? vodInfo.name : "视频");
        }
    }

    private TextView makeBtn(String text) {
        TextView btn = new TextView(getContext());
        btn.setText(text);
        btn.setTextSize(18);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(14), dp(8), dp(14), dp(8));
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundResource(R.drawable.shape_setting_model_focus);
        return btn;
    }

    private LinearLayout.LayoutParams lp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(10);
        return lp;
    }

    private int dp(int v) {
        return (int) (getContext().getResources().getDisplayMetrics().density * v + 0.5f);
    }

    class EpAdapter extends BaseQuickAdapter<VodInfo.VodSeries, BaseViewHolder> {
        EpAdapter(List<VodInfo.VodSeries> data) {
            super(android.R.layout.simple_list_item_1, data);
        }

        @Override
        protected void convert(BaseViewHolder helper, VodInfo.VodSeries item) {
            TextView tv = helper.getView(android.R.id.text1);
            tv.setText(item.name != null ? item.name : "");
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(16);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(dp(6), dp(8), dp(6), dp(8));
            tv.setBackgroundResource(R.drawable.shape_setting_model_focus);
            tv.setOnClickListener(v -> {
                if (item.url == null || item.url.isEmpty()) {
                    Toast.makeText(getContext(), "该集无播放地址", Toast.LENGTH_SHORT).show();
                    return;
                }
                String name = vodInfo.name + " " + (item.name != null ? item.name : "");
                if (callback != null) {
                    callback.onCache(item.url, name, pic, bandwidthPref);
                }
                Toast.makeText(getContext(), "已加入缓存: " + name, Toast.LENGTH_SHORT).show();
            });
        }
    }
}
