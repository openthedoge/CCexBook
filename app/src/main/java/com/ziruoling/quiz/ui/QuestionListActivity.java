package com.ziruoling.quiz.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.ziruoling.quiz.R;
import com.ziruoling.quiz.core.QuestionType;
import com.ziruoling.quiz.data.Db;
import com.ziruoling.quiz.data.Question;
import com.ziruoling.quiz.util.ShareUtil;
import com.ziruoling.quiz.util.Ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 题目列表：全部浏览 / 错题本 / 收藏 / 疑错标记 / 搜索 共用 */
public class QuestionListActivity extends BaseActivity {

    public static final int SCOPE_ALL = 0;
    public static final int SCOPE_WRONG = 1;
    public static final int SCOPE_FAV = 2;
    public static final int SCOPE_FLAGGED = 3;

    public static final int FILTER_ALL = -1;
    public static final int FILTER_UNRESOLVED = 0;
    public static final int FILTER_RESOLVED = 1;

    private long bankId;
    private int scope;
    private int filter;
    private String titleText = "";

    private ListView list;
    private TextView countTv, actionBtn;
    private View emptyView;
    private EditText search;
    private Spinner catSpinner;
    private View catRow;
    private List<String> categories = new ArrayList<>();
    private String category = null;
    private int typeMask = -1;
    /** 标记页勾选的词条（多选，命中任一即显示） */
    private java.util.List<Long> selectedTags = new java.util.ArrayList<>();
    private String query = "";
    private List<Long> ids = new ArrayList<>();
    private QuestionAdapter adapter;
    private Map<Long, String> bankNames = new HashMap<>();

    public static void start(Context c, long bankId, int scope, int filter, String title) {
        Intent it = new Intent(c, QuestionListActivity.class);
        it.putExtra("bank", bankId);
        it.putExtra("scope", scope);
        it.putExtra("filter", filter);
        it.putExtra("title", title == null ? "" : title);
        c.startActivity(it);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_question_list);

        bankId = getIntent().getLongExtra("bank", 0);
        scope = getIntent().getIntExtra("scope", SCOPE_ALL);
        filter = getIntent().getIntExtra("filter", FILTER_ALL);
        titleText = getIntent().getStringExtra("title");
        if (titleText == null) titleText = "";

        ((TextView) findViewById(R.id.title)).setText(titleText);
        countTv = findViewById(R.id.countTv);
        list = findViewById(R.id.list);
        emptyView = findViewById(R.id.emptyView);
        search = findViewById(R.id.search);
        actionBtn = findViewById(R.id.actionBtn);
        catRow = findViewById(R.id.catRow);
        catSpinner = findViewById(R.id.catSpinner);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString();
                load();
            }
        });

        buildChips();
        buildCategoryRow();
        buildTagChips();
        load();
    }

    /**
     * 筛选条下标 → 题型掩码：0=全部(-1)，1..7 对应 {@code 1 << QuestionType}。
     * 早先这里直接用了下标减一（0..6）当掩码，导致「点多选出单选」这类错位。
     */
    static int chipMask(int chipIndex) {
        return chipIndex <= 0 ? -1 : (1 << (chipIndex - 1));
    }

    private void buildChips() {
        List<TextView> chips = new ArrayList<>();
        chips.add(Ui.chip(this, "全部", R.attr.qzPrimary, R.attr.qzPrimaryBg));
        for (int i = 0; i < QuestionType.COUNT; i++) {
            chips.add(Ui.chip(this, QuestionType.SHORT_NAMES[i], R.attr.qzTextSub, R.attr.qzChipBg));
        }
        // 全部题型 + 题型 chips
        LinearLayout box = findViewById(R.id.chips);
        box.removeAllViews();
        for (int i = 0; i < chips.size(); i++) {
            TextView t = chips.get(i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(this, 6);
            t.setLayoutParams(lp);
            final int type = chipMask(i);
            final int idx = i;
            t.setOnClickListener(v -> {
                typeMask = type;
                refreshChipStyle(box, idx);
                load();
            });
            box.addView(t);
        }
        // 错题本特有：未掌握/已掌握
        if (scope == SCOPE_WRONG) {
            TextView all = Ui.chip(this, "全部状态", R.attr.qzTextSub, R.attr.qzChipBg);
            TextView un = Ui.chip(this, "未掌握", R.attr.qzTextSub, R.attr.qzChipBg);
            TextView re = Ui.chip(this, "已掌握", R.attr.qzTextSub, R.attr.qzChipBg);
            all.setId(901);
            un.setId(902);
            re.setId(903);
            all.setOnClickListener(v -> {
                filter = FILTER_ALL;
                load();
            });
            un.setOnClickListener(v -> {
                filter = FILTER_UNRESOLVED;
                load();
            });
            re.setOnClickListener(v -> {
                filter = FILTER_RESOLVED;
                load();
            });
            box.addView(all);
            box.addView(un);
            box.addView(re);
        }
        refreshChipStyle(box, 0);
    }

    private void refreshChipStyle(LinearLayout box, int activeIndex) {
        for (int i = 0; i < box.getChildCount(); i++) {
            TextView t = (TextView) box.getChildAt(i);
            t.setTextColor(Ui.attrColor(this, i == activeIndex ? R.attr.qzPrimary : R.attr.qzTextSub));
            t.setBackground(Ui.bg(Ui.attrColor(this, i == activeIndex ? R.attr.qzPrimaryBg : R.attr.qzChipBg),
                    Ui.dp(this, 14)));
        }
    }

    private void buildCategoryRow() {
        if (bankId <= 0) {
            catRow.setVisibility(View.GONE);
            return;
        }
        categories = Db.categories(bankId);
        if (categories.isEmpty()) {
            catRow.setVisibility(View.GONE);
            return;
        }
        catRow.setVisibility(View.VISIBLE);
        List<String> items = new ArrayList<>();
        items.add("全部分类");
        items.addAll(categories);
        ArrayAdapter<String> aa = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        aa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        catSpinner.setAdapter(aa);
        catSpinner.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                category = pos == 0 ? null : items.get(pos);
                load();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> p) {
            }
        });
    }

    /** 长按题目：编辑 / 删除 */
    private void showQuestionMenu(final long qid) {
        new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setItems(new CharSequence[]{"编辑题目", "删除题目"}, (d, w) -> {
                    if (w == 0) {
                        EditQuestionActivity.start(this, qid, 0);
                    } else {
                        com.ziruoling.quiz.ui.dialog.ConfirmDialog.showDanger(this, "删除题目",
                                "将永久删除这道题及其作答记录；该题的收藏 / 标记 / 笔记会一起删除；后面的题目会自动前移。确定删除？",
                                () -> {
                                    Db.deleteQuestion(qid);
                                    load();
                                    Ui.toast(this, "已删除");
                                });
                    }
                })
                .show();
    }

    /** 标记页：勾选词条（多选，命中任一即可），与题型/分类筛选叠加 */
    private void pickTags() {
        final java.util.List<com.ziruoling.quiz.data.Tag> tags = Db.tags(bankId);
        if (tags.isEmpty()) return;
        final boolean[] checked = new boolean[tags.size()];
        String[] labels = new String[tags.size()];
        for (int i = 0; i < tags.size(); i++) {
            labels[i] = tags.get(i).name;
            checked[i] = selectedTags.contains(tags.get(i).id);
        }
        new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("按标记词条筛选（可多选）")
                .setMultiChoiceItems(labels, checked, (d, w, c) -> checked[w] = c)
                .setPositiveButton("确定", (d, w) -> {
                    selectedTags.clear();
                    for (int i = 0; i < tags.size(); i++) {
                        if (checked[i]) selectedTags.add(tags.get(i).id);
                    }
                    load();
                })
                .setNeutralButton("全部清除", (d, w) -> {
                    selectedTags.clear();
                    load();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 标记页：词条筛选条（与题型筛选并列，可多选，选「全部」清空） */
    private void buildTagChips() {
        final View row = findViewById(R.id.tagChipsRow);
        final android.widget.LinearLayout box = findViewById(R.id.tagChips);
        box.removeAllViews();
        if (scope != SCOPE_FLAGGED || bankId <= 0) {
            row.setVisibility(View.GONE);
            return;
        }
        row.setVisibility(View.VISIBLE);
        final java.util.List<com.ziruoling.quiz.data.Tag> tags = Db.tags(bankId);
        java.util.List<String> names = new java.util.ArrayList<>();
        names.add("全部");
        for (com.ziruoling.quiz.data.Tag t : tags) names.add(t.name);
        for (int i = 0; i < names.size(); i++) {
            final int idx = i;
            TextView chip = Ui.chip(this, names.get(i), R.attr.qzTextSub, R.attr.qzChipBg);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(this, 6);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                if (idx == 0) {
                    selectedTags.clear();
                } else {
                    long id = tags.get(idx - 1).id;
                    if (selectedTags.contains(id)) selectedTags.remove(Long.valueOf(id));
                    else selectedTags.add(id);
                }
                refreshTagChipStyle(box, tags);
                load();
            });
            box.addView(chip);
        }
        refreshTagChipStyle(box, tags);
    }

    private void refreshTagChipStyle(android.widget.LinearLayout box,
                                     java.util.List<com.ziruoling.quiz.data.Tag> tags) {
        for (int i = 0; i < box.getChildCount(); i++) {
            boolean active = i == 0 ? selectedTags.isEmpty()
                    : selectedTags.contains(tags.get(i - 1).id);
            TextView t = (TextView) box.getChildAt(i);
            t.setTextColor(Ui.attrColor(this, active ? R.attr.qzPrimary : R.attr.qzTextSub));
            t.setBackground(Ui.bg(Ui.attrColor(this, active ? R.attr.qzPrimaryBg : R.attr.qzChipBg),
                    Ui.dp(this, 14)));
        }
    }

    private void load() {        int wrongFilter;
        boolean favOnly;
        boolean flaggedOnly;
        switch (scope) {
            case SCOPE_WRONG:
                wrongFilter = filter;
                favOnly = false;
                flaggedOnly = false;
                break;
            case SCOPE_FAV:
                wrongFilter = -1;
                favOnly = true;
                flaggedOnly = false;
                break;
            case SCOPE_FLAGGED:
                wrongFilter = -1;
                favOnly = false;
                flaggedOnly = false;
                break;
            default:
                wrongFilter = -1;
                favOnly = false;
                flaggedOnly = false;
                break;
        }
        if (scope == SCOPE_FLAGGED) {
            // 标记页：按勾选的词条筛题（与题型/分类筛选叠加）
            ids = Db.listIdsByTags(bankId, typeMask, category, selectedTags);
        } else {
            ids = Db.listIds(bankId, typeMask, category, wrongFilter, favOnly, flaggedOnly, query);
        }
        adapter = new QuestionAdapter();
        list.setAdapter(adapter);
        countTv.setText("共 " + ids.size() + " 题");
        emptyView.setVisibility(ids.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(ids.isEmpty() ? View.GONE : View.VISIBLE);

        // 快捷练习按钮
        if (scope == SCOPE_WRONG) {
            int un = Db.wrongCount(bankId, 0);
            actionBtn.setText("重做未掌握 " + un + " 题");
            actionBtn.setOnClickListener(v -> {
                List<Long> wids = Db.wrongIds(bankId, 0);
                PracticeActivity.startPractice(this, bankId, wids, 0,
                        PracticeActivity.MODE_WRONG, "错题重练", false);
            });
        } else if (scope == SCOPE_ALL) {
            actionBtn.setText("练习全部 " + ids.size() + " 题");
            actionBtn.setOnClickListener(v -> PracticeActivity.startPractice(this, bankId, ids, 0,
                    PracticeActivity.MODE_PRACTICE, titleText, false));
        } else if (scope == SCOPE_FAV) {
            actionBtn.setText("练习全部收藏");
            actionBtn.setOnClickListener(v -> PracticeActivity.startPractice(this, bankId, ids, 0,
                    PracticeActivity.MODE_PRACTICE, "收藏练习", false));
        } else {
            actionBtn.setText("练习全部 " + ids.size() + " 题");
            actionBtn.setOnClickListener(v -> PracticeActivity.startPractice(this, bankId, ids, 0,
                    PracticeActivity.MODE_PRACTICE, "标记练习", false));
        }
        actionBtn.setVisibility(View.VISIBLE);

        // 收藏夹专属：背题模式浏览（答案默认隐藏，先回忆再看，复用背题显隐按钮）
        TextView btnRecite = findViewById(R.id.btnRecite);
        if (scope == SCOPE_FAV && !ids.isEmpty()) {
            btnRecite.setVisibility(View.VISIBLE);
            btnRecite.setOnClickListener(v -> {
                // 固定种子乱序（同一收藏集顺序稳定，避免顺序记忆）
                long[] arr = new long[ids.size()];
                for (int i = 0; i < arr.length; i++) arr[i] = ids.get(i);
                PracticeActivity.reciteShuffle(arr, bankId);
                List<Long> shuffled = new ArrayList<>(arr.length);
                for (long id : arr) shuffled.add(id);
                PracticeActivity.startPractice(this, bankId, shuffled, 0,
                        PracticeActivity.MODE_RECITE, "收藏背题", false);
            });
        } else {
            btnRecite.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private class QuestionAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return ids.size();
        }

        @Override
        public Object getItem(int position) {
            return ids.get(position);
        }

        @Override
        public long getItemId(int position) {
            return ids.get(position);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(QuestionListActivity.this).inflate(R.layout.item_question_row, parent, false);
            }
            long qid = ids.get(position);
            Question q = Db.getQuestion(qid);
            if (q == null) return v;

            TextView typeChip = v.findViewById(R.id.typeChip);
            TextView statusTv = v.findViewById(R.id.statusTv);
            TextView bankTv = v.findViewById(R.id.bankTv);
            TextView content = v.findViewById(R.id.content);
            TextView badges = v.findViewById(R.id.badges);
            TextView btnShare = v.findViewById(R.id.btnShare);

            typeChip.setText(QuestionType.SHORT_NAMES[q.type]);
            typeChip.setBackground(Ui.bg(typeBg(q.type), Ui.dp(QuestionListActivity.this, 12)));
            typeChip.setTextColor(Ui.attrColor(QuestionListActivity.this, R.attr.qzOnPrimary));
            typeChip.setTextSize(11);

            int st = Db.lastStatus(qid);
            if (st == 1) {
                statusTv.setText("✓ 已答对");
                statusTv.setTextColor(Ui.attrColor(QuestionListActivity.this, R.attr.qzGreen));
            } else if (st == 2) {
                statusTv.setText("✗ 答错过");
                statusTv.setTextColor(Ui.attrColor(QuestionListActivity.this, R.attr.qzRed));
            } else {
                statusTv.setText("未作答");
                statusTv.setTextColor(Ui.attrColor(QuestionListActivity.this, R.attr.qzTextSub));
            }

            if (bankId <= 0) {
                String bn = bankNames.get(q.bankId);
                if (bn == null) {
                    bn = bankName(q.bankId);
                    bankNames.put(q.bankId, bn);
                }
                bankTv.setText(bn);
                bankTv.setVisibility(View.VISIBLE);
            } else {
                bankTv.setText("");
            }

            content.setText(com.ziruoling.quiz.util.TextUtil.rich(
                    (q.imgPaths != null && !q.imgPaths.isEmpty() ? "[图] " : "") + q.content));
            StringBuilder b = new StringBuilder();
            if (Db.isFav(qid)) b.append("★ 收藏  ");
            // 标记词条：一个显示名称，多个只显示数量
            java.util.List<com.ziruoling.quiz.data.Tag> qTags = Db.questionTags(qid);
            if (qTags.size() == 1) {
                String nm = qTags.get(0).name;
                if (nm.length() <= 6) b.append("⚑ ").append(nm).append("  ");
                else b.append("⚑1  ");
            } else if (qTags.size() > 1) {
                b.append("⚑").append(qTags.size()).append("  ");
            }
            badges.setText(b.toString());
            badges.setVisibility(b.length() == 0 ? View.GONE : View.VISIBLE);

            v.setOnClickListener(vv -> PracticeActivity.startPractice(QuestionListActivity.this,
                    bankId, ids, position, scope == SCOPE_WRONG ? PracticeActivity.MODE_WRONG
                            : PracticeActivity.MODE_PRACTICE, titleText, false));
            // 非常规操作：长按 → 编辑 / 删除（删除后题序自动前移）
            v.setOnLongClickListener(vv -> {
                showQuestionMenu(qid);
                return true;
            });
            // 分享本道题目：二选一（不含答案 / 含答案+解析）
            btnShare.setOnClickListener(vv -> ShareUtil.showChooser(QuestionListActivity.this,
                    q, bankName(q.bankId), position + 1, ids.size(),
                    q.options, q.answer, null, null));
            return v;
        }
    }

    private String bankName(long bankId) {
        for (com.ziruoling.quiz.data.Bank b : Db.listBanks()) {
            if (b.id == bankId) return b.name;
        }
        return "题库";
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
}