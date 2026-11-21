package com.ziruoling.quiz.ui.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.view.Gravity;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.ziruoling.quiz.R;
import com.ziruoling.quiz.core.QuestionType;
import com.ziruoling.quiz.data.Db;
import com.ziruoling.quiz.util.Ui;

import java.util.ArrayList;
import java.util.List;

/** 专项练习：题型 + 分类 + 顺序/随机 选择 */
public final class TypeFilterDialog {

    public interface Callback {
        void onStart(int typeMask, String category, boolean random);
    }

    private TypeFilterDialog() {}

    public static void show(Context c, long bankId, Callback cb) {
        int[] counts = Db.typeCounts(bankId);
        List<String> cats = Db.categories(bankId);

        LinearLayout wrap = new LinearLayout(c);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(Ui.dp(c, 24), Ui.dp(c, 16), Ui.dp(c, 24), Ui.dp(c, 8));

        TextView t1 = Ui.text(c, "题型（可多选）", 14, R.attr.qzText, 1);
        wrap.addView(t1);

        final List<CheckBox> boxes = new ArrayList<>();
        boolean any = false;
        for (int i = 0; i < QuestionType.COUNT; i++) {
            if (counts[i] <= 0) continue;
            CheckBox box = new CheckBox(c);
            box.setText(QuestionType.NAMES[i] + "（" + counts[i] + " 题）");
            box.setTextColor(Ui.attrColor(c, R.attr.qzText));
            box.setTextSize(14);
            box.setChecked(true);
            boxes.add(box);
            any = true;
            wrap.addView(box);
        }
        if (!any) {
            TextView t = Ui.text(c, "题库暂无题目", 13, R.attr.qzTextSub, 0);
            wrap.addView(t);
            new com.ziruoling.quiz.util.Ui.AlertBuilder(c).setTitle("专项练习").setView(wrap)
                    .setPositiveButton("确定", null).show();
            return;
        }

        TextView t2 = Ui.text(c, "分类", 14, R.attr.qzText, 1);
        t2.setPadding(0, Ui.dp(c, 10), 0, 0);
        wrap.addView(t2);

        Spinner spinner = new Spinner(c);
        List<String> catList = new ArrayList<>();
        catList.add(com.ziruoling.quiz.data.Db.ALL_CATEGORY);
        catList.addAll(cats);
        ArrayAdapter<String> aa = new ArrayAdapter<>(c, android.R.layout.simple_spinner_item, catList);
        aa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(aa);
        wrap.addView(spinner);

        TextView t3 = Ui.text(c, "练习方式", 14, R.attr.qzText, 1);
        t3.setPadding(0, Ui.dp(c, 10), 0, 0);
        wrap.addView(t3);

        RadioGroup rg = new RadioGroup(c);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rb1 = new RadioButton(c);
        rb1.setText("顺序");
        rb1.setTextColor(Ui.attrColor(c, R.attr.qzText));
        rb1.setChecked(true);
        rb1.setId(1);
        RadioButton rb2 = new RadioButton(c);
        rb2.setText("随机");
        rb2.setTextColor(Ui.attrColor(c, R.attr.qzText));
        rb2.setId(2);
        rg.addView(rb1);
        rg.addView(rb2);
        wrap.addView(rg);

        new com.ziruoling.quiz.util.Ui.AlertBuilder(c)
                .setTitle("专项练习")
                .setView(wrap)
                .setPositiveButton("开始练习", (d, w) -> {
                    int mask = 0;
                    for (CheckBox b : boxes) {
                        if (b.isChecked()) {
                            String s = b.getText().toString();
                            for (int i = 0; i < QuestionType.COUNT; i++) {
                                if (s.startsWith(QuestionType.NAMES[i])) mask |= 1 << i;
                            }
                        }
                    }
                    if (mask == 0) {
                        Ui.toast(c, "请至少选择一种题型");
                        return;
                    }
                    String category = spinner.getSelectedItem() == null ? null
                            : spinner.getSelectedItem().toString();
                    if (com.ziruoling.quiz.data.Db.ALL_CATEGORY.equals(category)) category = null;
                    boolean random = rb2.isChecked();
                    if (cb != null) cb.onStart(mask, category, random);
                })
                .setNegativeButton("取消", null)
                .show();
    }
}