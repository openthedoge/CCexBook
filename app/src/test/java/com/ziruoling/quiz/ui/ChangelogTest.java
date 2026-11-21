package com.ziruoling.quiz.ui;

import android.content.Context;

import com.ziruoling.quiz.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** 更新日志：assets 解析 + 页面可用 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ChangelogTest {

    private String readAsset() throws Exception {
        Context c = RuntimeEnvironment.getApplication();
        try (InputStream in = c.getAssets().open("changelog.txt");
             BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }

    @Test
    public void parsesAllVersionsNewestFirst() throws Exception {
        List<ChangelogActivity.Entry> entries = ChangelogActivity.parse(readAsset());
        assertTrue("应至少包含 v1.0 起的各版本段", entries.size() >= 5);
        assertTrue("最新版本应在最上面", entries.get(0).header.startsWith("v1.6"));
        assertTrue("最新版本应含追加题目", entries.get(0).lines.toString().contains("笔记"));
        assertTrue(entries.get(1).header.startsWith("v1.5"));
        assertTrue("v1.5 应记录多词条", entries.get(1).lines.toString().contains("多词条"));
        assertTrue("最早版本应在最下面",
                entries.get(entries.size() - 1).header.startsWith("fork-v1.0"));
        assertFalse("版本下应有条目", entries.get(0).lines.isEmpty());
    }

    @Test
    public void ignoresCommentAndBlankLines() {
        List<ChangelogActivity.Entry> e = ChangelogActivity.parse(
                "# 注释\n\n## v9.9  2026-01-01\n- 条目一\n\n- 条目二\n");
        assertEquals(1, e.size());
        assertEquals("v9.9  2026-01-01", e.get(0).header);
        assertEquals(2, e.get(0).lines.size());
    }

    @Test
    public void emptyInputYieldsNoEntries() {
        assertTrue(ChangelogActivity.parse("").isEmpty());
        assertTrue(ChangelogActivity.parse(null).isEmpty());
    }

    @Test
    public void activityLaunches() {
        ChangelogActivity a = Robolectric.buildActivity(ChangelogActivity.class).setup().get();
        assertNotNull(a.findViewById(R.id.body));
        assertNotNull(a.findViewById(R.id.btnBack));
    }
}
