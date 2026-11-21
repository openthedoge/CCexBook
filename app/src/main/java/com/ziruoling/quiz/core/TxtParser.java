package com.ziruoling.quiz.core;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文本文档题库解析（零依赖）。
 * <p>支持格式：
 * <pre>
 * [单选] 题干内容
 * A. 选项内容
 * B. 选项内容
 * 答案：A
 * 解析：……
 * 分类：网络基础
 * （空行分隔题目；支持 "一、单选题" 大节标题与 "第一章 xxx" 章节标题）
 * </pre>
 */
public final class TxtParser {

    private static final Pattern P_SECTION = Pattern.compile(
            "^[（(]?[一二三四五六七八九十]+[）)、.．]\\s*(单选题?|多选题?|判断题?|填空题?|简答题?|问答题?|选择题?)\\s*$");
    private static final Pattern P_CHAPTER = Pattern.compile(
            "^第.{1,15}?(章|部分|课|讲|节|单元)\\s*[^、]{0,20}$");
    private static final Pattern P_TYPE_TAG = Pattern.compile(
            "^[\\[【（(]\\s*(单选题?|多选题?|判断题?|填空题?|简答题?|问答题?)\\s*[\\]】）)]\\s*(.*)$");
    private static final Pattern P_TYPE_TAG_END = Pattern.compile(
            "^(.+?)\\s*[\\[【（(]\\s*(单选题?|多选题?|判断题?|填空题?|简答题?|问答题?)\\s*[\\]】）)]\\s*$");
    private static final Pattern P_META_ANS = Pattern.compile(
            "^\\s*(正确答案|参考答案|标准答案|答案|答)\\s*[：:＝=]\\s*(.*)$");
    private static final Pattern P_META_ANA = Pattern.compile(
            "^\\s*(答案解析|解析|解释)\\s*[：:]\\s*(.*)$");
    private static final Pattern P_META_CAT = Pattern.compile(
            "^\\s*(分类|类别|章节|科目|知识点)\\s*[：:]\\s*(.*)$");
    private static final Pattern P_OPT_PAREN = Pattern.compile(
            "^\\s*[（(]\\s*([A-Fa-f])\\s*[）)]\\s*(.*)$");
    private static final Pattern P_OPT_SEP = Pattern.compile(
            "^\\s*([A-Fa-f])\\s*[.、．,，:：]\\s*(.*)$");
    private static final Pattern P_INLINE_PAIR = Pattern.compile(
            "([A-Fa-f])\\s*[.、．,，:：]\\s*([^A-Fa-f]+)");

    /** 解析中的一题 */
    private static class Q {
        int explicitType = -1;
        int startRow = 0;
        int docNo = 0;
        final StringBuilder content = new StringBuilder();
        final List<String> options = new ArrayList<>();
        String ansRaw = "";
        String analysis = "";
        String category = "";
    }

    public TxtParser() {
    }

    public ParseResult parse(File f) throws IOException {
        return parseText(readAuto(f));
    }

    public static ParseResult parseText(String text) {
        List<String> lines = new ArrayList<>();
        int st = 0;
        String t = text.replace("\r\n", "\n").replace('\r', '\n');
        for (int i = 0; i < t.length(); i++) {
            if (t.charAt(i) == '\n') {
                lines.add(t.substring(st, i));
                st = i + 1;
            }
        }
        if (st < t.length()) lines.add(t.substring(st));
        return parseLines(lines);
    }

    public static ParseResult parseLines(List<String> rawLines) {
        ParseResult res = new ParseResult();
        List<String> lines = rawLines == null ? new ArrayList<String>() : rawLines;
        res.totalRows = lines.size();
        int sectionType = -1;
        String sectionCat = "";
        Q cur = null;

        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            String line = raw == null ? "" : raw.trim();
            int no = i + 1;

            if (line.isEmpty()) {
                cur = finalize(cur, res, no, sectionType, sectionCat);
                continue;
            }

            // 大节标题：一、单选题
            Matcher mSec = P_SECTION.matcher(line);
            if (mSec.matches()) {
                cur = finalize(cur, res, no, sectionType, sectionCat);
                int t = QuestionType.fromText(mSec.group(1));
                if (t >= 0) sectionType = t;
                continue;
            }
            // 章节标题：第一章 网络基础
            if (P_CHAPTER.matcher(line).matches()) {
                sectionCat = line;
                continue;
            }
            // 答案行
            Matcher mA = P_META_ANS.matcher(line);
            if (mA.matches()) {
                if (cur != null) cur.ansRaw = mA.group(2).trim();
                continue;
            }
            // 解析行
            Matcher mAna = P_META_ANA.matcher(line);
            if (mAna.matches()) {
                if (cur != null) {
                    String add = mAna.group(2).trim();
                    if (!add.isEmpty()) {
                        cur.analysis = cur.analysis.isEmpty() ? add : cur.analysis + "\n" + add;
                    }
                }
                continue;
            }
            // 分类行
            Matcher mCat = P_META_CAT.matcher(line);
            if (mCat.matches()) {
                if (cur != null) {
                    cur.category = mCat.group(2).trim();
                } else {
                    sectionCat = mCat.group(2).trim();
                }
                continue;
            }
            // 题型标记开头：[单选] 题干
            Matcher mTag = P_TYPE_TAG.matcher(line);
            if (mTag.matches()) {
                cur = finalize(cur, res, no, sectionType, sectionCat);
                cur = new Q();
                cur.startRow = no;
                cur.explicitType = QuestionType.fromText(mTag.group(1));
                String rest = mTag.group(2).trim();
                if (!rest.isEmpty()) {
                    cur.docNo = AnswerUtil.parseNumbering(rest);
                    cur.content.append(AnswerUtil.stripNumbering(rest));
                }
                continue;
            }
            // 选项行（先看内联对："A.对 B.错"）
            if (cur != null && cur.content.length() > 0) {
                List<String> inline = parseInlineOptions(line);
                if (inline != null) {
                    cur.options.addAll(inline);
                    continue;
                }
                String opt = matchOption(line);
                if (opt != null) {
                    cur.options.add(opt);
                    continue;
                }
            }
            // 普通文本行 → 题干
            if (cur != null && !cur.ansRaw.isEmpty()) {
                // 已有答案：视为新题开始
                cur = finalize(cur, res, no, sectionType, sectionCat);
            }
            if (cur == null) {
                cur = new Q();
                cur.startRow = no;
            }
            // 结尾题型标记："题干……[单选]"
            Matcher mTagEnd = P_TYPE_TAG_END.matcher(line);
            if (mTagEnd.matches()) {
                int t = QuestionType.fromText(mTagEnd.group(2));
                String head = mTagEnd.group(1) == null ? "" : mTagEnd.group(1).trim();
                if (t >= 0 && !head.isEmpty()) {
                    cur.explicitType = t;
                    line = head;
                }
            }
            // 题干第一行才取原题号（"5.题干" → 5），后续续行不覆盖
            if (cur.content.length() == 0) {
                cur.docNo = AnswerUtil.parseNumbering(line);
            }
            String piece = AnswerUtil.stripNumbering(line.trim());
            if (cur.content.length() > 0) cur.content.append('\n');
            cur.content.append(piece);
        }
        finalize(cur, res, lines.size(), sectionType, sectionCat);
        return res;
    }

    private static Q finalize(Q cur, ParseResult res, int rowNo, int sectionType, String sectionCat) {
        if (cur == null) return null;
        if (cur.content.length() == 0) return null;
        int source = cur.startRow > 0 ? cur.startRow : rowNo;
        String content = cur.content.toString().trim();
        if (content.isEmpty()) return null;

        ParsedQuestion q = new ParsedQuestion();
        q.sourceRow = source;
        q.docNo = cur.docNo;
        q.content = content;
        q.analysis = cur.analysis;
        q.category = !cur.category.isEmpty() ? cur.category : (sectionCat == null ? "" : sectionCat);

        int type = cur.explicitType >= 0 ? cur.explicitType
                : (sectionType >= 0 ? sectionType : RowMapper.inferType("", cur.options, cur.ansRaw));
        q.type = type;

        switch (type) {
            case QuestionType.SINGLE:
            case QuestionType.MULTI:
                if (!RowMapper.applyChoice(q, cur.options, cur.ansRaw, source, res)) return null;
                break;
            case QuestionType.JUDGE: {
                String a = AnswerUtil.normalizeJudge(cur.ansRaw);
                if (a.isEmpty()) {
                    res.warnings.add("第" + source + "行附近：判断题答案无效（应填 对/错/√/×/A/B 等），已跳过");
                    return null;
                }
                q.options.add("正确");
                q.options.add("错误");
                q.answer = a;
                break;
            }
            case QuestionType.FILL:
                q.answer = cur.ansRaw;
                if (cur.ansRaw.isEmpty()) {
                    res.warnings.add("第" + source + "行附近：填空题缺少参考答案，练习时将自行核对");
                }
                break;
            default:
                q.answer = cur.ansRaw;
                if (cur.ansRaw.isEmpty()) {
                    res.warnings.add("第" + source + "行附近：简答题缺少参考答案");
                }
                break;
        }
        res.questions.add(q);
        return null;
    }

    /** 单个选项行匹配（返回选项文本），不匹配返回 null */
    static String matchOption(String line) {
        Matcher m1 = P_OPT_PAREN.matcher(line);
        if (m1.matches()) {
            String text = m1.group(2);
            return text == null ? "" : text.trim();
        }
        Matcher m2 = P_OPT_SEP.matcher(line);
        if (m2.matches()) {
            String text = m2.group(2);
            return text == null ? "" : text.trim();
        }
        return null;
    }

    /** 内联多选项："A.对 B.错"，返回选项列表；不是内联样式返回 null */
    static List<String> parseInlineOptions(String line) {
        Matcher m = P_INLINE_PAIR.matcher(line);
        int expectStart = 0;
        int pos = 0;
        List<String> opts = new ArrayList<>();
        List<Character> letters = new ArrayList<>();
        while (m.find(pos)) {
            if (m.start() > 0 && line.charAt(m.start() - 1) != ' ' && line.charAt(m.start() - 1) != '\t'
                    && line.charAt(m.start() - 1) != '　' && pos == 0) {
                return null;
            }
            if (m.start() != expectStart) return null;
            String text = m.group(2).trim();
            if (text.isEmpty()) return null;
            opts.add(text);
            letters.add(Character.toUpperCase(m.group(1).charAt(0)));
            expectStart = m.end();
            pos = m.end();
        }
        if (opts.size() < 2) return null;
        // 字母须连续且不重复
        for (int i = 0; i < letters.size(); i++) {
            if (letters.get(i) != (char) ('A' + i)) return null;
        }
        return opts;
    }

    /** 自动识别编码读取文本：UTF-8(BOM)/UTF-16/GBK */
    public static String readAuto(File f) throws IOException {
        return decodeAuto(Files.readAllBytes(f.toPath()));
    }

    public static String decodeAuto(byte[] data) {
        if (data == null || data.length == 0) return "";
        if (data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF) {
            return new String(data, 3, data.length - 3, StandardCharsets.UTF_8);
        }
        if (data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xFE) {
            return new String(data, 2, data.length - 2, StandardCharsets.UTF_16LE);
        }
        if (data.length >= 2 && (data[0] & 0xFF) == 0xFE && (data[1] & 0xFF) == 0xFF) {
            return new String(data, 2, data.length - 2, StandardCharsets.UTF_16BE);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException e) {
            return new String(data, Charset.forName("GBK"));
        }
    }
}
