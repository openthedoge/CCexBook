package com.ziruoling.quiz.core;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RowMapperTest {

    private ParseResult res = new ParseResult();

    private static String[] row(String... cells) {
        return cells;
    }

    @Test
    public void testDetectHeader() {
        RowMapper.Mapping m = RowMapper.detectHeader(row(
                "题型", "题目", "选项A", "B", "选项C", "D", "E", "F", "正确答案", "解析", "分类"));
        assertEquals(0, m.type);
        assertEquals(1, m.content);
        assertEquals(2, m.opt[0]);
        assertEquals(3, m.opt[1]);
        assertEquals(4, m.opt[2]);
        assertEquals(5, m.opt[3]);
        assertEquals(6, m.opt[4]);
        assertEquals(7, m.opt[5]);
        assertEquals(8, m.ans);
        assertEquals(9, m.analysis);
        assertEquals(10, m.cat);
        assertTrue(m.fromHeader);
    }

    @Test
    public void testDetectHeaderMissingType() {
        RowMapper.Mapping m = RowMapper.detectHeader(row("题目", "A", "B", "C", "D", "答案"));
        assertEquals(-1, m.type);
        assertEquals(0, m.content);
        assertEquals(5, m.ans);
    }

    @Test
    public void testDetectHeaderInvalid() {
        assertNull(RowMapper.detectHeader(row("1", "2", "3")));
        assertNull(RowMapper.detectHeader(row("题目", "A", "B"))); // 无答案列
        assertNull(RowMapper.detectHeader(null));
    }

    @Test
    public void testMapRowSingle() {
        RowMapper.Mapping m = RowMapper.template();
        ParsedQuestion q = RowMapper.mapRow(row(
                "单选题", "OSI 参考模型共几层？", "4层", "5层", "6层", "7层", "", "", "D", "物理层到应用层", "网络基础"),
                m, 2, res, "");
        assertEquals(QuestionType.SINGLE, q.type);
        assertEquals("OSI 参考模型共几层？", q.content);
        assertEquals(4, q.options.size());
        assertEquals("7层", q.options.get(3));
        assertEquals("D", q.answer);
        assertEquals("网络基础", q.category);
        assertEquals(0, res.warnings.size());
    }

    @Test
    public void testMapRowMulti() {
        RowMapper.Mapping m = RowMapper.template();
        ParsedQuestion q = RowMapper.mapRow(row(
                "多选", "下列属于传输层协议的有", "IP", "TCP", "UDP", "ARP", "", "", "bc", "", ""),
                m, 3, res, "");
        assertEquals(QuestionType.MULTI, q.type);
        assertEquals("BC", q.answer);
    }

    @Test
    public void testMapRowJudge() {
        RowMapper.Mapping m = RowMapper.template();
        ParsedQuestion q = RowMapper.mapRow(row(
                "判断题", "ping 命令使用 ICMP 协议。", "", "", "", "", "", "", "√", "", "网络检测"),
                m, 4, res, "");
        assertEquals(QuestionType.JUDGE, q.type);
        assertEquals("A", q.answer);
        assertEquals(2, q.options.size());
        assertEquals("正确", q.options.get(0));
    }

    @Test
    public void testMapRowFill() {
        RowMapper.Mapping m = RowMapper.template();
        ParsedQuestion q = RowMapper.mapRow(row(
                "填空", "默认情况下 FTP 服务使用的控制端口是____。", "", "", "", "", "", "", "21", "", ""),
                m, 5, res, "");
        assertEquals(QuestionType.FILL, q.type);
        assertEquals("21", q.answer);
        assertTrue(q.options.isEmpty());
    }

    @Test
    public void testSingleAutoUpgradeMulti() {
        RowMapper.Mapping m = RowMapper.template();
        List<ParsedQuestion> keep = new ArrayList<>();
        ParsedQuestion q = RowMapper.mapRow(row(
                "单选", "下列哪些是网络设备？", "路由器", "交换机", "防火墙", "打印机", "", "", "AB", "", ""),
                m, 6, res, "");
        // 单选遇多字母答案：升级为多选
        assertEquals(QuestionType.MULTI, q.type);
        assertEquals("AB", q.answer);
        assertTrue(res.infos.size() >= 1);
    }

    @Test
    public void testInvalidRows() {
        RowMapper.Mapping m = RowMapper.template();
        int before = res.warnings.size();
        assertNull(RowMapper.mapRow(row("单选", "", "a", "b", "c", "d", "", "", "A", "", ""), m, 7, res, ""));
        assertNull(RowMapper.mapRow(row("单选", "题干", "a", "b", "", "", "", "", "", "", ""), m, 8, res, "")); // 无答案
        assertNull(RowMapper.mapRow(row("单选", "题干", "a", "b", "c", "d", "", "", "E", "", ""), m, 9, res, "")); // 超范围
        assertNull(RowMapper.mapRow(row("判断", "题干", "", "", "", "", "", "", "也许", "", ""), m, 10, res, "")); // 答案无效
        assertEquals(before + 4, res.warnings.size());
    }

    @Test
    public void testMapRowsWithHeader() {
        List<String[]> rows = new ArrayList<>();
        rows.add(row("题型", "题目", "A", "B", "C", "D", "答案", "解析", "分类"));
        rows.add(row("单选", "题1", "甲", "乙", "丙", "丁", "A", "略", "第一章"));
        rows.add(row("单选", "题2", "甲", "乙", "丙", "丁", "C", "", "第一章"));
        ParseResult r = new ParseResult();
        RowMapper.mapRows(rows, r);
        assertEquals(2, r.questions.size());
        assertEquals(2, r.totalRows);
        assertTrue(r.infos.get(0).contains("表头"));
    }

    @Test
    public void testMapRowsTemplateNoHeader() {
        List<String[]> rows = new ArrayList<>();
        rows.add(row("单选", "题1", "甲", "乙", "丙", "丁", "", "", "A", "", ""));
        rows.add(row("判断", "题2", "", "", "", "", "", "", "错", "", ""));
        ParseResult r = new ParseResult();
        RowMapper.mapRows(rows, r);
        assertEquals(2, r.questions.size());
        assertEquals(QuestionType.JUDGE, r.questions.get(1).type);
    }

    @Test
    public void testMapRowSort() {
        RowMapper.Mapping m = RowMapper.template();
        ParsedQuestion q = RowMapper.mapRow(row(
                "排序题", "将步骤按顺序排列。", "第一步", "第二步", "第三步", "", "", "", "CBA", "按发生顺序", "网络基础"),
                m, 11, res, "");
        assertEquals(QuestionType.SORT, q.type);
        assertEquals(3, q.options.size());
        assertEquals("CBA", q.answer);
    }

    @Test
    public void testMapRowSortInvalidCount() {
        RowMapper.Mapping m = RowMapper.template();
        int before = res.warnings.size();
        // 答案字母数与选项数不一致应被跳过
        assertNull(RowMapper.mapRow(row(
                "排序", "题干。", "甲", "乙", "丙", "", "", "", "AB", "", ""),
                m, 12, res, ""));
        assertEquals(before + 1, res.warnings.size());
    }

    @Test
    public void testMapRowOther() {
        RowMapper.Mapping m = RowMapper.template();
        ParsedQuestion q = RowMapper.mapRow(row(
                "其它题", "描述组网连接。", "", "", "", "", "", "",
                "光猫完成光/电转换后由路由器接入。", "参考文本作答+自评", "网络与交换"),
                m, 13, res, "");
        assertEquals(QuestionType.OTHER, q.type);
        assertEquals("光猫完成光/电转换后由路由器接入。", q.answer);
        assertTrue(q.options.isEmpty());
        assertTrue(!q.typeGuessed);
    }

    /** 题型列写了但识别不了：先猜类型，并标记为待确认 */
    @Test
    public void testUnrecognizedTypeTextIsMarkedGuessed() {
        RowMapper.Mapping m = RowMapper.template();
        ParsedQuestion q = RowMapper.mapRow(row(
                "名词解释", "什么是光衰。", "", "", "", "", "", "",
                "光功率的衰减量。", "", ""),
                m, 14, res, "");
        assertNotNull(q);
        assertTrue("无法识别的题型应标记为待确认", q.typeGuessed);
    }

    @Test
    public void testFromTextNewTypes() {
        assertEquals(QuestionType.SORT, QuestionType.fromText("排序题"));
        assertEquals(QuestionType.SORT, QuestionType.fromText("排序"));
        assertEquals(QuestionType.OTHER, QuestionType.fromText("其它题"));
        assertEquals(QuestionType.OTHER, QuestionType.fromText("其他类型"));
        // 看图不再作为独立题型：交给答案/选项推断，识别不了再问用户
        assertEquals(-1, QuestionType.fromText("看图题"));
        assertEquals(-1, QuestionType.fromText("识图题"));
        assertEquals(7, QuestionType.COUNT);
        assertTrue(QuestionType.examinable(QuestionType.SORT));
        assertTrue(!QuestionType.examinable(QuestionType.OTHER));
        assertTrue(!QuestionType.examinable(QuestionType.SHORT));
    }
}
