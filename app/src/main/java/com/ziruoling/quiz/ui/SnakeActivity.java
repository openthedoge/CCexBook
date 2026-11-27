package com.ziruoling.quiz.ui;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.ziruoling.quiz.R;
import com.ziruoling.quiz.data.Prefs;
import com.ziruoling.quiz.util.Ui;

import java.util.LinkedList;

/** 彩蛋：贪吃蛇（雏形）。 */
public class SnakeActivity extends BaseActivity {

    /** 正式版进入前需要新做的题数（0 = 测试期关闭） */
    public static int GATE_QUESTIONS = 0;

    private static final int N = 9;
    private static final int WIN_FOOD = 80;   // 初始长度 1，吃满 80 个正好填满 81 格
    private static final int A_MAX = 11;

    private SnakeView view;
    private FrameLayout gridBox;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable tick, clock;

    private final LinkedList<int[]> snake = new LinkedList<>();
    private int dirX = 1, dirY = 0, nextX = 1, nextY = 0;
    private int foodX, foodY, eaten;
    private double score;
    private int aCount, foodAccel;
    private long intervalMs = 1000;
    private boolean alive, won, paused;
    private long playStartMs, playedMs;
    private long lastStepAt;
    private int moveCount;
    private final java.util.ArrayDeque<Integer> growDue = new java.util.ArrayDeque<>();
    private boolean stepping;
    private long lastBPress;

    private TextView scoreTv, lenTv, curTimeTv, multTv, bestTv, maxLenTv, playsTv, totalTv;
    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("snake_stats", MODE_PRIVATE);
        buildUi();
        startRound();
    }

    // ---------------- 界面 ----------------
    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.attrColor(this, R.attr.qzSurface));

        TextView title = new TextView(this);
        title.setText("放弃思考，专注敏捷");
        title.setTextSize(16);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(Ui.attrColor(this, R.attr.qzText));
        title.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 6));
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        gridBox = new FrameLayout(this);
        view = new SnakeView();
        gridBox.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(gridBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 3f));

        LinearLayout data = new LinearLayout(this);
        data.setOrientation(LinearLayout.VERTICAL);
        data.setPadding(Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 8));
        LinearLayout row1 = new LinearLayout(this);
        LinearLayout row2 = new LinearLayout(this);
        scoreTv = dataCell(row1, "得分");
        curTimeTv = dataCell(row1, "本局时长");
        lenTv = dataCell(row1, "长度");
        multTv = dataCell(row1, "倍率");
        bestTv = dataCell(row2, "最高分");
        totalTv = dataCell(row2, "总时长");
        maxLenTv = dataCell(row2, "最长");
        playsTv = dataCell(row2, "游玩次数");
        data.addView(row1);
        LinearLayout.LayoutParams row2Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row2Lp.topMargin = Ui.dp(this, 8);
        data.addView(row2, row2Lp);
        root.addView(data, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout controls = new FrameLayout(this);
        root.addView(controls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 2f));

        int disc = Ui.dp(this, 138);
        DPadView pad = new DPadView();
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(disc, disc);
        plp.leftMargin = Ui.dp(this, 30);
        plp.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        controls.addView(pad, plp);

        TextView bBtn = abKey("B", this::pressB);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                Ui.dp(this, 62), Ui.dp(this, 62));
        blp.rightMargin = Ui.dp(this, 104);
        blp.topMargin = Ui.dp(this, 20);
        blp.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
        controls.addView(bBtn, blp);

        TextView aBtn = abKey("A", this::accelerate);
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(
                Ui.dp(this, 62), Ui.dp(this, 62));
        alp.rightMargin = Ui.dp(this, 20);
        alp.bottomMargin = Ui.dp(this, 46);
        alp.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
        controls.addView(aBtn, alp);

        setContentView(root);
        updateStats();
    }

    private TextView dataCell(LinearLayout row, String label) {
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        box.setBackground(Ui.bg(Ui.attrColor(this, R.attr.qzChipBg), Ui.dp(this, 4)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
        row.addView(box, lp);

        TextView t = new TextView(this);
        t.setText("0");
        t.setTextSize(16);
        t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(Ui.attrColor(this, R.attr.qzText));
        t.setPadding(Ui.dp(this, 2), Ui.dp(this, 8), Ui.dp(this, 2), Ui.dp(this, 16));
        box.addView(t, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView lab = new TextView(this);
        lab.setText(label);
        lab.setTextSize(9);
        lab.setTextColor(Ui.attrColor(this, R.attr.qzTextSub));
        android.widget.FrameLayout.LayoutParams llp = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.gravity = Gravity.BOTTOM | Gravity.END;
        llp.rightMargin = Ui.dp(this, 4);
        llp.bottomMargin = Ui.dp(this, 2);
        box.addView(lab, llp);
        return t;
    }

    /** A/B：按下即触发，带按压反馈 */
    private TextView abKey(String label, final Runnable action) {
        final TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(18);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(Ui.attrColor(this, R.attr.qzPrimary));
        t.setBackground(Ui.bgStroke(this, Ui.attrColor(this, R.attr.qzChipBg),
                Ui.attrColor(this, R.attr.qzPrimary), Ui.dp(this, 31)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 62), Ui.dp(this, 62));
        lp.setMargins(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        t.setLayoutParams(lp);
        final int primary = Ui.attrColor(this, R.attr.qzPrimary);
        final int onPrimary = Ui.attrColor(this, R.attr.qzOnPrimary);
        final int chip = Ui.attrColor(this, R.attr.qzChipBg);
        t.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                // 与方向盘一致：按下整块填充主题色
                v.setBackground(Ui.bgStroke(this, primary, primary, Ui.dp(this, 31)));
                ((TextView) v).setTextColor(onPrimary);
                action.run();
            } else if (e.getAction() == MotionEvent.ACTION_UP
                    || e.getAction() == MotionEvent.ACTION_CANCEL) {
                v.setBackground(Ui.bgStroke(this, chip, primary, Ui.dp(this, 31)));
                ((TextView) v).setTextColor(primary);
            }
            return true;
        });
        return t;
    }

    // ---------------- 游戏 ----------------
    private void startRound() {
        snake.clear();
        snake.add(new int[]{4, 4});
        dirX = 1; dirY = 0; nextX = 1; nextY = 0;
        eaten = 0; score = 0; aCount = 0; foodAccel = 0; moveCount = 0; growDue.clear(); intervalMs = 1000;
        alive = true; won = false; paused = false;
        playStartMs = System.currentTimeMillis();
        lastStepAt = playStartMs;
        playedMs = 0;
        placeFood();
        updateStats();
        scheduleTick();
        if (clock == null) {
            clock = new Runnable() {
                @Override
                public void run() {
                    updateTime();
                    handler.postDelayed(this, 500);
                }
            };
        }
        handler.post(clock);
    }

    private void scheduleTick() {
        if (tick == null) {
            tick = new Runnable() {
                @Override
                public void run() {
                    lastStepAt = System.currentTimeMillis();
                    stepping = true;
                    if (alive && !paused) step();
                    stepping = false;
                    if (alive && !paused) handler.postDelayed(this, intervalMs);
                }
            };
        }
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, intervalMs);
    }

    private void step() {
        if (!(nextX == 0 && nextY == 0) && !(nextX == -dirX && nextY == -dirY)) {
            dirX = nextX;
            dirY = nextY;
        }
        int[] head = snake.getFirst();
        int nx = head[0] + dirX, ny = head[1] + dirY;
        if (nx < 0 || ny < 0 || nx >= N || ny >= N) {
            die();
            return;
        }
        // 尾巴本步会让出格子时，允许头部进入尾巴当前所在的格子
        int[] tailCell = snake.getLast();
        boolean tailMoves = growDue.isEmpty() || growDue.peekFirst() > moveCount + 1;
        for (int[] s : snake) {
            if (s[0] == nx && s[1] == ny) {
                boolean isTail = s == tailCell;
                if (!(isTail && tailMoves)) {
                    die();
                    return;
                }
            }
        }
        snake.addFirst(new int[]{nx, ny});
        moveCount++;
        boolean growNow = !growDue.isEmpty() && growDue.peekFirst() <= moveCount;
        if (growNow) growDue.pollFirst();

        if (nx == foodX && ny == foodY) {
            eaten++;
            score += eaten * multiplier();
            // 吃下后延迟 1 个移动周期再长一格（本次吃的那格不算）
            growDue.addLast(moveCount + 1);
            if (isFoodAccel(eaten)) {
                foodAccel++;
                recomputeInterval();
                popGaia();
            }
            if (eaten >= WIN_FOOD) {
                win();
                return;
            }
            placeFood();
        }
        if (!growNow) snake.removeLast();
        updateStats();
        view.invalidate();
    }

    private final java.util.List<int[]> freeCells = new java.util.ArrayList<>();

    private void placeFood() {
        freeCells.clear();
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                boolean used = false;
                for (int[] s : snake) {
                    if (s[0] == x && s[1] == y) {
                        used = true;
                        break;
                    }
                }
                if (!used) freeCells.add(new int[]{x, y});
            }
        }
        if (freeCells.isEmpty()) return;
        int[] cell = freeCells.get((int) (Math.random() * freeCells.size()));
        foodX = cell[0];
        foodY = cell[1];
    }

    /** 吃果子触发加速的编号：1、9、18、27…（不含 81） */
    static boolean isFoodAccel(int eaten) {
        if (eaten == 1) return true;
        return eaten > 1 && eaten % 9 == 0;
    }

    /** 倍率：初始 1.0，每次加速 +0.1 */
    private double multiplier() {
        return 1.0 + 0.1 * (aCount + foodAccel);
    }

    private void recomputeInterval() {
        intervalMs = Math.round(1000 * Math.pow(0.95, aCount + foodAccel));
        // 按「上次移动到现在」的剩余时间重排，加速立即生效且不产生停顿
        if (!stepping && alive && !paused && tick != null) {
            long elapsed = System.currentTimeMillis() - lastStepAt;
            long remain = Math.max(0, intervalMs - elapsed);
            handler.removeCallbacks(tick);
            handler.postDelayed(tick, remain);
        }
    }

    private void turn(int dx, int dy) {
        if (!alive || won) return;
        // 只与「当前实际方向」比较：反向忽略；同向忽略；
        // 相对待生效方向的相反输入允许覆盖（最后一次有效输入优先）
        if (dx == -dirX && dy == -dirY) return;
        if (dx == nextX && dy == nextY) return;
        nextX = dx;
        nextY = dy;
    }

    private void accelerate() {
        if (!alive || won) return;
        if (aCount >= A_MAX) {
            Ui.pillToast(this, "太快了！受不了！");
            return;
        }
        aCount++;
        recomputeInterval();
        popGaia();
    }

    private void pressB() {
        long now = System.currentTimeMillis();
        if (now - lastBPress <= 2000) {
            // 退出前先取消还没消失的提示，Dialog 不会留在已结束的页面上
            Ui.dismissToasts(this);
            finish();
            return;
        }
        lastBPress = now;
        Ui.toast(this, "你确定要继续玩吗");
    }

    /** 只把这两个弹窗上移到屏幕 1/10 高度附近，不影响其它弹窗 */
    private void positionTop(android.app.AlertDialog dlg) {
        if (dlg == null || dlg.getWindow() == null) return;
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        android.view.WindowManager.LayoutParams lp = dlg.getWindow().getAttributes();
        lp.gravity = Gravity.TOP;
        lp.y = dm.heightPixels * 4 / 10;
        dlg.getWindow().setAttributes(lp);
    }

    private void die() {
        alive = false;
        gaias.clear();
        if (view != null) view.invalidate();
        handler.removeCallbacks(tick);
        handler.removeCallbacks(clock);
        handler.removeCallbacks(gaiaTick);
        gaiaTickScheduled = false;
        recordRound();
        final android.app.AlertDialog dlg = new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("学够了，该去玩了。")
                .setPositiveButton("好吧", (d, w) -> finish())
                .setNegativeButton("我还要学", (d, w) -> {
                    android.widget.TextView msg = new android.widget.TextView(this);
                    msg.setText("去玩 5 分钟吧");
                    msg.setTextSize(18);
                    msg.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    msg.setTextColor(Ui.attrColor(this, R.attr.qzPrimary));
                    msg.setGravity(android.view.Gravity.START);
                    int padH = Ui.dp(this, 24);
                    msg.setPadding(padH, Ui.dp(this, 18), padH, Ui.dp(this, 8));
                    android.app.AlertDialog d2 = new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                            .setView(msg)
                            .setPositiveButton("好吧", (dd, ww) -> finish())
                            .create();
                    d2.show();
                    Ui.styleContent(d2, this, false);
                    positionTop(d2);
                })
                .create();
        dlg.setCancelable(false);
        dlg.show();
        Ui.styleContent(dlg, this, false);
        positionTop(dlg);
    }

    private void win() {
        alive = false;
        Prefs.snakeUnlocked(true);
        gaias.clear();
        if (view != null) view.invalidate();
        won = true;
        handler.removeCallbacks(tick);
        handler.removeCallbacks(clock);
        handler.removeCallbacks(gaiaTick);
        gaiaTickScheduled = false;
        recordRound();
        final android.app.AlertDialog dlg = new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("学完了")
                .setPositiveButton("确定", null)
                .create();
        dlg.setCancelable(false);
        dlg.show();
        Ui.styleContent(dlg, this, false);
    }

    private void recordRound() {
        playedMs += System.currentTimeMillis() - playStartMs;
        int plays = sp.getInt("plays", 0);
        if (score > 0) plays++;
        sp.edit()
                .putInt("plays", plays)
                .putFloat("bestF", (float) Math.max(sp.getFloat("bestF", 0f), score))
                .putInt("maxlen", Math.max(sp.getInt("maxlen", 1), snake.size()))
                .putLong("totalMs", sp.getLong("totalMs", 0) + playedMs)
                .apply();
        updateStats();
    }

    private void updateStats() {
        if (scoreTv == null) return;
        scoreTv.setText(fmtScore(score));
        lenTv.setText(String.valueOf(snake.size()));
        multTv.setText(String.format("×%.1f", multiplier()));
        bestTv.setText(fmtScore(sp.getFloat("bestF", 0f)));
        maxLenTv.setText(String.valueOf(sp.getInt("maxlen", 1)));
        playsTv.setText(String.valueOf(sp.getInt("plays", 0)));
        totalTv.setText(fmt(sp.getLong("totalMs", 0)));
    }

    private String fmtScore(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.001) return String.valueOf((long) Math.rint(v));
        return String.format("%.1f", v);
    }

    private String fmt(long ms) {
        long sec = ms / 1000;
        return String.format("%02d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60);
    }

    private void updateTime() {
        if (curTimeTv == null) return;
        long ms = playedMs;
        if (alive) ms += System.currentTimeMillis() - playStartMs;
        curTimeTv.setText(fmt(ms));
        totalTv.setText(fmt(sp.getLong("totalMs", 0)));
    }

    /** 盖亚气泡：记录 (x, y, 起始时间)，由网格画布绘制 */
    private final java.util.List<float[]> gaias = new java.util.ArrayList<>();
    private boolean gaiaTickScheduled;

    private void popGaia() {
        float[] hp = view.headCenter();
        gaias.add(new float[]{hp[0], hp[1], 0f});
        while (gaias.size() > 24) gaias.remove(0);
        view.invalidate();
        if (!gaiaTickScheduled) {
            gaiaTickScheduled = true;
            handler.post(gaiaTick);
        }
    }

    private final Runnable gaiaTick = new Runnable() {
        @Override
        public void run() {
            for (int i = gaias.size() - 1; i >= 0; i--) {
                float[] g = gaias.get(i);
                g[2] += 16f;
                if (g[2] >= 500f) gaias.remove(i);
            }
            view.invalidate();
            if (!gaias.isEmpty() && !paused && !isFinishing()) {
                handler.postDelayed(this, 16);
            } else {
                gaiaTickScheduled = false;
            }
        }
    };

    @Override
    public void onBackPressed() {
        // 游戏内返回键无效：只能死亡、胜利或双击 B 退出
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (alive) {
            paused = true;
            playedMs += System.currentTimeMillis() - playStartMs;
            handler.removeCallbacks(tick);
            handler.removeCallbacks(clock);
            handler.removeCallbacks(gaiaTick);
            gaiaTickScheduled = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (alive && paused) {
            paused = false;
            playStartMs = System.currentTimeMillis();
            scheduleTick();
            if (clock != null) handler.post(clock);
            if (!gaias.isEmpty() && !gaiaTickScheduled) {
                gaiaTickScheduled = true;
                handler.post(gaiaTick);
            }
        }
    }

    @Override
    protected void onDestroy() {
        Ui.dismissToasts(this);
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    // ---------------- 颜色 ----------------
    private static int brighten(int color, float ratio) {
        int r = (int) (((color >> 16) & 0xFF) + (255 - ((color >> 16) & 0xFF)) * ratio);
        int g = (int) (((color >> 8) & 0xFF) + (255 - ((color >> 8) & 0xFF)) * ratio);
        int b = (int) ((color & 0xFF) + (255 - (color & 0xFF)) * ratio);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int darken(int color, float ratio) {
        int r = (int) (((color >> 16) & 0xFF) * (1 - ratio));
        int g = (int) (((color >> 8) & 0xFF) * (1 - ratio));
        int b = (int) ((color & 0xFF) * (1 - ratio));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // ---------------- 圆盘 ----------------
    private class DPadView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tx = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF boxRect = new RectF();
        private final android.graphics.Path[] paths = {
                new android.graphics.Path(), new android.graphics.Path(),
                new android.graphics.Path(), new android.graphics.Path()};
        private int pressedDir = -1;   // 0 上 1 下 2 左 3 右

        DPadView() {
            super(SnakeActivity.this);
        }

        @Override
        protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float r = Math.min(getWidth(), getHeight()) / 2f - Ui.dp(SnakeActivity.this, 3);
            int primary = Ui.attrColor(SnakeActivity.this, R.attr.qzPrimary);
            int chip = Ui.attrColor(SnakeActivity.this, R.attr.qzChipBg);
            boxRect.set(cx - r, cy - r, cx + r, cy + r);

            // 四瓣扇区（X 分割）
            p.setStyle(Paint.Style.FILL);
            // 索引与 pressedDir 对齐：0 上 / 1 下 / 2 左 / 3 右
            final float[] starts = {-135f, 45f, 135f, -45f};
            for (int d = 0; d < 4; d++) {
                float start = starts[d];
                android.graphics.Path path = paths[d];
                path.reset();
                path.moveTo(cx, cy);
                path.arcTo(boxRect, start, 90f, false);
                path.close();
                if (pressedDir == d) {
                    p.setColor(primary);
                    p.setAlpha(80);
                } else {
                    p.setColor(chip);
                    p.setAlpha(255);
                }
                c.drawPath(path, p);
            }
            p.setAlpha(255);

            // 外圈
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Ui.dp(SnakeActivity.this, 2));
            p.setColor(primary);
            c.drawCircle(cx, cy, r, p);

            // 中间整圆不分割
            p.setStyle(Paint.Style.FILL);
            p.setColor(chip);
            c.drawCircle(cx, cy, r * 0.32f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setColor(primary);
            c.drawCircle(cx, cy, r * 0.32f, p);

            // 方向箭头
            tx.setStyle(Paint.Style.FILL);
            tx.setTextAlign(Paint.Align.CENTER);
            tx.setTextSize(Ui.dp(SnakeActivity.this, 17));
            tx.setColor(primary);
            float off = r * 0.70f, adj = Ui.dp(SnakeActivity.this, 6);
            c.drawText("▲", cx, cy - off + adj, tx);
            c.drawText("▼", cx, cy + off + adj, tx);
            c.drawText("◀", cx - off, cy + adj - Ui.dp(SnakeActivity.this, 1), tx);
            c.drawText("▶", cx + off, cy + adj - Ui.dp(SnakeActivity.this, 1), tx);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float dx = e.getX() - getWidth() / 2f;
            float dy = e.getY() - getHeight() / 2f;
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                float r = Math.min(getWidth(), getHeight()) / 2f;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist < r * 0.33f) return true;   // 中心圆：不响应
                // 以 X 型分瓣：上/右/下/左各 90°
                double deg = Math.toDegrees(Math.atan2(dy, dx));
                if (deg >= -135 && deg < -45) {
                    pressedDir = 0;
                    turn(0, -1);
                } else if (deg >= -45 && deg < 45) {
                    pressedDir = 3;
                    turn(1, 0);
                } else if (deg >= 45 && deg < 135) {
                    pressedDir = 1;
                    turn(0, 1);
                } else {
                    pressedDir = 2;
                    turn(-1, 0);
                }
                invalidate();
            } else if (e.getAction() == MotionEvent.ACTION_UP
                    || e.getAction() == MotionEvent.ACTION_CANCEL) {
                pressedDir = -1;
                invalidate();
            }
            return true;
        }
    }

    // ---------------- 网格 ----------------
    private class SnakeView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF tmp = new RectF();

        SnakeView() {
            super(SnakeActivity.this);
        }

        float[] headCenter() {
            int w = getWidth(), h = getHeight();
            int size = Math.min(w, h);
            float left = (w - size) / 2f, top = (h - size) / 2f;
            float cell = size / (float) N;
            int[] s = snake.getFirst();
            return new float[]{left + (s[0] + 0.5f) * cell, top + (s[1] + 0.5f) * cell};
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            int size = Math.min(w, h);
            float left = (w - size) / 2f, top = (h - size) / 2f;
            float cell = size / (float) N;
            int bg = Ui.attrColor(SnakeActivity.this, R.attr.qzSurface);
            int line = Ui.attrColor(SnakeActivity.this, R.attr.qzDivider);
            int snakeColor = Ui.attrColor(SnakeActivity.this, R.attr.qzPrimary);
            boolean dark = ((bg >> 16 & 0xFF) + (bg >> 8 & 0xFF) + (bg & 0xFF)) < 384;
            int edge = dark ? brighten(snakeColor, 0.5f) : darken(snakeColor, 0.3f);

            p.setStyle(Paint.Style.FILL);
            p.setColor(bg);
            c.drawRect(left, top, left + size, top + size, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Ui.dp(SnakeActivity.this, 1));
            p.setColor(line);
            for (int i = 0; i <= N; i++) {
                c.drawLine(left + i * cell, top, left + i * cell, top + size, p);
                c.drawLine(left, top + i * cell, left + size, top + i * cell, p);
            }

            // 蛇：铺满格子（无缝隙），亮/暗一档主题色描边区分相邻格
            p.setStyle(Paint.Style.FILL);
            p.setColor(snakeColor);
            for (int[] s : snake) {
                c.drawRect(left + s[0] * cell, top + s[1] * cell,
                        left + (s[0] + 1) * cell, top + (s[1] + 1) * cell, p);
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Ui.dp(SnakeActivity.this, 1.5f));
            p.setColor(edge);
            for (int[] s : snake) {
                c.drawRect(left + s[0] * cell + 1, top + s[1] * cell + 1,
                        left + (s[0] + 1) * cell - 1, top + (s[1] + 1) * cell - 1, p);
            }

            // 蛇头眼睛：朝向当前实际移动方向
            if (!snake.isEmpty()) {
                int[] head = snake.getFirst();
                float hx = left + (head[0] + 0.5f) * cell;
                float hy = top + (head[1] + 0.5f) * cell;
                float fx = dirX, fy = dirY;   // 实际移动方向
                float px = -fy, py = fx;      // 垂直方向
                float eyeR = cell * 0.16f;
                float fwd = cell * 0.14f, side = cell * 0.20f;
                for (int k = -1; k <= 1; k += 2) {
                    float ex = hx + fx * fwd + px * side * k;
                    float ey = hy + fy * fwd + py * side * k;
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0xFFFFFFFF);
                    c.drawCircle(ex, ey, eyeR, p);
                    p.setColor(0xFF222222);
                    c.drawCircle(ex + fx * eyeR * 0.35f, ey + fy * eyeR * 0.35f,
                            eyeR * 0.5f, p);
                }
            }

            // 食物：亮一档主题色实心方块 + 白描边
            p.setStyle(Paint.Style.FILL);
            p.setColor(brighten(snakeColor, 0.55f));
            tmp.set(left + foodX * cell + cell * 0.12f, top + foodY * cell + cell * 0.12f,
                    left + (foodX + 1) * cell - cell * 0.12f, top + (foodY + 1) * cell - cell * 0.12f);
            c.drawRect(tmp, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Ui.dp(SnakeActivity.this, 1.5f));
            p.setColor(0xFFFFFFFF);
            c.drawRect(tmp, p);

            // 棋盘外框：加粗主题色，与内部细网格线区分
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Ui.dp(SnakeActivity.this, 2.5f));
            p.setColor(snakeColor);
            c.drawRect(left - 1, top - 1, left + size + 1, top + size + 1, p);

            // 盖亚气泡
            for (int gi = gaias.size() - 1; gi >= 0; gi--) {
                float[] g = gaias.get(gi);
                float age = g[2] / 500f;
                if (age > 1f) continue;
                float alpha = age < 0.15f ? age / 0.15f
                        : (age < 0.45f ? 1f : 1f - (age - 0.45f) / 0.55f);
                float gx = g[0], gy = g[1] - age * Ui.dp(SnakeActivity.this, 40);
                p.setStyle(Paint.Style.FILL);
                p.setTextSize(Ui.dp(SnakeActivity.this, 15));
                p.setTextAlign(Paint.Align.CENTER);
                p.setColor(Ui.attrColor(SnakeActivity.this, R.attr.qzText));
                p.setAlpha((int) (255 * alpha));
                c.drawText("盖亚！", gx, gy, p);
                p.setAlpha(255);
            }
        }
    }
}
