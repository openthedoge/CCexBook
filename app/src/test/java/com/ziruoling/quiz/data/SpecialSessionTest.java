package com.ziruoling.quiz.data;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 专项训练会话：题库内唯一、可标记完成、可重置/删除 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SpecialSessionTest {

    private long bankId;

    @Before
    public void setUp() {
        Prefs.resetForTest();
        Prefs.init(org.robolectric.RuntimeEnvironment.getApplication());
        Db.resetForTest();
        Db.init(org.robolectric.RuntimeEnvironment.getApplication());
        bankId = Db.createBank("专项测试", "");
        Db.importQuestions(bankId, DemoBank.build());
    }

    @Test
    public void sessionLifecycle() {
        List<Long> ids = Db.questionIds(bankId);
        List<Long> picked = Arrays.asList(ids.get(0), ids.get(1));

        Db.saveSpecial(bankId, picked);
        assertTrue(Db.hasSpecial(bankId));
        assertEquals(2, Db.specialIds(bankId).size());

        Db.specialMark(bankId, picked.get(0), true);
        Map<Long, Boolean> done = Db.specialDone(bankId);
        assertEquals(1, done.size());
        assertTrue(done.get(picked.get(0)));

        int[] st = Db.specialStats(bankId);
        assertEquals(2, st[0]);
        assertEquals(1, st[1]);
        assertEquals(1, st[2]);

        Db.specialReset(bankId);
        assertEquals(0, Db.specialStats(bankId)[1]);

        Db.specialDelete(bankId);
        assertFalse(Db.hasSpecial(bankId));
    }

    @Test
    public void savingNewSessionReplacesOld() {
        List<Long> ids = Db.questionIds(bankId);
        Db.saveSpecial(bankId, Arrays.asList(ids.get(0), ids.get(1), ids.get(2)));
        Db.saveSpecial(bankId, Arrays.asList(ids.get(3)));
        assertEquals(1, Db.specialIds(bankId).size());
    }
}
