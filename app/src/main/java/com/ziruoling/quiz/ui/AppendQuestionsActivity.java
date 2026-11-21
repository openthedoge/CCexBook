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

    private static final int REQ_FILE = 301;
    private static final int REQ_EDIT_BASE = 400;

    private long bankId;
    private String bankName = "题库";
    private TextView statusTv;

    /** 逐题确认时待处理的题目 id（按插入顺序） */
    private final List<Long> pending = new ArrayList<>();

    public static void start(Context c, long bankId, String bankName) {
        c.startActivity(new Intent(c, AppendQuestionsActivity.class)
                .putExtra(EXTRA_BANK, bankId)
                .putExtra(EXTRA_BANK_NAME, bankName));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_append_questions);
        bankId = getIntent().getLongExtra(EXTRA_BANK, 0);
        String name = getIntent().getStringExtra(EXTRA_BANK_NAME);
        if (name != null && !name.isEmpty()) bankName = name;
        ((TextView) findViewById(R.id.appendBank)).setText("当前题库：" + bankName);
        statusTv = findViewById(R.id.appendStatus);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnPickFile).setOnClickListener(v -> pickFile());
        findViewById(R.id.btnPasteText).setOnClickListener(v -> pasteText());
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

    private void pasteText() {
        final EditText et = new EditText(this);
        et.setHint("题干\nA.选项一\nB.选项二\n答案：A\n解析：…\n分类：第1篇");
        et.setMinLines(8);
        et.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        int pad = Ui.dp(this, 16);
        et.setPadding(pad, Ui.dp(this, 8), pad, 0);
        new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("粘贴题目")
                .setView(et)
                .setPositiveButton("解析", (d, w) -> handle(TxtParser.parseText(et.getText().toString())))
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            parseUri(data.getData());
            return;
        }
        if (requestCode >= REQ_EDIT_BASE) {
            afterEdit(requestCode - REQ_EDIT_BASE);
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

    private void handle(ParseResult r) {
        if (r == null || r.questions.isEmpty()) {
            statusTv.setText("");
            Ui.toast(this, "没有解析到题目");
            return;
        }
        final ParseResult fr = r;
        statusTv.setText("解析到 " + r.questions.size() + " 题，等待选择处理方式");
        new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("解析到 " + r.questions.size() + " 题")
                .setItems(new CharSequence[]{
                        "自动插入（按分类追加到末尾）",
                        "逐题确认（可编辑并指定位置）"}, (d, which) -> {
                    if (which == 0) autoInsert(fr);
                    else editEach(fr);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void autoInsert(ParseResult r) {
        int n = 0;
        for (ParsedQuestion q : r.questions) {
            n += Db.insertQuestions(bankId, Collections.singletonList(q), q.category, Integer.MAX_VALUE);
        }
        statusTv.setText("已插入 " + n + " 题");
        Ui.toast(this, "已插入 " + n + " 题");
    }

    private void editEach(ParseResult r) {
        pending.clear();
        for (ParsedQuestion q : r.questions) {
            pending.addAll(Db.insertQuestionsIds(bankId, Collections.singletonList(q),
                    q.category, Integer.MAX_VALUE));
        }
        if (pending.isEmpty()) {
            Ui.toast(this, "插入失败");
            return;
        }
        statusTv.setText("逐题确认：1 / " + pending.size());
        openEditor(0);
    }

    private void openEditor(int index) {
        if (index >= pending.size()) {
            statusTv.setText("全部处理完成，共 " + pending.size() + " 题");
            Ui.toast(this, "全部处理完成");
            return;
        }
        statusTv.setText("逐题确认：" + (index + 1) + " / " + pending.size());
        EditQuestionActivity.start(this, pending.get(index), REQ_EDIT_BASE + index);
    }

    private void afterEdit(final int index) {
        if (index >= pending.size()) return;
        final long qid = pending.get(index);
        PositionPicker.show(this, bankId, "这道题插到哪？（可直接取消，保持末尾）",
                () -> openEditor(index + 1),
                (cat, pos) -> {
                    Db.moveQuestion(qid, cat, pos);
                    openEditor(index + 1);
                });
    }
}
