package com.ziruoling.quiz.core;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * docx 题库解析（纯标准库：zip + SAX）。
 * <p>优先按"表格题库"解析（每行一题，同 Excel 列格式）；
 * 文档无表格时按"段落题库"解析（同 TXT 文本格式）。
 * <p>表格行内的贴图（w:drawing → a:blip r:embed）会被提取为题目图片（看图题/配图题）。
 */
public final class DocxParser {

    public ParseResult parse(File f) throws IOException {
        ZipFile zip;
        try {
            zip = new ZipFile(f);
        } catch (IOException e) {
            throw new IOException("文件不是有效的 docx（可能为旧版 .doc，请另存为 .docx）");
        }
        try {
            ZipEntry doc = zip.getEntry("word/document.xml");
            if (doc == null) throw new IOException("docx 中未找到 word/document.xml");
            Handler h = new Handler();
            InputStream in = zip.getInputStream(doc);
            try {
                Xml.parse(in, h);
            } finally {
                in.close();
            }
            ParseResult res = new ParseResult();
            int maxCols = 0;
            for (String[] r : h.tables) maxCols = Math.max(maxCols, r.length);
            if (h.tables.size() >= 2 && maxCols >= 3) {
                RowMapper.mapRows(h.tables, res);
                res.infos.add("按 Word 表格解析（每行一题）");
                attachImages(zip, h, res);
                int imgCount = 0;
                for (ParsedQuestion q : res.questions) imgCount += q.images.size();
                if (imgCount > 0) {
                    res.infos.add("提取到 " + imgCount + " 张行内图片，导入后将在题目中显示");
                }
            } else if (!h.paragraphs.isEmpty()) {
                res = TxtParser.parseLines(h.paragraphs);
                res.infos.add("按文档段落解析（文本题库格式）");
            } else {
                throw new IOException("文档中没有可识别的表格或题目内容");
            }
            return res;
        } finally {
            zip.close();
        }
    }

    /** 把行内图片字节挂到对应题目（按来源行号对齐） */
    private void attachImages(ZipFile zip, Handler h, ParseResult res) throws IOException {
        // rId → 字节
        Map<String, byte[]> media = loadRels(zip);
        for (ParsedQuestion q : res.questions) {
            int idx = q.sourceRow - 1;
            if (idx < 0 || idx >= h.rowImgs.size()) continue;
            List<String> rids = h.rowImgs.get(idx);
            if (rids == null) continue;
            for (String rid : rids) {
                byte[] bytes = media.get(rid);
                if (bytes != null) q.images.add(bytes);
            }
        }
    }

    /** 读取 word/_rels/document.xml.rels，把 rId → 媒体字节 */
    private Map<String, byte[]> loadRels(ZipFile zip) {
        Map<String, byte[]> out = new HashMap<>();
        try {
            ZipEntry rels = zip.getEntry("word/_rels/document.xml.rels");
            if (rels == null) return out;
            RelHandler rh = new RelHandler();
            InputStream in = zip.getInputStream(rels);
            try {
                Xml.parse(in, rh);
            } finally {
                in.close();
            }
            for (Map.Entry<String, String> e : rh.targets.entrySet()) {
                String path = "word/" + e.getValue();
                ZipEntry media = zip.getEntry(path);
                if (media == null) continue;
                InputStream mi = zip.getInputStream(media);
                try {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = mi.read(buf)) > 0) bos.write(buf, 0, n);
                    out.put(e.getKey(), bos.toByteArray());
                } finally {
                    mi.close();
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** rels 解析：Id → Target */
    private static class RelHandler extends DefaultHandler {
        final Map<String, String> targets = new HashMap<>();

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if (localName.equals("Relationship")) {
                String id = attributes.getValue("Id");
                String target = attributes.getValue("Target");
                if (id != null && target != null && target.contains("media/")) {
                    targets.put(id, target);
                }
            }
        }
    }

    /** document.xml 解析：表格行 + 表格外段落 + 行内图片 rId */
    private static class Handler extends DefaultHandler {
        final List<String[]> tables = new ArrayList<>();
        final List<String> paragraphs = new ArrayList<>();
        /** 每行（与 tables 平行）的图片 rId 列表 */
        final List<List<String>> rowImgs = new ArrayList<>();

        private int tblDepth = 0;
        private boolean inTr = false;
        private boolean inTc = false;
        private ArrayList<String> curRow = null;
        private List<String> curRowImgs = null;
        private final StringBuilder cell = new StringBuilder();
        private final StringBuilder para = new StringBuilder();

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if (localName.equals("tbl")) {
                tblDepth++;
            } else if (localName.equals("tr") && tblDepth == 1) {
                inTr = true;
                curRow = new ArrayList<>();
                curRowImgs = new ArrayList<>();
            } else if (localName.equals("tc") && tblDepth == 1 && inTr) {
                inTc = true;
                cell.setLength(0);
            } else if (localName.equals("blip") && inTr && curRowImgs != null) {
                // 图片引用：<a:blip r:embed="rIdX">
                for (int i = 0; i < attributes.getLength(); i++) {
                    if ("embed".equals(attributes.getLocalName(i))) {
                        String v = attributes.getValue(i);
                        if (v != null && !v.isEmpty()) curRowImgs.add(v);
                    }
                }
            } else if (localName.equals("p")) {
                if (tblDepth == 0) para.setLength(0);
                // 表格内段落由 endElement 追加换行
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inTc) {
                cell.append(ch, start, length);
            } else if (tblDepth == 0) {
                para.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (localName.equals("t")) {
                // 文本节点内容已在 characters 中收集
            } else if (localName.equals("p")) {
                if (tblDepth == 0) {
                    paragraphs.add(para.toString().trim());
                    para.setLength(0);
                } else if (inTc) {
                    cell.append('\n');
                }
            } else if (localName.equals("tc")) {
                if (inTc) {
                    curRow.add(cell.toString().trim());
                    inTc = false;
                }
            } else if (localName.equals("tr")) {
                if (inTr && curRow != null && !curRow.isEmpty()) {
                    tables.add(curRow.toArray(new String[0]));
                    rowImgs.add(curRowImgs == null ? new ArrayList<>() : curRowImgs);
                }
                inTr = false;
                curRow = null;
                curRowImgs = null;
            } else if (localName.equals("tbl")) {
                tblDepth--;
            }
        }
    }
}