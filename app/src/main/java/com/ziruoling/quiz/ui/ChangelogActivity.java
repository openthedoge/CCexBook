package com.ziruoling.quiz.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.widget.TextView;

import com.ziruoling.quiz.R;
import com.ziruoling.quiz.util.Ui;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 更新日志：内容放在 {@code assets/changelog.txt}，每次发版只改文本文件，不用动布局与代码。
 * <p>
 * 性能考虑：读取与解析放在后台线程（一次性顺序扫描，约几十行文本）；
 * 渲染合并进**一个** TextView 的 Spannable，避免为每行创建 View。
 * 格式：{@code ## 版本  日期} 开头，条目以 {@code -} 开头，{@code #} 开头为注释。
 */
public class ChangelogActivity extends BaseActivity {

    private static final String ASSET = "changelog.txt";

    /** 一条版本记录 */
    static final class Entry {
        String header = "";
        final List<String> lines = new ArrayList<>();
    }

    public static void start(Context c) {
        c.startActivity(new Intent(c, ChangelogActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_changelog);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        final TextView body = findViewById(R.id.body);
        body.setText("正在加载…");

        new Thread(() -> {
            final List<Entry> entries = parse(readAsset());
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (entries.isEmpty()) {
                    body.setText("暂无更新记录");
                } else {
                    body.setText(render(entries));
                }
            });
        }, "changelog-load").start();
    }

    /** 读取 assets 文本（失败返回空串） */
    private String readAsset() {
        try (InputStream in = getAssets().open(ASSET);
             BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            StringBuilder sb = new StringBuilder(4096);
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** 纯文本 → 版本条目列表（可在任意线程调用） */
    static List<Entry> parse(String raw) {
        List<Entry> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        Entry cur = null;
        for (String line : raw.split("\n")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (t.startsWith("##")) {
                cur = new Entry();
                cur.header = t.substring(2).trim();
                out.add(cur);
            } else if (t.startsWith("#")) {
                continue;   // 注释行
            } else if (t.startsWith("-")) {
                if (cur == null) {
                    cur = new Entry();
                    out.add(cur);
                }
                cur.lines.add(t.substring(1).trim());
            } else if (cur != null) {
                cur.lines.add(t);
            }
        }
        return out;
    }

    /** 条目 → 单个 Spannable（版本行加粗放大、用主色；正文用次要字色） */
    private CharSequence render(List<Entry> entries) {
        int accent = Ui.attrColor(this, R.attr.qzPrimary);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (Entry e : entries) {
            if (!e.header.isEmpty()) {
                int start = sb.length();
                sb.append(e.header).append('\n');
                int end = sb.length();
                sb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new ForegroundColorSpan(accent), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new RelativeSizeSpan(1.12f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            for (String item : e.lines) {
                sb.append("· ").append(item).append('\n');
            }
            sb.append('\n');
        }
        return sb;
    }
}
