package com.ziruoling.quiz.core;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** TXT / CSV 解析测试（含 GBK 编码、内联选项、大节标题等真实题库常见写法） */
public class TxtCsvParserTest {

    @Test
    public void testBasicSingle() {
        String text = "[单选] OSI 参考模型共分为几层？\n"
                + "A. 4层\nB. 5层\nC. 6层\nD. 7层\n"
                + "答案：D\n解析：从物理层到应用层共7层。\n分类：网络基础\n";
        ParseResult r = TxtParser.parseText(text);
        assertEquals(1, r.questions.size());
        ParsedQuestion q = r.questions.get(0);
        assertEquals(QuestionType.SINGLE, q.type);
        assertEquals("OSI 参考模型共分为几层？", q.content);
        assertEquals(4, q.options.size());
        assertEquals("D", q.answer);
        assertEquals("网络基础", q.category);
        assertTrue(q.analysis.contains("7层"));
        assertEquals(0, r.warnings.size());
    }

    @Test
    public void testBlankLineSeparate() {
        String text = "1. 光纤到户的英文缩写是？\nA.FTTB\nB.FTTH\nC.FTTO\nD.FTTC\n答案：B\n\n"
                + "2. 下列属于无线局域网标准的是？\nA.802.3\nB.802.11\n答案：B\n";
        ParseResult r = TxtParser.parseText(text);
        assertEquals(2, r.questions.size());
        assertEquals("光纤到户的英文缩写是？", r.questions.get(0).content);
        assertEquals("B", r.questions.get(0).answer);
        assertEquals("下列属于无线局域网标准的是？", r.questions.get(1).content);
    }

    @Test
    public void testSectionAndJudge() {
        String text = "一、判断题\n"
                + "1、ping 命令基于 ICMP 协议实现。\n答案：对\n\n"
                + "2、交换机工作在 OSI 的网络层。\n答案：错误\n\n"
                + "二、填空题\n"
                + "1、IPv4 地址由____位二进制数组成。\n答案：32\n";
        ParseResult r = TxtParser.parseText(text);
        assertEquals(3, r.questions.size());
        assertEquals(QuestionType.JUDGE, r.questions.get(0).type);
        assertEquals("A", r.questions.get(0).answer);
        assertEquals("B", r.questions.get(1).answer);
        assertEquals(QuestionType.FILL, r.questions.get(2).type);
        assertEquals("32", r.questions.get(2).answer);
    }

    @Test
    public void testChapterAndMulti() {
        String text = "第一章 网络基础\n"
                + "[多选] 下列属于 TCP/IP 模型网络层协议的有：\nA.IP\nB.ICMP\nC.TCP\nD.ARP\n"
                + "答案：ABD\n解析：TCP 属于传输层。\n\n"
                + "[判断] 子网掩码 255.255.255.0 可容纳 254 台主机。\n答案：正确\n";
        ParseResult r = TxtParser.parseText(text);
        assertEquals(2, r.questions.size());
        assertEquals("第一章 网络基础", r.questions.get(0).category);
        assertEquals("ABD", r.questions.get(0).answer);
        assertEquals(QuestionType.JUDGE, r.questions.get(1).type);
        assertEquals("A", r.questions.get(1).answer);
    }

    @Test
    public void testInlineOptionsAndTagEnd() {
        String text = "[判断] 5类非屏蔽双绞线最大传输距离为100米。A.对 B.错\n答案：A\n\n"
                + "IPv6 地址长度为128位。[单选]\nA.对\nB.错\n答案：A\n";
        ParseResult r = TxtParser.parseText(text);
        assertEquals(2, r.questions.size());
        assertEquals(QuestionType.JUDGE, r.questions.get(0).type);
        assertEquals("A", r.questions.get(0).answer);
        assertEquals(QuestionType.SINGLE, r.questions.get(1).type);
    }

    @Test
    public void testGbkDecode() throws IOException {
        String text = "[单选] 中国电信的业务品牌包括？\nA.天翼\nB.移动\n答案：A\n";
        File f = File.createTempFile("gbk", ".txt");
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(text.getBytes(Charset.forName("GBK")));
        }
        ParseResult r = new TxtParser().parse(f);
        assertEquals(1, r.questions.size());
        assertTrue(r.questions.get(0).options.get(0).contains("天翼"));
        f.delete();
    }

    @Test
    public void testMultiLineStem() {
        String text = "[简答] 请简述 ARP 协议的作用，\n并说明其工作原理。\n答案：地址解析协议，将IP地址映射为MAC地址。\n";
        ParseResult r = TxtParser.parseText(text);
        assertEquals(1, r.questions.size());
        assertTrue(r.questions.get(0).content.contains("并说明其工作原理"));
        assertEquals(QuestionType.SHORT, r.questions.get(0).type);
    }

    @Test
    public void testCsvWithQuotesAndGbk() throws IOException {
        String csv = "题型,题目,选项A,选项B,选项C,选项D,答案,解析,分类\n"
                + "单选题,\"下列哪个是‘路由,器’的主要功能？\",转发,显示,存储,打印,B,,网络设备\n";
        File f = File.createTempFile("csv", ".csv");
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(csv.getBytes(Charset.forName("GBK")));
        }
        ParseResult r = new CsvParser().parse(f);
        assertEquals(1, r.questions.size());
        assertEquals("B", r.questions.get(0).answer);
        assertTrue(r.questions.get(0).content.contains("路由,器"));
        f.delete();
    }

    @Test
    public void testCsvSemicolonDetect() throws IOException {
        String csv = "题目;A;B;答案\n题干1;甲;乙;A\n";
        File f = File.createTempFile("csv2", ".csv");
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(csv.getBytes(StandardCharsets.UTF_8));
        }
        ParseResult r = new CsvParser().parse(f);
        assertEquals(1, r.questions.size());
        assertEquals("A", r.questions.get(0).answer);
        f.delete();
    }

    // ---------- 办公文档 zip 构造辅助（供 Xlsx/Docx 测试复用） ----------

    static File buildZip(String prefix, Map<String, String> entries) throws IOException {
        File f = File.createTempFile(prefix, ".tmp");
        f.deleteOnExit();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return f;
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static String sheetWithShared(List<String> cells) {
        // cells 按 (row,col) 顺序传入，构造 sharedStrings + sheet
        StringBuilder ss = new StringBuilder();
        ss.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
          .append("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"")
          .append(cells.size()).append("\" uniqueCount=\"").append(cells.size()).append("\">");
        for (String c : cells) ss.append("<si><t>").append(esc(c)).append("</t></si>");
        ss.append("</sst>");
        return ss.toString();
    }

    static List<String> toList(String[] arr) {
        List<String> l = new ArrayList<>();
        for (String s : arr) l.add(s);
        return l;
    }
}
