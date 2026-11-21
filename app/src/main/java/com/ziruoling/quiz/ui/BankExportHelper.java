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
