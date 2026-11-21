package com.ziruoling.quiz.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 表格行 → 题目 的映射与校验（xlsx / csv / docx 表格共用）。
 * <p>支持表头自动识别；无表头时按标准模板列序：
 * 题型, 题目, 选项A-F, 答案, 解析, 分类
 */
public final class RowMapper {

    /** 列映射 */
    public static class Mapping {
        public int type = -1;
        public int content = -1;
        public int ans = -1;
        public int analysis = -1;
        public int cat = -1;
        public final int[] opt = {-1, -1, -1, -1, -1, -1};
        public boolean fromHeader = false;
    }

    private RowMapper() {}

    /** 标准模板列序（无表头时） */
    public static Mapping template() {
        Mapping m = new Mapping();
        m.type = 0;
        m.content = 1;
        for (int i = 0; i < 6; i++) m.opt[i] = 2 + i;
        m.ans = 8;
        m.analysis = 9;
        m.cat = 10;
        return m;
    }

    /** 表头识别：必须找到"题目/题干"列且找到"答案"列才算表头 */
    public static Mapping detectHeader(String[] row) {
        if (row == null) return null;
        Mapping m = new Mapping();
        m.fromHeader = true;
        for (int i = 0; i < row.length; i++) {
            String c = row[i] == null ? "" : AnswerUtil.toHalfWidth(row[i]).trim().toUpperCase();
            if (c.isEmpty()) continue;
            if (m.analysis < 0 && (c.contains("解析") || c.contains("解释"))) {
                m.analysis = i;
            } else if (m.cat < 0 && (c.contains("分类") || c.contains("章节") || c.contains("科目")
                    || c.contains("知识点") || c.contains("单元") || c.contains("模块"))) {
                m.cat = i;
            } else if (m.type < 0 && (c.contains("题型") || c.equals("类型") || c.equals("TYPE"))) {
                m.type = i;
            } else if (m.ans < 0 && c.contains("答案")) {
                m.ans = i;
            } else if (m.content < 0 && (c.contains("题目") || c.contains("题干") || c.contains("题面")
                    || c.contains("问题") || c.contains("内容"))) {
                m.content = i;
            } else {
                String stripped = c.replace("选项", "").trim();
                if (stripped.matches("^[A-F]$")) {
                    int idx = stripped.charAt(0) - 'A';
                    if (m.opt[idx] < 0) m.opt[idx] = i;
                }
            }
        }
        if (m.content >= 0 && m.ans >= 0) return m;
        return null;
    }

    /** 依据题型文本/选项/答案推断题型 */
    public static int inferType(String typeText, List<String> options, String ansRaw) {
        int t = QuestionType.fromText(typeText);
        if (t >= 0) {
            if (t == QuestionType.SINGLE && typeText != null
                    && typeText.contains("选择")
                    && AnswerUtil.extractLetters(ansRaw).length() > 1) {
                return QuestionType.MULTI;
            }
            return t;
        }
        String judgeAns = AnswerUtil.normalizeJudge(ansRaw);
        if (options == null || options.isEmpty()) {
            if (!judgeAns.isEmpty()) return QuestionType.JUDGE;
            return QuestionType.FILL;
        }
        if (options.size() == 2 && AnswerUtil.looksJudgeOption(options.get(0))
                && AnswerUtil.looksJudgeOption(options.get(1)) && !judgeAns.isEmpty()) {
            return QuestionType.JUDGE;
        }
        if (AnswerUtil.extractLetters(ansRaw).length() > 1) return QuestionType.MULTI;
        return QuestionType.SINGLE;
    }

    /**
     * 将一行数据映射为题目；失败时向 out.warnings 追加原因并返回 null。
     *
     * @param rowNo 行号（1 起，用于提示）
     */
    public static ParsedQuestion mapRow(String[] cells, Mapping m, int rowNo, ParseResult out, String fallbackCategory) {
        if (cells == null || allEmpty(cells)) return null;
        String typeText = cell(cells, m.type);
        String content = cell(cells, m.content).trim();
        if (content.isEmpty()) {
            out.warnings.add("第" + rowNo + "行：缺少题目内容，已跳过");
            return null;
        }
        int docNo = AnswerUtil.parseNumbering(content);
        content = AnswerUtil.stripNumbering(content);

        List<String> options = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            if (m.opt[i] >= 0) {
                String o = cell(cells, m.opt[i]).trim();
                if (!o.isEmpty()) options.add(o);
            }
        }
        String ansRaw = cell(cells, m.ans).trim();
        String analysis = cell(cells, m.analysis).trim();
        String category = cell(cells, m.cat).trim();
        if (category.isEmpty()) category = fallbackCategory == null ? "" : fallbackCategory;

        int type = inferType(typeText, options, ansRaw);

        ParsedQuestion q = new ParsedQuestion();
        q.type = type;
        // 题型列写了但识别不出来 → 这个类型是按选项/答案猜的，导入前要问用户
        q.typeGuessed = typeText != null && !typeText.trim().isEmpty()
                && QuestionType.fromText(typeText) < 0;
        q.content = content;
        q.docNo = docNo;
        q.analysis = analysis;
        q.category = category;
        q.sourceRow = rowNo;

        switch (type) {
            case QuestionType.SINGLE:
            case QuestionType.MULTI:
                if (!applyChoice(q, options, ansRaw, rowNo, out)) return null;
                break;
            case QuestionType.JUDGE: {
                String a = AnswerUtil.normalizeJudge(ansRaw);
                if (a.isEmpty()) {
                    out.warnings.add("第" + rowNo + "行：判断题答案无效（应填 对/错/√/×/A/B 等），已跳过");
                    return null;
                }
                q.options.add("正确");
                q.options.add("错误");
                q.answer = a;
                break;
            }
            case QuestionType.FILL:
                q.answer = ansRaw;
                if (ansRaw.isEmpty()) {
                    out.warnings.add("第" + rowNo + "行：填空题缺少参考答案，练习时将自行核对");
                }
                break;
            case QuestionType.SORT: {
                // 排序题：答案为正确顺序的字母串，长度须等于选项数
                String seq = AnswerUtil.extractSequence(ansRaw);
                if (options.size() < 2) {
                    out.warnings.add("第" + rowNo + "行：排序题选项不足（至少需要2个），已跳过");
                    return null;
                }
                if (seq.length() != options.size()) {
                    out.warnings.add("第" + rowNo + "行：排序题答案字母数（" + seq.length()
                            + "）应等于选项数（" + options.size() + "），如 3 项填 BAC，已跳过");
                    return null;
                }
                q.options = new ArrayList<>(options);
                q.answer = seq;
                break;
            }
            case QuestionType.OTHER:
                // 其它题：答案为参考答案文本，按简答方式自评；可带图片
                q.answer = ansRaw;
                if (ansRaw.isEmpty()) {
                    out.warnings.add("第" + rowNo + "行：其它题缺少参考答案");
                }
                if (!options.isEmpty()) {
                    out.infos.add("第" + rowNo + "行：其它题按“参考文本 + 自评”处理，选项列已忽略");
                }
                break;
            default: // 简答
                q.answer = ansRaw;
                if (ansRaw.isEmpty()) {
                    out.warnings.add("第" + rowNo + "行：简答题缺少参考答案");
                }
                break;
        }
        return q;
    }

    /**
     * 单选/多选题的选项与答案校验。单选题遇到多字母答案时自动升级为多选并记录提示。
     *
     * @return true=成功，false=已跳过
     */
    static boolean applyChoice(ParsedQuestion q, List<String> options, String ansRaw, int rowNo, ParseResult out) {
        if (options.size() < 2) {
            out.warnings.add("第" + rowNo + "行：选项不足（至少需要2个），已跳过");
            return false;
        }
        String letters = AnswerUtil.extractLetters(ansRaw);
        if (letters.isEmpty()) {
            out.warnings.add("第" + rowNo + "行：答案缺失或无效（应填选项字母，如 A 或 ABD），已跳过");
            return false;
        }
        if (q.type == QuestionType.SINGLE && letters.length() > 1) {
            q.type = QuestionType.MULTI;
            out.infos.add("第" + rowNo + "行：答案为多字母，已按多选题处理");
        }
        if (letters.charAt(letters.length() - 1) - 'A' >= options.size()) {
            out.warnings.add("第" + rowNo + "行：答案超出选项范围，已跳过");
            return false;
        }
        q.options = new ArrayList<>(options);
        q.answer = letters;
        return true;
    }

    /** 批量映射行（自动处理表头） */
    public static void mapRows(List<String[]> rows, ParseResult out) {
        if (rows == null || rows.isEmpty()) return;
        Mapping m = null;
        int start = 0;
        for (int i = 0; i < Math.min(3, rows.size()); i++) {
            m = detectHeader(rows.get(i));
            if (m != null) {
                start = i + 1;
                out.infos.add("已按第" + (i + 1) + "行表头识别列（题型/题目/选项/答案/解析/分类）");
                break;
            }
        }
        if (m == null) {
            m = template();
            start = 0;
            out.infos.add("未识别到表头，按标准列序解析：题型,题目,选项A-F,答案,解析,分类");
        }
        out.totalRows = 0;
        for (int i = start; i < rows.size(); i++) {
            String[] cells = rows.get(i);
            if (cells == null || allEmpty(cells)) continue;
            out.totalRows++;
            ParsedQuestion q = mapRow(cells, m, i + 1, out, "");
            if (q != null) out.questions.add(q);
        }
    }

    static String cell(String[] cells, int idx) {
        if (cells == null || idx < 0 || idx >= cells.length) return "";
        String s = cells[idx];
        return s == null ? "" : s;
    }

    static boolean allEmpty(String[] cells) {
        for (String c : cells) {
            if (c != null && !c.trim().isEmpty()) return false;
        }
        return true;
    }
}
