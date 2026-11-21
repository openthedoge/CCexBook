package com.ziruoling.quiz.data;

import com.ziruoling.quiz.core.ParsedQuestion;
import com.ziruoling.quiz.core.QuestionType;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** 标记词条：默认词条、上限、多选关联、筛选、删除、重置题库 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TagTest {

    private long bankId;
    private long q1, q2;

    @Before
    public void setUp() {
        Db.resetForTest();
        Db.init(RuntimeEnvironment.getApplication());
        bankId = Db.createBank("标记测试", "test");
        List<ParsedQuestion> list = new ArrayList<>();
        list.add(pq("题一", "分类A"));
        list.add(pq("题二", "分类A"));
        Db.importQuestions(bankId, list);
        List<Long> ids = Db.questionIds(bankId);
        q1 = ids.get(0);
        q2 = ids.get(1);
    }

    private ParsedQuestion pq(String content, String category) {
        ParsedQuestion p = new ParsedQuestion();
        p.type = QuestionType.SINGLE;
        p.content = content;
        p.options = Arrays.asList("甲", "乙");
        p.answer = "A";
        p.category = category;
        return p;
    }

    @Test
    public void defaultTagsExist() {
        List<Tag> tags = Db.tags(bankId);
        assertEquals("新题库只有内置「其他」词条", 1, tags.size());
        assertTrue(tags.get(0).isDefault);
        assertEquals("其他", tags.get(0).name);
        assertTrue("内置词条标记", Db.isBuiltinTag(tags.get(0).id));
        assertNotNull(Db.defaultTag(bankId));
    }

    @Test
    public void builtinTagCannotBeRenamedOrDeleted() {
        Tag builtin = Db.defaultTag(bankId);
        assertFalse("内置词条不可改名", Db.renameTag(builtin.id, "别的"));
        assertEquals("其他", Db.defaultTag(bankId).name);
        assertFalse("内置词条不可删除", Db.deleteTag(builtin.id));
    }

    @Test
    public void duplicateNamesAreAllowed() {
        Tag a = Db.addTag(bankId, "其他");
        assertNotNull("允许与内置词条重名（靠 id 区分）", a);
        assertFalse("新词条不是内置", Db.isBuiltinTag(a.id));
    }

    @Test
    public void singleTapTogglesDefaultTag() {
        Tag def = Db.defaultTag(bankId);
        assertTrue(Db.toggleQuestionTag(q1, def.id));
        assertEquals(1, Db.questionTagIds(q1).size());
        assertFalse(Db.toggleQuestionTag(q1, def.id));
        assertEquals(0, Db.questionTagIds(q1).size());
    }

    @Test
    public void multipleTagsPerQuestion() {
        Tag def = Db.defaultTag(bankId);
        Tag custom = Db.addTag(bankId, "易错");
        assertNotNull(custom);
        Db.setQuestionTags(q1, Arrays.asList(def.id, custom.id));
        assertEquals(2, Db.questionTagIds(q1).size());
        assertEquals(2, Db.questionTags(q1).size());
    }

    @Test
    public void tagLimitIsTen() {
        for (int i = 0; i < 9; i++) {
            assertNotNull("第 " + (i + 2) + " 个应能加入", Db.addTag(bankId, "标签" + i));
        }
        assertEquals(Db.MAX_TAGS, Db.tags(bankId).size());
        assertNull("超过 10 个应拒绝", Db.addTag(bankId, "第十一个"));
    }

    @Test
    public void filterByTags() {
        Tag a = Db.addTag(bankId, "A档");
        Tag b = Db.addTag(bankId, "B档");
        Db.setQuestionTags(q1, Collections.singletonList(a.id));
        Db.setQuestionTags(q2, Collections.singletonList(b.id));

        assertEquals(Collections.singletonList(q1),
                Db.listIdsByTags(bankId, -1, null, Collections.singletonList(a.id)));
        assertEquals("多选命中任一", 2,
                Db.listIdsByTags(bankId, -1, null, Arrays.asList(a.id, b.id)).size());
        assertEquals("不选词条=不筛", 2, Db.listIdsByTags(bankId, -1, null, null).size());
    }

    @Test
    public void deleteTagClearsLinks() {
        Tag custom = Db.addTag(bankId, "临时");
        Db.setQuestionTags(q1, Collections.singletonList(custom.id));
        assertEquals(1, Db.tagUseCount(custom.id));

        assertTrue(Db.deleteTag(custom.id));
        assertEquals(0, Db.questionTagIds(q1).size());
        assertEquals(0, Db.tagUseCount(custom.id));
    }

    @Test
    public void setDefaultTagSticksAndSurvivesEnsure() {
        Tag custom = Db.addTag(bankId, "1");
        Db.setDefaultTag(bankId, custom.id);
        assertEquals("默认应切到新词条", "1", Db.defaultTag(bankId).name);
        // 再次读取（会走 ensureTags）不能把默认抢回「其他」
        assertEquals("1", Db.defaultTag(bankId).name);
        assertEquals("1", Db.tags(bankId).get(0).name);
    }

    @Test
    public void deletingDefaultTagFallsBackToBuiltin() {
        Tag custom = Db.addTag(bankId, "1");
        Db.setDefaultTag(bankId, custom.id);
        assertTrue(Db.deleteTag(custom.id));
        Tag def = Db.defaultTag(bankId);
        assertNotNull(def);
        assertEquals("默认应回到内置词条", "其他", def.name);
        assertTrue(Db.isBuiltinTag(def.id));
    }

    @Test
    public void defaultTagCannotBeDeleted() {
        Tag def = Db.defaultTag(bankId);
        assertFalse(Db.deleteTag(def.id));
        assertEquals("其他", Db.defaultTag(bankId).name);
    }

    @Test
    public void resetBankClearsChosenPartsOnly() {
        Db.addRecord(bankId, q1, 0, "A", true, false);
        Db.toggleFav(bankId, q1);
        Db.toggleQuestionTag(q1, Db.defaultTag(bankId).id);
        long statsBefore = Db.globalStats()[0];

        Db.resetBank(bankId, true, false, false, false);

        assertEquals("做题记录已清", 0, Db.listBanks().get(0).recordCount);
        assertEquals("总统计不动", statsBefore, Db.globalStats()[0]);
        assertTrue("收藏保留", Db.isFav(q1));
        assertEquals("标记保留", 1, Db.questionTagIds(q1).size());
    }
}
