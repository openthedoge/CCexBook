package com.ziruoling.quiz.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ziruoling.quiz.R;
import com.ziruoling.quiz.core.QuestionType;
import com.ziruoling.quiz.core.XlsxWriter;
import com.ziruoling.quiz.data.Bank;
import com.ziruoling.quiz.data.Db;
import com.ziruoling.quiz.data.Folder;
import com.ziruoling.quiz.data.Prefs;

import java.util.ArrayList;
import com.ziruoling.quiz.data.DemoBank;
import com.ziruoling.quiz.ui.dialog.ConfirmDialog;
import com.ziruoling.quiz.ui.dialog.InputDialog;
import com.ziruoling.quiz.util.Ui;

import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/** 首页：题库管理 + 全局入口 */
public class MainActivity extends BaseActivity {

    public static final String EXTRA_TAB = "tab";

    private static final int REQ_EXPORT = 101;
    private static final int REQ_EXPORT_DOCX = 102;

    private long exportBankId = 0;

    private ListView list;
    private View emptyView;
    private TextView navBanks, navStats, navSettings;
    private TextView statToday, statTodayRate, statStreak, statCheckin;
    private TextView btnGenerate, btnImport;
    private TextView quickFav;
    private BankAdapter adapter;
    private SettingsPage settingsPage;
    private int lastTab = 0;
    private Map<Long, int[]> typeCounts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Db.deleteDrafts();   // 清理上次进程被杀残留的草稿题
        setContentView(R.layout.activity_main);

        list = findViewById(R.id.list);
        emptyView = findViewById(R.id.emptyView);
        statToday = findViewById(R.id.statToday);
        statTodayRate = findViewById(R.id.statTodayRate);
        statStreak = findViewById(R.id.statStreak);
        statCheckin = findViewById(R.id.statCheckin);
        btnGenerate = findViewById(R.id.btnGenerate);
        btnImport = findViewById(R.id.btnImport);
        quickFav = findViewById(R.id.quickFav);

        btnImport.setOnClickListener(v -> startActivity(new Intent(this, ImportEntryActivity.class)));
        btnGenerate.setOnClickListener(v -> generateDemo());
        styleContinueLast();
        findViewById(R.id.btnContinueLast).setOnClickListener(v -> continueLast());
        findViewById(R.id.btnBankMenu).setOnClickListener(v -> showBankBarMenu());
        findViewById(R.id.sectionTitle).setOnClickListener(v -> showBankBarMenu());
        findViewById(R.id.titleBox).setOnClickListener(v -> showBankBarMenu());
        // 顶栏图标：点击进更新日志
        findViewById(R.id.titleLogo).setOnClickListener(v ->
                ChangelogActivity.start(this));
        findViewById(R.id.titleLogo).setOnLongClickListener(v -> {
            showEasterEgg();
            return true;
        });

        quickFav.setOnClickListener(v -> QuestionListActivity.start(this, 0, QuestionListActivity.SCOPE_FAV,
                QuestionListActivity.FILTER_ALL, "收藏夹"));

        // 底栏切换：题库 / 统计（设置暂未内嵌）
        navBanks = findViewById(R.id.navBanks);
        navStats = findViewById(R.id.navStats);
        navSettings = findViewById(R.id.navSettings);
        navBanks.setOnClickListener(v -> showTab(0));
        navStats.setOnClickListener(v -> showTab(1));
        navSettings.setOnClickListener(v -> showTab(2));
        int startTab = getIntent().getIntExtra(EXTRA_TAB, 0);
        if (startTab != 0) showTab(startTab);
    }

    /** 0=题库 1=统计 */
    private void showTab(int tab) {
        findViewById(R.id.pageBanks).setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        findViewById(R.id.pageStats).setVisibility(tab == 1 ? View.VISIBLE : View.GONE);
        findViewById(R.id.pageSettings).setVisibility(tab == 2 ? View.VISIBLE : View.GONE);
        getIntent().putExtra(EXTRA_TAB, tab);
        int accent = Ui.attrColor(this, R.attr.qzPrimary);
        int sub = Ui.attrColor(this, R.attr.qzTextSub);
        navBanks.setTextColor(tab == 0 ? accent : sub);
        navStats.setTextColor(tab == 1 ? accent : sub);
        navSettings.setTextColor(tab == 2 ? accent : sub);
        if (lastTab == 2 && tab != 2 && settingsPage != null) settingsPage.resetToMenu();
        lastTab = tab;
        if (tab == 1) fillStatsTab();
        if (tab == 2) ensureSettings();
    }

    /** 首次进入设置 tab 时把设置布局内嵌进来 */
    private void ensureSettings() {
        if (settingsPage != null) {
            settingsPage.onResumePage();
            return;
        }
        android.widget.FrameLayout box = findViewById(R.id.pageSettings);
        View page = android.view.LayoutInflater.from(this)
                .inflate(R.layout.activity_settings, box, false);
        // 隐藏设置页自带的顶部返回栏（底栏负责导航）
        View back = page.findViewById(R.id.btnBack);
        if (back != null && back.getParent() instanceof View) {
            ((View) back.getParent()).setVisibility(View.GONE);
        }
        box.addView(page);
        settingsPage = new SettingsPage(this, page);
    }

    private long lastBackAt;

    @Override
    public void onBackPressed() {
        // 设置内的二级页先退回设置菜单
        if (settingsPage != null && settingsPage.onBack()) return;
        // 三个底栏平级：第一下提示，2 秒内第二下退出
        long now = System.currentTimeMillis();
        if (now - lastBackAt <= 2000) {
            super.onBackPressed();
            return;
        }
        lastBackAt = now;
        Ui.toast(this, "再按一次返回键退出");
    }

    private void fillStatsTab() {
        android.widget.LinearLayout box = findViewById(R.id.calendarBox);
        if (box != null) box.removeAllViews();   // 每次切回统计都重新加载日历数据
        if (box != null) {
            com.ziruoling.quiz.ui.view.MonthCalendarView cal =
                    new com.ziruoling.quiz.ui.view.MonthCalendarView(this);
            cal.setOnDayClick((y, m, d) -> {
                int[] v = Db.dayDetail(y, m, d);
                String rate = v[0] <= 0 ? "-" : Math.round(v[1] * 100f / v[0]) + "%";
                String day = String.format("%04d%02d%02d", y, m, d);
                Ui.pillToast(this, day + "\n" + v[0] + " 题   " + rate);
            });
            box.addView(cal);
        }
        long[] g = Db.globalStats();
        int today = Db.todayCount();
        int todayRate = Db.todayRate();
        setStat(R.id.s1, String.valueOf(today));
        setStat(R.id.s2, todayRate < 0 ? "-" : todayRate + "%");
        setStat(R.id.s3, String.valueOf(g[0]));
        setStat(R.id.s4, g[0] <= 0 ? "-" : Math.round(g[1] * 100f / g[0]) + "%");
        setStat(R.id.s5, String.valueOf(Db.weekCount()));
        setStat(R.id.s6, String.valueOf(Db.monthCount()));
        setStat(R.id.s7, Db.streak() + " 天");
        setStat(R.id.s8, Db.checkinDays() + " 天");
        fillTypeStats();
        fillBankProgress();
    }

    private void fillTypeStats() {
        android.widget.LinearLayout box = findViewById(R.id.typeBox2);
        box.removeAllViews();
        int[][] ts = Db.typeStats();
        for (int t = 0; t < com.ziruoling.quiz.core.QuestionType.COUNT; t++) {
            if (ts[t][1] <= 0) continue;
            box.addView(statRow(com.ziruoling.quiz.core.QuestionType.NAMES[t], ts[t][1], ts[t][0],
                    Math.round(ts[t][0] * 100f / ts[t][1])));
        }
        if (box.getChildCount() == 0) {
            box.addView(Ui.text(this, "暂无练习记录", 13, R.attr.qzTextSub, 0));
        }
    }

    private void fillBankProgress() {
        android.widget.LinearLayout box = findViewById(R.id.bankBox2);
        box.removeAllViews();
        java.util.List<com.ziruoling.quiz.data.Bank> banks = Db.listBanks();
        if (banks.isEmpty()) {
            box.addView(Ui.text(this, "暂无题库", 13, R.attr.qzTextSub, 0));
            return;
        }
        for (com.ziruoling.quiz.data.Bank b : banks) {
            int done = Math.min(b.questionCount, b.doneCount);
            int pct = b.questionCount <= 0 ? 0 : Math.round(done * 100f / b.questionCount);
            box.addView(statRow(b.name, b.questionCount, done, pct));
        }
    }

    private View statRow(String label, int total, int done, int pct) {
        View v = android.view.LayoutInflater.from(this).inflate(R.layout.item_stat_row, null);
        ((android.widget.TextView) v.findViewById(R.id.label)).setText(label);
        android.widget.ProgressBar pb = v.findViewById(R.id.progress);
        pb.setProgress(Math.max(0, Math.min(100, pct)));
        ((android.widget.TextView) v.findViewById(R.id.value))
                .setText(done + " / " + total + "（" + pct + "%）");
        return v;
    }

    private void setStat(int id, String text) {
        TextView tv = findViewById(id);
        if (tv != null) tv.setText(text);
    }

    /** 生成示例：三选（生成 / 取消 / 直接导出模板） */
    private void generateDemo() {
        final android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padH = Ui.dp(this, 20);
        box.setPadding(padH, Ui.dp(this, 4), padH, Ui.dp(this, 6));

        android.widget.TextView msg = new android.widget.TextView(this);
        msg.setText("将生成示例题库进行体验，自行从题库中导出 Word 或 Excel 格式使用。");
        msg.setTextSize(14);
        msg.setTextColor(Ui.attrColor(this, R.attr.qzPrimary));
        box.addView(msg);

        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.END);
        row.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 2));

        final android.app.AlertDialog dlg = new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("生成示例题库")
                .setView(box)
                .create();

        android.widget.TextView export = sideButton("导出模板");
        export.setOnClickListener(v -> { dlg.dismiss(); exportTemplate(); });
        android.widget.TextView share = sideButton("分享模板");
        share.setOnClickListener(v -> { dlg.dismiss(); shareTemplate(); });
        android.widget.TextView cancel = sideButton("取消");
        cancel.setOnClickListener(v -> dlg.dismiss());
        android.widget.TextView ok = sideButton("确定");
        ok.setOnClickListener(v -> { dlg.dismiss(); createDemoBank(); });

        row.addView(export);
        row.addView(share);

        android.view.View spacer = new android.view.View(this);
        row.addView(spacer, new android.widget.LinearLayout.LayoutParams(
                0, 1, 1f));

        row.addView(cancel);
        row.addView(ok);
        box.addView(row);

        dlg.show();
        Ui.styleContent(dlg, this, false);
    }

    /** 弹窗内的次级文字按钮（主题色） */
    private android.widget.TextView sideButton(String text) {
        android.widget.TextView t = new android.widget.TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTextColor(Ui.attrColor(this, R.attr.qzPrimary));
        t.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        return t;
    }

    private void createDemoBank() {
        long bankId = Db.createBank("自行导出按格式替换", "内置示例");
        // createBank 内部是 insert()，失败返回 -1：此时不能继续导入，也不能提示成功
        if (bankId <= 0) {
            Ui.toast(this, "生成示例失败，请重试");
            return;
        }
        Db.importQuestions(bankId, com.ziruoling.quiz.data.TemplateDemoBank.build());
        Ui.toast(this, "已生成示例题库");
        refresh();
    }

    /** 分享内置模板：先落一份到下载目录，再调系统分享（微信/QQ/AI 应用等） */
    private void shareTemplate() {
        final String name = "刷题导入模板.docx";
        try {
            if (android.os.Build.VERSION.SDK_INT < 29) {
                exportTemplate();
                Ui.toast(this, "已导出到下载目录，请从文件管理器分享");
                return;
            }
            android.content.ContentValues v = new android.content.ContentValues();
            v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_DOWNLOADS);
            v.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1);
            android.net.Uri uri = getContentResolver().insert(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) {
                Ui.toast(this, "分享失败：无法写入文件");
                return;
            }
            java.io.InputStream in = getAssets().open(name);
            java.io.OutputStream out = getContentResolver().openOutputStream(uri);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            out.close();
            v.clear();
            v.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0);
            getContentResolver().update(uri, v, null, null);

            android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND);
            send.setType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            send.putExtra(android.content.Intent.EXTRA_STREAM, uri);
            send.putExtra(android.content.Intent.EXTRA_SUBJECT, "刷题导入模板");
            send.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(android.content.Intent.createChooser(send, "分享模板到"));
        } catch (Exception e) {
            Ui.toast(this, "分享失败：" + e.getMessage());
        }
    }

    /** 把内置的导入模板导出到手机（Android 10+ 走 MediaStore，直接落到 Download） */
    private void exportTemplate() {
        final String name = "刷题导入模板.docx";
        try (java.io.InputStream in = getAssets().open(name)) {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name);
                cv.put(android.provider.MediaStore.MediaColumns.MIME_TYPE,
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
                cv.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_DOWNLOADS);
                android.net.Uri uri = getContentResolver()
                        .insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) throw new java.io.IOException("无法创建下载文件");
                try (java.io.OutputStream os = getContentResolver().openOutputStream(uri)) {
                    copyStream(in, os);
                }
                Ui.toast(this, "已导出到 手机/Download/" + name);
            } else {
                java.io.File dir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
                if (dir == null) dir = getFilesDir();
                java.io.File f = new java.io.File(dir, name);
                try (java.io.OutputStream os = new java.io.FileOutputStream(f)) {
                    copyStream(in, os);
                }
                Ui.toast(this, "已导出到 " + f.getAbsolutePath());
            }
        } catch (Exception e) {
            Ui.toast(this, "导出失败：" + e.getMessage());
        }
    }

    private static void copyStream(java.io.InputStream in, java.io.OutputStream os)
            throws java.io.IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null) {
            int tab = intent.getIntExtra(EXTRA_TAB, 0);
            if (tab != 0) showTab(tab);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    /** 专注模式：隐藏统计 tab 与首页统计，用「专注！」横幅替换 */
    public void applyFocusMode() {
        boolean on = com.ziruoling.quiz.data.Prefs.focusMode();
        navStats.setVisibility(on ? View.GONE : View.VISIBLE);
        View row = findViewById(R.id.homeStatsRow);
        if (row != null) row.setVisibility(on ? View.GONE : View.VISIBLE);
    }

    private void refresh() {
        statToday.setText(String.valueOf(Db.todayCount()));
        int rate = Db.todayRate();
        statTodayRate.setText(rate < 0 ? "-" : rate + "%");
        statStreak.setText(Db.streak() + " 天");
        statCheckin.setText(Db.checkinDays() + " 天");
        int favCount = Db.favCount();
        quickFav.setText(favCount > 0 ? "收藏夹(" + favCount + ")" : "收藏夹");
        typeCounts = Db.typeCountsMap();
        if (collapsedFolders.isEmpty()) collapsedFolders.addAll(Prefs.collapsedFolders());
        applyFocusMode();
        adapter = new BankAdapter(buildRows());
        list.setAdapter(adapter);
        emptyView.setVisibility(adapter.getCount() == 0 ? View.VISIBLE : View.GONE);
        list.setVisibility(adapter.getCount() == 0 ? View.GONE : View.VISIBLE);
    }

    /** 题库行操作菜单的命令 ID（不再用标题字符串做分发，改文案不会点不动） */

    /** 题库行操作：重命名 / 追加题目 / 重置题库 / 删除题库 */
    private void showBankMenu(final Bank bank, View anchor) {
        final String[] items = {"重命名", "追加题目", "重置题库…", "移动到文件夹…",
                "上移", "下移", "移至顶部", (bank.pinned > 0 ? "取消置顶" : "置顶"), "删除题库"};
        new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle(bank.name)
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            InputDialog.show(this, "重命名题库", bank.name, "题库名称", name -> {
                                if (!name.isEmpty()) {
                                    Db.renameBank(bank.id, name);
                                    refresh();
                                }
                            });
                            break;
                        case 1:
                            AppendQuestionsActivity.start(this, bank.id, bank.name);
                            break;
                        case 2:
                            showResetBankDialog(bank);
                            break;
                        case 3:
                            moveToFolder(bank);
                            break;
                        case 4:
                            moveBank(bank, -1);
                            break;
                        case 5:
                            moveBank(bank, 1);
                            break;
                        case 6:
                            moveBank(bank, -2);
                            break;
                        case 7:
                            // 置顶改为切换：已置顶再点就取消
                            Db.setBankPinned(bank.id, bank.pinned <= 0);
                            refresh();
                            break;
                        case 8:
                            int[] extra = Db.bankExtraCounts(bank.id);
                            ConfirmDialog.showDanger(this, "删除题库",
                                    "将删除题库「" + bank.name + "」共 " + bank.questionCount
                                            + " 题，其中收藏 " + extra[0] + " 题、标记 " + extra[1]
                                            + " 题、笔记 " + extra[2]
                                            + " 条，将一并删除。\n\n此操作不可恢复，确定删除？",
                                    () -> new Thread(() -> {
                                        try {
                                            Db.deleteBank(bank.id);
                                            runOnUiThread(() -> {
                                                if (isFinishing()) return;
                                                Ui.toast(this, "已删除");
                                                refresh();
                                            });
                                        } catch (Throwable t) {
                                            runOnUiThread(() -> {
                                                if (isFinishing()) return;
                                                Ui.toast(this, "删除失败：" + t.getMessage());
                                            });
                                        }
                                    }, "del-bank").start());
                            break;
                        default:
                            break;
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ==================== 文件夹 / 排序 ====================

    /** 长按「题库」标题：新建文件夹 / 文件夹排序 / 无文件夹题库排序 */
    /** 彩蛋入口：玩累了，来做做题吧！ */
    private void showEasterEgg() {
        int gate = com.ziruoling.quiz.data.Prefs.snakeGate();
        if (!com.ziruoling.quiz.data.Prefs.snakeUnlocked() && gate < 5) {
            int remain = 5 - gate;
            new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                    .setTitle("去玩 " + remain + " 分钟吧")
                    .setPositiveButton("好吧", null)
                    .show();
            return;
        }
        new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("玩累了，来做做题吧！")
                .setPositiveButton("确定", (d, w) -> {
                    if (!com.ziruoling.quiz.data.Prefs.snakeUnlocked()) {
                        com.ziruoling.quiz.data.Prefs.snakeGate(0);   // 未解锁：消耗，再进需再做 5 题
                    }
                    startActivity(new android.content.Intent(this, SnakeActivity.class));
                })
                .setNegativeButton("不，我要继续玩", null)
                .show();
    }

    /** 继续上次刷题按钮：主题色描边 + 主题色文字 */
    private void styleContinueLast() {
        android.widget.TextView btn = findViewById(R.id.btnContinueLast);
        if (btn == null) return;
        btn.setTextColor(Ui.attrColor(this, R.attr.qzPrimary));
        btn.setBackground(Ui.bgStroke(this, Ui.attrColor(this, R.attr.qzSurface),
                Ui.attrColor(this, R.attr.qzPrimary), Ui.dp(this, 12)));
    }

    /** 继续上次刷题：取所有题库中最近使用的那个 */
    private void continueLast() {
        Bank last = null;
        for (Bank b : Db.listBanks()) {
            if (last == null || b.lastAt > last.lastAt) last = b;
        }
        if (last == null) {
            Ui.toast(this, "还没有题库");
            return;
        }
        java.util.List<Long> seqIds = Db.questionIds(last.id);
        if (seqIds.isEmpty()) {
            Ui.toast(this, "「" + last.name + "」还没有题目");
            return;
        }
        long lastQid = Db.getProgress(last.id);
        int idx = lastQid > 0 ? seqIds.indexOf(lastQid) : -1;
        PracticeActivity.startPractice(this, last.id, seqIds, Math.max(0, idx),
                PracticeActivity.MODE_PRACTICE, "继续刷题", true, true);
    }

    private void showBankBarMenu() {
        final String[] items = {"新建文件夹", "文件夹排序", "无文件夹题库排序"};
        new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("题库管理")
                .setItems(items, (d, which) -> {
                    if (which == 0) newFolder();
                    else if (which == 1) showSortDialog(null);
                    else showSortDialog(null, true);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void newFolder() {
        com.ziruoling.quiz.ui.dialog.InputDialog.show(this, "新建文件夹", "练习册",
                "文件夹名称", name -> {
                    String base = name == null ? "" : name.trim();
                    if (base.isEmpty()) return;
                    String unique = base;
                    int i = 2;
                    java.util.Set<String> existing = new java.util.HashSet<>();
                    for (Folder f : Db.listFolders()) existing.add(f.name);
                    while (existing.contains(unique)) {
                        unique = base + " " + i;
                        i++;
                    }
                    Db.createFolder(unique);
                    refresh();
                    Ui.toast(this, "已新建「" + unique + "」");
                });
    }

    /** 文件夹长按菜单 */
    private void showFolderMenu(final Folder f) {
        final String[] items = {"重命名", "删除文件夹", "上移", "下移", "移至顶部",
                (f.pinned > 0 ? "取消置顶" : "置顶"), "排序"};
        new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle(f.name)
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            com.ziruoling.quiz.ui.dialog.InputDialog.show(this, "重命名文件夹",
                                    f.name, "文件夹名称", name -> {
                                        if (name != null && !name.trim().isEmpty()) {
                                            Db.renameFolder(f.id, name.trim());
                                            refresh();
                                        }
                                    });
                            break;
                        case 1:
                            com.ziruoling.quiz.ui.dialog.ConfirmDialog.showDanger(this, "删除文件夹",
                                    "文件夹内的题库会回到「无文件夹」位置。确定删除「" + f.name + "」？",
                                    () -> {
                                        Db.deleteFolder(f.id);
                                        refresh();
                                    });
                            break;
                        case 2:
                            moveFolder(f, -1);
                            break;
                        case 3:
                            moveFolder(f, 1);
                            break;
                        case 4:
                            moveFolder(f, -2);
                            break;
                        case 5:
                            // 置顶改为切换：已置顶再点就取消
                            Db.setFolderPinned(f.id, f.pinned <= 0);
                            refresh();
                            break;
                        case 6:
                            showSortDialog(f);
                            break;
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void moveFolder(Folder f, int mode) {
        java.util.List<Folder> list = new java.util.ArrayList<>(Db.listFolders());
        java.util.Collections.sort(list, (a, b) -> {
            if (a.pinned != b.pinned) return b.pinned - a.pinned;
            int r = compareBy(Prefs.folderSortField(), Prefs.folderSortDir(), a, b);
            return r != 0 ? r : Long.compare(a.id, b.id);
        });
        int idx = -1;
        for (int i = 0; i < list.size(); i++) if (list.get(i).id == f.id) idx = i;
        if (idx < 0) return;
        Folder cur = list.remove(idx);
        int target;
        if (mode == -2) target = 0;
        else target = Math.max(0, Math.min(list.size(), idx + mode));
        list.add(target, cur);
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (Folder x : list) ids.add(x.id);
        Db.saveFolderOrder(ids);
        Prefs.folderSortField(3);
        Prefs.folderSortDir(0);
        refresh();
    }

    private void moveBank(Bank b, int mode) {
        java.util.List<Bank> list = new java.util.ArrayList<>();
        int field, dir;
        if (b.folderId == 0) {
            for (Bank x : Db.listBanks()) if (x.folderId == 0) list.add(x);
            field = Prefs.bankSortField();
            dir = Prefs.bankSortDir();
        } else {
            list.addAll(Db.banksInFolder(b.folderId));
            Folder f = Db.getFolder(b.folderId);
            field = f == null ? 1 : f.sortField;
            dir = f == null ? 1 : f.sortDir;
        }
        final int fld = field, dr = dir;
        java.util.Collections.sort(list, (x, y) -> {
            if (x.pinned != y.pinned) return y.pinned - x.pinned;
            int r = compareBy(fld, dr, x, y);
            return r != 0 ? r : Long.compare(x.id, y.id);
        });
        int idx = -1;
        for (int i = 0; i < list.size(); i++) if (list.get(i).id == b.id) idx = i;
        if (idx < 0) return;
        Bank cur = list.remove(idx);
        int target;
        if (mode == -2) target = 0;
        else target = Math.max(0, Math.min(list.size(), idx + mode));
        list.add(target, cur);
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (Bank x : list) ids.add(x.id);
        Db.saveBankOrder(ids);
        if (b.folderId == 0) {
            Prefs.bankSortField(3);
            Prefs.bankSortDir(0);
        } else {
            Db.setFolderSort(b.folderId, 3, 0);
        }
        refresh();
    }

    private java.util.List<Bank> looseBanks() {
        java.util.List<Bank> out = new java.util.ArrayList<>();
        for (Bank b : Db.listBanks()) if (b.folderId == 0) out.add(b);
        return out;
    }

    /** 移动到文件夹：列出文件夹 + 移出文件夹 */
    private void moveToFolder(final Bank b) {
        final java.util.List<Folder> folders = Db.listFolders();
        final java.util.List<String> labels = new java.util.ArrayList<>();
        for (Folder f : folders) labels.add(f.name);
        labels.add("移出文件夹");
        new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("移动到文件夹")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    if (which == folders.size()) Db.setBankFolder(b.id, 0);
                    else Db.setBankFolder(b.id, folders.get(which).id);
                    refresh();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 排序弹窗：3 个字段胶囊 + 2 个方向胶囊 */
    private void showSortDialog(final Folder scope) {
        showSortDialog(scope, false);
    }

    private void showSortDialog(final Folder scope, boolean looseOnly) {
        final boolean isFolder = scope == null && !looseOnly;
        final android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 6), Ui.dp(this, 20), 0);

        final android.widget.TextView label = new android.widget.TextView(this);
        label.setText("排序方式");
        label.setTextSize(13);
        label.setTextColor(Ui.attrColor(this, R.attr.qzTextSub));
        box.addView(label);

        final int field0 = scope != null ? scope.sortField
                : (looseOnly ? Prefs.bankSortField() : Prefs.folderSortField());
        final int dir0 = scope != null ? scope.sortDir
                : (looseOnly ? Prefs.bankSortDir() : Prefs.folderSortDir());
        final int[] field = {field0};
        final int[] dir = {dir0};

        final android.widget.LinearLayout fieldSeg = new android.widget.LinearLayout(this);
        fieldSeg.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        box.addView(fieldSeg);
        final android.widget.LinearLayout dirSeg = new android.widget.LinearLayout(this);
        dirSeg.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        android.widget.LinearLayout.LayoutParams dlp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = Ui.dp(this, 10);
        box.addView(dirSeg, dlp);

        // 当前是手动排序（字段 = 3）时补一个「手动排序」胶囊并显示选中态，
        // 否则弹窗里一个高亮的字段都没有，用户看不出当前处于手动排序。
        final String[] fieldLabels;
        if (field0 == 3) {
            fieldLabels = new String[]{Prefs.SORT_FIELDS[0], Prefs.SORT_FIELDS[1],
                    Prefs.SORT_FIELDS[2], "手动排序"};
        } else {
            fieldLabels = new String[]{Prefs.SORT_FIELDS[0], Prefs.SORT_FIELDS[1],
                    Prefs.SORT_FIELDS[2]};
        }
        renderSortSeg(fieldSeg, fieldLabels, field, dirSeg, dir);

        final android.app.AlertDialog dlg = new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("排序")
                .setView(box)
                .setPositiveButton("应用", (d, w) -> {
                    final int curField = scope != null ? scope.sortField
                            : (looseOnly ? Prefs.bankSortField() : Prefs.folderSortField());
                    if (curField == 3 && field[0] != 3) {
                        com.ziruoling.quiz.ui.dialog.ConfirmDialog.show(this, "提示",
                                "当前是自定义排序，切换为自动排序会重置已调整的顺序。继续？",
                                () -> {
                                    applySort(scope, looseOnly, field[0], dir[0]);
                                    refresh();
                                });
                        return;
                    }
                    applySort(scope, looseOnly, field[0], dir[0]);
                    refresh();
                })
                .setNegativeButton("取消", null)
                .create();
        dlg.show();
        Ui.styleContent(dlg, this, false);
    }

    private void applySort(Folder scope, boolean looseOnly, int field, int dir) {
        if (scope != null) {
            Db.setFolderSort(scope.id, field, dir);
        } else if (looseOnly) {
            Prefs.bankSortField(field);
            Prefs.bankSortDir(dir);
        } else {
            Prefs.folderSortField(field);
            Prefs.folderSortDir(dir);
        }
    }

    private void renderSortSeg(final android.widget.LinearLayout fieldSeg, final String[] labels,
                               final int[] field, final android.widget.LinearLayout dirSeg,
                               final int[] dir) {
        fieldSeg.removeAllViews();
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            boolean on = field[0] == i;
            android.widget.TextView t = new android.widget.TextView(this);
            t.setText(labels[i]);
            t.setGravity(android.view.Gravity.CENTER);
            t.setTextSize(13);
            t.setTextColor(Ui.attrColor(this, on ? R.attr.qzOnPrimary : R.attr.qzTextSub));
            t.setBackground(Ui.bg(Ui.attrColor(this,
                    on ? R.attr.qzPrimary : R.attr.qzChipBg), Ui.dp(this, 9)));
            t.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(Ui.dp(this, 2), 0, Ui.dp(this, 2), 0);
            t.setLayoutParams(lp);
            t.setOnClickListener(v -> {
                field[0] = idx;
                renderSortSeg(fieldSeg, labels, field, dirSeg, dir);
            });
            fieldSeg.addView(t);
        }
        dirSeg.removeAllViews();
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            boolean on = dir[0] == i;
            android.widget.TextView t = new android.widget.TextView(this);
            t.setText(i == 0 ? "正序" : "倒序");
            t.setGravity(android.view.Gravity.CENTER);
            t.setTextSize(13);
            t.setTextColor(Ui.attrColor(this, on ? R.attr.qzOnPrimary : R.attr.qzTextSub));
            t.setBackground(Ui.bg(Ui.attrColor(this,
                    on ? R.attr.qzPrimary : R.attr.qzChipBg), Ui.dp(this, 9)));
            t.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(Ui.dp(this, 2), 0, Ui.dp(this, 2), 0);
            t.setLayoutParams(lp);
            t.setOnClickListener(v -> {
                dir[0] = idx;
                renderSortSeg(fieldSeg, labels, field, dirSeg, dir);
            });
            dirSeg.addView(t);
        }
    }

    /** 重置题库：多选要清空的内容（总统计不受影响），再二次确认 */
    private void showResetBankDialog(final Bank bank) {
        final String[] items = {"做题记录（含顺序进度）", "错题本", "标记", "收藏"};
        final boolean[] sel = new boolean[items.length];
        android.app.AlertDialog.Builder b = new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("重置题库「" + bank.name + "」")
                .setMultiChoiceItems(items, sel, (d, w, isChecked) -> sel[w] = isChecked)
                .setPositiveButton("下一步", (d, w) -> {
                    if (!sel[0] && !sel[1] && !sel[2] && !sel[3]) {
                        Ui.toast(this, "请至少选择一项");
                        return;
                    }
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < items.length; i++) {
                        if (sel[i]) sb.append("\n· ").append(items[i]);
                    }
                    ConfirmDialog.showDanger(this, "确认重置题库",
                            "将清空该题库的：" + sb + "\n\n总统计不受影响；此操作不可恢复。",
                            () -> {
                                Db.resetBank(bank.id, sel[0], sel[1], sel[2], sel[3]);
                                refresh();
                                Ui.toast(this, "已重置");
                            });
                })
                .setNeutralButton("全选", null)
                .setNegativeButton("取消", null);
        final android.app.AlertDialog dlg = b.create();
        dlg.setOnShowListener(x -> dlg.getButton(android.app.AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(v -> {
                    for (int i = 0; i < items.length; i++) {
                        sel[i] = true;
                        dlg.getListView().setItemChecked(i, true);
                    }
                }));
        dlg.show();
    }

    /** 导出二级菜单：表格 / 文档（含图片） */
    private void showExportMenu(final Bank bank) {
        new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("导出题库")
                .setItems(new CharSequence[]{
                                "导出表格（Excel，不含图片）",
                                "导出文档（Word，含图片，可改后再导入）"},
                        (d, which) -> {
                            if (which == 0) exportBank(bank);
                            else exportBankDocx(bank);
                        })
                .setNegativeButton("取消", null)
                .show();
    }

    private void exportBank(Bank bank) {
        exportBankId = bank.id;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        intent.putExtra(Intent.EXTRA_TITLE, BankExportHelper.safeFileName(bank.name, ".xlsx"));
        startActivityForResult(intent, REQ_EXPORT);
    }

    private void exportBankDocx(Bank bank) {
        exportBankId = bank.id;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        intent.putExtra(Intent.EXTRA_TITLE, BankExportHelper.safeFileName(bank.name, ".docx"));
        startActivityForResult(intent, REQ_EXPORT_DOCX);
    }

    /** Word 导出放后台线程：读图 + 拼 OOXML */
    private void writeDocx(final Uri uri) {
        final long bid = exportBankId;
        Bank b = Db.getBank(bid);
        final String name = b == null ? "题库" : b.name;
        Ui.toast(this, "正在导出文档…");
        new Thread(() -> {
            String err = null;
            try {
                OutputStream os = getContentResolver().openOutputStream(uri);
                if (os == null) throw new Exception("无法写入所选位置");
                BankExportHelper.writeDocx(os, name, bid);
                os.close();
            } catch (Exception e) {
                err = e.getMessage();
            }
            final String ferr = err;
            runOnUiThread(() -> Ui.toast(this,
                    ferr == null ? "已导出 Word 文档（含图片）" : "导出失败：" + ferr));
        }, "docx-export").start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (settingsPage != null
                && (requestCode == SettingsPage.REQ_BACKUP_OUT
                || requestCode == SettingsPage.REQ_BACKUP_IN
                || requestCode == SettingsPage.REQ_SOUND_OK
                || requestCode == SettingsPage.REQ_SOUND_WRONG)) {
            settingsPage.onActivityResult(requestCode, resultCode, data);
            return;
        }
        if (resultCode != Activity.RESULT_OK || data == null) return;
        if (requestCode == REQ_EXPORT) {
            Uri uri = data.getData();
            if (uri == null) return;
            try {
                OutputStream out = getContentResolver().openOutputStream(uri);
                if (out != null) {
                    XlsxWriter.write(out, "题库", Db.exportRows(exportBankId));
                    out.close();
                    exportBankId = 0;
                    Ui.toast(this, "已导出题库 Excel");
                }
            } catch (Exception e) {
                Ui.toast(this, "导出失败：" + e.getMessage());
            }
        } else if (requestCode == REQ_EXPORT_DOCX) {
            Uri uri = data.getData();
            if (uri != null) writeDocx(uri);
        }
    }

    /** 列表行：文件夹或题库 */
    private static class Row {
        Folder folder;
        Bank bank;
        boolean inFolder;
        boolean isFolder() {
            return folder != null;
        }
    }

    private final java.util.Set<Long> collapsedFolders = new java.util.HashSet<>();

    private java.util.List<Row> buildRows() {
        java.util.List<Bank> all = Db.listBanks();
        java.util.List<Folder> folders = Db.listFolders();
        java.util.List<Row> rows = new java.util.ArrayList<>();

        java.util.Collections.sort(folders, (a, b) -> {
            if (a.pinned != b.pinned) return b.pinned - a.pinned;
            int r = compareBy(Prefs.folderSortField(), Prefs.folderSortDir(), a, b);
            return r != 0 ? r : Long.compare(a.id, b.id);
        });

        java.util.List<Bank> loose = new java.util.ArrayList<>();
        java.util.List<Bank> pinnedLoose = new java.util.ArrayList<>();
        for (Bank b : all) {
            if (b.folderId != 0) continue;
            if (b.pinned > 0) pinnedLoose.add(b);
            else loose.add(b);
        }
        java.util.Comparator<Bank> bc = (a, b) ->
                compareBy(Prefs.bankSortField(), Prefs.bankSortDir(), a, b);
        java.util.Collections.sort(loose, bc);
        java.util.Collections.sort(pinnedLoose, bc);

        // 1. 置顶文件夹（连同内容）
        for (Folder f : folders) {
            if (f.pinned > 0) addFolderRows(rows, f, all);
        }
        // 2. 置顶题库
        for (Bank b : pinnedLoose) {
            Row r = new Row();
            r.bank = b;
            rows.add(r);
        }
        // 3. 普通文件夹（连同内容）
        for (Folder f : folders) {
            if (f.pinned <= 0) addFolderRows(rows, f, all);
        }
        // 4. 无文件夹题库
        for (Bank b : loose) {
            Row r = new Row();
            r.bank = b;
            rows.add(r);
        }
        return rows;
    }

    private void addFolderRows(java.util.List<Row> rows, Folder f, java.util.List<Bank> all) {
        Row fr = new Row();
        fr.folder = f;
        rows.add(fr);
        if (collapsedFolders.contains(f.id)) return;
        java.util.List<Bank> inner = new java.util.ArrayList<>();
        for (Bank b : all) if (b.folderId == f.id) inner.add(b);
        java.util.Collections.sort(inner, (a, b) -> {
            if (a.pinned != b.pinned) return b.pinned - a.pinned;
            int r = compareBy(f.sortField, f.sortDir, a, b);
            return r != 0 ? r : Long.compare(a.id, b.id);
        });
        for (Bank b : inner) {
            Row r = new Row();
            r.bank = b;
            r.inFolder = true;
            rows.add(r);
        }
    }

    /** 中文按拼音排序（Android 自带 ICU Collator，无需第三方库） */
    private final java.text.Collator collator = java.text.Collator.getInstance(java.util.Locale.CHINA);

    private int compareBy(int field, int dir, Object a, Object b) {
        int r = 0;
        if (field == 0) {
            String na = a instanceof Folder ? ((Folder) a).name : ((Bank) a).name;
            String nb = b instanceof Folder ? ((Folder) b).name : ((Bank) b).name;
            r = collator.compare(na, nb);
            if (r == 0) {
                long la = a instanceof Folder ? ((Folder) a).createdAt : ((Bank) a).lastAt;
                long lb = b instanceof Folder ? ((Folder) b).createdAt : ((Bank) b).lastAt;
                r = -Long.compare(la, lb);
            }
        } else if (field == 1) {
            long la = a instanceof Folder ? Db.folderLastAt(((Folder) a).id) : ((Bank) a).lastAt;
            long lb = b instanceof Folder ? Db.folderLastAt(((Folder) b).id) : ((Bank) b).lastAt;
            r = Long.compare(la, lb);
        } else if (field == 2) {
            long ca = a instanceof Folder ? ((Folder) a).createdAt : ((Bank) a).createdAt;
            long cb = b instanceof Folder ? ((Folder) b).createdAt : ((Bank) b).createdAt;
            r = Long.compare(ca, cb);
        } else {
            int pa = a instanceof Folder ? ((Folder) a).sortPos : ((Bank) a).sortPos;
            int pb = b instanceof Folder ? ((Folder) b).sortPos : ((Bank) b).sortPos;
            r = Integer.compare(pa, pb);
        }
        // 字段 3 = 手动排序：sortPos 就是用户排定的顺序，方向对它无意义。
        // 若仍按 dir 取反，选过一次「倒序」后整份手动顺序会被反转。
        return dir == 1 && field != 3 ? -r : r;
    }

    private class BankAdapter extends BaseAdapter {
        private final List<Row> rows = new ArrayList<>();

        BankAdapter(List<Row> rows) {
            this.rows.clear();
            this.rows.addAll(rows);
        }

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            Row r = rows.get(position);
            return r.isFolder() ? r.folder.id : r.bank.id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Row row = rows.get(position);
            if (row.isFolder()) {
                View fv = convertView;
                if (fv == null || fv.findViewById(R.id.folderName) == null) {
                    fv = LayoutInflater.from(MainActivity.this)
                            .inflate(R.layout.item_folder, parent, false);
                }
                final Folder f = row.folder;
                ((TextView) fv.findViewById(R.id.folderName)).setText(f.name);
                int count = 0;
                for (Bank bk : Db.listBanks()) if (bk.folderId == f.id) count++;
                ((TextView) fv.findViewById(R.id.folderCount)).setText(count + " 个题库");
                ((TextView) fv.findViewById(R.id.folderArrow))
                        .setText(collapsedFolders.contains(f.id) ? "▸" : "▾");
                fv.setOnClickListener(vv -> {
                    if (!collapsedFolders.remove(f.id)) collapsedFolders.add(f.id);
                    Prefs.collapsedFolders(collapsedFolders);
                    refresh();
                });
                fv.setOnLongClickListener(vv -> {
                    showFolderMenu(f);
                    return true;
                });
                return fv;
            }
            View v = convertView;
            if (v == null || v.findViewById(R.id.folderName) != null) {
                v = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_bank, parent, false);
            }
            Bank b = row.bank;
            int padH = Ui.dp(MainActivity.this, 16);
            int padV = Ui.dp(MainActivity.this, 10);
            if (row.inFolder) {
                v.setPadding(padH + Ui.dp(MainActivity.this, 12), padV, padH, padV);
            } else {
                v.setPadding(padH, padV, padH, padV);
            }
            TextView name = v.findViewById(R.id.name);
            TextView counts = v.findViewById(R.id.counts);
            TextView lastAt = v.findViewById(R.id.lastAt);
            ProgressBar progress = v.findViewById(R.id.progress);

            name.setText(b.name);
            int[] tc = typeCounts == null ? null : typeCounts.get(b.id);
            StringBuilder sb = new StringBuilder("共 ").append(b.questionCount).append(" 题");
            if (tc != null) {
                boolean first = true;
                for (int i = 0; i < QuestionType.COUNT; i++) {
                    if (tc[i] > 0) {
                        if (first) {
                            sb.append("  ·  ");
                            first = false;
                        } else {
                            sb.append("  ");
                        }
                        sb.append(QuestionType.SHORT_NAMES[i]).append(tc[i]);
                    }
                }
            }
            sb.append("  ·  已做 ").append(b.doneCount)
                    .append("  ·  正确率 ").append(b.accuracy()).append("%");
            counts.setText(sb.toString());
            lastAt.setText(b.lastAt > 0 ? "最近 " + Ui.fmtTime(b.lastAt) : "");
            progress.setProgress(Math.round(b.progress() * 100));

            v.setOnClickListener(vv -> BankHomeActivity.start(MainActivity.this, b.id));
            v.setOnLongClickListener(vv -> {
                showBankMenu(b, vv);
                return true;
            });
            return v;
        }
    }
}