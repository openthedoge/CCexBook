package com.ziruoling.quiz.core;

import com.ziruoling.quiz.util.TextUtil;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 极简 docx 生成器（纯标准库：zip + WordprocessingML 字符串拼装）。
 * 用于：① Word 版导入模板（表格可直接贴图，导入时自动提取为看图题配图）；
 *      ② 试卷导出（按题型分组排版，可附答案页）。
 */
public final class DocxWriter {

    private DocxWriter() {}

    /** 试卷/题库导出用的题目结构（core 自洽，不依赖 data 层） */
    public static class PaperQ {
        public int type;
        public String content = "";
        public List<String> options = new ArrayList<>();
        public String answer = "";
        public String analysis = "";
        public String category = "";
        /** 原文档题号（按篇/节重新计数），0 = 无；导出题库时写回行首 */
        public int docNo = 0;
        public boolean hasImage;
        /** 原始图片字节（PNG/JPEG/GIF），导出时内嵌到 docx */
        public List<byte[]> images = new ArrayList<>();
    }

    // ==================== Word 导入模板 ====================

    public static void writeTemplate(OutputStream out) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(xmlHeader());
        sb.append(para("紫若灵刷题练习 · Word 版导入模板", "title"));
        sb.append(para("填写说明：", "bold"));
        sb.append(para("1. 每行一题，列格式与 Excel 模板一致：题型、题目、选项A~F、答案、解析、分类；", "body"));
        sb.append(para("2. 题型支持：单选题 / 多选题 / 判断题 / 填空题 / 简答题 / 排序题 / 其它题；", "body"));
        sb.append(para("3. 排序题：答案填正确顺序的字母串，字母数须等于选项数（如 3 个选项填 BAC）；", "body"));
        sb.append(para("4. 图片与题型无关：任何题型都可以在“题目”单元格内插入图片（拓扑图/截图等），导入后自动提取显示；", "body"));
        sb.append(para("5. 判断题答案：对/错/正确/错误/√/× 均可；多选答案如 ABD；填空多个参考答案用 || 分隔；", "body"));
        sb.append(para("6. 完成后在应用内「导入题库」选择本文件即可（支持追加到已有题库）。", "body"));
        sb.append(emptyPara());

        // 示例表
        sb.append(para("示例（可参考下表格式，导入前请删除示例行）：", "bold"));
        sb.append(tableStart(new int[]{900, 2600, 1500, 1500, 1500, 1500, 1500, 1500, 1000, 1800, 1100}));
        String[] head = {"题型", "题目", "选项A", "选项B", "选项C", "选项D", "选项E", "选项F",
                "答案", "解析", "分类"};
        sb.append(tableRow(head));
        sb.append(tableRow(new String[]{"单选题", "OSI 参考模型共分为几层？", "4层", "5层", "6层", "7层", "", "",
                "C", "物理层到应用层共 7 层。", "网络基础"}));
        sb.append(tableRow(new String[]{"多选题", "下列属于传输层协议的有？", "IP", "TCP", "UDP", "ICMP", "", "",
                "BC", "IP/ICMP 属网络层。", "网络基础"}));
        sb.append(tableRow(new String[]{"判断题", "UDP 是面向连接的协议。", "", "", "", "", "", "",
                "错", "UDP 为无连接协议。", "网络基础"}));
        sb.append(tableRow(new String[]{"填空题", "IPv4 地址由____位二进制数组成。", "", "", "", "", "", "",
                "32", "多个答案用 || 分隔。", "网络基础"}));
        sb.append(tableRow(new String[]{"简答题", "简述 ping 命令的作用。", "", "", "", "", "", "",
                "检测目标主机是否可达及往返时延。", "不参与自动判分。", "网络检测"}));
        sb.append(tableRow(new String[]{"排序题", "将 TCP 三次握手步骤按发生顺序排列。", "客户端回复 ACK", "服务端回 SYN+ACK", "客户端发 SYN", "", "", "",
                "CBA", "答案字母数须等于选项数。", "网络基础"}));
        sb.append(tableRow(new String[]{"其它题", "示例：描述图中组网连接关系（在本单元格插入图片）。", "", "", "", "", "", "",
                "光信号→电信号转换后经路由器接入。", "其它题按参考文本作答+自评。", "网络与交换"}));
        sb.append(tableEnd());

        sb.append(emptyPara());
        sb.append(para("请从下一行开始正式题库（删除上方示例行，保留表头）：", "bold"));
        sb.append(tableStart(new int[]{900, 2600, 1500, 1500, 1500, 1500, 1500, 1500, 1000, 1800, 1100}));
        sb.append(tableRow(head));
        for (int i = 0; i < 6; i++) {
            sb.append(tableRow(new String[11]));
        }
        sb.append(tableEnd());
        sb.append(xmlFooter());
        writeDocx(out, sb.toString());
    }

    // ==================== 试卷导出 ====================

    /**
     * 试卷导出：按题型分组排版，可附答案页。
     *
     * @param title      试卷标题
     * @param questions  题目（有序）
     * @param withAnswers 末尾附参考答案与解析
     */
    public static void writePaper(OutputStream out, String title, List<PaperQ> questions,
                                   boolean withAnswers) throws IOException {
        // 按固定组序分组：单选、多选、判断、填空、排序、其它、简答
        int[] groupOrder = {QuestionType.SINGLE, QuestionType.MULTI, QuestionType.JUDGE,
                QuestionType.FILL, QuestionType.SORT, QuestionType.OTHER, QuestionType.SHORT};
        String[] groupNames = {"单选题", "多选题", "判断题", "填空题", "排序题", "其它题", "简答题"};

        StringBuilder sb = new StringBuilder();
        sb.append(xmlHeader());
        sb.append(para(title == null || title.isEmpty() ? "试卷" : title, "title"));
        sb.append(para("姓名：____________　班级：____________　得分：____________", "info"));
        sb.append(emptyPara());

        // 题目正文：全局编号
        List<int[]> groups = new ArrayList<>(); // [type, startIndex(全局题号-1), count]
        List<byte[]> media = new ArrayList<>();
        List<String> exts = new ArrayList<>();
        int no = 0;
        for (int g = 0; g < groupOrder.length; g++) {
            List<PaperQ> group = new ArrayList<>();
            for (PaperQ q : questions) if (q.type == groupOrder[g]) group.add(q);
            if (group.isEmpty()) continue;
            groups.add(new int[]{groupOrder[g], no, group.size()});
            String cn = cnNumber(groups.size());
            sb.append(para(cn + "、" + groupNamesFor(groupOrder[g], groupNames) + "（共 " + group.size() + " 题）", "bold"));
            for (PaperQ q : group) {
                no++;
                String content = no + ". " + com.ziruoling.quiz.util.TextUtil.text(q.content == null ? "" : q.content);
                boolean imgs = q.images != null && !q.images.isEmpty();
                if (!imgs && (q.hasImage || q.type == QuestionType.OTHER)) {
                    content += "（本题含图片，请在「紫若灵刷题练习」应用中查看）";
                }
                sb.append(para(content, "body"));
                if (imgs) {
                    StringBuilder imgPara = new StringBuilder("<w:p><w:pPr><w:jc w:val=\"center\"/>")
                            .append("<w:spacing w:before=\"60\" w:after=\"60\"/></w:pPr>");
                    for (byte[] img : q.images) {
                        if (img == null || img.length == 0) continue;
                        String ext = imageExt(img);
                        if (ext == null) continue;
                        media.add(img);
                        exts.add(ext);
                        long[] emu = extentFor(img);
                        imgPara.append(drawingRun(media.size(), emu[0], emu[1]));
                    }
                    imgPara.append("</w:p>");
                    sb.append(imgPara);
                }
                for (int i = 0; i < (q.options == null ? 0 : q.options.size()); i++) {
                    sb.append(para("    " + (char) ('A' + i) + ". "
                            + com.ziruoling.quiz.util.TextUtil.text(q.options.get(i)), "option"));
                }
            }
            sb.append(emptyPara());
        }

        // 答案页
        if (withAnswers && !questions.isEmpty()) {
            sb.append(pageBreak());
            sb.append(para("参考答案与解析", "title"));
            int idx = 0;
            for (int[] g : groups) {
                sb.append(para(groupNames[g[0]] + "：", "bold"));
                for (int i = 0; i < g[2]; i++) {
                    PaperQ q = questions.get(g[1] + i);
                    idx++;
                    String ans = answerText(q);
                    StringBuilder line = new StringBuilder().append(idx).append(". ").append(ans);
                    if (q.analysis != null && !q.analysis.isEmpty()) {
                        line.append("　解析：").append(com.ziruoling.quiz.util.TextUtil.text(q.analysis));
                    }
                    sb.append(para(line.toString(), "body"));
                }
            }
        }
        sb.append(xmlFooter());
        writeDocx(out, sb.toString(), media, exts);
    }

    // ==================== 题库导出（表格式，可再导入） ====================

    /** 11 列宽度（twips，合计 8300 ≈ A4 可用宽度 146mm） */
    private static final int[] BANK_WIDTHS = {800, 1600, 700, 700, 700, 700, 700, 700, 350, 950, 600};

    /**
     * 题库导出为 Word 表格：11 列（题型/题目/选项A~F/答案/解析/分类），与导入模板一致，
     * 图片内嵌在「题目」单元格的同一段落里。导出的文件可被 {@link DocxParser} 重新导入（含图片），
     * 适合「导出 → 编辑 → 再导入」的批量修改流程。
     */
    public static void writeBank(OutputStream out, String title, List<PaperQ> questions) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(xmlHeader());
        if (title != null && !title.isEmpty()) sb.append(para(title, "title"));
        sb.append(tableStart(BANK_WIDTHS));
        sb.append(tableRow(new String[]{"题型", "题目", "选项A", "选项B", "选项C", "选项D", "选项E", "选项F",
                "答案", "解析", "分类"}));
        List<byte[]> media = new ArrayList<>();
        List<String> exts = new ArrayList<>();
        if (questions != null) {
            for (PaperQ q : questions) sb.append(bankRow(q, media, exts));
        }
        sb.append(tableEnd());
        sb.append(xmlFooter());
        writeDocx(out, sb.toString(), media, exts);
    }

    private static String bankRow(PaperQ q, List<byte[]> media, List<String> exts) {
        String content = q.content == null ? "" : q.content;
        if (q.docNo > 0) content = q.docNo + "." + content;
        String[] cells = {
                QuestionType.NAMES[q.type],
                content,
                opt(q, 0), opt(q, 1), opt(q, 2), opt(q, 3), opt(q, 4), opt(q, 5),
                q.answer == null ? "" : q.answer,
                q.analysis == null ? "" : q.analysis,
                q.category == null ? "" : q.category
        };
        StringBuilder sb = new StringBuilder("<w:tr>");
        for (int i = 0; i < cells.length; i++) {
            sb.append("<w:tc><w:tcPr><w:tcW w:w=\"1400\" w:type=\"dxa\"/></w:tcPr><w:p>");
            sb.append(textRun(cells[i], 18));
            if (i == 1 && q.images != null) {
                for (byte[] img : q.images) {
                    if (img == null || img.length == 0) continue;
                    String ext = imageExt(img);
                    if (ext == null) continue;
                    media.add(img);
                    exts.add(ext);
                    long[] emu = extentFor(img);
                    sb.append(drawingRun(media.size(), emu[0], emu[1]));
                }
            }
            sb.append("</w:p></w:tc>");
        }
        return sb.append("</w:tr>").toString();
    }

    private static String opt(PaperQ q, int i) {
        return q.options != null && i < q.options.size() && q.options.get(i) != null
                ? q.options.get(i) : "";
    }

    private static String textRun(String text, int halfPoints) {
        return "<w:r><w:rPr><w:sz w:val=\"" + halfPoints + "\"/><w:szCs w:val=\"" + halfPoints
                + "\"/></w:rPr><w:t xml:space=\"preserve\">" + escape(text == null ? "" : text)
                + "</w:t></w:r>";
    }

    /** 内嵌图片的 run（wp:inline，rIdImg{n} 对应 media/image{n}.ext） */
    private static String drawingRun(int id, long cx, long cy) {
        return "<w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">"
                + "<wp:extent cx=\"" + cx + "\" cy=\"" + cy + "\"/>"
                + "<wp:docPr id=\"" + id + "\" name=\"Picture" + id + "\"/>"
                + "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">"
                + "<pic:pic><pic:nvPicPr><pic:cNvPr id=\"" + id + "\" name=\"image" + id
                + ".png\"/><pic:cNvPicPr/></pic:nvPicPr>"
                + "<pic:blipFill><a:blip r:embed=\"rIdImg" + id + "\"/>"
                + "<a:stretch><a:fillRect/></a:stretch></pic:blipFill>"
                + "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"" + cx + "\" cy=\"" + cy + "\"/></a:xfrm>"
                + "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr>"
                + "</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>";
    }

    private static final long EMU_PER_MM = 36000L;
    /** 图片最大宽度/高度：题目列很窄，图片必然会溢出到右侧列，这里限到 90mm 避免整页被图占满 */
    private static final long MAX_W = 90 * EMU_PER_MM;
    private static final long MAX_H = 140 * EMU_PER_MM;

    /** 等比缩放到页面内（宽度优先，超高再压） */
    static long[] extentFor(byte[] img) {
        int[] wh = imageSize(img);
        if (wh == null || wh[0] <= 0 || wh[1] <= 0) {
            return new long[]{120 * EMU_PER_MM, 90 * EMU_PER_MM};
        }
        long cx = MAX_W;
        long cy = cx * wh[1] / wh[0];
        if (cy > MAX_H) {
            cy = MAX_H;
            cx = cy * wh[0] / wh[1];
        }
        return new long[]{cx, cy};
    }

    /** 从字节头部识别图片格式 */
    static String imageExt(byte[] b) {
        if (b == null || b.length < 12) return null;
        if ((b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47) return "png";
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) return "jpg";
        if (b[0] == 'G' && b[1] == 'I' && b[2] == 'F') return "gif";
        return null;
    }

    /** 只读图片尺寸（PNG/JPEG/GIF，解码失败返回 null） */
    static int[] imageSize(byte[] b) {
        String ext = imageExt(b);
        if (ext == null) return null;
        try {
            if ("png".equals(ext)) {
                int w = ((b[16] & 0xFF) << 24) | ((b[17] & 0xFF) << 16) | ((b[18] & 0xFF) << 8) | (b[19] & 0xFF);
                int h = ((b[20] & 0xFF) << 24) | ((b[21] & 0xFF) << 16) | ((b[22] & 0xFF) << 8) | (b[23] & 0xFF);
                return new int[]{w, h};
            }
            if ("gif".equals(ext)) {
                int w = (b[6] & 0xFF) | ((b[7] & 0xFF) << 8);
                int h = (b[8] & 0xFF) | ((b[9] & 0xFF) << 8);
                return new int[]{w, h};
            }
            int i = 2;
            while (i + 9 < b.length) {
                if ((b[i] & 0xFF) != 0xFF) { i++; continue; }
                int marker = b[i + 1] & 0xFF;
                if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) { i += 2; continue; }
                int len = ((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF);
                if (len < 2) return null;
                if (marker == 0xC0 || marker == 0xC1 || marker == 0xC2 || marker == 0xC3
                        || marker == 0xC5 || marker == 0xC6 || marker == 0xC7) {
                    int h = ((b[i + 5] & 0xFF) << 8) | (b[i + 6] & 0xFF);
                    int w = ((b[i + 7] & 0xFF) << 8) | (b[i + 8] & 0xFF);
                    return new int[]{w, h};
                }
                i += 2 + len;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String groupNamesFor(int type, String[] groupNames) {
        for (int i = 0; i < groupNames.length; i++) {
            if (type == i) return groupNames[i];
        }
        return QuestionType.NAMES[type];
    }

    /** 答案文本：判断→对/错；排序→字母顺序链；其余原文 */
    private static String answerText(PaperQ q) {
        if (q.type == QuestionType.JUDGE) {
            String j = AnswerUtil.normalizeJudge(q.answer);
            return "答案：" + ("A".equals(j) ? "对" : "错");
        }
        if (q.type == QuestionType.SORT) {
            String seq = AnswerUtil.extractSequence(q.answer);
            StringBuilder sb = new StringBuilder("答案：");
            for (int i = 0; i < seq.length(); i++) {
                if (i > 0) sb.append(" → ");
                sb.append(seq.charAt(i));
            }
            return sb.toString();
        }
        return "答案：" + (q.answer == null || q.answer.isEmpty()
                ? "（未提供）" : com.ziruoling.quiz.util.TextUtil.text(q.answer));
    }

    /** 中文序号（最多支持到十六） */
    private static String cnNumber(int n) {
        String[] cn = {"一", "二", "三", "四", "五", "六", "七", "八", "九", "十",
                "十一", "十二", "十三", "十四", "十五", "十六"};
        return n >= 1 && n <= cn.length ? cn[n - 1] : String.valueOf(n);
    }

    // ==================== XML 基础件 ====================

    private static String xmlHeader() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<w:document"
                + " xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\""
                + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\""
                + " xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\""
                + " xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\""
                + " xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">"
                + "<w:body>";
    }

    private static String xmlFooter() {
        return "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>"
                + "<w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/></w:sectPr>"
                + "</w:body></w:document>";
    }

    /** 样式段落：title=居中加粗大字，bold=加粗，body=正文，option=选项，info=卷头信息行 */
    private static String para(String text, String style) {
        StringBuilder pr = new StringBuilder();
        StringBuilder rpr = new StringBuilder();
        boolean center = "title".equals(style) || "info".equals(style);
        if (center) pr.append("<w:jc w:val=\"center\"/>");
        pr.append("<w:spacing w:before=\"60\" w:after=\"60\"/>");
        if ("title".equals(style)) {
            pr.append("<w:jc w:val=\"center\"/>");
            rpr.append("<w:b/><w:sz w:val=\"40\"/><w:szCs w:val=\"40\"/>");
        } else if ("bold".equals(style)) {
            rpr.append("<w:b/><w:sz w:val=\"24\"/><w:szCs w:val=\"24\"/>");
            pr.append("<w:spacing w:before=\"120\" w:after=\"60\"/>");
        } else if ("option".equals(style)) {
            rpr.append("<w:sz w:val=\"21\"/><w:szCs w:val=\"21\"/>");
            pr.append("<w:ind w:leftChars=\"100\"/>");
        } else {
            rpr.append("<w:sz w:val=\"21\"/><w:szCs w:val=\"21\"/>");
        }
        return "<w:p><w:pPr>" + pr + "</w:pPr>"
                + "<w:r><w:rPr>" + rpr + "</w:rPr>"
                + "<w:t xml:space=\"preserve\">" + escape(text) + "</w:t></w:r></w:p>";
    }

    private static String emptyPara() {
        return "<w:p/>";
    }

    private static String pageBreak() {
        return "<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>";
    }

    private static String tableStart(int[] widths) {
        StringBuilder sb = new StringBuilder("<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/>");
        sb.append("<w:tblBorders>")
                .append(borders("top")).append(borders("left")).append(borders("bottom"))
                .append(borders("right")).append(borders("insideH")).append(borders("insideV"))
                .append("</w:tblBorders></w:tblPr>");
        // 网格
        sb.append("<w:tblGrid>");
        for (int w : widths) sb.append("<w:gridCol w:w=\"").append(w).append("\"/>");
        sb.append("</w:tblGrid>");
        return sb.toString();
    }

    private static String borders(String side) {
        return "<w:" + side + " w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"999999\"/>";
    }

    private static String tableRow(String[] cells) {
        StringBuilder sb = new StringBuilder("<w:tr>");
        for (String c : cells) {
            sb.append("<w:tc><w:tcPr><w:tcW w:w=\"1400\" w:type=\"dxa\"/></w:tcPr>");
            sb.append("<w:p><w:r><w:rPr><w:sz w:val=\"20\"/><w:szCs w:val=\"20\"/></w:rPr>")
                    .append("<w:t xml:space=\"preserve\">").append(escape(c == null ? "" : c))
                    .append("</w:t></w:r></w:p></w:tc>");
        }
        return sb.append("</w:tr>").toString();
    }

    private static String tableEnd() {
        return "</w:tbl>";
    }

    private static void writeDocx(OutputStream out, String documentXml) throws IOException {
        writeDocx(out, documentXml, null, null);
    }

    private static void writeDocx(OutputStream out, String documentXml,
                                  List<byte[]> media, List<String> exts) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
        try {
            put(zip, "[Content_Types].xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                            + "<Default Extension=\"png\" ContentType=\"image/png\"/>"
                            + "<Default Extension=\"jpg\" ContentType=\"image/jpeg\"/>"
                            + "<Default Extension=\"jpeg\" ContentType=\"image/jpeg\"/>"
                            + "<Default Extension=\"gif\" ContentType=\"image/gif\"/>"
                            + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                            + "</Types>");
            put(zip, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                            + "</Relationships>");
            if (media != null && !media.isEmpty()) {
                StringBuilder rels = new StringBuilder();
                rels.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                        .append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
                for (int i = 0; i < media.size(); i++) {
                    rels.append("<Relationship Id=\"rIdImg").append(i + 1)
                            .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/image")
                            .append(i + 1).append('.').append(exts.get(i)).append("\"/>");
                }
                rels.append("</Relationships>");
                put(zip, "word/_rels/document.xml.rels", rels.toString());
                for (int i = 0; i < media.size(); i++) {
                    putBytes(zip, "word/media/image" + (i + 1) + "." + exts.get(i), media.get(i));
                }
            }
            put(zip, "word/document.xml", documentXml);
        } finally {
            closeQuietly(zip);
        }
    }

    private static void putBytes(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
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