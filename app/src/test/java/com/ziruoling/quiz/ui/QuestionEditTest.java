package com.ziruoling.quiz.ui;

import android.content.Intent;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.ziruoling.quiz.R;
import com.ziruoling.quiz.core.QuestionType;
import com.ziruoling.quiz.data.Db;
import com.ziruoling.quiz.data.DemoBank;
import com.ziruoling.quiz.data.Prefs;
import com.ziruoling.quiz.data.Question;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** 题目编辑器：字段落库、题型切换、图片增删 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class QuestionEditTest {

    @Before
    public void setUp() {
        Db.resetForTest();
        Prefs.resetForTest();
        Db.init(RuntimeEnvironment.getApplication());
        Prefs.init(RuntimeEnvironment.getApplication());
    }

    private long seedSingle() {
        long bankId = Db.createBank("测试题库", "测试");
        Db.importQuestions(bankId, DemoBank.build());
        List<Long> ids = Db.questionIdsByFilter(bankId, 1 << QuestionType.SINGLE, null, false);
        assertFalse(ids.isEmpty());
        return ids.get(0);
    }

    @Test
    public void updateQuestionPersistsAllFields() {
        long qid = seedSingle();
        Db.updateQuestion(qid, QuestionType.MULTI, "新的题干\n第二行",
                Arrays.asList("选项一", "选项二", "选项三"), "AC", "新的解析", "[]");

        Question q = Db.getQuestion(qid);
        assertNotNull(q);
        assertEquals(QuestionType.MULTI, q.type);
        assertEquals("新的题干\n第二行", q.content);
        assertEquals(3, q.options.size());
        assertEquals("AC", q.answer);
        assertEquals("新的解析", q.analysis);
    }

    @Test
    public void addAndDeleteImage() {
        long qid = seedSingle();
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                0, 0, 0, 0, 0, 0, 0, 0};
        String rel = Db.addImage(qid, jpeg);
        assertNotNull("应能识别 jpg 并落盘", rel);
        assertTrue(rel.startsWith("imgs/q" + qid + "_"));
        File f = Db.imgFile(rel);
        assertNotNull(f);
        assertTrue(f.isFile());

        Db.deleteImageFile(rel);
        assertFalse(f.exists());

        assertNull("非图片字节应被拒绝", Db.addImage(qid, new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}));
    }

    /** 在编辑器里把单选改成多选、填好答案并保存 */
    @Test
    public void editorSwitchesTypeAndSaves() {
        long qid = seedSingle();
        Question before = Db.getQuestion(qid);
        assertEquals(QuestionType.SINGLE, before.type);

        int multiIdx = QuestionType.MULTI;
        ActivityController<EditQuestionActivity> c = Robolectric.buildActivity(
                EditQuestionActivity.class,
                new Intent(RuntimeEnvironment.getApplication(), EditQuestionActivity.class)
                        .putExtra("qid", qid)).setup();

        LinearLayout chips = c.get().findViewById(R.id.typeChips);
        assertNotNull(chips);
        assertEquals(QuestionType.COUNT, chips.getChildCount());
        chips.getChildAt(multiIdx).performClick();

        EditText answer = c.get().findViewById(R.id.answerEt);
        answer.setText("AB");
        EditText content = c.get().findViewById(R.id.contentEt);
        content.setText("改过的题干");

        c.get().findViewById(R.id.btnSave).performClick();

        Question after = Db.getQuestion(qid);
        assertEquals("题型应改为多选", QuestionType.MULTI, after.type);
        assertEquals("AB", after.answer);
        assertEquals("改过的题干", after.content);
        assertTrue("保存后应关闭页面", c.get().isFinishing());
    }

    /** 答案格式不符合题型时应拦住，不落库 */
    @Test
    public void editorBlocksInvalidAnswer() {
        long qid = seedSingle();
        Question before = Db.getQuestion(qid);
        String oldAnswer = before.answer;

        ActivityController<EditQuestionActivity> c = Robolectric.buildActivity(
                EditQuestionActivity.class,
                new Intent(RuntimeEnvironment.getApplication(), EditQuestionActivity.class)
                        .putExtra("qid", qid)).setup();

        EditText answer = c.get().findViewById(R.id.answerEt);
        answer.setText("XYZ");           // 非法字母
        c.get().findViewById(R.id.btnSave).performClick();

        Question after = Db.getQuestion(qid);
        assertEquals("非法答案不应落库", oldAnswer, after.answer);
        assertFalse("应停留在编辑页", c.get().isFinishing());
        c.destroy();
    }

    /** 判断题切换后选项应固定为 正确/错误 */
    @Test
    public void judgeTypeUsesFixedOptions() {
        long qid = seedSingle();
        ActivityController<EditQuestionActivity> c = Robolectric.buildActivity(
                EditQuestionActivity.class,
                new Intent(RuntimeEnvironment.getApplication(), EditQuestionActivity.class)
                        .putExtra("qid", qid)).setup();

        LinearLayout chips = c.get().findViewById(R.id.typeChips);
        chips.getChildAt(QuestionType.JUDGE).performClick();

        EditText answer = c.get().findViewById(R.id.answerEt);
        answer.setText("对");
        c.get().findViewById(R.id.btnSave).performClick();

        Question after = Db.getQuestion(qid);
        assertEquals(QuestionType.JUDGE, after.type);
        assertEquals("A", after.answer);
        assertEquals(new ArrayList<>(Arrays.asList("正确", "错误")), after.options);
    }
}
