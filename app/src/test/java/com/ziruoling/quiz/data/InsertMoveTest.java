package com.ziruoling.quiz.data;

import com.ziruoling.quiz.core.ParsedQuestion;
import com.ziruoling.quiz.core.QuestionType;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/** 追加/插入/移动题目：题序重排、分类归属、记录不丢 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class InsertMoveTest {

    private long bankId;
    private long q0, q1, q2;

    @Before
    public void setUp() {
        Db.resetForTest();
        Db.init(RuntimeEnvironment.getApplication());
        bankId = Db.createBank("插入测试", "test");
        List<ParsedQuestion> list = new ArrayList<>();
        list.add(pq("A1", "分类A"));
        list.add(pq("A2", "分类A"));
        list.add(pq("B1", "分类B"));
        Db.importQuestions(bankId, list);
        List<Long> ids = Db.questionIds(bankId);
        q0 = ids.get(0);
        q1 = ids.get(1);
        q2 = ids.get(2);
    }

    private ParsedQuestion pq(String content, String category) {
        ParsedQuestion p = new ParsedQuestion();
        p.type = QuestionType.SINGLE;
        p.content = content;
        p.options = Arrays.asList("甲", "乙");
        p.answer = "A";
        p.category = category;
        return p;
    }

    private List<String> order() {
        List<String> out = new ArrayList<>();
        for (long id : Db.questionIds(bankId)) out.add(Db.getQuestion(id).content);
        return out;
    }

    @Test
    public void insertIntoMiddleOfCategory() {
        long newId = Db.insertQuestionsIds(bankId, Collections.singletonList(pq("A1.5", "分类A")),
                "分类A", 1).get(0);
        assertEquals(Arrays.asList("A1", "A1.5", "A2", "B1"), order());
        assertEquals("分类A", Db.getQuestion(newId).category);
        assertEquals("新题在第 2 位", 1, Db.getQuestion(newId).qindex);
    }

    @Test
    public void insertAtStartAndEnd() {
        Db.insertQuestions(bankId, Collections.singletonList(pq("A0", "分类A")), "分类A", 0);
        assertEquals(Arrays.asList("A0", "A1", "A2", "B1"), order());

        Db.insertQuestions(bankId, Collections.singletonList(pq("A9", "分类A")), "分类A", Integer.MAX_VALUE);
        assertEquals(Arrays.asList("A0", "A1", "A2", "A9", "B1"), order());
    }

    @Test
    public void insertIntoNewCategoryAppendsAtEnd() {
        Db.insertQuestions(bankId, Collections.singletonList(pq("C1", "分类C")), "分类C", 0);
        assertEquals(Arrays.asList("A1", "A2", "B1", "C1"), order());
        assertEquals(Arrays.asList("分类A", "分类B", "分类C"), Db.categoryOrder(bankId));
    }

    @Test
    public void moveQuestionToOtherCategoryAtPosition() {
        // 把 B 的第一题插到分类 A 的第 2 位（A 原来有 A1、A2）
        Db.moveQuestion(q2, "分类A", 1);

        assertEquals(Arrays.asList("A1", "B1", "A2"), order());
        assertEquals("分类A", Db.getQuestion(q2).category);
        assertEquals(1, Db.getQuestion(q2).qindex);
        assertEquals("A 类题数应增加到 3", 3, Db.categoryCount(bankId, "分类A"));
    }

    @Test
    public void moveQuestionToEndOfCategory() {
        Db.moveQuestion(q0, "分类B", Integer.MAX_VALUE);
        assertEquals(Arrays.asList("A2", "B1", "A1"), order());
        assertEquals("分类B", Db.getQuestion(q0).category);
        assertEquals(2, Db.categoryCount(bankId, "分类B"));
    }

    @Test
    public void movingKeepsAnswerRecords() {
        Db.addRecord(bankId, q1, 0, "A", true, false);
        assertNotNull(Db.lastAnswer(q1));

        Db.moveQuestion(q1, "分类B", 0);
        assertNotNull("移动后做题记录应还在", Db.lastAnswer(q1));
        assertEquals(1, Db.globalStats()[0]);
    }

    @Test
    public void deleteQuestionCompactsIndexAndKeepsStats() {
        Db.addRecord(bankId, q0, 0, "A", true, false);
        Db.deleteQuestion(q0);

        assertEquals(Arrays.asList("A2", "B1"), order());
        List<Long> ids = Db.questionIds(bankId);
        assertEquals(0, Db.getQuestion(ids.get(0)).qindex);
        assertEquals(1, Db.getQuestion(ids.get(1)).qindex);
        assertEquals("该题的记录随题删除", 0, Db.listBanks().get(0).recordCount);
        assertEquals("总统计不受删除影响", 1, Db.globalStats()[0]);
    }

    @Test
    public void insertKeepsExistingRecords() {
        Db.addRecord(bankId, q2, 0, "A", true, false);
        Db.insertQuestions(bankId, Collections.singletonList(pq("A1.5", "分类A")), "分类A", 1);

        assertNotNull(Db.lastAnswer(q2));
        assertEquals(1, Db.globalStats()[0]);
        assertEquals(4, Db.questionIds(bankId).size());
    }
}
