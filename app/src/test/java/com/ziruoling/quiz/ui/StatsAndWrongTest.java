package com.ziruoling.quiz.ui;

import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

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

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/** 统计口径 + 错题重做里的「移出错题本」 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class StatsAndWrongTest {

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
        assertFalse(ids.isEmpty());
        return ids.get(0);
    }

    private ActivityController<PracticeActivity> openWrong(long bankId, long qid) {
        return Robolectric.buildActivity(PracticeActivity.class,
                new Intent(RuntimeEnvironment.getApplication(), PracticeActivity.class)
                        .putExtra("ids", new long[]{qid})
                        .putExtra("start", 0)
                        .putExtra("mode", PracticeActivity.MODE_WRONG)
                        .putExtra("bank", bankId)
                        .putExtra("title", "错题重做")
                        .putExtra("progress", false)
                        .putExtra("restore", false)).setup();
    }

    /** 题型统计必须是 [正确数, 总数]，不能对调（对调会导致正确率 >100%、全错题型被隐藏） */
    @Test
    public void typeStatsReturnsCorrectThenTotal() {
        long bankId = seed();
        List<Long> singles = Db.questionIdsByFilter(bankId, 1 << QuestionType.SINGLE, null, false);
        long q1 = singles.get(0);
        long q2 = singles.get(1);
        Db.addRecord(bankId, q1, 0, "A", true, false);
        Db.addRecord(bankId, q2, 0, "B", false, false);

        int[][] ts = Db.typeStats();
        assertEquals("下标 0 应为正确数", 1, ts[QuestionType.SINGLE][0]);
        assertEquals("下标 1 应为总作答数", 2, ts[QuestionType.SINGLE][1]);

        int pct = Math.round(ts[QuestionType.SINGLE][0] * 100f / ts[QuestionType.SINGLE][1]);
        assertEquals("正确率应为 50%", 50, pct);
    }

    /** 全错题型也应出现在统计里（不能被当总数=0 跳过） */
    @Test
    public void typeStatsKeepsAllWrongTypeVisible() {
        long bankId = seed();
        List<Long> singles = Db.questionIdsByFilter(bankId, 1 << QuestionType.SINGLE, null, false);
        Db.addRecord(bankId, singles.get(0), 0, "A", false, false);

        int[][] ts = Db.typeStats();
        assertEquals(0, ts[QuestionType.SINGLE][0]);
        assertEquals(1, ts[QuestionType.SINGLE][1]);
        assertEquals(0, Math.round(ts[QuestionType.SINGLE][0] * 100f / ts[QuestionType.SINGLE][1]));
    }

    /** 错题重做答错后：显示「移出错题本」，点击后该题变已掌握 */
    @Test
    public void removeWrongButtonInWrongMode() {
        long bankId = seed();
        long qid = firstSingle(bankId);
        Db.addWrong(bankId, qid);
        assertEquals("初始应为未掌握", 1, Db.wrongStatus(qid));

        ActivityController<PracticeActivity> c = openWrong(bankId, qid);
        PracticeActivity a = c.get();
        TextView btn = a.findViewById(R.id.btnRemoveWrong);
        assertNotNull(btn);
        assertEquals("未作答时不应显示", View.GONE, btn.getVisibility());

        // 故意答错：选一个不是正确字母的选项
        Question q = Db.getQuestion(qid);
        String right = q.answer == null ? "" : q.answer.trim();
        int wrongIdx = "A".equals(right) ? 1 : 0;
        LinearLayout options = a.findViewById(R.id.options);
        options.getChildAt(wrongIdx).performClick();

        assertEquals("答错后应显示移出按钮", View.VISIBLE, btn.getVisibility());
        btn.performClick();
        assertEquals("点击后应变为已掌握", 2, Db.wrongStatus(qid));
        assertEquals(View.GONE, btn.getVisibility());
        c.destroy();
    }

    /** 关闭「答对自动移出」后：错题重做里答对也不应被移出，且要显示「移出错题本」 */
    @Test
    public void wrongModeRespectsAutoRemoveOff() {
        long bankId = seed();
        long qid = firstSingle(bankId);
        Db.addWrong(bankId, qid);
        Prefs.wrongAutoRemove(false);

        ActivityController<PracticeActivity> c = openWrong(bankId, qid);
        PracticeActivity a = c.get();
        Question q = Db.getQuestion(qid);
        int rightIdx = (q.answer == null ? "A" : q.answer.trim()).charAt(0) - 'A';
        ((LinearLayout) a.findViewById(R.id.options)).getChildAt(rightIdx).performClick();

        assertEquals("关闭自动移出后，答对不应移出错题本", 1, Db.wrongStatus(qid));
        assertEquals("应显示移出按钮供手动操作",
                View.VISIBLE, a.findViewById(R.id.btnRemoveWrong).getVisibility());
        c.destroy();
    }

    /** 开启「答对自动移出」后：错题重做里答对被移出，按钮自然不显示 */
    @Test
    public void wrongModeAutoRemovesWhenSettingOn() {
        long bankId = seed();
        long qid = firstSingle(bankId);
        Db.addWrong(bankId, qid);
        Prefs.wrongAutoRemove(true);

        ActivityController<PracticeActivity> c = openWrong(bankId, qid);
        PracticeActivity a = c.get();
        Question q = Db.getQuestion(qid);
        int rightIdx = (q.answer == null ? "A" : q.answer.trim()).charAt(0) - 'A';
        ((LinearLayout) a.findViewById(R.id.options)).getChildAt(rightIdx).performClick();

        assertEquals("开着自动移出时，答对应变已掌握", 2, Db.wrongStatus(qid));
        assertEquals(View.GONE, a.findViewById(R.id.btnRemoveWrong).getVisibility());
        c.destroy();
    }

    /** 普通练习模式下不出现该按钮 */
    @Test
    public void removeWrongButtonHiddenInNormalPractice() {
        long bankId = seed();
        long qid = firstSingle(bankId);
        Db.addWrong(bankId, qid);

        ActivityController<PracticeActivity> c = Robolectric.buildActivity(PracticeActivity.class,
                new Intent(RuntimeEnvironment.getApplication(), PracticeActivity.class)
                        .putExtra("ids", new long[]{qid})
                        .putExtra("start", 0)
                        .putExtra("mode", PracticeActivity.MODE_PRACTICE)
                        .putExtra("bank", bankId)
                        .putExtra("progress", false)).setup();
        PracticeActivity a = c.get();
        Question q = Db.getQuestion(qid);
        String right = q.answer == null ? "" : q.answer.trim();
        int wrongIdx = "A".equals(right) ? 1 : 0;
        ((LinearLayout) a.findViewById(R.id.options)).getChildAt(wrongIdx).performClick();
        assertEquals(View.GONE, a.findViewById(R.id.btnRemoveWrong).getVisibility());
        c.destroy();
    }
}
