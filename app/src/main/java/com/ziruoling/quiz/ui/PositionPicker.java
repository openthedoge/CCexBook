package com.ziruoling.quiz.ui;

import android.app.Activity;
import android.app.AlertDialog;

import com.ziruoling.quiz.data.Db;
import com.ziruoling.quiz.ui.dialog.InputDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * 选择「分类 + 位置」的两步对话框：先选分类（可新建），再输入第几题之前（留空=末尾）。
 */
final class PositionPicker {

    interface Callback {
        void onPicked(String category, int posBefore);
    }

    private PositionPicker() {}

    /** @param title 对话框标题，如「插入到哪」「移动到…」 */
    static void show(final Activity a, final long bankId, String title, final Callback cb) {
        show(a, bankId, title, null, cb);
    }

    static void show(final Activity a, final long bankId, String title, final Runnable onCancel,
                     final Callback cb) {
        final List<String> cats = new ArrayList<>(Db.categoryOrder(bankId));
        final String NEW = "（新建分类…）";
        cats.add(NEW);
        new com.ziruoling.quiz.util.Ui.AlertBuilder(a)
                .setTitle(title)
                .setItems(cats.toArray(new String[0]), (d, which) -> {
                    final String picked = cats.get(which);
                    if (NEW.equals(picked)) {
                        InputDialog.show(a, "新建分类", "", "分类名称", name -> {
                            String c = name == null ? "" : name.trim();
                            if (!c.isEmpty()) askPos(a, bankId, c, onCancel, cb);
                        });
                    } else {
                        askPos(a, bankId, picked, onCancel, cb);
                    }
                })
                .setNegativeButton("取消", (d, w) -> {
                    if (onCancel != null) onCancel.run();
                })
                .show();
    }

    private static void askPos(final Activity a, final long bankId, final String category,
                               final Runnable onCancel, final Callback cb) {
        final int n = Db.categoryCount(bankId, category);
        InputDialog.showNumber(a, "插入到「" + category + "」第几题之前", "1 - " + (n + 1) + "（留空=末尾）",
                input -> {
                    String digits = input == null ? "" : input.replaceAll("[^0-9]", "");
                    int pos;
                    if (digits.isEmpty()) {
                        pos = n;
                    } else {
                        int v;
                        try {
                            v = Integer.parseInt(digits);
                        } catch (NumberFormatException e) {
                            v = n + 1;
                        }
                        pos = Math.max(0, Math.min(n, v - 1));
                    }
                    cb.onPicked(category, pos);
                });
    }
}
