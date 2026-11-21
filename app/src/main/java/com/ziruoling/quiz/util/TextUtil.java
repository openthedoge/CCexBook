package com.ziruoling.quiz.util;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

/**
 * 题目文本规范化与富文本显示。
 * <p>
 * 解决三个显示兼容问题（均不改动入库数据，随时可逆）：
 * <ul>
 * <li>HTML 实体：题目来自网页复制时含 {@code &#8322;}（H&#8322;O）等实体，
 *     解码为真实字符 —— 下标 ₂、上标 ² 等；</li>
 * <li>上下标字形：字符 ₂(U+2080..)、²(U+00B2..) 在部分机型字体缺字形，
 *     显示为「？」或退化普通数字；{@link #rich} 转为数字 + 缩放基线样式，
 *     任何机型都能显示上/下标效果；</li>
 * <li>波浪号：日式/全角波浪（U+301C/U+FF5E）部分机型字体均缺字形，统一为 ASCII ~。</li>
 * </ul>
 */
public final class TextUtil {

    private TextUtil() {}

    /** 纯文本规范化：解码 HTML 实体 + 波浪号统一为 ASCII ~（分享 / AI 查询 / 导出 Word 用） */
    public static String text(String s) {
        if (s == null || s.isEmpty()) return "";
        if (s.indexOf('&') < 0 && s.indexOf('〜') < 0 && s.indexOf('～') < 0) return s;
        StringBuilder out = new StringBuilder(s.length());
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '&') {
                int semi = -1;
                int limit = Math.min(n, i + 10);
                for (int j = i + 1; j < limit; j++) {
                    if (s.charAt(j) == ';') { semi = j; break; }
                }
                if (semi > i) {
                    String e = s.substring(i + 1, semi);
                    String rep = namedEntity(e);
                    if (rep != null) { out.append(rep); i = semi + 1; continue; }
                    if (e.length() > 1 && e.charAt(0) == '#') {
                        try {
                            String d = e.substring(1);
                            int cp = (d.charAt(0) == 'x' || d.charAt(0) == 'X')
                                    ? Integer.parseInt(d.substring(1), 16)
                                    : Integer.parseInt(d);
                            if (cp > 0 && cp <= 0x10FFFF && cp != 0xFEFF
                                    && (cp < 0xD800 || cp > 0xDFFF)) {
                                out.append(Character.toChars(cp));
                                i = semi + 1;
                                continue;
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                out.append(c);
                i++;
            } else if (c == '〜' || c == '～') {
                // 日式/全角波浪部分机型字体均缺字形，统一 ASCII ~（任何字体可见）
                out.append('~');
                i++;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /**
     * 富文本显示：先 {@link #text} 规范化，再把上/下标字符转为
     * 缩放 + 基线偏移样式（不依赖机型字体字形，全机型可显示）。
     * 无上下标内容时直接返回 String，零开销。
     */
    public static CharSequence rich(String s) {
        String t = text(s);
        if (!hasScriptChar(t)) return t;
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int i = 0, n = t.length();
        while (i < n) {
            char c = t.charAt(i);
            if (isSub(c) || isSup(c)) {
                boolean sub = isSub(c);
                int start = sb.length();
                while (i < n && (sub ? isSub(t.charAt(i)) : isSup(t.charAt(i)))) {
                    sb.append(sub ? subToDigit(t.charAt(i)) : supToDigit(t.charAt(i)));
                    i++;
                }
                int end = sb.length();
                sb.setSpan(new ScriptSpan(!sub), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb;
    }

    /**
     * 上/下标样式：字号缩到 62%，基线偏移取「原始字号 ascent 的 1/3」。
     * <p>
     * 系统 {@link android.text.style.SuperscriptSpan} 的偏移是 {@code ascent / 2}，
     * 在行高被 {@code lineSpacingExtra} 收紧或字体 padding 较小的机型上，
     * 上标顶部会超出字体行盒、被 TextView/父容器裁掉（表现为「4 次方的 4 只露出一半」）。
     * 这里把偏移降到 1/3，保证字形完整落在行盒内，**不需要加高行高**；
     * 已经在原始字号下计算偏移，再缩放字号，避免缩放后偏移过小。
     */
    static final class ScriptSpan extends MetricAffectingSpan {
        private final boolean superscript;

        ScriptSpan(boolean superscript) {
            this.superscript = superscript;
        }

        @Override
        public void updateMeasureState(TextPaint tp) {
            apply(tp);
        }

        @Override
        public void updateDrawState(TextPaint tp) {
            apply(tp);
        }

        private void apply(TextPaint tp) {
            float offset = tp.ascent() / 3f;
            tp.setTextSize(tp.getTextSize() * 0.62f);
            tp.baselineShift += (int) (superscript ? offset : -offset);
        }
    }

    // ---------------- 字符判定 / 映射（纯函数，可 JVM 单测） ----------------

    /** 下标数字 ₀-₉（U+2080 - U+2089） */
    static boolean isSub(char c) {
        return c >= 0x2080 && c <= 0x2089;
    }

    /** 上标数字 ⁰¹²³⁴⁵⁶⁷⁸⁹（U+2070, U+00B9, U+00B2, U+00B3, U+2074-2079） */
    static boolean isSup(char c) {
        return c == 0x2070 || (c >= 0x2074 && c <= 0x2079)
                || c == 0x00B9 || c == 0x00B2 || c == 0x00B3;
    }

    static char subToDigit(char c) {
        return (char) ('0' + (c - 0x2080));
    }

    static char supToDigit(char c) {
        if (c == 0x2070) return '0';
        if (c == 0x00B9) return '1';
        if (c == 0x00B2) return '2';
        if (c == 0x00B3) return '3';
        // ⁴-⁹ 连续区（U+2074 - U+2079），基数 '4'
        return (char) ('4' + (c - 0x2074));
    }

    static boolean hasScriptChar(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (isSub(c) || isSup(c)) return true;
        }
        return false;
    }

    private static String namedEntity(String e) {
        switch (e) {
            case "amp": return "&";
            case "lt": return "<";
            case "gt": return ">";
            case "quot": return "\"";
            case "apos": return "'";
            case "nbsp": return " ";
            default: return null;
        }
    }
}