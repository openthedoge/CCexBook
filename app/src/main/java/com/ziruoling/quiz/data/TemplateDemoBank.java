package com.ziruoling.quiz.data;

import com.ziruoling.quiz.core.ParsedQuestion;
import com.ziruoling.quiz.core.QuestionType;

import java.util.ArrayList;
import java.util.List;

/** 内置示例题库：与 Word 模板一致的 8 行生活化示例，供体验与格式参考 */
public final class TemplateDemoBank {

    private TemplateDemoBank() {}

    public static List<ParsedQuestion> build() {
        List<ParsedQuestion> list = new ArrayList<>();

        six(list, QuestionType.SINGLE, "日常饮食", "早餐里，下列哪种食物富含维生素 C？",
                new String[]{"苹果", "白米饭", "牛肉", "牛奶", "", ""},
                "A", "苹果富含维生素 C，其余含量很低。");

        six(list, QuestionType.MULTI, "日常购物", "去超市买早餐，下列哪些属于奶制品？",
                new String[]{"牛奶", "酸奶", "面包", "鸡蛋", "", ""},
                "AB", "牛奶和酸奶是奶制品，面包是谷物，鸡蛋是蛋类。");

        ParsedQuestion judge = new ParsedQuestion();
        judge.type = QuestionType.JUDGE;
        judge.category = "健康作息";
        judge.content = "成年人每天建议睡眠 7~9 小时。";
        judge.answer = "A";
        judge.analysis = "多数健康指南建议成年人每晚睡 7~9 小时。";
        list.add(judge);

        ParsedQuestion fill = new ParsedQuestion();
        fill.type = QuestionType.FILL;
        fill.category = "生活常识";
        fill.content = "中国的首都是 ____。";
        fill.answer = "北京";
        fill.analysis = "中国首都是北京。";
        list.add(fill);

        ParsedQuestion fill2 = new ParsedQuestion();
        fill2.type = QuestionType.FILL;
        fill2.category = "日常烹饪";
        fill2.content = "用电饭煲煮米饭，需要 __ 和 __。";
        fill2.answer = "大米|水||米|清水";
        fill2.analysis = "大米加水即可，水量按口感调整。";
        list.add(fill2);


        ParsedQuestion fill3 = new ParsedQuestion();
        fill3.type = QuestionType.FILL;
        fill3.category = "日常饮品";
        fill3.content = "做一杯奶茶需要 __、__ 和 __。";
        fill3.answer = "茶叶|牛奶|糖||红茶|奶|白糖";
        fill3.analysis = "茶、奶、糖三样；两种写法都算对。";
        list.add(fill3);

        ParsedQuestion shortQ = new ParsedQuestion();
        shortQ.type = QuestionType.SHORT;
        shortQ.category = "家务流程";
        shortQ.content = "简单说说一次完整的洗衣流程。";
        shortQ.answer = "分类衣物 → 放入洗衣机 → 加洗涤剂 → 选择程序 → 洗完后晾晒。";
        shortQ.analysis = "按分类、洗涤、晾晒三步描述即可。";
        list.add(shortQ);

        ParsedQuestion sort = new ParsedQuestion();
        sort.type = QuestionType.SORT;
        sort.category = "日常烹饪";
        sort.content = "把做三明治的步骤按先后顺序排列。";
        sort.options.add("放一片面包");
        sort.options.add("抹上酱料");
        sort.options.add("放生菜和火腿");
        sort.options.add("盖上另一片面包");
        sort.answer = "BACD";
        sort.analysis = "先放面包，抹酱，放配料，最后盖上。";
        list.add(sort);

        return list;
    }

    private static void six(List<ParsedQuestion> l, int type, String cat, String content,
                            String[] opts, String ans, String analysis) {
        ParsedQuestion q = new ParsedQuestion();
        q.type = type;
        q.content = content;
        q.category = cat;
        for (String o : opts) {
            if (o != null && !o.isEmpty()) q.options.add(o);
        }
        q.answer = ans;
        q.analysis = analysis;
        l.add(q);
    }
}
