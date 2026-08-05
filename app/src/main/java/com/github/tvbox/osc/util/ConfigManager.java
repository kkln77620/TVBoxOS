package com.github.tvbox.osc.util;

import com.github.tvbox.osc.bean.ConfigBean;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.R;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.List;

/**
 * 多配置地址管理器
 * 支持: 新增 / 编辑 / 删除 / 启停 / 旧配置迁移
 */
public class ConfigManager {

    private static final String KEY_CONFIG_LIST = "config_list";
    private static final String KEY_HOME_CONFIG_ID = "home_config_id";

    /**
     * 设置主页配置(该配置的数据显示在主页)
     */
    public static void setHomeConfigId(String id) {
        Hawk.put(KEY_HOME_CONFIG_ID, id == null ? "" : id);
        // 同步旧版API_URL: 使设置页"配置地址"显示与主页配置一致
        syncLegacyApiUrl();
    }

    /**
     * 读取主页配置ID
     */
    public static String getHomeConfigId() {
        String id = Hawk.get(KEY_HOME_CONFIG_ID, "");
        return id == null ? "" : id;
    }

    /**
     * 同步旧版API_URL: 与当前第一条启用配置保持一致
     * (搜索源选择、配置加载等依赖API_URL的逻辑保证一致)
     */
    private static void syncLegacyApiUrl() {
        List<ConfigBean> enabled = getEnabledConfigs();
        String api = enabled.isEmpty() ? "" : enabled.get(0).apiUrl;
        if (api == null) api = "";
        Hawk.put(HawkConfig.API_URL, api.trim());
    }

    /**
     * 读取全部配置
     */
    public static List<ConfigBean> getConfigs() {
        List<ConfigBean> list = Hawk.get(KEY_CONFIG_LIST, new ArrayList<ConfigBean>());
        if (list == null) list = new ArrayList<>();
        return list;
    }

    /**
     * 保存配置列表
     */
    public static void saveConfigs(List<ConfigBean> list) {
        if (list == null) list = new ArrayList<>();
        Hawk.put(KEY_CONFIG_LIST, list);
        syncLegacyApiUrl();
    }

    /**
     * 新增配置
     */
    public static void addConfig(ConfigBean bean) {
        List<ConfigBean> list = getConfigs();
        list.add(bean);
        saveConfigs(list);
    }

    /**
     * 更新配置(按id)
     */
    public static void updateConfig(ConfigBean bean) {
        List<ConfigBean> list = getConfigs();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id != null && list.get(i).id.equals(bean.id)) {
                list.set(i, bean);
                break;
            }
        }
        saveConfigs(list);
    }

    /**
     * 删除配置(按id)
     */
    public static void deleteConfig(String id) {
        List<ConfigBean> list = getConfigs();
        list.removeIf(cb -> cb.id != null && cb.id.equals(id));
        // 删除的是主页配置: 清除主页标记
        if (id != null && id.equals(getHomeConfigId())) {
            setHomeConfigId("");
        }
        saveConfigs(list);
    }

    /**
     * 切换启用状态
     */
    public static void toggleEnabled(String id, boolean enabled) {
        List<ConfigBean> list = getConfigs();
        for (ConfigBean cb : list) {
            if (cb.id != null && cb.id.equals(id)) {
                cb.enabled = enabled;
                break;
            }
        }
        saveConfigs(list);
    }

    /**
     * 获取启用的配置(主页配置优先排在最前)
     */
    public static List<ConfigBean> getEnabledConfigs() {
        List<ConfigBean> list = new ArrayList<>();
        for (ConfigBean cb : getConfigs()) {
            if (cb.enabled && cb.apiUrl != null && !cb.apiUrl.trim().isEmpty()) {
                list.add(cb);
            }
        }
        // 主页配置优先
        String homeId = getHomeConfigId();
        if (!homeId.isEmpty()) {
            for (int i = 0; i < list.size(); i++) {
                if (homeId.equals(list.get(i).id)) {
                    ConfigBean home = list.remove(i);
                    list.add(0, home);
                    break;
                }
            }
        }
        return list;
    }

    /**
     * 首次迁移: 旧版单一API_URL -> 配置列表
     */
    public static void migrateFromLegacy() {
        List<ConfigBean> list = getConfigs();
        if (!list.isEmpty()) return;
        String legacyApi = Hawk.get(HawkConfig.API_URL, App.getInstance().getString(R.string.app_source));
        String legacyLive = Hawk.get(HawkConfig.LIVE_URL, "");
        String legacyEpg = Hawk.get(HawkConfig.EPG_URL, "");
        if (legacyApi == null || legacyApi.trim().isEmpty()) return;
        ConfigBean bean = new ConfigBean("默认配置", legacyApi.trim());
        bean.liveUrl = legacyLive;
        bean.epgUrl = legacyEpg;
        list.add(bean);
        saveConfigs(list);
    }
}