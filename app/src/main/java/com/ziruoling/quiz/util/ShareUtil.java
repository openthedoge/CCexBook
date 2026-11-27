package com.ziruoling.quiz.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.ziruoling.quiz.core.AnswerUtil;
import com.ziruoling.quiz.core.QuestionType;
import com.ziruoling.quiz.data.Db;
import com.ziruoling.quiz.data.ExamRecord;
import com.ziruoling.quiz.data.Question;

import java.util.List;

/** 题目/成绩分享工具：通过系统分享（微信等）发送纯文本，无需联网与权限 */
public final class ShareUtil {

    private ShareUtil() {}

    /** 调用系统分享面板 */
    public static void share(Context c, String text) {
        Intent it = new Intent(Intent.ACTION_SEND);
        it.setType("text/plain");
        it.putExtra(Intent.EXTRA_SUBJECT, "紫若灵刷题练习");
        it.putExtra(Intent.EXTRA_TEXT, text);
        try {
            c.startActivity(Intent.createChooser(it, "分享到"));
        } catch (Exception e) {
            Ui.toast(c, "没有可用的分享应用");
        }
    }

    /**
     * 点击「分享」时的四选一面板：
     * 分享题目 / 分享题目+答案与解析 / 复制题目 / 复制题目+答案与解析。
     * 分享走系统面板，复制写入剪贴板。
     */
    public static void showChooser(Context c, Question q, String bankName, int index, int total,
                                   List<String> opts, String dispAnswer,
                                   String userAnswer, Boolean correct) {
        String[] items = {
                "分享题目（不含答案）",
                "分享题目 + 答案与解析",
                "复制题目（不含答案）",
                "复制题目 + 答案与解析"
        };
        new com.ziruoling.quiz.util.Ui.AlertBuilder(c)
                .setTitle("分享 / 复制")
                .setItems(items, (d, which) -> {
                    boolean withAnswer = which == 1 || which == 3;
                    boolean copy = which >= 2;
                    String text = buildQuestionText(q, bankName, index, total, opts, dispAnswer,
                            userAnswer, correct, withAnswer);
                    if (copy) copyToClipboard(c, text);
                    else share(c, text);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 复制纯文本到剪贴板（Android 13+ 系统自带复制提示，就不再重复 toast） */
    public static void copyToClipboard(Context c, String text) {
        try {
            ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) {
                Ui.toast(c, "复制失败");
                return;
            }
            cm.setPrimaryClip(ClipData.newPlainText("题目", text));
            if (Build.VERSION.SDK_INT < 33) Ui.toast(c, "已复制到剪贴板");
        } catch (Exception e) {
            Ui.toast(c, "复制失败：" + e.getMessage());
        }
    }

    /**
     * 构造题目分享文本。
     *
     * @param q          题目
     * @param bankName   题库名（可为 null）
     * @param index      题号（从 1 起，0 表示不显示）
     * @param total      总题数
     * @param opts       当前展示的选项顺序（null 用 q.options 原始顺序）
     * @param dispAnswer 按 opts 顺序对应的答案字母（null 用 q.answer）
     * @param userAnswer 我的答案（可为 null）
     * @param correct    是否正确（null 表示未作答/背题）
     */
    public static String buildQuestionText(Question q, String bankName, int index, int total,
                                           List<String> opts, String dispAnswer,
                                           String userAnswer, Boolean correct) {
        return buildQuestionText(q, bankName, index, total, opts, dispAnswer,
                userAnswer, correct, true);
    }

    /**
     * 构造题目分享文本。
     *
     * @param withAnswer false 时只输出题干 + 选项（不含正确答案、我的答案、解析）
     */
    public static String buildQuestionText(Question q, String bankName, int index, int total,
                                           List<String> opts, String dispAnswer,
                                           String userAnswer, Boolean correct,
                                           boolean withAnswer) {
        StringBuilder sb = new StringBuilder();
        String head = "【" + QuestionType.SHORT_NAMES[q.type] + "】";
        if (bankName != null && !bankName.isEmpty()) head += " " + bankName;
        if (index > 0 && total > 0) head += " 第" + index + "/" + total + "题";
        sb.append(head).append('\n');
        sb.append(TextUtil.text(q.content)).append('\n');

        List<String> o = opts != null ? opts : q.options;
        String a = dispAnswer != null ? dispAnswer : q.answer;
        if (o != null && !o.isEmpty()) {
            sb.append('\n');
            for (int i = 0; i < o.size(); i++) {
                sb.append((char) ('A' + i)).append(". ").append(TextUtil.text(o.get(i))).append('\n');
            }
        }
        if (!withAnswer) return sb.toString().trim() + "\n";

        sb.append('\n');
        sb.append("正确答案：").append(answerDisplay(q, o, a)).append('\n');
        if (correct != null) {
            sb.append("我的答案：").append(userAnswer == null || userAnswer.isEmpty()
                    ? "（未提交）" : userAnswer)
                    .append(correct ? "（回答正确 ✓）" : "（回答错误 ✗）").append('\n');
        }
        if (q.analysis != null && !q.analysis.isEmpty()) {
            sb.append('\n').append("解析：").append(TextUtil.text(q.analysis));
        }
        return sb.toString();
    }

    /** 答案展示：判断→对/错；填空/简答/看图→原文；排序→字母顺序链；选择题→字母+选项文本 */
    private static String answerDisplay(Question q, List<String> opts, String answer) {
        if (answer == null || answer.isEmpty()) return "（未提供）";
        if (q.type == QuestionType.JUDGE) {
            // 能识别成正/误就显示「正确/错误」，识别不了（脏数据）就原样显示，
            // 不能一律回落到 judgeText（空串会被误显示成「错误」）。
            return AnswerUtil.answerDisplay(QuestionType.JUDGE, answer);
        }
        if (q.type == QuestionType.FILL || q.type == QuestionType.SHORT || q.type == QuestionType.OTHER) {
            return q.answer == null || q.answer.isEmpty() ? "（未提供）" : q.answer;
        }
        if (q.type == QuestionType.SORT) {
            String seq = AnswerUtil.extractSequence(answer);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < seq.length(); i++) {
                if (i > 0) sb.append(" → ");
                sb.append(seq.charAt(i));
            }
            return sb.length() == 0 ? answer : sb.toString();
        }
        StringBuilder sb = new StringBuilder();
        for (char c : AnswerUtil.extractLetters(answer).toCharArray()) {
            int idx = c - 'A';
            if (idx >= 0 && idx < opts.size()) {
                if (sb.length() > 0) sb.append("  ");
                sb.append(c).append(". ").append(opts.get(idx));
            } else if (idx >= 0) {
                if (sb.length() > 0) sb.append("  ");
                sb.append(c);
            }
        }
        return sb.length() == 0 ? answer : sb.toString();
    }

    /** 成绩单分享文本 */
    public static String buildExamText(ExamRecord r) {
        int scoreInt = (int) Math.round(r.score);
        int fullInt = (int) Math.round(r.full);
        int rate = r.total <= 0 ? 0 : Math.round(r.correct * 100f / r.total);
        String verdict = rate >= 90 ? "优秀" : rate >= 75 ? "良好" : rate >= 60 ? "及格" : "继续加油";

        StringBuilder sb = new StringBuilder();
        sb.append("【紫若灵刷题练习】考试成绩单\n");
        sb.append("考试：").append(r.name == null || r.name.isEmpty() ? "模拟考试" : r.name).append('\n');
        sb.append("题库：").append(r.bankName == null || r.bankName.isEmpty() ? "—" : r.bankName).append('\n');
        sb.append("得分：").append(scoreInt).append(" / ").append(fullInt).append(" 分（").append(verdict).append("）\n");
        sb.append("答对：").append(r.correct).append(" / ").append(r.total).append(" 题，正确率 ").append(rate).append("%\n");
        sb.append("用时：").append(Ui.fmtClock(r.durationSec)).append('\n');

        boolean hasDetail = false;
        for (int t = 0; t < QuestionType.COUNT; t++) {
            if (r.typeDetail[t][1] > 0) {
                if (!hasDetail) {
                    sb.append('\n').append("各题型：\n");
                    hasDetail = true;
                }
                int ok = r.typeDetail[t][0];
                int total = r.typeDetail[t][1];
                sb.append("· ").append(QuestionType.NAMES[t]).append("：").append(ok)
                        .append(" / ").append(total)
                        .append("（").append(total <= 0 ? 0 : Math.round(ok * 100f / total)).append("%）\n");
            }
        }
        if (!r.wrongIds.isEmpty()) {
            sb.append('\n').append("错题 ").append(r.wrongIds.size()).append(" 道：\n");
            int max = Math.min(10, r.wrongIds.size());
            for (int i = 0; i < max; i++) {
                Question q = Db.getQuestion(r.wrongIds.get(i));
                if (q == null) continue;
                sb.append(i + 1).append(". ").append(q.content).append('\n');
            }
            if (r.wrongIds.size() > 10) sb.append("…等 ").append(r.wrongIds.size()).append(" 道\n");
        }
        return sb.toString();
    }
}