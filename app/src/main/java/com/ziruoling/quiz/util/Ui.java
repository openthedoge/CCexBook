package com.ziruoling.quiz.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.TypedValue;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.ziruoling.quiz.R;
import com.ziruoling.quiz.data.Prefs;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 通用 UI 工具 */
public final class Ui {

    private Ui() {}

    public static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 取主题属性颜色 */
    public static int attrColor(Context c, int attrRes) {
        TypedValue tv = new TypedValue();
        if (c.getTheme().resolveAttribute(attrRes, tv, true)) return tv.data;
        return 0xFF3D6FE0;
    }

    public static int color(Context c, int colorRes) {
        return c.getResources().getColor(colorRes);
    }

    /** 取图标并染色（主题属性色） */
    public static Drawable icon(Context c, int drawableRes, int attrColorRes) {
        Drawable d = c.getResources().getDrawable(drawableRes).mutate();
        d.setTint(attrColor(c, attrColorRes));
        return d;
    }

    public static void toast(Context c, String msg) {
        Toast.makeText(c, Tr.s(msg), Toast.LENGTH_SHORT).show();
    }

    /**
     * 自动翻译对话框文案的 Builder：标题/正文/按钮/列表项统一走 {@link Tr}。
     * 用法：new Ui.AlertBuilder(ctx)...
     */
    public static class AlertBuilder extends android.app.AlertDialog.Builder {
        public AlertBuilder(Context c) {
            super(c);
        }

        @Override
        public android.app.AlertDialog.Builder setTitle(CharSequence title) {
            return super.setTitle(title == null ? null : Tr.s(title.toString()));
        }

        @Override
        public android.app.AlertDialog.Builder setMessage(CharSequence message) {
            return super.setMessage(message == null ? null : Tr.s(message.toString()));
        }

        @Override
        public android.app.AlertDialog.Builder setItems(CharSequence[] items,
                                                        android.content.DialogInterface.OnClickListener l) {
            return super.setItems(tr(items), l);
        }

        @Override
        public android.app.AlertDialog.Builder setMultiChoiceItems(CharSequence[] items, boolean[] checked,
                                                                  android.content.DialogInterface.OnMultiChoiceClickListener l) {
            return super.setMultiChoiceItems(tr(items), checked, l);
        }

        @Override
        public android.app.AlertDialog.Builder setPositiveButton(CharSequence text,
                                                                 android.content.DialogInterface.OnClickListener l) {
            return super.setPositiveButton(Tr.s(text == null ? null : text.toString()), l);
        }

        @Override
        public android.app.AlertDialog.Builder setNegativeButton(CharSequence text,
                                                                 android.content.DialogInterface.OnClickListener l) {
            return super.setNegativeButton(Tr.s(text == null ? null : text.toString()), l);
        }

        @Override
        public android.app.AlertDialog.Builder setNeutralButton(CharSequence text,
                                                                android.content.DialogInterface.OnClickListener l) {
            return super.setNeutralButton(Tr.s(text == null ? null : text.toString()), l);
        }

        private static CharSequence[] tr(CharSequence[] items) {
            if (items == null) return null;
            CharSequence[] out = new CharSequence[items.length];
            for (int i = 0; i < items.length; i++) {
                out[i] = items[i] == null ? null : Tr.s(items[i].toString());
            }
            return out;
        }
    }

    /** 按目标宽度采样解码图片，避免大图整张读进内存 */
    public static android.graphics.Bitmap decodeSampled(java.io.File f, int reqW) {
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        int sample = 1;
        if (reqW > 0 && o.outWidth > 0) {
            while (o.outWidth / (sample * 2) >= reqW) sample *= 2;
        }
        android.graphics.BitmapFactory.Options o2 = new android.graphics.BitmapFactory.Options();
        o2.inSampleSize = sample;
        return android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
    }

    /** 圆角背景 */
    public static GradientDrawable bg(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusDp);
        return g;
    }

    /** 圆角描边背景 */
    public static GradientDrawable bgStroke(Context c, int fillColor, int strokeColor, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fillColor);
        g.setCornerRadius(radiusDp);
        g.setStroke(dp(c, 1), strokeColor);
        return g;
    }

    /** 按压波纹色：浅色用暗波纹，深色/纯黑用亮波纹（否则深色下看不见） */
    private static int rippleColor(Context c) {
        return Prefs.isDark(c) ? 0x22FFFFFF : 0x22000000;
    }

    /** 无 Context 的波纹色：显式主题直接判断，AUTO 时读系统 uiMode */
    private static int rippleColor() {
        int t = Prefs.theme();
        if (t == Prefs.THEME_DARK || t == Prefs.THEME_BLACK) return 0x22FFFFFF;
        if (t == Prefs.THEME_LIGHT) return 0x22000000;
        int mode = Resources.getSystem().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES ? 0x22FFFFFF : 0x22000000;
    }

    /** 按压波纹圆角背景（属性色填充） */
    public static Drawable ripple(Context c, int fillAttr, int radiusDp) {
        int fill = attrColor(c, fillAttr);
        GradientDrawable g = bg(fill, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor(c)), g, null);
    }

    public static Drawable rippleFill(Context c, int fillColor, int radiusDp) {
        GradientDrawable g = bg(fillColor, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor(c)), g, null);
    }

    /** 小圆角标签 */
    public static TextView chip(Context c, String text, int textAttr, int bgAttr) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(12);
        t.setTextColor(attrColor(c, textAttr));
        t.setBackground(bg(attrColor(c, bgAttr), dp(c, 14)));
        t.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
        t.setGravity(Gravity.CENTER);
        return t;
    }

    /** 小圆角标签（固定颜色） */
    public static TextView chipColor(Context c, String text, int textColor, int bgColor) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(12);
        t.setTextColor(textColor);
        t.setBackground(bg(bgColor, dp(c, 14)));
        t.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
        t.setGravity(Gravity.CENTER);
        return t;
    }

    /** 标准内容卡片容器（圆角 + 细描边 + 柔和投影 + 按压波纹） */
    public static LinearLayout card(Context c) {
        LinearLayout ll = new LinearLayout(c);
        ll.setOrientation(LinearLayout.VERTICAL);
        Drawable content = bgStroke(c, attrColor(c, R.attr.qzSurface), attrColor(c, R.attr.qzStroke), dp(c, 14));
        ll.setBackground(new RippleDrawable(ColorStateList.valueOf(rippleColor(c)), content, null));
        ll.setElevation(dp(c, 3));
        ll.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        return ll;
    }

    /** 按压波纹包装：内容形状不变，仅加按下反馈（无 Context，按主题/系统 uiMode 取色） */
    public static Drawable press(Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor()), content, null);
    }

    /** 按压波纹包装（带 Context：便于显式按当前主题取色） */
    public static Drawable press(Context c, Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor(c)), content, null);
    }

    public static TextView text(Context c, String s, float sp, int colorAttr, int style) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(attrColor(c, colorAttr));
        if (style != 0) t.setTypeface(Typeface.DEFAULT, style);
        t.setLineSpacing(dp(c, 2), 1f);
        return t;
    }

    /** 时间 "MM-dd HH:mm" */
    public static String fmtTime(long millis) {
        return new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date(millis));
    }

    /** 秒 → "mm:ss" / "h:mm:ss" */
    public static String fmtClock(int sec) {
        int s = Math.max(0, sec);
        int h = s / 3600;
        int m = (s % 3600) / 60;
        int ss = s % 60;
        if (h > 0) return String.format(Locale.CHINA, "%d:%02d:%02d", h, m, ss);
        return String.format(Locale.CHINA, "%02d:%02d", m, ss);
    }
}