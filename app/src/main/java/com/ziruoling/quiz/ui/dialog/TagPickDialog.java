package com.ziruoling.quiz.ui.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;

import com.ziruoling.quiz.data.Db;
import com.ziruoling.quiz.data.Question;
import com.ziruoling.quiz.data.Tag;
import com.ziruoling.quiz.util.Ui;

import java.util.ArrayList;
import java.util.List;

/**
 * 标记词条选择框：多选已有词条、新建词条、长按词条改名/删除。
 * 每个题库独立一套词条（最多 10 个，含默认「其他」）。
 */
public final class TagPickDialog {

    private TagPickDialog() {}

    public static void show(final Activity a, final Question q, final Runnable onChanged) {
        final List<Tag> tags = Db.tags(q.bankId);
        final List<Long> cur = Db.questionTagIds(q.id);
        final String[] labels = new String[tags.size()];
        final boolean[] checked = new boolean[tags.size()];
        for (int i = 0; i < tags.size(); i++) {
            Tag t = tags.get(i);
            labels[i] = t.isDefault ? t.name + "（默认）" : t.name;
            checked[i] = cur.contains(t.id);
        }
        AlertDialog.Builder b = new com.ziruoling.quiz.util.Ui.AlertBuilder(a)
                .setTitle("标记（可多选，最多 " + Db.MAX_TAGS + " 个词条）")
                .setMultiChoiceItems(labels, checked, (d, w, isChecked) -> checked[w] = isChecked)
                .setPositiveButton("保存", (d, w) -> {
                    List<Long> ids = new ArrayList<>();
                    for (int i = 0; i < tags.size(); i++) {
                        if (checked[i]) ids.add(tags.get(i).id);
                    }
                    Db.setQuestionTags(q.id, ids);
                    if (onChanged != null) onChanged.run();
                })
                .setNegativeButton("取消", null)
                .setNeutralButton("新建词条", null);
        final AlertDialog dlg = b.create();
        dlg.setOnShowListener(x -> {
            dlg.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v -> {
                dlg.dismiss();
                newTag(a, q, onChanged);
            });
            dlg.getListView().setOnItemLongClickListener((parent, view, position, id) -> {
                // 先关掉当前多选框，避免下层还留着旧默认标记的旧界面
                dlg.dismiss();
                manage(a, q, tags.get(position), onChanged);
                return true;
            });
        });
        dlg.show();
    }

    private static void newTag(final Activity a, final Question q, final Runnable onChanged) {
        InputDialog.show(a, "新建词条", "", "词条名称", name -> {
            Tag t = Db.addTag(q.bankId, name);
            if (t == null) {
                Ui.toast(a, "已到上限（最多 " + Db.MAX_TAGS + " 个）或名称重复");
            }
            show(a, q, onChanged);
        });
    }

    /** 词条长按菜单命令（用 ID 分发，不用文案字符串） */
    private static final int CMD_DEFAULT = 1;
    private static final int CMD_RENAME = 2;
    private static final int CMD_DELETE = 3;

    private static void manage(final Activity a, final Question q, final Tag t,
                               final Runnable onChanged) {
        final java.util.List<CharSequence> items = new java.util.ArrayList<>();
        final java.util.List<Integer> codes = new java.util.ArrayList<>();
        final boolean builtin = Db.isBuiltinTag(t.id);
        if (!t.isDefault) {
            items.add("设为默认");
            codes.add(CMD_DEFAULT);
        }
        if (!builtin) {
            items.add("重命名");
            codes.add(CMD_RENAME);
            items.add("删除");
            codes.add(CMD_DELETE);
        }
        if (items.isEmpty()) {
            Ui.toast(a, "内置词条不可改名或删除");
            return;
        }
        new com.ziruoling.quiz.util.Ui.AlertBuilder(a)
                .setTitle(t.name)
                .setItems(items.toArray(new CharSequence[0]), (d, w) -> {
                    switch (codes.get(w)) {
                        case CMD_DEFAULT:
                            Db.setDefaultTag(q.bankId, t.id);
                            Ui.toast(a, "已把「" + t.name + "」设为默认词条");
                            if (onChanged != null) onChanged.run();
                            show(a, q, onChanged);
                            break;
                        case CMD_RENAME:
                            InputDialog.show(a, "重命名词条", t.name, "词条名称", name -> {
                                if (Db.renameTag(t.id, name) && onChanged != null) onChanged.run();
                                show(a, q, onChanged);
                            });
                            break;
                        default:
                            deleteTag(a, q, t, onChanged);
                            break;
                    }
                })
                .show();
    }

    private static void deleteTag(final Activity a, final Question q, final Tag t,
                                  final Runnable onChanged) {
        if (Db.isBuiltinTag(t.id)) {
            Ui.toast(a, "内置词条不能删除，可以重命名");
            return;
        }
        int n = Db.tagUseCount(t.id);
        String extra = t.isDefault ? "\n该词条是默认词条，删除后默认会回到内置词条。" : "";
        new com.ziruoling.quiz.util.Ui.AlertBuilder(a)
                .setTitle("删除词条「" + t.name + "」")
                .setMessage((n > 0
                        ? "有 " + n + " 道题使用了该词条，删除后会取消这些题的这个标记（题目不会被删除）。"
                        : "确定删除该词条？（题目不会被删除）") + extra)
                .setPositiveButton("取消标记并删除词条", (dd, ww) -> {
                    Db.deleteTag(t.id);
                    if (onChanged != null) onChanged.run();
                    show(a, q, onChanged);
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
