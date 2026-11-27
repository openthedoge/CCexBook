package com.ziruoling.quiz.ui.view;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ziruoling.quiz.R;
import com.ziruoling.quiz.data.Db;
import com.ziruoling.quiz.util.Ui;

import java.util.Calendar;

/**
 * 月历：周一开头，日期下方显示当日做题量；左右箭头切月；点日期回调。
 * 数据来自总统计账本的 stats_days 表。
 */
public class MonthCalendarView extends LinearLayout {

    public interface OnDayClick {
        void onDay(int year, int month, int day);
    }

    private int year;
    private int month;   // 1-12
    private final TextView title;
    private final GridLayout grid;
    private OnDayClick listener;

    public MonthCalendarView(Context c) {
        super(c);
        setOrientation(VERTICAL);
        int pad = Ui.dp(c, 12);
        setPadding(pad, pad, pad, pad);
        setBackground(Ui.bg(Ui.attrColor(c, R.attr.qzSurface), Ui.dp(c, 12)));

        Calendar now = Calendar.getInstance();
        year = now.get(Calendar.YEAR);
        month = now.get(Calendar.MONTH) + 1;

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(arrow("‹", v -> shift(-1)));
        title = new TextView(c);
        title.setGravity(Gravity.CENTER);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Ui.attrColor(c, R.attr.qzText));
        head.addView(title, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        head.addView(arrow("›", v -> shift(1)));
        addView(head);

        LinearLayout week = new LinearLayout(c);
        week.setOrientation(HORIZONTAL);
        week.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 4));
        String[] names = {"一", "二", "三", "四", "五", "六", "日"};
        for (String n : names) {
            TextView t = new TextView(c);
            t.setText(n);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(11);
            t.setTextColor(Ui.attrColor(c, R.attr.qzTextSub));
            week.addView(t, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        }
        addView(week);

        grid = new GridLayout(c);
        grid.setColumnCount(7);
        addView(grid);

        render();
    }

    public void setOnDayClick(OnDayClick l) {
        this.listener = l;
    }

    private TextView arrow(String text, OnClickListener l) {
        TextView t = new TextView(getContext());
        t.setText(text);
        t.setTextSize(20);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(Ui.attrColor(getContext(), R.attr.qzPrimary));
        t.setPadding(Ui.dp(getContext(), 12), 0, Ui.dp(getContext(), 12), 0);
        t.setOnClickListener(l);
        return t;
    }

    private void shift(int delta) {
        month += delta;
        if (month > 12) {
            month = 1;
            year++;
        } else if (month < 1) {
            month = 12;
            year--;
        }
        render();
    }

    private void render() {
        Context c = getContext();
        title.setText(year + " 年 " + month + " 月");
        grid.removeAllViews();

        final int[] series = Db.monthSeries(year, month);   // 下标 1..天数
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, 1);
        int dow = cal.get(Calendar.DAY_OF_WEEK);            // 1=周日 … 7=周六
        int offset = (dow + 5) % 7;                          // 周一 = 0
        int days = series.length - 1;

        for (int i = 0; i < offset; i++) {
            TextView blank = new TextView(c);
            grid.addView(blank, cellParams());
        }

        for (int day = 1; day <= days; day++) {
            final int d = day;
            int count = series[day];
            LinearLayout cell = new LinearLayout(c);
            cell.setOrientation(VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(0, Ui.dp(c, 3), 0, Ui.dp(c, 3));
            if (count > 0) {
                cell.setBackground(Ui.bg(Ui.attrColor(c, R.attr.qzPrimaryBg), Ui.dp(c, 8)));
            }

            TextView dayTv = new TextView(c);
            dayTv.setText(String.valueOf(day));
            dayTv.setGravity(Gravity.CENTER);
            dayTv.setTextSize(12);
            dayTv.setTextColor(Ui.attrColor(c, count > 0 ? R.attr.qzPrimary : R.attr.qzText));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            cell.addView(dayTv, dlp);

            if (count > 0) {
                View line = new View(c);
                // 浅色下用深色、深色下用浅色（按正文色取 55% 透明度）
                int rgb = Ui.attrColor(c, R.attr.qzText);
                line.setBackgroundColor(android.graphics.Color.argb(140,
                        android.graphics.Color.red(rgb),
                        android.graphics.Color.green(rgb),
                        android.graphics.Color.blue(rgb)));
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                        Ui.dp(c, 26), Ui.dp(c, 2));
                llp.gravity = Gravity.CENTER_HORIZONTAL;
                llp.topMargin = Ui.dp(c, 2);
                llp.bottomMargin = Ui.dp(c, 2);
                cell.addView(line, llp);

                TextView cntTv = new TextView(c);
                cntTv.setText(String.valueOf(count));
                cntTv.setGravity(Gravity.CENTER);
                cntTv.setTextSize(10);
                cntTv.setTextColor(Ui.attrColor(c, R.attr.qzPrimary));
                cell.addView(cntTv, new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            }

            cell.setOnClickListener(v -> {
                if (listener != null) listener.onDay(year, month, d);
            });
            grid.addView(cell, cellParams());
        }
    }

    private GridLayout.LayoutParams cellParams() {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = Ui.dp(getContext(), 42);
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(Ui.dp(getContext(), 2), Ui.dp(getContext(), 2),
                Ui.dp(getContext(), 2), Ui.dp(getContext(), 2));
        return lp;
    }
}
