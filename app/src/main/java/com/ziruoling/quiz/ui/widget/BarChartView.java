package com.ziruoling.quiz.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/** 柱状图（近7日做题量） */
public class BarChartView extends View {

    private int[] values = new int[7];
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int barColor = 0xFF3D6FE0;
    private int textColor = 0xFF6B7686;
    private int gridColor = 0x14000000;
    private String[] labels = {"", "", "", "", "", "", ""};

    public BarChartView(Context context) {
        super(context);
    }

    public BarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setData(int[] values, String[] labels, int barColor, int textColor) {
        this.values = values == null ? new int[7] : values;
        this.labels = labels == null ? new String[7] : labels;
        this.barColor = barColor;
        this.textColor = textColor;
        invalidate();
    }

    /** 网格线颜色（默认 0x14000000 在深色下不可见，调用方应传入主题相关色） */
    public void setGridColor(int c) {
        this.gridColor = c;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float density = getResources().getDisplayMetrics().density;
        float textSize = 10 * density;
        textPaint.setTextSize(textSize);
        textPaint.setColor(textColor);
        textPaint.setTextAlign(Paint.Align.CENTER);
        gridPaint.setStrokeWidth(density);
        gridPaint.setColor(gridColor);

        // 顶部留白
        float topPad = 18 * density;
        float bottomPad = 16 * density;
        float chartH = h - topPad - bottomPad;
        float slot = (float) w / values.length;
        float barW = slot * 0.45f;
        int max = 0;
        for (int v : values) max = Math.max(max, v);
        if (max <= 0) max = 1;

        barPaint.setColor(barColor);
        for (int i = 0; i < values.length; i++) {
            float cx = slot * i + slot / 2;
            float bh = values[i] <= 0 ? 2 * density : chartH * values[i] / max;
            RectF r = new RectF(cx - barW / 2, topPad + chartH - bh, cx + barW / 2, topPad + chartH);
            // 圆角柱
            canvas.drawRoundRect(r, barW / 2, barW / 2, barPaint);
            if (values[i] > 0) {
                canvas.drawText(String.valueOf(values[i]), cx, r.top - 4 * density, textPaint);
            }
            if (labels[i] != null && !labels[i].isEmpty()) {
                canvas.drawText(labels[i], cx, h - 4 * density, textPaint);
            }
        }
    }
}