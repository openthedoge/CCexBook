package com.ziruoling.quiz;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import com.ziruoling.quiz.data.Db;
import com.ziruoling.quiz.data.Prefs;
import com.ziruoling.quiz.util.Tr;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 应用入口：初始化数据库、设置，并记录轻量生命周期日志 */
public class App extends Application implements Application.ActivityLifecycleCallbacks {

    private int started;
    private final StringBuilder session = new StringBuilder();

    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.init(this);
        Db.init(this);
        // 进程被系统杀掉时可能残留草稿题：任何入口启动都先清理一次（失败不影响启动）
        try {
            Db.deleteDrafts();
        } catch (Throwable ignored) {
        }
        Tr.init(this);
        installCrashLog();
        // 始终注册，开关变化后无需重启即可生效
        registerActivityLifecycleCallbacks(this);
        if (!Prefs.lifecycleLog()) {
            clearLog();
            Prefs.sessionRunning(false);
            return;
        }
        // 上次是否没有正常结束
        if (Prefs.sessionRunning()) {
            exportText(readLog(), "上次异常结束");
        }
        clearLog();
        line("启动 v" + versionName());
        Prefs.sessionRunning(true);
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private File logFile() {
        return new File(getFilesDir(), "lifecycle.log");
    }

    private void line(String text) {
        if (!Prefs.lifecycleLog()) return;
        String ts = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date());
        String row = ts + "  " + text + "\n";
        session.append(row);
        try (FileOutputStream os = new FileOutputStream(logFile(), true)) {
            os.write(row.getBytes("UTF-8"));
        } catch (Throwable ignored) {
        }
    }

    private String readLog() {
        try {
            byte[] buf = new byte[(int) logFile().length()];
            java.io.FileInputStream in = new java.io.FileInputStream(logFile());
            int n = in.read(buf);
            in.close();
            return n <= 0 ? "" : new String(buf, 0, n, "UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }

    private void clearLog() {
        try {
            logFile().delete();
        } catch (Throwable ignored) {
        }
    }

    // ---------------- 生命周期 ----------------

    @Override
    public void onActivityCreated(Activity a, Bundle b) {
        started++;
        line("打开 " + a.getClass().getSimpleName());
    }

    @Override
    public void onActivityStarted(Activity a) {
    }

    @Override
    public void onActivityResumed(Activity a) {
    }

    @Override
    public void onActivityPaused(Activity a) {
        line("暂停 " + a.getClass().getSimpleName());
    }

    @Override
    public void onActivityStopped(Activity a) {
        line("停止 " + a.getClass().getSimpleName());
    }

    @Override
    public void onActivitySaveInstanceState(Activity a, Bundle b) {
    }

    @Override
    public void onActivityDestroyed(Activity a) {
        line("关闭 " + a.getClass().getSimpleName());
        started--;
        if (started <= 0) {
            started = 0;
            line("正常退出");
            if (Prefs.lifecycleLog()) exportText(readLog(), "正常退出");
            clearLog();
            Prefs.sessionRunning(false);
        }
    }

    // ---------------- 导出 ----------------

    /** 把生命周期日志写到系统下载目录 */
    private void exportText(String text, String reason) {
        if (text.isEmpty()) return;
        String name = "运行日志-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                .format(new Date()) + ".txt";
        String content = "CC练习本 " + versionName() + "（" + reason + "）\n\n" + text;
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.content.ContentValues v = new android.content.ContentValues();
                v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_DOWNLOADS);
                android.net.Uri uri = getContentResolver().insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri != null) {
                    java.io.OutputStream os = getContentResolver().openOutputStream(uri);
                    if (os != null) {
                        os.write(content.getBytes("UTF-8"));
                        os.close();
                    }
                }
            } else {
                File dir = getExternalFilesDir(null);
                if (dir != null) {
                    File f = new File(dir, name);
                    FileOutputStream os = new FileOutputStream(f);
                    os.write(content.getBytes("UTF-8"));
                    os.close();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** 捕获未处理异常，把堆栈写到下载目录，便于定位闪退 */
    private void installCrashLog() {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                java.io.PrintWriter pw = new java.io.PrintWriter(sw);
                e.printStackTrace(pw);
                pw.close();
                line("崩溃 " + e.getClass().getSimpleName() + ": " + e.getMessage());
                line(sw.toString());
                exportText(readLog(), "崩溃");
            } catch (Throwable ignored) {
            }
            if (prev != null) prev.uncaughtException(t, e);
        });
    }
}
