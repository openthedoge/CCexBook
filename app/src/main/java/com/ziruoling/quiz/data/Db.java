package com.ziruoling.quiz.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.ziruoling.quiz.core.AnswerUtil;
import com.ziruoling.quiz.core.ParsedQuestion;
import com.ziruoling.quiz.core.QuestionType;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 本地数据库（SQLite）：题库 / 答题记录 / 错题 / 收藏 / 考试 */
public final class Db {

    /** 分类筛选的「全部」占位（仅 UI 使用，不入库） */
    public static final String ALL_CATEGORY = "全部分类";

    private static volatile SQLiteDatabase sDb;
    private static volatile Context sCtx;

    private Db() {}

    public static void init(Context context) {
        if (sDb == null) {
            synchronized (Db.class) {
                if (sDb == null) {
                    sCtx = context.getApplicationContext();
                    sDb = new OpenHelper(sCtx).getWritableDatabase();
                }
            }
        }
    }

    /** 覆盖恢复前关闭数据库，释放文件句柄，便于替换 db 文件 */
    public static void closeForRestore() {
        synchronized (Db.class) {
            if (sDb != null) {
                try {
                    sDb.close();
                } catch (Exception ignored) {
                }
                sDb = null;
            }
        }
    }

    static SQLiteDatabase db() {
        if (sDb == null) throw new IllegalStateException("Db.init 未调用");
        return sDb;
    }

    /** 仅供单元测试：释放单例（每个用例重建 Application 时调用） */
    public static void resetForTest() {        synchronized (Db.class) {
            if (sDb != null) {
                try {
                    sDb.close();
                } catch (Exception ignored) {
                }
                sDb = null;
            }
        }
    }

    private static class OpenHelper extends SQLiteOpenHelper {
        OpenHelper(Context c) {
            super(c, "ziruoling_quiz.db", null, 7);
        }

        @Override
        public void onCreate(SQLiteDatabase d) {
            d.execSQL("CREATE TABLE banks("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "name TEXT NOT NULL,"
                    + "source TEXT DEFAULT '',"
                    + "created_at INTEGER,"
                    + "last_at INTEGER DEFAULT 0)");
            d.execSQL("CREATE TABLE questions("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "bank_id INTEGER NOT NULL,"
                    + "qindex INTEGER,"
                    + "type INTEGER NOT NULL,"
                    + "content TEXT NOT NULL,"
                    + "options TEXT DEFAULT '[]',"
                    + "answer TEXT DEFAULT '',"
                    + "analysis TEXT DEFAULT '',"
                    + "category TEXT DEFAULT '',"
                    + "flagged INTEGER DEFAULT 0,"
                    + "img_paths TEXT DEFAULT '[]',"
                    + "doc_no INTEGER DEFAULT 0)");
            d.execSQL("CREATE INDEX idx_q_bank ON questions(bank_id, qindex)");
            d.execSQL("CREATE TABLE records("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "bank_id INTEGER,"
                    + "question_id INTEGER,"
                    + "mode INTEGER DEFAULT 0,"
                    + "user_answer TEXT,"
                    + "correct INTEGER DEFAULT 0,"
                    + "answered_at INTEGER)");
            d.execSQL("CREATE INDEX idx_rec_q ON records(question_id)");
            d.execSQL("CREATE INDEX idx_rec_time ON records(answered_at)");
            d.execSQL("CREATE TABLE wrongs("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "bank_id INTEGER,"
                    + "question_id INTEGER UNIQUE,"
                    + "wrong_count INTEGER DEFAULT 1,"
                    + "resolved INTEGER DEFAULT 0,"
                    + "updated_at INTEGER)");
            d.execSQL("CREATE TABLE favs("
                    + "bank_id INTEGER,"
                    + "question_id INTEGER,"
                    + "created_at INTEGER,"
                    + "PRIMARY KEY(bank_id, question_id))");
            d.execSQL("CREATE TABLE exams("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "bank_id INTEGER,"
                    + "name TEXT,"
                    + "total INTEGER,"
                    + "done INTEGER,"
                    + "correct INTEGER,"
                    + "score REAL,"
                    + "full REAL,"
                    + "duration INTEGER,"
                    + "finished_at INTEGER,"
                    + "wrong_ids TEXT DEFAULT '[]',"
                    + "detail TEXT DEFAULT '[]')");
            d.execSQL("CREATE TABLE progress("
                    + "bank_id INTEGER PRIMARY KEY,"
                    + "last_qid INTEGER,"
                    + "updated_at INTEGER)");
            createStatsTables(d);
            createTagTables(d);
        }

        /** 标记词条表：每个题库独立一套，默认「其他」不可删 */
        private void createTagTables(SQLiteDatabase d) {
            d.execSQL("CREATE TABLE IF NOT EXISTS tags("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "bank_id INTEGER NOT NULL,"
                    + "name TEXT NOT NULL,"
                    + "is_default INTEGER DEFAULT 0,"
                    + "sort INTEGER DEFAULT 0,"
                    + "builtin INTEGER DEFAULT 0)");
            d.execSQL("CREATE TABLE IF NOT EXISTS question_tags("
                    + "question_id INTEGER NOT NULL,"
                    + "tag_id INTEGER NOT NULL,"
                    + "PRIMARY KEY(question_id, tag_id))");
            d.execSQL("CREATE INDEX IF NOT EXISTS idx_qt_tag ON question_tags(tag_id)");
        }

        /** 兼容旧库：v5 起用词条表；旧数据里的 flagged=1 迁移成「疑错」词条 */
        private void migrateTags(SQLiteDatabase d) {
            createTagTables(d);
            try {
                Cursor banks = d.rawQuery("SELECT id FROM banks", null);
                while (banks.moveToNext()) {
                    ensureTagRows(d, banks.getLong(0));
                }
                banks.close();
                // 旧「疑错」标记 → 「疑错」词条
                Cursor c = d.rawQuery("SELECT q.id, q.bank_id FROM questions q WHERE q.flagged=1", null);
                while (c.moveToNext()) {
                    long bankId = c.getLong(1);
                    long tagId = tagIdByName(d, bankId, "疑错");
                    if (tagId > 0) {
                        ContentValues v = new ContentValues();
                        v.put("question_id", c.getLong(0));
                        v.put("tag_id", tagId);
                        d.insertWithOnConflict("question_tags", null, v, SQLiteDatabase.CONFLICT_IGNORE);
                    }
                }
                c.close();
            } catch (Exception ignored) {
            }
        }

        private long tagIdByName(SQLiteDatabase d, long bankId, String name) {
            Cursor c = d.rawQuery("SELECT id FROM tags WHERE bank_id=? AND name=? LIMIT 1",
                    new String[]{String.valueOf(bankId), name});
            long id = c.moveToFirst() ? c.getLong(0) : 0;
            c.close();
            return id;
        }

        /** 给题库补齐「其他」（默认）与「疑错」两条词条 */
        private void ensureTagRows(SQLiteDatabase d, long bankId) {
            String[] names = {"其他", "疑错"};
            for (int i = 0; i < names.length; i++) {
                if (tagIdByName(d, bankId, names[i]) > 0) continue;
                ContentValues v = new ContentValues();
                v.put("bank_id", bankId);
                v.put("name", names[i]);
                v.put("is_default", i == 0 ? 1 : 0);
                v.put("sort", i);
                d.insert("tags", null, v);
            }
        }

        /** 总统计账本：与做题记录解耦，删题库/清记录都不会归零 */
        private void createStatsTables(SQLiteDatabase d) {
            d.execSQL("CREATE TABLE IF NOT EXISTS stats("
                    + "id INTEGER PRIMARY KEY,"
                    + "answered INTEGER DEFAULT 0,"
                    + "correct INTEGER DEFAULT 0)");
            d.execSQL("CREATE TABLE IF NOT EXISTS stats_days("
                    + "day INTEGER PRIMARY KEY,"
                    + "answered INTEGER DEFAULT 0,"
                    + "correct INTEGER DEFAULT 0)");
            d.execSQL("CREATE TABLE IF NOT EXISTS stats_types("
                    + "type INTEGER PRIMARY KEY,"
                    + "answered INTEGER DEFAULT 0,"
                    + "correct INTEGER DEFAULT 0)");
            d.execSQL("INSERT OR IGNORE INTO stats(id, answered, correct) VALUES(1, 0, 0)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase d, int oldVersion, int newVersion) {
            if (oldVersion < 2) {
                // v2：题目携带图片（看图题 / 题干配图，来自 docx 表格内嵌图）
                try {
                    d.execSQL("ALTER TABLE questions ADD COLUMN img_paths TEXT DEFAULT '[]'");
                } catch (Exception ignored) {
                }
            }
            if (oldVersion < 3) {                // v3：总统计独立成账本；升级时用现有记录回填，历史数字不丢
                createStatsTables(d);
                try {
                    d.execSQL("INSERT OR REPLACE INTO stats(id, answered, correct) "
                            + "SELECT 1, COUNT(*), COALESCE(SUM(correct),0) FROM records");
                    d.execSQL("INSERT OR REPLACE INTO stats_days(day, answered, correct) "
                            + "SELECT answered_at / 86400000, COUNT(*), COALESCE(SUM(correct),0) "
                            + "FROM records GROUP BY answered_at / 86400000");
                    d.execSQL("INSERT OR REPLACE INTO stats_types(type, answered, correct) "
                            + "SELECT q.type, COUNT(*), COALESCE(SUM(r.correct),0) FROM records r "
                            + "JOIN questions q ON q.id = r.question_id GROUP BY q.type");
                } catch (Exception ignored) {
                }
            }
            if (oldVersion < 4) {
                // v4：题干原题号（按篇/节重新计数），0 = 未识别
                try {
                    d.execSQL("ALTER TABLE questions ADD COLUMN doc_no INTEGER DEFAULT 0");
                } catch (Exception ignored) {
                }
            }
            if (oldVersion < 5) {
                // v5：多词条标记（旧 flagged=1 → 「疑错」词条）
                migrateTags(d);
            }
            if (oldVersion < 7) {
                // v7：题目笔记（独立于题干/解析，随题保存）
                try {
                    d.execSQL("ALTER TABLE questions ADD COLUMN note TEXT DEFAULT ''");
                } catch (Exception ignored) {
                }
            }
            if (oldVersion < 6) {
                // v6：内置词条标记（「其他」不可删除、不可改名）
                try {
                    d.execSQL("ALTER TABLE tags ADD COLUMN builtin INTEGER DEFAULT 0");
                } catch (Exception ignored) {
                }
                try {
                    d.execSQL("UPDATE tags SET builtin=1 WHERE id IN"
                            + " (SELECT MIN(id) FROM tags GROUP BY bank_id)");
                } catch (Exception ignored) {
                }
            }
        }
    }

    // ==================== 题库 ====================

    public static List<Bank> listBanks() {
        List<Bank> out = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT b.id, b.name, b.source, b.created_at, b.last_at,"
                + " COALESCE(q.qc,0), COALESCE(r.done,0), COALESCE(r.corr,0), COALESCE(r.recs,0)"
                + " FROM banks b"
                + " LEFT JOIN (SELECT bank_id, COUNT(*) qc FROM questions GROUP BY bank_id) q"
                + "   ON q.bank_id = b.id"
                + " LEFT JOIN (SELECT bank_id, COUNT(DISTINCT question_id) done,"
                + "   SUM(correct) corr, COUNT(*) recs FROM records GROUP BY bank_id) r"
                + "   ON r.bank_id = b.id"
                + " ORDER BY b.last_at DESC, b.id DESC", null);
        while (c.moveToNext()) {
            Bank b = new Bank();
            b.id = c.getLong(0);
            b.name = c.getString(1);
            b.source = c.getString(2);
            b.createdAt = c.getLong(3);
            b.lastAt = c.getLong(4);
            b.questionCount = c.getInt(5);
            b.doneCount = c.getInt(6);
            b.correctCount = c.getInt(7);
            b.recordCount = c.getInt(8);
            out.add(b);
        }
        c.close();
        return out;
    }

    public static long createBank(String name, String source) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("source", source == null ? "" : source);
        v.put("created_at", System.currentTimeMillis());
        v.put("last_at", System.currentTimeMillis());
        return db().insert("banks", null, v);
    }

    public static void renameBank(long id, String name) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        db().update("banks", v, "id=?", new String[]{String.valueOf(id)});
    }

    public static void touchBank(long id) {
        ContentValues v = new ContentValues();
        v.put("last_at", System.currentTimeMillis());
        db().update("banks", v, "id=?", new String[]{String.valueOf(id)});
    }

    public static void deleteBank(long id) {
        SQLiteDatabase d = db();
        String s = String.valueOf(id);
        // 先删除题目图片文件，再删数据
        List<Long> qids = questionIds(id);
        for (long qid : qids) {
            Question q = getQuestion(qid);
            if (q != null && q.imgPaths != null) {
                for (String rel : q.imgPaths) {
                    File f = imgFile(rel);
                    if (f != null && f.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        f.delete();
                    }
                }
            }
        }
        d.beginTransaction();
        try {
            d.delete("questions", "bank_id=?", new String[]{s});
            d.delete("records", "bank_id=?", new String[]{s});
            d.delete("wrongs", "bank_id=?", new String[]{s});
            d.delete("favs", "bank_id=?", new String[]{s});
            // 防御：清掉指向本库题目的收藏残留行（favs.bank_id 与题目归属不一致的场景）
            d.execSQL("DELETE FROM favs WHERE question_id NOT IN (SELECT id FROM questions)");
            d.delete("progress", "bank_id=?", new String[]{s});
            d.delete("exams", "bank_id=?", new String[]{s});
            d.delete("banks", "id=?", new String[]{s});
            d.setTransactionSuccessful();
        } finally {
            d.endTransaction();
        }
    }

    public static Bank getBank(long id) {
        for (Bank b : listBanks()) {
            if (b.id == id) return b;
        }
        return null;
    }

    // ==================== 题目 ====================

    public static void importQuestions(long bankId, java.util.List<ParsedQuestion> list) {
        SQLiteDatabase d = db();
        d.beginTransaction();
        try {
            int idx = 0;
            Cursor c = d.rawQuery("SELECT COALESCE(MAX(qindex), -1) + 1 FROM questions WHERE bank_id=?",
                    new String[]{String.valueOf(bankId)});
            if (c.moveToFirst()) idx = c.getInt(0);
            c.close();
            for (ParsedQuestion q : list) {
                ContentValues v = new ContentValues();
                v.put("bank_id", bankId);
                v.put("qindex", idx++);
                v.put("type", q.type);
                v.put("content", q.content);
                v.put("options", new JSONArray(q.options).toString());
                v.put("answer", q.answer);
                v.put("analysis", q.analysis);
                v.put("category", q.category);
                v.put("doc_no", q.docNo);
                long rowId = d.insert("questions", null, v);
                saveImages(rowId, q);
            }
            d.setTransactionSuccessful();
        } finally {
            d.endTransaction();
        }
    }

    /** 保存题目图片到应用私有目录（仅 png/jpg/gif），返回相对路径 JSON */
    private static void saveImages(long rowId, ParsedQuestion q) {
        if (rowId <= 0 || q.images == null || q.images.isEmpty() || sCtx == null) return;
        JSONArray paths = new JSONArray();
        File dir = new File(sCtx.getFilesDir(), "imgs");
        if (!dir.exists() && !dir.mkdirs()) return;
        int n = 0;
        for (byte[] img : q.images) {
            String ext = imgExt(img);
            if (ext == null) continue;
            String name = "q" + rowId + "_" + (n++) + "." + ext;
            try {
                FileOutputStream fos = new FileOutputStream(new File(dir, name));
                fos.write(img);
                fos.close();
                paths.put("imgs/" + name);
            } catch (Exception ignored) {
            }
        }
        if (paths.length() > 0) {
            ContentValues uv = new ContentValues();
            uv.put("img_paths", paths.toString());
            db().update("questions", uv, "id=?", new String[]{String.valueOf(rowId)});
        }
    }

    /** 图片字节格式识别：返回扩展名，不支持的格式返回 null */
    static String imgExt(byte[] b) {
        if (b == null || b.length < 12) return null;
        if ((b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47) return "png";
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) return "jpg";
        if (b[0] == 0x47 && b[1] == 0x49 && b[2] == 0x46) return "gif";
        return null;
    }

    /** 图片文件（传相对路径，如 imgs/q1_0.png） */
    public static File imgFile(String rel) {
        if (sCtx == null || rel == null || rel.isEmpty()) return null;
        return new File(sCtx.getFilesDir(), rel);
    }

    public static Question getQuestion(long id) {
        Cursor c = db().query("questions", null, "id=?", new String[]{String.valueOf(id)}, null, null, null);
        Question q = null;
        if (c.moveToFirst()) q = readQuestion(c);
        c.close();
        return q;
    }

    static Question readQuestion(Cursor c) {
        Question q = new Question();
        q.id = c.getLong(c.getColumnIndexOrThrow("id"));
        q.bankId = c.getLong(c.getColumnIndexOrThrow("bank_id"));
        q.qindex = c.getInt(c.getColumnIndexOrThrow("qindex"));
        q.type = c.getInt(c.getColumnIndexOrThrow("type"));
        q.content = c.getString(c.getColumnIndexOrThrow("content"));
        q.options = parseOptions(c.getString(c.getColumnIndexOrThrow("options")));
        q.answer = c.getString(c.getColumnIndexOrThrow("answer"));
        q.analysis = c.getString(c.getColumnIndexOrThrow("analysis"));
        q.category = c.getString(c.getColumnIndexOrThrow("category"));
        q.flagged = c.getInt(c.getColumnIndexOrThrow("flagged")) == 1;
        q.imgPaths = parseOptions(c.getString(c.getColumnIndexOrThrow("img_paths")));
        int docCol = c.getColumnIndex("doc_no");
        q.docNo = docCol >= 0 ? c.getInt(docCol) : 0;
        int noteCol = c.getColumnIndex("note");
        q.note = noteCol >= 0 ? c.getString(noteCol) : "";
        return q;
    }

    static ArrayList<String> parseOptions(String json) {
        ArrayList<String> out = new ArrayList<>();
        if (json == null || json.isEmpty()) return out;
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (JSONException ignored) {
        }
        return out;
    }

    /** 全部题目 id（按题序） */
    public static List<Long> questionIds(long bankId) {
        List<Long> out = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT id FROM questions WHERE bank_id=? ORDER BY qindex, id",
                new String[]{String.valueOf(bankId)});
        while (c.moveToNext()) out.add(c.getLong(0));
        c.close();
        return out;
    }

    // ==================== 插入 / 移动题目 ====================

    /** 分类列表，按题序首次出现顺序 */
    public static List<String> categoryOrder(long bankId) {
        List<String> out = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT category, MIN(qindex) AS mi FROM questions WHERE bank_id=?"
                        + " AND category IS NOT NULL AND category<>'' GROUP BY category ORDER BY mi",
                new String[]{String.valueOf(bankId)});
        while (c.moveToNext()) out.add(c.getString(0));
        c.close();
        return out;
    }

    /** 分类首题的 qindex；分类不存在返回 -1 */
    public static int categoryStart(long bankId, String category) {
        if (category == null || category.trim().isEmpty()) return -1;
        Cursor c = db().rawQuery("SELECT MIN(qindex) FROM questions WHERE bank_id=? AND category=?",
                new String[]{String.valueOf(bankId), category});
        int v = -1;
        if (c.moveToFirst() && !c.isNull(0)) v = c.getInt(0);
        c.close();
        return v;
    }

    public static int categoryCount(long bankId, String category) {
        if (category == null || category.trim().isEmpty()) return 0;
        Cursor c = db().rawQuery("SELECT COUNT(*) FROM questions WHERE bank_id=? AND category=?",
                new String[]{String.valueOf(bankId), category});
        int v = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return v;
    }

    private static int maxQindex(long bankId) {
        Cursor c = db().rawQuery("SELECT COALESCE(MAX(qindex), -1) FROM questions WHERE bank_id=?",
                new String[]{String.valueOf(bankId)});
        int v = c.moveToFirst() ? c.getInt(0) : -1;
        c.close();
        return v;
    }

    /** 有序序列里 qindex 之前有多少题（用于把 qindex 换算成列表下标） */
    private static int indexOfQindex(long bankId, int qindex) {
        Cursor c = db().rawQuery("SELECT COUNT(*) FROM questions WHERE bank_id=? AND qindex < ?",
                new String[]{String.valueOf(bankId), String.valueOf(qindex)});
        int v = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return v;
    }

    /**
     * 把题目插入到指定分类的第 pos 题之前（pos=0 开头，pos=分类题数 表示末尾）。
     * 分类不存在时追加到全库末尾；已有题目的 qindex 会整体后移，记录不受影响。
     *
     * @return 实际插入的题数
     */
    public static int insertQuestions(long bankId, java.util.List<ParsedQuestion> items,
                                      String category, int pos) {
        return insertQuestionsIds(bankId, items, category, pos).size();
    }

    /** 同 {@link #insertQuestions}，返回新插入题目的 id（按插入顺序） */
    public static List<Long> insertQuestionsIds(long bankId, java.util.List<ParsedQuestion> items,
                                                String category, int pos) {
        List<Long> ids = new ArrayList<>();
        if (items == null || items.isEmpty()) return ids;
        SQLiteDatabase d = db();
        d.beginTransaction();
        try {
            int n = items.size();
            int start = categoryStart(bankId, category);
            int insertAt;
            if (start < 0) {
                insertAt = maxQindex(bankId) + 1;
            } else {
                int count = categoryCount(bankId, category);
                insertAt = start + Math.max(0, Math.min(count, pos));
            }
            d.execSQL("UPDATE questions SET qindex = qindex + ? WHERE bank_id=? AND qindex >= ?",
                    new Object[]{n, bankId, insertAt});
            int i = 0;
            for (ParsedQuestion q : items) {
                ContentValues v = new ContentValues();
                v.put("bank_id", bankId);
                v.put("qindex", insertAt + i++);
                v.put("type", q.type);
                v.put("content", q.content);
                v.put("options", new JSONArray(q.options).toString());
                v.put("answer", q.answer);
                v.put("analysis", q.analysis);
                v.put("category", category == null ? "" : category);
                v.put("doc_no", q.docNo);
                long rowId = d.insert("questions", null, v);
                ids.add(rowId);
                saveImages(rowId, q);
            }
            d.setTransactionSuccessful();
            return ids;
        } finally {
            d.endTransaction();
        }
    }

    /**
     * 把已有题目移动到目标分类的第 pos 题之前（pos=分类题数 表示末尾）。
     * 原位置之后的题顺延，目标位置之后的题顺延。做题记录跟着题目 id 走，不受影响。
     */
    public static void moveQuestion(long qid, String category, int pos) {
        Question q = getQuestion(qid);
        if (q == null) return;
        List<Long> ids = questionIds(q.bankId);
        ids.remove(Long.valueOf(qid));
        int at;
        int start = categoryStart(q.bankId, category);
        if (start < 0) {
            at = ids.size();
        } else {
            int count = categoryCount(q.bankId, category);
            at = indexOfQindex(q.bankId, start) + Math.max(0, Math.min(count, pos));
        }
        at = Math.max(0, Math.min(ids.size(), at));
        ids.add(at, qid);

        SQLiteDatabase d = db();
        d.beginTransaction();
        try {
            ContentValues cv = new ContentValues();
            cv.put("category", category == null ? "" : category);
            d.update("questions", cv, "id=?", new String[]{String.valueOf(qid)});
            for (int i = 0; i < ids.size(); i++) {
                d.execSQL("UPDATE questions SET qindex=? WHERE id=?", new Object[]{i, ids.get(i)});
            }
            d.setTransactionSuccessful();
        } finally {
            d.endTransaction();
        }
    }

    // ==================== 标记词条 ====================

    /** 词条上限（含「其他」「疑错」） */
    public static final int MAX_TAGS = 10;

    /** 该题库的全部词条（默认词条排最前） */
    public static List<Tag> tags(long bankId) {
        ensureTags(bankId);
        List<Tag> out = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT id, bank_id, name, is_default, sort FROM tags"
                        + " WHERE bank_id=? ORDER BY is_default DESC, sort, id",
                new String[]{String.valueOf(bankId)});
        while (c.moveToNext()) out.add(readTag(c));
        c.close();
        return out;
    }

    private static Tag readTag(Cursor c) {
        Tag t = new Tag();
        t.id = c.getLong(0);
        t.bankId = c.getLong(1);
        t.name = c.getString(2);
        t.isDefault = c.getInt(3) == 1;
        t.sort = c.getInt(4);
        return t;
    }

    /** 默认词条（「其他」，不可删除）；不存在则创建 */
    public static Tag defaultTag(long bankId) {
        ensureTags(bankId);
        Cursor c = db().rawQuery("SELECT id, bank_id, name, is_default, sort FROM tags"
                        + " WHERE bank_id=? AND is_default=1 ORDER BY id LIMIT 1",
                new String[]{String.valueOf(bankId)});
        Tag t = null;
        if (c.moveToFirst()) t = readTag(c);
        c.close();
        return t;
    }

    /** 内置词条（「其他」，不可删除、不可改名） */
    public static boolean isBuiltinTag(long tagId) {
        Cursor c = db().rawQuery("SELECT builtin FROM tags WHERE id=?", new String[]{String.valueOf(tagId)});
        boolean builtin = c.moveToFirst() && c.getInt(0) == 1;
        c.close();
        return builtin;
    }

    /** 只保证内置词条存在（「疑错」仅用于 v5 旧数据迁移，删掉后不再重建） */
    public static void ensureTags(long bankId) {
        Cursor c = db().rawQuery("SELECT 1 FROM tags WHERE bank_id=? AND builtin=1 LIMIT 1",
                new String[]{String.valueOf(bankId)});
        boolean hasBuiltin = c.moveToFirst();
        c.close();
        if (hasBuiltin) return;
        ContentValues v = new ContentValues();
        v.put("bank_id", bankId);
        v.put("name", "其他");
        v.put("is_default", defaultTagId(bankId) > 0 ? 0 : 1);
        v.put("sort", 0);
        v.put("builtin", 1);
        db().insert("tags", null, v);
    }

    private static long builtinTagId(long bankId) {
        Cursor c = db().rawQuery("SELECT COALESCE(MIN(id), 0) FROM tags WHERE bank_id=? AND builtin=1",
                new String[]{String.valueOf(bankId)});
        long id = c.moveToFirst() ? c.getLong(0) : 0;
        c.close();
        return id;
    }

    private static long defaultTagId(long bankId) {
        Cursor c = db().rawQuery("SELECT COALESCE(MIN(id), 0) FROM tags WHERE bank_id=? AND is_default=1",
                new String[]{String.valueOf(bankId)});
        long id = c.moveToFirst() ? c.getLong(0) : 0;
        c.close();
        return id;
    }

    /** 新增词条（允许重名，内置词条靠 id 区分）；达到上限返回 null */
    public static Tag addTag(long bankId, String name) {
        ensureTags(bankId);
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return null;
        if (tags(bankId).size() >= MAX_TAGS) return null;
        ContentValues v = new ContentValues();
        v.put("bank_id", bankId);
        v.put("name", n);
        v.put("is_default", 0);
        v.put("sort", 100);
        long id = db().insert("tags", null, v);
        Tag t = new Tag();
        t.id = id;
        t.bankId = bankId;
        t.name = n;
        return t;
    }

    /** 把某个词条设为默认（「其他」的默认身份会转移过去） */
    public static void setDefaultTag(long bankId, long tagId) {
        db().execSQL("UPDATE tags SET is_default=0 WHERE bank_id=?", new Object[]{bankId});
        db().execSQL("UPDATE tags SET is_default=1 WHERE id=?", new Object[]{tagId});
    }

    /** 重命名词条；内置词条不可改名 */
    public static boolean renameTag(long tagId, String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || isBuiltinTag(tagId)) return false;
        ContentValues v = new ContentValues();
        v.put("name", n);
        return db().update("tags", v, "id=?", new String[]{String.valueOf(tagId)}) > 0;
    }

    /** 该词条被多少道题使用 */
    public static int tagUseCount(long tagId) {
        Cursor c = db().rawQuery("SELECT COUNT(*) FROM question_tags WHERE tag_id=?",
                new String[]{String.valueOf(tagId)});
        int n = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return n;
    }

    /** 删除词条：内置词条拒绝删除；删除的若是默认词条，默认身份回到内置词条 */
    public static boolean deleteTag(long tagId) {
        Cursor c = db().rawQuery("SELECT bank_id, is_default FROM tags WHERE id=?",
                new String[]{String.valueOf(tagId)});
        long bankId = 0;
        boolean wasDefault = false;
        if (c.moveToFirst()) {
            bankId = c.getLong(0);
            wasDefault = c.getInt(1) == 1;
        }
        c.close();
        if (bankId <= 0) return false;
        if (isBuiltinTag(tagId)) return false;
        SQLiteDatabase d = db();
        d.beginTransaction();
        try {
            d.delete("question_tags", "tag_id=?", new String[]{String.valueOf(tagId)});
            d.delete("tags", "id=?", new String[]{String.valueOf(tagId)});
            d.setTransactionSuccessful();
        } finally {
            d.endTransaction();
        }
        if (wasDefault) {
            long builtin = builtinTagId(bankId);
            if (builtin > 0) setDefaultTag(bankId, builtin);
        }
        return true;
    }

    /** 该题打上的词条 id */
    public static List<Long> questionTagIds(long qid) {
        List<Long> out = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT tag_id FROM question_tags WHERE question_id=?",
                new String[]{String.valueOf(qid)});
        while (c.moveToNext()) out.add(c.getLong(0));
        c.close();
        return out;
    }

    /** 该题打上的词条（按词条顺序） */
    public static List<Tag> questionTags(long qid) {
        List<Tag> out = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT t.id, t.bank_id, t.name, t.is_default, t.sort FROM tags t"
                        + " JOIN question_tags qt ON qt.tag_id = t.id WHERE qt.question_id=?"
                        + " ORDER BY t.is_default DESC, t.sort, t.id",
                new String[]{String.valueOf(qid)});
        while (c.moveToNext()) out.add(readTag(c));
        c.close();
        return out;
    }

    /** 整体替换某题的词条 */
    public static void setQuestionTags(long qid, List<Long> tagIds) {
        SQLiteDatabase d = db();
        d.beginTransaction();
        try {
            d.delete("question_tags", "question_id=?", new String[]{String.valueOf(qid)});
            if (tagIds != null) {
                for (long tid : tagIds) {
                    ContentValues v = new ContentValues();
                    v.put("question_id", qid);
                    v.put("tag_id", tid);
                    d.insertWithOnConflict("question_tags", null, v, SQLiteDatabase.CONFLICT_IGNORE);
                }
            }
            d.setTransactionSuccessful();
        } finally {
            d.endTransaction();
        }
    }

    /** 单击标记：切换某题的某词条 */
    public static boolean toggleQuestionTag(long qid, long tagId) {
        Cursor c = db().rawQuery("SELECT 1 FROM question_tags WHERE question_id=? AND tag_id=?",
                new String[]{String.valueOf(qid), String.valueOf(tagId)});
        boolean has = c.moveToFirst();
        c.close();
        if (has) {
            db().delete("question_tags", "question_id=? AND tag_id=?",
                    new String[]{String.valueOf(qid), String.valueOf(tagId)});
            return false;
        }
        ContentValues v = new ContentValues();
        v.put("question_id", qid);
        v.put("tag_id", tagId);
        db().insertWithOnConflict("question_tags", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        return true;
    }

    /** 按词条筛题：tagIds 为空表示不限；多选时命中任一即算（OR） */
    public static List<Long> listIdsByTags(long bankId, int typeMask, String category,
                                           List<Long> tagIds) {
        StringBuilder sql = new StringBuilder("SELECT q.id FROM questions q WHERE q.bank_id=?");
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(bankId));
        if (typeMask >= 0) {
            List<Integer> types = new ArrayList<>();
            for (int i = 0; i < QuestionType.COUNT; i++) {
                if ((typeMask & (1 << i)) != 0) types.add(i);
            }
            if (types.isEmpty()) return new ArrayList<>();
            if (types.size() < QuestionType.COUNT) {
                StringBuilder in = new StringBuilder(" AND q.type IN (");
                for (int i = 0; i < types.size(); i++) {
                    if (i > 0) in.append(',');
                    in.append(types.get(i));
                }
                sql.append(in).append(')');
            }
        }
        if (category != null && !category.isEmpty()) {
            sql.append(" AND q.category=?");
            args.add(category);
        }
        if (tagIds != null && !tagIds.isEmpty()) {
            sql.append(" AND EXISTS (SELECT 1 FROM question_tags qt WHERE qt.question_id=q.id AND qt.tag_id IN (");
            for (int i = 0; i < tagIds.size(); i++) {
                if (i > 0) sql.append(',');
                sql.append('?');
                args.add(String.valueOf(tagIds.get(i)));
            }
            sql.append("))");
        }
        sql.append(" ORDER BY q.qindex, q.id");
        List<Long> out = new ArrayList<>();
        Cursor c = db().rawQuery(sql.toString(), args.toArray(new String[0]));
        while (c.moveToNext()) out.add(c.getLong(0));
        c.close();
        return out;
    }

    /** 危险操作：删除一道题（连带该题的记录/错题/收藏/标记与图片），并把后续题号前移压缩 */
    public static void deleteQuestion(long qid) {
        Question q = getQuestion(qid);
        if (q == null) return;
        SQLiteDatabase d = db();
        d.beginTransaction();
        try {
            String s = String.valueOf(qid);
            d.delete("question_tags", "question_id=?", new String[]{s});
            d.delete("records", "question_id=?", new String[]{s});
            d.delete("wrongs", "question_id=?", new String[]{s});
            d.delete("favs", "question_id=?", new String[]{s});
            d.delete("questions", "id=?", new String[]{s});
            // 题序前移，保持 0..N-1 连续
            Cursor c = d.rawQuery("SELECT id FROM questions WHERE bank_id=? ORDER BY qindex, id",
                    new String[]{String.valueOf(q.bankId)});
            int i = 0;
            while (c.moveToNext()) {
                d.execSQL("UPDATE questions SET qindex=? WHERE id=?",
                        new Object[]{i++, c.getLong(0)});
            }
            c.close();
            d.setTransactionSuccessful();
        } finally {
            d.endTransaction();
        }
        if (q.imgPaths != null) {
            for (String rel : q.imgPaths) deleteImageFile(rel);
        }
    }

    /**
     * 重置题库：按选项清空该库的记录 / 错题 / 标记 / 收藏（总统计账本不动）。
     * 进度随做题记录一起清空。
     */
    public static void resetBank(long bankId, boolean records, boolean wrongs,
                                 boolean marks, boolean favs) {
        SQLiteDatabase d = db();
        String s = String.valueOf(bankId);
        d.beginTransaction();
        try {
            if (records) {
                d.delete("records", "bank_id=?", new String[]{s});
                d.execSQL("UPDATE progress SET last_qid = NULL WHERE bank_id=?", new Object[]{bankId});
            }
            if (wrongs) d.delete("wrongs", "bank_id=?", new String[]{s});
            if (favs) d.delete("favs", "bank_id=?", new String[]{s});
            if (marks) {
                d.execSQL("DELETE FROM question_tags WHERE question_id IN"
                        + " (SELECT id FROM questions WHERE bank_id=?)", new Object[]{bankId});
            }
            d.setTransactionSuccessful();
        } finally {
            d.endTransaction();
        }
    }

    // ==================== 题号（原文号 / 自动按分类编号） ====================

    /**
     * 按分类分组递增的计数器（导出时按题序走一遍即可得到每题的顺位）。
     * 分类为空则不计数，返回 0。
     */
    public static final class CategoryCounter {
        private final java.util.HashMap<String, Integer> m = new java.util.HashMap<>();

        public int next(String category) {
            if (category == null || category.trim().isEmpty()) return 0;
            int n = m.getOrDefault(category, 0) + 1;
            m.put(category, n);
            return n;
        }
    }

    /** 该题在其分类中的顺位（1 起）；分类为空返回 0 */
    public static int autoNo(Question q) {
        if (q == null || q.category == null || q.category.trim().isEmpty()) return 0;
        Cursor c = db().rawQuery(
                "SELECT COUNT(*) FROM questions WHERE bank_id=? AND category=?"
                        + " AND (qindex < ? OR (qindex = ? AND id < ?))",
                new String[]{String.valueOf(q.bankId), q.category,
                        String.valueOf(q.qindex), String.valueOf(q.qindex), String.valueOf(q.id)});
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n + 1;
    }

    /** 界面显示与导出统一取号：原文号优先，没有则按分类自动编号，都没有返回 0 */
    public static int displayNo(Question q) {
        return q == null ? 0 : (q.docNo > 0 ? q.docNo : autoNo(q));
    }

    /**
     * 按题型掩码 + 分类筛选题目 id。
     *
     * @param typeMask 7 位掩码（bit0=单选…bit6=看图），-1 表示不限
     * @param random   true=随机排序
     */
    public static List<Long> questionIdsByFilter(long bankId, int typeMask, String category, boolean random) {
        StringBuilder sql = new StringBuilder("SELECT id FROM questions WHERE bank_id=?");
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(bankId));
        if (typeMask >= 0) {
            List<Integer> types = new ArrayList<>();
            for (int i = 0; i < QuestionType.COUNT; i++) {
                if ((typeMask & (1 << i)) != 0) types.add(i);
            }
            if (types.isEmpty()) return new ArrayList<>();   // 掩码为空：没有任何题型可选
            if (types.size() < QuestionType.COUNT) {
                StringBuilder in = new StringBuilder(" AND type IN (");
                for (int i = 0; i < types.size(); i++) {
                    if (i > 0) in.append(",");
                    in.append(types.get(i));
                }
                in.append(")");
                sql.append(in);
            }
        }
        if (category != null && !category.isEmpty()) {
            sql.append(" AND category=?");
            args.add(category);
        }
        sql.append(random ? " ORDER BY RANDOM()" : " ORDER BY qindex, id");
        List<Long> out = new ArrayList<>();
        Cursor c = db().rawQuery(sql.toString(), args.toArray(new String[0]));
        while (c.moveToNext()) out.add(c.getLong(0));
        c.close();
        return out;
    }

    /** 综合筛选（浏览/错题/收藏/标记/搜索共用） */
    public static List<Long> listIds(long bankId, int typeMask, String category,
                                     int wrongFilter, boolean favOnly, boolean flaggedOnly, String search) {
        StringBuilder sql = new StringBuilder("SELECT DISTINCT q.id FROM questions q");
        List<String> args = new ArrayList<>();
        if (wrongFilter >= 0) sql.append(" JOIN wrongs w ON w.question_id = q.id");
        if (favOnly) sql.append(" JOIN favs f ON f.question_id = q.id");
        sql.append(" WHERE 1=1");
        if (bankId > 0) {
            sql.append(" AND q.bank_id=?");
            args.add(String.valueOf(bankId));
        }
        if (typeMask >= 0) {
            List<Integer> types = new ArrayList<>();
            for (int i = 0; i < QuestionType.COUNT; i++) {
                if ((typeMask & (1 << i)) != 0) types.add(i);
            }
            if (types.isEmpty()) return new ArrayList<>();   // 掩码为空：没有任何题型可选
            if (types.size() < QuestionType.COUNT) {
                StringBuilder in = new StringBuilder(" AND q.type IN (");
                for (int i = 0; i < types.size(); i++) {
                    if (i > 0) in.append(",");
                    in.append(types.get(i));
                }
                in.append(")");
                sql.append(in);
            }
        }
        if (category != null && !category.isEmpty()) {
            sql.append(" AND q.category=?");
            args.add(category);
        }
        if (wrongFilter == 0) sql.append(" AND w.resolved=0");
        if (wrongFilter == 1) sql.append(" AND w.resolved=1");
        if (flaggedOnly) sql.append(" AND q.flagged=1");
        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (q.content LIKE ? OR q.options LIKE ? OR q.analysis LIKE ? OR q.answer LIKE ?)");
            String kw = "%" + search.trim() + "%";
            args.add(kw);
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        sql.append(" ORDER BY q.bank_id, q.qindex, q.id");
        List<Long> out = new ArrayList<>();
        Cursor c = db().rawQuery(sql.toString(), args.toArray(new String[0]));
        while (c.moveToNext()) out.add(c.getLong(0));
        c.close();
        return out;
    }

    public static int[] typeCounts(long bankId) {
        int[] out = new int[QuestionType.COUNT];
        Cursor c = db().rawQuery("SELECT type, COUNT(*) FROM questions WHERE bank_id=? GROUP BY type",
                new String[]{String.valueOf(bankId)});
        while (c.moveToNext()) {
            int t = c.getInt(0);
            if (t >= 0 && t < QuestionType.COUNT) out[t] = c.getInt(1);
        }
        c.close();
        return out;
    }

    /** 所有题库的题型统计：bankId → int[COUNT] */
    public static Map<Long, int[]> typeCountsMap() {
        Map<Long, int[]> map = new HashMap<>();
        Cursor c = db().rawQuery("SELECT bank_id, type, COUNT(*) FROM questions GROUP BY bank_id, type", null);
        while (c.moveToNext()) {
            long bid = c.getLong(0);
            int t = c.getInt(1);
            int n = c.getInt(2);
            if (t >= 0 && t < QuestionType.COUNT) {
                int[] arr = map.get(bid);
                if (arr == null) {
                    arr = new int[QuestionType.COUNT];
                    map.put(bid, arr);
                }
                arr[t] = n;
            }
        }
        c.close();
        return map;
    }

    public static List<String> categories(long bankId) {
        List<String> out = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT DISTINCT category FROM questions WHERE bank_id=? AND category<>'' ORDER BY category",
                new String[]{String.valueOf(bankId)});
        while (c.moveToNext()) out.add(c.getString(0));
        c.close();
        return out;
    }

    public static void setFlag(long qid, boolean flag) {
        ContentValues v = new ContentValues();
        v.put("flagged", flag ? 1 : 0);
        db().update("questions", v, "id=?", new String[]{String.valueOf(qid)});
    }

    // ==================== 总统计账本 ====================

    /** 日索引（UTC 天，与旧版"连续天数"口径一致） */
    private static long dayIndex(long timeMs) {
        return timeMs / 86400000L;
    }

    private static int questionType(long qid) {
        Cursor c = db().rawQuery("SELECT type FROM questions WHERE id=?",
                new String[]{String.valueOf(qid)});
        int t = 0;
        if (c.moveToFirst()) t = c.getInt(0);
        c.close();
        return t;
    }

    /**
     * 累加总统计账本（题目作答次数 / 正确次数 / 按日 / 按题型）。
     * 只在答题与重判时调用；删除题库、清空记录都不会动它。
     */
    static void bumpStats(int deltaAnswered, int deltaCorrect, long day, int type) {        if (deltaAnswered == 0 && deltaCorrect == 0) return;
        db().execSQL("UPDATE stats SET answered = answered + ?, correct = correct + ? WHERE id = 1",
                new Object[]{deltaAnswered, deltaCorrect});

        Cursor c = db().rawQuery("SELECT 1 FROM stats_days WHERE day = ?",
                new String[]{String.valueOf(day)});
        boolean exists = c.moveToFirst();
        c.close();
        if (exists) {
            db().execSQL("UPDATE stats_days SET answered = answered + ?, correct = correct + ? WHERE day = ?",
                    new Object[]{deltaAnswered, deltaCorrect, day});
        } else if (deltaAnswered > 0 || deltaCorrect > 0) {
            db().execSQL("INSERT INTO stats_days(day, answered, correct) VALUES(?,?,?)",
                    new Object[]{day, deltaAnswered, deltaCorrect});
        }

        Cursor c2 = db().rawQuery("SELECT 1 FROM stats_types WHERE type = ?",
                new String[]{String.valueOf(type)});
        boolean e2 = c2.moveToFirst();
        c2.close();
        if (e2) {
            db().execSQL("UPDATE stats_types SET answered = answered + ?, correct = correct + ? WHERE type = ?",
                    new Object[]{deltaAnswered, deltaCorrect, type});
        } else if (deltaAnswered > 0 || deltaCorrect > 0) {
            db().execSQL("INSERT INTO stats_types(type, answered, correct) VALUES(?,?,?)",
                    new Object[]{type, deltaAnswered, deltaCorrect});
        }
    }

    /** 清空总统计（只清账本；做题记录、错题、题库都不受影响） */
    public static void clearStats() {
        db().execSQL("DELETE FROM stats_days");
        db().execSQL("DELETE FROM stats_types");
        db().execSQL("UPDATE stats SET answered = 0, correct = 0 WHERE id = 1");
    }

    // ==================== 答题记录 ====================

    /** mode: 0=练习 1=考试 */
    public static void addRecord(long bankId, long qid, int mode, String userAnswer,
                                 boolean correct, boolean resolveWrongOnCorrect) {
        ContentValues v = new ContentValues();
        v.put("bank_id", bankId);
        v.put("question_id", qid);
        v.put("mode", mode);
        v.put("user_answer", userAnswer == null ? "" : userAnswer);
        v.put("correct", correct ? 1 : 0);
        long now = System.currentTimeMillis();
        v.put("answered_at", now);
        db().beginTransaction();
        try {
            db().insert("records", null, v);
            // 总统计账本同步累加：之后哪怕删题库/清记录，这里的数字也不会被抹掉
            bumpStats(1, correct ? 1 : 0, dayIndex(now), questionType(qid));
            db().setTransactionSuccessful();
        } finally {
            db().endTransaction();
        }
        if (correct) {
            if (resolveWrongOnCorrect) resolveWrong(qid);
        } else {
            addWrong(bankId, qid);
        }
        touchBank(bankId);
    }

    /** 单题最近一次作答快照（重进练习时回填「已答」状态用） */
    public static final class LastAnswer {
        public String userAnswer = "";
        public boolean correct;
        public long at;
    }

    /** 取某题最近一次作答；没答过返回 null */
    public static LastAnswer lastAnswer(long qid) {
        Cursor c = db().rawQuery(
                "SELECT user_answer, correct, answered_at FROM records"
                        + " WHERE question_id=? ORDER BY answered_at DESC, id DESC LIMIT 1",
                new String[]{String.valueOf(qid)});
        LastAnswer a = null;
        if (c.moveToFirst()) {
            a = new LastAnswer();
            a.userAnswer = c.getString(0) == null ? "" : c.getString(0);
            a.correct = c.getInt(1) == 1;
            a.at = c.getLong(2);
        }
        c.close();
        return a;
    }

    /** 批量取多题最近一次作答（一次性回填，避免逐题查询） */
    public static Map<Long, LastAnswer> lastAnswers(Collection<Long> qids) {
        Map<Long, LastAnswer> out = new HashMap<>();
        if (qids == null || qids.isEmpty()) return out;
        List<Long> all = new ArrayList<>(qids);
        final int chunk = 700;
        for (int from = 0; from < all.size(); from += chunk) {
            int to = Math.min(all.size(), from + chunk);
            StringBuilder in = new StringBuilder();
            String[] args = new String[to - from];
            for (int i = from; i < to; i++) {
                if (in.length() > 0) in.append(',');
                in.append('?');
                args[i - from] = String.valueOf(all.get(i));
            }
            Cursor c = db().rawQuery(
                    "SELECT question_id, user_answer, correct, answered_at FROM records"
                            + " WHERE id IN (SELECT MAX(id) FROM records WHERE question_id IN ("
                            + in + ") GROUP BY question_id)", args);
            while (c.moveToNext()) {
                LastAnswer a = new LastAnswer();
                a.userAnswer = c.getString(1) == null ? "" : c.getString(1);
                a.correct = c.getInt(2) == 1;
                a.at = c.getLong(3);
                out.put(c.getLong(0), a);
            }
            c.close();
        }
        return out;
    }

    /** 修正题目的正确答案（题库纠错） */
    public static void updateAnswer(long qid, String answer) {
        ContentValues v = new ContentValues();
        v.put("answer", answer == null ? "" : answer.trim());
        db().update("questions", v, "id=?", new String[]{String.valueOf(qid)});
    }

    /** 调整题型（答案形态变化时，如单选改多选） */
    public static void updateType(long qid, int type) {
        ContentValues v = new ContentValues();
        v.put("type", type);
        db().update("questions", v, "id=?", new String[]{String.valueOf(qid)});
    }

    /** 整题编辑保存：题型 / 题干 / 选项 / 答案 / 解析 / 图片路径 */
    public static void updateQuestion(long qid, int type, String content, List<String> options,
                                      String answer, String analysis, String imgPathsJson) {
        ContentValues v = new ContentValues();
        v.put("type", type);
        v.put("content", content == null ? "" : content);
        v.put("options", new JSONArray(options == null ? new ArrayList<String>() : options).toString());
        v.put("answer", answer == null ? "" : answer.trim());
        v.put("analysis", analysis == null ? "" : analysis.trim());
        v.put("img_paths", imgPathsJson == null ? "[]" : imgPathsJson);
        db().update("questions", v, "id=?", new String[]{String.valueOf(qid)});
    }

    /**
     * 追加一张题目图片（原始字节，仅 png/jpg/gif），返回相对路径 {@code imgs/xxx}，失败返回 null。
     * 文件名沿用导入时的规则 {@code q<题目id>_<序号>.<ext>}。
     */
    public static String addImage(long qid, byte[] data) {
        String ext = imgExt(data);
        if (ext == null || sCtx == null) return null;
        File dir = new File(sCtx.getFilesDir(), "imgs");
        if (!dir.exists() && !dir.mkdirs()) return null;
        int n = 0;
        File f;
        do {
            f = new File(dir, "q" + qid + "_" + (n++) + "." + ext);
        } while (f.exists() && n < 1000);
        try {
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(data);
            fos.close();
        } catch (Exception e) {
            return null;
        }
        return "imgs/" + f.getName();
    }

    /** 删除题目图片文件（数据库中的 img_paths 由调用方同步更新） */
    public static void deleteImageFile(String rel) {
        File f = imgFile(rel);
        if (f != null && f.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    /** 保存某题的笔记（空串=清除） */
    public static void updateNote(long qid, String note) {
        ContentValues v = new ContentValues();
        v.put("note", note == null ? "" : note);
        db().update("questions", v, "id=?", new String[]{String.valueOf(qid)});
    }

    /** 编辑题目解析 */
    public static void updateAnalysis(long qid, String analysis) {
        ContentValues v = new ContentValues();
        v.put("analysis", analysis == null ? "" : analysis.trim());
        db().update("questions", v, "id=?", new String[]{String.valueOf(qid)});
    }

    /** 重做本题：清空该题全部作答记录与错题本条目 */
    public static void resetQuestion(long qid) {
        String s = String.valueOf(qid);
        db().delete("records", "question_id=?", new String[]{s});
        db().delete("wrongs", "question_id=?", new String[]{s});
    }

    /** 客观题才能按参考答案重判（简答/看图是自评，重判无意义） */
    static boolean rejudgeable(int type) {
        return type == QuestionType.SINGLE || type == QuestionType.MULTI
                || type == QuestionType.JUDGE || type == QuestionType.FILL
                || type == QuestionType.SORT;
    }

    /** 按题型判定一次作答 */
    static boolean judgeByType(int type, String userAnswer, String answer) {
        if (userAnswer == null || answer == null) return false;
        switch (type) {
            case QuestionType.MULTI:
                return AnswerUtil.matchMulti(userAnswer, answer);
            case QuestionType.JUDGE: {
                String u = AnswerUtil.normalizeJudge(userAnswer);
                String a = AnswerUtil.normalizeJudge(answer);
                return !u.isEmpty() && u.equals(a);
            }
            case QuestionType.FILL:
                return AnswerUtil.matchFill(userAnswer, answer);
            case QuestionType.SORT:
                return AnswerUtil.matchSort(userAnswer, answer);
            default:
                return AnswerUtil.matchSingle(userAnswer.trim(), answer.trim());
        }
    }

    /**
     * 正确答案被修改后，按新答案重判该题已有作答记录，并同步错题本。
     * 用户当时选的答案保持不变（选 C 还是 C），只是对错结论与统计随之变化。
     */
    public static void rejudgeQuestion(long qid) {
        Question q = getQuestion(qid);
        if (q == null || !rejudgeable(q.type)) return;
        List<Long> recIds = new ArrayList<>();
        List<String> recAnswers = new ArrayList<>();
        List<Integer> recCorrect = new ArrayList<>();
        List<Long> recTimes = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT id, user_answer, correct, answered_at FROM records WHERE question_id=?",
                new String[]{String.valueOf(qid)});
        while (c.moveToNext()) {
            recIds.add(c.getLong(0));
            recAnswers.add(c.getString(1) == null ? "" : c.getString(1));
            recCorrect.add(c.getInt(2));
            recTimes.add(c.getLong(3));
        }
        c.close();
        db().beginTransaction();
        try {
            for (int i = 0; i < recIds.size(); i++) {
                int newCorrect = judgeByType(q.type, recAnswers.get(i), q.answer) ? 1 : 0;
                if (newCorrect == recCorrect.get(i)) continue;
                ContentValues v = new ContentValues();
                v.put("correct", newCorrect);
                db().update("records", v, "id=?", new String[]{String.valueOf(recIds.get(i))});
                // 对错结论变了，总统计账本只调整"正确次数"，作答次数不变
                bumpStats(0, newCorrect - recCorrect.get(i), dayIndex(recTimes.get(i)), q.type);
            }
            db().setTransactionSuccessful();
        } finally {
            db().endTransaction();
        }
        // 错题本跟着最新一次结论走
        int latest = lastStatus(qid);
        if (latest == 1) removeWrong(qid);
        else if (latest == 2) addWrong(q.bankId, qid);
    }

    public static long[] globalStats() {
        long[] out = {0, 0}; // total, correct
        Cursor c = db().rawQuery("SELECT answered, correct FROM stats WHERE id = 1", null);
        if (c.moveToFirst()) {
            out[0] = c.getLong(0);
            out[1] = c.getLong(1);
        }
        c.close();
        return out;
    }

    public static int todayCount() {
        Cursor c = db().rawQuery("SELECT answered FROM stats_days WHERE day = ?",
                new String[]{String.valueOf(dayIndex(System.currentTimeMillis()))});
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    static long todayStart() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /** 连续打卡天数（含今天；今天未做题则从昨天起算）——读总统计账本 */
    public static int streak() {
        List<Integer> days = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT day FROM stats_days WHERE answered > 0 ORDER BY day DESC", null);
        while (c.moveToNext()) days.add(c.getInt(0));
        c.close();
        if (days.isEmpty()) return 0;
        long today = dayIndex(System.currentTimeMillis());
        int cur = days.get(0);
        int expect = (int) today;
        if (cur == expect - 1) expect--; // 今天没做，从昨天起算
        int streak = 0;
        for (int d : days) {
            if (d == expect) {
                streak++;
                expect--;
            } else {
                break;
            }
        }
        return streak;
    }

    /** 近 7 日做题数（下标 0=今天 … 6=六天前）——读总统计账本 */
    public static int[] last7Days() {
        int[] out = new int[7];
        long today = dayIndex(System.currentTimeMillis());
        for (int i = 0; i < 7; i++) {
            Cursor c = db().rawQuery("SELECT answered FROM stats_days WHERE day = ?",
                    new String[]{String.valueOf(today - i)});
            if (c.moveToFirst()) out[i] = c.getInt(0);
            c.close();
        }
        return out;
    }

    /** 各题型 [正确数, 总作答数]——读总统计账本 */
    public static int[][] typeStats() {
        int[][] out = new int[QuestionType.COUNT][2];
        Cursor c = db().rawQuery("SELECT type, answered, correct FROM stats_types", null);
        while (c.moveToNext()) {
            int t = c.getInt(0);
            if (t >= 0 && t < QuestionType.COUNT) {
                out[t][0] = c.getInt(2);   // 正确数
                out[t][1] = c.getInt(1);   // 总作答数
            }
        }
        c.close();
        return out;
    }

    /** 最近一次作答状态：0 无 1 对 2 错 */
    public static int lastStatus(long qid) {
        Cursor c = db().rawQuery("SELECT correct FROM records WHERE question_id=? ORDER BY answered_at DESC LIMIT 1",
                new String[]{String.valueOf(qid)});
        int s = 0;
        if (c.moveToFirst()) s = c.getInt(0) == 1 ? 1 : 2;
        c.close();
        return s;
    }

    public static void clearRecords() {
        db().delete("records", null, null);
        db().execSQL("UPDATE progress SET last_qid = NULL");
    }

    // ==================== 错题 ====================

    public static void addWrong(long bankId, long qid) {
        ContentValues v = new ContentValues();
        v.put("bank_id", bankId);
        v.put("question_id", qid);
        v.put("wrong_count", 1);
        v.put("resolved", 0);
        v.put("updated_at", System.currentTimeMillis());
        db().insertWithOnConflict("wrongs", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public static void resolveWrong(long qid) {
        ContentValues v = new ContentValues();
        v.put("resolved", 1);
        v.put("updated_at", System.currentTimeMillis());
        db().update("wrongs", v, "question_id=?", new String[]{String.valueOf(qid)});
    }

    public static void removeWrong(long qid) {
        db().delete("wrongs", "question_id=?", new String[]{String.valueOf(qid)});
    }

    /** 取某题库全部题目（导出用，单次查询） */
    public static List<Question> questionsOfBank(long bankId) {
        List<Question> out = new ArrayList<>();
        Cursor c = db().query("questions", null, "bank_id=?",
                new String[]{String.valueOf(bankId)}, null, null, "qindex, id");
        while (c.moveToNext()) out.add(readQuestion(c));
        c.close();
        return out;
    }

    /** 读取题目图片原始字节（导出/备份用；异常或过大返回 null） */
    public static byte[] imageBytes(String rel) {
        File f = imgFile(rel);
        if (f == null || !f.isFile()) return null;
        long len = f.length();
        if (len <= 0 || len > 24L * 1024 * 1024) return null;
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            byte[] buf = new byte[(int) len];
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n <= 0) break;
                off += n;
            }
            in.close();
            return buf;
        } catch (Exception e) {
            return null;
        }
    }

    /** 该题在错题本中的状态：0=不在错题本，1=未掌握，2=已掌握 */
    public static int wrongStatus(long qid) {
        Cursor c = db().rawQuery("SELECT resolved FROM wrongs WHERE question_id=?",
                new String[]{String.valueOf(qid)});
        int s = 0;
        if (c.moveToFirst()) s = c.getInt(0) == 1 ? 2 : 1;
        c.close();
        return s;
    }

    public static int wrongCount(long bankId, int resolvedFilter) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM wrongs WHERE 1=1");
        List<String> args = new ArrayList<>();
        if (bankId > 0) {
            sql.append(" AND bank_id=?");
            args.add(String.valueOf(bankId));
        }
        if (resolvedFilter == 0) sql.append(" AND resolved=0");
        if (resolvedFilter == 1) sql.append(" AND resolved=1");
        Cursor c = db().rawQuery(sql.toString(), args.toArray(new String[0]));
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    public static List<Long> wrongIds(long bankId, int resolvedFilter) {
        StringBuilder sql = new StringBuilder("SELECT question_id FROM wrongs WHERE 1=1");
        List<String> args = new ArrayList<>();
        if (bankId > 0) {
            sql.append(" AND bank_id=?");
            args.add(String.valueOf(bankId));
        }
        if (resolvedFilter == 0) sql.append(" AND resolved=0");
        if (resolvedFilter == 1) sql.append(" AND resolved=1");
        sql.append(" ORDER BY updated_at DESC");
        List<Long> out = new ArrayList<>();
        Cursor c = db().rawQuery(sql.toString(), args.toArray(new String[0]));
        while (c.moveToNext()) out.add(c.getLong(0));
        c.close();
        return out;
    }

    public static void clearWrongs() {
        db().delete("wrongs", null, null);
    }

    // ==================== 收藏 ====================

    public static boolean toggleFav(long bankId, long qid) {
        SQLiteDatabase d = db();
        if (isFav(qid)) {
            d.delete("favs", "question_id=?", new String[]{String.valueOf(qid)});
            return false;
        }
        ContentValues v = new ContentValues();
        v.put("bank_id", bankId);
        v.put("question_id", qid);
        v.put("created_at", System.currentTimeMillis());
        d.insertWithOnConflict("favs", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        return true;
    }

    public static boolean isFav(long qid) {
        Cursor c = db().rawQuery("SELECT 1 FROM favs WHERE question_id=? LIMIT 1",
                new String[]{String.valueOf(qid)});
        boolean r = c.moveToFirst();
        c.close();
        return r;
    }

    public static List<Long> favIds(long bankId) {
        String sql = bankId > 0
                ? "SELECT f.question_id FROM favs f JOIN questions q ON q.id=f.question_id"
                + " WHERE q.bank_id=? ORDER BY f.created_at DESC"
                : "SELECT f.question_id FROM favs f JOIN questions q ON q.id=f.question_id"
                + " ORDER BY f.created_at DESC";
        List<Long> out = new ArrayList<>();
        Cursor c = db().rawQuery(sql, bankId > 0 ? new String[]{String.valueOf(bankId)} : null);
        while (c.moveToNext()) out.add(c.getLong(0));
        c.close();
        return out;
    }

    public static int favCount() {
        Cursor c = db().rawQuery("SELECT COUNT(*) FROM favs", null);
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    /** 按题目真实所属题库计数（与收藏列表口径一致；favs 表残留行自动过滤） */
    public static int favCount(long bankId) {
        String sql = bankId > 0
                ? "SELECT COUNT(*) FROM favs f JOIN questions q ON q.id=f.question_id WHERE q.bank_id=?"
                : "SELECT COUNT(*) FROM favs f JOIN questions q ON q.id=f.question_id";
        Cursor c = db().rawQuery(sql, bankId > 0 ? new String[]{String.valueOf(bankId)} : null);
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    public static void clearFavs() {
        db().delete("favs", null, null);
    }

    // ==================== 顺序练习进度 ====================

    public static long getProgress(long bankId) {
        Cursor c = db().rawQuery("SELECT last_qid FROM progress WHERE bank_id=?",
                new String[]{String.valueOf(bankId)});
        long v = 0;
        if (c.moveToFirst()) v = c.getLong(0);
        c.close();
        return v;
    }

    public static void setProgress(long bankId, long lastQid) {
        ContentValues v = new ContentValues();
        v.put("bank_id", bankId);
        v.put("last_qid", lastQid);
        v.put("updated_at", System.currentTimeMillis());
        db().insertWithOnConflict("progress", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public static void resetProgress(long bankId) {
        db().delete("progress", "bank_id=?", new String[]{String.valueOf(bankId)});
    }

    // ==================== 考试 ====================

    public static long addExam(long bankId, String name, int total, int done, int correct,
                               double score, double full, int durationSec, List<Long> wrongIds,
                               int[][] typeDetail) {
        ContentValues v = new ContentValues();
        v.put("bank_id", bankId);
        v.put("name", name);
        v.put("total", total);
        v.put("done", done);
        v.put("correct", correct);
        v.put("score", score);
        v.put("full", full);
        v.put("duration", durationSec);
        v.put("finished_at", System.currentTimeMillis());
        v.put("wrong_ids", new JSONArray(wrongIds).toString());
        JSONArray detail = new JSONArray();
        for (int t = 0; t < QuestionType.COUNT; t++) {
            JSONArray pair = new JSONArray();
            if (typeDetail != null && t < typeDetail.length && typeDetail[t] != null
                    && typeDetail[t].length >= 2) {
                pair.put(typeDetail[t][0]);
                pair.put(typeDetail[t][1]);
            } else {
                pair.put(0);
                pair.put(0);
            }
            detail.put(pair);
        }
        v.put("detail", detail.toString());
        return db().insert("exams", null, v);
    }

    public static List<ExamRecord> listExams(int limit) {
        List<ExamRecord> out = new ArrayList<>();
        Cursor c = db().rawQuery("SELECT e.id, e.bank_id, COALESCE(b.name,''), e.name, e.total, e.done,"
                + " e.correct, e.score, e.full, e.duration, e.finished_at, e.wrong_ids, e.detail"
                + " FROM exams e LEFT JOIN banks b ON b.id = e.bank_id"
                + " ORDER BY e.finished_at DESC LIMIT ?", new String[]{String.valueOf(limit)});
        while (c.moveToNext()) out.add(readExam(c));
        c.close();
        return out;
    }

    public static ExamRecord getExam(long id) {
        Cursor c = db().rawQuery("SELECT e.id, e.bank_id, COALESCE(b.name,''), e.name, e.total, e.done,"
                + " e.correct, e.score, e.full, e.duration, e.finished_at, e.wrong_ids, e.detail"
                + " FROM exams e LEFT JOIN banks b ON b.id = e.bank_id WHERE e.id=?",
                new String[]{String.valueOf(id)});
        ExamRecord r = null;
        if (c.moveToFirst()) r = readExam(c);
        c.close();
        return r;
    }

    static ExamRecord readExam(Cursor c) {
        ExamRecord e = new ExamRecord();
        e.id = c.getLong(0);
        e.bankId = c.getLong(1);
        e.bankName = c.getString(2);
        e.name = c.getString(3);
        e.total = c.getInt(4);
        e.done = c.getInt(5);
        e.correct = c.getInt(6);
        e.score = c.getDouble(7);
        e.full = c.getDouble(8);
        e.durationSec = c.getInt(9);
        e.finishedAt = c.getLong(10);
        try {
            JSONArray a = new JSONArray(c.getString(11));
            for (int i = 0; i < a.length(); i++) e.wrongIds.add(a.getLong(i));
        } catch (JSONException ignored) {
        }
        try {
            JSONArray d = new JSONArray(c.getString(12));
            for (int t = 0; t < QuestionType.COUNT && t < d.length(); t++) {
                JSONArray pair = d.optJSONArray(t);
                if (pair != null) {
                    e.typeDetail[t][0] = pair.optInt(0);
                    e.typeDetail[t][1] = pair.optInt(1);
                }
            }
        } catch (JSONException ignored) {
        }
        return e;
    }

    public static void clearExams() {
        db().delete("exams", null, null);
    }

    // ==================== 导出 ====================

    /** 导出题库为 xlsx 行（含表头） */
    public static List<String[]> exportRows(long bankId) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"题型", "题目", "选项A", "选项B", "选项C", "选项D", "选项E", "选项F",
                "答案", "解析", "分类"});
        Cursor c = db().rawQuery(
                "SELECT content, type, options, answer, analysis, category, doc_no FROM questions"
                        + " WHERE bank_id=? ORDER BY qindex, id", new String[]{String.valueOf(bankId)});
        CategoryCounter counter = new CategoryCounter();
        while (c.moveToNext()) {
            String content = c.getString(0);
            int type = c.getInt(1);
            ArrayList<String> opts = parseOptions(c.getString(2));
            String answer = c.getString(3);
            String analysis = c.getString(4);
            String category = c.getString(5);
            int docNo = c.getInt(6);
            // 每行都推进分类计数，保证原文号与自动号混用时顺位一致
            int seq = counter.next(category);
            int no = docNo > 0 ? docNo : seq;
            String[] row = new String[11];
            row[0] = QuestionType.NAMES[type];
            // 导出时把题号写回行首，导出的文档与来源一致，回导也不丢号
            row[1] = no > 0 ? no + "." + content : content;
            for (int i = 0; i < 6; i++) {
                row[2 + i] = i < opts.size() ? opts.get(i) : "";
            }
            row[8] = type == QuestionType.JUDGE ? AnswerUtil.judgeText(answer) : answer;
            row[9] = analysis == null ? "" : analysis;
            row[10] = category == null ? "" : category;
            rows.add(row);
        }
        c.close();
        return rows;
    }
}