package com.ziruoling.quiz.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.Window;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.ziruoling.quiz.R;
import com.ziruoling.quiz.util.Ui;

/**
 * 答题卡：宫格总览 + 跳题。
 * 窗口按屏幕限定高度、宫格置于滚动容器内——题目再多也能滑到底；
 * 列数按屏宽自适应；点题号跳转后自动关闭。
 */
public final class AnswerSheetDialog {

    public interface OnJump {
        void onJump(int position);
    }

    /** 状态：0 未答 1 本次答对 2 本次答错 3 历史答对 4 历史答错 */
    public static void show(Context c, int total, int current, int[] states, String title, OnJump jump) {
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        int screenDp = Math.round(dm.widthPixels / Math.max(1f, dm.density));
        // 列数按屏宽自适应（每格约 52dp，5~8 列）
        int cols = Math.max(5, Math.min(8, (screenDp - 40) / 52));
        int cell = 46;

        final Dialog dialog = new Dialog(c);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.bg(Ui.attrColor(c, R.attr.qzBg), Ui.dp(c, 16)));
        int pad = Ui.dp(c, 14);
        root.setPadding(pad, Ui.dp(c, 12), pad, pad);

        // 标题 + 统计
        TextView head = new TextView(c);
        head.setText(title);
        head.setTextSize(16);
        head.setTextColor(Ui.attrColor(c, R.attr.qzText));
        head.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(head);

        // 图例
        LinearLayout legend = new LinearLayout(c);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setGravity(Gravity.CENTER);
        legend.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 8));
        int chipBg = Ui.attrColor(c, R.attr.qzChipBg);
        int primary = Ui.attrColor(c, R.attr.qzPrimary);
        int green = Ui.attrColor(c, R.attr.qzGreen);
        int red = Ui.attrColor(c, R.attr.qzRed);
        int textColor = Ui.attrColor(c, R.attr.qzText);
        int onPrimary = Ui.attrColor(c, R.attr.qzOnPrimary);
        // 历史已答：同色系但半透明，与「本次已答」的实心色区分
        int histGreen = (green & 0x00FFFFFF) | 0x66000000;
        int histRed = (red & 0x00FFFFFF) | 0x66000000;
        boolean reciteSheet = false;
        for (int st : states) {
            if (st == 5) {
                reciteSheet = true;
                break;
            }
        }
        int seenFg = (primary & 0x00FFFFFF) | 0x99000000;
        if (reciteSheet) {
            legend.addView(legendChip(c, "未看", chipBg, textColor));
            legend.addView(legendChip(c, "已看", (primary & 0x00FFFFFF) | 0x33000000, seenFg));
            root.addView(legend);
        } else {
        legend.addView(legendChip(c, "未答", chipBg, textColor));
        legend.addView(legendChip(c, "本次对", green, onPrimary));
        legend.addView(legendChip(c, "本次错", red, onPrimary));
        legend.addView(legendChip(c, "历史", histGreen, textColor));
        legend.addView(legendChip(c, "当前", primary, onPrimary));
        root.addView(legend);
        }

        // 宫格（自适应列数）
        GridLayout grid = new GridLayout(c);
        grid.setColumnCount(cols);
        for (int i = 0; i < total; i++) {
            TextView t = new TextView(c);
            t.setText(String.valueOf(i + 1));
            t.setTextSize(13);
            t.setGravity(Gravity.CENTER);
            t.setTextColor(textColor);
            int bg = chipBg;
            if (reciteSheet && states[i] == 5) {
                // 背题模式：当前题与已看题统一显示半透明「阅」
                t.setText("阅");
                t.setTextSize(16);
                t.setTextColor(i == current ? seenFg : seenFg);
                bg = i == current
                        ? ((primary & 0x00FFFFFF) | 0x33000000)
                        : ((primary & 0x00FFFFFF) | 0x1A000000);
            } else if (i == current) {
                t.setTextColor(onPrimary);
                bg = primary;
            } else if (states[i] == 1) {
                t.setTextColor(onPrimary);
                bg = green;
            } else if (states[i] == 2) {
                t.setTextColor(onPrimary);
                bg = red;
            } else if (states[i] == 3) {
                t.setTextColor(textColor);
                bg = histGreen;
            } else if (states[i] == 4) {
                t.setTextColor(textColor);
                bg = histRed;
            } else if (states[i] == 5) {
                t.setText("阅");
                t.setTextSize(16);
                t.setTextColor(seenFg);
                bg = (primary & 0x00FFFFFF) | 0x1A000000;
            }
            t.setBackground(Ui.bg(bg, Ui.dp(c, 10)));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = Ui.dp(c, cell);
            lp.height = Ui.dp(c, cell);
            lp.setMargins(Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3));
            final int pos = i;
            t.setOnClickListener(v -> {
                if (jump != null) jump.onJump(pos);
                dialog.dismiss();
            });
            grid.addView(t, lp);
        }

        // 滚动容器占满剩余高度：题目再多也能滑到底
        ScrollView sv = new ScrollView(c);
        sv.setFillViewport(true);
        sv.addView(grid, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // 关闭按钮
        TextView close = new TextView(c);
        close.setText("关闭");
        close.setTextSize(15);
        close.setTypeface(Typeface.DEFAULT_BOLD);
        close.setGravity(Gravity.CENTER);
        close.setTextColor(Ui.attrColor(c, R.attr.qzPrimary));
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 46));
        closeLp.topMargin = Ui.dp(c, 8);
        close.setLayoutParams(closeLp);
        close.setBackground(Ui.bg(Ui.attrColor(c, R.attr.qzSurface), Ui.dp(c, 12)));
        close.setOnClickListener(v -> dialog.dismiss());
        root.addView(close);

        dialog.setContentView(root);
        dialog.show();
        Window win = dialog.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new ColorDrawable(0x00000000));
            win.setLayout(Math.round(dm.widthPixels * 0.92f), Math.round(dm.heightPixels * 0.78f));
        }
        // 打开后把「当前题」滚到可视区中间，题目多时不用自己往下找
        final ScrollView scroll = sv;
        sv.post(() -> {
            int rowH = Ui.dp(c, cell + 6);
            int row = current / cols;
            int viewH = scroll.getHeight();
            int y = Math.max(0, row * rowH - Math.max(0, viewH - rowH) / 2);
            scroll.scrollTo(0, y);
        });
    }

    private static TextView legendChip(Context c, String label, int color, int textColor) {
        TextView t = new TextView(c);
        t.setText(label);
        t.setTextSize(11);
        t.setTextColor(textColor);
        t.setBackground(Ui.bg(color, Ui.dp(c, 10)));
        t.setPadding(Ui.dp(c, 8), Ui.dp(c, 3), Ui.dp(c, 8), Ui.dp(c, 3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0);
        t.setLayoutParams(lp);
        return t;
    }
}