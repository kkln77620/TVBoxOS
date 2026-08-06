package com.github.tvbox.osc.bean;

import java.io.Serializable;
import java.util.HashMap;

/**
 * 缓存下载任务
 */
public class CacheTask implements Serializable {
    public static final int STATE_WAIT = 0;      // 等待
    public static final int STATE_DOWNLOADING = 1; // 下载中
    public static final int STATE_DONE = 2;      // 已完成
    public static final int STATE_FAILED = 3;    // 失败
    public String id;
    public String name;      // 任务名称(片名+集数)
    public String url;       // 下载地址
    public String filePath;  // 保存路径
    public int state = STATE_WAIT;
    public long progress = 0; // 已下载字节
    public long total = 0;   // 总字节(0表示未知)
    public String errorMsg;  // 失败原因(仅失败时有效)
    public HashMap<String, String> headers; // 下载请求头(UA/Referer/Cookie等, 复用播放参数)

    public CacheTask() {
    }

    public CacheTask(String id, String name, String url, String filePath) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.filePath = filePath;
    }
}