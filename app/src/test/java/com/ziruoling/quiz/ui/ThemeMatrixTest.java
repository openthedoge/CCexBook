package com.ziruoling.quiz.ui;

import android.content.Context;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;

import com.ziruoling.quiz.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 主题矩阵回归：15 套主题（5 配色 × 浅/深/纯黑）都必须定义全部 16 个 qz 属性，
 * 且关键布局能在每套主题下正常解析 —— 防止漏配属性导致纯黑模式静默回退成亮色或直接崩溃。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ThemeMatrixTest {

    private static final int[] THEMES = {
            R.style.AppTheme_Light, R.style.AppTheme_Dark, R.style.AppTheme_Black,
            R.style.AppTheme_Red_Light, R.style.AppTheme_Red_Dark, R.style.AppTheme_Red_Black,
            R.style.AppTheme_Green_Light, R.style.AppTheme_Green_Dark, R.style.AppTheme_Green_Black,
            R.style.AppTheme_Orange_Light, R.style.AppTheme_Orange_Dark, R.style.AppTheme_Orange_Black,
            R.style.AppTheme_Purple_Light, R.style.AppTheme_Purple_Dark, R.style.AppTheme_Purple_Black
    };

    private static final int[] QZ_ATTRS = {
            R.attr.qzPrimary, R.attr.qzBg, R.attr.qzSurface, R.attr.qzChipBg,
            R.attr.qzText, R.attr.qzTextSub, R.attr.qzDivider,
            R.attr.qzGreen, R.attr.qzGreenBg, R.attr.qzRed, R.attr.qzRedBg,
            R.attr.qzOrange, R.attr.qzOrangeBg, R.attr.qzPrimaryBg, R.attr.qzStroke,
            R.attr.qzOnPrimary
    };

    private static final int[] LAYOUTS = {
            R.layout.activity_main, R.layout.activity_practice, R.layout.activity_bank_home,
            R.layout.activity_settings, R.layout.activity_question_list, R.layout.activity_exam_config,
            R.layout.activity_stats, R.layout.activity_edit_question, R.layout.activity_changelog,
            R.layout.item_bank, R.layout.item_question_row
    };

    private String name(int styleId) {
        return RuntimeEnvironment.getApplication().getResources().getResourceEntryName(styleId);
    }

    @Test
    public void everyThemeDefinesAllQzAttrs() {
        Context base = RuntimeEnvironment.getApplication();
        for (int style : THEMES) {
            Context themed = new ContextThemeWrapper(base, style);
            for (int attr : QZ_ATTRS) {
                TypedValue tv = new TypedValue();
                boolean ok = themed.getTheme().resolveAttribute(attr, tv, true);
                assertTrue("主题 " + name(style) + " 缺少属性 "
                        + base.getResources().getResourceEntryName(attr), ok);
                assertTrue("主题 " + name(style) + " 的属性 "
                        + base.getResources().getResourceEntryName(attr) + " 不是颜色值", tv.type >= 28 && tv.type <= 31);
            }
        }
    }

    @Test
    public void everyThemeInflatesKeyLayouts() {
        Context base = RuntimeEnvironment.getApplication();
        for (int style : THEMES) {
            Context themed = new ContextThemeWrapper(base, style);
            for (int layout : LAYOUTS) {
                assertNotNull("主题 " + name(style) + " 下布局解析失败："
                                + base.getResources().getResourceName(layout),
                        LayoutInflater.from(themed).inflate(layout, null, false));
            }
        }
    }

    /** 纯黑主题必须是真纯黑底色，正文不能是纯白 */
    @Test
    public void blackThemeIsRealBlackWithSoftText() {
        Context base = RuntimeEnvironment.getApplication();
        Context themed = new ContextThemeWrapper(base, R.style.AppTheme_Black);
        TypedValue bg = new TypedValue();
        TypedValue text = new TypedValue();
        themed.getTheme().resolveAttribute(R.attr.qzBg, bg, true);
        themed.getTheme().resolveAttribute(R.attr.qzText, text, true);
        assertTrue("纯黑主题 qzBg 应为 #000000", bg.data == 0xFF000000);
        assertTrue("纯黑主题正文不应是纯白", text.data != 0xFFFFFFFF);
    }
}
