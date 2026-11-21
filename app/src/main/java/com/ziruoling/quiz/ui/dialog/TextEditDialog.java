package com.ziruoling.quiz.ui.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.ziruoling.quiz.R;
import com.ziruoling.quiz.util.Ui;

/** 多行文本编辑对话框（编辑解析等长文本） */
public final class TextEditDialog {

    public interface Callback {
        void onOk(String text);
    }

    private TextEditDialog() {}

    public static void show(Context c, String title, String hint, String initial,
                            int minLines, Callback ok) {
        EditText et = new EditText(c);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setMinLines(Math.max(2, minLines));
        et.setGravity(Gravity.TOP | Gravity.START);
        et.setText(initial == null ? "" : initial);
        et.setHint(hint == null ? "" : hint);
        et.setTextSize(14);
        et.setTextColor(Ui.attrColor(c, R.attr.qzText));
        et.setHintTextColor(Ui.attrColor(c, R.attr.qzTextSub));
        et.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));

        ScrollView sv = new ScrollView(c);
        LinearLayout wrap = new LinearLayout(c);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(Ui.dp(c, 20), Ui.dp(c, 12), Ui.dp(c, 20), 0);
        wrap.addView(et, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        sv.addView(wrap);

        new com.ziruoling.quiz.util.Ui.AlertBuilder(c)
                .setTitle(title)
                .setView(sv)
                .setPositiveButton("保存", (d, w) -> {
                    if (ok != null) ok.onOk(et.getText().toString().trim());
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
