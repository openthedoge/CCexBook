package com.ziruoling.quiz.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV 题库解析：自动识别分隔符（逗号/分号/制表符）、引号转义、
 * 编码自动识别（UTF-8 / GBK / UTF-16），列映射同 Excel。
 */
public final class CsvParser {

    public ParseResult parse(File f) throws IOException {
        byte[] data = Files.readAllBytes(f.toPath());
        String text = TxtParser.decodeAuto(data);
        char delim = detectDelimiter(text);
        List<String[]> rows = splitAll(text, delim);
        ParseResult res = new ParseResult();
        RowMapper.mapRows(rows, res);
        return res;
    }

    /** 依据首个非空行的分隔符出现次数自动选择 */
    static char detectDelimiter(String text) {
        String first = null;
        for (String line : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", 0)) {
            if (!line.trim().isEmpty()) {
                first = line;
                break;
            }
        }
        if (first == null) return ',';
        int comma = count(first, ',');
        int semi = count(first, ';');
        int tab = count(first, '\t');
        if (tab > comma && tab >= semi) return '\t';
        if (semi > comma) return ';';
        return ',';
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }

    /** 完整解析（支持引号内换行与转义引号 ""） */
    static List<String[]> splitAll(String text, char delim) {
        List<String[]> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuote = false;
        boolean cellStarted = false;
        int n = text.length();
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (inQuote) {
                if (c == '\"') {
                    if (i + 1 < n && text.charAt(i + 1) == '\"') {
                        cell.append('\"');
                        i++;
                    } else {
                        inQuote = false;
                    }
                } else {
                    cell.append(c);
                }
            } else {
                if (c == '\"' && !cellStarted) {
                    inQuote = true;
                    cellStarted = true;
                } else if (c == delim) {
                    row.add(cell.toString().trim());
                    cell.setLength(0);
                    cellStarted = false;
                } else if (c == '\n') {
                    row.add(cell.toString().trim());
                    cell.setLength(0);
                    cellStarted = false;
                    if (!(row.size() == 1 && row.get(0).isEmpty())) {
                        rows.add(row.toArray(new String[0]));
                    }
                    row = new ArrayList<>();
                } else if (c != '\r') {
                    cell.append(c);
                    cellStarted = true;
                }
            }
        }
        row.add(cell.toString().trim());
        if (!(row.size() == 1 && row.get(0).isEmpty())) {
            rows.add(row.toArray(new String[0]));
        }
        return rows;
    }
}
