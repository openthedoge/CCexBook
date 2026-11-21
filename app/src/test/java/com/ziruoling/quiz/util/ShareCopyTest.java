package com.ziruoling.quiz.util;

import android.content.ClipboardManager;
import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/** 分享面板新增的「复制」分支 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ShareCopyTest {

    @Test
    public void copyToClipboardPutsTextIntoClipboard() {
        Context app = RuntimeEnvironment.getApplication();
        String text = "【单选】测试题库 第1/3题\n题干内容\n\nA. 甲\nB. 乙\n\n正确答案：A. 甲";
        ShareUtil.copyToClipboard(app, text);

        ClipboardManager cm = (ClipboardManager) app.getSystemService(Context.CLIPBOARD_SERVICE);
        assertNotNull(cm);
        assertNotNull("剪贴板应有内容", cm.getPrimaryClip());
        assertEquals(text, cm.getPrimaryClip().getItemAt(0).getText().toString());
    }

    @Test
    public void copyWithoutAnswerHasNoAnswerText() {
        Context app = RuntimeEnvironment.getApplication();
        String withAnswer = "题干\n\nA. 甲\n\n正确答案：A\n\n解析：因为甲";
        String withoutAnswer = "题干\n\nA. 甲\n";
        ShareUtil.copyToClipboard(app, withoutAnswer);
        ClipboardManager cm = (ClipboardManager) app.getSystemService(Context.CLIPBOARD_SERVICE);
        String got = cm.getPrimaryClip().getItemAt(0).getText().toString();
        assertEquals(withoutAnswer, got);
        assertEquals(false, got.contains("正确答案"));
        assertEquals(true, withAnswer.contains("正确答案"));
    }
}
