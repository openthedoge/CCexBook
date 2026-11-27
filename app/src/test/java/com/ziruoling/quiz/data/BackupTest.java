package com.ziruoling.quiz.data;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** 整库备份：导出 → 识别 → 追加 / 覆盖 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BackupTest {

    private Context app;

    @Before
    public void setUp() {
        Db.resetForTest();
        Prefs.resetForTest();
        app = RuntimeEnvironment.getApplication();
        Db.init(app);
        Prefs.init(app);
        Backup.deleteSnapshot(app);
    }

    private long seedBank() {
        long bankId = Db.createBank("测试题库", "测试");
        Db.importQuestions(bankId, DemoBank.build());
        List<Long> ids = Db.questionIds(bankId);
        Db.addRecord(bankId, ids.get(0), 0, "A", true, true);
        return bankId;
    }

    private File export() throws Exception {
        File zip = new File(app.getCacheDir(), "test_backup.zip");
        try (FileOutputStream fos = new FileOutputStream(zip)) {
            Backup.export(app, fos);
        }
        return zip;
    }

    @Test
    public void exportThenInspect() throws Exception {
        seedBank();
        File zip = export();
        assertTrue("导出的备份应非空", zip.length() > 0);

        Backup.Info info = Backup.inspect(zip);
        assertEquals("数据库版本", 13, info.dbVersion);
        assertEquals(1, info.banks);
        assertEquals(37, info.questions);
        assertEquals(1, info.records);
        assertTrue(info.describe().contains("题库 1 个"));
    }

    @Test
    public void appendQuestionsOnlyKeepsRecordsUntouched() throws Exception {
        seedBank();
        File zip = export();
        long recordsBefore = Db.globalStats()[0];

        Backup.Result r = Backup.append(app, zip, true);
        assertEquals(1, r.banks);
        assertEquals(37, r.questions);
        assertEquals(0, r.records);

        List<Bank> banks = Db.listBanks();
        assertEquals(2, banks.size());
        assertTrue("重名题库应改名为 (2)", hasBank(banks, "测试题库 (2)"));
        assertEquals("初始化导入不应带来作答记录", recordsBefore, Db.globalStats()[0]);
    }

    @Test
    public void appendWithRecordsRemapsIds() throws Exception {
        long bankId = seedBank();
        File zip = export();
        long recordsBefore = Db.globalStats()[0];

        Backup.Result r = Backup.append(app, zip, false);
        assertEquals(1, r.records);
        assertEquals(recordsBefore + 1, Db.globalStats()[0]);

        List<Bank> banks = Db.listBanks();
        assertEquals(2, banks.size());
        assertTrue(hasBank(banks, "测试题库 (2)"));

        // 复制出来的记录必须挂在新题库的题目上，且原题库统计不受影响
        Bank origin = null;
        Bank copy = null;
        for (Bank b : banks) {
            if ("测试题库".equals(b.name)) origin = b;
            if ("测试题库 (2)".equals(b.name)) copy = b;
        }
        assertNotNull(origin);
        assertNotNull(copy);
        assertEquals("原题库已做数不应变化", 1, origin.doneCount);
        assertEquals("导入题库应带上已做数", 1, copy.doneCount);
        assertEquals(bankId, origin.id);
    }

    @Test
    public void replaceWithRestoresWholeSnapshot() throws Exception {
        long bankId = seedBank();
        File zip = export();
        Db.createBank("临时题库", "手建");
        assertEquals(2, Db.listBanks().size());

        Backup.replaceWith(app, zip, false);

        assertEquals(1, Db.listBanks().size());
        assertEquals("测试题库", Db.listBanks().get(0).name);
        assertEquals(1, Db.globalStats()[0]);
        assertEquals(bankId, Db.listBanks().get(0).id);
        assertFalse("没有要求快照时不应产生原数据", Backup.hasSnapshot(app));
    }

    @Test
    public void snapshotLifecycle() throws Exception {
        seedBank();
        assertFalse(Backup.hasSnapshot(app));
        Backup.saveSnapshot(app);
        assertTrue(Backup.hasSnapshot(app));
        assertTrue(Backup.snapshotFile(app).length() > 0);

        // 快照本身应可被识别
        Backup.Info info = Backup.inspect(Backup.snapshotFile(app));
        assertEquals(1, info.banks);

        Backup.deleteSnapshot(app);
        assertFalse(Backup.hasSnapshot(app));
    }

    @Test
    public void inspectRejectsForeignZip() throws Exception {
        File zip = new File(app.getCacheDir(), "bad.zip");
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(
                new FileOutputStream(zip))) {
            z.putNextEntry(new java.util.zip.ZipEntry("hello.txt"));
            z.write("not a backup".getBytes("UTF-8"));
            z.closeEntry();
        }
        boolean thrown = false;
        try {
            Backup.inspect(zip);
        } catch (Exception e) {
            thrown = true;
        }
        assertTrue("非备份文件应被拒绝", thrown);
    }

    private boolean hasBank(List<Bank> banks, String name) {
        for (Bank b : banks) {
            if (name.equals(b.name)) return true;
        }
        return false;
    }
}
