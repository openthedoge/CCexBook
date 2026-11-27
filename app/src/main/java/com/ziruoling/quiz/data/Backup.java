package com.ziruoling.quiz.data;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 整库备份 / 恢复。
 * <p>
 * 备份格式：ZIP（二进制，不追求可读），内含
 * <ul>
 *   <li>{@code manifest.json} —— 时间、版本、库版本、条目计数</li>
 *   <li>{@code db} —— SQLite 数据库文件快照（可能附带 {@code db-wal}）</li>
 *   <li>{@code imgs/xxx} —— 看图题图片原始字节</li>
 *   <li>{@code prefs.json} —— 应用设置（主题/字号/语言/刷题行为/音效开关等，不含 session_running）</li>
 *   <li>{@code snake_stats.json} —— 彩蛋统计（最高分/最长/游玩次数/总时长）</li>
 *   <li>{@code sound/xxx} —— 自定义答对/答错音效文件（若有）</li>
 * </ul>
 * 兼容：旧版备份没有后三个条目，恢复时跳过、保持当前设置。
 * <p>
 * 性能优先：整文件快照 + 流式读写，不做逐行导出；几千题的库也是秒级。
 */
public final class Backup {

    public static final String FILE_PREFIX = "CC练习本 备份_";
    public static final String SNAPSHOT_NAME = "原数据.zip";
    public static final String MIME = "application/zip";

    private static final String APP_ID = "ziruoling_quiz";
    private static final String MANIFEST = "manifest.json";
    private static final String DB_ENTRY = "db";
    private static final String IMG_PREFIX = "imgs/";
    private static final String PREF_ENTRY = "prefs.json";
    private static final String SNAKE_ENTRY = "snake_stats.json";
    private static final String SOUND_PREFIX = "sound/";
    private static final String PREF_NAME = "ziruoling_quiz";
    private static final String SNAKE_PREF_NAME = "snake_stats";
    private static final String SESSION_RUNNING = "session_running";
    /** 本应用里以 long / float 存的设置键：JSON 不区分数字宽度（0.0 会写成 0），恢复时按原类型写回 */
    private static final java.util.Set<String> LONG_PREF_KEYS =
            java.util.Collections.singleton("totalMs");
    private static final java.util.Set<String> FLOAT_PREF_KEYS =
            java.util.Collections.singleton("bestF");
    /** 当前应用支持的最高库版本（恢复更低版本的备份时由 onUpgrade 自动迁移） */
    private static final int DB_VERSION = 13;

    private Backup() {}

    // ==================== 路径 ====================

    public static File backupDir(Context c) {
        File d = new File(c.getFilesDir(), "backup");
        if (!d.exists() && !d.mkdirs()) {
            // 目录建不出来时退回私有根目录，避免直接崩
        }
        return d;
    }

    public static File snapshotFile(Context c) {
        return new File(backupDir(c), SNAPSHOT_NAME);
    }

    public static boolean hasSnapshot(Context c) {
        File f = snapshotFile(c);
        return f.isFile() && f.length() > 0;
    }

    public static void deleteSnapshot(Context c) {
        File f = snapshotFile(c);
        if (f.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    /** 备份文件名：CC练习本 备份_20261119_193502.zip */
    public static String suggestFileName() {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss",
                java.util.Locale.CHINA);
        return FILE_PREFIX + f.format(new java.util.Date()) + ".zip";
    }

    // ==================== 摘要 ====================

    public static final class Info {
        public long createdAt;
        public int dbVersion;
        public String versionName = "";
        public int banks;
        public int questions;
        public int records;

        public String describe() {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",
                    java.util.Locale.CHINA);
            String when = createdAt <= 0 ? "未知时间" : f.format(new java.util.Date(createdAt));
            return "备份时间：" + when
                    + "\n应用版本：v" + (versionName.isEmpty() ? "?" : versionName)
                    + "\n题库 " + banks + " 个 · 题目 " + questions + " 道 · 记录 " + records + " 条";
        }
    }

    public static Info inspect(File zip) throws IOException {
        try (ZipFile zf = new ZipFile(zip)) {
            ZipEntry e = zf.getEntry(MANIFEST);
            if (e == null) throw new IOException("不是有效的备份文件（缺少清单）");
            JSONObject o = new JSONObject(readAll(zf.getInputStream(e)));
            if (!APP_ID.equals(o.optString("app"))) {
                throw new IOException("这不是「紫若灵刷题练习」的备份文件");
            }
            Info i = new Info();
            i.createdAt = o.optLong("createdAt");
            i.dbVersion = o.optInt("dbVersion", DB_VERSION);
            i.versionName = o.optString("versionName", "");
            i.banks = o.optInt("banks");
            i.questions = o.optInt("questions");
            i.records = o.optInt("records");
            if (i.dbVersion > DB_VERSION) {
                throw new IOException("备份来自更新版本的应用（库版本 " + i.dbVersion + "），请先升级本应用");
            }
            return i;
        } catch (JSONException e) {
            throw new IOException("备份清单损坏");
        }
    }

    // ==================== 导出 ====================

    public static void export(Context c, OutputStream out) throws IOException {
        SQLiteDatabase d = Db.db();
        try {
            // 把 WAL 落盘，保证主库文件自洽（非 WAL 模式下这句是空操作）
            Cursor ck = d.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null);
            ck.moveToFirst();
            ck.close();
        } catch (Exception ignored) {
        }

        ZipOutputStream z = new ZipOutputStream(new BufferedOutputStream(out, 64 * 1024));
        try {
            z.setLevel(java.util.zip.Deflater.BEST_SPEED);
            putManifest(z, c, d);

            File dbf = c.getDatabasePath("ziruoling_quiz.db");
            putFile(z, DB_ENTRY, dbf);
            File wal = new File(dbf.getPath() + "-wal");
            if (wal.isFile() && wal.length() > 0) putFile(z, DB_ENTRY + "-wal", wal);

            File imgDir = new File(c.getFilesDir(), "imgs");
            File[] imgs = imgDir.listFiles();
            if (imgs != null) {
                for (File f : imgs) {
                    if (f.isFile()) putFile(z, IMG_PREFIX + f.getName(), f);
                }
            }
            putPrefs(z, c, PREF_NAME, PREF_ENTRY);
            putPrefs(z, c, SNAKE_PREF_NAME, SNAKE_ENTRY);
            putSounds(z, c);
            z.finish();
        } finally {
            z.close();
        }
    }

    /** 覆盖导入前，把当前全部数据存成「原数据」快照（固定一份，后写覆盖前一份） */
    public static void saveSnapshot(Context c) throws IOException {
        File dst = snapshotFile(c);
        File tmp = new File(dst.getPath() + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            export(c, fos);
        }
        if (dst.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            dst.delete();
        }
        if (!tmp.renameTo(dst)) {
            throw new IOException("原数据快照写入失败");
        }
    }

    private static void putManifest(ZipOutputStream z, Context c, SQLiteDatabase d) throws IOException {
        JSONObject o = new JSONObject();
        try {
            o.put("app", APP_ID);
            o.put("createdAt", System.currentTimeMillis());
            o.put("versionName", appVersion(c));
            o.put("dbVersion", d.getVersion());
            o.put("banks", count(d, "banks"));
            o.put("questions", count(d, "questions"));
            o.put("records", count(d, "records"));
        } catch (JSONException ignored) {
        }
        ZipEntry e = new ZipEntry(MANIFEST);
        e.setTime(System.currentTimeMillis());
        z.putNextEntry(e);
        z.write(o.toString().getBytes("UTF-8"));
        z.closeEntry();
    }

    private static int count(SQLiteDatabase d, String table) {
        Cursor c = d.rawQuery("SELECT COUNT(*) FROM " + table, null);
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    private static String appVersion(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    private static void putFile(ZipOutputStream z, String entry, File f) throws IOException {
        if (!f.isFile()) return;
        ZipEntry e = new ZipEntry(entry);
        e.setTime(f.lastModified());
        z.putNextEntry(e);
        try (FileInputStream in = new FileInputStream(f)) {
            copy(in, z);
        }
        z.closeEntry();
    }

    /** 把一份 SharedPreferences 整体写成 JSON 条目（跳过 session_running，不把「本次会话」状态带出去） */
    private static void putPrefs(ZipOutputStream z, Context c, String prefName, String entry)
            throws IOException {
        SharedPreferences sp = c.getSharedPreferences(prefName, Context.MODE_PRIVATE);
        JSONObject o = new JSONObject();
        try {
            for (Map.Entry<String, ?> en : sp.getAll().entrySet()) {
                String k = en.getKey();
                if (SESSION_RUNNING.equals(k)) continue;
                Object v = en.getValue();
                if (v instanceof Boolean || v instanceof Integer || v instanceof Long
                        || v instanceof Float || v instanceof String) {
                    o.put(k, v);
                } else if (v instanceof java.util.Set) {
                    JSONArray a = new JSONArray();
                    for (Object item : (java.util.Set<?>) v) {
                        if (item != null) a.put(item.toString());
                    }
                    o.put(k, a);
                }
            }
        } catch (JSONException ignored) {
        }
        ZipEntry e = new ZipEntry(entry);
        e.setTime(System.currentTimeMillis());
        z.putNextEntry(e);
        z.write(o.toString().getBytes("UTF-8"));
        z.closeEntry();
    }

    /** 自定义答对/答错音效：filesDir 下 sound_ok_custom.* 与 sound_wrong_custom.* */
    private static void putSounds(ZipOutputStream z, Context c) throws IOException {
        File[] files = c.getFilesDir().listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!f.isFile() || !isSoundFile(f.getName())) continue;
            putFile(z, SOUND_PREFIX + f.getName(), f);
        }
    }

    /** 是否为自定义音效文件名（答对 / 答错） */
    private static boolean isSoundFile(String name) {
        return name.startsWith("sound_ok_custom.") || name.startsWith("sound_wrong_custom.");
    }

    // ==================== 追加导入 ====================

    public static final class Result {
        public int banks;
        public int questions;
        public int records;
    }

    /**
     * 追加导入：备份里的题库一律作为**独立新题库**加入（重名自动改名为「原名 (2)」），
     * 完全不改动现有题库的任何数据。
     *
     * @param questionsOnly true=只导入题库与题目（含图片），丢弃备份里的作答记录等；
     *                      false=连同作答记录/错题/收藏/考试/进度一起导入（题目 id 重新映射）
     */
    public static Result append(Context c, File zip, boolean questionsOnly) throws Exception {
        Result res = new Result();
        File tmpDb = extractToCache(c, zip, DB_ENTRY);
        SQLiteDatabase src = SQLiteDatabase.openDatabase(tmpDb.getPath(), null,
                SQLiteDatabase.OPEN_READONLY);
        ZipFile zf = new ZipFile(zip);
        SQLiteDatabase dst = Db.db();
        try {
            Map<Long, Long> qMap = new HashMap<>();
            Map<Long, Long> bankMap = new HashMap<>();
            Map<Long, Integer> typeMap = new HashMap<>();
            Map<Long, List<Long>> flaggedByBank = new HashMap<>();
            boolean srcHasTags = hasTable(src, "tags");
            dst.beginTransaction();
            try {
                Cursor b = src.rawQuery("SELECT id, name, source, created_at, last_at FROM banks ORDER BY id", null);
                while (b.moveToNext()) {
                    long oldBankId = b.getLong(0);
                    long newBankId = insertBank(dst, uniqueBankName(dst, b.getString(1)),
                            b.getString(2), b.getLong(3), b.getLong(4));
                    bankMap.put(oldBankId, newBankId);
                    res.banks++;

                    Cursor q = src.rawQuery("SELECT * FROM questions WHERE bank_id=? ORDER BY qindex, id",
                            new String[]{String.valueOf(oldBankId)});
                    int imgCol = q.getColumnIndex("img_paths");
                    while (q.moveToNext()) {
                        long oldQid = q.getLong(q.getColumnIndexOrThrow("id"));
                        ContentValues v = new ContentValues();
                        v.put("bank_id", newBankId);
                        v.put("qindex", q.getInt(q.getColumnIndexOrThrow("qindex")));
                        v.put("type", q.getInt(q.getColumnIndexOrThrow("type")));
                        v.put("content", q.getString(q.getColumnIndexOrThrow("content")));
                        v.put("options", q.getString(q.getColumnIndexOrThrow("options")));
                        v.put("answer", q.getString(q.getColumnIndexOrThrow("answer")));
                        v.put("analysis", q.getString(q.getColumnIndexOrThrow("analysis")));
                        v.put("category", q.getString(q.getColumnIndexOrThrow("category")));
                        v.put("flagged", q.getInt(q.getColumnIndexOrThrow("flagged")));
                        v.put("img_paths", "[]");
                        int docCol = q.getColumnIndex("doc_no");
                        if (docCol >= 0) v.put("doc_no", q.getInt(docCol));
                        long newQid = dst.insert("questions", null, v);
                        qMap.put(oldQid, newQid);
                        typeMap.put(newQid, v.getAsInteger("type"));
                        if (v.getAsInteger("flagged") != null && v.getAsInteger("flagged") == 1) {
                            List<Long> fq = flaggedByBank.get(newBankId);
                            if (fq == null) {
                                fq = new ArrayList<>();
                                flaggedByBank.put(newBankId, fq);
                            }
                            fq.add(newQid);
                        }
                        res.questions++;
                        if (imgCol >= 0) {
                            String mapped = copyImages(c, zf, q.getString(imgCol), newQid);
                            if (mapped != null && !"[]".equals(mapped)) {
                                ContentValues uv = new ContentValues();
                                uv.put("img_paths", mapped);
                                dst.update("questions", uv, "id=?", new String[]{String.valueOf(newQid)});
                            }
                        }
                    }
                    q.close();
                }
                b.close();

                // 旧备份没有词条表：把 flagged=1 的题迁移成「疑错」词条，避免旧标记"隐身"
                if (!srcHasTags) {
                    for (Map.Entry<Long, List<Long>> e : flaggedByBank.entrySet()) {
                        long tagId = ensureTag(dst, e.getKey(), "疑错");
                        for (long nq : e.getValue()) {
                            ContentValues tv = new ContentValues();
                            tv.put("question_id", nq);
                            tv.put("tag_id", tagId);
                            dst.insertWithOnConflict("question_tags", null, tv,
                                    SQLiteDatabase.CONFLICT_IGNORE);
                        }
                    }
                }

                if (!questionsOnly) {
                    res.records = copyRecords(dst, src, qMap, bankMap, typeMap);
                    copyWrongs(dst, src, qMap, bankMap);
                    copyFavs(dst, src, qMap, bankMap);
                    copyExams(dst, src, qMap, bankMap);
                    copyProgress(dst, src, qMap, bankMap);
                    copyTags(dst, src, qMap, bankMap);
                }
                dst.setTransactionSuccessful();
            } finally {
                dst.endTransaction();
            }
        } finally {
            zf.close();
            src.close();
            //noinspection ResultOfMethodCallIgnored
            tmpDb.delete();
        }
        return res;
    }

    private static String uniqueBankName(SQLiteDatabase d, String name) {
        String base = name == null || name.trim().isEmpty() ? "导入题库" : name.trim();
        String candidate = base;
        int n = 2;
        while (bankNameExists(d, candidate)) {
            candidate = base + " (" + n + ")";
            n++;
        }
        return candidate;
    }

    private static boolean bankNameExists(SQLiteDatabase d, String name) {
        Cursor c = d.rawQuery("SELECT 1 FROM banks WHERE name=? LIMIT 1", new String[]{name});
        boolean exists = c.moveToFirst();
        c.close();
        return exists;
    }

    private static long insertBank(SQLiteDatabase d, String name, String source, long created, long last) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("source", source == null ? "导入" : source);
        v.put("created_at", created > 0 ? created : System.currentTimeMillis());
        v.put("last_at", Math.max(0, last));
        return d.insert("banks", null, v);
    }

    /** 复制一张题目的图片，返回新的 img_paths JSON（失败返回 null） */
    private static String copyImages(Context c, ZipFile zf, String oldPaths, long newQid) {
        if (oldPaths == null || oldPaths.isEmpty()) return null;
        List<String> rels = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(oldPaths);
            File dir = new File(c.getFilesDir(), "imgs");
            if (!dir.exists() && !dir.mkdirs()) return null;
            int n = 0;
            for (int i = 0; i < a.length(); i++) {
                String rel = a.optString(i, "");
                if (rel.isEmpty()) continue;
                String name = rel.substring(rel.lastIndexOf('/') + 1);
                ZipEntry e = zf.getEntry(IMG_PREFIX + name);
                if (e == null) continue;
                String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : ".png";
                String newName = "q" + newQid + "_" + (n++) + ext;
                try (InputStream in = zf.getInputStream(e);
                     FileOutputStream out = new FileOutputStream(new File(dir, newName))) {
                    copy(in, out);
                }
                rels.add("imgs/" + newName);
            }
        } catch (Exception e) {
            return null;
        }
        if (rels.isEmpty()) return null;
        return new JSONArray(rels).toString();
    }

    private static int copyRecords(SQLiteDatabase dst, SQLiteDatabase src,
                                   Map<Long, Long> qMap, Map<Long, Long> bankMap,
                                   Map<Long, Integer> typeMap) {
        int n = 0;
        Cursor c = src.rawQuery("SELECT bank_id, question_id, mode, user_answer, correct, answered_at"
                + " FROM records", null);
        while (c.moveToNext()) {
            Long nq = qMap.get(c.getLong(1));
            Long nb = bankMap.get(c.getLong(0));
            if (nq == null || nb == null) continue;
            ContentValues v = new ContentValues();
            v.put("bank_id", nb);
            v.put("question_id", nq);
            v.put("mode", c.getInt(2));
            v.put("user_answer", c.getString(3));
            v.put("correct", c.getInt(4));
            long at = c.getLong(5);
            v.put("answered_at", at);
            if (dst.insert("records", null, v) > 0) {
                n++;
                // 导入进来的作答记录同样计入总统计账本（与手动答题一致）
                Integer type = typeMap.get(nq);
                Db.bumpStats(1, c.getInt(4), at / 86400000L, type == null ? 0 : type);
            }
        }
        c.close();
        return n;
    }

    private static void copyWrongs(SQLiteDatabase dst, SQLiteDatabase src,
                                   Map<Long, Long> qMap, Map<Long, Long> bankMap) {
        Cursor c = src.rawQuery("SELECT bank_id, question_id, wrong_count, resolved, updated_at"
                + " FROM wrongs", null);
        while (c.moveToNext()) {
            Long nq = qMap.get(c.getLong(1));
            Long nb = bankMap.get(c.getLong(0));
            if (nq == null || nb == null) continue;
            ContentValues v = new ContentValues();
            v.put("bank_id", nb);
            v.put("question_id", nq);
            v.put("wrong_count", c.getInt(2));
            v.put("resolved", c.getInt(3));
            v.put("updated_at", c.getLong(4));
            dst.insertWithOnConflict("wrongs", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        }
        c.close();
    }

    private static void copyFavs(SQLiteDatabase dst, SQLiteDatabase src,
                                 Map<Long, Long> qMap, Map<Long, Long> bankMap) {
        Cursor c = src.rawQuery("SELECT bank_id, question_id, created_at FROM favs", null);
        while (c.moveToNext()) {
            Long nq = qMap.get(c.getLong(1));
            Long nb = bankMap.get(c.getLong(0));
            if (nq == null || nb == null) continue;
            ContentValues v = new ContentValues();
            v.put("bank_id", nb);
            v.put("question_id", nq);
            v.put("created_at", c.getLong(2));
            dst.insertWithOnConflict("favs", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        }
        c.close();
    }

    private static void copyExams(SQLiteDatabase dst, SQLiteDatabase src,
                                  Map<Long, Long> qMap, Map<Long, Long> bankMap) {
        Cursor c = src.rawQuery("SELECT bank_id, name, total, done, correct, score, full, duration,"
                + " finished_at, wrong_ids, detail FROM exams", null);
        while (c.moveToNext()) {
            Long newBankId = bankMap.get(c.getLong(0));
            if (newBankId == null) continue;
            JSONArray mapped = new JSONArray();
            try {
                JSONArray a = new JSONArray(c.getString(9) == null ? "[]" : c.getString(9));
                for (int i = 0; i < a.length(); i++) {
                    Long nq = qMap.get(a.getLong(i));
                    if (nq != null) mapped.put(nq);
                }
            } catch (JSONException ignored) {
            }
            ContentValues v = new ContentValues();
            v.put("bank_id", newBankId);
            v.put("name", c.getString(1));
            v.put("total", c.getInt(2));
            v.put("done", c.getInt(3));
            v.put("correct", c.getInt(4));
            v.put("score", c.getDouble(5));
            v.put("full", c.getDouble(6));
            v.put("duration", c.getInt(7));
            v.put("finished_at", c.getLong(8));
            v.put("wrong_ids", mapped.toString());
            v.put("detail", c.getString(10));
            dst.insert("exams", null, v);
        }
        c.close();
    }

    private static boolean hasTable(SQLiteDatabase d, String name) {
        Cursor c = d.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
                new String[]{name});
        boolean b = c.moveToFirst();
        c.close();
        return b;
    }

    /** 取（或创建）某题库下指定名字的普通词条（非内置） */
    private static long ensureTag(SQLiteDatabase d, long bankId, String name) {
        Cursor c = d.rawQuery("SELECT id FROM tags WHERE bank_id=? AND name=? AND builtin=0 LIMIT 1",
                new String[]{String.valueOf(bankId), name});
        long id = c.moveToFirst() ? c.getLong(0) : 0;
        c.close();
        if (id > 0) return id;
        ContentValues v = new ContentValues();
        v.put("bank_id", bankId);
        v.put("name", name);
        v.put("is_default", 0);
        v.put("sort", 100);
        v.put("builtin", 0);
        return d.insert("tags", null, v);
    }

    /** 复制标记词条与题-词条关联（旧备份没有这两张表，忽略即可） */
    private static void copyTags(SQLiteDatabase dst, SQLiteDatabase src,
                                 Map<Long, Long> qMap, Map<Long, Long> bankMap) {
        try {
            Map<Long, Long> tagMap = new HashMap<>();
            Cursor c = src.rawQuery("SELECT id, bank_id, name, is_default, sort FROM tags", null);
            while (c.moveToNext()) {
                Long nb = bankMap.get(c.getLong(1));
                if (nb == null) continue;
                ContentValues v = new ContentValues();
                v.put("bank_id", nb);
                v.put("name", c.getString(2));
                v.put("is_default", c.getInt(3));
                v.put("sort", c.getInt(4));
                tagMap.put(c.getLong(0), dst.insert("tags", null, v));
            }
            c.close();

            Cursor q = src.rawQuery("SELECT question_id, tag_id FROM question_tags", null);
            while (q.moveToNext()) {
                Long nq = qMap.get(q.getLong(0));
                Long nt = tagMap.get(q.getLong(1));
                if (nq == null || nt == null) continue;
                ContentValues v = new ContentValues();
                v.put("question_id", nq);
                v.put("tag_id", nt);
                dst.insertWithOnConflict("question_tags", null, v, SQLiteDatabase.CONFLICT_IGNORE);
            }
            q.close();
        } catch (Exception ignored) {
            // 旧版备份里没有词条表
        }
    }

    private static void copyProgress(SQLiteDatabase dst, SQLiteDatabase src,
                                     Map<Long, Long> qMap, Map<Long, Long> bankMap) {
        Cursor c = src.rawQuery("SELECT bank_id, last_qid, updated_at FROM progress", null);
        while (c.moveToNext()) {
            Long newBankId = bankMap.get(c.getLong(0));
            Long nq = qMap.get(c.getLong(1));
            if (newBankId == null || nq == null) continue;
            ContentValues v = new ContentValues();
            v.put("bank_id", newBankId);
            v.put("last_qid", nq);
            v.put("updated_at", c.getLong(2));
            dst.insertWithOnConflict("progress", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        }
        c.close();
    }

    // ==================== 覆盖导入 ====================

    /**
     * 用备份整体替换当前数据；备份里带设置 / 彩蛋统计 / 自定义音效时一并恢复
     * （旧备份没有这些条目时保持当前设置）。
     *
     * @param snapshotCurrent 是否先把当前数据存成「原数据」快照
     */
    public static void replaceWith(Context c, File zip, boolean snapshotCurrent) throws Exception {
        if (snapshotCurrent) saveSnapshot(c);

        File tmpDb = extractToCache(c, zip, DB_ENTRY);
        File tmpImgs = new File(c.getCacheDir(), "restore_imgs");
        deleteRecursive(tmpImgs);
        //noinspection ResultOfMethodCallIgnored
        tmpImgs.mkdirs();
        try (ZipFile zf = new ZipFile(zip)) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory() || !e.getName().startsWith(IMG_PREFIX)) continue;
                String name = e.getName().substring(IMG_PREFIX.length());
                if (name.contains("/")) continue;
                try (InputStream in = zf.getInputStream(e);
                     FileOutputStream out = new FileOutputStream(new File(tmpImgs, name))) {
                    copy(in, out);
                }
            }
        }

        File target = c.getDatabasePath("ziruoling_quiz.db");
        Db.closeForRestore();
        try {
            //noinspection ResultOfMethodCallIgnored
            new File(target.getPath() + "-wal").delete();
            //noinspection ResultOfMethodCallIgnored
            new File(target.getPath() + "-shm").delete();
            //noinspection ResultOfMethodCallIgnored
            if (target.isFile()) target.delete();
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            try (InputStream in = new FileInputStream(tmpDb);
                 FileOutputStream out = new FileOutputStream(target)) {
                copy(in, out);
            }

            File imgDir = new File(c.getFilesDir(), "imgs");
            deleteRecursive(imgDir);
            //noinspection ResultOfMethodCallIgnored
            imgDir.mkdirs();
            File[] files = tmpImgs.listFiles();
            if (files != null) {
                for (File f : files) {
                    try (InputStream in = new FileInputStream(f);
                         FileOutputStream out = new FileOutputStream(new File(imgDir, f.getName()))) {
                        copy(in, out);
                    }
                }
            }
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmpDb.delete();
            deleteRecursive(tmpImgs);
            Db.init(c);
        }

        // 备份里的设置 / 彩蛋统计 / 自定义音效（旧备份没有这些条目时保持当前设置）
        restoreExtras(c, zip);
    }

    // ==================== 设置 / 彩蛋 / 音效（扩展备份） ====================

    /** 覆盖恢复后：按需恢复设置 / 彩蛋统计 / 自定义音效（旧备份没有对应条目就跳过） */
    private static void restoreExtras(Context c, File zip) throws IOException {
        try (ZipFile zf = new ZipFile(zip)) {
            restorePrefs(c, zf, PREF_ENTRY, PREF_NAME, true);
            restorePrefs(c, zf, SNAKE_ENTRY, SNAKE_PREF_NAME, false);
            restoreSounds(c, zf);
        }
    }

    /**
     * 恢复一份 SharedPreferences。
     *
     * @param remapSound 是否需要把自定义音效的旧绝对路径改到当前 filesDir
     */
    private static void restorePrefs(Context c, ZipFile zf, String entry, String prefName,
                                     boolean remapSound) throws IOException {
        ZipEntry e = zf.getEntry(entry);
        if (e == null) return;
        JSONObject o;
        try {
            o = new JSONObject(readAll(zf.getInputStream(e)));
        } catch (JSONException ex) {
            return; // 条目损坏就当没有，保持当前设置
        }
        SharedPreferences sp = c.getSharedPreferences(prefName, Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        java.util.Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            if (SESSION_RUNNING.equals(k)) continue;
            Object v = o.opt(k);
            if (v == null || v == JSONObject.NULL) continue;
            if (v instanceof Boolean) {
                ed.putBoolean(k, (Boolean) v);
            } else if (v instanceof Number) {
                putNumber(ed, k, (Number) v);
            } else if (v instanceof String) {
                String s = (String) v;
                // 自定义音效存的是绝对路径，换设备/换包名后要指到当前包的 filesDir
                if (remapSound && ("sound_ok_path".equals(k) || "sound_wrong_path".equals(k))
                        && !s.trim().isEmpty()) {
                    s = new File(c.getFilesDir(), new File(s).getName()).getAbsolutePath();
                }
                ed.putString(k, s);
            } else if (v instanceof JSONArray) {
                JSONArray a = (JSONArray) v;
                java.util.Set<String> set = new java.util.HashSet<>();
                for (int i = 0; i < a.length(); i++) set.add(a.optString(i, ""));
                ed.putStringSet(k, set);
            }
        }
        ed.apply();
    }

    /** 数字设置写回：JSON 不区分 int/long/float，按原类型恢复（totalMs=long，bestF=float） */
    private static void putNumber(SharedPreferences.Editor ed, String k, Number v) {
        if (FLOAT_PREF_KEYS.contains(k) || v instanceof Double || v instanceof Float) {
            ed.putFloat(k, v.floatValue());
        } else if (LONG_PREF_KEYS.contains(k) || v instanceof Long) {
            ed.putLong(k, v.longValue());
        } else {
            ed.putInt(k, v.intValue());
        }
    }

    /** 恢复自定义音效：有 sound/ 条目时先删当前自定义音效，再逐个写回 */
    private static void restoreSounds(Context c, ZipFile zf) throws IOException {
        List<String> names = new ArrayList<>();
        Enumeration<? extends ZipEntry> en = zf.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (e.isDirectory() || !e.getName().startsWith(SOUND_PREFIX)) continue;
            String name = e.getName().substring(SOUND_PREFIX.length());
            if (name.contains("/") || !isSoundFile(name)) continue;
            names.add(name);
        }
        if (names.isEmpty()) return;
        File dir = c.getFilesDir();
        File[] old = dir.listFiles();
        if (old != null) {
            for (File f : old) {
                if (!f.isFile() || !isSoundFile(f.getName())) continue;
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
        for (String name : names) {
            ZipEntry e = zf.getEntry(SOUND_PREFIX + name);
            if (e == null) continue;
            try (InputStream in = zf.getInputStream(e);
                 FileOutputStream out = new FileOutputStream(new File(dir, name))) {
                copy(in, out);
            }
        }
    }

    // ==================== 工具 ====================

    private static File extractToCache(Context c, File zip, String entry) throws IOException {
        File out = new File(c.getCacheDir(), "restore_db.tmp");
        try (ZipFile zf = new ZipFile(zip)) {
            ZipEntry e = zf.getEntry(entry);
            if (e == null) throw new IOException("备份里没有数据库文件");
            try (InputStream in = zf.getInputStream(e);
                 FileOutputStream fos = new FileOutputStream(out)) {
                copy(in, fos);
            }
        }
        return out;
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            copy(in, bos);
        } finally {
            in.close();
        }
        return new String(bos.toByteArray(), "UTF-8");
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) deleteRecursive(k);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
