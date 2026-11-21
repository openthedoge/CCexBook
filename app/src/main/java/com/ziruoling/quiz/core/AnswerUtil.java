package com.ziruoling.quiz.core;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 答案规范化与判定（纯 Java，可单元测试）。
 * <p>
 * 规范答案存储格式：
 * - 单选题：单个字母 "A".."F"
 * - 多选题：字母升序串，如 "ABD"
 * - 判断题：字母 "A"(正确) / "B"(错误)，选项固定为 [正确, 错误]
 * - 填空题：参考答案文本，多个可选答案用 "||" 分隔
 * - 简答题：参考答案文本
 */
public final class AnswerUtil {

    private static final Set<String> TRUE_WORDS = new HashSet<>(Arrays.asList(
            "对", "正确", "是", "√", "✓", "T", "Y", "V", "TRUE", "YES", "RIGHT", "真"));
    private static final Set<String> FALSE_WORDS = new HashSet<>(Arrays.asList(
            "错", "错误", "否", "×", "X", "✗", "F", "N", "FALSE", "NO", "WRONG", "假"));

    private AnswerUtil() {}

    /** 全角转半角 */
    public static String toHalfWidth(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c >= 0xFF01 && c <= 0xFF5E) {
                b.append((char) (c - 0xFEE0));
            } else if (c == 0x3000) {
                b.append(' ');
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    /** 提取选项字母并按升序去重，如 "a、B,d" → "ABD" */
    public static String extractLetters(String s) {
        if (s == null) return "";
        String h = toHalfWidth(s).toUpperCase();
        boolean[] seen = new boolean[6];
        for (int i = 0; i < h.length(); i++) {
            char c = h.charAt(i);
            if (c >= 'A' && c <= 'F') seen[c - 'A'] = true;
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (seen[i]) b.append((char) ('A' + i));
        }
        return b.toString();
    }

    /** 判断题答案规范化：识别 对/错/正确/错误/√/×/T/F/Y/N/A/B 等，返回 "A"/"B"，失败返回 "" */
    public static String normalizeJudge(String s) {
        if (s == null) return "";
        String h = toHalfWidth(s).trim();
        if (h.isEmpty()) return "";
        // 去掉 答案/正确答案/参考答案 前缀
        h = h.replaceFirst("^(正确答案|参考答案|标准答案|答案)\\s*[：:＝=]?\\s*", "");
        String up = h.toUpperCase().trim();
        // 去掉结尾句读
        up = up.replaceFirst("[。\\.．,，;；\\s]+$", "");
        if (up.isEmpty()) return "";
        if (up.equals("A")) return "A";
        if (up.equals("B")) return "B";
        if (TRUE_WORDS.contains(up)) return "A";
        if (FALSE_WORDS.contains(up)) return "B";
        return "";
    }

    /** 判断题答案字母转显示文本 */
    public static String judgeText(String letter) {
        return "A".equals(letter) ? "正确" : "错误";
    }

    /** 填空题文本规范化：全角转半角、忽略大小写与首尾标点、去空白 */
    public static String normFill(String s) {
        if (s == null) return "";
        String h = toHalfWidth(s).trim().toLowerCase();
        h = h.replaceAll("[。．.!！?？,，;；、\\s]+", "");
        return h;
    }

    /** 填空题判定：支持 "||" 分隔的多个参考答案 */
    public static boolean matchFill(String user, String ref) {
        if (ref == null) return false;
        String u = normFill(user);
        if (u.isEmpty()) return false;
        if (ref.trim().isEmpty()) return true; // 无参考答案：视为自行核对通过
        for (String alt : ref.split("\\|\\|")) {
            if (!alt.trim().isEmpty() && u.equals(normFill(alt))) return true;
        }
        return false;
    }

    /** 单选题判定 */
    public static boolean matchSingle(String userLetter, String answer) {
        return userLetter != null && answer != null && userLetter.equals(answer);
    }

    /** 多选题判定：完全一致才算对 */
    public static boolean matchMulti(String userLetters, String answer) {
        String u = extractLetters(userLetters);
        String a = extractLetters(answer);
        return !u.isEmpty() && u.equals(a);
    }

    /** 提取选项字母并保留出现顺序（排序题用），如 "b、A,d" → "BAD" */
    public static String extractSequence(String s) {
        if (s == null) return "";
        String h = toHalfWidth(s).toUpperCase();
        StringBuilder b = new StringBuilder();
        boolean[] seen = new boolean[6];
        for (int i = 0; i < h.length(); i++) {
            char c = h.charAt(i);
            if (c >= 'A' && c <= 'F' && !seen[c - 'A']) {
                seen[c - 'A'] = true;
                b.append(c);
            }
        }
        return b.toString();
    }

    /** 排序题判定：用户点击顺序串与正确顺序串完全一致（保留顺序，不升序化） */
    public static boolean matchSort(String userOrder, String answer) {
        if (userOrder == null || answer == null) return false;
        String u = extractSequence(userOrder);
        String a = extractSequence(answer);
        return !u.isEmpty() && u.equals(a);
    }

    /** 选项是否为判断类词汇（用于无题型标注时的推断） */
    public static boolean looksJudgeOption(String s) {
        if (s == null) return false;
        String up = toHalfWidth(s).trim().toUpperCase();
        return TRUE_WORDS.contains(up) || FALSE_WORDS.contains(up);
    }

    /** 去掉题干开头的序号，如 "12、"、"（3）"、"4."（不影响 "100M" 这类正文） */
    public static String stripNumbering(String s) {
        if (s == null) return "";
        return s.replaceFirst("^[（(]\\d{1,4}[）)]\\s*|^\\d{1,4}\\s*[、.．,，:：]\\s*", "");
    }

    /**
     * 取出题干开头的序号（如 "5.题干" → 5），没有则返回 0。
     * 与 {@link #stripNumbering} 配套：先取号再剥号，序号存库用于显示「本篇第几题」。
     */
    public static int parseNumbering(String s) {
        if (s == null) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^[（(](\\d{1,4})[）)]\\s*|^(\\d{1,4})\\s*[、.．,，:：]\\s*")
                .matcher(s.trim());
        if (!m.find()) return 0;
        String n = m.group(1) != null ? m.group(1) : m.group(2);
        try {
            return Integer.parseInt(n);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 答案显示文本（用于结果区） */
    public static String answerDisplay(int type, String answer) {
        if (answer == null) return "";
        switch (type) {
            case QuestionType.JUDGE:
                return normalizeJudge(answer).isEmpty() ? answer : judgeText(normalizeJudge(answer));
            default:
                return answer;
        }
    }
}
