package com.ziruoling.quiz.ui;

import android.app.Application;
import android.content.res.Resources;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;

import com.ziruoling.quiz.R;
import com.ziruoling.quiz.data.Prefs;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 主题一致性：对话框列表项/按钮颜色必须跟当前主题配色一致 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DialogThemeTest {

    private Application app(int themeRes) {
        Application app = RuntimeEnvironment.getApplication();
        app.setTheme(themeRes);
        return app;
    }

    private int colorAttr(android.content.Context c, int attr) {
        TypedValue tv = new TypedValue();
        c.getTheme().resolveAttribute(attr, tv, true);
        if (tv.resourceId != 0) return c.getResources().getColor(tv.resourceId, c.getTheme());
        return tv.data;
    }

    private int dialogStyle(int themeRes) {
        Application a = app(themeRes);
        TypedValue tv = new TypedValue();
        a.getTheme().resolveAttribute(android.R.attr.alertDialogTheme, tv, true);
        return tv.resourceId;
    }

    private int dialogListItemColor(int themeRes) {
        Application a = app(themeRes);
        ContextThemeWrapper w = new ContextThemeWrapper(a, dialogStyle(themeRes));
        return colorAttr(w, android.R.attr.textColorAlertDialogListItem);
    }

    private int dialogAccent(int themeRes) {
        Application a = app(themeRes);
        ContextThemeWrapper w = new ContextThemeWrapper(a, dialogStyle(themeRes));
        return colorAttr(w, android.R.attr.colorAccent);
    }

    private int themeAccent(int themeRes) {
        return colorAttr(app(themeRes), android.R.attr.colorAccent);
    }

    private int sum(int c) {
        return ((c >> 16) & 0xFF) + ((c >> 8) & 0xFF) + (c & 0xFF);
    }

    @Test
    public void lightDialogTextIsDark() {
        int[] themes = {R.style.AppTheme_Light, R.style.AppTheme_Red_Light,
                R.style.AppTheme_Green_Light, R.style.AppTheme_Orange_Light,
                R.style.AppTheme_Purple_Light};
        for (int t : themes) {
            assertTrue("浅色对话框列表项应为深色",
                    sum(dialogListItemColor(t)) < 420);
        }
    }

    @Test
    public void darkDialogTextIsLight() {
        int[] themes = {R.style.AppTheme_Dark, R.style.AppTheme_Red_Dark,
                R.style.AppTheme_Green_Dark, R.style.AppTheme_Orange_Dark,
                R.style.AppTheme_Purple_Dark, R.style.AppTheme_Black};
        for (int t : themes) {
            assertTrue("深色对话框列表项应为浅色",
                    sum(dialogListItemColor(t)) > 380);
        }
    }

    @Test
    public void dialogAccentMatchesThemeAccent() {
        int[] themes = {R.style.AppTheme_Light, R.style.AppTheme_Red_Light,
                R.style.AppTheme_Green_Light, R.style.AppTheme_Orange_Light,
                R.style.AppTheme_Purple_Light, R.style.AppTheme_Dark,
                R.style.AppTheme_Red_Dark};
        for (int t : themes) {
            assertEquals("对话框强调色应等于主题强调色", themeAccent(t), dialogAccent(t));
        }
    }
}
