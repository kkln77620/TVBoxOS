package com.github.tvbox.osc.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.github.tvbox.osc.base.App;

public class ToastHelper {

    /**
     * 全局Toast: 使用Application Context, 自动切回主线程
     */
    public static void show(String text) {
        Context context = App.getInstance();
        if (context == null) return;
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(context.getApplicationContext(), text, Toast.LENGTH_SHORT).show());
    }

    public static void showToast(Context context, String text) {
        new Thread(new Runnable() {
            public void run() {
                Looper.prepare();
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
                Looper.loop();
            }
        }).start();
    }

    public static void debugToast(Context context, String text) {
        if (HawkConfig.isDebug()) {
            showToast(context, text);
        }
    }
}