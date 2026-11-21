package com.ziruoling.quiz.util;

import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 上下标样式：偏移量必须小于字体 ascent 的一半。
 * 旧实现沿用系统的 SuperscriptSpan（ascent/2），在行高偏紧时上标会被裁掉。
 * <p>
 * Robolectric 下 Paint 的字体度量不可靠，这里用固定 ascent/descent 的假 Paint 保证断言确定。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ScriptSpanTest {

    private static final float SIZE = 45f;
    private static final float ASCENT = -45f;
    private static final float DESCENT = 12f;

    /** 固定字体度量的 TextPaint */
    private static final class FakePaint extends TextPaint {
        @Override
        public float ascent() {
            return ASCENT;
        }

        @Override
        public float descent() {
            return DESCENT;
        }
    }

    private TextPaint apply(CharSequence cs) {
        Spanned sp = (Spanned) cs;
        TextPaint tp = new FakePaint();
        tp.setTextSize(SIZE);
        MetricAffectingSpan[] spans = sp.getSpans(0, sp.length(), MetricAffectingSpan.class);
        assertTrue("应产生上下标样式", spans.length > 0);
        for (MetricAffectingSpan s : spans) s.updateDrawState(tp);
        return tp;
    }

    @Test
    public void superscriptOffsetIsThirdOfAscent() {
        CharSequence cs = TextUtil.rich("2⁴");
        assertEquals("上标字符应转成普通数字再靠样式渲染", "24", cs.toString());

        TextPaint tp = apply(cs);
        assertEquals("上标偏移应为 ascent/3", (int) (ASCENT / 3f), tp.baselineShift);
        assertTrue("偏移必须小于 ascent/2（旧实现的偏移会超出行盒）",
                Math.abs(tp.baselineShift) < Math.abs(ASCENT) / 2f);
        assertEquals("字号应缩到 62%", SIZE * 0.62f, tp.getTextSize(), 0.01f);
    }

    @Test
    public void subscriptShiftsDownBySameAmount() {
        CharSequence cs = TextUtil.rich("H₂O");
        assertEquals("H2O", cs.toString());

        TextPaint tp = apply(cs);
        assertEquals("下标应向下偏移 ascent/3", (int) (-ASCENT / 3f), tp.baselineShift);
        assertTrue("下标偏移也不应超过 descent + 字体 padding 的余量",
                tp.baselineShift < DESCENT + Math.abs(ASCENT) / 3f);
    }

    @Test
    public void plainTextHasNoSpans() {
        CharSequence cs = TextUtil.rich("纯文本 50%");
        assertTrue("没有上下标时不应创建 span", !(cs instanceof Spanned));
    }
}
