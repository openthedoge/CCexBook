package com.ziruoling.quiz.ui;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.ziruoling.quiz.R;
import com.ziruoling.quiz.data.Db;
import com.ziruoling.quiz.data.DemoBank;
import com.ziruoling.quiz.data.Prefs;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** 题库首页「继续刷题」：直接接着上次的位置，不再弹二级选择 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BankHomeResumeTest {

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

    private ActivityController<BankHomeActivity> open() {
        return Robolectric.buildActivity(BankHomeActivity.class,
                new Intent(RuntimeEnvironment.getApplication(), BankHomeActivity.class)
                        .putExtra("bank_id", bankId)).setup();
    }

    private View firstCard(BankHomeActivity a) {
        GridLayout grid = a.findViewById(R.id.grid);
        assertNotNull(grid);
        assertTrue("快捷入口应有卡片", grid.getChildCount() > 0);
        return grid.getChildAt(0);
    }

    private String label(View card) {
        return ((TextView) ((ViewGroup) card).getChildAt(0)).getText().toString();
    }

    private String subtitle(View card) {
        return ((TextView) ((ViewGroup) card).getChildAt(1)).getText().toString();
    }

    @Test
    public void continueItemIsFirstAndShowsProgress() {
        List<Long> ids = Db.questionIds(bankId);
        Db.setProgress(bankId, ids.get(3));

        ActivityController<BankHomeActivity> c = open();
        View card = firstCard(c.get());
        assertEquals("第一个入口应是「继续刷题」", "继续刷题", label(card));
        assertTrue("副标题应显示上次位置，实际：" + subtitle(card),
                subtitle(card).contains("第 4 题"));
        c.destroy();
    }

    @Test
    public void tappingContinueResumesAtSavedIndex() {
        List<Long> ids = Db.questionIds(bankId);
        Db.setProgress(bankId, ids.get(3));

        ActivityController<BankHomeActivity> c = open();
        firstCard(c.get()).performClick();

        Intent next = Shadows.shadowOf(c.get()).getNextStartedActivity();
        assertNotNull("应打开练习页", next);
        assertEquals(PracticeActivity.class.getName(), next.getComponent().getClassName());
        assertEquals("应从第 4 题开始（下标 3）", 3, next.getIntExtra("start", -1));
        assertTrue("应回填已答状态", next.getBooleanExtra("restore", false));
        assertTrue("应保存进度", next.getBooleanExtra("progress", false));
        c.destroy();
    }

    @Test
    public void withoutProgressStartsFromFirstQuestion() {
        ActivityController<BankHomeActivity> c = open();
        View card = firstCard(c.get());
        assertEquals("继续刷题", label(card));
        assertTrue("无进度时应提示从头开始，实际：" + subtitle(card),
                subtitle(card).contains("第 1 题"));

        card.performClick();
        Intent next = Shadows.shadowOf(c.get()).getNextStartedActivity();
        assertNotNull(next);
        assertEquals(0, next.getIntExtra("start", -1));
        c.destroy();
    }

    @Test
    public void secondItemIsStillSequentialPractice() {
        ActivityController<BankHomeActivity> c = open();
        GridLayout grid = c.get().findViewById(R.id.grid);
        View second = grid.getChildAt(1);
        assertEquals("顺序练习", label(second));
        c.destroy();
    }

    // ==================== 顺序练习：自定义起始题号 ====================

    private android.app.Dialog latestSheet() {
        return org.robolectric.shadows.ShadowDialog.getLatestDialog();
    }

    private void collectTexts(View v, java.util.List<String> out) {
        if (v instanceof TextView) {
            CharSequence cs = ((TextView) v).getText();
            if (cs != null && cs.length() > 0) out.add(cs.toString());
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collectTexts(g.getChildAt(i), out);
        }
    }

    private java.util.List<String> sheetTexts() {
        android.app.Dialog d = latestSheet();
        assertNotNull("应弹出底部选择器", d);
        java.util.List<String> out = new java.util.ArrayList<>();
        collectTexts(d.getWindow().getDecorView(), out);
        return out;
    }

    private void clickSheet(String contains) {
        android.app.Dialog d = latestSheet();
        assertNotNull(d);
        TextView t = findByText(d.getWindow().getDecorView(), contains);
        assertTrue("底部选择器里未找到「" + contains + "」", t != null);
        t.performClick();
    }

    private TextView findByText(View v, String contains) {
        if (v instanceof TextView) {
            CharSequence cs = ((TextView) v).getText();
            if (cs != null && cs.toString().contains(contains)) return (TextView) v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextView r = findByText(g.getChildAt(i), contains);
                if (r != null) return r;
            }
        }
        return null;
    }

    private android.app.AlertDialog latestDialog() {
        return org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    }

    private void clickItem(android.app.AlertDialog d, String contains) {
        ListView lv = d.getListView();
        assertNotNull(lv);
        int pos = -1;
        for (int i = 0; i < lv.getAdapter().getCount(); i++) {
            if (lv.getAdapter().getItem(i).toString().contains(contains)) {
                pos = i;
                break;
            }
        }
        assertTrue("菜单里未找到「" + contains + "」", pos >= 0);
        lv.performItemClick(null, pos, pos);
    }

    private EditText findEditText(View v) {
        if (v instanceof EditText) return (EditText) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                EditText e = findEditText(g.getChildAt(i));
                if (e != null) return e;
            }
        }
        return null;
    }

    /** 打开顺序练习 → 选「自定义起始题号」→ 输入题号 → 确认，返回 start 参数 */
    private int startWithCustomIndex(ActivityController<BankHomeActivity> c, String typed) {
        View seq = ((GridLayout) c.get().findViewById(R.id.grid)).getChildAt(1);
        assertEquals("顺序练习", label(seq));
        seq.performClick();
        idle();

        android.app.AlertDialog menu = latestDialog();
        assertNotNull("应弹出开始方式列表", menu);
        clickItem(menu, "自定义起始题号");
        idle();

        android.app.AlertDialog input = latestDialog();
        assertNotNull("应弹出题号输入框", input);
        EditText et = findEditText(input.getWindow().getDecorView());
        assertNotNull(et);
        et.setText(typed);
        input.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        idle();

        Intent next = Shadows.shadowOf(c.get()).getNextStartedActivity();
        assertNotNull("应打开练习页", next);
        assertEquals(PracticeActivity.class.getName(), next.getComponent().getClassName());
        return next.getIntExtra("start", -1);
    }

    /** Robolectric 下对话框关闭/按钮回调经 Handler 派发，需要先跑完主线程队列 */
    private void idle() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    @Test
    public void customIndexJumpsToTypedQuestion() {
        ActivityController<BankHomeActivity> c = open();
        assertEquals("输入第 5 题应传下标 4", 4, startWithCustomIndex(c, "5"));
        c.destroy();
    }

    @Test
    public void customIndexTooSmallClampsToFirst() {
        ActivityController<BankHomeActivity> c = open();
        assertEquals("输入 0 应夹到第 1 题", 0, startWithCustomIndex(c, "0"));
        c.destroy();
    }

    @Test
    public void customIndexTooLargeClampsToLast() {
        int total = Db.questionIds(bankId).size();
        ActivityController<BankHomeActivity> c = open();
        assertEquals("输入超出总数应夹到最后一题", total - 1, startWithCustomIndex(c, "99999"));
        c.destroy();
    }

    @Test
    public void menuOffersResumeWhenProgressExists() {
        List<Long> ids = Db.questionIds(bankId);
        Db.setProgress(bankId, ids.get(6));

        ActivityController<BankHomeActivity> c = open();
        View seq = ((GridLayout) c.get().findViewById(R.id.grid)).getChildAt(1);
        seq.performClick();
        android.app.AlertDialog d = latestDialog();
        assertNotNull(d);
        String all = "";
        for (int i = 0; i < d.getListView().getAdapter().getCount(); i++) {
            all += d.getListView().getAdapter().getItem(i).toString() + "|";
        }
        assertTrue("有进度时应提供继续项，实际：" + all, all.contains("从上次"));
        assertTrue(all.contains("重新开始"));
        assertTrue(all.contains("自定义起始题号"));
        c.destroy();
    }
}
