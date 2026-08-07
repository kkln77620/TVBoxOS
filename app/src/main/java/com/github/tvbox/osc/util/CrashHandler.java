package com.github.tvbox.osc.util;

import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 全局崩溃捕获: 崩溃堆栈自动写入文件
 * 保存路径: /sdcard/Download/tvbox_crash/crash_时间戳.log
 * 设置页"悬浮日志"区可开关(默认开启)
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static volatile CrashHandler instance;
    private final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    private boolean enabled = true;

    public static CrashHandler getInstance() {
        if (instance == null) {
            synchronized (CrashHandler.class) {
                if (instance == null) instance = new CrashHandler();
            }
        }
        return instance;
    }

    public void init() {
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    public void setEnabled(boolean on) {
        this.enabled = on;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public static String getCrashDir() {
        return new File(Environment.getExternalStorageDirectory(), "Download/tvbox_crash").getAbsolutePath();
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        if (enabled) {
            try {
                writeCrashLog(t, e);
            } catch (Throwable ignored) {
            }
        }
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(t, e);
        } else {
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    private void writeCrashLog(Thread t, Throwable e) {
        File dir = new File(Environment.getExternalStorageDirectory(), "Download/tvbox_crash");
        if (!dir.exists()) dir.mkdirs();
        File f = new File(dir, "crash_" + System.currentTimeMillis() + ".log");
        StringBuilder sb = new StringBuilder();
        sb.append("======== TVBox OS 崩溃日志 ========\n");
        sb.append("时间: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date())).append('\n');
        sb.append("线程: ").append(t != null ? t.getName() : "unknown").append('\n');
        sb.append("异常: ").append(e != null ? e.toString() : "null").append('\n');
        if (e != null) {
            for (StackTraceElement el : e.getStackTrace()) {
                sb.append("  at ").append(el.toString()).append('\n');
            }
            Throwable cause = e.getCause();
            while (cause != null) {
                sb.append("Caused by: ").append(cause.toString()).append('\n');
                for (StackTraceElement el : cause.getStackTrace()) {
                    sb.append("  at ").append(el.toString()).append('\n');
                }
                cause = cause.getCause();
            }
        }
        sb.append("====================================\n");
        try {
            FileOutputStream fos = new FileOutputStream(f);
            try {
                fos.write(sb.toString().getBytes("UTF-8"));
                fos.flush();
            } finally {
                fos.close();
            }
        } catch (Exception ignored) {
            // 崩溃处理中不能再抛异常, 写失败直接忽略
        }
    }
}