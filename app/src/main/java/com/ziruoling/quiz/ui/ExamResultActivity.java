package com.ziruoling.quiz.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ziruoling.quiz.R;
import com.ziruoling.quiz.core.QuestionType;
import com.ziruoling.quiz.data.Db;
import com.ziruoling.quiz.data.ExamRecord;
import com.ziruoling.quiz.data.Question;
import com.ziruoling.quiz.util.ShareUtil;
import com.ziruoling.quiz.util.Ui;

import java.util.List;
import java.util.Locale;

/** 考试成绩单 */
public class ExamResultActivity extends BaseActivity {

    private static final String EXTRA_EXAM = "exam_id";
    private ExamRecord record;

    public static void start(Context c, long examId) {
        Intent it = new Intent(c, ExamResultActivity.class);
        it.putExtra(EXTRA_EXAM, examId);
        c.startActivity(it);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exam_result);

        long examId = getIntent().getLongExtra(EXTRA_EXAM, 0);
        record = Db.getExam(examId);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        TextView scoreTv = findViewById(R.id.scoreTv);
        TextView verdict = findViewById(R.id.verdict);
        TextView statDuration = findViewById(R.id.statDuration);
        TextView statCorrect = findViewById(R.id.statCorrect);
        TextView statRate = findViewById(R.id.statRate);

        int scoreInt = (int) Math.round(record.score);
        int fullInt = (int) Math.round(record.full);
        scoreTv.setText(scoreInt + " / " + fullInt + " 分");
        int rate = record.total <= 0 ? 0 : Math.round(record.correct * 100f / record.total);
        verdict.setText(rate >= 90 ? "优秀" : rate >= 75 ? "良好" : rate >= 60 ? "及格" : "继续加油");
        statDuration.setText(Ui.fmtClock(record.durationSec));
        statCorrect.setText(record.correct + " / " + record.total);
        statRate.setText(rate + "%");

        // 各题型
        LinearLayout typeBox = findViewById(R.id.typeBox);
        typeBox.removeAllViews();
        for (int t = 0; t < QuestionType.COUNT; t++) {
            if (record.typeDetail[t][1] <= 0) continue;
            int ok = record.typeDetail[t][0];
            int total = record.typeDetail[t][1];
            TextView row = Ui.text(this,
                    QuestionType.NAMES[t] + "：" + ok + " / " + total
                            + "（" + (total <= 0 ? 0 : Math.round(ok * 100f / total)) + "%）",
                    14, ok == total ? R.attr.qzGreen : (ok == 0 ? R.attr.qzRed : R.attr.qzTextSub), 0);
            row.setPadding(0, Ui.dp(this, 4), 0, 0);
            typeBox.addView(row);
        }

        // 错题回顾
        LinearLayout wrongBox = findViewById(R.id.wrongBox);
        wrongBox.removeAllViews();
        TextView wrongTitle = findViewById(R.id.wrongTitle);
        List<Long> wrongIds = record.wrongIds;
        if (wrongIds.isEmpty()) {
            wrongTitle.setVisibility(android.view.View.GONE);
            TextView t = Ui.text(this, "全部答对，太棒了！", 14, R.attr.qzGreen, 0);
            wrongBox.addView(t);
        } else {
            wrongTitle.setVisibility(View.VISIBLE);
            int max = Math.min(10, wrongIds.size());
            for (int i = 0; i < max; i++) {
                Question q = Db.getQuestion(wrongIds.get(i));
                if (q == null) continue;
                TextView t = Ui.text(this, (i + 1) + ". " + q.content, 13, R.attr.qzTextSub, 0);
                // 富文本渲染：上下标自绘、实体解码、波浪规范化
                t.setText(com.ziruoling.quiz.util.TextUtil.rich((i + 1) + ". " + q.content));
                t.setPadding(0, Ui.dp(this, 3), 0, 0);
                wrongBox.addView(t);
            }
            if (wrongIds.size() > 10) {
                TextView more = Ui.text(this, "…共 " + wrongIds.size() + " 道错题", 13, R.attr.qzTextSub, 0);
                more.setPadding(0, Ui.dp(this, 3), 0, 0);
                wrongBox.addView(more);
            }
        }

        findViewById(R.id.btnShare).setOnClickListener(v ->
                ShareUtil.share(this, ShareUtil.buildExamText(record)));
        findViewById(R.id.btnRetry).setOnClickListener(v -> {
            if (record.wrongIds.isEmpty()) {
                Ui.toast(this, "没有错题");
                return;
            }
            PracticeActivity.startPractice(this, record.bankId, record.wrongIds, 0,
                    PracticeActivity.MODE_WRONG, "错题重练", false);
        });
        findViewById(R.id.btnAgain).setOnClickListener(v -> {
            startActivity(new Intent(this, ExamConfigActivity.class)
                    .putExtra(ExamConfigActivity.EXTRA_BANK, record.bankId));
            finish();
        });
    }
}