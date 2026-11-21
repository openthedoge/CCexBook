package com.ziruoling.quiz.core;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AnswerUtilTest {

    @Test
    public void testNormalizeJudge() {
        assertEquals("A", AnswerUtil.normalizeJudge("对"));
        assertEquals("A", AnswerUtil.normalizeJudge("正确"));
        assertEquals("A", AnswerUtil.normalizeJudge("√"));
        assertEquals("A", AnswerUtil.normalizeJudge("T"));
        assertEquals("A", AnswerUtil.normalizeJudge("y"));
        assertEquals("A", AnswerUtil.normalizeJudge("答案：对"));
        assertEquals("A", AnswerUtil.normalizeJudge("正确答案: TRUE"));
        assertEquals("B", AnswerUtil.normalizeJudge("错"));
        assertEquals("B", AnswerUtil.normalizeJudge("错误"));
        assertEquals("B", AnswerUtil.normalizeJudge("×"));
        assertEquals("B", AnswerUtil.normalizeJudge("F"));
        assertEquals("B", AnswerUtil.normalizeJudge("否"));
        assertEquals("A", AnswerUtil.normalizeJudge("A"));
        assertEquals("B", AnswerUtil.normalizeJudge("B"));
        assertEquals("", AnswerUtil.normalizeJudge(""));
        assertEquals("", AnswerUtil.normalizeJudge("不知道"));
    }

    @Test
    public void testExtractLetters() {
        assertEquals("ABD", AnswerUtil.extractLetters("a、B,d"));
        assertEquals("ABD", AnswerUtil.extractLetters("ＡＢＤ"));
        assertEquals("AC", AnswerUtil.extractLetters("A C"));
        assertEquals("ABCDEF", AnswerUtil.extractLetters("F,E,D,C,B,A"));
    }

    @Test
    public void testExtractSequenceAndMatchSort() {
        // 排序题：保留出现顺序（不升序化），去重保留首个
        assertEquals("BAD", AnswerUtil.extractSequence("b、A,d"));
        assertEquals("CADB", AnswerUtil.extractSequence("CADB"));
        assertEquals("CADB", AnswerUtil.extractSequence("ＣＡＤＢ"));
        assertTrue(AnswerUtil.matchSort("CADB", "CADB"));
        assertTrue(AnswerUtil.matchSort("cadb", "C、A、D、B"));
        // 顺序不同则判错
        assertFalse(AnswerUtil.matchSort("CABD", "CADB"));
        assertFalse(AnswerUtil.matchSort("", "CADB"));
        assertFalse(AnswerUtil.matchSort(null, "CADB"));
    }

    @Test
    public void testMatchFill() {
        assertTrue(AnswerUtil.matchFill("192.168.1.1", "192.168.1.1"));
        assertTrue(AnswerUtil.matchFill(" 192.168.1.1。", "192.168.1.1"));
        assertTrue(AnswerUtil.matchFill("abc", "ABC"));
        assertTrue(AnswerUtil.matchFill("x", "a||x"));
        assertTrue(AnswerUtil.matchFill("对", "正确||对"));
        assertTrue(AnswerUtil.matchFill("任意", ""));
        assertFalse(AnswerUtil.matchFill("", "答案"));
        assertFalse(AnswerUtil.matchFill("abc", "abd"));
        assertFalse(AnswerUtil.matchFill("abc", "abc def"));
    }

    @Test
    public void testMatchMulti() {
        assertTrue(AnswerUtil.matchMulti("ABD", "d,b,a"));
        assertFalse(AnswerUtil.matchMulti("AB", "ABC"));
        assertFalse(AnswerUtil.matchMulti("ABC", "AB"));
        assertFalse(AnswerUtil.matchMulti("", "AB"));
    }

    @Test
    public void testStripNumbering() {
        assertEquals("题目", AnswerUtil.stripNumbering("12、题目"));
        assertEquals("题目", AnswerUtil.stripNumbering("（3）题目"));
        assertEquals("题目", AnswerUtil.stripNumbering("4. 题目"));
        assertEquals("题目", AnswerUtil.stripNumbering("56．题目"));
        assertEquals("100M是指", AnswerUtil.stripNumbering("100M是指"));
        assertEquals("TCP协议", AnswerUtil.stripNumbering("TCP协议"));
        assertEquals("题", AnswerUtil.stripNumbering("12:题"));
    }

    @Test
    public void testHalfWidth() {
        assertEquals("AB123", AnswerUtil.toHalfWidth("ＡＢ１２３"));
        assertEquals("A B", AnswerUtil.toHalfWidth("A　B"));
    }

    @Test
    public void testOptionShuffler() {
        List<String> opts = Arrays.asList("选项一", "选项二", "选项三", "选项四");
        for (long seed = 1; seed < 200; seed++) {
            OptionShuffler.Shuffled s = OptionShuffler.shuffle(opts, "B", seed);
            assertEquals(4, s.options.size());
            // 找到原答案(选项二)的新位置，校验映射字母
            int pos = s.options.indexOf("选项二");
            String expect = String.valueOf((char) ('A' + pos));
            assertEquals(expect, s.answer);
            // 顺序映射自洽
            for (int i = 0; i < 4; i++) assertEquals(opts.get(s.order[i]), s.options.get(i));
        }
        // 多选映射
        for (long seed = 7; seed < 120; seed++) {
            OptionShuffler.Shuffled s = OptionShuffler.shuffle(opts, "AD", seed);
            int p1 = s.options.indexOf("选项一");
            int p4 = s.options.indexOf("选项四");
            String expect = String.valueOf((char) ('A' + Math.min(p1, p4)))
                    + (char) ('A' + Math.max(p1, p4));
            assertEquals(expect, s.answer);
        }
        assertTrue(OptionShuffler.shouldSkip(QuestionType.JUDGE, 2));
        assertFalse(OptionShuffler.shouldSkip(QuestionType.SINGLE, 4));
        assertTrue(OptionShuffler.shouldSkip(QuestionType.SINGLE, 2));
    }

    @Test
    public void testQuestionTypeFromText() {
        assertEquals(QuestionType.MULTI, QuestionType.fromText("多选题"));
        assertEquals(QuestionType.JUDGE, QuestionType.fromText("判断"));
        assertEquals(QuestionType.FILL, QuestionType.fromText("填空"));
        assertEquals(QuestionType.SHORT, QuestionType.fromText("简答"));
        assertEquals(QuestionType.SHORT, QuestionType.fromText("问答"));
        assertEquals(QuestionType.SINGLE, QuestionType.fromText("单选题"));
        assertEquals(-1, QuestionType.fromText("xx"));
    }

    @Test
    public void testRowMapperInfer() {
        List<String> opts = new ArrayList<>(Arrays.asList("甲", "乙", "丙", "丁"));
        assertEquals(QuestionType.SINGLE, RowMapper.inferType("", opts, "A"));
        assertEquals(QuestionType.MULTI, RowMapper.inferType("", opts, "AB"));
        assertEquals(QuestionType.JUDGE, RowMapper.inferType("", new ArrayList<String>(), "对"));
        assertEquals(QuestionType.FILL, RowMapper.inferType("", new ArrayList<String>(), "192.168.1.1"));
        List<String> judgeOpts = Arrays.asList("对", "错");
        assertEquals(QuestionType.JUDGE, RowMapper.inferType("", judgeOpts, "正确"));
        assertEquals(QuestionType.JUDGE, RowMapper.inferType("判断题", new ArrayList<String>(), ""));
    }
}
