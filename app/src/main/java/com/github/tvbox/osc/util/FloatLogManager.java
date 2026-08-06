package com.github.tvbox.osc.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 悬浮日志窗: 观察所有 Activity 活动与关键事件, 可拖动, 设置页开关
 */
public class FloatLogManager {

    private static final int MAX_LOG = 6000;

    private static volatile FloatLogManager instance;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final StringBuilder logs = new StringBuilder();
    private WindowManager wm;
    private View floatView;
    private TextView logView;
    private TextView titleView;
    private String currentPage = "";
    private boolean showing = false;
    private int startX, startY, startTouchX, startTouchY;

    public static FloatLogManager getInstance() {
        if (instance == null) {
            synchronized (FloatLogManager.class) {
                if (instance == null) instance = new FloatLogManager();
            }
        }
        return instance;
    }

    public boolean isShowing() {
        return showing;
    }

    /**
     * 设置当前所在页面(顶栏显示)
     */
    public void setCurrentPage(String page) {
        currentPage = page == null ? "" : page;
        if (showing && titleView != null) {
            mainHandler.post(() -> {
                if (titleView != null) updateTitle();
            });
        }
    }

    private void updateTitle() {
        if (titleView != null) {
            titleView.setText(currentPage.isEmpty() ? "悬浮日志" : "悬浮日志 · 当前页:" + currentPage);
        }
    }

    /**
     * 一键复制全部日志到剪贴板
     */
    public void copyLogs(Context context) {
        String text = getLogText();
        if (text.isEmpty()) text = "暂无日志";
        try {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("float_log", text));
            android.widget.Toast.makeText(context, "日志已复制(" + text.length() + "字)", android.widget.Toast.LENGTH_SHORT).show();
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    /**
     * 记录日志(未开启时也缓存, 开启后可见最近记录)
     */
    public void append(String line) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        synchronized (logs) {
            logs.append("[").append(time).append("] ").append(line).append("\n");
            if (logs.length() > MAX_LOG) {
                logs.delete(0, logs.length() - MAX_LOG);
            }
        }
        if (showing && logView != null) {
            mainHandler.post(() -> {
                if (logView != null) {
                    logView.setText(getLogText());
                    ((ScrollView) logView.getParent()).fullScroll(View.FOCUS_DOWN);
                }
            });
        }
    }

    private String getLogText() {
        synchronized (logs) {
            return logs.toString();
        }
    }

    public void clear() {
        synchronized (logs) {
            logs.setLength(0);
        }
        if (showing && logView != null) {
            mainHandler.post(() -> {
                if (logView != null) logView.setText("");
            });
        }
    }

    public void show(Context context) {
        if (showing) return;
        final Context app = context.getApplicationContext();
        wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;

        int dp = (int) (app.getResources().getDisplayMetrics().density + 0.5f);
        LinearLayout root = new LinearLayout(app);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xCC101010);
        root.setPadding(dp * 8, dp * 4, dp * 8, dp * 4);

        // 顶栏: 标题(当前页) + 复制 + 清空 + 关闭
        LinearLayout bar = new LinearLayout(app);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        titleView = new TextView(app);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(11);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        bar.addView(titleView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        updateTitle();
        TextView btnCopy = new TextView(app);
        btnCopy.setText("复制");
        btnCopy.setTextColor(Color.parseColor("#B9F6CA"));
        btnCopy.setTextSize(11);
        btnCopy.setPadding(dp * 6, dp * 2, dp * 6, dp * 2);
        btnCopy.setOnClickListener(v -> copyLogs(app));
        bar.addView(btnCopy);
        TextView btnClear = new TextView(app);
        btnClear.setText("清空");
        btnClear.setTextColor(Color.parseColor("#80D8FF"));
        btnClear.setTextSize(11);
        btnClear.setPadding(dp * 6, dp * 2, dp * 6, dp * 2);
        btnClear.setOnClickListener(v -> clear());
        bar.addView(btnClear);
        TextView btnClose = new TextView(app);
        btnClose.setText("关闭");
        btnClose.setTextColor(Color.parseColor("#FF8A80"));
        btnClose.setTextSize(11);
        btnClose.setPadding(dp * 6, dp * 2, dp * 6, dp * 2);
        btnClose.setOnClickListener(v -> hide());
        bar.addView(btnClose);
        root.addView(bar);

        // 日志区
        logView = new TextView(app);
        logView.setTextColor(Color.WHITE);
        logView.setTextSize(9);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setLineSpacing(0, 1.0f);
        ScrollView sv = new ScrollView(app);
        sv.addView(logView, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        // 拖动
        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                WindowManager.LayoutParams lp = (WindowManager.LayoutParams) floatView.getLayoutParams();
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = lp.x;
                        startY = lp.y;
                        startTouchX = (int) event.getRawX();
                        startTouchY = (int) event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        lp.x = startX + (int) event.getRawX() - startTouchX;
                        lp.y = startY + (int) event.getRawY() - startTouchY;
                        if (wm != null && floatView != null) wm.updateViewLayout(floatView, lp);
                        return true;
                }
                return false;
            }
        });

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                dp * 300, dp * 220,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = dp * 10;
        lp.y = dp * 60;

        try {
            wm.addView(root, lp);
            floatView = root;
            showing = true;
            logView.setText(getLogText());
            updateTitle();
            append("悬浮日志已开启, 正在观察Activity活动");
        } catch (Throwable th) {
            th.printStackTrace();
            floatView = null;
        }
    }

    public void hide() {
        if (wm != null && floatView != null) {
            try {
                wm.removeView(floatView);
            } catch (Throwable ignored) {
            }
        }
        floatView = null;
        logView = null;
        showing = false;
    }
}