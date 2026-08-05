package com.github.tvbox.osc.util;

/**
 * GitHub 更新检测工具类
 * 基于 GitHub Releases API: GET /repos/{owner}/{repo}/releases/latest
 * Release 的 tag 名称即版本号(如 1.0.20260805_1500), 附件第一个 APK 为下载地址
 */
public class Github {
    // 仓库信息: 在此配置你的 GitHub 仓库
    public static final String OWNER = "kkln77620";
    public static final String REPO = "TVBoxOS";

    /**
     * 最新 Release 信息接口(无需令牌, 公开仓库可访问)
     */
    public static String getLatestReleaseApi() {
        return "https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases/latest";
    }

    /**
     * 兼容旧引用: 返回版本检测 JSON(已废弃, 返回最新Release API)
     */
    public static String getJson() {
        return getLatestReleaseApi();
    }

    /**
     * 兼容旧引用: APK 下载地址(由 Release assets 动态获取)
     */
    public static String getApk() {
        return "";
    }
}
