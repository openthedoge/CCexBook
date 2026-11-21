package com.ziruoling.quiz.util;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.style.ReplacementSpan;

/**
 * 题干前的题号小标签：作为行内 span 参与排版，只占首行开头的一点位置，
 * 换行后的后续行会自动回到最左边（不像横向布局那样整段缩进一列）。
 */
public final class DocNoSpan extends ReplacementSpan {

    private final String text;
    private final int bgColor;
    private final int fgColor;
    private final float padH;
    private final float gap;
    private final float height;
    private final float textSize;

    /** 最近一次排版算出的标签宽度（不含 gap），供 draw 使用 */
    private float width;

    /**
     * @param text     显示内容（一般就是数字）
     * @param bgColor  标签底色
     * @param fgColor  数字颜色
     * @param padH     左右内边距（px）
     * @param gap      标签与正文之间的间距（px）
     * @param height   标签高度（px）
     * @param textSize 标签字号（px）
     */
    public DocNoSpan(String text, int bgColor, int fgColor,
                     float padH, float gap, float height, float textSize) {
        this.text = text == null ? "" : text;
        this.bgColor = bgColor;
        this.fgColor = fgColor;
        this.padH = padH;
        this.gap = gap;
        this.height = height;
        this.textSize = textSize;
    }

    public String text() {
        return text;
    }

    @Override
    public int getSize(Paint paint, CharSequence source, int start, int end,
                       Paint.FontMetricsInt fm) {
        float scale = paint.getTextSize() <= 0 ? 1f : textSize / paint.getTextSize();
        width = paint.measureText(text) * scale + padH * 2f;
        return Math.round(width + gap);
    }

    @Override
    public void draw(Canvas canvas, CharSequence source, int start, int end,
                     float x, int top, int y, int bottom, Paint paint) {
        Paint.FontMetricsInt fm = paint.getFontMetricsInt();
        float centerY = y + (fm.ascent + fm.descent) / 2f;
        float half = height / 2f;
        RectF r = new RectF(x, centerY - half, x + width, centerY + half);

        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(bgColor);
        canvas.drawRoundRect(r, half, half, bg);

        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(fgColor);
        tp.setTextSize(textSize);
        tp.setTypeface(Typeface.DEFAULT_BOLD);
        Paint.FontMetrics tfm = tp.getFontMetrics();
        float ty = centerY - (tfm.ascent + tfm.descent) / 2f;
        canvas.drawText(text, r.centerX() - tp.measureText(text) / 2f, ty, tp);
    }
}
