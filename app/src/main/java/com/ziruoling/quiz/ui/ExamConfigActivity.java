package com.ziruoling.quiz.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.ziruoling.quiz.R;
import com.ziruoling.quiz.core.QuestionType;
import com.ziruoling.quiz.data.Db;
import com.ziruoling.quiz.data.Prefs;
import com.ziruoling.quiz.util.Ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 模拟考试设置：题型题量 / 时长 / 分值 / 乱序 */
public class ExamConfigActivity extends BaseActivity {

    public static final String EXTRA_BANK = "bank";

    private long bankId;
    private int[] counts;
    private final EditText[] countEds = new EditText[QuestionType.COUNT];
    private final EditText[] pointEds = new EditText[QuestionType.COUNT];
    private final int[] defPoints = {1, 2, 1, 2, 5, 2, 0};
    private TextView totalTv, fullTv;
    private EditText minutes;
    private Switch shuffle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exam_config);

        bankId = getIntent().getLongExtra(EXTRA_BANK, 0);
        counts = Db.typeCounts(bankId);
        totalTv = findViewById(R.id.totalTv);
        fullTv = findViewById(R.id.fullTv);
        minutes = findViewById(R.id.minutes);
        shuffle = findViewById(R.id.shuffle);
        shuffle.setChecked(Prefs.shuffleOpts());

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnAutoMix).setOnClickListener(v -> autoMix());
        findViewById(R.id.btnStart).setOnClickListener(v -> startExam());

        buildRows();
        updateTotal();
    }

    private void buildRows() {
        LinearLayout typeRows = findViewById(R.id.typeRows);
        typeRows.removeAllViews();
        LinearLayout pointRows = findViewById(R.id.pointRows);
        pointRows.removeAllViews();

        for (int t = 0; t < QuestionType.COUNT; t++) {
            if (counts[t] <= 0) continue;
            boolean examType = QuestionType.examinable(t);

            // 题量行
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Ui.dp(this, 6), 0, 0);
            TextView label = Ui.text(this, QuestionType.NAMES[t], 14, R.attr.qzText, 0);
            row.addView(label);
            EditText et = new EditText(this);
            et.setText(examType ? "0" : "0");
            et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            et.setGravity(Gravity.CENTER);
            et.setTextSize(14);
            et.setTextColor(Ui.attrColor(this, R.attr.qzText));
            et.setBackground(Ui.bgStroke(this, Ui.attrColor(this, R.attr.qzSurface),
                    Ui.attrColor(this, R.attr.qzStroke), Ui.dp(this, 8)));
            LinearLayout.LayoutParams lpEt = new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 38));
            lpEt.leftMargin = Ui.dp(this, 8);
            et.setLayoutParams(lpEt);
            row.addView(et);
            TextView hint = Ui.text(this, "（共 " + counts[t] + " 题" + (examType ? "" : "，简答不参与考试") + "）",
                    12, R.attr.qzTextSub, 0);
            hint.setPadding(Ui.dp(this, 8), 0, 0, 0);
            row.addView(hint);
            typeRows.addView(row);
            countEds[t] = et;

            if (examType) {
                LinearLayout prow = new LinearLayout(this);
                prow.setOrientation(LinearLayout.HORIZONTAL);
                prow.setGravity(Gravity.CENTER_VERTICAL);
                prow.setPadding(0, Ui.dp(this, 6), 0, 0);
                TextView plabel = Ui.text(this, QuestionType.SHORT_NAMES[t] + "每题", 14, R.attr.qzTextSub, 0);
                prow.addView(plabel);
                EditText pe = new EditText(this);
                pe.setText(String.valueOf(defPoints[t]));
                pe.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                pe.setGravity(Gravity.CENTER);
                pe.setTextSize(14);
                pe.setTextColor(Ui.attrColor(this, R.attr.qzText));
                pe.setBackground(Ui.bgStroke(this, Ui.attrColor(this, R.attr.qzSurface),
                        Ui.attrColor(this, R.attr.qzStroke), Ui.dp(this, 8)));
                LinearLayout.LayoutParams lpPe = new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 38));
                lpPe.leftMargin = Ui.dp(this, 8);
                pe.setLayoutParams(lpPe);
                prow.addView(pe);
                TextView punit = Ui.text(this, "分/题", 12, R.attr.qzTextSub, 0);
                punit.setPadding(Ui.dp(this, 6), 0, 0, 0);
                prow.addView(punit);
                pointRows.addView(prow);
                pointEds[t] = pe;
            }
        }
    }

    private int parseInt(EditText et, int def) {
        try {
            return Integer.parseInt(et.getText().toString().trim());
        } catch (Exception e) {
            return def;
        }
    }

    private void autoMix() {
        int avail = 0;
        for (int t = 0; t < QuestionType.COUNT; t++) {
            if (QuestionType.examinable(t) && counts[t] > 0) avail += counts[t];
        }
        if (avail == 0) {
            Ui.toast(this, "题库没有可考试的题目");
            return;
        }
        int target = Math.min(50, avail);
        int[] take = new int[QuestionType.COUNT];
        int sum = 0;
        for (int t = 0; t < QuestionType.COUNT; t++) {
            if (QuestionType.examinable(t) && counts[t] > 0) {
                take[t] = Math.max(1, Math.round(target * (float) counts[t] / avail));
                sum += take[t];
            }
        }
        // 微调至等于 target
        int t = 0;
        while (sum > target && t < QuestionType.COUNT) {
            if (take[t] > 1) {
                take[t]--;
                sum--;
            }
            t++;
        }
        for (int i = 0; i < QuestionType.COUNT; i++) {
            if (countEds[i] != null) countEds[i].setText(String.valueOf(take[i]));
        }
        updateTotal();
    }

    private void updateTotal() {
        int total = 0;
        int full = 0;
        for (int t = 0; t < QuestionType.COUNT; t++) {
            int n = countEds[t] == null ? 0 : Math.min(counts[t], Math.max(0, parseInt(countEds[t], 0)));
            total += n;
            int p = pointEds[t] == null ? defPoints[t] : Math.max(0, parseInt(pointEds[t], defPoints[t]));
            full += n * p;
        }
        totalTv.setText("共 " + total + " 题 · 满分 " + full + " 分");
        fullTv.setText("满分 " + full + " 分");
    }

    private void startExam() {
        List<Long> ids = new ArrayList<>();
        int[] points = new int[QuestionType.COUNT];
        for (int t = 0; t < QuestionType.COUNT; t++) {
            if (counts[t] <= 0 || !QuestionType.examinable(t)) continue;
            int n = Math.min(counts[t], Math.max(0, parseInt(countEds[t], 0)));
            points[t] = pointEds[t] == null ? defPoints[t] : Math.max(0, parseInt(pointEds[t], defPoints[t]));
            if (n > 0) {
                List<Long> pool = Db.questionIdsByFilter(bankId, 1 << t, null, false);
                Collections.shuffle(pool, new java.util.Random(System.currentTimeMillis() + t));
                ids.addAll(pool.subList(0, Math.min(n, pool.size())));
            }
        }
        if (ids.isEmpty()) {
            Ui.toast(this, "请至少设置 1 道题");
            return;
        }
        int dur = parseInt(minutes, 0);
        long[] arr = new long[ids.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = ids.get(i);
        ExamActivity.start(this, bankId, arr, Math.max(0, dur) * 60, points, shuffle.isChecked());
    }
}