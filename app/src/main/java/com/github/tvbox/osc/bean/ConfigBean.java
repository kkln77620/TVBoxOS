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
        // UUID 保证唯一: 时间戳在毫秒级快速连续创建(如内置A/B/C批量导入)时会重复,
        // 导致启用/删除/主页标记只命中第一个, 出现三地址绑定问题
        this.id = java.util.UUID.randomUUID().toString();
        this.enabled = true;
    }

    public ConfigBean(String name, String apiUrl) {
        this();
        this.name = name;
        this.apiUrl = apiUrl;
    }
}
