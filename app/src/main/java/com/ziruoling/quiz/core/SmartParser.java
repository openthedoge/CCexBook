package com.ziruoling.quiz.core;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.zip.ZipFile;

/** 智能分派：按扩展名 + 文件内容选择解析器 */
public final class SmartParser {

    private SmartParser() {}

    public static ParseResult parse(File f, String fileName) throws IOException {
        if (!f.exists() || f.length() == 0) {
            throw new IOException("文件为空或不存在");
        }
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(".xls")) {
            throw new IOException("暂不支持旧版 .xls，请在 Excel 中另存为 .xlsx 后导入");
        }
        if (lower.endsWith(".doc")) {
            throw new IOException("暂不支持旧版 .doc，请在 Word 中另存为 .docx 后导入");
        }
        if (lower.endsWith(".pdf")) {
            throw new IOException("暂不支持 PDF，请导出为 xlsx / docx / txt 后导入");
        }
        if (lower.endsWith(".xlsx")) return new XlsxParser().parse(f);
        if (lower.endsWith(".docx")) return new DocxParser().parse(f);
        if (lower.endsWith(".csv")) return new CsvParser().parse(f);
        if (lower.endsWith(".txt") || lower.endsWith(".text")) return new TxtParser().parse(f);

        // 按内容识别
        if (isZip(f)) {
            ZipFile zip = new ZipFile(f);
            try {
                if (zip.getEntry("xl/workbook.xml") != null) return new XlsxParser().parse(f);
                if (zip.getEntry("word/document.xml") != null) return new DocxParser().parse(f);
            } finally {
                zip.close();
            }
            throw new IOException("不支持的压缩文档格式（仅支持 xlsx / docx）");
        }
        return new TxtParser().parse(f);
    }

    private static boolean isZip(File f) {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(f, "r");
            return raf.read() == 'P' && raf.read() == 'K';
        } catch (IOException e) {
            return false;
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
