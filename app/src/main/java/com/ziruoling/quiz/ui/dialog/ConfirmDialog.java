package com.ziruoling.quiz.ui.dialog;

import android.app.AlertDialog;
import android.content.Context;

import com.ziruoling.quiz.util.Tr;
import android.content.DialogInterface;

/** 通用确认对话框 */
public final class ConfirmDialog {

    public interface Callback {
        void onOk();
    }

    private ConfirmDialog() {}

    public static void show(Context c, String title, String message, Callback ok) {
        show(c, title, message, Tr.s("确定"), ok);
    }

    public static void show(Context c, String title, String message, String okText, Callback ok) {
        new com.ziruoling.quiz.util.Ui.AlertBuilder(c)
                .setTitle(Tr.s(title))
                .setMessage(Tr.s(message))
                .setPositiveButton(Tr.s(okText), (d, w) -> {
                    if (ok != null) ok.onOk();
                })
                .setNegativeButton(Tr.s("取消"), null)
                .danger()
                .show();
    }

    /** 危险操作（红色确认键） */
    public static void showDanger(Context c, String title, String message, Callback ok) {
        com.ziruoling.quiz.util.Ui.AlertBuilder b = new com.ziruoling.quiz.util.Ui.AlertBuilder(c)
                .setTitle(Tr.s(title))
                .setMessage(Tr.s(message))
                .setPositiveButton(Tr.s("删除"), (d, w) -> {
                    if (ok != null) ok.onOk();
                })
                .setNegativeButton(Tr.s("取消"), null);
        b.danger().show();
    }
}
