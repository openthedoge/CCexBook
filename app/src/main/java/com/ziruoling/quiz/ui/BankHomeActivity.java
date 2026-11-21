package com.ziruoling.quiz.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ziruoling.quiz.R;
import com.ziruoling.quiz.core.QuestionType;
import com.ziruoling.quiz.data.Bank;
import com.ziruoling.quiz.data.Db;
import com.ziruoling.quiz.data.Prefs;
import com.ziruoling.quiz.data.Question;
import com.ziruoling.quiz.ui.dialog.ConfirmDialog;
import com.ziruoling.quiz.ui.dialog.InputDialog;
import com.ziruoling.quiz.ui.dialog.TypeFilterDialog;
import com.ziruoling.quiz.util.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 题库主页：统计 + 练习模式入口 */
public class BankHomeActivity extends BaseActivity {

    private static final String EXTRA_BANK = "bank_id";
    private static final int REQ_EXPORT = 71;
    private static final int REQ_EXPORT_DOCX = 72;

    private long bankId;
    private long exportBankId;
    private TextView title, statLine, typeLine;
    private ProgressBar progress;
    private LinearLayout typeChips;
    private GridLayout grid;

    public static void start(Context c, long bankId) {
        Intent it = new Intent(c, BankHomeActivity.class);
        it.putExtra(EXTRA_BANK, bankId);
        c.startActivity(it);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bank_home);

        bankId = getIntent().getLongExtra(EXTRA_BANK, 0);
        title = findViewById(R.id.title);
        statLine = findViewById(R.id.statLine);
        typeLine = findViewById(R.id.typeLine);
        progress = findViewById(R.id.progress);
        typeChips = findViewById(R.id.typeChips);
        grid = findViewById(R.id.grid);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnMore).setOnClickListener(v -> showMenu());

        refresh();
        buildGrid();
    }

    /** 从练习/列表/收藏页返回时实时刷新统计与入口数字 */
    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        buildGrid();
    }

    private void refresh() {
        int[] types = Db.typeCounts(bankId);
        int total = 0;
        for (int t : types) total += t;
        int done = 0;
        for (Bank b : Db.listBanks()) {
            if (b.id == bankId) {
                done = b.doneCount;
            }
        }
        title.setText(bankName());
        statLine.setText(String.format(Locale.CHINA, "共 %d 题 · 已做 %d · 正确率 %d%%",
                total, done, accuracy()));
        typeLine.setText(typeSummary(types));
        progress.setProgress(total <= 0 ? 0 : Math.round(done * 100f / total));

        typeChips.removeAllViews();
        for (int i = 0; i < QuestionType.COUNT; i++) {            if (types[i] <= 0) continue;
            TextView chip = Ui.chip(this, QuestionType.SHORT_NAMES[i] + " " + types[i],
                    R.attr.qzPrimary, R.attr.qzPrimaryBg);
            int t = i;
            chip.setOnClickListener(v -> {
                List<Long> ids = Db.questionIdsByFilter(bankId, 1 << t, null, false);
                PracticeActivity.startPractice(this, bankId, ids, 0,
                        PracticeActivity.MODE_PRACTICE, QuestionType.SHORT_NAMES[t] + "练习", false);
            });
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            chipLp.rightMargin = Ui.dp(this, 6);
            chip.setLayoutParams(chipLp);
            typeChips.addView(chip);
        }
    }

    private String typeSummary(int[] types) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < QuestionType.COUNT; i++) {
            if (types[i] > 0) {
                if (sb.length() > 0) sb.append("  ·  ");
                sb.append(QuestionType.SHORT_NAMES[i]).append(types[i]);
            }
        }
        return sb.toString();
    }

    private void showMenu() {
        PopupMenu pm = new PopupMenu(this, findViewById(R.id.btnMore));
        pm.getMenu().add("重命名");
        pm.getMenu().add("重置顺序进度");
        pm.getMenu().add("导出题库…");
        pm.getMenu().add("删除题库");
        pm.setOnMenuItemClickListener(item -> {
            switch (item.getTitle().toString()) {
                case "重命名":
                    InputDialog.show(this, "重命名题库", title.getText().toString(), "题库名称", name -> {
                        if (!name.isEmpty()) {
                            Db.renameBank(bankId, name);
                            title.setText(name);
                        }
                    });
                    return true;
                case "重置顺序进度":
                    ConfirmDialog.show(this, "重置顺序进度", "将顺序练习从第一题重新开始，确定？", () -> {
                        Db.resetProgress(bankId);
                        Ui.toast(this, "已重置");
                        refresh();
                    });
                    return true;
                case "导出题库…":
                    showExportMenu();
                    return true;
                case "删除题库":
                    ConfirmDialog.showDanger(this, "删除题库",
                            "将删除该题库及其全部题目、记录，此操作不可恢复。确定删除？",
                            () -> {
                                Db.deleteBank(bankId);
                                finish();
                            });
                    return true;
            }
            return false;
        });
        pm.show();
    }

    private String bankName() {
        for (Bank b : Db.listBanks()) {
            if (b.id == bankId) return b.name;
        }
        return "题库";
    }

    private int accuracy() {
        for (Bank b : Db.listBanks()) {
            if (b.id == bankId) return b.accuracy();
        }
        return 0;
    }

    /** 导出题库：先让用户选表格还是文档 */
    private void showExportMenu() {
        new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("导出题库")
                .setItems(new CharSequence[]{
                                "导出表格（Excel，不含图片）",
                                "导出文档（Word，含图片，可改后再导入）"},
                        (d, which) -> {
                            if (which == 0) export();
                            else exportDocx();
                        })
                .setNegativeButton("取消", null)
                .show();
    }

    private void export() {
        exportBankId = bankId;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        intent.putExtra(Intent.EXTRA_TITLE, title.getText().toString() + ".xlsx");
        startActivityForResult(intent, REQ_EXPORT);
    }

    private void exportDocx() {
        exportBankId = bankId;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        intent.putExtra(Intent.EXTRA_TITLE, title.getText().toString() + ".docx");
        startActivityForResult(intent, REQ_EXPORT_DOCX);
    }

    /** 读题库 + 图片，组装成导出用的 PaperQ 列表（在后台线程调用） */
    private List<com.ziruoling.quiz.core.DocxWriter.PaperQ> buildPaperQs(long bankId) {
        return BankExportHelper.paperQs(bankId);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQ_EXPORT) {
            try {
                java.io.OutputStream os = getContentResolver().openOutputStream(data.getData());
                if (os != null) {
                    com.ziruoling.quiz.core.XlsxWriter.write(os, "题库", Db.exportRows(exportBankId));
                    os.close();
                    Ui.toast(this, "已导出题库 Excel");
                }
            } catch (Exception e) {
                Ui.toast(this, "导出失败：" + e.getMessage());
            }
        } else if (requestCode == REQ_EXPORT_DOCX) {
            writeDocx(data.getData());
        }
    }

    /** Word 导出放后台线程：读图 + 拼 OOXML，避免大题库卡界面 */
    private void writeDocx(final android.net.Uri uri) {
        final long bid = exportBankId;
        final String bankName = title.getText().toString();
        Ui.toast(this, "正在导出文档…");
        new Thread(() -> {
            String err = null;
            try {
                java.io.OutputStream os = getContentResolver().openOutputStream(uri);
                if (os == null) throw new Exception("无法写入所选位置");
                List<com.ziruoling.quiz.core.DocxWriter.PaperQ> list = buildPaperQs(bid);
                com.ziruoling.quiz.core.DocxWriter.writeBank(os, bankName, list);
                os.close();
            } catch (Exception e) {
                err = e.getMessage();
            }
            final String ferr = err;
            runOnUiThread(() -> Ui.toast(this,
                    ferr == null ? "已导出 Word 文档（含图片）" : "导出失败：" + ferr));
        }, "docx-export").start();
    }

    private void buildGrid() {
        grid.removeAllViews();
        // 「继续刷题」：直接按上次进度续上，不再走「顺序练习」的二级选择
        final List<Long> seqIds = Db.questionIds(bankId);
        long lastQid = Db.getProgress(bankId);
        final int resumeIdx = lastQid > 0 ? seqIds.indexOf(lastQid) : -1;
        String resumeSub;
        if (seqIds.isEmpty()) {
            resumeSub = "题库为空";
        } else if (resumeIdx < 0) {
            resumeSub = "尚无进度，从第 1 题开始";
        } else {
            resumeSub = "上次到第 " + (resumeIdx + 1) + " 题";
        }
        addItem("继续刷题", resumeSub, () -> {
            if (seqIds.isEmpty()) {
                Ui.toast(this, "题库为空");
                return;
            }
            PracticeActivity.startPractice(this, bankId, seqIds, Math.max(0, resumeIdx),
                    PracticeActivity.MODE_PRACTICE, "继续刷题", true, true);
        });
        addItem("顺序练习", "继续或重新开始", () -> {
            List<Long> ids = Db.questionIds(bankId);
            if (ids.isEmpty()) {
                Ui.toast(this, "题库为空");
                return;
            }
            startWithChoice("顺序练习", PracticeActivity.MODE_PRACTICE, ids, "练到");
        });
        addItem("随机刷题", "随机打乱题目", () -> {
            List<Long> ids = Db.questionIdsByFilter(bankId, -1, null, true);
            if (ids.isEmpty()) {
                Ui.toast(this, "题库为空");
                return;
            }
            PracticeActivity.startPractice(this, bankId, ids, 0,
                    PracticeActivity.MODE_PRACTICE, "随机刷题", false);
        });
        addItem("背题模式", "看题回忆，乱序防记序", () -> {
            List<Long> ids = Db.questionIds(bankId);
            if (ids.isEmpty()) {
                Ui.toast(this, "题库为空");
                return;
            }
            // 固定种子乱序：同一题库每次顺序相同（续看位置有效），且避免顺序记忆
            long[] arr = new long[ids.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = ids.get(i);
            PracticeActivity.reciteShuffle(arr, bankId);
            List<Long> shuffled = new ArrayList<>(arr.length);
            for (long id : arr) shuffled.add(id);
            startWithChoice("背题模式", PracticeActivity.MODE_RECITE, shuffled, "看到");
        });
        addItem("专项练习", "按题型/分类筛选", () ->
                TypeFilterDialog.show(this, bankId, (mask, category, random) -> {
                    List<Long> ids = Db.questionIdsByFilter(bankId, mask, category, random);
                    if (ids.isEmpty()) {
                        Ui.toast(this, "该组合下没有题目");
                        return;
                    }
                    PracticeActivity.startPractice(this, bankId, ids, 0,
                            PracticeActivity.MODE_PRACTICE, "专项练习", false, true);
                }));
        addItem("浏览全部题目", "列表 + 搜索", () ->
                QuestionListActivity.start(this, bankId, QuestionListActivity.SCOPE_ALL,
                        QuestionListActivity.FILTER_ALL, "全部题目"));
        addItem("模拟考试", "计时 + 评分", () ->
                startActivity(new Intent(this, ExamConfigActivity.class)
                        .putExtra(ExamConfigActivity.EXTRA_BANK, bankId)));
        addItem("错题重做", Db.wrongCount(bankId, 0) + " 道未掌握", () ->
                QuestionListActivity.start(this, bankId, QuestionListActivity.SCOPE_WRONG,
                        QuestionListActivity.FILTER_UNRESOLVED, "错题重做"));
        addItem("收藏练习", Db.favCount(bankId) + " 道", () ->
                QuestionListActivity.start(this, bankId, QuestionListActivity.SCOPE_FAV,
                        QuestionListActivity.FILTER_ALL, "收藏练习"));
        addItem("标记", "按词条查看标记题目", () ->
                QuestionListActivity.start(this, bankId, QuestionListActivity.SCOPE_FLAGGED,
                        QuestionListActivity.FILTER_ALL, "标记"));
        addItem("考试记录", "历史成绩", () ->
                startActivity(new Intent(this, StatsActivity.class)));
        addItem("导出试卷", "随机/按题型抽题出 Word 卷", () ->
                ExportExamActivity.start(this, bankId));
        addItem("导出题库", "表格或文档（含图片）", this::showExportMenu);
    }

    /**
     * 顺序练习 / 背题模式：开始方式列表——从上次继续 / 重新开始 / 自定义起始题号。
     * 自定义题号只收数字，超出范围时自动夹到第 1 题或最后一题。
     */
    private void startWithChoice(String title, int mode, List<Long> ids, String verb) {
        if (ids == null || ids.isEmpty()) {
            Ui.toast(this, "题库为空");
            return;
        }
        int n = ids.size();
        long lastQid = Db.getProgress(bankId);
        int lastIdx = lastQid > 0 ? ids.indexOf(lastQid) : -1;

        final List<String> labels = new ArrayList<>();
        final List<Integer> starts = new ArrayList<>();
        if (lastIdx > 0) {
            labels.add("从上次" + verb + "的位置继续（第 " + (lastIdx + 1) + " 题）");
            starts.add(lastIdx);
        }
        labels.add("重新开始（第 1 题）");
        starts.add(0);
        labels.add("自定义起始题号…");
        starts.add(-1);

        final String fTitle = title;
        final int fMode = mode;
        new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle(title + "（共 " + n + " 题）")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    int start = starts.get(which);
                    if (start >= 0) {
                        PracticeActivity.startPractice(this, bankId, ids, start, fMode, fTitle, true, true);
                    } else {
                        askStartIndex(fTitle, fMode, ids);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 自定义起始题号：只允许数字，越界自动夹到首/末题 */
    private void askStartIndex(final String title, final int mode, final List<Long> ids) {
        final int n = ids.size();
        InputDialog.showNumber(this, "从第几题开始", "输入 1 - " + n, input -> {
            String digits = input == null ? "" : input.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) {
                Ui.toast(this, "请输入数字");
                return;
            }
            int num;
            try {
                num = Integer.parseInt(digits);
            } catch (NumberFormatException e) {
                Ui.toast(this, "请输入数字");
                return;
            }
            int clamped = Math.max(1, Math.min(n, num));
            if (clamped != num) {
                Ui.toast(this, "题号超出范围，已定位到第 " + clamped + " 题");
            }
            PracticeActivity.startPractice(this, bankId, ids, clamped - 1, mode, title, true, true);
        });
    }

    private void addItem(String label, String sub, Runnable action) {
        LinearLayout card = Ui.card(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        TextView t = Ui.text(this, label, 15, R.attr.qzText, Typeface.BOLD);
        TextView s = Ui.text(this, sub, 12, R.attr.qzTextSub, 0);
        card.addView(t);
        card.addView(s, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        s.setPadding(0, Ui.dp(this, 2), 0, 0);
        card.setOnClickListener(v -> action.run());
        grid.addView(card, new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)));
        ((GridLayout.LayoutParams) card.getLayoutParams()).setMargins(
                Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
    }
}