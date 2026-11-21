package com.ziruoling.quiz.data;

/** 标记词条（每个题库独立一套，默认「其他」不可删） */
public class Tag {
    public long id;
    public long bankId;
    public String name = "";
    /** 默认词条（「其他」）：不可删除，可改名 */
    public boolean isDefault;
    public int sort;
}
