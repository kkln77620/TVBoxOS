package com.github.tvbox.osc.bean;

/**
 * 配置地址条目 (多配置模式)
 * 支持: 名称 / 配置地址 / 直播地址 / EPG地址 / 代理 / 启用状态
 */
public class ConfigBean {
    public String id;       // 唯一标识(时间戳)
    public String name;     // 配置名称
    public String apiUrl;   // 配置地址(必填)
    public String liveUrl;  // 直播地址(可选)
    public String epgUrl;   // EPG地址(可选)
    public String proxy;    // 代理(可选)
    public boolean enabled; // 是否启用

    public ConfigBean() {
        this.id = String.valueOf(System.currentTimeMillis());
        this.enabled = true;
    }

    public ConfigBean(String name, String apiUrl) {
        this();
        this.name = name;
        this.apiUrl = apiUrl;
    }
}
