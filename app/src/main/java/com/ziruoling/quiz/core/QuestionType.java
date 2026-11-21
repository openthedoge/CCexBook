package com.ziruoling.quiz.core;

/** 题型常量 */
public final class QuestionType {
    public static final int SINGLE = 0;
    public static final int MULTI = 1;
    public static final int JUDGE = 2;
    public static final int FILL = 3;
    public static final int SHORT = 4;
    public static final int SORT = 5;
    /** 其它题：收容不属于以上类型的题，按简答方式作答（自评），可带图片 */
    public static final int OTHER = 6;

    /** 题型总数（数组遍历用） */
    public static final int COUNT = 7;

    public static final String[] NAMES = {"单选题", "多选题", "判断题", "填空题", "简答题", "排序题", "其它题"};
    public static final String[] SHORT_NAMES = {"单选", "多选", "判断", "填空", "简答", "排序", "其它"};

    private QuestionType() {}

    public static boolean valid(int t) {
        return t >= 0 && t < COUNT;
    }

    /** 是否参与模拟考试自动判分（简答/其它不参与） */
    public static boolean examinable(int t) {
        return t != SHORT && t != OTHER;
    }

    /** 是否为“自由文本作答、自评”的题型（简答/其它） */
    public static boolean selfAssessed(int t) {
        return t == SHORT || t == OTHER;
    }

    /** 根据文本识别题型，失败返回 -1 */
    public static int fromText(String s) {
        if (s == null) return -1;
        String h = AnswerUtil.toHalfWidth(s).trim();
        if (h.contains("多选")) return MULTI;
        if (h.contains("判断")) return JUDGE;
        if (h.contains("填空")) return FILL;
        if (h.contains("简答") || h.contains("问答")) return SHORT;
        if (h.contains("排序") || h.contains("排顺序") || h.contains("顺序题")) return SORT;
        if (h.contains("其它") || h.contains("其他")) return OTHER;
        if (h.contains("单选") || h.contains("选择")) return SINGLE;
        return -1;
    }
}
