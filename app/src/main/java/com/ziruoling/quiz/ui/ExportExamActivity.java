package com.ziruoling.quiz.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.ziruoling.quiz.R;
import com.ziruoling.quiz.core.DocxWriter;
import com.ziruoling.quiz.core.QuestionType;
import com.ziruoling.quiz.data.Db;
import com.ziruoling.quiz.data.Question;
import com.ziruoling.quiz.util.Ui;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 试卷导出：按题型/分类抽题（随机或按题序），生成 Word 试卷（.docx），
 * 可附参考答案与解析页。看图题/带图题在卷面标注（图片请在应用内查看）。
 */
public class ExportExamActivity extends BaseActivity {

    private static final String EXTRA_BANK = "bank";
    private static final int REQ_SAVE = 401;

    public static void start(Context c, long bankId) {
        Intent it = new Intent(c, ExportExamActivity.class);
        it.putExtra(EXTRA_BANK, bankId);
        c.startActivity(it);
    }

    private long bankId;
    private int[] counts;
    private final EditText[] countEds = new EditText[QuestionType.COUNT];
    private EditText paperName;
    private Spinner category;
    private Switch random, withAnswers;
    private TextView totalTv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_export_exam);

        bankId = getIntent().getLongExtra(EXTRA_BANK, 0);
        counts = Db.typeCounts(bankId);
        paperName = findViewById(R.id.paperName);
        category = findViewById(R.id.category);
        random = findViewById(R.id.random);
        withAnswers = findViewById(R.id.withAnswers);
        totalTv = findViewById(R.id.totalTv);
        withAnswers.setChecked(true);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnFillAll).setOnClickListener(v -> fillAll());
        findViewById(R.id.btnExport).setOnClickListener(v -> export());

        String bankName = "";
        for (com.ziruoling.quiz.data.Bank b : Db.listBanks()) {
            if (b.id == bankId) bankName = b.name;
        }
        paperName.setText(bankName.isEmpty() ? "试卷" : bankName + " 试卷");

        List<String> cats = new ArrayList<>();
        cats.add(com.ziruoling.quiz.data.Db.ALL_CATEGORY);
        cats.addAll(Db.categories(bankId));
        ArrayAdapter<String> aa = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, cats);
        aa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        category.setAdapter(aa);
        category.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateTotal();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        buildRows();
        updateTotal();
    }

    private void buildRows() {
        LinearLayout typeRows = findViewById(R.id.typeRows);
        typeRows.removeAllViews();
        for (int t = 0; t < QuestionType.COUNT; t++) {
            if (counts[t] <= 0) continue;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Ui.dp(this, 6), 0, 0);
            TextView label = Ui.text(this, QuestionType.NAMES[t], 14, R.attr.qzText, 0);
            row.addView(label);
            EditText et = new EditText(this);
            et.setText("0");
            et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            et.setGravity(Gravity.CENTER);
            et.setTextSize(14);
            et.setTextColor(Ui.attrColor(this, R.attr.qzText));
            et.setBackground(Ui.bgStroke(this, Ui.attrColor(this, R.attr.qzSurface),
                    Ui.attrColor(this, R.attr.qzStroke), Ui.dp(this, 8)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 38));
            lp.leftMargin = Ui.dp(this, 8);
            et.setLayoutParams(lp);
            row.addView(et);
            TextView hint = Ui.text(this, "（共 " + counts[t] + " 题）", 12, R.attr.qzTextSub, 0);
            hint.setPadding(Ui.dp(this, 8), 0, 0, 0);
            row.addView(hint);
            typeRows.addView(row);
            countEds[t] = et;
            final int type = t;
            et.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void onTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {
                    updateTotal();
                }
            });
        }
    }

    private void fillAll() {
        for (int t = 0; t < QuestionType.COUNT; t++) {
            if (countEds[t] != null) countEds[t].setText(String.valueOf(counts[t]));
        }
        updateTotal();
    }

    private int parseCount(int t) {
        if (countEds[t] == null) return 0;
        try {
            return Math.min(counts[t], Math.max(0, Integer.parseInt(countEds[t].getText().toString().trim())));
        } catch (Exception e) {
            return 0;
        }
    }

    private String selectedCategory() {
        Object sel = category.getSelectedItem();
        String cat = sel == null ? null : sel.toString();
        return com.ziruoling.quiz.data.Db.ALL_CATEGORY.equals(cat) ? null : cat;
    }

    private void updateTotal() {
        int total = 0;
        for (int t = 0; t < QuestionType.COUNT; t++) total += parseCount(t);
        totalTv.setText("共 " + total + " 题" + (total > 0 ? "，点下方按钮导出" : "（请设置各题型抽题数量）"));
    }

    /** 组卷：按题型抽题合并 */
    private List<Question> buildPaper() {
        List<Question> out = new ArrayList<>();
        boolean rnd = random.isChecked();
        String cat = selectedCategory();
        for (int t = 0; t < QuestionType.COUNT; t++) {
            int n = parseCount(t);
            if (n <= 0) continue;
            List<Long> pool = Db.questionIdsByFilter(bankId, 1 << t, cat, rnd);
            if (rnd) {
                Collections.shuffle(pool, new Random(System.currentTimeMillis() + t));
            }
            int take = Math.min(n, pool.size());
            for (int i = 0; i < take; i++) {
                Question q = Db.getQuestion(pool.get(i));
                if (q != null) out.add(q);
            }
        }
        return out;
    }

    private void export() {
        List<Question> qs = buildPaper();
        if (qs.isEmpty()) {
            Ui.toast(this, "请先设置抽题数量（至少 1 题）");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        String name = paperName.getText().toString().trim();
        if (name.isEmpty()) name = "试卷";
        intent.putExtra(Intent.EXTRA_TITLE, BankExportHelper.safeFileName(name, ".docx"));
        startActivityForResult(intent, REQ_SAVE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_SAVE || resultCode != Activity.RESULT_OK
                || data == null || data.getData() == null) return;
        exportDocx(data.getData());
    }

    /** 拼 OOXML 并写文件（图片内嵌，放后台线程避免大卷卡界面） */
    private void exportDocx(final Uri uri) {
        final List<Question> qs = buildPaper();
        if (qs.isEmpty()) {
            Ui.toast(this, "组卷为空，请重试");
            return;
        }
        final boolean withAns = withAnswers.isChecked();
        String n = paperName.getText().toString().trim();
        final String name = n.isEmpty() ? "试卷" : n;
        Ui.toast(this, "正在导出试卷…");
        new Thread(() -> {
            String err = null;
            int count = 0;
            try {
                List<DocxWriter.PaperQ> paper = new ArrayList<>();
                for (Question q : qs) {
                    DocxWriter.PaperQ p = new DocxWriter.PaperQ();
                    p.type = q.type;
                    p.content = q.content;
                    p.options = q.options;
                    p.answer = q.answer;
                    p.analysis = q.analysis;
                    p.category = q.category;
                    p.docNo = q.docNo;
                    if (q.imgPaths != null) {
                        for (String rel : q.imgPaths) {
                            byte[] bytes = Db.imageBytes(rel);
                            if (bytes != null) p.images.add(bytes);
                        }
                    }
                    p.hasImage = q.imgPaths != null && !q.imgPaths.isEmpty();
                    paper.add(p);
                }
                count = paper.size();
                OutputStream os = getContentResolver().openOutputStream(uri);
                if (os == null) throw new Exception("无法写入所选位置");
                DocxWriter.writePaper(os, name, paper, withAns);
                os.close();
            } catch (Exception e) {
                err = e.getMessage();
            }
            final String ferr = err;
            final int fc = count;
            runOnUiThread(() -> Ui.toast(this, ferr == null
                    ? "试卷已导出（" + fc + " 题，含图片），可在电脑上打开/打印"
                    : "导出失败：" + ferr));
        }, "paper-export").start();
    }
}