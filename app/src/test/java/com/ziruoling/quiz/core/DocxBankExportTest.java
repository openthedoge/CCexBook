package com.ziruoling.quiz.core;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** 题库导出为 Word 表格：应内嵌图片，且能被本应用重新导入 */
public class DocxBankExportTest {

    /** 1x1 PNG */
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private File exportDocx() throws Exception {
        DocxWriter.PaperQ q1 = new DocxWriter.PaperQ();
        q1.type = QuestionType.SINGLE;
        q1.content = "带图单选题";
        q1.options = Arrays.asList("甲", "乙", "丙");
        q1.answer = "A";
        q1.analysis = "解析文字";
        q1.category = "第1篇";
        q1.images.add(PNG);

        DocxWriter.PaperQ q2 = new DocxWriter.PaperQ();
        q2.type = QuestionType.JUDGE;
        q2.content = "判断题";
        q2.options = Arrays.asList("正确", "错误");
        q2.answer = "对";
        q2.category = "第2篇";

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DocxWriter.writeBank(bos, "测试题库", Arrays.asList(q1, q2));

        // 固定落一份到 build/tmp，便于用 LibreOffice 打开做可视化校验
        File sample = new File("build/tmp/bank_export_sample.docx");
        File dir = sample.getParentFile();
        if (dir != null && !dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        Files.write(sample.toPath(), bos.toByteArray());

        File f = File.createTempFile("bank_export", ".docx");
        f.deleteOnExit();
        Files.write(f.toPath(), bos.toByteArray());
        return f;
    }

    @Test
    public void docxContainsMediaAndRels() throws Exception {
        File f = exportDocx();
        try (ZipFile zf = new ZipFile(f)) {
            assertNotNull("应内嵌图片", zf.getEntry("word/media/image1.png"));
            assertNotNull("应有 document.xml.rels", zf.getEntry("word/_rels/document.xml.rels"));
            assertNotNull(zf.getEntry("word/document.xml"));
            assertNotNull(zf.getEntry("[Content_Types].xml"));
        }
    }

    @Test
    public void exportedDocxCanBeImportedBackWithImages() throws Exception {
        File f = exportDocx();
        ParseResult r = new DocxParser().parse(f);
        assertEquals("应解析回两题", 2, r.questions.size());

        int imgs = 0;
        boolean foundContent = false;
        for (ParsedQuestion q : r.questions) {
            imgs += q.images.size();
            if (q.content.contains("带图单选题")) foundContent = true;
        }
        assertEquals("图片应随题目一起被重新导入", 1, imgs);
        assertTrue(foundContent);
    }

    /** 试卷导出也必须带图（之前只写了「请在应用中查看」的提示文字） */
    @Test
    public void paperExportEmbedsImages() throws Exception {
        DocxWriter.PaperQ q = new DocxWriter.PaperQ();
        q.type = QuestionType.SINGLE;
        q.content = "带图单选题";
        q.options = Arrays.asList("甲", "乙");
        q.answer = "A";
        q.analysis = "解析";
        q.images.add(PNG);
        q.hasImage = true;

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DocxWriter.writePaper(bos, "试卷", Arrays.asList(q), true);
        File sample = new File("build/tmp/paper_export_sample.docx");
        File dir = sample.getParentFile();
        if (dir != null && !dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        Files.write(sample.toPath(), bos.toByteArray());

        File f = File.createTempFile("paper_export", ".docx");
        f.deleteOnExit();
        Files.write(f.toPath(), bos.toByteArray());

        try (ZipFile zf = new ZipFile(f)) {
            assertNotNull("试卷应内嵌图片", zf.getEntry("word/media/image1.png"));
            java.util.zip.ZipEntry doc = zf.getEntry("word/document.xml");
            assertNotNull(doc);
            java.io.InputStream in = zf.getInputStream(doc);
            java.io.ByteArrayOutputStream xmlBuf = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) xmlBuf.write(buf, 0, n);
            in.close();
            String xml = new String(xmlBuf.toByteArray(), "UTF-8");
            assertTrue("应写入 w:drawing", xml.contains("<w:drawing>"));
            assertFalse("有图时不应再写“请在应用中查看”",
                    xml.contains("请在「紫若灵刷题练习」应用中查看"));
        }
    }

    @Test
    public void imageExtAndSize() {        assertEquals("png", DocxWriter.imageExt(PNG));
        int[] wh = DocxWriter.imageSize(PNG);
        assertNotNull(wh);
        assertEquals(1, wh[0]);
        assertEquals(1, wh[1]);

        long[] emu = DocxWriter.extentFor(PNG);
        assertEquals("1x1 图片宽度应按上限铺满", 90L * 36000, emu[0]);
        assertEquals(90L * 36000, emu[1]);
    }
}
