package com.ziruoling.quiz.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.ziruoling.quiz.R;
import com.ziruoling.quiz.core.AnswerUtil;
import com.ziruoling.quiz.core.AnswerRule;
import com.ziruoling.quiz.core.OptionShuffler;
import com.ziruoling.quiz.core.QuestionType;
import com.ziruoling.quiz.data.Db;
import com.ziruoling.quiz.data.Prefs;
import com.ziruoling.quiz.data.Question;
import com.ziruoling.quiz.ui.dialog.AnswerSheetDialog;
import com.ziruoling.quiz.ui.dialog.ConfirmDialog;
import com.ziruoling.quiz.ui.dialog.InputDialog;
import com.ziruoling.quiz.ui.dialog.TagPickDialog;
import com.ziruoling.quiz.ui.dialog.TextEditDialog;
import com.ziruoling.quiz.ui.dialog.TypeFilterDialog;
import com.ziruoling.quiz.util.ShareUtil;
import com.ziruoling.quiz.util.SoundFx;
import com.ziruoling.quiz.util.TextUtil;
import com.ziruoling.quiz.util.Ui;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 刷题核心引擎：顺序/随机/背题/错题重做/收藏/专项 统一入口。
 * 传入题目 id 列表驱动；作答即时判分，写入记录与错题本。
 */
public class PracticeActivity extends BaseActivity {

    public static final int MODE_PRACTICE = 0;
    public static final int MODE_RECITE = 1;
    public static final int MODE_WRONG = 2;

    /** 切题动画方向 */
    private static final int SLIDE_NONE = 0;
    private static final int SLIDE_NEXT = 1; // 新题从右侧滑入
    private static final int SLIDE_PREV = 2; // 新题从左侧滑入

    private static final String EXTRA_IDS = "ids";
    private static final String EXTRA_START = "start";
    private static final String EXTRA_MODE = "mode";

    private static boolean pendingSpecial;
    private boolean special;

    /** 由专项入口在 startPractice 前调用，标记本次为专项会话 */
    public static void setPendingSpecial() {
        pendingSpecial = true;
    }
    private static final String EXTRA_BANK = "bank";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_PROGRESS = "progress";
    /** 是否回填历史「已答」状态（顺序练习/背题/专项开启） */
    private static final String EXTRA_RESTORE = "restore";
    private static final int REQ_EDIT_QUESTION = 401;
    /** 按住题型标签多久进入题目编辑器（比系统长按更久，避免误触） */
    private static final long TYPE_CHIP_HOLD_MS = 2000;

    private long[] ids;
    private int cur;
    private int mode;
    private long bankId;
    private String titleText = "";
    private boolean saveProgress;
    /** 重进练习时是否把数据库里已有的作答回填成「已答」并锁定 */
    private boolean restoreAnswered;

    private final Handler handler = new Handler();
    private final Handler holdHandler = new Handler(android.os.Looper.getMainLooper());
    private Runnable holdAction;
    private final long sessionSeed = System.currentTimeMillis();
    private final Map<Long, Question> qCache = new HashMap<>();
    private final Map<Long, OptionShuffler.Shuffled> shuffledCache = new HashMap<>();
    private final Map<Long, String> displayAnswerCache = new HashMap<>();
    private final SparseArray<String> userAnswers = new SparseArray<>();
    private final Set<Long> answeredSet = new HashSet<>();
    /** 从数据库回填的「历史已答」（区别于本次会话真正作答的题） */
    private final Set<Long> restoredIds = new HashSet<>();
    private final Map<Long, Boolean> correctMap = new HashMap<>();
    private final Set<Long> sessionWrong = new HashSet<>();
    private int sessionAnswered = 0;
    private int sessionCorrectCount = 0;
    /** 多空填空「有未填写的内容」二次点击窗口：上次拦截的题目 id 与时间（elapsedRealtime 毫秒） */
    private long fillGuardQid = -1;
    private long fillGuardAt = 0L;
    private long currentQid = -1;
    /** 背题模式：当前题答案是否已显示 */
    private boolean reciteRandom;
    /** 左右滑动切题手势 */
    private GestureDetector gestureDetector;

    private TextView counterTv, contentTv, verdict, answerTv, analysisTv, btnReveal, noteTv;
    private android.view.View noteCard;
    private LinearLayout headChips, optionsBox, resultCard, analysisCard, fillBox, shortBox;
    private EditText fillEt, shortEt;
    private final java.util.List<EditText> blankEts = new java.util.ArrayList<>();
    private TextView btnFillSubmit, btnShortSubmit, btnMultiConfirm;
    private TextView btnShare, btnSheet, btnFav, btnFlag, btnPrev, btnNext, btnRemoveWrong;
    /** 当前题目展示用的选项顺序（分享时保持一致） */
    private List<String> currentOpts = new ArrayList<>();

    public static void startPractice(Context c, long bankId, List<Long> qids, int start,
                                     int mode, String title, boolean saveProgress) {
        startPractice(c, bankId, qids, start, mode, title, saveProgress, false);
    }

    /**
     * @param restoreAnswered 是否回填历史作答（顺序练习/背题/专项开启；随机、错题重练、收藏、考试不开启）
     */
    public static void startPractice(Context c, long bankId, List<Long> qids, int start,
                                     int mode, String title, boolean saveProgress,
                                     boolean restoreAnswered) {
        if (qids == null || qids.isEmpty()) return;
        long[] arr = new long[qids.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = qids.get(i);
        Intent it = new Intent(c, PracticeActivity.class);
        it.putExtra(EXTRA_IDS, arr);
        it.putExtra(EXTRA_START, Math.max(0, Math.min(start, arr.length - 1)));
        it.putExtra(EXTRA_MODE, mode);
        it.putExtra(EXTRA_BANK, bankId);
        it.putExtra(EXTRA_TITLE, title == null ? "" : title);
        it.putExtra(EXTRA_PROGRESS, saveProgress);
        it.putExtra(EXTRA_RESTORE, restoreAnswered);
        c.startActivity(it);
    }

    /**
     * 背题模式固定乱序：以题库为种子打乱题目顺序——
     * 同一题库每次进入顺序相同（「从上次继续」的位置依然有效），又能避免顺序记忆。
     * 由入口调用（BankHome / 收藏背题），PracticeActivity 内不再重复打乱。
     */
    /** 无种子洗牌：每次进入随机背题都换一个顺序 */
    public static void reciteShuffleSeedless(long[] ids) {
        if (ids == null || ids.length < 2) return;
        java.util.Random rnd = new java.util.Random(System.nanoTime());
        for (int i = ids.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            long t = ids[i];
            ids[i] = ids[j];
            ids[j] = t;
        }
    }

    /** 本次会话是否「随机背题」（答题卡跳题时用于重新打乱后续） */
    private static boolean reciteRandomSession = false;

    public static void setReciteRandom(boolean random) {
        reciteRandomSession = random;
    }

    public static void reciteShuffle(long[] ids, long bankId) {
        if (ids == null || ids.length < 2) return;
        java.util.Random rnd = new java.util.Random(bankId * 31L + 17);
        for (int i = ids.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            long t = ids[i];
            ids[i] = ids[j];
            ids[j] = t;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_practice);

        ids = getIntent().getLongArrayExtra(EXTRA_IDS);
        if (ids == null) ids = new long[0];
        cur = Math.max(0, Math.min(getIntent().getIntExtra(EXTRA_START, 0), Math.max(0, ids.length - 1)));
        mode = getIntent().getIntExtra(EXTRA_MODE, MODE_PRACTICE);
        reciteRandom = reciteRandomSession;
        bankId = getIntent().getLongExtra(EXTRA_BANK, 0);
        titleText = getIntent().getStringExtra(EXTRA_TITLE);
        if (titleText == null) titleText = "";
        saveProgress = getIntent().getBooleanExtra(EXTRA_PROGRESS, false);
        restoreAnswered = getIntent().getBooleanExtra(EXTRA_RESTORE, false);
        special = pendingSpecial;
        pendingSpecial = false;
        if (restoreAnswered) restoreAllAnswered();
        if (special) {
            java.util.Map<Long, Boolean> done = Db.specialDone(bankId);
            for (java.util.Map.Entry<Long, Boolean> e : done.entrySet()) {
                answeredSet.add(e.getKey());
                correctMap.put(e.getKey(), e.getValue());
                userAnswers.put((int) (long) e.getKey(), "");
                restoredIds.add(e.getKey());
            }
        }

        TextView titleTv = findViewById(R.id.title);
        counterTv = findViewById(R.id.counter);
        headChips = findViewById(R.id.headChips);
        contentTv = findViewById(R.id.content);
        noteCard = findViewById(R.id.noteCard);
        noteTv = findViewById(R.id.noteTv);
        if (noteTv != null) noteTv.setOnClickListener(v -> editNote());
        optionsBox = findViewById(R.id.options);
        fillBox = findViewById(R.id.fillBox);
        fillEt = findViewById(R.id.fillEt);
        btnFillSubmit = findViewById(R.id.btnFillSubmit);
        shortBox = findViewById(R.id.shortBox);
        shortEt = findViewById(R.id.shortEt);
        btnShortSubmit = findViewById(R.id.btnShortSubmit);
        btnMultiConfirm = findViewById(R.id.btnMultiConfirm);
        resultCard = findViewById(R.id.resultCard);
        verdict = findViewById(R.id.verdict);
        answerTv = findViewById(R.id.answerTv);
        btnReveal = findViewById(R.id.btnReveal);
        analysisCard = findViewById(R.id.analysisCard);
        analysisTv = findViewById(R.id.analysisTv);
        btnSheet = findViewById(R.id.btnSheet);
        btnShare = findViewById(R.id.btnShare);
        btnFav = findViewById(R.id.btnFav);
        btnFlag = findViewById(R.id.btnFlag);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);
        btnRemoveWrong = findViewById(R.id.btnRemoveWrong);

        titleTv.setText(titleText.isEmpty() ? "刷题" : titleText);
        if (mode == MODE_RECITE) btnMultiConfirm.setVisibility(View.GONE);
        setupAutoBall();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnPrev.setOnClickListener(v -> {
            if (cur > 0) {
                cur--;
                render(SLIDE_PREV);
            }
        });
        btnNext.setOnClickListener(v -> next());
        btnFav.setOnClickListener(v -> toggleFav());
        btnShare.setOnClickListener(v -> shareCurrent());
        if (btnShare != null) {
            btnShare.setTextColor(Ui.attrColor(this, R.attr.qzOnPrimary));
            btnShare.setBackground(Ui.bgStroke(this, 0x00000000,
                    Ui.attrColor(this, R.attr.qzOnPrimary), Ui.dp(this, 16)));
            btnShare.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
            btnShare.setTextSize(13);
            btnShare.setMinWidth(Ui.dp(this, 64));
        }
        // 答案卡 / 解析卡长按：修改答案、编辑解析、重做本题（不占用界面空间）
        resultCard.setOnLongClickListener(v -> {
            showQuestionMenu();
            return true;
        });
        answerTv.setOnLongClickListener(v -> {
            showQuestionMenu();
            return true;
        });
        analysisCard.setOnLongClickListener(v -> {
            showQuestionMenu();
            return true;
        });
        analysisTv.setOnLongClickListener(v -> {
            showQuestionMenu();
            return true;
        });
        btnFlag.setOnClickListener(v -> toggleFlag());
        btnFlag.setOnLongClickListener(v -> {
            openTagEditor();
            return true;
        });
        // 错题重做：作答后如果这道题仍在错题本里，可以手动移出
        btnRemoveWrong.setOnClickListener(v -> {
            if (ids.length == 0) return;
            Question q = getQ(ids[cur]);
            Db.resolveWrong(q.id);
            btnRemoveWrong.setVisibility(View.GONE);
            Ui.toast(this, "已移出错题本");
        });
        btnSheet.setOnClickListener(v -> showSheet());
        btnFillSubmit.setOnClickListener(v -> submitFill());
        btnShortSubmit.setOnClickListener(v -> showShortAnswer());
        btnMultiConfirm.setOnClickListener(v -> submitMulti());

        // 左右滑动切题（只旁路识别，不拦截点击与上下滚动）
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                int min = Ui.dp(PracticeActivity.this, 80);
                if (Math.abs(dx) > Math.abs(dy) * 1.5f && Math.abs(dx) > min) {
                    if (dx < 0) {
                        next(); // 左滑：下一题
                    } else if (cur > 0) {
                        cur--;  // 右滑：上一题
                        render(SLIDE_PREV);
                    }
                    return true;
                }
                return false;
            }
        });

        render();
    }

    /** 手势总入口：旁路识别手势，不消费任何事件（点击/滚动不受影响） */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // 摸到悬浮球/边缘条时不走左右滑动翻页
        if (gestureDetector != null && !hitsBall(ev)) gestureDetector.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    /** 背题：翻到尚未浏览过的题时，刷新题库使用时间 */
    private void touchReciteIfUnseen() {
        if (mode != MODE_RECITE) return;
        if (cur < 0 || cur >= ids.length) return;
        if (!Db.isReciteSeen(bankId, ids[cur])) Db.touchBank(bankId);
    }

    private void next() {
        if (cur >= ids.length - 1) {
            if (mode == MODE_RECITE) {
                promptReciteFinished();
            } else {
                showSummary();
            }
            return;
        }
        cur++;
        touchReciteIfUnseen();
        render(SLIDE_NEXT);
    }

    /** 随机背题下从答题卡跳题：以目标题为首，其余题目重新打乱 */
    private void jumpReciteRandom(int pos) {
        long target = ids[pos];
        java.util.List<Long> rest = new java.util.ArrayList<>();
        for (long id : ids) if (id != target) rest.add(id);
        long[] arr = new long[rest.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = rest.get(i);
        reciteShuffleSeedless(arr);
        long[] nextIds = new long[arr.length + 1];
        nextIds[0] = target;
        System.arraycopy(arr, 0, nextIds, 1, arr.length);
        ids = nextIds;
        cur = 0;
        touchReciteIfUnseen();
        render(SLIDE_NEXT);
    }

    /** 背题：全部浏览完 */
    private void promptReciteFinished() {
        new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("提示")
                .setMessage("所有题已经浏览完\n是否按当前模式重新开始？")
                .setPositiveButton("是", (d, w) -> restartReciteRound())
                .setNegativeButton("否", (d, w) -> finish())
                .show();
    }

    /** 清空已看，按当前模式重开一轮（随机则重新打乱） */
    private void restartReciteRound() {
        Db.resetRecite(bankId);
        java.util.List<Long> all = Db.questionIds(bankId);
        long[] arr = new long[all.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = all.get(i);
        if (reciteRandom) reciteShuffleSeedless(arr);
        ids = arr;
        cur = 0;
        answeredSet.clear();
        restoredIds.clear();
        userAnswers.clear();
        correctMap.clear();
        qCache.clear();
        displayAnswerCache.clear();
        shuffledCache.clear();
        // 重开一轮即新的一轮：本次会话统计一并清零，避免第二轮小结累计第一轮
        sessionAnswered = 0;
        sessionCorrectCount = 0;
        sessionWrong.clear();
        render();
    }

    // ---------------- 自动背题悬浮球 ----------------

    private TextView autoBall;
    private android.view.View autoOverlay;
    private android.os.Handler autoHandler;
    private Runnable autoTick;
    private boolean autoPlaying;

    private boolean hitsBall(android.view.MotionEvent e) {
        return hitTest(autoBall, e);
    }

    private boolean hitTest(View v, android.view.MotionEvent e) {
        if (v == null || v.getVisibility() != View.VISIBLE) return false;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        float x = e.getRawX();
        float y = e.getRawY();
        return x >= loc[0] && x <= loc[0] + v.getWidth()
                && y >= loc[1] && y <= loc[1] + v.getHeight();
    }

    private void setupAutoBall() {
        if (mode != MODE_RECITE || !Prefs.autoReciteBall()) return;
        final android.widget.FrameLayout root = findViewById(android.R.id.content);
        autoBall = new TextView(this);
        autoBall.setText("自动");
        autoBall.setTextSize(12);
        autoBall.setGravity(android.view.Gravity.CENTER);
        autoBall.setTextColor(Ui.attrColor(this, R.attr.qzOnPrimary));
        autoBall.setAlpha(0.72f);
        int primary = Ui.attrColor(this, R.attr.qzPrimary);
        autoBall.setBackground(Ui.bg((primary & 0x00FFFFFF) | 0xCC000000, Ui.dp(this, 24)));
        int size = Ui.dp(this, 46);
        android.widget.FrameLayout.LayoutParams lp =
                new android.widget.FrameLayout.LayoutParams(size, size);
        lp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        lp.leftMargin = Prefs.ballX() >= 0 ? Prefs.ballX() : Ui.dp(this, 300);
        lp.topMargin = Prefs.ballY() >= 0 ? Prefs.ballY() : Ui.dp(this, 520);
        root.addView(autoBall, lp);

        autoBall.setOnTouchListener(new android.view.View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean moved;

            @Override
            public boolean onTouch(android.view.View v, android.view.MotionEvent e) {
                android.widget.FrameLayout.LayoutParams p =
                        (android.widget.FrameLayout.LayoutParams) v.getLayoutParams();
                switch (e.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = p.leftMargin;
                        startY = p.topMargin;
                        moved = false;
                        return true;
                    case android.view.MotionEvent.ACTION_MOVE:
                        int dx = (int) (e.getRawX() - downX);
                        int dy = (int) (e.getRawY() - downY);
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) moved = true;
                        p.leftMargin = Math.max(0, startX + dx);
                        p.topMargin = Math.max(0, startY + dy);
                        v.setLayoutParams(p);
                        return true;
                    case android.view.MotionEvent.ACTION_UP:
                        if (!moved) {
                            v.performClick();
                        } else {
                            snapNow();   // 松手立即贴边
                        }
                        return true;
                }
                return false;
            }
        });
        autoBall.setOnClickListener(v -> showAutoDialog());
    }

    /** 松手后立即贴到最近的左右边缘，并开始 5 秒倒计时 */
    private void snapNow() {
        if (autoBall == null) return;
        android.widget.FrameLayout.LayoutParams p =
                (android.widget.FrameLayout.LayoutParams) autoBall.getLayoutParams();
        int w = getResources().getDisplayMetrics().widthPixels;
        boolean left = (p.leftMargin + autoBall.getWidth() / 2) < w / 2;
        p.leftMargin = left ? 0 : Math.max(0, w - autoBall.getWidth());
        autoBall.setLayoutParams(p);
        Prefs.ballPos(p.leftMargin, p.topMargin);

    }

    /** 选择间隔并开始自动背题 */
    private void showAutoDialog() {
        final String[] labels = {"5 秒", "10 秒", "15 秒", "自定义（1-60 秒）"};
        new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("自动背题")
                .setItems(labels, (d, which) -> {
                    if (which < 3) {
                        Prefs.autoReciteGap(which == 0 ? 5 : (which == 1 ? 10 : 15));
                        startAuto();
                    } else {
                        com.ziruoling.quiz.ui.dialog.InputDialog.showNumber(this,
                                "间隔秒数", "输入 1 - 60", input -> {
                                    String dg = input == null ? "" : input.replaceAll("[^0-9]", "");
                                    if (dg.isEmpty()) {
                                        Ui.toast(this, "请输入数字");
                                        return;
                                    }
                                    Prefs.autoReciteGap(Integer.parseInt(dg));
                                    startAuto();
                                });
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void startAuto() {
        if (autoPlaying) return;
        autoPlaying = true;
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Ui.toast(this, "自动背题已开始，点屏幕任意位置停止");
        // 全屏透明遮罩：任意点击停止
        android.widget.FrameLayout root = findViewById(android.R.id.content);
        autoOverlay = new android.view.View(this);
        autoOverlay.setBackgroundColor(0x05000000);
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
        root.addView(autoOverlay, lp);
        autoOverlay.bringToFront();
        autoBall.bringToFront();
        autoOverlay.setOnClickListener(v -> stopAuto());

        autoHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        autoTick = () -> {
            if (!autoPlaying) return;
            if (cur >= ids.length - 1) {
                stopAuto();
                promptReciteFinished();
                return;
            }
            next();
            autoHandler.postDelayed(autoTick, Prefs.autoReciteGap() * 1000L);
        };
        autoHandler.postDelayed(autoTick, Prefs.autoReciteGap() * 1000L);
    }

    private void stopAuto() {
        autoPlaying = false;
        if (autoHandler != null) autoHandler.removeCallbacksAndMessages(null);
        if (autoOverlay != null) {
            ((android.view.ViewGroup) autoOverlay.getParent()).removeView(autoOverlay);
            autoOverlay = null;
        }
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (autoBall != null) autoBall.setAlpha(0.72f);
    }

    // ---------------- 渲染 ----------------

    private Question getQ(long id) {
        Question q = qCache.get(id);
        if (q == null) {
            q = Db.getQuestion(id);
            if (q != null) qCache.put(id, q);
        }
        return q;
    }

    private void render() {
        render(SLIDE_NONE);
    }

    /** 笔记区：无笔记显示占位，有笔记显示内容 */
    private void updateNoteView(Question q) {
        if (noteTv == null) return;
        String n = q.note == null ? "" : q.note.trim();
        noteTv.setText(n.isEmpty() ? "＋ 添加笔记" : n);
        noteTv.setTextColor(Ui.attrColor(this, n.isEmpty() ? R.attr.qzTextSub : R.attr.qzText));
    }

    /** 笔记区只在题目做完后出现 */
    private void updateNoteCardVisible(Question q) {
        if (noteCard == null) return;
        if (mode == MODE_RECITE) {
            noteCard.setVisibility(android.view.View.VISIBLE);
            return;
        }
        boolean answered = q != null && answeredSet.contains(q.id);
        noteCard.setVisibility(answered ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    /** 编辑本题笔记（单独的编辑框，取消/保存） */
    private void editNote() {
        final Question q = getQ(ids[cur]);
        final EditText et = new EditText(this);
        et.setText(q.note == null ? "" : q.note);
        et.setHint("注意：笔记跟题走，题无笔记无");
        et.setMinLines(5);
        et.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        int pad = Ui.dp(this, 16);
        et.setPadding(pad, Ui.dp(this, 8), pad, 0);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(pad, Ui.dp(this, 12), pad, 0);
        wrap.addView(et);
        new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("笔记")
                .setView(wrap)
                .setPositiveButton("保存", (d, w) -> {
                    Db.updateNote(q.id, et.getText().toString());
                    q.note = et.getText().toString();
                    updateNoteView(q);
        updateNoteCardVisible(q);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 题干 + 题号标签。标签用行内 ReplacementSpan 实现，
     * 只占首行开头的一小块，换行后正文自动回到最左边（不缩进整段）。
     */
    private CharSequence contentWithNo(Question q, int no) {
        CharSequence rich = TextUtil.rich(q.content == null ? "" : q.content);
        if (no <= 0) return rich;
        // 用一个零宽字符承载 span，避免被复制出多余数字
        SpannableStringBuilder sb = new SpannableStringBuilder("\u200B");
        sb.setSpan(new com.ziruoling.quiz.util.DocNoSpan(String.valueOf(no),
                        Ui.attrColor(this, R.attr.qzPrimary),
                        Ui.attrColor(this, R.attr.qzOnPrimary),
                        Ui.dp(this, 7), Ui.dp(this, 8),
                        Ui.dp(this, 19), Ui.dp(this, 12)),
                0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(rich);
        return sb;
    }

    private void render(int slide) {
        if (ids.length == 0) {
            finish();
            return;
        }
        Question q = getQ(ids[cur]);
        currentQid = q.id;
        if (mode == MODE_RECITE) {
            // 背题模式：独立进度，且记录「已看」，不影响顺序/随机
            Db.setReciteProgress(bankId, q.id);
            Db.markReciteSeen(bankId, q.id);
        } else if (saveProgress && mode == MODE_PRACTICE) {
            Db.setProgress(bankId, q.id);
        }
        counterTv.setText((cur + 1) + " / " + ids.length);

        headChips.removeAllViews();
        TextView typeChip = Ui.chip(this, QuestionType.SHORT_NAMES[q.type],
                R.attr.qzPrimary, R.attr.qzPrimaryBg);
        // 按住题型标签 2 秒 → 编辑整道题（题干 / 选项 / 答案 / 解析 / 图片）
        bindTypeChipHold(typeChip, q.id);
        headChips.addView(typeChip);
        if (q.category != null && !q.category.isEmpty()) {
            TextView cat = Ui.chip(this, q.category, R.attr.qzTextSub, R.attr.qzChipBg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = Ui.dp(this, 6);
            cat.setLayoutParams(lp);
            headChips.addView(cat);
        }

        // 题干前的题号（按篇/节重新计数）；作为行内 span，只占首行开头
        contentTv.setText(contentWithNo(q, Db.displayNo(q)));
        updateNoteView(q);
        renderImages(q);
        resultCard.setVisibility(View.GONE);
        analysisCard.setVisibility(View.GONE);
        fillBox.setVisibility(View.GONE);
        shortBox.setVisibility(View.GONE);
        btnMultiConfirm.setVisibility(View.GONE);
        fillEt.setText("");
        shortEt.setText("");
        // 换题 / 重绘即作废上一次「二次点击才提交」的窗口
        fillGuardQid = -1;
        fillGuardAt = 0L;
        boolean answered = answeredSet.contains(q.id);
        if (!answered && restoreAnswered) answered = restoreAnsweredState(q.id);
        // 笔记区只在「这道题确实已作答」时出现
        updateNoteCardVisible(q);
        boolean recite = mode == MODE_RECITE;
        if (q.type == QuestionType.FILL && !answered && !recite) {
            fillBox.setVisibility(View.VISIBLE);
            buildFillInputs(q);
        } else if ((q.type == QuestionType.SHORT || q.type == QuestionType.OTHER) && !answered && !recite) {
            shortBox.setVisibility(View.VISIBLE);
        } else if (q.type == QuestionType.SORT && !answered && !recite) {
            // 排序题：显示「重新排序」按钮，按点击顺序排号，排满自动判
            btnMultiConfirm.setVisibility(View.VISIBLE);
            btnMultiConfirm.setText("重新排序");
        }

        buildOptions(q);

        if (recite) {
            showAnswerPanel(q, null, null);
        } else if (answered) {
            showAnswerPanel(q, userAnswers.get((int) q.id),
                    Boolean.TRUE.equals(correctMap.get(q.id)));
        }
        updateFavText();
        updateFlagText();
        updateRemoveWrongButton(q);
        applySlideAnim(slide);
    }

    /** 切题动画：新内容从滑动方向滑入 + 淡入；同时滚动回顶部 */
    private void applySlideAnim(int slide) {
        ScrollView sv = findViewById(R.id.scroll);
        View body = sv == null ? null : (View) sv.getChildAt(0);
        if (body == null) return;
        sv.scrollTo(0, 0);
        if (slide == SLIDE_NONE) {
            body.setTranslationX(0f);
            body.setAlpha(1f);
            return;
        }
        View root = findViewById(android.R.id.content);
        float w = Math.max(240f, root == null ? 240f : root.getWidth() * 0.35f);
        body.setTranslationX(slide == SLIDE_NEXT ? w : -w);
        body.setAlpha(0.35f);
        body.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    /** 题干配图（看图题 / docx 表格贴图通用） */
    private void renderImages(Question q) {
        LinearLayout imgBox = findViewById(R.id.imgBox);
        imgBox.removeAllViews();
        if (q.imgPaths == null || q.imgPaths.isEmpty()) {
            imgBox.setVisibility(View.GONE);
            return;
        }
        int shown = 0;
        for (String rel : q.imgPaths) {
            File f = Db.imgFile(rel);
            if (f == null || !f.exists()) continue;
            Bitmap bm = Ui.decodeSampled(f, getResources().getDisplayMetrics().widthPixels);
            if (bm == null) continue;
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(bm);
            iv.setAdjustViewBounds(true);
            iv.setMaxHeight(Ui.dp(this, 360));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(this, 10);
            iv.setLayoutParams(lp);
            iv.setBackground(Ui.bgStroke(this, Ui.attrColor(this, R.attr.qzSurface),
                    Ui.attrColor(this, R.attr.qzStroke), Ui.dp(this, 12)));
            imgBox.addView(iv);
            shown++;
        }
        imgBox.setVisibility(shown > 0 ? View.VISIBLE : View.GONE);
    }

    private void buildOptions(Question q) {
        optionsBox.removeAllViews();
        boolean shuffle = Prefs.shuffleOpts() && !OptionShuffler.shouldSkip(q.type, q.options.size());
        List<String> opts = q.options;
        String dispAnswer = q.answer;
        if (shuffle) {
            OptionShuffler.Shuffled sh = shuffledCache.get(q.id);
            if (sh == null) {
                sh = OptionShuffler.shuffle(q.options, q.answer, sessionSeed + q.id);
                shuffledCache.put(q.id, sh);
            }
            opts = sh.options;
            dispAnswer = sh.answer;
        }
        displayAnswerCache.put(q.id, dispAnswer);
        currentOpts = new ArrayList<>(opts);
        for (int i = 0; i < opts.size(); i++) {
            char letter = (char) ('A' + i);
            OptionView ov = new OptionView(this, letter, opts.get(i));
            optionsBox.addView(ov);
        }
    }

    private OptionView optionAt(int index) {
        return (OptionView) optionsBox.getChildAt(index);
    }

    // ---------------- 作答 ----------------

    /** 单选/判断：点选即判 */
    private void answerSingle(char letter) {
        Question q = getQ(ids[cur]);
        judge(q, String.valueOf(letter), AnswerUtil.matchSingle(String.valueOf(letter),
                displayAnswerCache.get(q.id)));
    }

    /** 多选：勾选后点确认；排序题：该按钮作为「重新排序」 */
    private void submitMulti() {
        Question q = getQ(ids[cur]);
        if (q.type == QuestionType.SORT) {
            resetSort();
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < optionsBox.getChildCount(); i++) {
            OptionView ov = (OptionView) optionsBox.getChildAt(i);
            if (ov.selected) sb.append(ov.letter);
        }
        if (sb.length() == 0) {
            Ui.toast(this, "请先选择选项");
            return;
        }
        String u = sb.toString();
        judge(q, u, AnswerUtil.matchMulti(u, displayAnswerCache.get(q.id)));
    }

    /** 排序题：清空排号重新排 */
    private void resetSort() {
        for (int i = 0; i < optionsBox.getChildCount(); i++) {
            OptionView ov = optionAt(i);
            ov.sortNo = -1;
            ov.setState(OptionView.STATE_NORMAL);
        }
    }

    /** 排序题：按点击顺序排号，排满自动判 */
    private void onSortClick(OptionView clicked) {
        Question q = getQ(ids[cur]);
        if (mode == MODE_RECITE || answeredSet.contains(q.id)) return;
        if (clicked.sortNo > 0) return; // 已排过；需重排请点「重新排序」
        int next = 1;
        for (int i = 0; i < optionsBox.getChildCount(); i++) {
            OptionView ov = optionAt(i);
            if (ov.sortNo > 0) next = Math.max(next, ov.sortNo + 1);
        }
        clicked.sortNo = next;
        clicked.setState(OptionView.STATE_SELECTED);
        if (next >= optionsBox.getChildCount()) {
            char[] seq = new char[optionsBox.getChildCount()];
            for (int i = 0; i < optionsBox.getChildCount(); i++) {
                OptionView ov = optionAt(i);
                seq[Math.max(0, ov.sortNo) - 1] = ov.letter;
            }
            String u = new String(seq);
            judge(q, u, AnswerUtil.matchSort(u, displayAnswerCache.get(q.id)));
        }
    }

    /** 填空：输入后点提交 */
    /** 按题干空数生成多个输入框（第 1 空复用原输入框） */
    private void buildFillInputs(Question q) {
        for (int i = blankEts.size() - 1; i >= 1; i--) {
            View v = blankEts.remove(i);
            if (v.getParent() instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) v.getParent()).removeView(v);
            }
        }
        blankEts.clear();
        fillEt.setVisibility(View.VISIBLE);
        blankEts.add(fillEt);
        int n = AnswerUtil.blankCount(q.content);
        int base = ((android.view.ViewGroup) fillEt.getParent()).indexOfChild(fillEt);
        for (int i = 1; i < n; i++) {
            EditText e = new EditText(this);
            e.setHint("第 " + (i + 1) + " 空");
            e.setTextSize(15);
            e.setTextColor(Ui.attrColor(this, R.attr.qzText));
            e.setHintTextColor(Ui.attrColor(this, R.attr.qzTextSub));
            e.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
            e.setBackground(Ui.bg(Ui.attrColor(this, R.attr.qzChipBg), Ui.dp(this, 8)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(this, 8);
            ((android.view.ViewGroup) fillEt.getParent()).addView(e, base + i, lp);
            blankEts.add(e);
        }
    }

    private void submitFill() {
        Question q = getQ(ids[cur]);
        int blanks = AnswerUtil.blankCount(q.content);
        if (blanks > 1) {
            // 多空填空：还有空没填时，第一次点「提交答案」只提示、不判分；
            // 同一题 2 秒内再点一次才放行（按错判提交）。单空题保持原逻辑。
            boolean unfinished = false;
            for (int i = 0; i < blankEts.size(); i++) {
                if (blankEts.get(i).getText().toString().trim().isEmpty()) {
                    unfinished = true;
                    break;
                }
            }
            if (unfinished && !confirmFillSecondTap(q.id)) {
                fillGuardQid = q.id;
                fillGuardAt = SystemClock.elapsedRealtime();
                Ui.toast(this, "有未填写的内容，确认要提交吗？");
                return;
            }
            // 提交后清掉拦截窗口，避免以后同题误判成「二次点击」
            fillGuardQid = -1;
            fillGuardAt = 0L;
            if (blankEts.size() > 1) {
                String[] parts = new String[blankEts.size()];
                StringBuilder joined = new StringBuilder();
                for (int i = 0; i < blankEts.size(); i++) {
                    parts[i] = blankEts.get(i).getText().toString().trim();
                    if (i > 0) joined.append(" | ");
                    joined.append(parts[i]);
                }
                judge(q, joined.toString(), AnswerUtil.matchFillBlanks(parts, q.answer));
            } else {
                // 兜底：题干有多个空但没有生成多个输入框时，按「|」拆已有输入
                String input = fillEt.getText().toString().trim();
                String[] parts = input.split("\\|", -1);
                judge(q, input, AnswerUtil.matchFillBlanks(parts, q.answer));
            }
            return;
        }
        String input = fillEt.getText().toString().trim();
        if (input.isEmpty()) {
            Ui.toast(this, "请先输入答案");
            return;
        }
        judge(q, input, AnswerUtil.matchFill(input, q.answer));
    }

    /** 同一题在上次「有未填内容」拦截后的 2 秒内再次点提交 → true（允许按错判提交） */
    private boolean confirmFillSecondTap(long qid) {
        if (fillGuardAt <= 0L || fillGuardQid != qid) return false;
        return SystemClock.elapsedRealtime() - fillGuardAt <= 2000L;
    }

    /** 简答：查看参考答案 + 自评 */
    private void showShortAnswer() {
        Question q = getQ(ids[cur]);
        String input = shortEt.getText().toString().trim();
        new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("参考答案")
                .setMessage(q.answer == null || q.answer.isEmpty()
                        ? "（未提供参考答案）" : TextUtil.rich(q.answer))
                .setPositiveButton("我答对了", (d, w) -> judge(q, input, true))
                .setNegativeButton("我答错了", (d, w) -> judge(q, input, false))
                .setNeutralButton("返回", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SoundFx.release();
    }

    /** 专项训练结束小结：左「重做 重选」，右「返回」 */
    private void showSpecialSummary() {
        if (isFinishing()) return;
        int[] st = Db.specialStats(bankId);
        int total = st[0], done = st[1], correct = st[2];
        int rate = done <= 0 ? 0 : Math.round(correct * 100f / done);

        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padH = Ui.dp(this, 20);
        box.setPadding(padH, Ui.dp(this, 4), padH, Ui.dp(this, 6));

        android.widget.TextView msg = new android.widget.TextView(this);
        msg.setText("做题 " + done + " 题，正确率 " + rate + "%");
        msg.setTextSize(15);
        msg.setTextColor(Ui.attrColor(this, R.attr.qzPrimary));
        box.addView(msg);

        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.END);
        row.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 2));

        final android.app.AlertDialog dlg = new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("专项训练结束")
                .setView(box)
                .create();

        android.widget.TextView again = dialogButton("重做");
        again.setOnClickListener(v -> {
            dlg.dismiss();
            Db.specialReset(bankId);
            answeredSet.clear();
            correctMap.clear();
            userAnswers.clear();
            restoredIds.clear();
            // 重做即重开本轮：本次会话统计一并清零，否则第二轮小结会累计第一轮
            sessionAnswered = 0;
            sessionCorrectCount = 0;
            sessionWrong.clear();
            cur = 0;
            render();
        });
        android.widget.TextView rePick = dialogButton("重选");
        rePick.setOnClickListener(v -> {
            dlg.dismiss();
            reselectSpecialAndFinish();
        });
        android.widget.TextView back = dialogButton("返回");
        back.setOnClickListener(v -> {
            dlg.dismiss();
            Db.specialDelete(bankId);
            finish();
        });
        row.addView(again);
        final android.view.View gap = new android.view.View(this);
        final android.widget.LinearLayout.LayoutParams gapLp =
                new android.widget.LinearLayout.LayoutParams(0, 1);
        row.addView(gap, gapLp);
        row.addView(rePick);
        row.post(() -> {
            gapLp.width = rePick.getWidth();
            gap.setLayoutParams(gapLp);
        });
        android.view.View spacer = new android.view.View(this);
        row.addView(spacer, new android.widget.LinearLayout.LayoutParams(0, 1, 1f));
        row.addView(back);
        box.addView(row);

        dlg.setCancelable(false);
        dlg.show();
        dlg.setCanceledOnTouchOutside(false);
        Ui.styleContent(dlg, this, false);
    }

    private android.widget.TextView dialogButton(String text) {
        android.widget.TextView t = new android.widget.TextView(this);
        t.setText(text);
        t.setTextSize(14);
        t.setTextColor(Ui.attrColor(this, R.attr.qzPrimary));
        t.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        return t;
    }

    /**
     * 专项小结「重选」：删掉本次专项会话，回题库页后自动弹出「专项练习」选题弹窗。
     *
     * 本工程不允许改题库页（无法像重构版那样在 BankHome 里消费 SpecialPickerRequest），
     * 所以用一次性的 Activity 生命周期回调做中转：本页 finish 后第一个重新 resume 的
     * 页面（题库页）负责把现有的选题弹窗（TypeFilterDialog）弹出来。
     */
    private void reselectSpecialAndFinish() {
        Db.specialDelete(bankId);
        getApplication().registerActivityLifecycleCallbacks(
                new SpecialPickOnResume(getApplication(), bankId));
        finish();
    }

    /** 题库页：打开专项选题弹窗（与 BankHomeActivity.pickSpecial 的流程一致） */
    private static void showSpecialPicker(Activity a, long bid) {
        TypeFilterDialog.show(a, bid, (mask, category, random) -> {
            java.util.List<Long> ids = Db.questionIdsByFilter(bid, mask, category, random);
            if (ids.isEmpty()) {
                Ui.toast(a, "该组合下没有题目");
                return;
            }
            Db.saveSpecial(bid, ids);
            setPendingSpecial();
            startPractice(a, bid, ids, 0, MODE_PRACTICE, "专项练习", false);
        });
    }

    /** 「重选」后的一次性中转：题库页重新 resume 时弹选题弹窗，触发一次后自动注销 */
    private static final class SpecialPickOnResume
            implements Application.ActivityLifecycleCallbacks {
        private final Application app;
        private final long bid;

        SpecialPickOnResume(Application app, long bid) {
            this.app = app;
            this.bid = bid;
        }

        @Override
        public void onActivityCreated(Activity a, Bundle b) {
        }

        @Override
        public void onActivityStarted(Activity a) {
        }

        @Override
        public void onActivityResumed(Activity a) {
            if (a instanceof PracticeActivity) return; // 还没离开练习页，继续等
            app.unregisterActivityLifecycleCallbacks(this);
            a.getWindow().getDecorView().post(() -> showSpecialPicker(a, bid));
        }

        @Override
        public void onActivityPaused(Activity a) {
        }

        @Override
        public void onActivityStopped(Activity a) {
        }

        @Override
        public void onActivitySaveInstanceState(Activity a, Bundle b) {
        }

        @Override
        public void onActivityDestroyed(Activity a) {
        }
    }

    /** 判分落库 */
    private void judge(Question q, String userAnswer, boolean correct) {
        if (answeredSet.contains(q.id)) return;
        answeredSet.add(q.id);
        updateNoteCardVisible(q);
        restoredIds.remove(q.id);   // 本次真答过，不再算「历史」
        userAnswers.put((int) q.id, userAnswer == null ? "" : userAnswer);
        correctMap.put(q.id, correct);
        sessionAnswered++;
        if (correct) {
            sessionCorrectCount++;
        } else {
            sessionWrong.add(q.id);
        }
        if (Prefs.haptic()) {
            findViewById(R.id.content).performHapticFeedback(
                    correct ? HapticFeedbackConstants.VIRTUAL_KEY : HapticFeedbackConstants.LONG_PRESS);
        }
        SoundFx.play(correct);
        // 是否「答对就移出错题本」完全由设置决定。
        // 早期这里还额外 or 了 mode == MODE_WRONG，导致关掉开关后在错题重做里照样自动移出，
        // 也就永远看不到「移出错题本」按钮。
        boolean forceResolve = Prefs.wrongAutoRemove();
        Db.addRecord(q.bankId, q.id, 0, userAnswer, correct, forceResolve);
        if (special) {
            Db.specialMark(bankId, q.id, correct);
            int[] st = Db.specialStats(bankId);
            if (st[0] > 0 && st[1] >= st[0]) {
                handler.postDelayed(this::showSpecialSummary, 700);
            }
        }
        Prefs.snakeGateOnAnswer();
        showAnswerPanel(q, userAnswer, correct);
        updateRemoveWrongButton(q);
        if (correct && Prefs.autoNext()) {
            final int pos = cur;
            final long qid = q.id;
            handler.postDelayed(() -> {
                if (cur == pos && currentQid == qid && answeredSet.contains(qid) && cur < ids.length - 1) {
                    next();
                }
            }, 900);
        }
    }

    /** 展示判定 / 参考答案 */
    private void showAnswerPanel(Question q, String userAnswer, Boolean correct) {
        resultCard.setVisibility(View.VISIBLE);
        String dispAnswer = displayAnswerCache.get(q.id);
        if (correct != null) {
            boolean ok = correct;
            resultCard.setBackground(Ui.bg(Ui.attrColor(this, ok ? R.attr.qzGreenBg : R.attr.qzRedBg),
                    Ui.dp(this, 14)));
            verdict.setTextColor(Ui.attrColor(this, ok ? R.attr.qzGreen : R.attr.qzRed));
            verdict.setText(ok ? "回答正确 ✓" : "回答错误 ✗");
            StringBuilder sb = new StringBuilder("正确答案：" + answerDisplay(q, dispAnswer));
            if (userAnswer != null && !userAnswer.isEmpty()) {
                sb.insert(0, "你的答案：" + userAnswer + "\n");
            }
            answerTv.setText(TextUtil.rich(sb.toString()));
        } else {
            resultCard.setBackground(Ui.bg(Ui.attrColor(this, R.attr.qzPrimaryBg), Ui.dp(this, 14)));
            verdict.setTextColor(Ui.attrColor(this, R.attr.qzPrimary));
            verdict.setText("背题模式 · 答案");
            answerTv.setText(TextUtil.rich("正确答案：" + answerDisplay(q, dispAnswer)));
            // 背题模式：显隐状态全局记忆——选择「显示」后后续所有题都显示
            answerTv.setVisibility(View.VISIBLE);
            btnReveal.setVisibility(View.GONE);
            applyReciteHighlight(q, true);
        }
        answerTv.setTextColor(Ui.attrColor(this, R.attr.qzText));
        if (q.analysis != null && !q.analysis.isEmpty()) {
            analysisTv.setText(TextUtil.rich(q.analysis));
            boolean show = true;
            analysisCard.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (correct != null) {
            answerTv.setVisibility(View.VISIBLE);
            btnReveal.setVisibility(View.GONE);
            highlightOptions(q, userAnswer, correct);
        }
    }

    private String answerDisplay(Question q, String dispAnswer) {
        if (q.type == QuestionType.JUDGE) {
            return AnswerUtil.answerDisplay(QuestionType.JUDGE, dispAnswer);
        }
        if (q.type == QuestionType.FILL || q.type == QuestionType.SHORT || q.type == QuestionType.OTHER) {
            return q.answer == null ? "" : q.answer;
        }
        if (q.type == QuestionType.SORT) {
            String seq = AnswerUtil.extractSequence(dispAnswer);
            if (seq.isEmpty()) return dispAnswer == null ? "" : dispAnswer;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < seq.length(); i++) {
                if (i > 0) sb.append(" → ");
                sb.append(seq.charAt(i));
            }
            return sb.toString();
        }
        StringBuilder sb = new StringBuilder();
        // 选项文本必须按「当前展示顺序」取：开了随机选项时 currentOpts 已打乱，
        // 而 dispAnswer 也已重映射成展示字母，用 q.options 会张冠李戴。
        List<String> dispOpts = currentOpts;
        for (char c : AnswerUtil.extractLetters(dispAnswer).toCharArray()) {
            int idx = c - 'A';
            if (idx >= 0 && idx < dispOpts.size()) {
                if (sb.length() > 0) sb.append("  ");
                sb.append(c).append(". ").append(dispOpts.get(idx));
            }
        }
        return sb.toString();
    }

    /** 背题模式：展开答案时标出正确选项（之前完全不高亮，看起来像"答案对不上"） */
    private void applyReciteHighlight(Question q, boolean on) {
        for (int i = 0; i < optionsBox.getChildCount(); i++) {
            OptionView ov = optionAt(i);
            ov.sortNo = -1;
            ov.setState(OptionView.STATE_NORMAL);
        }
        if (!on) return;
        String ans = displayAnswerCache.get(q.id);
        if (q.type == QuestionType.SORT) {
            String seq = AnswerUtil.extractSequence(ans);
            for (int i = 0; i < optionsBox.getChildCount(); i++) {
                OptionView ov = optionAt(i);
                int pos = seq.indexOf(ov.letter);
                if (pos >= 0) {
                    ov.sortNo = pos + 1;
                    ov.setState(OptionView.STATE_CORRECT);
                }
            }
            return;
        }
        Set<Character> correct = new HashSet<>();
        for (char c : AnswerUtil.extractLetters(ans).toCharArray()) correct.add(c);
        for (int i = 0; i < optionsBox.getChildCount(); i++) {
            OptionView ov = optionAt(i);
            ov.setState(correct.contains(ov.letter) ? OptionView.STATE_CORRECT : OptionView.STATE_DIM);
        }
    }

    private void highlightOptions(Question q, String userAnswer, boolean correct) {
        if (q.type == QuestionType.SORT) {
            // 排序题：按“用户顺序中位置与正确顺序一致”逐项高亮
            String u = AnswerUtil.extractSequence(userAnswer == null ? "" : userAnswer);
            String a = AnswerUtil.extractSequence(displayAnswerCache.get(q.id));
            for (int i = 0; i < optionsBox.getChildCount(); i++) {
                OptionView ov = optionAt(i);
                int userPos = u.indexOf(ov.letter);
                int correctPos = a.indexOf(ov.letter);
                ov.setState(userPos == correctPos ? OptionView.STATE_CORRECT : OptionView.STATE_WRONG);
            }
            return;
        }
        Set<Character> correctLetters = new HashSet<>();
        for (char c : AnswerUtil.extractLetters(displayAnswerCache.get(q.id)).toCharArray()) {
            correctLetters.add(c);
        }
        Set<Character> userLetters = new HashSet<>();
        for (char c : AnswerUtil.extractLetters(userAnswer).toCharArray()) userLetters.add(c);
        for (int i = 0; i < optionsBox.getChildCount(); i++) {
            OptionView ov = optionAt(i);
            if (correctLetters.contains(ov.letter)) ov.setState(OptionView.STATE_CORRECT);
            else if (userLetters.contains(ov.letter)) ov.setState(OptionView.STATE_WRONG);
            else ov.setState(OptionView.STATE_DIM);
        }
    }

    /**
     * 题型标签「按住 2 秒」进入编辑器。
     * 系统长按约 0.5 秒，这里改成自定义计时：按下开始计时并轻微缩小，
     * 松手/取消即复位；满 2 秒触发（带震动反馈）。
     */
    private void bindTypeChipHold(final View chip, final long qid) {
        if (holdAction != null) holdHandler.removeCallbacks(holdAction);
        holdAction = () -> {
            holdHandler.removeCallbacks(holdAction);
            if (isFinishing()) return;
            chip.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            chip.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            EditQuestionActivity.start(this, qid, REQ_EDIT_QUESTION);
        };
        chip.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(150).start();
                    holdHandler.removeCallbacks(holdAction);
                    holdHandler.postDelayed(holdAction, TYPE_CHIP_HOLD_MS);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    holdHandler.removeCallbacks(holdAction);
                    v.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                    return true;
                default:
                    return false;
            }
        });
    }

    // ---------------- 分享 / 改答案 / 改解析 / 重做 ----------------

    /** 分享当前题目：先选「不带答案」还是「带答案与解析」 */
    private void shareCurrent() {
        Question q = getQ(ids[cur]);
        boolean answered = answeredSet.contains(q.id);
        String userAnswer = answered ? userAnswers.get((int) q.id) : null;
        Boolean correct = answered ? Boolean.TRUE.equals(correctMap.get(q.id)) : null;
        ShareUtil.showChooser(this, q, bankName(), cur + 1, ids.length,
                currentOpts, displayAnswerCache.get(q.id), userAnswer, correct);
    }

    /** 从数据库回填「已答」状态；返回该题是否已答过 */
    private boolean restoreAnsweredState(long qid) {
        Db.LastAnswer a = Db.lastAnswer(qid);
        if (a == null) return false;
        answeredSet.add(qid);
        restoredIds.add(qid);
        userAnswers.put((int) qid, a.userAnswer);
        correctMap.put(qid, a.correct);
        return true;
    }

    /** 一次性回填整组题目的「已答」状态（答题卡的计数要准确） */
    private void restoreAllAnswered() {
        List<Long> all = new ArrayList<>(ids.length);
        for (long id : ids) all.add(id);
        Map<Long, Db.LastAnswer> map = Db.lastAnswers(all);
        for (Map.Entry<Long, Db.LastAnswer> e : map.entrySet()) {
            long qid = e.getKey();
            answeredSet.add(qid);
            restoredIds.add(qid);
            userAnswers.put((int) qid, e.getValue().userAnswer);
            correctMap.put(qid, e.getValue().correct);
        }
    }

    /** 长按答案卡 / 解析卡弹出的操作菜单（按钮不常驻界面） */
    private void showQuestionMenu() {
        if (ids.length == 0) return;
        final Question q = getQ(ids[cur]);
        if (q == null) return;
        new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("题目操作")
                .setItems(new CharSequence[]{"修改正确答案", "编辑解析", "重做本题"},
                        (d, which) -> {
                            if (which == 0) editAnswer(q);
                            else if (which == 1) editAnalysis(q);
                            else redoQuestion(q);
                        })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 修改正确答案：输入 → 校验/规范 → 二次确认 → 立即入库并按新答案重判历史记录 */
    private void editAnswer(final Question q) {
        final String old = q.answer == null ? "" : q.answer;
        InputDialog.show(this, "修改正确答案", old, answerHint(q.type), input -> {
            final AnswerRule.Check ck = AnswerRule.check(q.type, input,
                    q.options == null ? 0 : q.options.size());
            if (!ck.ok()) {
                Ui.toast(this, ck.error);
                return;
            }
            if (ck.answer.equals(old) && ck.type == q.type) {
                Ui.toast(this, "答案没有变化");
                return;
            }
            StringBuilder msg = new StringBuilder();
            msg.append("原答案：").append(old.isEmpty() ? "（空）" : old).append('\n');
            msg.append("新答案：").append(ck.answer);
            if (ck.type != q.type) {
                msg.append("\n题型：").append(QuestionType.NAMES[q.type])
                        .append(" → ").append(QuestionType.NAMES[ck.type]);
            }
            msg.append("\n\n");
            if (ck.warning != null) msg.append("⚠ ").append(ck.warning).append("\n\n");
            msg.append("修改后，本题已有的作答记录会按新答案重新判定")
                    .append("（你当时选的答案保持不变），正确率与错题本会随之更新。确定修改？");
            ConfirmDialog.show(this, "确认修改答案", msg.toString(),
                    "确定修改", () -> applyAnswerEdit(q, ck.answer, ck.type));
        });
    }

    private String answerHint(int type) {
        switch (type) {
            case QuestionType.SINGLE:
                return "如 C";
            case QuestionType.MULTI:
                return "如 ABD";
            case QuestionType.JUDGE:
                return "对 或 错";
            case QuestionType.FILL:
                return "多个可选答案用 || 分隔";
            case QuestionType.SORT:
                return "正确顺序字母串，如 CBA";
            default:
                return "参考答案文本";
        }
    }

    private void applyAnswerEdit(Question q, String newAnswer, int newType) {
        Db.updateAnswer(q.id, newAnswer);
        if (newType != q.type) Db.updateType(q.id, newType);
        Db.rejudgeQuestion(q.id);
        qCache.remove(q.id);
        displayAnswerCache.remove(q.id);
        shuffledCache.remove(q.id);
        Question fresh = getQ(q.id);
        if (fresh == null) return;
        Db.LastAnswer la = Db.lastAnswer(fresh.id);
        if (la != null) {
            if (!answeredSet.contains(fresh.id)) restoredIds.add(fresh.id);
            answeredSet.add(fresh.id);
            userAnswers.put((int) fresh.id, la.userAnswer);
            correctMap.put(fresh.id, la.correct);
        }
        render();
        Ui.toast(this, "答案已更新");
    }

    /** 编辑解析：多行编辑 → 立即入库 */
    private void editAnalysis(final Question q) {
        final String old = q.analysis == null ? "" : q.analysis;
        TextEditDialog.show(this, "编辑解析", "留空表示没有解析", old, 4, input -> {
            final String v = input == null ? "" : input.trim();
            if (v.equals(old)) return;
            Db.updateAnalysis(q.id, v);
            qCache.remove(q.id);
            Question fresh = getQ(q.id);
            if (fresh == null) return;
            if (v.isEmpty()) {
                analysisCard.setVisibility(View.GONE);
            } else {
                analysisTv.setText(TextUtil.rich(v));
                analysisCard.setVisibility(View.VISIBLE);
            }
            Ui.toast(this, "解析已保存");
        });
    }

    /** 重做本题：清空该题作答记录与错题标记 */
    private void redoQuestion(final Question q) {
        ConfirmDialog.show(this, "重做本题",
                "将清除这道题的作答记录与错题标记，回到未作答状态"
                        + "（正确答案和解析不受影响）。确定？",
                "确定重做", () -> {
                    Db.resetQuestion(q.id);
                    answeredSet.remove(q.id);
                    restoredIds.remove(q.id);
                    userAnswers.remove((int) q.id);
                    correctMap.remove(q.id);
                    sessionWrong.remove(q.id);
                    render();
                    Ui.toast(this, "已回到未作答");
                });
    }

    private String bankName() {
        for (com.ziruoling.quiz.data.Bank b : Db.listBanks()) {
            if (b.id == bankId) return b.name;
        }
        return "";
    }

    // ---------------- 收藏 / 标记 / 答题卡 ----------------

    private void toggleFav() {
        Question q = getQ(ids[cur]);
        boolean now = Db.toggleFav(q.bankId, q.id);
        Ui.toast(this, now ? "已加入收藏" : "已取消收藏");
        updateFavText();
    }

    private void updateFavText() {
        Question q = getQ(ids[cur]);
        boolean fav = Db.isFav(q.id);
        btnFav.setText(fav ? "★ 已收藏" : "☆ 收藏");
        btnFav.setTextColor(Ui.attrColor(this, fav ? R.attr.qzOrange : R.attr.qzText));
    }

    /** 单击标记：切换默认词条（「其他」） */
    private void toggleFlag() {
        Question q = getQ(ids[cur]);
        com.ziruoling.quiz.data.Tag def = Db.defaultTag(q.bankId);
        if (def == null) return;
        boolean on = Db.toggleQuestionTag(q.id, def.id);
        Ui.toast(this, on ? "已标记为「" + def.name + "」" : "已取消「" + def.name + "」标记");
        updateFlagText();
    }

    /** 长按标记：多词条选择（勾选已有 / 新建 / 长按管理） */
    private void openTagEditor() {
        TagPickDialog.show(this, getQ(ids[cur]), this::updateFlagText);
    }

    private void updateFlagText() {
        Question q = getQ(ids[cur]);
        java.util.List<com.ziruoling.quiz.data.Tag> ts = Db.questionTags(q.id);
        int n = ts.size();
        String label;
        if (n == 0) label = "⚑ 标记";
        else if (n == 1 && ts.get(0).name.length() <= 6) label = "⚑ " + ts.get(0).name;
        else if (n == 1) label = "⚑1";
        else label = "⚑" + n;
        btnFlag.setText(label);
        // 已标记：橙底 + 强调色上的字；未标记：浅灰底 + 次要字色。
        // 未标记态若也沿用 qzOnPrimary（深色字），纯黑主题下会变成「近黑字 + 近黑底」看不见。
        btnFlag.setTextColor(Ui.attrColor(this, n > 0 ? R.attr.qzOnPrimary : R.attr.qzTextSub));
        btnFlag.setBackground(Ui.bg(n > 0
                ? Ui.attrColor(this, R.attr.qzOrange)
                : Ui.attrColor(this, R.attr.qzChipBg), Ui.dp(this, 14)));
    }

    /**
     * 错题重做模式下，本题「答对」且仍在错题本（未掌握）时，才显示「移出错题本」。
     * 答错说明还没掌握，不该给「移出」入口（与重构版 removeWrongVisible 一致）。
     * 注意：答完题不会整体重绘，所以判断后要单独更新一次。
     */
    private void updateRemoveWrongButton(Question q) {
        boolean show = q != null && answeredSet.contains(q.id)
                && Boolean.TRUE.equals(correctMap.get(q.id))
                && mode == MODE_WRONG && Db.wrongStatus(q.id) == 1;
        btnRemoveWrong.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /** 背题模式：显示 / 隐藏答案 */

    /**
     * 计算答题卡状态：0 未答 / 1 本次答对 / 2 本次答错 / 3 历史答对 / 4 历史答错。
     *
     * @param counts 出参（长度 &ge; 2）：counts[0] = 本次已答数，counts[1] = 历史已答数
     */
    int[] sheetStates(int[] counts) {
        int n = ids.length;
        int[] states = new int[n];
        int session = 0, hist = 0;
        List<Long> all = new ArrayList<>(n);
        for (long id : ids) all.add(id);
        Map<Long, Db.LastAnswer> history = Db.lastAnswers(all);
        for (int i = 0; i < n; i++) {
            long id = ids[i];
            if (answeredSet.contains(id)) {
                boolean restored = restoredIds.contains(id);
                boolean ok = Boolean.TRUE.equals(correctMap.get(id));
                states[i] = restored ? (ok ? 3 : 4) : (ok ? 1 : 2);
                if (restored) hist++;
                else session++;
            } else {
                Db.LastAnswer a = history.get(id);
                if (a != null) {
                    states[i] = a.correct ? 3 : 4;
                    hist++;
                }
            }
        }
        if (counts != null && counts.length >= 2) {
            counts[0] = session;
            counts[1] = hist;
        }
        return states;
    }

    private void showSheet() {
        int n = ids.length;
        if (mode == MODE_RECITE) {
            int[] seen = new int[n];
            int seenCount = 0;
            for (int i = 0; i < n; i++) {
                if (Db.isReciteSeen(bankId, ids[i])) {
                    seen[i] = 5;
                    seenCount++;
                }
            }
            AnswerSheetDialog.show(this, n, cur, seen,
                    "答题卡（已看 " + seenCount + "/" + n + "）",
                    pos -> {
                        if (reciteRandom) {
                            jumpReciteRandom(pos);
                        } else {
                            int from = cur;
                            cur = pos;
                            touchReciteIfUnseen();
                            render(from <= pos ? SLIDE_NEXT : SLIDE_PREV);
                        }
                    });
            return;
        }
        int[] counts = new int[2];
        int[] states = sheetStates(counts);
        String title = "答题卡（本次 " + counts[0] + "/" + n
                + (counts[1] > 0 ? " · 历史 " + counts[1] : "") + "）";
        AnswerSheetDialog.show(this, n, cur, states, title, pos -> {
                    int from = cur;
                    cur = pos;
                    render(from <= pos ? SLIDE_NEXT : SLIDE_PREV);
                });
    }

    /** 练习完成小结 */
    private void showSummary() {
        int rate = sessionAnswered <= 0 ? 0 : Math.round(sessionCorrectCount * 100f / sessionAnswered);
        AlertDialog d = new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("练习完成")
                .setMessage("本次作答 " + sessionAnswered + " 题\n答对 " + sessionCorrectCount + " 题\n正确率 " + rate + "%")
                .setPositiveButton("完成", (dd, w) -> finish())
                .create();
        if (!sessionWrong.isEmpty()) {
            d.setButton(AlertDialog.BUTTON_NEUTRAL, "重练错题", (dd, w) -> {
                // 新会话是独立的「错题重练」：清掉专项 / 随机背题的进程级标记，
                // 避免新会话被当成专项会话或随机背题会话（对应重构版 route.copy 里 special/reciteRandom 置 false）
                pendingSpecial = false;
                reciteRandomSession = false;
                List<Long> wrongIds = new ArrayList<>(sessionWrong);
                startPractice(this, bankId, wrongIds, 0, MODE_WRONG, "错题重练", false);
                finish();
            });
        }
        d.show();
    }

    /** 从题目编辑器返回：清掉缓存，按新内容重绘当前题 */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_EDIT_QUESTION && resultCode == RESULT_OK && ids.length > 0) {
            long qid = ids[cur];
            qCache.remove(qid);
            displayAnswerCache.remove(qid);
            shuffledCache.remove(qid);
            render();
        }
    }

    // ---------------- 选项视图 ----------------

    private class OptionView extends LinearLayout {
        static final int STATE_NORMAL = 0;
        static final int STATE_SELECTED = 1;
        static final int STATE_CORRECT = 2;
        static final int STATE_WRONG = 3;
        static final int STATE_DIM = 4;

        final char letter;
        final TextView letterTv;
        final TextView textTv;
        boolean selected;
        /** 排序题点击顺序号（-1 未排） */
        int sortNo = -1;

        OptionView(Context c, char letter, String text) {
            super(c);
            this.letter = letter;
            setOrientation(HORIZONTAL);
            // 顶部对齐：选项文字换行变高时，字母气泡仍贴住第一行，各选项看起来才齐
            setGravity(Gravity.TOP);
            // 上下标字形可能略微超出字体行盒，允许子 View 画出边界，避免被裁掉（不改变行高）
            setClipChildren(false);
            setClipToPadding(false);
            int pad = Ui.dp(c, 12);
            setPadding(pad, Ui.dp(c, 10), pad, Ui.dp(c, 10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(c, 8);
            setLayoutParams(lp);

            letterTv = new TextView(c);
            letterTv.setText(String.valueOf(letter));
            letterTv.setTextSize(14);
            letterTv.setTextColor(Ui.attrColor(c, R.attr.qzTextSub));
            letterTv.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lpL = new LinearLayout.LayoutParams(Ui.dp(c, 30), Ui.dp(c, 30));
            lpL.topMargin = Ui.dp(c, 1);
            letterTv.setLayoutParams(lpL);
            addView(letterTv);

            textTv = new TextView(c);
            textTv.setText(TextUtil.rich(text));
            textTv.setTextSize(15);
            textTv.setTextColor(Ui.attrColor(c, R.attr.qzText));
            textTv.setLineSpacing(Ui.dp(c, 2), 1f);
            LinearLayout.LayoutParams lpText = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lpText.leftMargin = Ui.dp(c, 8);
            textTv.setLayoutParams(lpText);
            addView(textTv);

            setBackground(Ui.press(Ui.bgStroke(c, Ui.attrColor(c, R.attr.qzSurface),
                    Ui.attrColor(c, R.attr.qzStroke), Ui.dp(c, 12))));
            setState(STATE_NORMAL);
            setOnClickListener(v -> onOptionClick());
        }

        private void onOptionClick() {
            Question q = getQ(ids[cur]);
            if (mode == MODE_RECITE || answeredSet.contains(q.id)) return;
            int type = q.type;
            if (type == QuestionType.SORT) {
                onSortClick(this);
                return;
            }
            if (type == QuestionType.MULTI) {
                selected = !selected;
                setState(selected ? STATE_SELECTED : STATE_NORMAL);
                // 「确认答案」只在有勾选时显示：全部取消勾选后要跟着隐藏
                boolean anySelected = false;
                for (int i = 0; i < optionsBox.getChildCount(); i++) {
                    if (((OptionView) optionsBox.getChildAt(i)).selected) {
                        anySelected = true;
                        break;
                    }
                }
                btnMultiConfirm.setVisibility(anySelected ? View.VISIBLE : View.GONE);
                return;
            }
            // 单选 / 判断
            for (int i = 0; i < optionsBox.getChildCount(); i++) {
                OptionView ov = (OptionView) optionsBox.getChildAt(i);
                ov.selected = (ov == this);
                ov.setState(ov.selected ? STATE_SELECTED : STATE_NORMAL);
            }
            answerSingle(letter);
        }

        void setState(int state) {
            int surface = Ui.attrColor(getContext(), R.attr.qzSurface);
            int stroke = Ui.attrColor(getContext(), R.attr.qzStroke);
            int letterBg = Ui.attrColor(getContext(), R.attr.qzChipBg);
            int letterFg = Ui.attrColor(getContext(), R.attr.qzTextSub);
            switch (state) {
                case STATE_SELECTED:
                    surface = Ui.attrColor(getContext(), R.attr.qzPrimaryBg);
                    stroke = Ui.attrColor(getContext(), R.attr.qzPrimary);
                    letterBg = Ui.attrColor(getContext(), R.attr.qzPrimary);
                    letterFg = Ui.attrColor(getContext(), R.attr.qzOnPrimary);
                    break;
                case STATE_CORRECT:
                    surface = Ui.attrColor(getContext(), R.attr.qzGreenBg);
                    stroke = Ui.attrColor(getContext(), R.attr.qzGreen);
                    letterBg = Ui.attrColor(getContext(), R.attr.qzGreen);
                    letterFg = Ui.attrColor(getContext(), R.attr.qzOnPrimary);
                    break;
                case STATE_WRONG:
                    surface = Ui.attrColor(getContext(), R.attr.qzRedBg);
                    stroke = Ui.attrColor(getContext(), R.attr.qzRed);
                    letterBg = Ui.attrColor(getContext(), R.attr.qzRed);
                    letterFg = Ui.attrColor(getContext(), R.attr.qzOnPrimary);
                    break;
                case STATE_DIM:
                    stroke = Ui.attrColor(getContext(), R.attr.qzStroke);
                    break;
                default:
                    break;
            }
            setBackground(Ui.press(Ui.bgStroke(getContext(), surface, stroke, Ui.dp(getContext(), 12))));
            letterTv.setBackground(Ui.bg(letterBg, Ui.dp(getContext(), 17)));
            letterTv.setTextColor(letterFg);
            // 排序题：已排项显示点击顺序号，未排显示字母
            letterTv.setText(sortNo > 0 ? String.valueOf(sortNo) : String.valueOf(letter));
        }
    }
}