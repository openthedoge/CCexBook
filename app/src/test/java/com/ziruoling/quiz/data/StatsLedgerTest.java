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
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 总统计账本：删题库、清空练习记录都不应影响首页/统计页的累计数字；
 * 只有「清空统计」能归零，且不影响做题记录。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class StatsLedgerTest {

    private long bankId;
    private long singleQid;
    private long judgeQid;

    @Before
    public void setUp() {
        Db.resetForTest();
        Db.init(RuntimeEnvironment.getApplication());

        List<ParsedQuestion> list = new ArrayList<>();
        ParsedQuestion q1 = new ParsedQuestion();
        q1.type = QuestionType.SINGLE;
        q1.content = "单选题一";
        q1.options = Arrays.asList("甲", "乙", "丙");
        q1.answer = "A";
        list.add(q1);

        ParsedQuestion q2 = new ParsedQuestion();
        q2.type = QuestionType.JUDGE;
        q2.content = "判断题一";
        q2.options = Arrays.asList("正确", "错误");
        q2.answer = "对";
        list.add(q2);

        ParsedQuestion q3 = new ParsedQuestion();
        q3.type = QuestionType.SINGLE;
        q3.content = "单选题二";
        q3.options = Arrays.asList("甲", "乙");
        q3.answer = "B";
        list.add(q3);

        bankId = Db.createBank("统计测试", "test");
        Db.importQuestions(bankId, list);
        List<Long> ids = Db.questionIds(bankId);
        singleQid = ids.get(0);
        judgeQid = ids.get(1);
    }

    private void answerThree() {
        Db.addRecord(bankId, singleQid, 0, "A", true, true);    // 对
        Db.addRecord(bankId, judgeQid, 0, "B", false, false);   // 错
        Db.addRecord(bankId, singleQid, 0, "A", true, true);    // 对
    }

    /** 同一题反复做：每次都要计入总统计（1 错 + 2 对 = 做 3 次、对 2 次） */
    @Test
    public void repeatedAttemptsCountEveryTime() {
        Db.addRecord(bankId, singleQid, 0, "B", false, false);
        Db.addRecord(bankId, singleQid, 0, "A", true, true);
        Db.addRecord(bankId, singleQid, 0, "A", true, true);

        long[] g = Db.globalStats();
        assertEquals("同一题做 3 次就计 3 次", 3, g[0]);
        assertEquals("其中对 2 次", 2, g[1]);
        assertEquals("题库只有 3 道题，但作答次数可以超过题量", 3, Db.questionIds(bankId).size());
        assertEquals(3, Db.typeStats()[QuestionType.SINGLE][1]);

        Db.deleteBank(bankId);
        assertEquals("删题库后总作答数不变", 3, Db.globalStats()[0]);
        assertEquals("删题库后总正确数不变", 2, Db.globalStats()[1]);
    }

    /** 考试作答（mode=1）同样计入总统计 */
    @Test
    public void examAnswersAlsoCount() {
        Db.addRecord(bankId, judgeQid, 1, "A", true, true);
        long[] g = Db.globalStats();
        assertEquals(1, g[0]);
        assertEquals(1, g[1]);
        assertEquals(1, Db.typeStats()[QuestionType.JUDGE][1]);
    }

    @Test
    public void answeringBumpsLedger() {
        answerThree();
        long[] g = Db.globalStats();
        assertEquals(3, g[0]);
        assertEquals(2, g[1]);
        assertEquals(3, Db.todayCount());
        assertEquals(3, Db.last7Days()[0]);
        assertEquals(1, Db.streak());

        int[][] ts = Db.typeStats();
        assertEquals("单选：对 2 / 共 2", 2, ts[QuestionType.SINGLE][0]);
        assertEquals(2, ts[QuestionType.SINGLE][1]);
        assertEquals("判断：对 0 / 共 1", 0, ts[QuestionType.JUDGE][0]);
        assertEquals(1, ts[QuestionType.JUDGE][1]);
    }

    @Test
    public void deletingBankKeepsTotals() {
        answerThree();
        Db.deleteBank(bankId);

        assertEquals("题库已删", 0, Db.questionIds(bankId).size());
        long[] g = Db.globalStats();
        assertEquals("删题库不应影响总作答数", 3, g[0]);
        assertEquals("删题库不应影响总正确数", 2, g[1]);
        assertEquals("今日做题数应保留", 3, Db.todayCount());
        assertEquals("题型统计应保留", 2, Db.typeStats()[QuestionType.SINGLE][1]);
    }

    @Test
    public void clearingRecordsKeepsTotals() {
        answerThree();
        Db.clearRecords();

        long[] g = Db.globalStats();
        assertEquals(3, g[0]);
        assertEquals(2, g[1]);
        assertEquals(3, Db.todayCount());
        assertEquals("记录确实清掉了", 0, Db.listBanks().get(0).recordCount);
    }

    @Test
    public void clearStatsZeroesLedgerButKeepsRecords() {
        answerThree();
        Db.clearStats();

        long[] g = Db.globalStats();
        assertEquals(0, g[0]);
        assertEquals(0, g[1]);
        assertEquals(0, Db.todayCount());
        assertEquals(0, Db.streak());
        assertEquals(0, Db.typeStats()[QuestionType.SINGLE][1]);
        assertEquals("做题记录不受清空统计影响", 3, Db.listBanks().get(0).recordCount);
    }

    @Test
    public void newAnswerAfterClearStatsStartsFromZero() {
        answerThree();
        Db.clearStats();
        Db.addRecord(bankId, singleQid, 0, "A", true, true);

        long[] g = Db.globalStats();
        assertEquals(1, g[0]);
        assertEquals(1, g[1]);
    }

    @Test
    public void rejudgeAdjustsCorrectCount() {
        Db.addRecord(bankId, singleQid, 0, "A", true, true);
        assertEquals(1, Db.globalStats()[1]);

        // 把答案改成 B，重新判定：原来那次的 A 就变成错了
        Db.updateAnswer(singleQid, "B");
        Db.rejudgeQuestion(singleQid);

        long[] g = Db.globalStats();
        assertEquals("作答次数不变", 1, g[0]);
        assertEquals("正确数应减 1", 0, g[1]);
        assertEquals(1, Db.typeStats()[QuestionType.SINGLE][1]);
        assertEquals(0, Db.typeStats()[QuestionType.SINGLE][0]);
    }

    @Test
    public void ledgerStartsEmpty() {
        long[] g = Db.globalStats();
        assertEquals(0, g[0]);
        assertEquals(0, g[1]);
        assertTrue(Db.streak() == 0);
        assertEquals(0, Db.todayCount());
    }
}
