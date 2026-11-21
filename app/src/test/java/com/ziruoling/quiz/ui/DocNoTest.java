package com.ziruoling.quiz.ui;

import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import com.ziruoling.quiz.R;
import com.ziruoling.quiz.core.AnswerUtil;
import com.ziruoling.quiz.core.DocxParser;
import com.ziruoling.quiz.core.DocxWriter;
import com.ziruoling.quiz.core.ParseResult;
import com.ziruoling.quiz.core.ParsedQuestion;
import com.ziruoling.quiz.core.QuestionType;
import com.ziruoling.quiz.data.Db;
import com.ziruoling.quiz.data.Prefs;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * 原题号（按篇/节重新计数）：导入时解析保存、练习页显示小标签、导出写回行首且可再导入。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class DocNoTest {

    @Before
    public void setUp() {
        Db.resetForTest();
        Prefs.resetForTest();
        Db.init(RuntimeEnvironment.getApplication());
        Prefs.init(RuntimeEnvironment.getApplication());
    }

    private long seed(String content, int docNo) {
        long bankId = Db.createBank("题号测试", "test");
        ParsedQuestion p = new ParsedQuestion();
        p.type = QuestionType.SINGLE;
        p.content = content;
        p.options = Arrays.asList("甲", "乙");
        p.answer = "A";
        p.docNo = docNo;
        Db.importQuestions(bankId, Collections.singletonList(p));
        return bankId;
    }

    private ActivityController<PracticeActivity> open(long bankId, long qid) {
        Intent it = new Intent(RuntimeEnvironment.getApplication(), PracticeActivity.class)
                .putExtra("ids", new long[]{qid})
                .putExtra("start", 0)
                .putExtra("mode", PracticeActivity.MODE_PRACTICE)
                .putExtra("bank", bankId)
                .putExtra("title", "测试")
                .putExtra("progress", false)
                .putExtra("restore", false);
        return Robolectric.buildActivity(PracticeActivity.class, it).setup();
    }

    private ParsedQuestion pq(String content, int docNo, String category) {
        ParsedQuestion p = new ParsedQuestion();
        p.type = QuestionType.SINGLE;
        p.content = content;
        p.options = Arrays.asList("甲", "乙");
        p.answer = "A";
        p.docNo = docNo;
        p.category = category;
        return p;
    }

    /** 两篇：第1篇题 0/1（无号），第2篇题 2（无号）、题 3（原文号 5） */
    private long seedMixed() {
        long bankId = Db.createBank("题号测试", "test");
        java.util.List<ParsedQuestion> list = new java.util.ArrayList<>();
        list.add(pq("题0", 0, "第1篇 X"));
        list.add(pq("题1", 0, "第1篇 X"));
        list.add(pq("题2", 0, "第2篇 Y"));
        list.add(pq("题3", 5, "第2篇 Y"));
        Db.importQuestions(bankId, list);
        return bankId;
    }

    @Test
    public void autoNumberingByCategory() {
        long bankId = seedMixed();
        List<Long> ids = Db.questionIds(bankId);
        assertEquals("第1篇第1题", 1, Db.displayNo(Db.getQuestion(ids.get(0))));
        assertEquals("第1篇第2题", 2, Db.displayNo(Db.getQuestion(ids.get(1))));
        assertEquals("换篇后重新从 1 数", 1, Db.displayNo(Db.getQuestion(ids.get(2))));
        assertEquals("原文号优先于自动号", 5, Db.displayNo(Db.getQuestion(ids.get(3))));
    }

    @Test
    public void exportUsesAutoNumbers() {
        long bankId = seedMixed();
        List<String[]> rows = Db.exportRows(bankId);
        assertEquals("1.题0", rows.get(1)[1]);
        assertEquals("2.题1", rows.get(2)[1]);
        assertEquals("1.题2", rows.get(3)[1]);
        assertEquals("5.题3", rows.get(4)[1]);
    }

    @Test
    public void badgeShowsAutoNumberWhenNoDocNo() {
        long bankId = seedMixed();
        ActivityController<PracticeActivity> c = open(bankId, Db.questionIds(bankId).get(1));
        assertEquals("2", badgeText(c.get()));
        c.destroy();
    }

    @Test
    public void badgeHiddenWithoutCategoryAndDocNo() {
        long bankId = seed("题干内容", 0);
        ActivityController<PracticeActivity> c = open(bankId, Db.questionIds(bankId).get(0));
        assertNull(badgeText(c.get()));
        c.destroy();
    }

    @Test
    public void parseNumberingRecognizesPrefixes() {
        assertEquals(5, AnswerUtil.parseNumbering("5.题干"));
        assertEquals(3, AnswerUtil.parseNumbering("（3）题干"));
        assertEquals(12, AnswerUtil.parseNumbering("12、题干"));
        assertEquals(7, AnswerUtil.parseNumbering("7．题干"));
        assertEquals("不该把正文里的数字当题号", 0, AnswerUtil.parseNumbering("100M 宽带"));
        assertEquals(0, AnswerUtil.parseNumbering("题干 5.5"));
        assertEquals(0, AnswerUtil.parseNumbering(""));
    }

    @Test
    public void importStoresDocNoAndStripsItFromContent() {
        long bankId = seed("题干内容", 5);
        long qid = Db.questionIds(bankId).get(0);
        assertEquals(5, Db.getQuestion(qid).docNo);
        assertEquals("题干内容", Db.getQuestion(qid).content);
    }

    /** 取题干里题号标签的文字；没有标签返回 null */
    private String badgeText(PracticeActivity a) {
        TextView tv = a.findViewById(R.id.content);
        CharSequence cs = tv.getText();
        if (!(cs instanceof android.text.Spanned)) return null;
        android.text.Spanned sp = (android.text.Spanned) cs;
        com.ziruoling.quiz.util.DocNoSpan[] spans =
                sp.getSpans(0, sp.length(), com.ziruoling.quiz.util.DocNoSpan.class);
        return spans.length == 0 ? null : spans[0].text();
    }

    @Test
    public void practiceShowsDocNoBadge() {
        long bankId = seed("题干内容", 5);
        ActivityController<PracticeActivity> c = open(bankId, Db.questionIds(bankId).get(0));
        assertEquals("5", badgeText(c.get()));
        c.destroy();
    }

    @Test
    public void badgeHiddenWhenNoDocNo() {
        long bankId = seed("题干内容", 0);
        ActivityController<PracticeActivity> c = open(bankId, Db.questionIds(bankId).get(0));
        assertNull(badgeText(c.get()));
        c.destroy();
    }

    @Test
    public void exportWritesNumberBackAndReimportKeepsIt() throws Exception {
        DocxWriter.PaperQ q = new DocxWriter.PaperQ();
        q.type = QuestionType.SINGLE;
        q.content = "题干内容";
        q.options = Arrays.asList("甲", "乙");
        q.answer = "A";
        q.docNo = 7;

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DocxWriter.writeBank(bos, "题库", Collections.singletonList(q));
        File f = File.createTempFile("docno", ".docx");
        f.deleteOnExit();
        Files.write(f.toPath(), bos.toByteArray());

        ParseResult r = new DocxParser().parse(f);
        assertEquals(1, r.questions.size());
        assertEquals("导出的 Word 应把题号写回行首并重新解析回 docNo", 7, r.questions.get(0).docNo);
        assertEquals("题干内容", r.questions.get(0).content);
    }

    @Test
    public void xlsxRowsIncludeDocNo() {
        long bankId = seed("题干内容", 9);
        List<String[]> rows = Db.exportRows(bankId);
        assertEquals("9.题干内容", rows.get(1)[1]);
    }
}
