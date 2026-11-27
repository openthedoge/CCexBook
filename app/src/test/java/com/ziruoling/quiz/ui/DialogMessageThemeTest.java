package com.ziruoling.quiz.ui;

import android.app.AlertDialog;
import android.widget.TextView;

import com.ziruoling.quiz.R;
import com.ziruoling.quiz.data.Prefs;
import com.ziruoling.quiz.util.Ui;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

import static org.junit.Assert.assertEquals;

/** 弹窗正文必须使用主题色（此前小结卡正文被漏掉过） */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DialogMessageThemeTest {

    @Before
    public void setUp() {
        Prefs.resetForTest();
        Prefs.init(org.robolectric.RuntimeEnvironment.getApplication());
    }

    @Test
    public void messageUsesPrimaryColor() {
        android.app.Activity a = Robolectric.buildActivity(android.app.Activity.class).setup().get();
        a.setTheme(R.style.AppTheme_Light);
        new Ui.AlertBuilder(a)
                .setTitle("专项训练结束")
                .setMessage("做题 8 题，正确率 75%")
                .setPositiveButton("返回", null)
                .show();
        AlertDialog d = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        TextView msg = d.findViewById(android.R.id.message);
        int actual = msg.getCurrentTextColor();
        int primary = Ui.attrColor(a, R.attr.qzPrimary);
        int body = Ui.attrColor(a, R.attr.qzText);
        assertEquals("正文应为主题色 primary=" + primary + " body=" + body + " actual=" + actual,
                primary, actual);
        d.dismiss();
    }
}
