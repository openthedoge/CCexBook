package com.ziruoling.quiz.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import com.ziruoling.quiz.R;
import com.ziruoling.quiz.data.Db;
import com.ziruoling.quiz.data.DemoBank;
import com.ziruoling.quiz.data.Prefs;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowDialog;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * UI 冒烟测试：在 JVM 上真实启动每个界面并模拟作答/交卷，
 * 用于在交付前拦截启动崩溃（如 findViewById 类型不匹配等）。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SmokeTest {

    private Context app() {
        return RuntimeEnvironment.getApplication();
    }

    @Before
    public void setUp() {
        // Robolectric 每个用例都会重建 Application，且在 @Before 之前已创建，
        // 需释放上个用例遗留的静态单例后重新绑定当前用例
        Db.resetForTest();
        Prefs.resetForTest();
        Db.init(RuntimeEnvironment.getApplication());
        Prefs.init(RuntimeEnvironment.getApplication());
    }

    private long seedBank() {
        long bankId = Db.createBank("测试题库", "测试");
        Db.importQuestions(bankId, DemoBank.build());
        return bankId;
    }

    private long[] arr(List<Long> list) {
        long[] a = new long[list.size()];
        for (int i = 0; i < a.length; i++) a[i] = list.get(i);
        return a;
    }

    /** 全部布局可正常解析（需带主题的上下文，与真机 Activity 渲染一致） */
    @Test
    public void inflateAllLayouts() {
        android.content.Context themed =
                new android.view.ContextThemeWrapper(app(), R.style.AppTheme_Light);
        int[] layouts = {
                R.layout.activity_main, R.layout.activity_import, R.layout.activity_bank_home,
                R.layout.activity_practice, R.layout.activity_exam, R.layout.activity_exam_config,
                R.layout.activity_exam_result, R.layout.activity_question_list,
                R.layout.activity_stats, R.layout.activity_settings,
                R.layout.item_bank, R.layout.item_question_row, R.layout.item_stat_row};
        for (int id : layouts) {
            assertNotNull("布局解析失败: " + themed.getResources().getResourceName(id),
                    android.view.LayoutInflater.from(themed).inflate(id, null, false));
        }
    }

    @Test
    public void mainActivityLaunch() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertNotNull(a.findViewById(R.id.list));
        assertNotNull(a.findViewById(R.id.emptyView));
    }

    /** v1.13.0 界面美化：主按钮/快捷入口带按压波纹，空状态有图标，列表项卡片化 */
    @Test
    public void uiPolishStyles() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertTrue("导入按钮应为波纹背景",
                a.findViewById(R.id.btnImport).getBackground() instanceof android.graphics.drawable.RippleDrawable);
        assertTrue("快捷入口应为胶囊波纹背景",
                a.findViewById(R.id.quickWrong).getBackground() instanceof android.graphics.drawable.RippleDrawable);
        assertNotNull("空状态应有图标", a.findViewById(R.id.emptyIcon));
        assertEquals("无题库时空状态图标应可见", View.VISIBLE, a.findViewById(R.id.emptyIcon).getVisibility());
        android.content.Context themed =
                new android.view.ContextThemeWrapper(app(), R.style.AppTheme_Light);
        View bank = android.view.LayoutInflater.from(themed).inflate(R.layout.item_bank, null, false);
        assertTrue("题库列表项应为可点击卡片（波纹）",
                bank.getBackground() instanceof android.graphics.drawable.RippleDrawable);
    }

    @Test
    public void bankHomeLaunch() {
        long bankId = seedBank();
        BankHomeActivity a = Robolectric.buildActivity(BankHomeActivity.class,
                new Intent(app(), BankHomeActivity.class)
                        .putExtra("bank_id", bankId)).setup().get();
        assertNotNull(a.findViewById(R.id.grid));
        int[] tc = Db.typeCounts(bankId);
        int total = 0;
        for (int t : tc) total += t;
        assertEquals("DemoBank 应含 37 题（34 + 排序 2 + 看图 1）", 37, total);
    }

    /** 排序题：按点击顺序排号作答，排满自动判分落库 */
    @Test
    public void sortQuestionFlow() {
        long bankId = seedBank();
        List<Long> sortIds = Db.questionIdsByFilter(bankId, 1 << com.ziruoling.quiz.core.QuestionType.SORT, null, false);
        assertTrue(sortIds.size() >= 2);
        PracticeActivity a = Robolectric.buildActivity(PracticeActivity.class,
                new Intent(app(), PracticeActivity.class)
                        .putExtra("ids", arr(sortIds))
                        .putExtra("start", 0)
                        .putExtra("mode", PracticeActivity.MODE_PRACTICE)
                        .putExtra("bank", bankId)
                        .putExtra("title", "排序练习")
                        .putExtra("progress", false)).setup().get();
        LinearLayout options = a.findViewById(R.id.options);
        assertTrue(options.getChildCount() >= 3);
        // 「重新排序」按钮可见
        android.widget.TextView btnSort = a.findViewById(R.id.btnMultiConfirm);
        assertNotNull(btnSort);
        // 依次点击全部选项：排满自动判分落库
        for (int i = 0; i < options.getChildCount(); i++) {
            options.getChildAt(i).performClick();
        }
        assertEquals(1, Db.globalStats()[0]);
        // 下一题再点一轮（第二道排序题）
        a.findViewById(R.id.btnNext).performClick();
        options = a.findViewById(R.id.options);
        for (int i = 0; i < options.getChildCount(); i++) {
            options.getChildAt(i).performClick();
        }
        assertEquals(2, Db.globalStats()[0]);
    }

    /** 试卷导出页可启动并展示题型抽题行 */
    @Test
    public void exportExamLaunch() {
        long bankId = seedBank();
        ExportExamActivity a = Robolectric.buildActivity(ExportExamActivity.class,
                new Intent(app(), ExportExamActivity.class)
                        .putExtra("bank", bankId)).setup().get();
        assertNotNull(a.findViewById(R.id.typeRows));
        assertNotNull(a.findViewById(R.id.btnExport));
    }

    @Test
    public void practiceLaunchAnswerAndSheet() {
        long bankId = seedBank();
        List<Long> ids = Db.questionIds(bankId);
        PracticeActivity a = Robolectric.buildActivity(PracticeActivity.class,
                new Intent(app(), PracticeActivity.class)
                        .putExtra("ids", arr(ids))
                        .putExtra("start", 0)
                        .putExtra("mode", PracticeActivity.MODE_PRACTICE)
                        .putExtra("bank", bankId)
                        .putExtra("title", "顺序练习")
                        .putExtra("progress", true)).setup().get();

        LinearLayout options = a.findViewById(R.id.options);
        assertTrue(options.getChildCount() >= 2);
        // 点击第一个选项（单选题：点选即判）
        options.getChildAt(0).performClick();
        assertEquals(1, Db.globalStats()[0]);
        // 答题卡
        a.findViewById(R.id.btnSheet).performClick();
        assertNotNull(ShadowDialog.getLatestDialog());
        // 下一题
        a.findViewById(R.id.btnNext).performClick();
        // 收藏与疑错标记
        a.findViewById(R.id.btnFav).performClick();
        a.findViewById(R.id.btnFlag).performClick();
        assertEquals(1, Db.favCount());
    }

    @Test
    public void practiceReciteMode() {
        long bankId = seedBank();
        List<Long> ids = Db.questionIds(bankId);
        PracticeActivity a = Robolectric.buildActivity(PracticeActivity.class,
                new Intent(app(), PracticeActivity.class)
                        .putExtra("ids", arr(ids))
                        .putExtra("start", 0)
                        .putExtra("mode", PracticeActivity.MODE_RECITE)
                        .putExtra("bank", bankId)
                        .putExtra("title", "背题模式")
                        .putExtra("progress", false)).setup().get();
        // 背题模式不写记录
        assertEquals(0, Db.globalStats()[0]);
        // 答案默认隐藏；点「显示答案」后显隐状态全局记忆，换题保持
        TextView reveal = a.findViewById(R.id.btnReveal);
        TextView answerTv = a.findViewById(R.id.answerTv);
        assertNotNull(reveal);
        assertEquals(View.VISIBLE, reveal.getVisibility());
        assertEquals(View.GONE, answerTv.getVisibility());
        reveal.performClick();
        assertEquals(View.VISIBLE, answerTv.getVisibility());
        a.findViewById(R.id.btnNext).performClick();
        assertEquals("显示后换题仍显示", View.VISIBLE, answerTv.getVisibility());
        reveal.performClick();
        assertEquals(View.GONE, answerTv.getVisibility());
        a.findViewById(R.id.btnNext).performClick();
        assertEquals("隐藏后换题仍隐藏", View.GONE, answerTv.getVisibility());
    }

    /** 背题固定乱序：同种子顺序稳定（续做位置有效）、异种子顺序不同、集合不丢不重 */
    @Test
    public void reciteShuffleStable() {
        long[] base = new long[10];
        for (int i = 0; i < 10; i++) base[i] = 100 + i;
        long[] a1 = base.clone();
        PracticeActivity.reciteShuffle(a1, 77L);
        long[] a2 = base.clone();
        PracticeActivity.reciteShuffle(a2, 77L);
        assertTrue("同一题库种子每次进入顺序应相同", java.util.Arrays.equals(a1, a2));
        long[] a3 = base.clone();
        PracticeActivity.reciteShuffle(a3, 999L);
        assertFalse("不同题库种子顺序应不同", java.util.Arrays.equals(a1, a3));
        long[] s1 = a1.clone();
        java.util.Arrays.sort(s1);
        assertTrue("乱序不应丢失或重复题目", java.util.Arrays.equals(s1, base));
        // 少于 2 题不打乱
        long[] one = {5L};
        PracticeActivity.reciteShuffle(one, 77L);
        assertEquals(5L, one[0]);
    }

    /** 左右滑动切题：全屏手势事件派发不崩溃（真机为左右滑） */
    @Test
    public void practiceSwipeGesture() {
        long bankId = seedBank();
        List<Long> ids = Db.questionIds(bankId);
        PracticeActivity a = Robolectric.buildActivity(PracticeActivity.class,
                new Intent(app(), PracticeActivity.class)
                        .putExtra("ids", arr(ids))
                        .putExtra("start", 0)
                        .putExtra("mode", PracticeActivity.MODE_PRACTICE)
                        .putExtra("bank", bankId)
                        .putExtra("title", "顺序练习")
                        .putExtra("progress", false)).setup().get();
        long t = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent down = android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, 600, 800, 0);
        android.view.MotionEvent move = android.view.MotionEvent.obtain(t, t + 30, android.view.MotionEvent.ACTION_MOVE, 200, 800, 0);
        android.view.MotionEvent up = android.view.MotionEvent.obtain(t, t + 60, android.view.MotionEvent.ACTION_UP, 100, 800, 0);
        a.dispatchTouchEvent(down);
        a.dispatchTouchEvent(move);
        a.dispatchTouchEvent(up);
        down.recycle();
        move.recycle();
        up.recycle();
        TextView counter = a.findViewById(R.id.counter);
        assertNotNull(counter.getText());
    }

    /** 富文本渲染：HTML 实体解码、上下标 span 化、波浪规范化，渲染与切题动画不崩 */
    @Test
    public void practiceRichTextRender() {
        long bankId = Db.createBank("富文本测试", "测试");
        com.ziruoling.quiz.core.ParsedQuestion pq = new com.ziruoling.quiz.core.ParsedQuestion();
        pq.type = com.ziruoling.quiz.core.QuestionType.SINGLE;
        pq.content = "水分子 H&#8322;O 与光速 2.998×10&#179; m/s\uFF5E衰减 50%"; // 实体+上标+全角波浪
        pq.options = java.util.Arrays.asList("H&#8322;O 对", "2&#178; m/s\u301C", "50%", "80%");
        pq.answer = "A";
        pq.analysis = "解析含 W\u2082 与 cm&#178; 以及 A\u301CB";
        Db.importQuestions(bankId, java.util.Collections.singletonList(pq));
        List<Long> ids = Db.questionIds(bankId);
        assertEquals(1, ids.size());

        PracticeActivity a = Robolectric.buildActivity(PracticeActivity.class,
                new Intent(app(), PracticeActivity.class)
                        .putExtra("ids", arr(ids))
                        .putExtra("start", 0)
                        .putExtra("mode", PracticeActivity.MODE_PRACTICE)
                        .putExtra("bank", bankId)
                        .putExtra("title", "富文本")
                        .putExtra("progress", false)).setup().get();
        // 题干：实体已解码为上下标字符并 span 化（字面变普通数字）
        TextView content = a.findViewById(R.id.content);
        assertTrue(content.getText().toString().contains("H2O"));
        assertTrue(content.getText().toString().contains("103"));
        assertTrue("波浪应统一为 ASCII ~（任何字体可见）", content.getText().toString().contains("~"));
        // 选项：同样的处理
        LinearLayout options = a.findViewById(R.id.options);
        TextView opt0 = (TextView) ((LinearLayout) options.getChildAt(0)).getChildAt(1);
        assertTrue(opt0.getText().toString().contains("H2O"));
        TextView opt1 = (TextView) ((LinearLayout) options.getChildAt(1)).getChildAt(1);
        assertTrue("选项实体+波浪应解码并规范化", opt1.getText().toString().contains("22"));
        assertTrue(opt1.getText().toString().contains("~"));
        // 作答显示答案/解析（含上下标 span 的路径）不崩
        options.getChildAt(0).performClick();
        // 切题动画路径（下一题）不崩
        a.findViewById(R.id.btnNext).performClick();
    }

    @Test
    public void sequenceResumeDialog() {
        long bankId = seedBank();
        List<Long> ids = Db.questionIds(bankId);
        Db.setProgress(bankId, ids.get(2));
        BankHomeActivity a = Robolectric.buildActivity(BankHomeActivity.class,
                new Intent(app(), BankHomeActivity.class)
                        .putExtra("bank_id", bankId)).setup().get();
        android.widget.GridLayout grid = a.findViewById(R.id.grid);
        View target = null;
        for (int i = 0; i < grid.getChildCount(); i++) {
            View card = grid.getChildAt(i);
            if (!(card instanceof LinearLayout)) continue;
            View first = ((LinearLayout) card).getChildAt(0);
            if (first instanceof TextView && "顺序练习".equals(((TextView) first).getText().toString())) {
                target = card;
                break;
            }
        }
        assertNotNull(target);
        target.performClick();
        AlertDialog d = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(d);
        // 开始方式改成了列表：从上次继续 / 重新开始 / 自定义起始题号
        android.widget.ListView lv = d.getListView();
        int pos = -1;
        for (int i = 0; i < lv.getAdapter().getCount(); i++) {
            if (lv.getAdapter().getItem(i).toString().contains("从上次")) {
                pos = i;
                break;
            }
        }
        assertTrue("有进度时应提供「从上次继续」", pos >= 0);
        lv.performItemClick(null, pos, pos);
        // Robolectric 下对话框按钮经 Handler 异步派发，先跑完主线程队列
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        Intent next = Shadows.shadowOf(a).getNextStartedActivity();
        assertNotNull(next);
        assertEquals(PracticeActivity.class.getName(), next.getComponent().getClassName());
        assertEquals(2, next.getIntExtra("start", 0));
    }

    @Test
    public void questionListLaunchAndSearch() {
        long bankId = seedBank();
        QuestionListActivity a = Robolectric.buildActivity(QuestionListActivity.class,
                new Intent(app(), QuestionListActivity.class)
                        .putExtra("bank", bankId)
                        .putExtra("scope", QuestionListActivity.SCOPE_ALL)
                        .putExtra("filter", QuestionListActivity.FILTER_ALL)
                        .putExtra("title", "全部题目")).setup().get();
        assertNotNull(a.findViewById(R.id.list));
        EditText search = a.findViewById(R.id.search);
        search.setText("OSI");
    }

    /** 收藏夹「背题模式浏览」入口：按钮可见，点击后以背题模式进入且收藏题不丢 */
    @Test
    public void favReciteEntry() {
        long bankId = seedBank();
        List<Long> ids = Db.questionIds(bankId);
        Db.toggleFav(bankId, ids.get(0));
        Db.toggleFav(bankId, ids.get(1));
        QuestionListActivity a = Robolectric.buildActivity(QuestionListActivity.class,
                new Intent(app(), QuestionListActivity.class)
                        .putExtra("bank", bankId)
                        .putExtra("scope", QuestionListActivity.SCOPE_FAV)
                        .putExtra("filter", QuestionListActivity.FILTER_ALL)
                        .putExtra("title", "收藏夹")).setup().get();
        TextView btnRecite = a.findViewById(R.id.btnRecite);
        assertNotNull(btnRecite);
        assertEquals("收藏夹应显示背题入口", View.VISIBLE, btnRecite.getVisibility());
        btnRecite.performClick();
        Intent next = Shadows.shadowOf(a).getNextStartedActivity();
        assertNotNull(next);
        assertEquals(PracticeActivity.class.getName(), next.getComponent().getClassName());
        assertEquals("应进入背题模式", PracticeActivity.MODE_RECITE, next.getIntExtra("mode", -1));
        assertEquals("收藏背题", next.getStringExtra("title"));
        long[] got = next.getLongArrayExtra("ids");
        assertEquals(2, got.length);
        boolean has0 = false, has1 = false;
        for (long id : got) {
            if (id == ids.get(0)) has0 = true;
            if (id == ids.get(1)) has1 = true;
        }
        assertTrue("乱序不应丢失收藏题", has0 && has1);
    }

    /** 题目列表行内「分享」按钮可用（二选一分享面板：不含答案 / 含答案+解析） */
    @Test
    public void questionListRowShareEntry() {
        long bankId = seedBank();
        QuestionListActivity a = Robolectric.buildActivity(QuestionListActivity.class,
                new Intent(app(), QuestionListActivity.class)
                        .putExtra("bank", bankId)
                        .putExtra("scope", QuestionListActivity.SCOPE_ALL)
                        .putExtra("filter", QuestionListActivity.FILTER_ALL)
                        .putExtra("title", "全部题目")).setup().get();
        ListView list = a.findViewById(R.id.list);
        android.widget.BaseAdapter adapter = (android.widget.BaseAdapter) list.getAdapter();
        View row = adapter.getView(0, null, (ViewGroup) list);
        assertNotNull(row.findViewById(R.id.btnShare));
        row.findViewById(R.id.btnShare).performClick();
    }

    @Test
    public void wrongBookScope() {
        long bankId = seedBank();
        Robolectric.buildActivity(QuestionListActivity.class,
                new Intent(app(), QuestionListActivity.class)
                        .putExtra("bank", bankId)
                        .putExtra("scope", QuestionListActivity.SCOPE_WRONG)
                        .putExtra("filter", QuestionListActivity.FILTER_UNRESOLVED)
                        .putExtra("title", "错题重做")).setup().get();
    }

    @Test
    public void examConfigLaunch() {
        long bankId = seedBank();
        ExamConfigActivity a = Robolectric.buildActivity(ExamConfigActivity.class,
                new Intent(app(), ExamConfigActivity.class)
                        .putExtra(ExamConfigActivity.EXTRA_BANK, bankId)).setup().get();
        assertNotNull(a.findViewById(R.id.typeRows));
    }

    @Test
    public void examLaunchAnswerAndSubmit() {
        long bankId = seedBank();
        List<Long> ids = Db.questionIdsByFilter(bankId,
                (1 << 0) | (1 << 1) | (1 << 2) | (1 << 3), null, false);
        assertTrue(ids.size() >= 4);
        ExamActivity a = Robolectric.buildActivity(ExamActivity.class,
                new Intent(app(), ExamActivity.class)
                        .putExtra("bank", bankId)
                        .putExtra("ids", arr(ids))
                        .putExtra("duration", 60)
                        .putExtra("points", new int[]{1, 2, 1, 2, 5})
                        .putExtra("shuffle", false)).setup().get();

        LinearLayout options = a.findViewById(R.id.options);
        assertTrue(options.getChildCount() >= 2);
        options.getChildAt(0).performClick();
        a.findViewById(R.id.btnNext).performClick();
        options = a.findViewById(R.id.options);
        options.getChildAt(0).performClick();
        // 交卷确认（验证按钮 → 确认框的连线）
        a.findViewById(R.id.btnSubmit).performClick();
        AlertDialog d = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(d);
        // Robolectric 下对话框按钮未布局，直接调用真实交卷流程（真机为点确认框）
        a.submit();
        // 交卷后生成考试记录，并为全部考题写入答题记录
        assertEquals(1, Db.listExams(10).size());
        assertEquals(ids.size(), Db.globalStats()[0]);
    }

    @Test
    public void examResultLaunch() {
        long bankId = seedBank();
        List<Long> ids = Db.questionIds(bankId);
        List<Long> wrong = new ArrayList<>();
        if (ids.size() > 1) wrong.add(ids.get(1));
        int[][] detail = new int[com.ziruoling.quiz.core.QuestionType.COUNT][2];
        detail[0][0] = 1;
        detail[0][1] = 1;
        long examId = Db.addExam(bankId, "模拟考试", 4, 4, 3, 3.0, 6.0, 300, wrong, detail);
        ExamResultActivity a = Robolectric.buildActivity(ExamResultActivity.class,
                new Intent(app(), ExamResultActivity.class)
                        .putExtra("exam_id", examId)).setup().get();
        assertNotNull(a.findViewById(R.id.scoreTv));
    }

    @Test
    public void statsLaunch() {
        seedBank();
        StatsActivity a = Robolectric.buildActivity(StatsActivity.class).setup().get();
        assertNotNull(a.findViewById(R.id.bar));
    }

    @Test
    public void settingsLaunch() {
        // 断言中文文案：测试机默认英文环境时先固定为中文
        com.ziruoling.quiz.data.Prefs.language(com.ziruoling.quiz.data.Prefs.LANG_ZH);
        SettingsActivity a = Robolectric.buildActivity(SettingsActivity.class).setup().get();
        assertNotNull(a.findViewById(R.id.themeGroup));
        assertNotNull(a.findViewById(R.id.swAutoNext));
        // 关于卡片：版本号运行时读取，应带上 fork 后缀，且注明基于哪个原版修改、由谁修改
        TextView about = a.findViewById(R.id.aboutVersion);
        assertNotNull(about);
        String text = about.getText().toString();
        assertTrue("版本号应取自 versionName: " + text, text.contains("1.6."));
        TextView fork = a.findViewById(R.id.aboutFork);
        assertNotNull(fork);
        String forkText = fork.getText().toString();
        assertTrue("应注明原版版本: " + forkText, forkText.contains("v1.13.0"));
        assertTrue("应注明原作者: " + forkText, forkText.contains("lssudi"));
        assertTrue("应注明修改者: " + forkText, forkText.contains("CC"));

        // 彩蛋：点「基于原版…」弹「题库已清空」，但不做任何数据操作
        fork.performClick();
        AlertDialog dlg = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull("应弹出彩蛋提示", dlg);
        String msg = ((android.widget.TextView) dlg.findViewById(android.R.id.message)) == null
                ? "" : ((android.widget.TextView) dlg.findViewById(android.R.id.message)).getText().toString();
        assertTrue("彩蛋文案应为清空提示，实际：" + msg, msg.contains("您的题库已经全部清空"));
        assertTrue("彩蛋文案应带告别语，实际：" + msg, msg.contains("祝您生活愉快"));
        dlg.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertTrue("彩蛋确认后应退出应用", a.isFinishing());
        dlg.dismiss();
        // 更新日志已移到独立页面：关于页只保留一行入口
        TextView updates = a.findViewById(R.id.aboutUpdates);
        assertNotNull(updates);
        assertEquals("多处功能增加与改动", updates.getText().toString());
        TextView entry = a.findViewById(R.id.aboutChangelogEntry);
        assertNotNull("应有查看以往更新内容的入口", entry);
        assertTrue(entry.getText().toString().contains("查看以往更新内容"));
    }

    /** 音效开关：默认开启，切换实时写入设置 */
    @Test
    public void settingsSoundToggle() {
        SettingsActivity a = Robolectric.buildActivity(SettingsActivity.class).setup().get();
        Switch sw = a.findViewById(R.id.swSound);
        assertNotNull(sw);
        assertTrue(sw.isChecked());
        assertTrue(Prefs.sound());
        sw.setChecked(false);
        assertFalse(Prefs.sound());
        sw.setChecked(true);
        assertTrue(Prefs.sound());
        // 试听音效按钮：点击立即+延迟播放不崩溃（真机出声，JVM 为空操作）
        TextView btnTest = a.findViewById(R.id.btnSoundTest);
        assertNotNull(btnTest);
        btnTest.performClick();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    @Test
    public void importActivityLaunch() {
        ImportActivity a = Robolectric.buildActivity(ImportActivity.class).setup().get();
        assertNotNull(a.findViewById(R.id.bankSpinner));
        assertNotNull(a.findViewById(R.id.nameRow));
    }

    /** 模拟微信「用其他应用打开」传入 content:// 附件（验证接收链路不崩） */
    @Test
    public void importReceiveViewIntent() {
        Intent it = new Intent(Intent.ACTION_VIEW);
        it.setDataAndType(android.net.Uri.parse("content://com.tencent.mm.external.fileprovider/attachment/ceshi.xlsx"),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ImportActivity a = Robolectric.buildActivity(ImportActivity.class, it).setup().get();
        assertNotNull(a.findViewById(R.id.bankSpinner));
    }
}
