package com.ziruoling.quiz.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ziruoling.quiz.R;
import com.ziruoling.quiz.core.AnswerUtil;
import com.ziruoling.quiz.core.OptionShuffler;
import com.ziruoling.quiz.core.QuestionType;
import com.ziruoling.quiz.data.Db;
import com.ziruoling.quiz.data.Prefs;
import com.ziruoling.quiz.data.Question;
import com.ziruoling.quiz.ui.dialog.AnswerSheetDialog;
import com.ziruoling.quiz.util.Ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 模拟考试：计时答题 + 统一交卷评分 */
public class ExamActivity extends BaseActivity {

    private static final String EXTRA_BANK = "bank";
    private static final String EXTRA_IDS = "ids";
    private static final String EXTRA_DURATION = "duration";
    private static final String EXTRA_POINTS = "points";
    private static final String EXTRA_SHUFFLE = "shuffle";

    private long bankId;
    private long[] ids;
    private int durationSec;
    private int[] points;
    private boolean shuffleOpts;
    private int cur;
    private int remaining;

    private final long sessionSeed = System.currentTimeMillis();
    private final Map<Long, Question> qCache = new HashMap<>();
    private final Map<Long, String> displayAnswerCache = new HashMap<>();
    private final Map<Long, String> singleAns = new HashMap<>();
    private final Map<Long, Set<Character>> multiSel = new HashMap<>();
    private final Map<Long, List<Character>> sortSel = new HashMap<>();
    private final Map<Long, String> fillAns = new HashMap<>();

    private TextView timerTv, progressTv, contentTv;
    private LinearLayout headChips, optionsBox, fillBox;
    private EditText fillEt;
    private Handler handler = new Handler();
    private Runnable ticker;
    private boolean finished;

    public static void start(Context c, long bankId, long[] ids, int durationSec, int[] points, boolean shuffle) {
        Intent it = new Intent(c, ExamActivity.class);
        it.putExtra(EXTRA_BANK, bankId);
        it.putExtra(EXTRA_IDS, ids);
        it.putExtra(EXTRA_DURATION, durationSec);
        it.putExtra(EXTRA_POINTS, points);
        it.putExtra(EXTRA_SHUFFLE, shuffle);
        c.startActivity(it);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exam);

        bankId = getIntent().getLongExtra(EXTRA_BANK, 0);
        ids = getIntent().getLongArrayExtra(EXTRA_IDS);
        if (ids == null) ids = new long[0];
        durationSec = getIntent().getIntExtra(EXTRA_DURATION, 0);
        points = getIntent().getIntArrayExtra(EXTRA_POINTS);
        if (points == null) points = new int[QuestionType.COUNT];
        else if (points.length < QuestionType.COUNT) {
            int[] p2 = new int[QuestionType.COUNT];
            System.arraycopy(points, 0, p2, 0, points.length);
            points = p2;
        }
        shuffleOpts = getIntent().getBooleanExtra(EXTRA_SHUFFLE, false);

        timerTv = findViewById(R.id.timer);
        progressTv = findViewById(R.id.progress);
        headChips = findViewById(R.id.headChips);
        contentTv = findViewById(R.id.content);
        optionsBox = findViewById(R.id.options);
        fillBox = findViewById(R.id.fillBox);
        fillEt = findViewById(R.id.fillEt);

        findViewById(R.id.btnBack).setOnClickListener(v -> confirmAbandon());
        findViewById(R.id.btnSubmit).setOnClickListener(v -> confirmSubmit());
        findViewById(R.id.btnSheet).setOnClickListener(v -> showSheet());
        findViewById(R.id.btnPrev).setOnClickListener(v -> {
            if (cur > 0) {
                saveInput();
                cur--;
                render();
            }
        });
        findViewById(R.id.btnNext).setOnClickListener(v -> {
            saveInput();
            if (cur >= ids.length - 1) {
                confirmSubmit();
            } else {
                cur++;
                render();
            }
        });
        fillEt.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (cur < ids.length) fillAns.put(ids[cur], s.toString());
            }
        });

        remaining = durationSec;
        updateTimer();
        if (durationSec > 0) {
            ticker = new Runnable() {
                @Override
                public void run() {
                    if (finished) return;
                    remaining--;
                    updateTimer();
                    if (remaining <= 0) {
                        autoSubmit();
                    } else {
                        handler.postDelayed(this, 1000);
                    }
                }
            };
            handler.postDelayed(ticker, 1000);
        }
        render();
    }

    private void render() {
        if (ids.length == 0) {
            finish();
            return;
        }
        Question q = getQ(ids[cur]);
        int answered = countAnswered();
        progressTv.setText("已答 " + answered + " / " + ids.length + " 题");

        headChips.removeAllViews();
        headChips.addView(Ui.chip(this, QuestionType.SHORT_NAMES[q.type],
                R.attr.qzPrimary, R.attr.qzPrimaryBg));
        if (q.category != null && !q.category.isEmpty()) {
            TextView cat = Ui.chip(this, q.category, R.attr.qzTextSub, R.attr.qzChipBg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = Ui.dp(this, 6);
            cat.setLayoutParams(lp);
            headChips.addView(cat);
        }
        contentTv.setText(com.ziruoling.quiz.util.TextUtil.rich(q.content));
        optionsBox.removeAllViews();
        fillBox.setVisibility(View.GONE);
        if (q.type == QuestionType.FILL) {
            fillBox.setVisibility(View.VISIBLE);
            fillEt.setText(fillAns.get(q.id) == null ? "" : fillAns.get(q.id));
        }
        buildOptions(q);
    }

    private void buildOptions(Question q) {
        boolean shuffle = shuffleOpts && !OptionShuffler.shouldSkip(q.type, q.options.size());
        List<String> opts = q.options;
        String dispAnswer = q.answer;
        if (shuffle) {
            OptionShuffler.Shuffled sh = OptionShuffler.shuffle(q.options, q.answer, sessionSeed + q.id);
            opts = sh.options;
            dispAnswer = sh.answer;
        }
        displayAnswerCache.put(q.id, dispAnswer);
        for (int i = 0; i < opts.size(); i++) {
            char letter = (char) ('A' + i);
            final char L = letter;
            TextView row = new TextView(this);
            row.setText(com.ziruoling.quiz.util.TextUtil.rich(optionLabel(q, L, opts.get(i))));
            row.setTextSize(15);
            row.setTextColor(Ui.attrColor(this, R.attr.qzText));
            row.setLineSpacing(Ui.dp(this, 2), 1f);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(this, 8);
            row.setLayoutParams(lp);
            row.setTag("opt_" + L);
            row.setOnClickListener(v -> onPick(q, L));
            optionsBox.addView(row);
            // 回填选中态
            boolean sel;
            if (q.type == QuestionType.MULTI) {
                sel = multiSel.containsKey(q.id) && multiSel.get(q.id).contains(L);
            } else if (q.type == QuestionType.SORT) {
                sel = sortSel.containsKey(q.id) && sortSel.get(q.id).contains(L);
            } else {
                sel = singleAns.get(q.id) != null && singleAns.get(q.id).charAt(0) == L;
            }
            if (sel) applyPick(row, true);
        }
    }

    private static final char[] SORT_CIRCLES = {'①', '②', '③', '④', '⑤', '⑥'};

    /** 选项文本：排序题已排项前显示序号圈 */
    private String optionLabel(Question q, char letter, String content) {
        List<Character> seq = sortSel.get(q.id);
        if (q.type == QuestionType.SORT && seq != null) {
            int pos = seq.indexOf(letter);
            if (pos >= 0 && pos < SORT_CIRCLES.length) {
                return SORT_CIRCLES[pos] + String.valueOf(letter) + ". " + content;
            }
        }
        return String.valueOf(letter) + ". " + content;
    }

    private void onPick(Question q, char letter) {
        if (q.type == QuestionType.SORT) {
            // 排序题：点击依序排号；再点已排项取消（后续项自动重排）
            List<Character> seq = sortSel.get(q.id);
            if (seq == null) {
                seq = new ArrayList<>();
                sortSel.put(q.id, seq);
            }
            if (seq.contains(letter)) {
                seq.remove(Character.valueOf(letter));
            } else {
                seq.add(letter);
            }
            applyPickStyle(q, letter);
            return;
        }
        if (q.type == QuestionType.MULTI) {
            Set<Character> set = multiSel.get(q.id);
            if (set == null) {
                set = new HashSet<>();
                multiSel.put(q.id, set);
            }
            if (set.contains(letter)) {
                set.remove(letter);
            } else {
                set.add(letter);
            }
            applyPickStyle(q, letter);
        } else {
            singleAns.put(q.id, String.valueOf(letter));
            applyPickStyle(q, letter);
        }
    }

    private void applyPickStyle(Question q, char letter) {
        for (int i = 0; i < optionsBox.getChildCount(); i++) {
            View v = optionsBox.getChildAt(i);
            char L = (char) ('A' + i);
            boolean sel;
            if (q.type == QuestionType.MULTI) {
                sel = multiSel.containsKey(q.id) && multiSel.get(q.id).contains(L);
            } else if (q.type == QuestionType.SORT) {
                sel = sortSel.containsKey(q.id) && sortSel.get(q.id).contains(L);
            } else {
                sel = L == letter;
            }
            if (v instanceof TextView && q.type == QuestionType.SORT) {
                ((TextView) v).setText(com.ziruoling.quiz.util.TextUtil.rich(
                        optionLabel(q, L, q.options.get(i))));
            }
            applyPick(v, sel);
        }
    }

    private void applyPick(View v, boolean sel) {
        if (sel) {
            v.setBackground(Ui.bgStroke(this, Ui.attrColor(this, R.attr.qzPrimaryBg),
                    Ui.attrColor(this, R.attr.qzPrimary), Ui.dp(this, 12)));
        } else {
            v.setBackground(Ui.bgStroke(this, Ui.attrColor(this, R.attr.qzSurface),
                    Ui.attrColor(this, R.attr.qzStroke), Ui.dp(this, 12)));
        }
    }

    private int countAnswered() {
        int n = 0;
        for (long qid : ids) {
            if (singleAns.containsKey(qid)
                    || (multiSel.containsKey(qid) && !multiSel.get(qid).isEmpty())
                    || (sortSel.containsKey(qid) && !sortSel.get(qid).isEmpty())
                    || (fillAns.containsKey(qid) && !fillAns.get(qid).trim().isEmpty())) {
                n++;
            }
        }
        return n;
    }

    private void saveInput() {
        if (cur < ids.length && fillBox.getVisibility() == View.VISIBLE) {
            fillAns.put(ids[cur], fillEt.getText().toString());
        }
    }

    private void updateTimer() {
        timerTv.setText(remaining <= 0 ? "不限时" : Ui.fmtClock(remaining));
        timerTv.setTextColor(Ui.attrColor(this, remaining <= 60 && durationSec > 0
                ? R.attr.qzRed : R.attr.qzPrimary));
    }

    private void confirmSubmit() {
        saveInput();
        int unanswered = ids.length - countAnswered();
        new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("确认交卷")
                .setMessage("已答 " + countAnswered() + " / " + ids.length + " 题" + (unanswered > 0 ? "，还有 " + unanswered + " 题未作答" : "") + "。交卷后立即评分，确定交卷？")
                .setPositiveButton("交卷", (d, w) -> submit())
                .setNegativeButton("继续答题", null)
                .show();
    }

    private void autoSubmit() {
        if (finished) return;
        Ui.toast(this, "时间到，自动交卷");
        submit();
    }

    private void confirmAbandon() {
        new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("退出考试")
                .setMessage("退出后本次作答不会计入成绩，确定退出？")
                .setPositiveButton("退出", (d, w) -> finish())
                .setNegativeButton("继续考试", null)
                .show();
    }

    /** 交卷评分（包内可见供冒烟测试调用） */
    void submit() {
        if (finished) return;
        finished = true;
        if (ticker != null) handler.removeCallbacks(ticker);
        int answeredCount = countAnswered();
        int correctCount = 0;
        double score = 0;
        double full = 0;
        List<Long> wrongIds = new ArrayList<>();
        int[][] detail = new int[QuestionType.COUNT][2];
        for (long qid : ids) {
            Question q = getQ(qid);
            String ans = userAnswer(qid);
            boolean ok = isCorrect(q, ans);
            // 本次展示顺序下的正确答案（选项乱序时字母已重映射），与部分分判定同一口径
            String expected = displayAnswerOf(q);
            double qFull = fullPointsOf(q);
            detail[q.type][1]++;
            if (ok) {
                detail[q.type][0]++;
                correctCount++;
                score += qFull;
            } else if (partialCredit(q, ans, expected)) {
                score += qFull * Prefs.examPartialPercent() / 100.0;
            } else {
                wrongIds.add(qid);
            }
            full += qFull;
            Db.addRecord(q.bankId, qid, 1, ans, ok, Prefs.wrongAutoRemove());
            if (ans != null && !ans.trim().isEmpty()) Prefs.snakeGateOnAnswer();
        }
        long examId = Db.addExam(bankId, "模拟考试", ids.length, answeredCount, correctCount,
                score, full, Math.max(0, durationSec - remaining), wrongIds, detail);
        finish();
        ExamResultActivity.start(this, examId);
    }

    /** 单题满分：填空 = 空数 × 每空分值，其它 = 题型分值 */
    private double fullPointsOf(Question q) {
        if (q.type == QuestionType.FILL) {
            int blanks = com.ziruoling.quiz.core.AnswerUtil.blankCount(q.content);
            if (blanks <= 0) blanks = 1;
            return blanks * Prefs.examBlankScore();
        }
        return points[q.type];
    }

    /**
     * 本次展示顺序下的正确答案：已渲染的题直接用缓存；未渲染的题按同一确定性种子重算乱序答案。
     * 与 {@link #buildOptions} 的映射口径一致（{@code sessionSeed + q.id}、同样的跳过条件）。
     */
    private String displayAnswerOf(Question q) {
        String cached = displayAnswerCache.get(q.id);
        if (cached != null) return cached;
        String expected = q.answer;
        if (shuffleOpts && !OptionShuffler.shouldSkip(q.type, q.options.size())) {
            expected = OptionShuffler.shuffle(q.options, q.answer, sessionSeed + q.id).answer;
        }
        displayAnswerCache.put(q.id, expected);
        return expected;
    }

    /** 多选部分分：所选答案非空、全部包含在本次展示映射后的正确答案内且未选全 */
    private boolean partialCredit(Question q, String ans, String expected) {
        if (q.type != QuestionType.MULTI) return false;
        String u = com.ziruoling.quiz.core.AnswerUtil.extractLetters(ans);
        String a = com.ziruoling.quiz.core.AnswerUtil.extractLetters(expected);
        if (u.isEmpty() || a.isEmpty() || u.equals(a)) return false;
        for (int i = 0; i < u.length(); i++) {
            if (a.indexOf(u.charAt(i)) < 0) return false;
        }
        return true;
    }

    private Question getQ(long id) {
        Question q = qCache.get(id);
        if (q == null) {
            q = Db.getQuestion(id);
            if (q != null) qCache.put(id, q);
        }
        return q;
    }

    private String userAnswer(long qid) {
        if (singleAns.containsKey(qid)) return singleAns.get(qid);
        Set<Character> set = multiSel.get(qid);
        if (set != null && !set.isEmpty()) {
            List<Character> list = new ArrayList<>(set);
            java.util.Collections.sort(list);
            StringBuilder sb = new StringBuilder();
            for (char c : list) sb.append(c);
            return sb.toString();
        }
        List<Character> seq = sortSel.get(qid);
        if (seq != null && !seq.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (char c : seq) sb.append(c);
            return sb.toString();
        }
        String f = fillAns.get(qid);
        return f == null ? "" : f.trim();
    }

    private boolean isCorrect(Question q, String ans) {
        if (ans == null || ans.isEmpty()) return false;
        switch (q.type) {
            case QuestionType.SINGLE:
            case QuestionType.JUDGE:
                return AnswerUtil.matchSingle(ans, displayAnswerCache.get(q.id));
            case QuestionType.MULTI:
                return AnswerUtil.matchMulti(ans, displayAnswerCache.get(q.id));
            case QuestionType.SORT:
                return AnswerUtil.matchSort(ans, displayAnswerCache.get(q.id));
            default:
                return AnswerUtil.matchFill(ans, q.answer);
        }
    }

    private boolean answered(int i) {
        long qid = ids[i];
        return singleAns.containsKey(qid)
                || (multiSel.containsKey(qid) && !multiSel.get(qid).isEmpty())
                || (sortSel.containsKey(qid) && !sortSel.get(qid).isEmpty())
                || (fillAns.containsKey(qid) && !fillAns.get(qid).trim().isEmpty());
    }

    private void showSheet() {
        int n = ids.length;
        int[] states = new int[n];
        for (int i = 0; i < n; i++) {
            states[i] = answered(i) ? 1 : 0;
        }
        AnswerSheetDialog.show(this, n, cur, states,
                "答题卡（已答 " + countAnswered() + "/" + n + "）", pos -> {
                    saveInput();
                    cur = pos;
                    render();
                });
    }
}