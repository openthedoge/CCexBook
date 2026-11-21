package com.ziruoling.quiz.ui;

import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;

import com.ziruoling.quiz.R;
import com.ziruoling.quiz.core.QuestionType;
import com.ziruoling.quiz.data.Db;
import com.ziruoling.quiz.data.DemoBank;
import com.ziruoling.quiz.data.Prefs;
import com.ziruoling.quiz.data.Question;
import com.ziruoling.quiz.util.ShareUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 练习状态持久化：重进回填「已答」、重做本题、改答案重判、分享文案二选一。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PracticeRestoreTest {

    @Before
    public void setUp() {
        Db.resetForTest();
        Prefs.resetForTest();
        Db.init(RuntimeEnvironment.getApplication());
        Prefs.init(RuntimeEnvironment.getApplication());
    }

    private long seed() {
        long bankId = Db.createBank("测试题库", "测试");
        Db.importQuestions(bankId, DemoBank.build());
        return bankId;
    }

    private long firstSingle(long bankId) {
        List<Long> ids = Db.questionIdsByFilter(bankId, 1 << QuestionType.SINGLE, null, false);
        assertFalse("示例题库应含单选题", ids.isEmpty());
        return ids.get(0);
    }

    private ActivityController<PracticeActivity> open(long bankId, long qid, boolean restore) {
        Intent it = new Intent(RuntimeEnvironment.getApplication(), PracticeActivity.class)
                .putExtra("ids", new long[]{qid})
                .putExtra("start", 0)
                .putExtra("mode", PracticeActivity.MODE_PRACTICE)
                .putExtra("bank", bankId)
                .putExtra("title", "测试")
                .putExtra("progress", false)
                .putExtra("restore", restore);
        return Robolectric.buildActivity(PracticeActivity.class, it).setup();
    }

    private LinearLayout optionsOf(PracticeActivity a) {
        return a.findViewById(R.id.options);
    }

    /** 答完一题后重进：应回填成「已答」（答案卡可见），且不能再重复作答 */
    @Test
    public void answeredStateRestoredOnReopen() {
        long bankId = seed();
        long qid = firstSingle(bankId);

        ActivityController<PracticeActivity> firstC = open(bankId, qid, true);
        PracticeActivity first = firstC.get();
        LinearLayout options = optionsOf(first);
        assertTrue(options.getChildCount() >= 2);
        options.getChildAt(0).performClick();
        assertEquals("作答应即时入库", 1, Db.globalStats()[0]);
        assertEquals(View.VISIBLE, first.findViewById(R.id.resultCard).getVisibility());
        firstC.destroy();

        ActivityController<PracticeActivity> againC = open(bankId, qid, true);
        PracticeActivity again = againC.get();
        assertEquals("重进后应回填为已答状态",
                View.VISIBLE, again.findViewById(R.id.resultCard).getVisibility());
        optionsOf(again).getChildAt(1).performClick();
        assertEquals("已答的题不应再产生记录", 1, Db.globalStats()[0]);
        againC.destroy();
    }

    /** 未开启回填的模式（随机/错题重练等）：仍按未作答呈现 */
    @Test
    public void restoreDisabledKeepsQuestionUnanswered() {
        long bankId = seed();
        long qid = firstSingle(bankId);

        ActivityController<PracticeActivity> firstC = open(bankId, qid, false);
        optionsOf(firstC.get()).getChildAt(0).performClick();
        firstC.destroy();

        ActivityController<PracticeActivity> againC = open(bankId, qid, false);
        PracticeActivity again = againC.get();
        assertEquals(View.GONE, again.findViewById(R.id.resultCard).getVisibility());
        optionsOf(again).getChildAt(1).performClick();
        assertEquals(2, Db.globalStats()[0]);
        againC.destroy();
    }

    /** 修改正确答案后：历史作答保留，但按新答案重判，错题本同步 */
    @Test
    public void rejudgeAfterAnswerChanged() {
        long bankId = seed();
        long qid = firstSingle(bankId);
        Question q = Db.getQuestion(qid);
        String right = q.answer == null ? "" : q.answer.trim();
        String wrong = "A".equals(right) ? "B" : "A";

        ActivityController<PracticeActivity> c = open(bankId, qid, true);
        int wrongIdx = wrong.charAt(0) - 'A';
        optionsOf(c.get()).getChildAt(wrongIdx).performClick();
        Db.LastAnswer after = Db.lastAnswer(qid);
        assertNotNull(after);
        assertFalse("故意答错", after.correct);
        assertEquals(1, Db.wrongCount(bankId, 0));

        Db.updateAnswer(qid, wrong);
        Db.rejudgeQuestion(qid);

        Db.LastAnswer la = Db.lastAnswer(qid);
        assertNotNull(la);
        assertTrue("改答案后应按新答案重判为正确", la.correct);
        assertEquals("用户当时选的答案不应被改写", wrong, la.userAnswer);
        assertEquals("错题应随之移除", 0, Db.wrongCount(bankId, 0));
        c.destroy();
    }

    /** 重做本题：记录与错题标记一并清空 */
    @Test
    public void resetQuestionClearsRecord() {
        long bankId = seed();
        long qid = firstSingle(bankId);
        ActivityController<PracticeActivity> c = open(bankId, qid, true);
        optionsOf(c.get()).getChildAt(0).performClick();
        assertEquals(1, Db.globalStats()[0]);

        Db.resetQuestion(qid);
        assertNull(Db.lastAnswer(qid));
        assertEquals("「重做本题」只清该题的作答记录，不影响首页累计统计", 1, Db.globalStats()[0]);
        c.destroy();
    }

    /** 长按答案卡应弹出「修改答案 / 编辑解析 / 重做本题」菜单 */
    @Test
    public void longPressResultCardOpensMenu() {
        long bankId = seed();
        long qid = firstSingle(bankId);
        ActivityController<PracticeActivity> c = open(bankId, qid, true);
        PracticeActivity a = c.get();
        optionsOf(a).getChildAt(0).performClick();

        View card = a.findViewById(R.id.resultCard);
        assertTrue(card.performLongClick());
        android.app.AlertDialog d = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull("长按答案卡应弹出操作菜单", d);
        assertNotNull(d.getListView());
        assertEquals("菜单应有三项", 3, d.getListView().getAdapter().getCount());
        c.destroy();
    }

    /** 答题卡要区分「本次作答」与「历史回填」：回填过的要显示为历史色，且仍锁定 */
    @Test
    public void answerSheetDistinguishesSessionAndHistory() {
        long bankId = seed();
        long qid = firstSingle(bankId);

        ActivityController<PracticeActivity> firstC = open(bankId, qid, true);
        PracticeActivity first = firstC.get();
        optionsOf(first).getChildAt(0).performClick();

        int[] c1 = new int[2];
        int[] s1 = first.sheetStates(c1);
        assertEquals("本次应记 1 题", 1, c1[0]);
        assertEquals("历史应为 0", 0, c1[1]);
        assertTrue("本次作答应为实心状态 1/2", s1[0] == 1 || s1[0] == 2);
        firstC.destroy();

        ActivityController<PracticeActivity> againC = open(bankId, qid, true);
        PracticeActivity again = againC.get();

        int[] c2 = new int[2];
        int[] s2 = again.sheetStates(c2);
        assertEquals("回填的题不应算本次作答", 0, c2[0]);
        assertEquals("回填的题应算历史", 1, c2[1]);
        assertTrue("回填应为历史状态 3/4", s2[0] == 3 || s2[0] == 4);

        // 回填的题仍应锁定，不能被重复作答
        optionsOf(again).getChildAt(1).performClick();
        assertEquals("已答的题不应再产生记录", 1, Db.globalStats()[0]);
        againC.destroy();
    }

    /** 「疑错」按钮在纯黑主题下也必须可辨认（曾经是近黑字压近黑底） */
    @Test
    public void flagButtonReadableInBlackTheme() {
        Prefs.theme(Prefs.THEME_BLACK);
        long bankId = seed();
        long qid = firstSingle(bankId);
        ActivityController<PracticeActivity> c = open(bankId, qid, true);
        android.widget.TextView flag = c.get().findViewById(R.id.btnFlag);
        assertNotNull(flag);
        assertTrue("纯黑主题下「疑错」按钮文字与底色对比度过低",
                contrast(flag.getCurrentTextColor(), flagBackground(flag)) > 3.0);
        c.destroy();
    }

    /** 浅色主题下同样要能看清 */
    @Test
    public void flagButtonReadableInLightTheme() {
        Prefs.theme(Prefs.THEME_LIGHT);
        long bankId = seed();
        long qid = firstSingle(bankId);
        ActivityController<PracticeActivity> c = open(bankId, qid, true);
        android.widget.TextView flag = c.get().findViewById(R.id.btnFlag);
        assertNotNull(flag);
        assertTrue("浅色主题下「疑错」按钮文字与底色对比度过低",
                contrast(flag.getCurrentTextColor(), flagBackground(flag)) > 3.0);
        c.destroy();
    }

    private int flagBackground(android.widget.TextView v) {
        android.graphics.drawable.Drawable d = v.getBackground();
        if (d instanceof android.graphics.drawable.GradientDrawable) {
            android.content.res.ColorStateList cs = ((android.graphics.drawable.GradientDrawable) d).getColor();
            if (cs != null) return cs.getDefaultColor();
        }
        return 0xFFFFFFFF;
    }

    /** WCAG 相对亮度对比度 */
    private double contrast(int c1, int c2) {
        double a = luminance(c1);
        double b = luminance(c2);
        double hi = Math.max(a, b);
        double lo = Math.min(a, b);
        return (hi + 0.05) / (lo + 0.05);
    }

    private double luminance(int color) {
        double[] ch = new double[3];
        int[] raw = {(color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF};
        for (int i = 0; i < 3; i++) {
            double v = raw[i] / 255.0;
            ch[i] = v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * ch[0] + 0.7152 * ch[1] + 0.0722 * ch[2];
    }

    /** 分享：不带答案的文本里不能出现答案与解析 */
    @Test
    public void shareTextCanOmitAnswer() {
        long bankId = seed();
        Question q = Db.getQuestion(firstSingle(bankId));

        String withAnswer = ShareUtil.buildQuestionText(
                q, "测试题库", 1, 10, q.options, q.answer, null, null, true);
        String withoutAnswer = ShareUtil.buildQuestionText(
                q, "测试题库", 1, 10, q.options, q.answer, null, null, false);

        assertTrue(withAnswer.contains("正确答案："));
        assertFalse("不带答案的分享不应含答案", withoutAnswer.contains("正确答案："));
        assertFalse("不带答案的分享不应含解析", withoutAnswer.contains("解析："));
        assertTrue(withoutAnswer.contains("【单选】"));
        assertTrue(withoutAnswer.contains("A. "));
    }
}
