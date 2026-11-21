package com.ziruoling.quiz.core;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 极简 xlsx 生成器（纯标准库，无第三方依赖）。
 * 输出使用 inlineStr 单元格，Excel / WPS 均可直接打开编辑。
 */
public final class XlsxWriter {

    private XlsxWriter() {}

    /** 写入 xlsx：rows 为行集合，每行为单元格数组 */
    public static void write(OutputStream out, String sheetName, List<String[]> rows) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
        try {
            put(zip, "[Content_Types].xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                            + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                            + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                            + "</Types>");

            put(zip, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                            + "</Relationships>");

            put(zip, "xl/workbook.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                            + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
                            + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                            + "<sheets><sheet name=\"" + escape(sheetName) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"
                            + "</workbook>");

            put(zip, "xl/_rels/workbook.xml.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                            + "</Relationships>");

            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
            sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
            sb.append("<cols>");
            int[] widths = {8, 50, 20, 20, 20, 20, 20, 20, 12, 40, 14};
            for (int i = 0; i < widths.length; i++) {
                sb.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1)
                        .append("\" width=\"").append(widths[i]).append("\" customWidth=\"1\"/>");
            }
            sb.append("</cols><sheetData>");
            for (int r = 0; r < rows.size(); r++) {
                String[] row = rows.get(r);
                sb.append("<row r=\"").append(r + 1).append("\">");
                for (int c = 0; c < row.length; c++) {
                    String ref = colName(c) + (r + 1);
                    String v = row[c] == null ? "" : row[c];
                    if (v.isEmpty()) {
                        sb.append("<c r=\"").append(ref).append("\"/>");
                    } else {
                        sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                                .append(escape(v)).append("</t></is></c>");
                    }
                }
                sb.append("</row>");
            }
            sb.append("</sheetData></worksheet>");
            put(zip, "xl/worksheets/sheet1.xml", sb.toString());
        } finally {
            closeQuietly(zip);
        }
    }

    private static String colName(int idx) {
        StringBuilder b = new StringBuilder();
        int n = idx;
        do {
            b.insert(0, (char) ('A' + n % 26));
            n = n / 26 - 1;
        } while (n >= 0);
        return b.toString();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': b.append("&amp;"); break;
                case '<': b.append("&lt;"); break;
                case '>': b.append("&gt;"); break;
                case '\"': b.append("&quot;"); break;
                case '\'': b.append("&apos;"); break;
                default:
                    if (c >= 0x20 || c == '\n' || c == '\t') b.append(c);
                    break;
            }
        }
        return b.toString();
    }

    private static void closeQuietly(Closeable c) {
        try {
            c.close();
        } catch (IOException ignored) {
        }
    }
}
