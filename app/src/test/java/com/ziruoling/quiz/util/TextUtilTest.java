package com.ziruoling.quiz.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * TextUtil 纯逻辑测试：HTML 实体解码 / 波浪规范化 / 上下标字符识别。
 * rich() 的 span 渲染由 Robolectric 冒烟覆盖，此处只测不含样式的路径。
 */
public class TextUtilTest {

    @Test
    public void decodeNumericEntities() {
        assertEquals("H₂O", TextUtil.text("H&#8322;O"));
        assertEquals("2²", TextUtil.text("2&#178;"));
        assertEquals("₂O", TextUtil.text("&#x2082;O"));   // 十六进制
        assertEquals("m/s²", TextUtil.text("m/s&#xB2;"));
    }

    @Test
    public void decodeNamedEntities() {
        assertEquals("&<>\" ", TextUtil.text("&amp;&lt;&gt;&quot;&nbsp;"));
    }

    @Test
    public void noDoubleDecode() {
        // &amp;#8322; 先解出 &，不能二次解码成 ₂
        assertEquals("&#8322;", TextUtil.text("&amp;#8322;"));
    }

    @Test
    public void invalidEntityKept() {
        assertEquals("&#99999999;", TextUtil.text("&#99999999;")); // 越界
        assertEquals("&#xZZ;", TextUtil.text("&#xZZ;"));           // 非法十六进制
        assertEquals("A&B", TextUtil.text("A&B"));                 // 普通与号
        assertEquals("&&", TextUtil.text("&&"));
    }

    @Test
    public void waveDashNormalized() {
        assertEquals("A~B", TextUtil.text("A〜B"));               // U+301C → ASCII ~
        assertEquals("50%~80%", TextUtil.text("50%\uFF5E80%"));    // U+FF5E → ASCII ~（部分机型全角也缺字形）
    }

    @Test
    public void plainUntouched() {
        String s = "普通题目 50%~80% 没有特殊字符";
        assertSame(s, TextUtil.text(s)); // 无实体/日式或全角波浪时返回原引用，零开销
        assertEquals("", TextUtil.text(null));
        assertEquals("", TextUtil.text(""));
    }

    @Test
    public void scriptCharClassification() {
        // 下标 ₀-₉ (U+2080-U+2089)
        assertTrue(TextUtil.isSub('₂'));
        assertTrue(TextUtil.isSub('₀'));
        assertFalse(TextUtil.isSub('2'));
        assertEquals('2', TextUtil.subToDigit('₂'));
        assertEquals('0', TextUtil.subToDigit('₀'));
        assertEquals('9', TextUtil.subToDigit('₉'));
        // 上标 ⁰¹²³⁴⁵⁶⁷⁸⁹
        assertTrue(TextUtil.isSup('²'));
        assertTrue(TextUtil.isSup('¹'));
        assertTrue(TextUtil.isSup('⁰'));
        assertTrue(TextUtil.isSup('⁹'));
        assertFalse(TextUtil.isSup('2'));
        assertEquals('2', TextUtil.supToDigit('²'));
        assertEquals('1', TextUtil.supToDigit('¹'));
        assertEquals('3', TextUtil.supToDigit('³'));
        assertEquals('7', TextUtil.supToDigit('⁷'));
        // 检测
        assertTrue(TextUtil.hasScriptChar("H₂O"));
        assertTrue(TextUtil.hasScriptChar("2² m/s"));
        assertFalse(TextUtil.hasScriptChar("H2O"));
        assertFalse(TextUtil.hasScriptChar(null));
    }

    @Test
    public void richPlainNoStyle() {        // 无上下标时不触碰 Android 样式 API，直接返回 String；
        // （含上下标字符的 span 渲染在 Robolectric 冒烟用例中覆盖）
        CharSequence r = TextUtil.rich("光衰阈值 50%~80%");
        assertEquals("光衰阈值 50%~80%", r.toString());
        assertEquals("", TextUtil.rich(null).toString());
        assertEquals("纯文本 2 的平方", TextUtil.rich("纯文本 2 的平方").toString());
    }
}