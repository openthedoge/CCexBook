package com.ziruoling.quiz.util;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * 运行时文案翻译：中文原文 → 当前语言。
 * 词库放在 assets/i18n/en.txt（每行「中文=English」），中文环境直接返回原文。
 * 未收录的文案返回原文（渐进式翻译，不会崩）。
 */
public final class Tr {

    private static final Map<String, String> EN = new HashMap<>();
    private static boolean loaded;

    private Tr() {}

    public static void init(Context c) {
        if (loaded) return;
        loaded = true;
        try {
            AssetManager am = c.getAssets();
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(am.open("i18n/en.txt"), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int i = line.indexOf('=');
                if (i <= 0) continue;
                EN.put(line.substring(0, i), line.substring(i + 1));
            }
            r.close();
        } catch (Exception ignored) {
        }
    }

    /** 中文原文 → 当前语言；英文环境下查不到就返回原文 */
    public static String s(String zh) {
        if (zh == null || zh.isEmpty()) return zh;
        if (!isEnglish()) return zh;
        String en = EN.get(zh);
        return en == null ? zh : en;
    }

    private static boolean isEnglish() {
        try {
            return "en".equals(com.ziruoling.quiz.data.Prefs.languageTag(null));
        } catch (Exception e) {
            return false;
        }
    }
}
