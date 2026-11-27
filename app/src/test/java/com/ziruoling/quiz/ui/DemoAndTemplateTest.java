package com.ziruoling.quiz.ui;

import com.ziruoling.quiz.data.Db;
import com.ziruoling.quiz.data.Prefs;
import com.ziruoling.quiz.data.Question;
import com.ziruoling.quiz.data.TemplateDemoBank;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 内置示例题库必须与模板一致，且为生活化示例 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DemoAndTemplateTest {

    @Before
    public void setUp() {
        Prefs.resetForTest();
        Prefs.init(org.robolectric.RuntimeEnvironment.getApplication());
        Db.resetForTest();
        Db.init(org.robolectric.RuntimeEnvironment.getApplication());
    }

    @Test
    public void builtInDemoUsesLifeExamples() {
        assertEquals("内置示例应为 8 题", 8, TemplateDemoBank.build().size());
        StringBuilder all = new StringBuilder();
        for (com.ziruoling.quiz.core.ParsedQuestion q : TemplateDemoBank.build()) {
            all.append(q.content).append('|').append(q.category).append('\n');
        }
        String text = all.toString();
        for (String w : new String[]{"维生素 C", "奶制品", "睡眠", "首都", "米饭", "奶茶", "洗衣", "三明治"}) {
            assertTrue("示例应包含生活化内容: " + w + "\n实际: " + text, text.contains(w));
        }
        assertTrue("不应再使用网络基础示例", !text.contains("网络基础"));
    }

    @Test
    public void generateDemoCreatesSevenQuestions() throws Exception {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        Method m = MainActivity.class.getDeclaredMethod("createDemoBank");
        m.setAccessible(true);
        m.invoke(a);

        assertEquals("应生成一个示例题库", 1, Db.listBanks().size());
        long bankId = Db.listBanks().get(0).id;
        assertEquals("示例题库应包含 8 道题", 8, Db.questionIds(bankId).size());

        StringBuilder all = new StringBuilder();
        for (long qid : Db.questionIds(bankId)) {
            Question q = Db.getQuestion(qid);
            if (q != null) all.append(q.content).append('\n');
        }
        assertTrue("示例应包含早餐题", all.toString().contains("维生素 C"));
    }
}
