package com.ziruoling.quiz.ui;

import com.ziruoling.quiz.core.QuestionType;
import com.ziruoling.quiz.data.Db;
import com.ziruoling.quiz.data.DemoBank;
import com.ziruoling.quiz.data.Prefs;
import com.ziruoling.quiz.data.Question;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 题目浏览页的题型筛选：筛选条下标 → 掩码必须一一对应，不能再错位 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class QuestionFilterTest {

    private long bankId;

    @Before
    public void setUp() {
        Db.resetForTest();
        Prefs.resetForTest();
        Db.init(RuntimeEnvironment.getApplication());
        Prefs.init(RuntimeEnvironment.getApplication());
        bankId = Db.createBank("测试题库", "测试");
        Db.importQuestions(bankId, DemoBank.build());
    }

    @Test
    public void chipIndexMapsToTypeBit() {
        assertEquals("「全部」应表示不限", -1, QuestionListActivity.chipMask(0));
        for (int t = 0; t < QuestionType.COUNT; t++) {
            assertEquals("第 " + (t + 1) + " 个 chip 应选中 " + QuestionType.NAMES[t],
                    1 << t, QuestionListActivity.chipMask(t + 1));
        }
    }

    @Test
    public void multiChipReturnsOnlyMultiQuestions() {
        int mask = QuestionListActivity.chipMask(QuestionType.MULTI + 1);
        List<Long> ids = Db.listIds(bankId, mask, null, -1, false, false, null);
        assertTrue("示例题库应有多选题", !ids.isEmpty());
        for (long id : ids) {
            Question q = Db.getQuestion(id);
            assertEquals("筛选「多选」时不应混入其它题型", QuestionType.MULTI, q.type);
        }
    }

    @Test
    public void everyTypeChipMatchesItsOwnType() {
        for (int t = 0; t < QuestionType.COUNT; t++) {
            int count = Db.typeCounts(bankId)[t];
            if (count <= 0) continue;
            int mask = QuestionListActivity.chipMask(t + 1);
            List<Long> ids = Db.listIds(bankId, mask, null, -1, false, false, null);
            assertEquals(QuestionType.NAMES[t] + " 数量应一致", count, ids.size());
            for (long id : ids) {
                assertEquals(QuestionType.NAMES[t] + " 筛选混入其它题型", t, Db.getQuestion(id).type);
            }
        }
    }

    @Test
    public void emptyMaskReturnsNothingInsteadOfCrashing() {
        List<Long> ids = Db.listIds(bankId, 0, null, -1, false, false, null);
        assertTrue(ids.isEmpty());
    }
}
