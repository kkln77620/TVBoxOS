package com.github.tvbox.osc.ui.fragment;

import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.player.thirdparty.Kodi;
import com.github.tvbox.osc.player.thirdparty.MXPlayer;
import com.github.tvbox.osc.player.thirdparty.ReexPlayer;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.dialog.DanmuUrlDialog;
import com.github.tvbox.osc.ui.dialog.MediaSettingDialog;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HawkUtils;
import com.github.tvbox.osc.util.PlayerHelper;
import com.orhanobut.hawk.Hawk;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * 设置-播放页: 窗口预览/画面缩放/后台播放/播放器/解码设置/广告过滤
 */
public class PlaySettingFragment extends BaseLazyFragment {

    private TextView tvShowPreviewText;
    private TextView tvScale;
    private TextView tvPlay;
    private TextView tvVideoPurifyText;

    public static PlaySettingFragment newInstance() {
        return new PlaySettingFragment();
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_player;
    }

    @Override
    protected void init() {
        tvShowPreviewText = findViewById(R.id.showPreviewText);
        tvShowPreviewText.setText(Hawk.get(HawkConfig.SHOW_PREVIEW, true) ? "开启" : "关闭");
        tvScale = findViewById(R.id.tvScaleType);
        tvScale.setText(PlayerHelper.getScaleName(Hawk.get(HawkConfig.PLAY_SCALE, 0)));
        tvPlay = findViewById(R.id.tvPlay);
        tvPlay.setText(PlayerHelper.getPlayerName(Hawk.get(HawkConfig.PLAY_TYPE, 0)));
        tvVideoPurifyText = findViewById(R.id.tvVideoPurifyText);
        tvVideoPurifyText.setText(Hawk.get(HawkConfig.VIDEO_PURIFY, true) ? "开启" : "关闭");
        // 缓存启用: 点击弹警告二级菜单, 用户确认才开启(缓存存在大量BUG风险)
        TextView tvCacheEnableText = findViewById(R.id.tvCacheEnableText);
        tvCacheEnableText.setText(Hawk.get(HawkConfig.CACHE_ENABLE, false) ? "已开启" : "已关闭");
        findViewById(R.id.llCacheEnable).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            boolean now = Hawk.get(HawkConfig.CACHE_ENABLE, false);
            if (now) {
                // 已开启: 点击直接关闭
                Hawk.put(HawkConfig.CACHE_ENABLE, false);
                tvCacheEnableText.setText("已关闭");
                Toast.makeText(mContext, "缓存功能已关闭", Toast.LENGTH_SHORT).show();
                return;
            }
            // 未开启: 警告二级菜单
            new android.app.AlertDialog.Builder(mContext)
                    .setTitle("⚠️ 缓存功能警告")
                    .setMessage("缓存功能存在大量BUG，可能出现闪退、报错、文件损坏等问题，是否仍要开启？")
                    .setPositiveButton("仍要开启", (d, w) -> {
                        Hawk.put(HawkConfig.CACHE_ENABLE, true);
                        tvCacheEnableText.setText("已开启");
                        Toast.makeText(mContext, "缓存功能已开启, 使用中如遇异常请反馈", Toast.LENGTH_LONG).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
        // 播放自动缓存: 暂未开放(禁用态)
        findViewById(R.id.llAutoCache).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            Toast.makeText(mContext, "播放自动缓存暂未开放, 敬请期待", Toast.LENGTH_SHORT).show();
        });
        // 弹幕地址
        TextView tvDanmuUrl = findViewById(R.id.tvDanmuUrl);
        tvDanmuUrl.setText(TextUtils.isEmpty(HawkUtils.getDanmuUrl()) ? "未设置" : HawkUtils.getDanmuUrl());
        findViewById(R.id.llDanmu).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            new DanmuUrlDialog(getContext(), HawkUtils.getDanmuUrl(), url -> {
                HawkUtils.setDanmuUrl(url);
                tvDanmuUrl.setText(TextUtils.isEmpty(url) ? "未设置" : url);
            }).show();
        });

        // 窗口预览
        findViewById(R.id.showPreview).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                Hawk.put(HawkConfig.SHOW_PREVIEW, !Hawk.get(HawkConfig.SHOW_PREVIEW, true));
                tvShowPreviewText.setText(Hawk.get(HawkConfig.SHOW_PREVIEW, true) ? "开启" : "关闭");
            }
        });
        // 画面缩放
        findViewById(R.id.llScale).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.PLAY_SCALE, 0);
                ArrayList<Integer> players = new ArrayList<>();
                players.add(0);
                players.add(1);
                players.add(2);
                players.add(3);
                players.add(4);
                players.add(5);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip(getString(R.string.dia_ratio));
                dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.PLAY_SCALE, value);
                        tvScale.setText(PlayerHelper.getScaleName(value));
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return PlayerHelper.getScaleName(val);
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
                }, players, defaultPos);
                dialog.show();
            }
        });
        // 后台播放
        View backgroundPlay = findViewById(R.id.llBackgroundPlay);
        TextView tvBgPlayType = findViewById(R.id.tvBackgroundPlayType);
        Integer defaultBgPlayTypePos = Hawk.get(HawkConfig.BACKGROUND_PLAY_TYPE, 0);
        ArrayList<String> bgPlayTypes = new ArrayList<>();
        bgPlayTypes.add("关闭");
        bgPlayTypes.add("开启");
        bgPlayTypes.add("画中画");
        tvBgPlayType.setText(bgPlayTypes.get(defaultBgPlayTypePos));
        backgroundPlay.setOnClickListener(view -> {
            FastClickCheckUtil.check(view);
            int bgPlayTypePos = Hawk.get(HawkConfig.BACKGROUND_PLAY_TYPE, 0);
            SelectDialog<String> dialog = new SelectDialog<>(mActivity);
            dialog.setTip("请选择");
            dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<String>() {
                @Override
                public void click(String value, int pos) {
                    tvBgPlayType.setText(value);
                    Hawk.put(HawkConfig.BACKGROUND_PLAY_TYPE, pos);
                }

                @Override
                public String getDisplay(String val) {
                    return val;
                }
            }, new DiffUtil.ItemCallback<String>() {
                @Override
                public boolean areItemsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
                    return oldItem.equals(newItem);
                }

                @Override
                public boolean areContentsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
                    return oldItem.equals(newItem);
                }
            }, bgPlayTypes, bgPlayTypePos);
            dialog.show();
        });
        // 播放器
        findViewById(R.id.llPlay).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.PLAY_TYPE, 0);
                ArrayList<Integer> players = new ArrayList<>();
                players.add(0);
                players.add(1);
                players.add(2);
                players.add(3);
                players.add(4); // VLC 内置内核
                if (MXPlayer.getPackageInfo() != null) {
                    players.add(10);
                }
                if (ReexPlayer.getPackageInfo() != null) {
                    players.add(11);
                }
                if (Kodi.getPackageInfo() != null) {
                    players.add(12);
                }
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip(getString(R.string.dia_player));
                dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.PLAY_TYPE, value);
                        tvPlay.setText(PlayerHelper.getPlayerName(value));
                        PlayerHelper.init();
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return PlayerHelper.getPlayerName(val);
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
                }, players, defaultPos);
                dialog.show();
            }
        });
        // 解码设置
        findViewById(R.id.llMediaSetting).setOnClickListener(view -> {
            FastClickCheckUtil.check(view);
            MediaSettingDialog mediaSettingDialog = new MediaSettingDialog(view.getContext());
            mediaSettingDialog.show();
        });
        // 广告过滤
        findViewById(R.id.llVideoPurify).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            Hawk.put(HawkConfig.VIDEO_PURIFY, !Hawk.get(HawkConfig.VIDEO_PURIFY, true));
            tvVideoPurifyText.setText(Hawk.get(HawkConfig.VIDEO_PURIFY, true) ? "开启" : "关闭");
        });
    }
}