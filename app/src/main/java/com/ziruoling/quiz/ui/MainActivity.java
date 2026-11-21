package com.ziruoling.quiz.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ziruoling.quiz.R;
import com.ziruoling.quiz.core.QuestionType;
import com.ziruoling.quiz.core.XlsxWriter;
import com.ziruoling.quiz.data.Bank;
import com.ziruoling.quiz.data.Db;
import com.ziruoling.quiz.data.DemoBank;
import com.ziruoling.quiz.ui.dialog.ConfirmDialog;
import com.ziruoling.quiz.ui.dialog.InputDialog;
import com.ziruoling.quiz.util.Ui;

import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/** 首页：题库管理 + 全局入口 */
public class MainActivity extends BaseActivity {

    private static final int REQ_EXPORT = 101;
    private static final int REQ_EXPORT_DOCX = 102;

    private long exportBankId = 0;

    private ListView list;
    private View emptyView;
    private TextView statToday, statStreak, statWrong;
    private TextView btnGenerate, btnImport, btnSettings;
    private TextView quickWrong, quickFav, quickStats;
    private BankAdapter adapter;
    private Map<Long, int[]> typeCounts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        list = findViewById(R.id.list);
        emptyView = findViewById(R.id.emptyView);
        statToday = findViewById(R.id.statToday);
        statStreak = findViewById(R.id.statStreak);
        statWrong = findViewById(R.id.statWrong);
        btnGenerate = findViewById(R.id.btnGenerate);
        btnImport = findViewById(R.id.btnImport);
        btnSettings = findViewById(R.id.btnSettings);
        quickWrong = findViewById(R.id.quickWrong);
        quickFav = findViewById(R.id.quickFav);
        quickStats = findViewById(R.id.quickStats);

        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        btnImport.setOnClickListener(v -> startActivity(new Intent(this, ImportActivity.class)));
        btnGenerate.setOnClickListener(v -> generateDemo());

        quickWrong.setOnClickListener(v -> QuestionListActivity.start(this, 0, QuestionListActivity.SCOPE_WRONG,
                QuestionListActivity.FILTER_ALL, "错题本"));
        quickFav.setOnClickListener(v -> QuestionListActivity.start(this, 0, QuestionListActivity.SCOPE_FAV,
                QuestionListActivity.FILTER_ALL, "收藏夹"));
        quickStats.setOnClickListener(v -> startActivity(new Intent(this, StatsActivity.class)));
    }

    private void generateDemo() {
        ConfirmDialog.show(this, "生成示例题库",
                "将生成「网络技术培训示例」题库（34 题：单选/多选/判断/填空/简答，覆盖网络基础、光纤接入、专线产品、网络检测等分类），用于体验功能与参考导入格式。",
                () -> {
                    long bankId = Db.createBank("网络技术培训示例", "内置示例");
                    Db.importQuestions(bankId, DemoBank.build());
                    Ui.toast(MainActivity.this, "已生成示例题库");
                    refresh();
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        long[] g = Db.globalStats();
        statToday.setText(String.valueOf(Db.todayCount()));
        statStreak.setText(Db.streak() + " 天");
        statWrong.setText(String.valueOf(Db.wrongCount(0, -1)));
        typeCounts = Db.typeCountsMap();
        adapter = new BankAdapter(Db.listBanks());
        list.setAdapter(adapter);
        emptyView.setVisibility(adapter.getCount() == 0 ? View.VISIBLE : View.GONE);
        list.setVisibility(adapter.getCount() == 0 ? View.GONE : View.VISIBLE);
    }

    /** 题库行操作菜单的命令 ID（不再用标题字符串做分发，改文案不会点不动） */
    private static final int CMD_RENAME = 1;
    private static final int CMD_APPEND = 2;
    private static final int CMD_RESET = 3;
    private static final int CMD_DELETE = 4;

    /** 题库行操作：重命名 / 追加题目 / 重置题库 / 删除题库 */
    private void showBankMenu(Bank bank) {
        PopupMenu pm = new PopupMenu(this, list);
        pm.getMenu().add(0, CMD_RENAME, 0, "重命名");
        pm.getMenu().add(0, CMD_APPEND, 1, "追加题目");
        pm.getMenu().add(0, CMD_RESET, 2, "重置题库…");
        pm.getMenu().add(0, CMD_DELETE, 3, "删除题库");
        pm.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case CMD_RENAME:
                    InputDialog.show(this, "重命名题库", bank.name, "题库名称",
                            name -> {
                                if (!name.isEmpty()) {
                                    Db.renameBank(bank.id, name);
                                    refresh();
                                }
                            });
                    break;
                case CMD_APPEND:
                    AppendQuestionsActivity.start(this, bank.id, bank.name);
                    break;
                case CMD_RESET:
                    showResetBankDialog(bank);
                    break;
                case CMD_DELETE:
                    ConfirmDialog.showDanger(this, "删除题库",
                            "将删除题库「" + bank.name + "」及其全部题目、错题、记录，此操作不可恢复。确定删除？",
                            () -> {
                                Db.deleteBank(bank.id);
                                refresh();
                            });
                    break;
                default:
                    break;
            }
            return true;
        });
        pm.show();
    }

    /** 重置题库：多选要清空的内容（总统计不受影响），再二次确认 */
    private void showResetBankDialog(final Bank bank) {
        final String[] items = {"做题记录（含顺序进度）", "错题本", "标记", "收藏"};
        final boolean[] sel = new boolean[items.length];
        android.app.AlertDialog.Builder b = new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("重置题库「" + bank.name + "」")
                .setMultiChoiceItems(items, sel, (d, w, isChecked) -> sel[w] = isChecked)
                .setPositiveButton("下一步", (d, w) -> {
                    if (!sel[0] && !sel[1] && !sel[2] && !sel[3]) {
                        Ui.toast(this, "请至少选择一项");
                        return;
                    }
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < items.length; i++) {
                        if (sel[i]) sb.append("\n· ").append(items[i]);
                    }
                    ConfirmDialog.showDanger(this, "确认重置题库",
                            "将清空该题库的：" + sb + "\n\n总统计不受影响；此操作不可恢复。",
                            () -> {
                                Db.resetBank(bank.id, sel[0], sel[1], sel[2], sel[3]);
                                refresh();
                                Ui.toast(this, "已重置");
                            });
                })
                .setNeutralButton("全选", null)
                .setNegativeButton("取消", null);
        final android.app.AlertDialog dlg = b.create();
        dlg.setOnShowListener(x -> dlg.getButton(android.app.AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(v -> {
                    for (int i = 0; i < items.length; i++) {
                        sel[i] = true;
                        dlg.getListView().setItemChecked(i, true);
                    }
                }));
        dlg.show();
    }

    /** 导出二级菜单：表格 / 文档（含图片） */
    private void showExportMenu(final Bank bank) {
        new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("导出题库")
                .setItems(new CharSequence[]{
                                "导出表格（Excel，不含图片）",
                                "导出文档（Word，含图片，可改后再导入）"},
                        (d, which) -> {
                            if (which == 0) exportBank(bank);
                            else exportBankDocx(bank);
                        })
                .setNegativeButton("取消", null)
                .show();
    }

    private void exportBank(Bank bank) {
        exportBankId = bank.id;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        intent.putExtra(Intent.EXTRA_TITLE, bank.name + ".xlsx");
        startActivityForResult(intent, REQ_EXPORT);
    }

    private void exportBankDocx(Bank bank) {
        exportBankId = bank.id;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        intent.putExtra(Intent.EXTRA_TITLE, bank.name + ".docx");
        startActivityForResult(intent, REQ_EXPORT_DOCX);
    }

    /** Word 导出放后台线程：读图 + 拼 OOXML */
    private void writeDocx(final Uri uri) {
        final long bid = exportBankId;
        Bank b = Db.getBank(bid);
        final String name = b == null ? "题库" : b.name;
        Ui.toast(this, "正在导出文档…");
        new Thread(() -> {
            String err = null;
            try {
                OutputStream os = getContentResolver().openOutputStream(uri);
                if (os == null) throw new Exception("无法写入所选位置");
                BankExportHelper.writeDocx(os, name, bid);
                os.close();
            } catch (Exception e) {
                err = e.getMessage();
            }
            final String ferr = err;
            runOnUiThread(() -> Ui.toast(this,
                    ferr == null ? "已导出 Word 文档（含图片）" : "导出失败：" + ferr));
        }, "docx-export").start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null) return;
        if (requestCode == REQ_EXPORT) {
            Uri uri = data.getData();
            if (uri == null) return;
            try {
                OutputStream out = getContentResolver().openOutputStream(uri);
                if (out != null) {
                    XlsxWriter.write(out, "题库", Db.exportRows(exportBankId));
                    out.close();
                    exportBankId = 0;
                    Ui.toast(this, "已导出题库 Excel");
                }
            } catch (Exception e) {
                Ui.toast(this, "导出失败：" + e.getMessage());
            }
        } else if (requestCode == REQ_EXPORT_DOCX) {
            Uri uri = data.getData();
            if (uri != null) writeDocx(uri);
        }
    }

    private class BankAdapter extends BaseAdapter {
        private final List<Bank> banks = Db.listBanks();

        BankAdapter(List<Bank> banks) {
            this.banks.clear();
            this.banks.addAll(banks);
        }

        @Override
        public int getCount() {
            return banks.size();
        }

        @Override
        public Object getItem(int position) {
            return banks.get(position);
        }

        @Override
        public long getItemId(int position) {
            return banks.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_bank, parent, false);
            }
            Bank b = banks.get(position);
            TextView name = v.findViewById(R.id.name);
            TextView counts = v.findViewById(R.id.counts);
            TextView lastAt = v.findViewById(R.id.lastAt);
            ProgressBar progress = v.findViewById(R.id.progress);

            name.setText(b.name);
            int[] tc = typeCounts == null ? null : typeCounts.get(b.id);
            StringBuilder sb = new StringBuilder("共 ").append(b.questionCount).append(" 题");
            if (tc != null) {
                boolean first = true;
                for (int i = 0; i < QuestionType.COUNT; i++) {
                    if (tc[i] > 0) {
                        if (first) {
                            sb.append("  ·  ");
                            first = false;
                        } else {
                            sb.append("  ");
                        }
                        sb.append(QuestionType.SHORT_NAMES[i]).append(tc[i]);
                    }
                }
            }
            sb.append("  ·  已做 ").append(b.doneCount)
                    .append("  ·  正确率 ").append(b.accuracy()).append("%");
            counts.setText(sb.toString());
            lastAt.setText(b.lastAt > 0 ? "最近 " + Ui.fmtTime(b.lastAt) : "");
            progress.setProgress(Math.round(b.progress() * 100));

            v.setOnClickListener(vv -> BankHomeActivity.start(MainActivity.this, b.id));
            v.setOnLongClickListener(vv -> {
                showBankMenu(b);
                return true;
            });
            return v;
        }
    }
}