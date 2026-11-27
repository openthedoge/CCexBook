package com.ziruoling.quiz.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ziruoling.quiz.R;
import com.ziruoling.quiz.core.AnswerRule;
import com.ziruoling.quiz.core.QuestionType;
import com.ziruoling.quiz.data.Db;
import com.ziruoling.quiz.data.Question;
import com.ziruoling.quiz.util.Ui;

import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 题目编辑器：题型 / 题干 / 选项 / 答案 / 解析 / 图片。
 * <p>
 * 改题型时**不自动改写**已有选项与答案，只按新题型重新校验；格式不符会提示并阻止保存。
 * 保存后若题型发生变化，会按新题型重判该题历史作答。
 */
public class EditQuestionActivity extends BaseActivity {

    private static final String EXTRA_QID = "qid";
    private static final int REQ_PICK_IMAGES = 301;
    private static final int MAX_IMAGES = 9;
    private static final int MAX_OPTIONS = 6;
    private boolean imageOnly;

    private long qid;
    private Question origin;
    private int type;

    private LinearLayout typeChips, optionsWrap, optionsBox, imgBox;
    private EditText contentEt, answerEt, analysisEt;
    private TextView answerHint, btnAddOption, btnAddImage;

    private final List<String> optionTexts = new ArrayList<>();
    private final List<String> imgPaths = new ArrayList<>();
    private final List<String> removedImgs = new ArrayList<>();
    private final List<EditText> optionEts = new ArrayList<>();
    /** 切到判断题前暂存的选项，切走时还原（判断题选项固定为 正确/错误） */
    private List<String> judgeBackup;

    public static void start(Activity a, long qid, int requestCode) {
        a.startActivityForResult(new Intent(a, EditQuestionActivity.class)
                .putExtra(EXTRA_QID, qid), requestCode);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_question);

        qid = getIntent().getLongExtra(EXTRA_QID, 0);
        origin = Db.getQuestion(qid);
        if (origin == null) {
            Ui.toast(this, "题目不存在");
            finish();
            return;
        }
        type = origin.type;
        optionTexts.addAll(origin.options);
        imgPaths.addAll(origin.imgPaths);

        typeChips = findViewById(R.id.typeChips);
        optionsWrap = findViewById(R.id.optionsWrap);
        optionsBox = findViewById(R.id.optionsBox);
        imgBox = findViewById(R.id.imgBox);
        contentEt = findViewById(R.id.contentEt);
        answerEt = findViewById(R.id.answerEt);
        analysisEt = findViewById(R.id.analysisEt);
        answerHint = findViewById(R.id.answerHint);
        btnAddOption = findViewById(R.id.btnAddOption);
        btnAddImage = findViewById(R.id.btnAddImage);

        contentEt.setText(origin.content == null ? "" : origin.content);
        answerEt.setText(origin.answer == null ? "" : origin.answer);
        setupJudgeAnswer();
        analysisEt.setText(origin.analysis == null ? "" : origin.analysis);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        imageOnly = origin.imageOnly;
        final TextView swImageOnly = findViewById(R.id.swImageOnly);
        renderImageOnly(swImageOnly);
        swImageOnly.setOnClickListener(v -> {
            imageOnly = !imageOnly;
            renderImageOnly(swImageOnly);
        });
        findViewById(R.id.btnSave).setOnClickListener(v -> save());
        findViewById(R.id.btnSaveBottom).setOnClickListener(v -> save());
        // 移动到其他分类的任意位置（默认末尾）；题目 id 不变，做题记录不受影响
        findViewById(R.id.btnMove).setOnClickListener(v -> {
            final com.ziruoling.quiz.data.Question cur = Db.getQuestion(qid);
            if (cur == null) return;
            PositionPicker.show(this, cur.bankId, "移动到哪个分类？（默认末尾）", null, (cat, pos) -> {
                Db.moveQuestion(qid, cat, pos);
                Ui.toast(this, "已移动");
                finish();
            });
        });
        btnAddOption.setOnClickListener(v -> addOption());
        btnAddImage.setOnClickListener(v -> pickImages());

        buildTypeChips();
        applyTypeUi();
        renderOptions();
        renderImages();
        updateHint();

        answerEt.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                updateHint();
            }
        });
    }

    // ==================== 题型 ====================

    private void buildTypeChips() {
        typeChips.removeAllViews();
        for (int t = 0; t < QuestionType.COUNT; t++) {
            final int tt = t;
            TextView chip = new TextView(this);
            chip.setText(QuestionType.SHORT_NAMES[t]);
            chip.setTextSize(13);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(this, 8);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> switchType(tt));
            typeChips.addView(chip);
        }
        refreshTypeChips();
    }

    private void refreshTypeChips() {
        for (int i = 0; i < typeChips.getChildCount(); i++) {
            TextView chip = (TextView) typeChips.getChildAt(i);
            boolean sel = i == type;
            chip.setTextColor(Ui.attrColor(this, sel ? R.attr.qzOnPrimary : R.attr.qzTextSub));
            chip.setBackground(Ui.press(sel
                    ? Ui.bg(Ui.attrColor(this, R.attr.qzPrimary), Ui.dp(this, 14))
                    : Ui.bg(Ui.attrColor(this, R.attr.qzChipBg), Ui.dp(this, 14))));
        }
    }

    private void switchType(int t) {
        if (t == type) return;
        if (type == QuestionType.JUDGE) {
            optionTexts.clear();
            if (judgeBackup != null) optionTexts.addAll(judgeBackup);
            judgeBackup = null;
        } else {
            syncOptionsFromViews();
        }
        if (t == QuestionType.JUDGE) {
            judgeBackup = new ArrayList<>(optionTexts);
            optionTexts.clear();
            optionTexts.add("对");
            optionTexts.add("错");
        }
        type = t;
        refreshTypeChips();
        applyTypeUi();
        renderOptions();
        updateHint();
    }

    /** 选项区域只在选择类/排序/判断题显示 */
    private void applyTypeUi() {
        boolean hasOptions = type == QuestionType.SINGLE || type == QuestionType.MULTI
                || type == QuestionType.JUDGE || type == QuestionType.SORT;
        optionsWrap.setVisibility(hasOptions ? View.VISIBLE : View.GONE);
        btnAddOption.setVisibility(type == QuestionType.JUDGE ? View.GONE : View.VISIBLE);
    }

    /** 判断题：答案锁成 A/B，用两个按钮点选，避免绑定「对/错」文本 */
    private void setupJudgeAnswer() {
        if (origin.type != QuestionType.JUDGE) return;
        android.view.ViewGroup parent = (android.view.ViewGroup) answerEt.getParent();
        if (parent == null) return;
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setPadding(0, Ui.dp(this, 6), 0, 0);
        String cur = com.ziruoling.quiz.core.AnswerUtil.normalizeJudge(answerEt.getText().toString());
        if (cur.isEmpty()) cur = "A";
        final String[] picked = {cur};
        final TextView a = judgeBtn("A   对");
        final TextView b = judgeBtn("B   错");
        row.addView(a);
        row.addView(b);
        answerEt.setVisibility(android.view.View.GONE);
        parent.addView(row, parent.indexOfChild(answerEt) + 1);
        answerEt.setText(picked[0]);
        renderJudgePick(a, b, picked[0]);
        a.setOnClickListener(v -> {
            picked[0] = "A";
            answerEt.setText("A");
            renderJudgePick(a, b, "A");
        });
        b.setOnClickListener(v -> {
            picked[0] = "B";
            answerEt.setText("B");
            renderJudgePick(a, b, "B");
        });
    }

    private TextView judgeBtn(String text) {
        TextView t = Ui.text(this, text, 15, R.attr.qzPrimary, Typeface.BOLD);
        t.setGravity(android.view.Gravity.CENTER);
        t.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18), Ui.dp(this, 10));
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, Ui.dp(this, 10), 0);
        t.setLayoutParams(lp);
        return t;
    }

    private void renderJudgePick(TextView a, TextView b, String pick) {
        boolean isA = "A".equals(pick);
        a.setBackground(Ui.bg(Ui.attrColor(this, isA ? R.attr.qzPrimary : R.attr.qzChipBg), Ui.dp(this, 8)));
        b.setBackground(Ui.bg(Ui.attrColor(this, !isA ? R.attr.qzPrimary : R.attr.qzChipBg), Ui.dp(this, 8)));
        a.setTextColor(Ui.attrColor(this, isA ? R.attr.qzOnPrimary : R.attr.qzPrimary));
        b.setTextColor(Ui.attrColor(this, !isA ? R.attr.qzOnPrimary : R.attr.qzPrimary));
    }

    // ==================== 选项 ====================

    private void syncOptionsFromViews() {
        optionTexts.clear();
        for (EditText et : optionEts) {
            optionTexts.add(et.getText().toString());
        }
    }

    private void renderOptions() {
        optionsBox.removeAllViews();
        optionEts.clear();
        for (int i = 0; i < optionTexts.size(); i++) {
            addOptionRow(optionTexts.get(i), i);
        }
    }

    private void addOptionRow(String text, final int index) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 3));

        TextView label = Ui.text(this, String.valueOf((char) ('A' + index)), 14,
                R.attr.qzTextSub, Typeface.BOLD);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpLabel = new LinearLayout.LayoutParams(Ui.dp(this, 22),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        row.addView(label, lpLabel);

        EditText et = new EditText(this);
        et.setText(text);
        et.setTextSize(14);
        et.setTextColor(Ui.attrColor(this, R.attr.qzText));
        et.setHint("选项内容");
        et.setHintTextColor(Ui.attrColor(this, R.attr.qzTextSub));
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));
        et.setBackground(Ui.bg(Ui.attrColor(this, R.attr.qzSurface), Ui.dp(this, 8)));
        LinearLayout.LayoutParams lpEt = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpEt.leftMargin = Ui.dp(this, 4);
        row.addView(et, lpEt);
        optionEts.add(et);

        TextView del = Ui.text(this, "×", 18, R.attr.qzRed, Typeface.BOLD);
        del.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpDel = new LinearLayout.LayoutParams(Ui.dp(this, 32),
                ViewGroup.LayoutParams.MATCH_PARENT);
        del.setOnClickListener(v -> {
            syncOptionsFromViews();
            if (index < optionTexts.size()) {
                optionTexts.remove(index);
                renderOptions();
            }
        });
        row.addView(del, lpDel);

        optionsBox.addView(row);
    }

    private void addOption() {
        syncOptionsFromViews();
        if (optionTexts.size() >= MAX_OPTIONS) {
            Ui.toast(this, "最多 " + MAX_OPTIONS + " 个选项");
            return;
        }
        optionTexts.add("");
        renderOptions();
        if (!optionEts.isEmpty()) {
            EditText last = optionEts.get(optionEts.size() - 1);
            last.requestFocus();
        }
    }

    // ==================== 答案提示 / 校验 ====================

    private String hintFor(int t) {
        switch (t) {
            case QuestionType.SINGLE:
                return "单个字母，如 C";
            case QuestionType.MULTI:
                return "多个字母，如 ABD";
            case QuestionType.JUDGE:
                return "点选 A 对 / B 错";
            case QuestionType.FILL:
                return "参考答案；多个可接受答案用 || 分隔";
            case QuestionType.SORT:
                return "按正确顺序的字母串，字母数须等于选项数，如 CBA";
            default:
                return "参考答案文本，简答 / 其它题由作答者自评";
        }
    }

    private void updateHint() {
        String raw = answerEt.getText().toString();
        AnswerRule.Check ck = AnswerRule.check(type, raw, optionTexts.size());
        if (!ck.ok()) {
            if (raw.trim().isEmpty()) {
                answerHint.setText("⚠ " + hintFor(type));
                answerHint.setTextColor(Ui.attrColor(this, R.attr.qzTextSub));
            } else {
                answerHint.setText("⚠ " + ck.error);
                answerHint.setTextColor(Ui.attrColor(this, R.attr.qzRed));
            }
            return;
        }
        String msg = hintFor(type);
        if (ck.warning != null) msg += "\n⚠ " + ck.warning;
        answerHint.setText(msg);
        answerHint.setTextColor(ck.warning != null
                ? Ui.attrColor(this, R.attr.qzOrange)
                : Ui.attrColor(this, R.attr.qzTextSub));
    }

    // ==================== 图片 ====================

    private void pickImages() {
        int remain = MAX_IMAGES - imgPaths.size();
        if (remain <= 0) {
            Ui.toast(this, "最多 " + MAX_IMAGES + " 张图片");
            return;
        }
        Intent it = new Intent(Intent.ACTION_GET_CONTENT);
        it.setType("image/*");
        it.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(Intent.createChooser(it, "选择图片"), REQ_PICK_IMAGES);
        } catch (Exception e) {
            Ui.toast(this, "没有可用的图片选择器");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_IMAGES || resultCode != RESULT_OK || data == null) return;
        List<Uri> uris = new ArrayList<>();
        ClipData cd = data.getClipData();
        if (cd != null) {
            for (int i = 0; i < cd.getItemCount(); i++) uris.add(cd.getItemAt(i).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        int added = 0;
        for (Uri u : uris) {
            if (imgPaths.size() >= MAX_IMAGES) {
                Ui.toast(this, "最多 " + MAX_IMAGES + " 张图片");
                break;
            }
            byte[] bytes = readBytes(u);
            if (bytes == null) continue;
            String rel = Db.addImage(qid, bytes);
            if (rel == null) {
                Ui.toast(this, "仅支持 png / jpg / gif 图片");
                continue;
            }
            imgPaths.add(rel);
            added++;
        }
        if (added > 0) renderImages();
    }

    private byte[] readBytes(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
                if (bos.size() > 24 * 1024 * 1024) return null;
            }
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private void renderImages() {
        imgBox.removeAllViews();
        if (imgPaths.isEmpty()) {
            imgBox.setVisibility(View.GONE);
            return;
        }
        imgBox.setVisibility(View.VISIBLE);
        for (int i = 0; i < imgPaths.size(); i++) {
            final String rel = imgPaths.get(i);
            File f = Db.imgFile(rel);
            if (f == null || !f.isFile()) continue;

            FrameLayout wrap = new FrameLayout(this);
            LinearLayout.LayoutParams lpWrap = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 140));
            lpWrap.bottomMargin = Ui.dp(this, 8);
            wrap.setLayoutParams(lpWrap);
            wrap.setBackground(Ui.bgStroke(this, Ui.attrColor(this, R.attr.qzSurface),
                    Ui.attrColor(this, R.attr.qzStroke), Ui.dp(this, 10)));

            ImageView iv = new ImageView(this);
            iv.setImageBitmap(com.ziruoling.quiz.util.Ui.decodeSampled(f,
                    getResources().getDisplayMetrics().widthPixels));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            wrap.addView(iv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            TextView del = Ui.text(this, "×", 16, R.attr.qzOnPrimary, Typeface.BOLD);
            del.setGravity(Gravity.CENTER);
            del.setBackground(Ui.bg(Ui.attrColor(this, R.attr.qzRed), Ui.dp(this, 14)));
            FrameLayout.LayoutParams lpDel = new FrameLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 28),
                    Gravity.TOP | Gravity.END);
            lpDel.topMargin = Ui.dp(this, 6);
            lpDel.rightMargin = Ui.dp(this, 6);
            del.setOnClickListener(v -> {
                imgPaths.remove(rel);
                removedImgs.add(rel);
                renderImages();
            });
            wrap.addView(del, lpDel);

            imgBox.addView(wrap);
        }
    }

    // ==================== 保存 ====================

    private void renderImageOnly(TextView t) {
        t.setText(imageOnly ? "纯图题：开" : "纯图题：关");
        t.setTextColor(Ui.attrColor(this, imageOnly ? R.attr.qzOnPrimary : R.attr.qzPrimary));
        t.setBackground(Ui.bg(Ui.attrColor(this,
                imageOnly ? R.attr.qzPrimary : R.attr.qzChipBg), Ui.dp(this, 8)));
    }

    private void save() {
        String content = contentEt.getText().toString().trim();
        if (content.isEmpty() && !imageOnly) {
            Ui.toast(this, "题干不能为空");
            contentEt.requestFocus();
            return;
        }
        if (imageOnly && imgPaths.size() == 0) {
            Ui.toast(this, "纯图题至少需要一张图片");
            return;
        }
        syncOptionsFromViews();

        boolean needOptions = type == QuestionType.SINGLE || type == QuestionType.MULTI
                || type == QuestionType.JUDGE || type == QuestionType.SORT;
        List<String> opts = new ArrayList<>();
        for (String s : optionTexts) {
            if (s != null && !s.trim().isEmpty()) opts.add(s.trim());
        }
        if (needOptions) {
            if (imageOnly) {
                opts = new ArrayList<>();
                for (int i = 0; i < optionTexts.size(); i++) {
                    String t = optionTexts.get(i);
                    opts.add(t == null ? "" : t.trim());
                }
                while (opts.size() < 2) opts.add("");
            } else if (type == QuestionType.JUDGE) {
                opts.clear();
                opts.add("正确");
                opts.add("错误");
            } else if (!imageOnly && opts.size() < 2) {
                Ui.toast(this, "至少需要两个选项");
                return;
            }
        }
        if (type == QuestionType.SORT && opts.size() > 6) {
            Ui.toast(this, "排序题最多 6 个选项");
            return;
        }

        if (type == QuestionType.FILL && !imageOnly) {
            int blanks = com.ziruoling.quiz.core.AnswerUtil.blankCount(content);
            String raw = answerEt.getText().toString().trim();
            int segs = com.ziruoling.quiz.core.AnswerUtil.fillSegmentCount(raw);
            if (blanks > 0 && segs != blanks) {
                Ui.toast(this, "题干有 " + blanks + " 个空，答案需 " + blanks + " 段（用 | 分隔）");
                answerEt.requestFocus();
                return;
            }
        }
        AnswerRule.Check ck = AnswerRule.check(type, answerEt.getText().toString(), opts.size());
        if (!ck.ok()) {
            updateHint();
            Ui.toast(this, ck.error);
            answerEt.requestFocus();
            return;
        }

        java.util.List<String> safeImgs = new java.util.ArrayList<>();
        for (String rel : imgPaths) {
            if (rel != null && !rel.isEmpty()) safeImgs.add(rel);
        }
        Db.updateQuestion(qid, type, content, opts, ck.answer,
                analysisEt.getText().toString(), new JSONArray(safeImgs).toString(), imageOnly);
        if (type != origin.type) Db.rejudgeQuestion(qid);
        for (String rel : removedImgs) Db.deleteImageFile(rel);

        Ui.toast(this, "已保存");
        setResult(RESULT_OK);
        finish();
    }
}
