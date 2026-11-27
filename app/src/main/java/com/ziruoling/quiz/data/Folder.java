package com.ziruoling.quiz.data;

/** 题库文件夹（一层，不支持嵌套） */
public class Folder {
    public long id;
    public String name = "";
    public int pinned;
    public int sortPos;
    /** 0 名称 / 1 做题日期 / 2 加入日期 / 3 手动 */
    public int sortField;
    /** 0 正序 / 1 倒序 */
    public int sortDir;
    public long createdAt;
    /** 界面展开状态（仅内存） */
    public boolean expanded = true;
}
