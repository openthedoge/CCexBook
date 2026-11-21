package com.ziruoling.quiz.core;

import java.util.ArrayList;
import java.util.List;

/** 解析中的一道题目（导入阶段的数据载体） */
public class ParsedQuestion {
    public int type = QuestionType.SINGLE;
    public String content = "";
    public List<String> options = new ArrayList<>();
    public String answer = "";
    public String analysis = "";
    public String category = "";
    /** 原文档里的题号（按篇/节重新计数），0 = 无 */
    public int docNo = 0;
    /** 来源行号（1 起，用于错误提示） */
    public int sourceRow = 0;
    /** 题型是“猜”出来的（题型列写了但识别不了），导入前需要让用户确认 */
    public boolean typeGuessed;
    /** 行内图片字节（docx 表格内嵌图，导入时落盘） */
    public List<byte[]> images = new ArrayList<>();

    public ParsedQuestion copy() {
        ParsedQuestion q = new ParsedQuestion();
        q.type = type;
        q.content = content;
        q.options = new ArrayList<>(options);
        q.answer = answer;
        q.analysis = analysis;
        q.category = category;
        q.docNo = docNo;
        q.sourceRow = sourceRow;
        q.typeGuessed = typeGuessed;
        q.images = new ArrayList<>(images);
        return q;
    }
}
