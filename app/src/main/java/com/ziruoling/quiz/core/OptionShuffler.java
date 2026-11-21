package com.ziruoling.quiz.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** 选项乱序工具：确定性洗牌（同一题同一会话结果一致），并重映射答案字母（纯 Java 可测试） */
public final class OptionShuffler {

    /** 洗牌结果 */
    public static class Shuffled {
        /** order[i] = 第 i 个显示位置对应的原始下标 */
        public final int[] order;
        /** 重映射后的答案字母 */
        public final String answer;
        public final List<String> options;

        Shuffled(int[] order, String answer, List<String> options) {
            this.order = order;
            this.answer = answer;
            this.options = options;
        }
    }

    private OptionShuffler() {}

    /**
     * 洗牌选项并重映射答案。
     *
     * @param options        原始选项
     * @param answerLetters  原始答案字母（升序）
     * @param seed           随机种子
     */
    public static Shuffled shuffle(List<String> options, String answerLetters, long seed) {
        int n = options.size();
        int[] order = new int[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Random r = new Random(seed);
        for (int i = n - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = order[i];
            order[i] = order[j];
            order[j] = t;
        }
        // 原始下标 → 新位置
        int[] newPos = new int[n];
        for (int pos = 0; pos < n; pos++) newPos[order[pos]] = pos;
        // 重映射答案
        StringBuilder sb = new StringBuilder();
        String letters = AnswerUtil.extractLetters(answerLetters);
        for (int i = 0; i < letters.length(); i++) {
            int orig = letters.charAt(i) - 'A';
            if (orig >= 0 && orig < n) sb.append((char) ('A' + newPos[orig]));
        }
        char[] chars = sb.toString().toCharArray();
        java.util.Arrays.sort(chars);
        List<String> newOpts = new ArrayList<>(n);
        for (int pos = 0; pos < n; pos++) newOpts.add(options.get(order[pos]));
        return new Shuffled(order, new String(chars), newOpts);
    }

    /** 是否应跳过乱序：判断题、排序题（选项顺序是题目内容）或选项过少 */
    public static boolean shouldSkip(int type, int optionCount) {
        return type == QuestionType.JUDGE || type == QuestionType.SORT || optionCount < 3;
    }
}
