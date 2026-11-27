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

    /** 背题模式显示「自动背题」悬浮球（默认开） */
    /** 生命周期日志：记录页面开关并导出，默认开启 */
    public static boolean lifecycleLog() {
        return sp.getBoolean("lifecycle_log", false);
    }

    public static void lifecycleLog(boolean v) {
        sp.edit().putBoolean("lifecycle_log", v).apply();
    }

    /** 专注模式：隐藏统计入口与首页统计 */
    public static boolean focusMode() {
        return sp.getBoolean("focus_mode", false);
    }

    public static void focusMode(boolean v) {
        sp.edit().putBoolean("focus_mode", v).apply();
    }

    /** 上次会话是否未正常结束（用于异常退出检测） */
    public static boolean sessionRunning() {
        return sp.getBoolean("session_running", false);
    }

    public static void sessionRunning(boolean v) {
        sp.edit().putBoolean("session_running", v).apply();
    }

    /** 是否已通关贪吃蛇：通关后随时可玩，不再需要做题 */
    public static boolean snakeUnlocked() {
        return sp.getBoolean("snake_unlocked", false);
    }

    public static void snakeUnlocked(boolean v) {
        sp.edit().putBoolean("snake_unlocked", v).apply();
    }

    /** 彩蛋门槛计数：0~5，初始 5（可直接玩一次），每答一题 +1，封顶 5 */
    public static int snakeGate() {
        return sp.getInt("snake_gate", 5);
    }

    public static void snakeGate(int v) {
        int n = Math.max(0, Math.min(5, v));
        sp.edit().putInt("snake_gate", n).apply();
    }

    /** 答完一题调用：未满 5 则 +1 */
    public static void snakeGateOnAnswer() {
        int g = snakeGate();
        if (g < 5) snakeGate(g + 1);
    }

    /** 填空每空分值（考试用） */
    public static int examBlankScore() {
        return sp.getInt("exam_blank_score", 2);
    }

    public static void examBlankScore(int v) {
        sp.edit().putInt("exam_blank_score", v).apply();
    }

    /** 多选未全对给分比例（%） */
    public static int examPartialPercent() {
        return sp.getInt("exam_partial_percent", 50);
    }

    public static void examPartialPercent(int v) {
        sp.edit().putInt("exam_partial_percent", v).apply();
    }

    /** 排序字段：0 名称 / 1 做题日期 / 2 加入日期 / 3 手动 */
    public static final String[] SORT_FIELDS = {"名称", "做题日期", "加入日期"};
    public static final String[] SORT_DIRS = {"正序", "倒序"};

    public static int bankSortField() {
        return sp.getInt("bank_sort_field", 1);
    }

    public static void bankSortField(int v) {
        sp.edit().putInt("bank_sort_field", v).apply();
    }

    public static int bankSortDir() {
        return sp.getInt("bank_sort_dir", 1);
    }

    public static void bankSortDir(int v) {
        sp.edit().putInt("bank_sort_dir", v).apply();
    }

    public static int folderSortField() {
        return sp.getInt("folder_sort_field", 0);
    }

    public static void folderSortField(int v) {
        sp.edit().putInt("folder_sort_field", v).apply();
    }

    public static int folderSortDir() {
        return sp.getInt("folder_sort_dir", 1);
    }

    public static void folderSortDir(int v) {
        sp.edit().putInt("folder_sort_dir", v).apply();
    }

    /** 折叠的文件夹 id（默认全部展开） */
    public static java.util.Set<Long> collapsedFolders() {
        java.util.Set<Long> out = new java.util.HashSet<>();
        java.util.Set<String> raw = sp.getStringSet("folders_collapsed", null);
        if (raw != null) {
            for (String s : raw) {
                try {
                    out.add(Long.parseLong(s));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return out;
    }

    public static void collapsedFolders(java.util.Set<Long> ids) {
        java.util.Set<String> raw = new java.util.HashSet<>();
        for (Long id : ids) raw.add(String.valueOf(id));
        sp.edit().putStringSet("folders_collapsed", raw).apply();
    }

    /** 自定义音效文件路径（空 = 用内置合成音） */
    public static String customSound(boolean correct) {
        return sp.getString(correct ? "sound_ok_path" : "sound_wrong_path", "");
    }

    public static void customSound(boolean correct, String path) {
        sp.edit().putString(correct ? "sound_ok_path" : "sound_wrong_path",
                path == null ? "" : path).apply();
    }

    /** 动画速度：0 跟随系统 / 1 快 / 2 中 / 3 慢 / 4 关闭 */
    public static final String[] ANIM_NAMES = {"跟随系统", "快", "中", "慢", "关闭"};

    public static int animSpeed() {
        return sp.getInt("anim_speed", 2);
    }

    public static void animSpeed(int v) {
        sp.edit().putInt("anim_speed", Math.max(0, Math.min(4, v))).apply();
    }

    public static boolean autoReciteBall() {
        return sp.getBoolean("auto_recite_ball", true);
    }

    public static void autoReciteBall(boolean v) {
        sp.edit().putBoolean("auto_recite_ball", v).apply();
    }

    /** 自动背题间隔（秒） */
    public static int autoReciteGap() {
        return sp.getInt("auto_recite_gap", 10);
    }

    public static void autoReciteGap(int v) {
        sp.edit().putInt("auto_recite_gap", Math.max(1, Math.min(60, v))).apply();
    }

    /** 悬浮球位置（全局，-1 表示未记录） */
    public static int ballX() {
        return sp.getInt("ball_x", -1);
    }

    public static int ballY() {
        return sp.getInt("ball_y", -1);
    }

    public static void ballPos(int x, int y) {
        sp.edit().putInt("ball_x", x).putInt("ball_y", y).apply();
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