package com.ziruoling.quiz.data;

/** 题库模型（含统计字段） */
public class Bank {
    public long id;
    public String name = "";
    public String source = "";
    public long createdAt;
    public long lastAt;
    public int questionCount;
    public int doneCount;
    public int correctCount;
    public int recordCount;
    /** 已做进度 0..1 */
    public float progress() {
        return questionCount <= 0 ? 0 : Math.min(1f, (float) doneCount / questionCount);
    }

    /** 正确率 0..100（无记录返回 0） */
    public int accuracy() {
        return recordCount <= 0 ? 0 : Math.round(correctCount * 100f / recordCount);
    }
}