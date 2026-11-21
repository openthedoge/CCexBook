package com.ziruoling.quiz.core;

/**
 * 答案编辑规则：校验用户输入的正确答案，并在形态与题型不符时给出应该采用的题型。
 * <p>
 * 规则（纯 Java，无 Android 依赖，可单元测试）：
 * <ul>
 *   <li>单选/多选：允许多少个字母决定题型——多字母即多选，单字母即单选；字母不得超出选项数</li>
 *   <li>判断：只接受 对/错/正确/错误/√/×/A/B，并规范化为 A/B</li>
 *   <li>排序：字母数必须等于选项数（每个选项恰好出现一次）</li>
 *   <li>填空/简答/看图：自由文本</li>
 * </ul>
 */
public final class AnswerRule {

    public static final class Check {
        /** 规范化后的答案（失败时为 null） */
        public String answer;
        /** 应采用的题型 */
        public int type;
        /** 非空表示需要提示用户（例如题型被自动调整） */
        public String warning;
        /** 非空表示校验失败，内容为给用户看的原因 */
        public String error;

        public boolean ok() {
            return error == null;
        }
    }

    private AnswerRule() {}

    public static Check check(int currentType, String raw, int optionCount) {
        Check r = new Check();
        r.type = currentType;
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) {
            r.error = "答案不能为空";
            return r;
        }
        switch (currentType) {
            case QuestionType.SINGLE:
            case QuestionType.MULTI: {
                String letters = AnswerUtil.extractLetters(s);
                if (letters.isEmpty()) {
                    r.error = "选择题答案只能包含 A-F 字母";
                    return r;
                }
                for (int i = 0; i < letters.length(); i++) {
                    if (letters.charAt(i) - 'A' >= optionCount) {
                        r.error = "字母 " + letters.charAt(i) + " 超出本题的 " + optionCount + " 个选项";
                        return r;
                    }
                }
                r.answer = letters;
                r.type = letters.length() > 1 ? QuestionType.MULTI : QuestionType.SINGLE;
                if (r.type != currentType) {
                    r.warning = "答案是 " + letters.length() + " 个字母，与当前题型不符，将自动调整为「"
                            + QuestionType.NAMES[r.type] + "」";
                }
                return r;
            }
            case QuestionType.JUDGE: {
                String j = AnswerUtil.normalizeJudge(s);
                if (j.isEmpty()) {
                    r.error = "判断题答案只能是 对/错/正确/错误/√/×/A/B";
                    return r;
                }
                r.answer = j;
                r.type = QuestionType.JUDGE;
                if (!j.equals(s)) {
                    r.warning = "答案将规范化为「" + AnswerUtil.judgeText(j) + "」";
                }
                return r;
            }
            case QuestionType.SORT: {
                String seq = AnswerUtil.extractSequence(s);
                if (optionCount == 0 || seq.length() != optionCount) {
                    r.error = "排序题答案必须是 " + optionCount + " 个不重复字母（如 CBA）";
                    return r;
                }
                r.answer = seq;
                r.type = QuestionType.SORT;
                if (!seq.equals(s)) r.warning = "答案将规范化为 " + seq;
                return r;
            }
            default: {
                r.answer = s;
                r.type = currentType;
                return r;
            }
        }
    }
}
