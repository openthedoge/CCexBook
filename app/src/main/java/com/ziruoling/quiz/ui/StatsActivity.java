package com.ziruoling.quiz.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ziruoling.quiz.R;
import com.ziruoling.quiz.core.QuestionType;
import com.ziruoling.quiz.data.Bank;
import com.ziruoling.quiz.data.Db;
import com.ziruoling.quiz.data.ExamRecord;
import com.ziruoling.quiz.ui.dialog.ConfirmDialog;
import com.ziruoling.quiz.ui.widget.BarChartView;
import com.ziruoling.quiz.util.Ui;

import java.util.List;
import java.util.Locale;

/** 练习统计：全局指标 / 7日柱状 / 题型 / 题库 / 考试记录 */
public class StatsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnClear).setOnClickListener(v ->
                ConfirmDialog.show(this, "清空练习记录", "将清空全部答题记录与顺序进度，错题本、收藏与题库不受影响。确定？",
                        () -> {
                            Db.clearRecords();
                            refresh();
                            Ui.toast(this, "已清空练习记录");
                        }));
        findViewById(R.id.btnClearWrong).setOnClickListener(v ->
                ConfirmDialog.show(this, "清空错题本", "将清空全部错题记录，确定？",
                        () -> {
                            Db.clearWrongs();
                            refresh();
                            Ui.toast(this, "已清空错题本");
                        }));
        findViewById(R.id.btnClearFav).setOnClickListener(v ->
                ConfirmDialog.show(this, "清空收藏", "将清空全部收藏题目，确定？",
                        () -> {
                            Db.clearFavs();
                            refresh();
                            Ui.toast(this, "已清空收藏");
                        }));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        long[] g = Db.globalStats();
        ((TextView) findViewById(R.id.totalTv)).setText(String.valueOf(g[0]));
        ((TextView) findViewById(R.id.rateTv)).setText(g[0] <= 0 ? "-" : Math.round(g[1] * 100f / g[0]) + "%");
        ((TextView) findViewById(R.id.todayTv)).setText(String.valueOf(Db.todayCount()));
        ((TextView) findViewById(R.id.streakTv)).setText(Db.streak() + " 天");

        // 近7日柱状图
        int[] days = Db.last7Days();
        BarChartView bar = findViewById(R.id.bar);
        bar.setData(days, new String[]{"6天前", "5天前", "4天前", "3天前", "2天前", "昨天", "今天"},
                Ui.attrColor(this, R.attr.qzPrimary), Ui.attrColor(this, R.attr.qzTextSub));
        bar.setGridColor(Ui.attrColor(this, R.attr.qzDivider));

        // 题型正确率
        LinearLayout typeBox = findViewById(R.id.typeBox);
        typeBox.removeAllViews();
        int[][] ts = Db.typeStats();
        for (int t = 0; t < QuestionType.COUNT; t++) {
            if (ts[t][1] <= 0) continue;
            typeBox.addView(statRow(QuestionType.NAMES[t],
                    ts[t][1], ts[t][0], ts[t][1] <= 0 ? 0 : Math.round(ts[t][0] * 100f / ts[t][1])));
        }
        if (typeBox.getChildCount() == 0) {
            typeBox.addView(Ui.text(this, "暂无练习记录", 13, R.attr.qzTextSub, 0));
        }

        // 题库进度
        LinearLayout bankBox = findViewById(R.id.bankBox);
        bankBox.removeAllViews();
        List<Bank> banks = Db.listBanks();
        if (banks.isEmpty()) {
            bankBox.addView(Ui.text(this, "暂无题库", 13, R.attr.qzTextSub, 0));
        }
        for (Bank b : banks) {
            int done = Math.min(b.questionCount, b.doneCount);
            int pct = b.questionCount <= 0 ? 0 : Math.round(done * 100f / b.questionCount);
            bankBox.addView(statRow(b.name, b.questionCount, done, pct));
        }

        // 考试记录
        LinearLayout examBox = findViewById(R.id.examBox);
        examBox.removeAllViews();
        List<ExamRecord> exams = Db.listExams(10);
        if (exams.isEmpty()) {
            examBox.addView(Ui.text(this, "暂无考试记录", 13, R.attr.qzTextSub, 0));
        }
        for (ExamRecord e : exams) {
            TextView row = Ui.text(this, Ui.fmtTime(e.finishedAt) + "  " + e.name + "（" + e.bankName + "）  "
                            + Math.round(e.score) + " / " + Math.round(e.full) + " 分",
                    14, R.attr.qzText, 0);
            row.setPadding(0, Ui.dp(this, 5), 0, 0);
            long id = e.id;
            row.setOnClickListener(v -> ExamResultActivity.start(this, id));
            examBox.addView(row);
        }
    }

    private View statRow(String label, int total, int done, int pct) {
        View v = LayoutInflater.from(this).inflate(R.layout.item_stat_row, null);
        ((TextView) v.findViewById(R.id.label)).setText(label);
        ProgressBar pb = v.findViewById(R.id.progress);
        pb.setProgress(Math.max(0, Math.min(100, pct)));
        ((TextView) v.findViewById(R.id.value)).setText(done + " / " + total + "（" + pct + "%）");
        return v;
    }
}