package com.ziruoling.quiz.data;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** 「直接添加题目」的草稿题必须能真正建出来，编辑器才能打开 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DraftQuestionTest {

    private long bankId;

    @Before
    public void setUp() {
        Prefs.resetForTest();
        Prefs.init(org.robolectric.RuntimeEnvironment.getApplication());
        Db.resetForTest();
        Db.init(org.robolectric.RuntimeEnvironment.getApplication());
        bankId = Db.createBank("草稿测试", "");
    }

    @Test
    public void draftCanBeOpenedInEditor() {
        long id = Db.createDraftQuestion(bankId);
        assertTrue("草稿 id 必须有效", id > 0);
        Question q = Db.getQuestion(id);
        assertNotNull("编辑器应能按 id 找到草稿题", q);
        assertTrue("草稿应属于当前题库", q.bankId == bankId);
    }
}
