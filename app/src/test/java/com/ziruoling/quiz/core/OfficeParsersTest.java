package com.ziruoling.quiz.core;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** xlsx / docx 解析测试：手工构造与 Office 实际结构一致的 zip 文档 */
public class OfficeParsersTest {

    private static final String CT = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
            + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
            + "</Types>";

    private static final String RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
            + "</Relationships>";

    private static final String WB_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
            + "</Relationships>";

    private static final String WB = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
            + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
            + "<sheets><sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>";

    private String cell(String ref, int sharedIdx) {
        return "<c r=\"" + ref + "\" t=\"s\"><v>" + sharedIdx + "</v></c>";
    }

    private String cellInline(String ref, String text) {
        return "<c r=\"" + ref + "\" t=\"inlineStr\"><is><t>" + TxtCsvParserTest.esc(text) + "</t></is></c>";
    }

    /** 数值单元格 + 样式索引（百分比） */
    private String cellNumStyle(String ref, int style, String val) {
        return "<c r=\"" + ref + "\" s=\"" + style + "\"><v>" + val + "</v></c>";
    }

    /** Excel 百分比格式单元格按显示格式还原：0.5 → "50%（含内置与自定义百分比格式） */
    @Test
    public void testXlsxPercentFormat() throws Exception {
        // styles：xf 索引 1 = 内置 9（0%），索引 2 = 自定义 164（0.0%）
        String styles = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<numFmts><numFmt numFmtId=\"164\" formatCode=\"0.0%\"/></numFmts>"
                + "<cellXfs>"
                + "<xf numFmtId=\"0\"/>"
                + "<xf numFmtId=\"9\"/>"
                + "<xf numFmtId=\"164\"/>"
                + "</cellXfs></styleSheet>";
        StringBuilder sh = new StringBuilder();
        sh.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
          .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        // 表头
        sh.append("<row r=\"1\">")
          .append(cellInline("A1", "题型")).append(cellInline("B1", "题目"))
          .append(cellInline("C1", "选项A")).append(cellInline("D1", "选项B"))
          .append(cellInline("E1", "选项C")).append(cellInline("F1", "选项D"))
          .append(cellInline("G1", "答案")).append(cellInline("H1", "解析")).append(cellInline("I1", "分类"))
          .append("</row>");
        // 单选题：选项为百分比数值单元格（两种样式）
        sh.append("<row r=\"2\">")
          .append(cellInline("A2", "单选题")).append(cellInline("B2", "光纤衰减阈值占比大致是？"))
          .append(cellNumStyle("C2", 1, "0.5"))
          .append(cellNumStyle("D2", 1, "0.25"))
          .append(cellNumStyle("E2", 2, "0.85"))
          .append(cellNumStyle("F2", 2, "0.666"))
          .append(cellInline("G2", "A")).append(cellInline("H2", "")).append(cellInline("I2", "基础"))
          .append("</row>");
        // 填空题：答案列本身就是百分比数值单元格
        sh.append("<row r=\"3\">")
          .append(cellInline("A3", "填空题")).append(cellInline("B3", "默认光衰占比上限为____。"))
          .append(cellNumStyle("G3", 1, "0.5"))
          .append(cellInline("H3", "")).append(cellInline("I3", "基础"))
          .append("</row>");
        sh.append("</sheetData></worksheet>");

        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", CT);
        entries.put("_rels/.rels", RELS);
        entries.put("xl/workbook.xml", WB);
        entries.put("xl/_rels/workbook.xml.rels", WB_RELS);
        entries.put("xl/styles.xml", styles);
        entries.put("xl/worksheets/sheet1.xml", sh.toString());
        File f = TxtCsvParserTest.buildZip("xlsxpct", entries);

        ParseResult r = new XlsxParser().parse(f);
        assertEquals(2, r.questions.size());
        ParsedQuestion q1 = r.questions.get(0);
        assertEquals("50%", q1.options.get(0));
        assertEquals("25%", q1.options.get(1));
        assertEquals("85%", q1.options.get(2));
        assertEquals("66.6%", q1.options.get(3));
        ParsedQuestion q2 = r.questions.get(1);
        assertEquals("填空题百分比答案应还原为 50%", "50%", q2.answer);
        f.delete();
    }

    /** WPS 风格继承：cellXfs 的 xf 未显式 numFmtId 时经 xfId 继承 cellStyleXfs 的百分比格式；
     *  内置 47（时间）不再误判为百分比 */
    @Test
    public void testXlsxPercentInherit() throws Exception {
        String styles = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"9\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"3\">"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
                + "<xf numFmtId=\"47\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "</cellXfs></styleSheet>";
        StringBuilder sh = new StringBuilder();
        sh.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
          .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        // 表头
        sh.append("<row r=\"1\">")
          .append(cellInline("A1", "题型")).append(cellInline("B1", "题目"))
          .append(cellInline("C1", "选项A")).append(cellInline("D1", "选项B"))
          .append(cellInline("E1", "选项C")).append(cellInline("F1", "选项D"))
          .append(cellInline("G1", "答案")).append(cellInline("H1", "解析")).append(cellInline("I1", "分类"))
          .append("</row>");
        // 填空题：答案为继承百分比格式的数值单元格（xf1 无 numFmtId，经 xfId=0 继承内置 9）
        sh.append("<row r=\"2\">")
          .append(cellInline("A2", "填空题")).append(cellInline("B2", "全屋WiFi覆盖率设计目标为____。"))
          .append(cellNumStyle("G2", 1, "0.95"))
          .append(cellInline("H2", "")).append(cellInline("I2", "组网"))
          .append("</row>");
        // 单选题：三种样式对比——继承百分比 / 常规格式原样 / 内置 47（时间）不误转
        sh.append("<row r=\"3\">")
          .append(cellInline("A3", "单选题")).append(cellInline("B3", "光衰占比阈值大致是？"))
          .append(cellNumStyle("C3", 1, "0.5"))
          .append(cellNumStyle("D3", 0, "0.5"))
          .append(cellNumStyle("E3", 2, "0.47"))
          .append(cellNumStyle("F3", 1, "0.25"))
          .append(cellInline("G3", "A")).append(cellInline("H3", "")).append(cellInline("I3", "基础"))
          .append("</row>");
        sh.append("</sheetData></worksheet>");

        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", CT);
        entries.put("_rels/.rels", RELS);
        entries.put("xl/workbook.xml", WB);
        entries.put("xl/_rels/workbook.xml.rels", WB_RELS);
        entries.put("xl/styles.xml", styles);
        entries.put("xl/worksheets/sheet1.xml", sh.toString());
        File f = TxtCsvParserTest.buildZip("xlsxinh", entries);

        ParseResult r = new XlsxParser().parse(f);
        assertEquals(2, r.questions.size());
        ParsedQuestion q1 = r.questions.get(0);
        assertEquals("未显式 numFmtId 应继承 cellStyleXfs 的百分比格式", "95%", q1.answer);
        ParsedQuestion q2 = r.questions.get(1);
        assertEquals("50%", q2.options.get(0));
        assertEquals("常规格式数值保持原样", "0.5", q2.options.get(1));
        assertEquals("内置 47 为时间格式，不应误转百分比", "0.47", q2.options.get(2));
        assertEquals("25%", q2.options.get(3));
        f.delete();
    }

    @Test
    public void testXlsxSharedStrings() throws Exception {
        List<String> ss = Arrays.asList(
                "题型", "题目", "选项A", "选项B", "选项C", "选项D", "答案", "解析", "分类",
                "单选题", "OSI 参考模型共几层？", "4层", "5层", "6层", "7层", "D", "物理层到应用层共7层", "网络基础",
                "判断题", "ping 命令基于 ICMP 协议。", "对");
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
          .append("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"")
          .append(ss.size()).append("\" uniqueCount=\"").append(ss.size()).append("\">");
        for (String s : ss) sb.append("<si><t>").append(TxtCsvParserTest.esc(s)).append("</t></si>");
        sb.append("</sst>");

        StringBuilder sh = new StringBuilder();
        sh.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
          .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
          .append("<sheetData>");
        sh.append("<row r=\"1\">");
        for (int c = 0; c < 9; c++) sh.append(cell((char) ('A' + c) + "1", c));
        sh.append("</row>");
        sh.append("<row r=\"2\">")
          .append(cell("A2", 9)).append(cell("B2", 10)).append(cell("C2", 11))
          .append(cell("D2", 12)).append(cell("E2", 13)).append(cell("F2", 14))
          .append(cell("G2", 15)).append(cell("H2", 16)).append(cell("I2", 17))
          .append("</row>");
        // 稀疏行：只有 A、B、G 有值
        sh.append("<row r=\"3\">")
          .append(cell("A3", 18)).append(cell("B3", 19)).append("<c r=\"C3\"/>").append("<c r=\"D3\"/>")
          .append("<c r=\"E3\"/>").append("<c r=\"F3\"/>").append(cell("G3", 20))
          .append("</row>");
        sh.append("</sheetData></worksheet>");

        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", CT);
        entries.put("_rels/.rels", RELS);
        entries.put("xl/workbook.xml", WB);
        entries.put("xl/_rels/workbook.xml.rels", WB_RELS);
        entries.put("xl/sharedStrings.xml", sb.toString());
        entries.put("xl/worksheets/sheet1.xml", sh.toString());
        File f = TxtCsvParserTest.buildZip("xlsx1", entries);

        ParseResult r = new XlsxParser().parse(f);
        assertEquals(2, r.questions.size());
        ParsedQuestion q1 = r.questions.get(0);
        assertEquals(QuestionType.SINGLE, q1.type);
        assertEquals("D", q1.answer);
        assertEquals("网络基础", q1.category);
        assertEquals("物理层到应用层共7层", q1.analysis);
        ParsedQuestion q2 = r.questions.get(1);
        assertEquals(QuestionType.JUDGE, q2.type);
        assertEquals("A", q2.answer);
        assertEquals("正确", q2.options.get(0));
        f.delete();
    }

    @Test
    public void testXlsxInlineAndNoHeader() throws Exception {
        StringBuilder sh = new StringBuilder();
        sh.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
          .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        sh.append("<row r=\"1\">")
          .append(cellInline("A1", "单选题")).append(cellInline("B1", "双绞线最大传输距离是多少米？"))
          .append(cellInline("C1", "50")).append(cellInline("D1", "100"))
          .append(cellInline("E1", "150")).append(cellInline("F1", "200"))
          .append(cellInline("I1", "B"))
          .append("</row></sheetData></worksheet>");

        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", CT);
        entries.put("_rels/.rels", RELS);
        entries.put("xl/workbook.xml", WB);
        entries.put("xl/_rels/workbook.xml.rels", WB_RELS);
        entries.put("xl/worksheets/sheet1.xml", sh.toString());
        File f = TxtCsvParserTest.buildZip("xlsx2", entries);

        ParseResult r = new XlsxParser().parse(f);
        assertEquals(1, r.questions.size());
        ParsedQuestion q = r.questions.get(0);
        assertEquals(QuestionType.SINGLE, q.type);
        assertEquals("B", q.answer);
        assertEquals(4, q.options.size());
        assertTrue(r.infos.get(0).contains("标准列序"));
        f.delete();
    }

    @Test
    public void testXlsxWriterRoundtrip() throws Exception {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"题型", "题目", "选项A", "选项B", "选项C", "选项D", "选项E", "选项F", "答案", "解析", "分类"});
        rows.add(new String[]{"多选题", "下列属于传输层协议的有？", "IP", "TCP", "UDP", "ICMP", "", "", "BC", "IP/ICMP为网络层", "协议基础"});
        rows.add(new String[]{"判断题", "VLAN 可以隔离广播域。", "", "", "", "", "", "", "对", "", "交换技术"});
        rows.add(new String[]{"填空题", "默认 SSH 端口为____。", "", "", "", "", "", "", "22", "", ""});
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XlsxWriter.write(bos, "题库", rows);
        File f = File.createTempFile("xlsx3", ".xlsx");
        f.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(bos.toByteArray());
        }
        ParseResult r = new XlsxParser().parse(f);
        assertEquals(3, r.questions.size());
        assertEquals(QuestionType.MULTI, r.questions.get(0).type);
        assertEquals("BC", r.questions.get(0).answer);
        assertEquals("交换技术", r.questions.get(1).category);
        assertEquals("A", r.questions.get(1).answer);
        assertEquals("22", r.questions.get(2).answer);
        assertEquals(0, r.warnings.size());
        f.delete();
    }

    private String tc(String text) {
        return "<w:tc><w:p><w:r><w:t>" + TxtCsvParserTest.esc(text) + "</w:t></w:r></w:p></w:tc>";
    }

    private String tr(String... cells) {
        StringBuilder b = new StringBuilder("<w:tr>");
        for (String c : cells) b.append(tc(c));
        b.append("</w:tr>");
        return b.toString();
    }

    @Test
    public void testDocxTable() throws Exception {
        String body = "<w:tbl>"
                + tr("题型", "题目", "选项A", "选项B", "答案", "解析", "分类")
                + tr("单选题", "光猫的中文名称是？", "光学调制解调器", "光调制解调终端", "A", "光调制解调器俗称光猫", "光纤接入")
                + tr("判断题", "路由器工作在网络层。", "", "", "对", "", "路由组网")
                + "</w:tbl><w:p/>";
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                + "<w:body>" + body + "</w:body></w:document>";
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("word/document.xml", xml);
        File f = TxtCsvParserTest.buildZip("docx1", entries);

        ParseResult r = new DocxParser().parse(f);
        assertEquals(2, r.questions.size());
        assertEquals("A", r.questions.get(0).answer);
        assertEquals("光纤接入", r.questions.get(0).category);
        assertEquals(QuestionType.JUDGE, r.questions.get(1).type);
        assertEquals("A", r.questions.get(1).answer);
        f.delete();
    }

    @Test
    public void testDocxParagraphs() throws Exception {
        // 段落被拆成多段（run 拆分）、空段落分隔
        String p1 = "<w:p><w:r><w:t>[单选] TCP 三次</w:t></w:r><w:r><w:t>握手的第二步发送的报文是？</w:t></w:r></w:p>";
        String p1a = "<w:p><w:r><w:t>A. SYN</w:t></w:r></w:p>";
        String p1b = "<w:p><w:r><w:t>B. SYN+ACK</w:t></w:r></w:p>";
        String p1c = "<w:p><w:r><w:t>C. ACK</w:t></w:r></w:p>";
        String p1d = "<w:p><w:r><w:t>答案：B</w:t></w:r></w:p>";
        String empty = "<w:p/>";
        String p2 = "<w:p><w:r><w:t>[判断] UDP 是面向连接的协议。</w:t></w:r></w:p>";
        String p2a = "<w:p><w:r><w:t>答案：错</w:t></w:r></w:p>";
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                + "<w:body>" + p1 + p1a + p1b + p1c + p1d + empty + p2 + p2a + "</w:body></w:document>";
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("word/document.xml", xml);
        File f = TxtCsvParserTest.buildZip("docx2", entries);

        ParseResult r = new DocxParser().parse(f);
        assertEquals(2, r.questions.size());
        assertTrue(r.questions.get(0).content.contains("TCP 三次"));
        assertTrue(r.questions.get(0).content.contains("握手"));
        assertEquals("B", r.questions.get(0).answer);
        assertEquals(QuestionType.JUDGE, r.questions.get(1).type);
        assertEquals("B", r.questions.get(1).answer);
        f.delete();
    }

    @Test
    public void testDocxWriterTemplateRoundtrip() throws Exception {
        // 生成器与解析器闭环：Word 导入模板应能被解析回题目，且含排序/看图示例
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DocxWriter.writeTemplate(bos);
        File f = File.createTempFile("docx_tpl", ".docx");
        f.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(bos.toByteArray());
        }
        ParseResult r = new DocxParser().parse(f);
        assertTrue("模板应解析出示例题，实际 " + r.questions.size(), r.questions.size() >= 7);
        boolean hasSort = false;
        boolean hasOther = false;
        for (ParsedQuestion q : r.questions) {
            if (q.type == QuestionType.SORT) hasSort = true;
            if (q.type == QuestionType.OTHER) hasOther = true;
        }
        assertTrue("模板应包含排序题示例", hasSort);
        assertTrue("模板应包含其它题示例", hasOther);
        ParsedQuestion sort = null;
        for (ParsedQuestion q : r.questions) {
            if (q.type == QuestionType.SORT) { sort = q; break; }
        }
        assertEquals("CBA", sort.answer);
        assertEquals(3, sort.options.size());
        f.delete();
    }

    @Test
    public void testDocxImageExtract() throws Exception {
        // 表格行内图片（a:blip r:embed）应提取到对应题目的 images
        String drawing = "<w:tc><w:p><w:r><w:drawing>"
                + "<a:blip xmlns:a=\"http://www.w3.org/dml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" r:embed=\"rId9\"/>"
                + "</w:drawing></w:r></w:p></w:tc>";
        String row = "<w:tr>"
                + "<w:tc><w:p><w:r><w:t>其它题</w:t></w:r></w:p></w:tc>"
                + "<w:tc><w:p><w:r><w:t>看图描述拓扑连接</w:t></w:r></w:p></w:tc>"
                + drawing
                + "<w:tc><w:p><w:r><w:t></w:t></w:r></w:p></w:tc>"
                + "<w:tc><w:p><w:r><w:t></w:t></w:r></w:p></w:tc>"
                + "<w:tc><w:p><w:r><w:t></w:t></w:r></w:p></w:tc>"
                + "<w:tc><w:p><w:r><w:t>光猫→路由器→终端</w:t></w:r></w:p></w:tc>"
                + "<w:tc><w:p><w:r><w:t></w:t></w:r></w:p></w:tc>"
                + "<w:tc><w:p><w:r><w:t></w:t></w:r></w:p></w:tc>"
                + "</w:tr>";
        String head = "<w:tr>"
                + "<w:tc><w:p><w:r><w:t>题型</w:t></w:r></w:p></w:tc>"
                + "<w:tc><w:p><w:r><w:t>题目</w:t></w:r></w:p></w:tc>"
                + "<w:tc><w:p><w:r><w:t>选项A</w:t></w:r></w:p></w:tc>"
                + "<w:tc><w:p><w:r><w:t>选项B</w:t></w:r></w:p></w:tc>"
                + "<w:tc><w:p><w:r><w:t>选项C</w:t></w:r></w:p></w:tc>"
                + "<w:tc><w:p><w:r><w:t>答案</w:t></w:r></w:p></w:tc>"
                + "<w:tc><w:p><w:r><w:t>解析</w:t></w:r></w:p></w:tc>"
                + "<w:tc><w:p><w:r><w:t>分类</w:t></w:r></w:p></w:tc>"
                + "</w:tr>";
        String body = "<w:tbl>" + head + row + "</w:tbl><w:p/>";
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                + "<w:body>" + body + "</w:body></w:document>";
        String rels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId9\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/image1.png\"/>"
                + "</Relationships>";
        // 最小 PNG 头
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

        File f = File.createTempFile("docx_img", ".docx");
        f.deleteOnExit();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                new FileOutputStream(f))) {
            zos.putNextEntry(new java.util.zip.ZipEntry("word/document.xml"));
            zos.write(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new java.util.zip.ZipEntry("word/_rels/document.xml.rels"));
            zos.write(rels.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new java.util.zip.ZipEntry("word/media/image1.png"));
            zos.write(png);
            zos.closeEntry();
        }
        ParseResult r = new DocxParser().parse(f);
        assertEquals(1, r.questions.size());
        ParsedQuestion q = r.questions.get(0);
        assertEquals(QuestionType.OTHER, q.type);
        assertEquals("图片未提取到题目上", 1, q.images.size());
        assertTrue(q.images.get(0).length >= 12);
        f.delete();
    }
}
