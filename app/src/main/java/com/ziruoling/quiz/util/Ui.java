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

    // ---- 轻提示 Dialog 跟踪：页面结束时统一取消，避免 Dialog 留在已销毁的窗口上 ----

    /** 当前还在显示、未自动消失的轻提示（按页面分组，页面销毁时不残留） */
    private static final java.util.WeakHashMap<android.app.Activity,
            java.util.ArrayList<android.app.Dialog>> LIVE_TOASTS = new java.util.WeakHashMap<>();

    private static void trackToast(final android.app.Activity a, final android.app.Dialog d) {
        synchronized (LIVE_TOASTS) {
            java.util.ArrayList<android.app.Dialog> list = LIVE_TOASTS.get(a);
            if (list == null) {
                list = new java.util.ArrayList<>();
                LIVE_TOASTS.put(a, list);
            }
            list.add(d);
        }
        d.setOnDismissListener(x -> untrackToast(a, d));
    }

    private static void untrackToast(android.app.Activity a, android.app.Dialog d) {
        synchronized (LIVE_TOASTS) {
            java.util.ArrayList<android.app.Dialog> list = LIVE_TOASTS.get(a);
            if (list == null) return;
            list.remove(d);
            if (list.isEmpty()) LIVE_TOASTS.remove(a);
        }
    }

    /** 页面结束前取消所有还没消失的轻提示（如贪吃蛇双击 B 退出时） */
    public static void dismissToasts(android.app.Activity a) {
        if (a == null) return;
        java.util.ArrayList<android.app.Dialog> list;
        synchronized (LIVE_TOASTS) {
            list = LIVE_TOASTS.remove(a);
        }
        if (list == null) return;
        for (android.app.Dialog d : list) {
            try {
                if (d.isShowing()) d.dismiss();
            } catch (Throwable ignored) {
            }
        }
    }

    /** 延迟自动消失：页面已结束/销毁时直接跳过，dismiss 本身也吞掉窗口异常 */
    private static void dismissLater(final android.app.Activity a, final android.app.Dialog d,
                                     android.view.View host, long delayMs) {
        host.postDelayed(() -> {
            untrackToast(a, d);
            if (a == null || a.isFinishing() || a.isDestroyed()) return;
            try {
                d.dismiss();
            } catch (Throwable ignored) {
            }
        }, delayMs);
    }

    /** 居中胶囊提示：自定义弹层，圆角、可点击关闭、2 秒自动消失 */
    public static void pillToast(final android.app.Activity a, String msg) {
        if (a == null || a.isFinishing() || a.isDestroyed()) return;
        final android.app.Dialog d = new android.app.Dialog(a);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        int padH = dp(a, 22);
        int padV = dp(a, 16);
        android.widget.LinearLayout box = new android.widget.LinearLayout(a);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setGravity(android.view.Gravity.CENTER);
        box.setPadding(padH, padV, padH, padV);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        g.setCornerRadius(dp(a, 26));
        int base = attrColor(a, R.attr.qzSurface);
        g.setColor((base & 0x00FFFFFF) | 0xE6000000);
        g.setStroke(dp(a, 1), attrColor(a, R.attr.qzStroke));
        box.setBackground(g);

        android.widget.TextView t = new android.widget.TextView(a);
        t.setText(msg);
        t.setTextSize(15);
        t.setTextColor(attrColor(a, R.attr.qzPrimary));
        t.setGravity(android.view.Gravity.CENTER);
        t.setPadding(dp(a, 6), dp(a, 2), dp(a, 6), dp(a, 2));
        box.addView(t);

        d.setContentView(box);
        android.view.Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            w.setLayout(android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(android.view.Gravity.CENTER);
            android.view.WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = 0f;
            w.setAttributes(lp);
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        box.setOnClickListener(v -> d.dismiss());
        d.setCanceledOnTouchOutside(true);
        d.show();
        trackToast(a, d);
        dismissLater(a, d, box, 1000);
    }

    /** 居中提示：用于需要明显被看到的一次性反馈 */
    /** 主题化轻提示：非阻塞浮层（不拦截触摸），1.6 秒自动消失 */
    private static void showThemed(android.content.Context c, String msg, int gravity, int yOffset) {
        if (c == null) return;
        String text = Tr.s(msg);
        if (!(c instanceof android.app.Activity) || ((android.app.Activity) c).isFinishing()
                || ((android.app.Activity) c).isDestroyed()) {
            Toast.makeText(c, text, Toast.LENGTH_SHORT).show();
            return;
        }
        final android.app.Activity a = (android.app.Activity) c;
        final android.app.Dialog d = new android.app.Dialog(a);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        android.widget.TextView tv = new android.widget.TextView(a);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTextColor(attrColor(a, R.attr.qzPrimary));
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setPadding(dp(a, 18), dp(a, 10), dp(a, 18), dp(a, 10));
        tv.setBackground(bgStroke(a, attrColor(a, R.attr.qzSurface),
                attrColor(a, R.attr.qzStroke), dp(a, 20)));
        d.setContentView(tv);
        android.view.Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
            android.view.WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = 0f;
            lp.gravity = gravity;
            lp.y = yOffset;
            w.setAttributes(lp);
            w.setLayout(android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        d.show();
        trackToast(a, d);
        dismissLater(a, d, tv, 1600);
    }

    public static void toastCenter(android.content.Context c, String msg) {
        showThemed(c, msg, android.view.Gravity.CENTER, 0);
    }

    public static void toast(Context c, String msg) {
        showThemed(c, msg, android.view.Gravity.BOTTOM, dp(c, 110));
    }

    public static class AlertBuilder extends android.app.AlertDialog.Builder {
        private boolean danger = false;
        private final Context ctx;

        public AlertBuilder(Context c) {
            super(c);
            ctx = c;
        }

        /** 危险操作：确认按钮用实心红 */
        public AlertBuilder danger() {
            danger = true;
            return this;
        }

        @Override
        public android.app.AlertDialog create() {
            final android.app.AlertDialog d = super.create();
            styleWindow(d, ctx);
            // 任何方式 show() 都会套用内容样式（含 create() 后手动 show）
            d.setOnShowListener(x -> styleContent(d, ctx, danger));
            return d;
        }

        @Override
        public android.app.AlertDialog show() {
            android.app.AlertDialog d = create();
            d.show();
            styleContent(d, ctx, danger);
            return d;
        }

        @Override
        public AlertBuilder setTitle(CharSequence title) {
            super.setTitle(title == null ? null : Tr.s(title.toString()));
            return this;
        }

        @Override
        public AlertBuilder setMessage(CharSequence message) {
            super.setMessage(message == null ? null : Tr.s(message.toString()));
            return this;
        }

        @Override
        public AlertBuilder setItems(CharSequence[] items,
                                     android.content.DialogInterface.OnClickListener l) {
            super.setItems(tr(items), l);
            return this;
        }

        @Override
        public AlertBuilder setMultiChoiceItems(CharSequence[] items, boolean[] checked,
                                                android.content.DialogInterface.OnMultiChoiceClickListener l) {
            super.setMultiChoiceItems(tr(items), checked, l);
            return this;
        }

        @Override
        public AlertBuilder setPositiveButton(CharSequence text,
                                              android.content.DialogInterface.OnClickListener l) {
            super.setPositiveButton(Tr.s(text == null ? null : text.toString()), l);
            return this;
        }

        @Override
        public AlertBuilder setNegativeButton(CharSequence text,
                                              android.content.DialogInterface.OnClickListener l) {
            super.setNegativeButton(Tr.s(text == null ? null : text.toString()), l);
            return this;
        }

        @Override
        public AlertBuilder setNeutralButton(CharSequence text,
                                             android.content.DialogInterface.OnClickListener l) {
            super.setNeutralButton(Tr.s(text == null ? null : text.toString()), l);
            return this;
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
    /** 窗口层样式：圆角背景、宽度、动画（必须在 show 之前应用） */
    public static void styleWindow(android.app.AlertDialog d, Context c) {
        if (d == null || c == null) return;
        android.view.Window w = d.getWindow();
        if (w == null) return;
        w.setBackgroundDrawable(c.getDrawable(R.drawable.bg_dialog));
        android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
        android.view.WindowManager.LayoutParams lp = w.getAttributes();
        lp.width = (int) (dm.widthPixels * 0.9f);
        w.setAttributes(lp);
        w.setWindowAnimations(animStyle(c));
    }

    /** 兼容旧调用：窗口 + 内容一起套用 */
    public static void styleDialog(android.app.AlertDialog d, Context c, boolean danger) {
        styleWindow(d, c);
        styleContent(d, c, danger);
    }

    /** 内容层样式：标题、分割线、列表行、按钮（show 之后调用） */
    public static void styleContent(android.app.AlertDialog d, Context c, boolean danger) {
        if (d == null || c == null) return;
        int titleId = c.getResources().getIdentifier("alertTitle", "id", "android");
        if (titleId != 0) {
            android.widget.TextView title = d.findViewById(titleId);
            if (title != null) {
                title.setTextColor(attrColor(c, R.attr.qzPrimary));
                title.setTextSize(18);
                title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                title.setGravity(android.view.Gravity.START);
            }
        }
        int divId = c.getResources().getIdentifier("titleDivider", "id", "android");
        if (divId != 0) {
            android.view.View div = d.findViewById(divId);
            if (div != null) {
                div.setVisibility(android.view.View.VISIBLE);
                div.setBackgroundColor(attrColor(c, R.attr.qzDivider));
            }
        }
        android.widget.TextView msg = d.findViewById(android.R.id.message);
        if (msg != null) {
            msg.setTextColor(attrColor(c, R.attr.qzPrimary));
            msg.setTextSize(15);
            msg.setLineSpacing(dp(c, 3), 1f);
        }
        android.widget.ListView lv = d.getListView();
        if (lv != null) {
            lv.setDivider(new android.graphics.drawable.ColorDrawable(attrColor(c, R.attr.qzDivider)));
            lv.setDividerHeight(dp(c, 1));
        }
        int primary = attrColor(c, R.attr.qzPrimary);
        android.widget.Button pos = d.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        if (pos != null) {
            pos.setAllCaps(false);
            pos.setMinHeight(dp(c, 44));
            if (danger) {
                pos.setTextColor(0xFFFFFFFF);
                pos.setBackground(c.getDrawable(R.drawable.bg_dialog_danger));
                pos.setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8));
            } else {
                pos.setTextColor(primary);
            }
        }
        android.widget.Button neg = d.getButton(android.content.DialogInterface.BUTTON_NEGATIVE);
        if (neg != null) {
            neg.setAllCaps(false);
            neg.setMinHeight(dp(c, 44));
            neg.setTextColor(primary);
        }
        android.widget.Button neu = d.getButton(android.content.DialogInterface.BUTTON_NEUTRAL);
        if (neu != null) {
            neu.setAllCaps(false);
            neu.setTextColor(primary);
        }
    }

    private static int animStyle(Context c) {
        switch (com.ziruoling.quiz.data.Prefs.animSpeed()) {
            case 0: {
                // 跟随系统动画倍率：0=关闭，<1=快，=1=中，>1=慢
                float scale = 1f;
                try {
                    scale = android.provider.Settings.Global.getFloat(c.getContentResolver(),
                            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
                } catch (Throwable ignored) {
                }
                if (scale <= 0f) return R.style.QzAnimNone;
                if (scale < 1f) return R.style.QzAnimFast;
                if (scale > 1f) return R.style.QzAnimSlow;
                return R.style.QzAnimMid;
            }
            case 1: return R.style.QzAnimFast;
            case 3: return R.style.QzAnimSlow;
            case 4: return R.style.QzAnimNone;
            default: return R.style.QzAnimMid;
        }
    }


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