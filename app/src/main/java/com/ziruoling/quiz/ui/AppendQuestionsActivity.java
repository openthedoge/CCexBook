package com.ziruoling.quiz.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.ziruoling.quiz.R;
import com.ziruoling.quiz.core.ParseResult;
import com.ziruoling.quiz.core.QuestionType;
import com.ziruoling.quiz.core.ParsedQuestion;
import com.ziruoling.quiz.core.SmartParser;
import com.ziruoling.quiz.core.TxtParser;
import com.ziruoling.quiz.data.Db;
import com.ziruoling.quiz.util.Ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 追加题目：把新题插进现有题库，不动已有做题记录。
 * 支持「文件」与「粘贴文本」两种来源，解析后可自动插入或逐题确认（编辑 + 选位置）。
 */
public class AppendQuestionsActivity extends BaseActivity {

    public static final String EXTRA_BANK = "bank";
    public static final String EXTRA_BANK_NAME = "bank_name";
    private static final String EXTRA_NEW_BANK = "new_bank";

    private static final int REQ_FILE = 301;

    private static final int REQ_NEW_EDIT = 260;
    private static final int REQ_CONFIRM = 261;
    private static final int REQ_EDIT_BASE = 400;

    private long bankId;
    private String bankName = "题库";
    private boolean newBank;
    private TextView statusTv;

    /** 逐题确认时待处理的题目 id（按插入顺序） */
    private final List<Long> pending = new ArrayList<>();
    private long pendingDraft;

    public static void start(Context c, long bankId, String bankName) {
        start(c, bankId, bankName, false);
    }

    /** newBank=true：本题库是刚为“导入”新建的，退出时若仍为空则自动删除 */
    public static void start(Context c, long bankId, String bankName, boolean newBank) {
        c.startActivity(new Intent(c, AppendQuestionsActivity.class)
                .putExtra(EXTRA_BANK, bankId)
                .putExtra(EXTRA_BANK_NAME, bankName)
                .putExtra(EXTRA_NEW_BANK, newBank));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_append_questions);
        bankId = getIntent().getLongExtra(EXTRA_BANK, 0);
        newBank = getIntent().getBooleanExtra(EXTRA_NEW_BANK, false);
        String name = getIntent().getStringExtra(EXTRA_BANK_NAME);
        if (name != null && !name.isEmpty()) bankName = name;
        ((TextView) findViewById(R.id.appendBank)).setText("当前题库：" + bankName);
        statusTv = findViewById(R.id.appendStatus);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnPickFile).setOnClickListener(v -> pickFile());
        findViewById(R.id.btnPasteText).setOnClickListener(v -> pasteText());
        findViewById(R.id.btnAddOne).setOnClickListener(v -> addQuestion());
    }

    private void pickFile() {
        Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType("*/*");
        it.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "text/plain", "text/csv", "text/comma-separated-values",
                "application/octet-stream"});
        startActivityForResult(it, REQ_FILE);
    }

    /** 直接添加题目：建草稿 → 编辑器 → 选分类与位置 */
    private void addQuestion() {
        long id = Db.createDraftQuestion(bankId);
        if (id <= 0) {
            Ui.toast(this, "创建题目失败，请重试");
            return;
        }
        pendingDraft = id;
        EditQuestionActivity.start(this, pendingDraft, REQ_NEW_EDIT);
    }

    private void pasteText() {
        final EditText et = new EditText(this);
        et.setHint("题干\nA.选项一\nB.选项二\n答案：A\n解析：…\n分类：第1篇");
        et.setMinLines(8);
        et.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        int pad = Ui.dp(this, 16);
        et.setPadding(pad, Ui.dp(this, 8), pad, 0);

        final android.app.AlertDialog dlg = new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("粘贴题目")
                .setView(et)
                .setPositiveButton("解析", null)
                .setNegativeButton("取消", null)
                .create();
        dlg.setOnShowListener(x -> {
            dlg.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
                    .setOnClickListener(v -> Ui.toast(this, "长按「解析」确认，防止误触"));
            dlg.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
                    .setOnLongClickListener(v -> {
                        String text = et.getText().toString();
                        dlg.dismiss();
                        handle(TxtParser.parseText(text));
                        return true;
                    });
            dlg.getButton(android.content.DialogInterface.BUTTON_NEGATIVE)
                    .setOnClickListener(v -> {
                        if (et.getText().toString().trim().isEmpty()) {
                            dlg.dismiss();
                        } else {
                            com.ziruoling.quiz.ui.dialog.ConfirmDialog.show(this, "放弃粘贴内容",
                                    "粘贴框里还有内容，确定放弃？", dlg::dismiss);
                        }
                    });
        });
        dlg.setOnCancelListener(d -> {
            if (!et.getText().toString().trim().isEmpty()) {
                Ui.toast(this, "已保留，未解析");
            }
        });
        dlg.show();
        Ui.styleContent(dlg, this, false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            parseUri(data.getData());
            return;
        }
        if (requestCode == REQ_CONFIRM) {
            if (resultCode == RESULT_OK) {
                pickCategory(editingId);
            } else {
                Db.deleteQuestion(editingId);
                advance();
            }
            return;
        }
        if (requestCode == REQ_NEW_EDIT) {
            if (resultCode == RESULT_OK) {
                pickCategory(pendingDraft);
            } else {
                Db.deleteQuestion(pendingDraft);
            }
            return;
        }
    }

    private void parseUri(final Uri uri) {
        statusTv.setText("正在解析…");
        new Thread(() -> {
            String err = null;
            ParseResult r = null;
            try {
                String display = displayName(uri);
                String lower = display.toLowerCase(java.util.Locale.ROOT);
                String ext = lower.endsWith(".docx") ? ".docx"
                        : lower.endsWith(".xlsx") ? ".xlsx"
                        : lower.endsWith(".csv") ? ".csv" : ".txt";
                File tmp = new File(getCacheDir(), "append_tmp" + ext);
                try (InputStream in = getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(tmp)) {
                    if (in == null) throw new Exception("无法读取所选文件");
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                r = SmartParser.parse(tmp, display);
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            } catch (Exception e) {
                err = e.getMessage();
            }
            final ParseResult fr = r;
            final String ferr = err;
            runOnUiThread(() -> {
                if (ferr != null) {
                    statusTv.setText("");
                    Ui.toast(this, "解析失败：" + ferr);
                } else {
                    handle(fr);
                }
            });
        }, "append-parse").start();
    }

    private String displayName(Uri uri) {
        String n = null;
        android.database.Cursor c = getContentResolver().query(uri, null, null, null, null);
        if (c != null) {
            int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
            if (idx >= 0 && c.moveToFirst()) n = c.getString(idx);
            c.close();
        }
        return n == null ? "题库.txt" : n;
    }

    // ==================== 解析结果处理 ====================

    /** 待处理队列 */
    private final List<ParsedQuestion> queue = new ArrayList<>();
    private int cursor;
    private boolean autoRest;
    private long editingId;

    /** 完整性：题干必有；判断题只看题干；选择题需 ≥2 选项且答案非空 */
    private boolean complete(ParsedQuestion q) {
        if (q.content == null || q.content.trim().isEmpty()) return false;
        if (q.type == QuestionType.JUDGE) return true;
        if (q.answer == null || q.answer.trim().isEmpty()) return false;
        if ((q.type == QuestionType.SINGLE || q.type == QuestionType.MULTI)
                && q.options.size() < 2) return false;
        return true;
    }

    private String summary(ParsedQuestion q, int i) {
        String content = q.content == null ? "" : q.content.replace("\n", " ").trim();
        if (content.length() > 18) content = content.substring(0, 18) + "…";
        String ans = q.answer == null || q.answer.trim().isEmpty() ? "—" : q.answer.trim();
        String typeName = QuestionType.NAMES[q.type];
        String miss = complete(q) ? "" : "  ⚠缺内容";
        return (i + 1) + ". " + typeName + " · " + ans + " · " + content + miss;
    }

    /** 还有未处理的题时，每次退出都先确认（顶栏返回仍是直接 finish） */
    @Override
    public void onBackPressed() {
        int left = Math.max(0, queue.size() - cursor);
        if (left > 0) {
            new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                    .setTitle("还有 " + left + " 题未处理")
                    .setMessage("退出将丢弃这些题目")
                    .setPositiveButton("继续处理", null)
                    .setNegativeButton("放弃", (d, w) -> {
                        queue.clear();
                        cursor = 0;
                        finish();
                    })
                    .show();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (newBank && bankId > 0 && Db.questionIds(bankId).isEmpty()) {
            Db.deleteBank(bankId);   // 新建后一题没加就退出，清掉空题库
        }
    }

    private void handle(ParseResult r) {
        if (r == null || r.questions.isEmpty()) {
            statusTv.setText("");
            Ui.toast(this, "没有解析到题目");
            return;
        }
        queue.clear();
        queue.addAll(r.questions);
        cursor = 0;
        autoRest = false;
        showPreview();
    }

    /** 预览列表：长按删除某题，底部按钮选处理方式 */
    private void showPreview() {
        if (queue.isEmpty()) {
            Ui.toast(this, "没有待处理题目");
            return;
        }
        int bad = 0;
        final String[] labels = new String[queue.size()];
        for (int i = 0; i < queue.size(); i++) {
            if (!complete(queue.get(i))) bad++;
            labels[i] = summary(queue.get(i), i);
        }
        statusTv.setText("解析到 " + queue.size() + " 题，等待选择处理方式");
        final android.app.AlertDialog dlg = new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("解析到 " + queue.size() + " 题" + (bad > 0 ? "（不完整 " + bad + "）" : ""))
                .setItems(labels, null)
                .setPositiveButton("自动插入", (d, w) -> autoInsert())
                .setNegativeButton("逐题确认", (d, w) -> startConfirm())
                .setNeutralButton("取消", null)
                .setCancelable(false)
                .create();
        dlg.show();
        dlg.setCanceledOnTouchOutside(false);
        Ui.styleContent(dlg, this, false);
        dlg.getListView().setOnItemLongClickListener((parent, view, pos, id) -> {
            queue.remove(pos);
            dlg.dismiss();
            Ui.toast(this, "已删除该题");
            if (!queue.isEmpty()) showPreview();
            return true;
        });
    }

    /** 自动插入：只插完整的，不完整的交给用户决定 */
    private void autoInsert() {
        int ok = 0;
        final List<ParsedQuestion> bad = new ArrayList<>();
        for (ParsedQuestion q : queue) {
            if (complete(q)) {
                ok += Db.insertQuestions(bankId, Collections.singletonList(q), q.category,
                        Integer.MAX_VALUE);
            } else {
                bad.add(q);
            }
        }
        if (bad.isEmpty()) {
            statusTv.setText("已插入 " + ok + " 题");
            Ui.toast(this, "已插入 " + ok + " 题");
            finish();
            return;
        }
        final int okCount = ok;
        final List<ParsedQuestion> badList = bad;
        new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("已插入 " + okCount + " 题")
                .setMessage(badList.size() + " 题缺少内容未插入")
                .setCancelable(false)
                .setItems(new CharSequence[]{"取消它们", "直接插入", "一一编辑"}, (d, which) -> {
                    if (which == 0) {
                        finish();
                    } else if (which == 1) {
                        int n = 0;
                        for (ParsedQuestion q : badList) {
                            n += Db.insertQuestions(bankId, Collections.singletonList(q),
                                    q.category, Integer.MAX_VALUE);
                        }
                        statusTv.setText("已插入 " + (okCount + n) + " 题");
                        Ui.toast(this, "已插入 " + (okCount + n) + " 题");
                        finish();
                    } else {
                        queue.clear();
                        queue.addAll(badList);
                        cursor = 0;
                        startConfirm();
                    }
                })
                .show();
    }

    private void startConfirm() {
        if (cursor >= queue.size()) {
            statusTv.setText("全部处理完成");
            Ui.toast(this, "全部处理完成");
            finish();
            return;
        }
        final ParsedQuestion q = queue.get(cursor);
        statusTv.setText("逐题确认：" + (cursor + 1) + " / " + queue.size());
        new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("第 " + (cursor + 1) + " / " + queue.size() + " 题")
                .setMessage(summary(q, cursor))
                .setItems(new CharSequence[]{"编辑并确认", "删除本题",
                        "后续自动插入（剩余全部按解析结果）", "退出"}, (d, which) -> {
                    if (which == 0) editCurrent();
                    else if (which == 1) {
                        queue.remove(cursor);
                        startConfirm();
                    } else if (which == 2) {
                        autoRest = true;
                        insertRemaining();
                    } else {
                        // 每次点「退出」都先确认未处理题（与系统返回一致）
                        onBackPressed();
                    }
                })
                .show();
    }

    /** 编辑当前题：先落草稿，保存后才算确认；取消则删除草稿 */
    private void editCurrent() {
        final ParsedQuestion q = queue.get(cursor);
        if (q.type == QuestionType.JUDGE && (q.answer == null || q.answer.trim().isEmpty())) {
            q.answer = "A";
        }
        List<Long> ids = Db.insertQuestionsIds(bankId, Collections.singletonList(q),
                q.category, Integer.MAX_VALUE);
        if (ids.isEmpty()) {
            Ui.toast(this, "无法创建题目");
            return;
        }
        editingId = ids.get(0);
        EditQuestionActivity.start(this, editingId, REQ_CONFIRM);
    }

    /** 剩余的题按「只插完整的」批量入库 */
    private void insertRemaining() {
        int ok = 0;
        int skip = 0;
        for (int i = cursor; i < queue.size(); i++) {
            ParsedQuestion q = queue.get(i);
            if (complete(q)) {
                ok += Db.insertQuestions(bankId, Collections.singletonList(q), q.category,
                        Integer.MAX_VALUE);
            } else {
                skip++;
            }
        }
        statusTv.setText("已插入 " + ok + " 题" + (skip > 0 ? "，跳过 " + skip + " 题" : ""));
        Ui.toast(this, "已插入 " + ok + " 题" + (skip > 0 ? "，跳过 " + skip + " 题" : ""));
        finish();
    }

    private void pickCategory(final long qid) {
        final java.util.List<String> cats = Db.categories(bankId);
        final java.util.List<String> labels = new java.util.ArrayList<>(cats);
        labels.add("新分类…");
        new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("保存到分类")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    if (which == cats.size()) {
                        com.ziruoling.quiz.ui.dialog.InputDialog.show(this, "新分类", "",
                                "分类名称", name -> {
                                    String cat = name == null ? "" : name.trim();
                                    if (cat.isEmpty()) return;
                                    askPosition(qid, cat);
                                });
                    } else {
                        askPosition(qid, cats.get(which));
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void askPosition(final long qid, final String category) {
        com.ziruoling.quiz.ui.dialog.InputDialog.showNumber(this, "第几题位置",
                "留空 = 追加到该分类末尾", input -> {
                    String digits = input == null ? "" : input.replaceAll("[^0-9]", "");
                    int pos = digits.isEmpty() ? -1 : Math.max(0, Integer.parseInt(digits) - 1);
                    Db.moveQuestion(qid, category, pos);
                    advance();
                });
    }

    private void advance() {
        cursor++;
        if (autoRest) insertRemaining();
        else if (cursor >= queue.size()) {
            statusTv.setText("全部处理完成");
            Ui.toast(this, "全部处理完成");
            finish();
        } else {
            startConfirm();
        }
    }
}
