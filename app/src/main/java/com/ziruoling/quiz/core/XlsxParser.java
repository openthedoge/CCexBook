package com.ziruoling.quiz.core;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * xlsx 题库解析（纯标准库：zip + SAX，无需 POI）。
 * <p>读取第一个工作表；支持 sharedStrings / inlineStr / str / 公式单元格值；
 * 自动识别表头列映射。
 */
public final class XlsxParser {

    public ParseResult parse(File f) throws IOException {
        ZipFile zip;
        try {
            zip = new ZipFile(f);
        } catch (IOException e) {
            throw new IOException("文件不是有效的 xlsx（可能为旧版 .xls，请另存为 .xlsx）");
        }
        try {
            List<String> shared = new ArrayList<>();
            ZipEntry ss = zip.getEntry("xl/sharedStrings.xml");
            if (ss != null) {
                InputStream in = zip.getInputStream(ss);
                try {
                    SharedHandler h = new SharedHandler();
                    Xml.parse(in, h);
                    shared = h.list;
                } finally {
                    in.close();
                }
            }
            // 样式表：识别百分比格式单元格（Excel 存的是 0.5 这类原始小数）
            Set<Integer> percentStyles = new HashSet<>();
            ZipEntry st = zip.getEntry("xl/styles.xml");
            if (st != null) {
                InputStream in = zip.getInputStream(st);
                try {
                    Xml.parse(in, new StylesHandler(percentStyles));
                } finally {
                    in.close();
                }
            }
            ZipEntry sheet = pickSheet(zip);
            if (sheet == null) throw new IOException("xlsx 中未找到工作表（xl/worksheets）");
            List<String[]> rows;
            InputStream in = zip.getInputStream(sheet);
            try {
                SheetHandler h = new SheetHandler(shared, percentStyles);
                Xml.parse(in, h);
                rows = h.rows;
            } finally {
                in.close();
            }
            ParseResult res = new ParseResult();
            RowMapper.mapRows(rows, res);
            return res;
        } finally {
            zip.close();
        }
    }

    static ZipEntry pickSheet(ZipFile zip) {
        ZipEntry best = null;
        int bestNo = Integer.MAX_VALUE;
        Enumeration<? extends ZipEntry> e = zip.entries();
        while (e.hasMoreElements()) {
            ZipEntry en = e.nextElement();
            String n = en.getName();
            if (n.startsWith("xl/worksheets/") && n.endsWith(".xml") && !en.isDirectory()) {
                int no = Integer.MAX_VALUE;
                String base = n.substring(n.lastIndexOf('/') + 1, n.length() - 4);
                try {
                    no = Integer.parseInt(base.replace("sheet", ""));
                } catch (NumberFormatException ignored) {
                }
                if (no < bestNo) {
                    bestNo = no;
                    best = en;
                }
            }
        }
        return best;
    }

    /** sharedStrings.xml 解析：si 内多个 t 拼接，跳过 rPh 注音 */
    private static class SharedHandler extends DefaultHandler {
        final List<String> list = new ArrayList<>();
        private final StringBuilder buf = new StringBuilder();
        private boolean inSi = false;
        private boolean inT = false;
        private int rPh = 0;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if (localName.equals("si")) {
                inSi = true;
                buf.setLength(0);
            } else if (localName.equals("rPh")) {
                rPh++;
            } else if (localName.equals("t")) {
                inT = inSi && rPh == 0;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inT) buf.append(ch, start, length);
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (localName.equals("si")) {
                list.add(buf.toString());
                inSi = false;
                inT = false;
            } else if (localName.equals("rPh")) {
                rPh--;
            } else if (localName.equals("t")) {
                inT = false;
            }
        }
    }

    /**
     * styles.xml 解析：找出百分比格式（numFmtId 9/10 或自定义 formatCode 含 %；
     * cellXfs 未显式 numFmtId 时经 xfId 继承 cellStyleXfs）
     * 的单元格样式索引（cellXfs 下标），供单元格数值按显示格式还原为 "50%" 形式。
     */
    private static class StylesHandler extends DefaultHandler {
        /** 内置百分比数字格式 id */
        private static final Set<Integer> BUILTIN_PERCENT = new HashSet<>();

        static {
            // 仅内置 9（0%）与 10（0.00%）；45-48 为时间/科学计数等非百分比，避免误转
            BUILTIN_PERCENT.add(9);
            BUILTIN_PERCENT.add(10);
        }

        final Set<Integer> out;
        private final Map<Integer, String> customFormats = new HashMap<>();
        /** cellStyleXfs 各条目的 numFmtId（供 cellXfs 未显式指定时继承） */
        private final List<Integer> cellStyleNumFmts = new ArrayList<>();
        private boolean inCellXfs = false;
        private boolean inCellStyleXfs = false;
        private int xfIndex = 0;

        StylesHandler(Set<Integer> out) {
            this.out = out;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if (localName.equals("numFmt")) {
                try {
                    int id = Integer.parseInt(attributes.getValue("numFmtId"));
                    String code = attributes.getValue("formatCode");
                    if (code != null) customFormats.put(id, code);
                } catch (NumberFormatException ignored) {
                }
            } else if (localName.equals("cellStyleXfs")) {
                inCellStyleXfs = true;
            } else if (localName.equals("cellXfs")) {
                inCellXfs = true;
                xfIndex = 0;
            } else if (localName.equals("xf")) {
                int id = parseIntOr(attributes.getValue("numFmtId"), -1);
                if (inCellStyleXfs) {
                    cellStyleNumFmts.add(id < 0 ? 0 : id);
                } else if (inCellXfs) {
                    // 未显式指定 numFmtId 时继承 xfId 指向的 cellStyleXfs 条目（WPS 等生成器风格）
                    if (id < 0) {
                        int xfId = parseIntOr(attributes.getValue("xfId"), -1);
                        id = (xfId >= 0 && xfId < cellStyleNumFmts.size())
                                ? cellStyleNumFmts.get(xfId) : 0;
                    }
                    boolean percent = BUILTIN_PERCENT.contains(id)
                            || (customFormats.containsKey(id)
                            && customFormats.get(id) != null
                            && customFormats.get(id).contains("%"));
                    if (percent) out.add(xfIndex);
                    xfIndex++;
                }
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (localName.equals("cellXfs")) inCellXfs = false;
            if (localName.equals("cellStyleXfs")) inCellStyleXfs = false;
        }

        private static int parseIntOr(String v, int def) {
            if (v == null) return def;
            try {
                return Integer.parseInt(v.trim());
            } catch (NumberFormatException e) {
                return def;
            }
        }
    }

    /** 工作表解析：row/c/v/is，支持稀疏单元格引用定位列；百分比单元格按显示格式还原 */
    private static class SheetHandler extends DefaultHandler {
        final List<String[]> rows = new ArrayList<>();
        private final List<String> shared;
        private final Set<Integer> percentStyles;
        private ArrayList<String> curRow = null;
        private int curCol = -1;
        private String curType = null;
        private boolean curPercent = false;
        private final StringBuilder cellBuf = new StringBuilder();
        private boolean inV = false;
        private boolean inT = false;

        SheetHandler(List<String> shared, Set<Integer> percentStyles) {
            this.shared = shared;
            this.percentStyles = percentStyles;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if (localName.equals("row")) {
                curRow = new ArrayList<>();
            } else if (localName.equals("c")) {
                if (curRow == null) curRow = new ArrayList<>();
                curType = attributes.getValue("t");
                String ref = attributes.getValue("r");
                curCol = ref == null ? curRow.size() : colIndexOf(ref);
                curPercent = false;
                String s = attributes.getValue("s");
                if (s != null) {
                    try {
                        curPercent = percentStyles.contains(Integer.parseInt(s.trim()));
                    } catch (NumberFormatException ignored) {
                    }
                }
                cellBuf.setLength(0);
            } else if (localName.equals("v")) {
                inV = true;
            } else if (localName.equals("t")) {
                inT = true;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inV || inT) cellBuf.append(ch, start, length);
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (localName.equals("v")) {
                inV = false;
            } else if (localName.equals("t")) {
                inT = false;
            } else if (localName.equals("c")) {
                String value;
                if ("s".equals(curType)) {
                    value = sharedIndex(cellBuf.toString().trim());
                } else if ("inlineStr".equals(curType) || "str".equals(curType)) {
                    value = cellBuf.toString();
                } else if ("b".equals(curType)) {
                    value = "1".equals(cellBuf.toString().trim()) ? "TRUE" : "FALSE";
                } else if (curPercent) {
                    value = applyPercent(cellBuf.toString()); // 百分比格式：0.5 → "50%"
                } else {
                    value = cellBuf.toString();
                }
                setCell(curRow, curCol, value);
                curCol = -1;
                curType = null;
                curPercent = false;
            } else if (localName.equals("row")) {
                if (curRow != null && !curRow.isEmpty()) {
                    rows.add(curRow.toArray(new String[0]));
                }
                curRow = null;
            }
        }

        /** 百分比原始值（0.5）按显示精度还原为 "50%"；非数值原样返回 */
        static String applyPercent(String raw) {
            if (raw == null) return "";
            String t = raw.trim();
            if (t.isEmpty()) return raw;
            try {
                BigDecimal p = new BigDecimal(t).multiply(BigDecimal.valueOf(100));
                BigDecimal v = p.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
                return v.toPlainString() + "%";
            } catch (NumberFormatException e) {
                return raw;
            }
        }

        private String sharedIndex(String idx) {
            try {
                int i = Integer.parseInt(idx);
                if (i >= 0 && i < shared.size()) return shared.get(i);
            } catch (NumberFormatException ignored) {
            }
            return "";
        }

        private static void setCell(ArrayList<String> row, int col, String value) {
            if (col < 0) col = row.size();
            while (row.size() <= col) row.add("");
            row.set(col, value == null ? "" : value);
        }

        /** "BC12" → 1（0 起列号） */
        static int colIndexOf(String ref) {
            int n = 0;
            for (int i = 0; i < ref.length(); i++) {
                char c = ref.charAt(i);
                if (c >= 'A' && c <= 'Z') {
                    n = n * 26 + (c - 'A' + 1);
                } else if (c >= 'a' && c <= 'z') {
                    n = n * 26 + (c - 'a' + 1);
                } else {
                    break;
                }
            }
            return n - 1;
        }
    }
}
