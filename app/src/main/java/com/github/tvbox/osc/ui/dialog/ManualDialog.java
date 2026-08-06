package com.github.tvbox.osc.ui.dialog;

import android.content.Context;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;

import org.jetbrains.annotations.NotNull;

/**
 * 首次使用手册: 告诉用户如何配置地址
 */
public class ManualDialog extends BaseDialog {

    public ManualDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_manual);
        setCanceledOnTouchOutside(false);
        ((android.widget.TextView) findViewById(R.id.manualContent)).setText(
                "欢迎使用 TVBox OS！\n\n" +
                "【第一步】配置数据源地址\n" +
                "设置 → 配置地址 → 配置管理：\n" +
                "· 点\"使用内置配置地址\"一键导入 3 个内置源\n" +
                "· 或点\"＋新增配置\"手动输入地址，如饭太硬、肥猫等接口\n\n" +
                "【第二步】启用与主页\n" +
                "· 每个配置右侧 ✓/✗ 可切换启用状态\n" +
                "· 点\"主页源\"把某配置设为主页显示的数据\n" +
                "· 多配置模式下主页数据源按配置分组展示\n\n" +
                "【第三步】开始使用\n" +
                "· 主页浏览分类与推荐，详情页可缓存下载\n" +
                "· 搜索支持拼音首字母，如 wzzs → 无职转生\n" +
                "· 直播界面点击画面呼出频道切换控制台\n" +
                "· 设置页分三页：主页 / 播放 / 系统，左右滑动切换");
        findViewById(R.id.btnManualKnow).setOnClickListener(v -> dismiss());
    }
}