package com.ziruoling.quiz.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;

import com.ziruoling.quiz.R;
import com.ziruoling.quiz.data.Prefs;

/** 基类：多主题配色 + 夜间模式 + 字体缩放统一处理 */
public abstract class BaseActivity extends Activity {

    /** 浅色主题组（与 Prefs.COLOR_* 顺序一致） */
    private static final int[] THEME_LIGHT = {
            R.style.AppTheme_Light,       // 科技蓝
            R.style.AppTheme_Red_Light,   // 中国红
            R.style.AppTheme_Green_Light, // 翡翠绿
            R.style.AppTheme_Orange_Light,// 活力橙
            R.style.AppTheme_Purple_Light // 浪漫紫
    };

    /** 深色主题组（与 Prefs.COLOR_* 顺序一致） */
    private static final int[] THEME_DARK = {
            R.style.AppTheme_Dark,
            R.style.AppTheme_Red_Dark,
            R.style.AppTheme_Green_Dark,
            R.style.AppTheme_Orange_Dark,
            R.style.AppTheme_Purple_Dark
    };

    /** 纯黑（AMOLED）主题组（与 Prefs.COLOR_* 顺序一致） */
    private static final int[] THEME_BLACK = {
            R.style.AppTheme_Black,
            R.style.AppTheme_Red_Black,
            R.style.AppTheme_Green_Black,
            R.style.AppTheme_Orange_Black,
            R.style.AppTheme_Purple_Black
    };

    private int appliedTheme = -1;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(applyLocale(applyFontScale(newBase)));
    }

    /** 语言切换：多语言暂停中，界面固定中文（恢复时改回按 Prefs.languageTag 取语言即可） */
    public static Context applyLocale(Context base) {
        java.util.Locale locale = java.util.Locale.SIMPLIFIED_CHINESE;
        java.util.Locale.setDefault(locale);
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        cfg.setLocale(locale);
        return base.createConfigurationContext(cfg);
    }

    /** 字体缩放（仅影响 sp 字号） */
    public static Context applyFontScale(Context base) {
        float scale = Prefs.fontScale();
        if (Math.abs(scale - 1f) < 0.001f) return base;
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        cfg.fontScale = scale;
        return base.createConfigurationContext(cfg);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyThemeInternal();
        super.onCreate(savedInstanceState);
    }

    /** 当前主题签名：模式(0/1/2) * 10 + 配色索引 */
    private int themeKey() {
        return themeModeKey() * 10 + Prefs.themeColor();
    }

    /** 0=浅色 1=深色 2=纯黑（AUTO 跟随系统时按 uiMode 区分为浅/深） */
    private int themeModeKey() {
        int mode = Prefs.themeMode();
        if (mode == 0 && Prefs.isDark(this)) mode = 1;
        return mode;
    }

    private void applyThemeInternal() {
        appliedTheme = themeKey();
        int color = Prefs.themeColor();
        int mode = themeModeKey();
        int[] themes = mode == 2 ? THEME_BLACK : (mode == 1 ? THEME_DARK : THEME_LIGHT);
        setTheme(themes[color]);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (appliedTheme != themeKey()) {
            applyThemeInternal();
            recreate();
        }
    }
}