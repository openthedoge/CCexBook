package com.ziruoling.quiz.core;

import java.util.ArrayList;
import java.util.List;

/** 题库解析结果 */
public class ParseResult {
    public final List<ParsedQuestion> questions = new ArrayList<>();
    /** 被跳过行的原因 */
    public final List<String> warnings = new ArrayList<>();
    /** 解析器给出的提示信息 */
    public final List<String> infos = new ArrayList<>();
    /** 原始数据行数（不含表头） */
    public int totalRows = 0;

    public int[] counts() {
        int[] c = new int[QuestionType.COUNT];
        for (ParsedQuestion q : questions) {
            if (QuestionType.valid(q.type)) c[q.type]++;
        }
        return c;
    }

    public int count(int type) {
        int n = 0;
        for (ParsedQuestion q : questions) if (q.type == type) n++;
        return n;
    }

    public boolean isEmpty() {
        return questions.isEmpty();
    }
}
