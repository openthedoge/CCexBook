package com.ziruoling.quiz.ui;

import android.view.View;
import android.widget.TextView;

import com.ziruoling.quiz.R;
import com.ziruoling.quiz.data.Db;
import com.ziruoling.quiz.data.Prefs;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 设置页分组：首屏只显示分组入口，点进去看具体项，返回回到首屏 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SettingsSectionsTest {

    @Before
    public void setUp() {
        Db.resetForTest();
        Prefs.resetForTest();
        Db.init(RuntimeEnvironment.getApplication());
        Prefs.init(RuntimeEnvironment.getApplication());
    }

    private ActivityController<SettingsActivity> open() {
        return Robolectric.buildActivity(SettingsActivity.class).setup();
    }

    private int vis(SettingsActivity a, int id) {
        return a.findViewById(id).getVisibility();
    }

    @Test
    public void menuIsFirstScreenAndOthersHidden() {
        ActivityController<SettingsActivity> c = open();
        SettingsActivity a = c.get();
        assertEquals(View.VISIBLE, vis(a, R.id.secMenu));
        assertEquals(View.GONE, vis(a, R.id.secAppearance));
        assertEquals(View.GONE, vis(a, R.id.secBehavior));
        assertEquals(View.GONE, vis(a, R.id.secData));
        assertEquals(View.GONE, vis(a, R.id.secReset));
        assertEquals("关于内容直接显示在首屏", View.VISIBLE, vis(a, R.id.secAbout));
        assertEquals("设置", ((TextView) a.findViewById(R.id.titleTv)).getText().toString());
        assertEquals(4, ((android.view.ViewGroup) a.findViewById(R.id.secMenu)).getChildCount());
        c.destroy();
    }

    @Test
    public void appearanceOpensAsSubPageAndBackReturns() {
        ActivityController<SettingsActivity> c = open();
        SettingsActivity a = c.get();
        a.findViewById(R.id.rowAppearance).performClick();

        assertEquals(View.GONE, vis(a, R.id.secMenu));
        assertEquals(View.VISIBLE, vis(a, R.id.secAppearance));
        assertEquals("外观", ((TextView) a.findViewById(R.id.titleTv)).getText().toString());
        assertTrue("主题/配色/字体仍在", a.findViewById(R.id.themeGroup) != null);

        a.onBackPressed();
        assertEquals(View.VISIBLE, vis(a, R.id.secMenu));
        assertEquals(View.GONE, vis(a, R.id.secAppearance));
        c.destroy();
    }

    @Test
    public void behaviorDataResetSectionsOpen() {
        ActivityController<SettingsActivity> c = open();
        SettingsActivity a = c.get();

        a.findViewById(R.id.rowBehavior).performClick();
        assertEquals(View.VISIBLE, vis(a, R.id.secBehavior));
        a.onBackPressed();

        a.findViewById(R.id.rowData).performClick();
        assertEquals(View.VISIBLE, vis(a, R.id.secData));
        a.onBackPressed();

        a.findViewById(R.id.rowReset).performClick();
        assertEquals(View.VISIBLE, vis(a, R.id.secReset));
        // 四个清空按钮都在重置页
        assertTrue(a.findViewById(R.id.btnClearRecords) != null);
        assertTrue(a.findViewById(R.id.btnClearWrongs) != null);
        assertTrue(a.findViewById(R.id.btnClearFavs) != null);
        assertTrue(a.findViewById(R.id.btnClearStats) != null);
        c.destroy();
    }

    @Test
    public void aboutSectionShowsVersionAndAttribution() {
        ActivityController<SettingsActivity> c = open();
        SettingsActivity a = c.get();

        assertEquals("关于内容在首屏可见", View.VISIBLE, vis(a, R.id.secAbout));
        String fork = ((TextView) a.findViewById(R.id.aboutFork)).getText().toString();
        assertTrue("应注明原作者: " + fork, fork.contains("lssudi"));
        assertTrue("应注明基于原版: " + fork, fork.contains("v1.13.0"));
        c.destroy();
    }

    @Test
    public void subtitlesAreFilled() {
        ActivityController<SettingsActivity> c = open();
        SettingsActivity a = c.get();
        String app = ((TextView) a.findViewById(R.id.subAppearance)).getText().toString();
        assertTrue("外观摘要应含主题名: " + app, app.contains("·"));
        c.destroy();
    }

    @Test
    public void appendQuestionsActivityInflates() {
        Db.createBank("追加测试", "t");
        long bankId = 1L;
        com.ziruoling.quiz.data.Bank b = Db.listBanks().get(0);
        bankId = b.id;
        ActivityController<AppendQuestionsActivity> c = Robolectric.buildActivity(
                AppendQuestionsActivity.class,
                new android.content.Intent(RuntimeEnvironment.getApplication(), AppendQuestionsActivity.class)
                        .putExtra(AppendQuestionsActivity.EXTRA_BANK, bankId)
                        .putExtra(AppendQuestionsActivity.EXTRA_BANK_NAME, "追加测试")).setup();
        AppendQuestionsActivity a = c.get();
        assertTrue(a.findViewById(R.id.btnPickFile) != null);
        assertTrue(a.findViewById(R.id.btnPasteText) != null);
        String bankLine = ((TextView) a.findViewById(R.id.appendBank)).getText().toString();
        assertTrue("应显示当前题库: " + bankLine, bankLine.contains("追加测试"));
        c.destroy();
    }
}
