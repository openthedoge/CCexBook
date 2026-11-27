package com.ziruoling.quiz.data;

import java.util.ArrayList;
import java.util.List;

/** 题目模型 */
public class Question {
    public long id;
    public long bankId;
    public int qindex;
    public int type;
    public String content = "";
    public List<String> options = new ArrayList<>();
    public String answer = "";
    public String analysis = "";
    /** 本题笔记（独立编辑，随题保存） */
    public String note = "";
    public String category = "";
    public boolean flagged;
    /** 原文档题号（按篇/节重新计数），0 = 无 */
    public int docNo;
    /** 题干配图（相对路径，如 imgs/q1_0.png；docx 表格内嵌图，看图题/配图题通用） */
    public List<String> imgPaths = new ArrayList<>();
    /** 纯图题：无文字题干，选项只有字母 */
    public boolean imageOnly;
}