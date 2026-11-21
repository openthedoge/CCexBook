package com.ziruoling.quiz.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;

import com.ziruoling.quiz.R;
import com.ziruoling.quiz.core.AnswerUtil;
import com.ziruoling.quiz.core.DocxWriter;
import com.ziruoling.quiz.core.ParseResult;
import com.ziruoling.quiz.core.ParsedQuestion;
import com.ziruoling.quiz.core.QuestionType;
import com.ziruoling.quiz.core.SmartParser;
import com.ziruoling.quiz.core.XlsxWriter;
import com.ziruoling.quiz.data.Bank;
import com.ziruoling.quiz.data.Db;
import com.ziruoling.quiz.util.Ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 题库导入：系统文件选择器 / 微信等应用「用其他应用打开」/ 系统分享。
 * 导入前可视化预览，支持新建题库或追加到已有题库。
 */
public class ImportActivity extends BaseActivity {

    private static final int REQ_PICK = 301;
    private static final int REQ_TEMPLATE = 302;

    private TextView fileInfo, warnings, btnImport;
    private EditText name;
    private LinearLayout chips, preview;
    private Spinner bankSpinner;
    private View nameRow;
    private ParseResult result;
    private File tmp;
    private boolean parsing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import);

        fileInfo = findViewById(R.id.fileInfo);
        name = findViewById(R.id.name);
        nameRow = findViewById(R.id.nameRow);
        bankSpinner = findViewById(R.id.bankSpinner);
        chips = findViewById(R.id.chips);
        warnings = findViewById(R.id.warnings);
        preview = findViewById(R.id.preview);
        btnImport = findViewById(R.id.btnImport);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnTemplate).setOnClickListener(v -> showTemplateMenu());
        btnImport.setOnClickListener(v -> doImport());

        setupBankSpinner();

        // 微信 / QQ / 文件管理器等通过「用其他应用打开」或「分享」传入附件
        Uri received = null;
        Intent intent = getIntent();
        if (intent != null) {
            String action = intent.getAction();
            if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
                received = intent.getData();
            } else if (Intent.ACTION_SEND.equals(action)) {
                Object stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (stream instanceof Uri) {
                    received = (Uri) stream;
                }
            }
        }
        if (received != null) {
            fileInfo.setText("正在解析来自其他应用的文件…");
            parseUri(received);
        } else {
            pickFile();
        }
    }

    private void setupBankSpinner() {
        List<String> items = new ArrayList<>();
        items.add("＋ 新建题库");
        for (Bank b : Db.listBanks()) {
            items.add(b.name);
        }
        ArrayAdapter<String> aa = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        aa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        bankSpinner.setAdapter(aa);
        bankSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                nameRow.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void pickFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "text/plain",
                "text/csv",
                "application/octet-stream"});
        try {
            startActivityForResult(intent, REQ_PICK);
        } catch (Exception e) {
            Ui.toast(this, "无法打开文件选择器");
            finish();
        }
    }

    private void showTemplateMenu() {
        PopupMenu pm = new PopupMenu(this, findViewById(R.id.btnTemplate));
        pm.getMenu().add("导出空白模板（xlsx）");
        pm.getMenu().add("导出示例模板（xlsx）");
        pm.getMenu().add("导出导入模板（Word / docx，可贴图）");
        pm.setOnMenuItemClickListener(item -> {
            String t = item.getTitle().toString();
            if (t.contains("Word")) {
                exportTemplateWord();
            } else {
                exportTemplate(t.contains("示例"));
            }
            return true;
        });
        pm.show();
    }

    private void exportTemplate(boolean sample) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        intent.putExtra(Intent.EXTRA_TITLE, sample ? "刷题导入模板_示例.xlsx" : "刷题导入模板.xlsx");
        startActivityForResult(intent, REQ_TEMPLATE);
    }

    private void exportTemplateWord() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        intent.putExtra(Intent.EXTRA_TITLE, "刷题导入模板_Word版.docx");
        startActivityForResult(intent, REQ_TEMPLATE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK) {
            if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
                Ui.toast(this, "未选择文件");
                finish();
                return;
            }
            parseUri(data.getData());
        } else if (requestCode == REQ_TEMPLATE && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            try {
                OutputStream os = getContentResolver().openOutputStream(data.getData());
                if (os != null) {
                    String name = queryName(data.getData());
                    if (name != null && name.toLowerCase().endsWith(".docx")) {
                        DocxWriter.writeTemplate(os);
                    } else {
                        XlsxWriter.write(os, "模板", templateRows());
                    }
                    os.close();
                    Ui.toast(this, "模板已导出，可在电脑上用 Office/WPS 编辑后导入");
                }
            } catch (Exception e) {
                Ui.toast(this, "导出失败：" + e.getMessage());
            }
        }
    }

    /** 统一解析入口：复制到缓存后解析（支持 SAF / 微信 FileProvider 等来源） */
    private void parseUri(final Uri uri) {
        if (parsing) return;
        parsing = true;
        final String displayName = queryName(uri);
        new Thread(() -> {
            try {
                InputStream in = getContentResolver().openInputStream(uri);
                if (in == null) throw new Exception("无法读取该文件（可能已被清理）");
                String ext = extensionOf(displayName);
                File f = File.createTempFile("import_", ext, getCacheDir());
                FileOutputStream fos = new FileOutputStream(f);
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
                fos.close();
                in.close();
                final ParseResult r = SmartParser.parse(f, displayName);
                runOnUiThread(() -> resolveGuessedThenShow(displayName, f, r));
            } catch (final Exception e) {
                runOnUiThread(() -> {
                    parsing = false;
                    fileInfo.setText("解析失败：" + e.getMessage()
                            + "\n\n微信/QQ 附件请使用「用其他应用打开」方式导入：在聊天中点开附件 → 右上角“···” → 选择其他应用打开 → 选择本应用。");
                    Ui.toast(this, "解析失败：" + e.getMessage());
                });
            }
        }).start();
    }

    private String queryName(Uri uri) {
        try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String s = c.getString(idx);
                    if (s != null) return s;
                }
            }
        } catch (Exception ignored) {
        }
        String p = uri.getLastPathSegment();
        return p == null ? "题库文件" : p;
    }

    private String extensionOf(String name) {
        int i = name.lastIndexOf('.');
        String ext = i >= 0 ? name.substring(i) : ".txt";
        if (ext.length() > 6) ext = ".txt";
        return ext;
    }

    // ==================== 题型无法识别时的确认 ====================

    /** 解析结果里若存在「题型列识别不了、靠猜」的题，先让用户确认再预览 */
    private void resolveGuessedThenShow(final String fileName, final File tmpFile, final ParseResult r) {
        int guessed = 0;
        for (ParsedQuestion q : r.questions) if (q.typeGuessed) guessed++;
        if (guessed == 0) {
            showResult(fileName, tmpFile, r);
            return;
        }
        final int n = guessed;
        new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("题型无法识别")
                .setMessage("有 " + n + " 道题的「题型」列无法识别，已先按选项和答案猜了类型。\n\n"
                        + "要统一指定一种题型，还是逐条指定？")
                .setPositiveButton("统一指定", (d, w) -> pickOneTypeForAll(r, n, () -> showResult(fileName, tmpFile, r)))
                .setNegativeButton("逐条指定", (d, w) -> askTypeOneByOne(fileName, tmpFile, r))
                .setNeutralButton("保持猜测", (d, w) -> showResult(fileName, tmpFile, r))
                .show();
    }

    private CharSequence[] typeItems() {
        CharSequence[] items = new CharSequence[QuestionType.COUNT];
        for (int i = 0; i < QuestionType.COUNT; i++) items[i] = QuestionType.NAMES[i];
        return items;
    }

    private void pickOneTypeForAll(final ParseResult r, final int count, final Runnable done) {
        new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("把这 " + count + " 道题统一归为")
                .setItems(typeItems(), (d, which) -> {
                    for (ParsedQuestion q : r.questions) {
                        if (q.typeGuessed) applyType(q, which);
                    }
                    done.run();
                })
                .setNegativeButton("取消", (d, w) -> done.run())
                .show();
    }

    private void askTypeOneByOne(final String fileName, final File tmpFile, final ParseResult r) {
        final List<ParsedQuestion> list = new ArrayList<>();
        for (ParsedQuestion q : r.questions) if (q.typeGuessed) list.add(q);
        askTypeAt(list, 0, fileName, tmpFile, r);
    }

    private void askTypeAt(final List<ParsedQuestion> list, final int index,
                           final String fileName, final File tmpFile, final ParseResult r) {
        if (index >= list.size()) {
            showResult(fileName, tmpFile, r);
            return;
        }
        final ParsedQuestion q = list.get(index);
        String preview = q.content == null ? "" : q.content;
        if (preview.length() > 40) preview = preview.substring(0, 40) + "…";
        new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("第 " + (index + 1) + "/" + list.size() + " 道：请选择题型")
                .setMessage(preview + "\n\n当前猜测：" + QuestionType.NAMES[q.type])
                .setItems(typeItems(), (d, which) -> {
                    applyType(q, which);
                    askTypeAt(list, index + 1, fileName, tmpFile, r);
                })
                .setNegativeButton("保持猜测", (d, w) -> askTypeAt(list, index + 1, fileName, tmpFile, r))
                .show();
    }

    /**
     * 把用户指定的题型落到解析结果上，必要时同步调整选项/答案；
     * 数据不足以支撑目标题型时退化成「其它题」，避免导入一道无法作答的题。
     */
    private void applyType(ParsedQuestion q, int type) {
        switch (type) {
            case QuestionType.SINGLE:
            case QuestionType.MULTI: {
                String letters = AnswerUtil.extractLetters(q.answer);
                if (q.options.size() >= 2 && !letters.isEmpty()) {
                    q.type = letters.length() > 1 ? QuestionType.MULTI : QuestionType.SINGLE;
                    q.answer = letters;
                } else {
                    q.type = QuestionType.OTHER;
                }
                break;
            }
            case QuestionType.JUDGE: {
                String a = AnswerUtil.normalizeJudge(q.answer);
                if (!a.isEmpty()) {
                    q.type = QuestionType.JUDGE;
                    q.options = new ArrayList<>();
                    q.options.add("正确");
                    q.options.add("错误");
                    q.answer = a;
                } else {
                    q.type = QuestionType.OTHER;
                }
                break;
            }
            case QuestionType.SORT: {
                String seq = AnswerUtil.extractSequence(q.answer);
                if (q.options.size() >= 2 && seq.length() == q.options.size()) {
                    q.type = QuestionType.SORT;
                    q.answer = seq;
                } else {
                    q.type = QuestionType.OTHER;
                }
                break;
            }
            default: {   // 填空 / 简答 / 其它：自由文本作答
                q.type = type;
                q.options = new ArrayList<>();
                break;
            }
        }
        q.typeGuessed = false;
    }

    private void showResult(String fileName, File tmpFile, ParseResult r) {
        parsing = false;
        this.tmp = tmpFile;
        this.result = r;
        String base = fileName;
        int i = base.lastIndexOf('.');
        if (i > 0) base = base.substring(0, i);
        if (base.length() > 20) base = base.substring(0, 20);
        name.setText(base);
        fileInfo.setText(fileName + "（" + r.totalRows + " 行数据）");

        chips.removeAllViews();
        chips.addView(Ui.chipColor(this, "共 " + r.questions.size() + " 题",
                Ui.attrColor(this, R.attr.qzPrimary), Ui.attrColor(this, R.attr.qzPrimaryBg)));
        int[] counts = r.counts();
        for (int t = 0; t < QuestionType.COUNT; t++) {
            if (counts[t] > 0) {
                chips.addView(Ui.chip(this, QuestionType.SHORT_NAMES[t] + " " + counts[t],
                        R.attr.qzTextSub, R.attr.qzChipBg));
            }
        }
        if (r.warnings.size() > 0) {
            warnings.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder("⚠ 跳过 " + r.warnings.size() + " 行：");
            int shown = 0;
            for (String w : r.warnings) {
                if (shown++ >= 6) break;
                sb.append("\n").append(w);
            }
            if (r.warnings.size() > 6) sb.append("\n…共 ").append(r.warnings.size()).append(" 条");
            warnings.setText(sb.toString());
        } else {
            warnings.setVisibility(View.GONE);
        }
        for (String info : r.infos) {
            fileInfo.append("\n· " + info);
        }

        preview.removeAllViews();
        int max = Math.min(30, r.questions.size());
        for (int k = 0; k < max; k++) {
            preview.addView(buildPreviewRow(r.questions.get(k)));
        }
        if (r.questions.isEmpty()) {
            TextView t = Ui.text(this, "解析后未得到有效题目，请检查文件格式", 14, R.attr.qzTextSub, 0);
            preview.addView(t);
        }

        btnImport.setEnabled(!r.questions.isEmpty());
        btnImport.setText("导入 " + r.questions.size() + " 题");
    }

    private View buildPreviewRow(ParsedQuestion q) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackground(Ui.bg(Ui.attrColor(this, R.attr.qzSurface), Ui.dp(this, 12)));
        row.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 8);
        row.setLayoutParams(lp);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(head);

        head.addView(Ui.chipColor(this, QuestionType.SHORT_NAMES[q.type],
                Ui.attrColor(this, R.attr.qzOnPrimary), typeBg(q.type)));
        if (q.category != null && !q.category.isEmpty()) {
            TextView cat = Ui.chip(this, q.category, R.attr.qzTextSub, R.attr.qzChipBg);
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            chipLp.leftMargin = Ui.dp(this, 6);
            cat.setLayoutParams(chipLp);
            head.addView(cat);
        }

        TextView content = makeText(q.content, 14, R.attr.qzText);
        content.setPadding(0, Ui.dp(this, 6), 0, 0);
        row.addView(content);

        if (!q.options.isEmpty()) {
            TextView opt = makeText(optionSummary(q), 12, R.attr.qzTextSub);
            opt.setPadding(0, Ui.dp(this, 2), 0, 0);
            row.addView(opt);
        }
        TextView ans = makeText("答案：" + q.answer, 12, R.attr.qzPrimary);
        ans.setPadding(0, Ui.dp(this, 2), 0, 0);
        row.addView(ans);
        return row;
    }

    private String optionSummary(ParsedQuestion q) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < q.options.size(); i++) {
            if (i > 0) sb.append("   ");
            sb.append((char) ('A' + i)).append(". ").append(q.options.get(i));
        }
        return sb.toString();
    }

    private TextView makeText(String s, float sp, int colorAttr) {
        TextView t = new TextView(this);
        // 富文本渲染：上下标自绘、HTML 实体解码、波浪规范化（预览与练习页显示一致）
        t.setText(com.ziruoling.quiz.util.TextUtil.rich(s));
        t.setTextSize(sp);
        t.setTextColor(Ui.attrColor(this, colorAttr));
        return t;
    }

    private int typeBg(int type) {
        switch (type) {
            case QuestionType.MULTI:
                return Ui.attrColor(this, R.attr.qzOrange);
            case QuestionType.JUDGE:
                return Ui.attrColor(this, R.attr.qzGreen);
            case QuestionType.FILL:
                return Ui.attrColor(this, R.attr.qzRed);
            case QuestionType.SHORT:
                return Ui.attrColor(this, R.attr.qzTextSub);
            case QuestionType.SORT:
                return Ui.attrColor(this, R.attr.qzPrimary);
            case QuestionType.OTHER:
                return Ui.attrColor(this, R.attr.qzOrange);
            default:
                return Ui.attrColor(this, R.attr.qzPrimary);
        }
    }

    private void doImport() {
        if (result == null || result.questions.isEmpty()) return;
        int pos = bankSpinner.getSelectedItemPosition();
        long bankId;
        String msg;
        if (pos <= 0) {
            String bankName = name.getText().toString().trim();
            if (bankName.isEmpty()) {
                Ui.toast(this, "请填写题库名称");
                return;
            }
            bankId = Db.createBank(bankName, "导入");
            msg = "导入成功：" + result.questions.size() + " 题";
        } else {
            List<Bank> banks = Db.listBanks();
            if (pos - 1 >= banks.size()) {
                Ui.toast(this, "题库状态已变化，请重试");
                return;
            }
            bankId = banks.get(pos - 1).id;
            msg = "已追加 " + result.questions.size() + " 题到「" + banks.get(pos - 1).name + "」";
        }
        Db.importQuestions(bankId, result.questions);
        if (tmp != null) tmp.delete();
        Ui.toast(this, msg);
        BankHomeActivity.start(this, bankId);
        finish();
    }

    private List<String[]> templateRows() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"题型", "题目", "选项A", "选项B", "选项C", "选项D", "选项E", "选项F",
                "答案", "解析", "分类"});
        rows.add(new String[]{"单选题", "示例：OSI 参考模型共分为几层？", "4层", "5层", "6层", "7层", "", "",
                "C", "物理层到应用层共 7 层。", "网络基础"});
        rows.add(new String[]{"多选题", "示例：下列属于传输层协议的有？", "IP", "TCP", "UDP", "ICMP", "", "",
                "BC", "IP/ICMP 属于网络层。", "网络基础"});
        rows.add(new String[]{"判断题", "示例：UDP 是面向连接的协议。", "", "", "", "", "", "",
                "错", "UDP 为无连接协议。", "网络基础"});
        rows.add(new String[]{"填空题", "示例：IPv4 地址由____位二进制数组成。", "", "", "", "", "", "",
                "32", "多个答案用 || 分隔。", "网络基础"});
        rows.add(new String[]{"简答题", "示例：简述 ping 命令的作用。", "", "", "", "", "", "",
                "检测目标主机是否可达及往返时延。", "简答题不参与自动判分。", "网络检测"});
        rows.add(new String[]{"排序题", "示例：将 TCP 三次握手步骤按发生顺序排列（点击选项排序作答）。",
                "客户端回复 ACK 确认", "服务端回复 SYN+ACK", "客户端发送 SYN 请求", "", "", "",
                "CBA", "答案为正确顺序的字母串，字母数须等于选项数。", "网络基础"});
        rows.add(new String[]{"其它题", "示例：描述图中组网连接关系（图片请在 Word 模板表格内贴图，应用内自动提取）。",
                "", "", "", "", "", "",
                "光信号→电信号转换后经路由器接入。", "其它题按参考文本作答+自评，不参与考试自动判分。", "网络与交换"});
        return rows;
    }
}