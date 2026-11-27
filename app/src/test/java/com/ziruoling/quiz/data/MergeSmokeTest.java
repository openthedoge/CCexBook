package com.ziruoling.quiz.data;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 1.11.final-1 合并冒烟：置顶取消、手动排序、专项重选 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MergeSmokeTest {

    private long a;
    private long b;

    @Before
    public void setUp() {
        Prefs.resetForTest();
        Prefs.init(org.robolectric.RuntimeEnvironment.getApplication());
        Db.resetForTest();
        Db.init(org.robolectric.RuntimeEnvironment.getApplication());
        a = Db.createBank("题库A", "");
        b = Db.createBank("题库B", "");
        Db.touchBank(a);
        try {
            Thread.sleep(5);
        } catch (InterruptedException ignored) {
        }
        Db.touchBank(b);   // B 最近使用
    }

    @Test
    public void unpinRestoresTimeOrder() {
        Db.setBankPinned(a, true);
        assertEquals("置顶后 A 应在最前", a, Db.listBanks().get(0).id);
        Db.setBankPinned(a, false);
        assertEquals("取消置顶后按最近使用排序，B 在前", b, Db.listBanks().get(0).id);
    }

    @Test
    public void manualOrderApplied() {
        Db.saveBankOrder(Arrays.asList(b, a));
        List<Bank> list = Db.listBanks();
        assertEquals(b, list.get(0).id);
        assertEquals(a, list.get(1).id);
    }

    @Test
    public void specialResetKeepsListButClearsDone() {
        List<Long> ids = Arrays.asList(a, b);
        Db.saveSpecial(a, ids);
        Db.specialMark(a, b, true);
        assertEquals(1, Db.specialStats(a)[1]);

        Db.specialReset(a);
        assertEquals("重做清空已做", 0, Db.specialStats(a)[1]);
        assertEquals("重做保留题目", 2, Db.specialIds(a).size());

        Db.specialDelete(a);
        assertFalse("重选删除会话", Db.hasSpecial(a));
    }
}
