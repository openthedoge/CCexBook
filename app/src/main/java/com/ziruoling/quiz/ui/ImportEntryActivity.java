package com.ziruoling.quiz.ui;

import android.content.Intent;
import android.os.Bundle;

import com.ziruoling.quiz.R;
import com.ziruoling.quiz.data.Db;
import com.ziruoling.quiz.ui.dialog.InputDialog;
import com.ziruoling.quiz.util.Ui;

/** 导入题库入口：文件 / 粘贴文本 / 直接添加题目（与追加页三种方式一致） */
public class ImportEntryActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_import_entry);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.entryFile).setOnClickListener(v ->
                startActivity(new Intent(this, ImportActivity.class)));

        findViewById(R.id.entryPaste).setOnClickListener(v -> newBankThen(name -> {
            long id = Db.createBank(name, "");
            AppendQuestionsActivity.start(this, id, name, true);
            finish();
        }));

        findViewById(R.id.entryDirect).setOnClickListener(v -> newBankThen(name -> {
            long id = Db.createBank(name, "");
            long qid = Db.createDraftQuestion(id);
            if (qid <= 0) {
                Ui.toast(this, "创建题目失败，请重试");
                Db.deleteBank(id);
                return;
            }
            draftId = qid;
            draftBankId = id;
            EditQuestionActivity.start(this, qid, REQ_EDIT_DRAFT);
        }));
    }

    private static final int REQ_EDIT_DRAFT = 211;
    private long draftId;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_EDIT_DRAFT) {
            if (resultCode != RESULT_OK && draftId > 0) {
                Db.deleteQuestion(draftId);   // 取消编辑：删掉草稿题
                // 草稿删除后若题库为空，一并清掉
                if (draftBankId > 0 && Db.questionIds(draftBankId).isEmpty()) Db.deleteBank(draftBankId);
            }
            finish();
        }
    }

    private long draftBankId;

    private interface NameCallback {
        void onName(String name);
    }

    private void newBankThen(final NameCallback cb) {
        InputDialog.show(this, "新建题库", "我的题库", "题库名称", input -> {
            String name = input == null ? "" : input.trim();
            if (name.isEmpty()) return;
            cb.onName(name);
        });
    }
}
