package com.ziruoling.quiz.data;

import android.content.Context;
import android.content.SharedPreferences;

/** 用户设置 */
public final class Prefs {

    public static final int THEME_AUTO = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;
    /** 纯黑（AMOLED）：深色的一种，但页面背景为真纯黑 */
    public static final int THEME_BLACK = 3;

    // ---- 主题配色 ----
    public static final int COLOR_BLUE = 0;
    public static final int COLOR_RED = 1;
    public static final int COLOR_GREEN = 2;
    public static final int COLOR_ORANGE = 3;
    public static final int COLOR_PURPLE = 4;

    /** 配色名称（与设置界面选项顺序一致） */
    public static final String[] COLOR_NAMES = {"科技蓝", "中国红", "翡翠绿", "活力橙", "浪漫紫"};

    private static SharedPreferences sp;
    private static Context app;

    private Prefs() {}

    public static void init(Context context) {
        app = context.getApplicationContext();
        if (sp == null) sp = app.getSharedPreferences("ziruoling_quiz", Context.MODE_PRIVATE);
    }

    /** 仅供单元测试：释放单例 */
    public static void resetForTest() {
        sp = null;
    }

    private static SharedPreferences p() {
        if (sp == null) init(app);
        return sp;
    }

    // ---- 主题 ----
    public static int theme() {
        return p().getInt("theme", THEME_AUTO);
    }

    public static void theme(int v) {
        p().edit().putInt("theme", v).apply();
    }

    public static boolean isDark(Context c) {
        int t = theme();
        if (t == THEME_DARK || t == THEME_BLACK) return true;
        if (t == THEME_LIGHT) return false;
        int mode = c.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /** 是否纯黑（AMOLED）模式 */
    public static boolean isAmoled() {
        return theme() == THEME_BLACK;
    }

    /**
     * 主题模式：0=浅色 1=深色 2=纯黑。
     * THEME_AUTO 不带 Context，按浅色处理；判断系统夜间请用 {@link #isDark(Context)}。
     */
    public static int themeMode() {
        int t = theme();
        if (t == THEME_BLACK) return 2;
        if (t == THEME_DARK) return 1;
        return 0;
    }

    /** 主题配色索引（0=科技蓝 1=中国红 2=翡翠绿 3=活力橙 4=浪漫紫） */
    public static int themeColor() {
        return Math.max(COLOR_BLUE, Math.min(COLOR_PURPLE, p().getInt("themeColor", COLOR_BLUE)));
    }

    public static void themeColor(int v) {
        p().edit().putInt("themeColor", v).apply();
    }

    // ---- 字体 ----
    private static final float[] FONT_SCALES = {1.0f, 0.85f, 1.15f, 1.3f};

    public static int fontIndex() {
        return Math.max(0, Math.min(3, p().getInt("font", 0)));
    }

    public static void fontIndex(int v) {
        p().edit().putInt("font", v).apply();
    }

    public static float fontScale() {
        return FONT_SCALES[fontIndex()];
    }

    // ---- 语言 ----
    public static final int LANG_SYSTEM = 0;
    public static final int LANG_ZH = 1;
    public static final int LANG_EN = 2;

    public static int language() {
        return Math.max(0, Math.min(2, p().getInt("lang", LANG_SYSTEM)));
    }

    public static void language(int v) {
        p().edit().putInt("lang", v).apply();
    }

    /** 实际生效的语言：zh / en（跟随系统时，非中文一律英文） */
    public static String languageTag(Context c) {
        int v = language();
        if (v == LANG_ZH) return "zh";
        if (v == LANG_EN) return "en";
        String sys = java.util.Locale.getDefault().getLanguage();
        return "zh".equals(sys) ? "zh" : "en";
    }

    // ---- 刷题行为 ----
    public static boolean autoNext() {
        return p().getBoolean("autoNext", true);
    }

    public static void autoNext(boolean v) {
        p().edit().putBoolean("autoNext", v).apply();
    }

    public static boolean haptic() {
        return p().getBoolean("haptic", true);
    }

    public static void haptic(boolean v) {
        p().edit().putBoolean("haptic", v).apply();
    }

    /** 答题音效（答对/答错提示音） */
    public static boolean sound() {
        return p().getBoolean("sound", true);
    }

    public static void sound(boolean v) {
        p().edit().putBoolean("sound", v).apply();
    }

    public static boolean shuffleOpts() {
        return p().getBoolean("shuffleOpts", false);
    }

    public static void shuffleOpts(boolean v) {
        p().edit().putBoolean("shuffleOpts", v).apply();
    }

    public static boolean wrongAutoRemove() {
        return p().getBoolean("wrongAutoRemove", true);
    }

    public static void wrongAutoRemove(boolean v) {
        p().edit().putBoolean("wrongAutoRemove", v).apply();
    }

    public static boolean showAnalysis() {
        return p().getBoolean("showAnalysis", true);
    }

    public static void showAnalysis(boolean v) {
        p().edit().putBoolean("showAnalysis", v).apply();
    }
}