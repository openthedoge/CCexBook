package com.ziruoling.quiz.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** 改答案时的题型校验与自动调整规则 */
public class AnswerRuleTest {

    @Test
    public void singleBecomesMultiWhenMultipleLetters() {
        AnswerRule.Check c = AnswerRule.check(QuestionType.SINGLE, "AB", 4);
        assertTrue(c.ok());
        assertEquals(QuestionType.MULTI, c.type);
        assertEquals("AB", c.answer);
        assertNotNull("题型变化应给出提示", c.warning);
    }

    @Test
    public void multiBecomesSingleWhenOneLetter() {
        AnswerRule.Check c = AnswerRule.check(QuestionType.MULTI, "a", 4);
        assertTrue(c.ok());
        assertEquals(QuestionType.SINGLE, c.type);
        assertEquals("A", c.answer);
        assertNotNull(c.warning);
    }

    @Test
    public void multiKeepsMultiWithMultipleLetters() {
        AnswerRule.Check c = AnswerRule.check(QuestionType.MULTI, "A、B、D", 4);
        assertTrue(c.ok());
        assertEquals(QuestionType.MULTI, c.type);
        assertEquals("ABD", c.answer);
        assertNull("题型没变不应有提示", c.warning);
    }

    @Test
    public void letterOutOfOptionRangeRejected() {
        AnswerRule.Check c = AnswerRule.check(QuestionType.SINGLE, "C", 2);
        assertFalse(c.ok());
        assertTrue(c.error.contains("超出"));
    }

    @Test
    public void judgeNormalizesWords() {
        AnswerRule.Check yes = AnswerRule.check(QuestionType.JUDGE, "对", 2);
        assertTrue(yes.ok());
        assertEquals("A", yes.answer);
        assertEquals(QuestionType.JUDGE, yes.type);

        AnswerRule.Check no = AnswerRule.check(QuestionType.JUDGE, "×", 2);
        assertTrue(no.ok());
        assertEquals("B", no.answer);
    }

    @Test
    public void judgeRejectsIllegalValue() {
        AnswerRule.Check c = AnswerRule.check(QuestionType.JUDGE, "C", 2);
        assertFalse(c.ok());
        assertNotNull(c.error);
    }

    @Test
    public void sortRequiresAllLetters() {
        AnswerRule.Check ok = AnswerRule.check(QuestionType.SORT, "C,B,A", 3);
        assertTrue(ok.ok());
        assertEquals("CBA", ok.answer);

        AnswerRule.Check bad = AnswerRule.check(QuestionType.SORT, "CB", 3);
        assertFalse(bad.ok());
    }

    @Test
    public void fillKeepsFreeText() {
        AnswerRule.Check c = AnswerRule.check(QuestionType.FILL, "32 位 || 三十二位", 0);
        assertTrue(c.ok());
        assertEquals("32 位 || 三十二位", c.answer);
        assertEquals(QuestionType.FILL, c.type);
    }

    @Test
    public void emptyAnswerRejected() {
        assertFalse(AnswerRule.check(QuestionType.SINGLE, "   ", 4).ok());
        assertFalse(AnswerRule.check(QuestionType.FILL, null, 0).ok());
    }
}
