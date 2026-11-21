package com.ziruoling.quiz.ui.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.ziruoling.quiz.util.Ui;

/** 单行输入对话框 */
public final class InputDialog {

    public interface Callback {
        void onOk(String text);
    }

    private InputDialog() {}

    public static void show(Context c, String title, String initial, String hint, Callback ok) {
        show(c, title, initial, hint, InputType.TYPE_CLASS_TEXT, ok);
    }

    /** 纯数字输入（键盘只出数字，粘贴非数字也会被过滤） */
    public static void showNumber(Context c, String title, String hint, Callback ok) {
        show(c, title, "", hint, InputType.TYPE_CLASS_NUMBER, ok);
    }

    public static void show(Context c, String title, String initial, String hint, int inputType,
                            Callback ok) {
        EditText et = new EditText(c);
        et.setInputType(inputType);
        if (inputType == InputType.TYPE_CLASS_NUMBER) {
            et.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789"));
        }
        et.setText(initial == null ? "" : initial);
        et.setHint(hint == null ? "" : hint);
        et.setSelectAllOnFocus(true);
        et.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));
        LinearLayout wrap = new LinearLayout(c);
        wrap.setPadding(Ui.dp(c, 24), Ui.dp(c, 12), Ui.dp(c, 24), 0);
        wrap.addView(et);
        new com.ziruoling.quiz.util.Ui.AlertBuilder(c)
                .setTitle(title)
                .setView(wrap)
                .setPositiveButton("确定", (d, w) -> {
                    if (ok != null) ok.onOk(et.getText().toString().trim());
                })
                .setNegativeButton("取消", null)
                .show();
    }
}