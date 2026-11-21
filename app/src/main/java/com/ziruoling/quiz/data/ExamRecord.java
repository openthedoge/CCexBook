package com.ziruoling.quiz.data;

import com.ziruoling.quiz.core.QuestionType;

import java.util.ArrayList;
import java.util.List;

/** 考试记录模型 */
public class ExamRecord {
    public long id;
    public long bankId;
    public String bankName = "";
    public String name = "";
    public int total;
    public int done;
    public int correct;
    public double score;
    public double full;
    public int durationSec;
    public long finishedAt;
    public List<Long> wrongIds = new ArrayList<>();
    /** [题型][0=答对, 1=总数] */
    public int[][] typeDetail = new int[QuestionType.COUNT][2];
}