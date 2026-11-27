package com.ziruoling.quiz.ui;

import com.ziruoling.quiz.core.DocxWriter;
import com.ziruoling.quiz.data.Db;
import com.ziruoling.quiz.data.Question;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** 题库导出（Excel / Word）的公共逻辑：首页与题库页共用 */
final class BankExportHelper {

    private BankExportHelper() {}

    /**
     * 导出文件名过滤：非法字符 \ / : * ? " &lt; &gt; | 与控制字符替换为 {@code _}，
     * 去掉首尾空白/点，空名回退「题库」。
     */
    static String safeFileName(String raw, String ext) {
        String s = raw == null ? "" : raw;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' || ch == '/' || ch == ':' || ch == '*' || ch == '?'
                    || ch == '"' || ch == '<' || ch == '>' || ch == '|'
                    || Character.isISOControl(ch)) {
                sb.append('_');
            } else {
                sb.append(ch);
            }
        }
        String name = sb.toString();
        int start = 0;
        int end = name.length();
        while (start < end && isTrimmedChar(name.charAt(start))) start++;
        while (end > start && isTrimmedChar(name.charAt(end - 1))) end--;
        name = name.substring(start, end);
        if (name.isEmpty()) name = "题库";
        return name + ext;
    }

    /** 首尾需要去掉的字符：空白与点 */
    private static boolean isTrimmedChar(char ch) {
        return Character.isWhitespace(ch) || Character.isSpaceChar(ch) || ch == '.';
    }

    /** 读题库 + 图片字节，组装成 docx 导出用的题目列表（可能较慢，请在后台线程调用） */
    static List<DocxWriter.PaperQ> paperQs(long bankId) {
        List<DocxWriter.PaperQ> out = new ArrayList<>();
        Db.CategoryCounter counter = new Db.CategoryCounter();
        for (Question q : Db.questionsOfBank(bankId)) {
            DocxWriter.PaperQ pq = new DocxWriter.PaperQ();
            pq.type = q.type;
            pq.content = q.content;
            pq.options = q.options;
            pq.answer = q.answer;
            pq.analysis = q.analysis;
            pq.category = q.category;
            // 原文号优先；没号时按分类自动编号（导出与界面一致）
            int seq = counter.next(q.category);
            pq.docNo = q.docNo > 0 ? q.docNo : seq;
            if (q.imgPaths != null) {
                for (String rel : q.imgPaths) {
                    byte[] bytes = Db.imageBytes(rel);
                    if (bytes != null) pq.images.add(bytes);
                }
            }
            out.add(pq);
        }
        return out;
    }

    /** 写整库 Word 文档（表格式，图片内嵌） */
    static void writeDocx(OutputStream os, String bankName, long bankId) throws IOException {
        DocxWriter.writeBank(os, bankName, paperQs(bankId));
    }
}
