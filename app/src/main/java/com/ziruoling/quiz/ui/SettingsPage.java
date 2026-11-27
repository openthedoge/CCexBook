package com.ziruoling.quiz.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;

import com.ziruoling.quiz.R;
import com.ziruoling.quiz.data.Backup;
import com.ziruoling.quiz.data.Db;
import com.ziruoling.quiz.data.Prefs;
import com.ziruoling.quiz.ui.dialog.ConfirmDialog;
import com.ziruoling.quiz.util.SoundFx;
import com.ziruoling.quiz.util.Ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** 设置：首屏分组（外观 / 刷题行为 / 数据管理 / 重置选项 / 关于），点进去看具体项 */
public class SettingsPage extends android.content.ContextWrapper {

    /** 备份/恢复的请求码（宿主 Activity 转发用） */
    public static final int REQ_BACKUP_OUT = 201;
    public static final int REQ_BACKUP_IN = 202;

    private final android.app.Activity a;
    private final View root;

    public SettingsPage(android.app.Activity a, View root) {
        super(a);
        this.a = a;
        this.root = root;
        bind();
    }

    /** 内嵌时视图挂在宿主布局上，统一从这里找 */
    @SuppressWarnings("unchecked")
    private <T extends View> T findViewById(int id) {
        return (T) root.findViewById(id);
    }


    public static final int SEC_MENU = 0;
    public static final int SEC_APPEARANCE = 1;
    public static final int SEC_BEHAVIOR = 2;
    public static final int SEC_DATA = 3;
    public static final int SEC_RESET = 4;
    public static final int SEC_ABOUT = 5;
    public static final String EXTRA_SECTION = "section";

    private static final int[] SECTION_IDS = {
            R.id.secMenu, R.id.secAppearance, R.id.secBehavior,
            R.id.secData, R.id.secReset};

    private static final String[] SECTION_TITLES = {
            "设置", "外观", "刷题行为", "数据管理", "重置选项"};

    /** 备份/恢复进行中，避免重复点击 */
    private boolean busy;

    /** 音效替换的请求码 */
    public static final int REQ_SOUND_OK = 301;
    public static final int REQ_SOUND_WRONG = 302;

    private int section = SEC_MENU;

    private void bind() {
        findViewById(R.id.btnBack).setOnClickListener(v -> showSection(SEC_MENU));
        findViewById(R.id.btnSettingsBack).setOnClickListener(v -> showSection(SEC_MENU));
        findViewById(R.id.rowAppearance).setOnClickListener(v -> showSection(SEC_APPEARANCE));
        findViewById(R.id.rowBehavior).setOnClickListener(v -> showSection(SEC_BEHAVIOR));
        findViewById(R.id.rowData).setOnClickListener(v -> showSection(SEC_DATA));
        findViewById(R.id.rowReset).setOnClickListener(v -> showSection(SEC_RESET));

        // 动画速度：一行 5 段（关闭/慢/中/快/系统）
        renderAnimSeg();

        // 关于：版本号运行时读取，避免与 build.gradle 脱节
        TextView about = findViewById(R.id.aboutVersion);
        about.setText(getString(R.string.app_name) + " v" + versionName());
        findViewById(R.id.aboutChangelogEntry).setOnClickListener(v -> ChangelogActivity.start(this));
        // 彩蛋：点「基于原版…」弹一句玩笑提示，确认后退出应用（不做任何数据操作）
        android.view.View.OnClickListener egg = v -> {
            final android.app.AlertDialog dlg = new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                    .setTitle("提示")
                    .setMessage("您的题库已经全部清空，感谢您的使用，祝您生活愉快，再见。")
                    .setCancelable(false)
                    .setPositiveButton("确定", (d, w) -> a.finishAffinity())
                    .create();
            dlg.setOnShowListener(x -> {
                android.widget.Button ok = dlg.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
                if (ok != null) {
                    ok.setOnTouchListener(new android.view.View.OnTouchListener() {
                        long downAt = 0;

                        @Override
                        public boolean onTouch(android.view.View v, android.view.MotionEvent e) {
                            if (e.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                                downAt = System.currentTimeMillis();
                                return true;
                            }
                            if (e.getAction() == android.view.MotionEvent.ACTION_UP) {
                                if (System.currentTimeMillis() - downAt >= 3000) {
                                    com.ziruoling.quiz.data.Prefs.snakeGate(5);
                                    Ui.pillToast(a, "盖亚！");
                                    dlg.dismiss();
                                } else {
                                    a.finishAffinity();
                                }
                                return true;
                            }
                            return false;
                        }
                    });
                }
            });
            dlg.show();
            com.ziruoling.quiz.util.Ui.styleContent(dlg, a, false);
        };
        findViewById(R.id.aboutThanks).setOnClickListener(egg);
        findViewById(R.id.aboutGithub).setOnClickListener(v -> {
            try {
                startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://github.com/openthedoge/CCexBook")));
            } catch (Exception ignored) {
            }
        });

        // 语言：多语言暂停中，选项暂不展示（恢复时把下面这段和布局里的语言卡片还原即可）
        android.view.View langGroup = findViewById(R.id.langGroup);
        if (langGroup != null && langGroup.getParent() instanceof android.view.View) {
            ((android.view.View) langGroup.getParent()).setVisibility(android.view.View.GONE);
        }
        // 主题模式：分段控件
        final String[] themeLabels = {"跟随", "浅色", "深色", "纯黑"};
        buildSeg(findViewById(R.id.themeSeg), themeLabels, Prefs.theme(), which -> {
            Prefs.theme(which);
            a.getIntent().putExtra(MainActivity.EXTRA_TAB, 2);
            a.recreate();
        });

        // 主题配色：色点
        buildDots(findViewById(R.id.colorDots), Prefs.themeColor(), which -> {
            Prefs.themeColor(which);
            a.getIntent().putExtra(MainActivity.EXTRA_TAB, 2);
            a.recreate();
        });

        // 字体：分段控件（顺序：小/标准/大/特大 → 1/0/2/3）
        final String[] fontLabels = {"小", "标准", "大", "特大"};
        buildSeg(findViewById(R.id.fontSeg), fontLabels, fontSegIndex(Prefs.fontIndex()), which -> {
            Prefs.fontIndex(which == 0 ? 1 : (which == 1 ? 0 : (which == 2 ? 2 : 3)));
            a.getIntent().putExtra(MainActivity.EXTRA_TAB, 2);
            a.recreate();
        });

        // 行为项
        bindCapsule(R.id.swAutoNext, Prefs.autoNext(), v -> Prefs.autoNext(v));
        bindCapsule(R.id.swHaptic, Prefs.haptic(), v -> Prefs.haptic(v));
        bindCapsule(R.id.swSound, Prefs.sound(), v -> Prefs.sound(v));
        bindCapsule(R.id.swShuffle, Prefs.shuffleOpts(), v -> Prefs.shuffleOpts(v));
        bindCapsule(R.id.swAutoBall, Prefs.autoReciteBall(), v -> Prefs.autoReciteBall(v));
        bindCapsule(R.id.swWrongRemove, Prefs.wrongAutoRemove(), v -> Prefs.wrongAutoRemove(v));
        bindCapsule(R.id.swAnalysis, Prefs.showAnalysis(), v -> Prefs.showAnalysis(v));
        bindCapsule(R.id.swLifecycle, com.ziruoling.quiz.data.Prefs.lifecycleLog(),
                v -> com.ziruoling.quiz.data.Prefs.lifecycleLog(v));
        bindCapsule(R.id.swFocus, Prefs.focusMode(), v -> {
            Prefs.focusMode(v);
            if (a instanceof MainActivity) ((MainActivity) a).applyFocusMode();
        });

        findViewById(R.id.btnReplaceSound).setOnClickListener(v -> showSoundMenu());

        // 试听音效：先播答对音，稍候播答错音（不受开关限制，便于排查）
        findViewById(R.id.btnSoundTest).setOnClickListener(v -> {
            SoundFx.preview(true);
            new android.os.Handler().postDelayed(() -> SoundFx.preview(false), 700);
            Ui.toast(this, "试听：第一声=答对，第二声=答错");
        });

        // 数据
        findViewById(R.id.btnClearRecords).setOnClickListener(v -> ConfirmDialog.show(this,
                "清空练习记录", "将清空全部答题记录与顺序进度；首页与统计页的累计统计不受影响。确定？", () -> {
                    Db.clearRecords();
                    Ui.toast(this, "已清空");
                }));
        findViewById(R.id.btnClearWrongs).setOnClickListener(v -> ConfirmDialog.show(this,
                "清空错题本", "将清空全部错题记录，确定？", () -> {
                    Db.clearWrongs();
                    Ui.toast(this, "已清空");
                }));
        findViewById(R.id.btnClearFavs).setOnClickListener(v -> ConfirmDialog.show(this,
                "清空收藏", "将清空全部收藏，确定？", () -> {
                    Db.clearFavs();
                    Ui.toast(this, "已清空");
                }));
        // 清空总统计：二次确认（累计数据一旦归零无法恢复）
        findViewById(R.id.btnClearStats).setOnClickListener(v -> ConfirmDialog.show(this,
                "清空统计",
                "将把首页与统计页的累计统计（总作答、正确率、今日、连续天数、题型正确率）归零。\n"
                        + "做题记录、错题本、题库都不会被删除。确定继续？",
                () -> ConfirmDialog.showDanger(this, "再次确认",
                        "累计统计将永久归零，且无法从记录重新计算回来。真的要清空吗？",
                        () -> {
                            Db.clearStats();
                            Ui.toast(this, "统计已清空");
                        })));

        // 备份与恢复
        findViewById(R.id.btnBackupData).setOnClickListener(v -> startBackup());
        findViewById(R.id.btnRestoreData).setOnClickListener(v -> startRestorePick());
        findViewById(R.id.btnRestoreOriginal).setOnClickListener(v -> restoreOriginalSnapshot());
        findViewById(R.id.btnDeleteOriginal).setOnClickListener(v -> deleteOriginalSnapshot());

        showSection(a.getIntent().getIntExtra(EXTRA_SECTION, SEC_MENU));
    }

    /** 切离设置 tab 时回到首屏（再次进入就是总设置页） */
    public void resetToMenu() {
        showSection(SEC_MENU);
    }

    // ==================== 分组切换 ====================

    private void showSection(int s) {
        if (s < 0 || s >= SECTION_IDS.length) s = SEC_MENU;
        section = s;
        a.getIntent().putExtra(EXTRA_SECTION, s);
        for (int i = 0; i < SECTION_IDS.length; i++) {
            View v = findViewById(SECTION_IDS[i]);
            if (v != null) v.setVisibility(i == s ? View.VISIBLE : View.GONE);
        }
        android.view.View about = findViewById(R.id.secAbout);
        if (about != null) about.setVisibility(s == SEC_MENU ? View.VISIBLE : View.GONE);
        View backBar = findViewById(R.id.settingsBackBar);
        if (backBar != null) {
            backBar.setVisibility(s == SEC_MENU ? View.GONE : View.VISIBLE);
            TextView st = findViewById(R.id.settingsSectionTitle);
            if (st != null) st.setText(SECTION_TITLES[s]);
        }
        TextView title = findViewById(R.id.titleTv);
        if (title != null) title.setText(SECTION_TITLES[s]);
        android.widget.ScrollView sv = findViewById(R.id.settingsScroll);
        if (sv != null) sv.scrollTo(0, 0);
        updateMenuSummary();
    }

    private boolean isSystemDark() {
        int mode = a.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /** 动画速度分段：点选后重建自身以刷新选中态 */
    private void renderAnimSeg() {
        final String[] labels = {"关闭", "慢", "中", "快", "系统"};
        final int[] values = {4, 3, 2, 1, 0};
        int sel = 2;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == Prefs.animSpeed()) {
                sel = i;
                break;
            }
        }
        buildSeg(findViewById(R.id.animSeg), labels, sel, which -> {
            Prefs.animSpeed(values[which]);
            renderAnimSeg();
        });
    }

    private int fontSegIndex(int fontIndex) {
        if (fontIndex == 1) return 0;
        if (fontIndex == 2) return 2;
        if (fontIndex == 3) return 3;
        return 1;
    }

    /** 分段控件：一行胶囊，选中填主题色 */
    private void buildSeg(android.widget.LinearLayout box, final String[] labels,
                          int selected, java.util.function.IntConsumer onPick) {
        if (box == null) return;
        box.removeAllViews();
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            boolean on = i == selected;
            android.widget.TextView t = new android.widget.TextView(this);
            t.setText(labels[i]);
            t.setGravity(android.view.Gravity.CENTER);
            t.setTextSize(14);
            t.setTypeface(android.graphics.Typeface.DEFAULT, on
                    ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            t.setTextColor(Ui.attrColor(this, on ? R.attr.qzOnPrimary : R.attr.qzTextSub));
            t.setBackground(Ui.bg(Ui.attrColor(this,
                    on ? R.attr.qzPrimary : R.attr.qzChipBg), Ui.dp(this, 10)));
            t.setPadding(0, Ui.dp(this, 9), 0, Ui.dp(this, 9));
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(Ui.dp(this, 2), 0, Ui.dp(this, 2), 0);
            t.setLayoutParams(lp);
            t.setOnClickListener(v -> {
                if (idx != selected) onPick.accept(idx);
            });
            box.addView(t);
        }
    }

    /** 配色色点：5 个圆点，选中的加外圈 */
    private void buildDots(android.widget.LinearLayout box, int selected,
                           java.util.function.IntConsumer onPick) {
        if (box == null) return;
        box.removeAllViews();
        int[] colors = {R.color.primaryLight, R.color.primaryRedLight, R.color.primaryGreenLight,
                R.color.primaryOrangeLight, R.color.primaryPurpleLight};
        int size = Ui.dp(this, 32);
        for (int i = 0; i < colors.length; i++) {
            final int idx = i;
            boolean on = i == selected;
            android.widget.TextView dot = new android.widget.TextView(this);
            dot.setBackground(dotDrawable(getResources().getColor(colors[i], getTheme()), on));
            dot.setText(on ? "✓" : "");
            dot.setTextSize(13);
            dot.setTextColor(0xFFFFFFFF);
            dot.setGravity(android.view.Gravity.CENTER);
            android.widget.LinearLayout.LayoutParams lp =
                    new android.widget.LinearLayout.LayoutParams(size + Ui.dp(this, 6),
                            size + Ui.dp(this, 6));
            lp.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
            dot.setLayoutParams(lp);
            dot.setAlpha(Prefs.theme() == Prefs.THEME_BLACK ? 0.45f : 1f);
            dot.setEnabled(Prefs.theme() != Prefs.THEME_BLACK);
            dot.setOnClickListener(v -> {
                if (Prefs.theme() == Prefs.THEME_BLACK) {
                    Ui.toast(this, "纯黑主题使用灰阶配色");
                    return;
                }
                if (idx != selected) onPick.accept(idx);
            });
            box.addView(dot);
        }
    }

    private android.graphics.drawable.Drawable dotDrawable(int color, boolean selected) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(color);
        if (selected) g.setStroke(Ui.dp(this, 3), Ui.attrColor(this, R.attr.qzText));
        return g;
    }

    /** 纯黑主题下配色不可选（A 屏统一灰阶） */
    private void applyColorEnabled(RadioGroup group) {
        if (group == null) return;
        boolean enabled = Prefs.theme() != Prefs.THEME_BLACK;
        for (int i = 0; i < group.getChildCount(); i++) {
            View v = group.getChildAt(i);
            v.setEnabled(enabled);
            v.setAlpha(enabled ? 1f : 0.45f);
        }
    }

    /** 首屏每个分组下方的一行摘要 */
    private void updateMenuSummary() {
        String[] themes = {"跟随系统", "浅色", "深色", "纯黑"};
        String[] colors = {"科技蓝", "中国红", "翡翠绿", "活力橙", "浪漫紫"};
        String[] fonts = {"标准", "小", "大", "特大"};
        int t = Prefs.themeMode();
        int c = Prefs.themeColor();
        int f = Prefs.fontIndex();
        setSub(R.id.subAppearance, themes[Math.max(0, Math.min(3, t))] + " · "
                + colors[Math.max(0, Math.min(4, c))] + " · " + fonts[Math.max(0, Math.min(3, f))]);
        setSub(R.id.subBehavior, "自动下一题 · 音效 · 乱序选项等");
        setSub(R.id.subData, "备份全部数据 / 从备份恢复");
        setSub(R.id.subReset, "清空记录 / 错题本 / 收藏 / 统计");
    }

    private void setSub(int id, String text) {
        TextView tv = findViewById(id);
        if (tv != null) tv.setText(text);
    }

    /** 宿主返回键：消费返回 true（回到设置首屏），否则 false */
    public boolean onBack() {
        if (section != SEC_MENU) {
            showSection(SEC_MENU);
            return true;
        }
        return false;
    }

    // ==================== 备份 ====================

    private void startBackup() {
        if (busy) return;
        Intent it = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType(Backup.MIME);
        it.putExtra(Intent.EXTRA_TITLE, Backup.suggestFileName());
        a.startActivityForResult(it, REQ_BACKUP_OUT);
    }

    private void doBackup(final Uri uri) {
        final OutputStream out;
        try {
            out = a.getContentResolver().openOutputStream(uri);
            if (out == null) {
                Ui.toast(this, "无法写入所选位置");
                return;
            }
        } catch (Exception e) {
            Ui.toast(this, "无法写入所选位置：" + e.getMessage());
            return;
        }
        busy = true;
        Ui.toast(this, "正在备份…");
        new Thread(() -> {
            String err = null;
            try {
                Backup.export(this, out);
            } catch (Exception e) {
                err = e.getMessage();
            } finally {
                try {
                    out.close();
                } catch (Exception ignored) {
                }
            }
            final String ferr = err;
            a.runOnUiThread(() -> {
                busy = false;
                Ui.toast(a, ferr == null ? "备份完成" : "备份失败：" + ferr);
            });
        }).start();
    }

    // ==================== 恢复 ====================

    private void startRestorePick() {
        if (busy) return;
        Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType("*/*");
        a.startActivityForResult(it, REQ_BACKUP_IN);
    }

    private void doRestorePick(final Uri uri) {
        busy = true;
        Ui.toast(this, "正在读取备份…");
        new Thread(() -> {
            String err = null;
            File tmp = new File(getCacheDir(), "picked_backup.zip");
            Backup.Info info = null;
            try (InputStream in = a.getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(tmp)) {
                if (in == null) throw new Exception("无法读取所选文件");
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                info = Backup.inspect(tmp);
            } catch (Exception e) {
                err = e.getMessage();
            }
            final Backup.Info fInfo = info;
            final String ferr = err;
            a.runOnUiThread(() -> {
                busy = false;
                if (ferr != null) {
                    Ui.toast(a, "无法识别备份：" + ferr);
                    return;
                }
                chooseRestoreMode(tmp, fInfo);
            });
        }).start();
    }

    private void chooseRestoreMode(final File zip, final Backup.Info info) {
        new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("从备份恢复")
                .setMessage(info.describe()
                        + "\n\n请选择恢复方式：\n"
                        + "· 追加导入：把备份里的题库作为独立新题库加入，现有数据完全不动\n"
                        + "· 覆盖替换：用备份整体替换当前全部数据")
                .setPositiveButton("追加导入", (d, w) -> chooseInitOnly(zip, info))
                .setNegativeButton("覆盖替换", (d, w) -> chooseSnapshot(zip))
                .setNeutralButton("取消", null)
                .show();
    }

    private void chooseInitOnly(final File zip, final Backup.Info info) {
        new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("追加导入")
                .setMessage("备份里的 " + info.banks + " 个题库会作为独立题库加入，"
                        + "重名自动改名为「原名 (2)」。\n现有题库不受任何影响。\n\n"
                        + "是否「初始化导入的题库」？\n"
                        + "· 初始化：只导入题目（含图片），丢弃备份里的作答记录/错题/收藏/考试/进度\n"
                        + "· 不初始化：连作答记录一起导入，并重新挂接到新题目上")
                .setPositiveButton("初始化（只要题目）", (d, w) -> runAppend(zip, true))
                .setNegativeButton("保留做题记录", (d, w) -> runAppend(zip, false))
                .setNeutralButton("取消", null)
                .show();
    }

    private void runAppend(final File zip, final boolean questionsOnly) {
        busy = true;
        Ui.toast(this, "正在导入…");
        new Thread(() -> {
            Backup.Result r = null;
            String err = null;
            try {
                r = Backup.append(this, zip, questionsOnly);
            } catch (Exception e) {
                err = e.getMessage();
            }
            final Backup.Result fr = r;
            final String ferr = err;
            a.runOnUiThread(() -> {
                busy = false;
                if (ferr != null) {
                    Ui.toast(a, "导入失败：" + ferr);
                    return;
                }
                Ui.toast(a, "已导入 " + fr.banks + " 个题库、"
                        + fr.questions + " 道题" + (questionsOnly ? "" : "，" + fr.records + " 条记录"));
            });
        }).start();
    }

    private void chooseSnapshot(final File zip) {
        new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("覆盖替换")
                .setMessage("将用备份整体替换当前全部数据（题库、题目、作答记录、错题、收藏、"
                        + "考试、进度、题目图片）。\n设置项也会按备份恢复（旧备份不含设置时保持当前设置）。\n\n"
                        + "是否先把当前数据存成「原数据」快照？\n"
                        + "存了之后可在本页「恢复原数据」找回；建议保留，"
                        + "确认本次导入无误后再删除，以免原数据丢失。")
                .setPositiveButton("先备份再覆盖", (d, w) -> runReplace(zip, true))
                .setNegativeButton("直接覆盖", (d, w) -> runReplace(zip, false))
                .setNeutralButton("取消", null)
                .show();
    }

    private void runReplace(final File zip, final boolean snapshot) {
        busy = true;
        Ui.toast(this, "正在恢复…");
        new Thread(() -> {
            String err = null;
            try {
                Backup.replaceWith(this, zip, snapshot);
            } catch (Exception e) {
                err = e.getMessage();
            }
            final String ferr = err;
            a.runOnUiThread(() -> {
                busy = false;
                if (ferr != null) {
                    Ui.toast(a, "恢复失败：" + ferr);
                    return;
                }
                Ui.toast(a, "已恢复" + (snapshot ? "，原数据已留存" : ""));
                restartApp();
            });
        }).start();
    }

    private void restoreOriginalSnapshot() {
        if (busy) return;
        final File snap = Backup.snapshotFile(this);
        if (!snap.isFile()) {
            Ui.toast(this, "没有可恢复的原数据");
            return;
        }
        ConfirmDialog.show(this, "恢复原数据",
                "将用上次覆盖前留存的快照整体替换当前数据，当前新产生的记录会丢失。确定？",
                () -> runReplace(snap, false));
    }

    private void deleteOriginalSnapshot() {
        if (busy) return;
        if (!Backup.hasSnapshot(this)) {
            Ui.toast(this, "没有原数据快照");
            return;
        }
        ConfirmDialog.showDanger(this, "删除原数据",
                "将删除覆盖前留存的快照，之后无法再用它恢复。确认导入结果无误后再删除？",
                () -> {
                    Backup.deleteSnapshot(this);
                    refreshSnapshotButtons();
                    Ui.toast(this, "已删除原数据快照");
                });
    }

    private void refreshSnapshotButtons() {
        boolean has = Backup.hasSnapshot(this);
        findViewById(R.id.btnRestoreOriginal).setVisibility(has ? android.view.View.VISIBLE : android.view.View.GONE);
        findViewById(R.id.btnDeleteOriginal).setVisibility(has ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private void restartApp() {
        Intent it = new Intent(this, MainActivity.class);
        it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(it);
        a.finish();
    }

    /** 宿主转发：备份 / 恢复 */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_SOUND_OK || requestCode == REQ_SOUND_WRONG) {
            handleSoundResult(requestCode, resultCode, data);
            return;
        }
        if (resultCode != android.app.Activity.RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQ_BACKUP_OUT) {
            doBackup(data.getData());
        } else if (requestCode == REQ_BACKUP_IN) {
            doRestorePick(data.getData());
        }
    }

    public void onResumePage() {
        refreshSnapshotButtons();
    }

    private interface BoolSetter {
        void set(boolean v);
    }

    /** 当前安装包的 versionName（含 fork 后缀） */
    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    /** 替换音效菜单：说明 + 两个可长按还原的选项 */
    private void showSoundMenu() {
        final android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);

        android.widget.TextView hint = new android.widget.TextView(this);
        hint.setText("小于 10 秒、3MB 以内，常规音频格式");
        hint.setTextSize(13);
        hint.setTextColor(Ui.attrColor(this, R.attr.qzTextSub));
        hint.setPadding(Ui.dp(this, 20), Ui.dp(this, 4), Ui.dp(this, 20), Ui.dp(this, 10));
        box.addView(hint);

        final String[] labels = {
                "替换答对音效" + (Prefs.customSound(true).isEmpty() ? "" : "（自定义）"),
                "替换答错音效" + (Prefs.customSound(false).isEmpty() ? "" : "（自定义）")
        };
        final android.app.AlertDialog dlg = new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("提示：长按还原默认音效")
                .setView(box)
                .setNegativeButton("取消", null)
                .create();

        for (int i = 0; i < labels.length; i++) {
            final boolean correct = i == 0;
            android.widget.TextView row = new android.widget.TextView(this);
            row.setText(labels[i]);
            row.setTextSize(16);
            row.setTextColor(Ui.attrColor(this, R.attr.qzText));
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), 0);
            row.setMinimumHeight(Ui.dp(this, 52));
            row.setBackground(Ui.press(a, Ui.ripple(a, R.attr.qzSurface, 0)));
            row.setOnClickListener(v -> {
                dlg.dismiss();
                pickSound(correct);
            });
            row.setOnLongClickListener(v -> {
                if (Prefs.customSound(correct).isEmpty()) {
                    Ui.pillToast(a, "当前已是默认音效");
                } else {
                    Prefs.customSound(correct, "");
                    Ui.pillToast(a, "已还原默认音效");
                }
                dlg.dismiss();
                return true;
            });
            box.addView(row);
            if (i == 0) {
                android.view.View line = new android.view.View(this);
                line.setBackgroundColor(Ui.attrColor(this, R.attr.qzDivider));
                android.widget.LinearLayout.LayoutParams llp =
                        new android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 1));
                llp.leftMargin = Ui.dp(this, 20);
                llp.rightMargin = Ui.dp(this, 20);
                box.addView(line, llp);
            }
        }

        dlg.show();
        Ui.styleContent(dlg, this, false);
    }

    private void pickSound(boolean correct) {
        try {
            android.content.Intent it = new android.content.Intent(
                    android.content.Intent.ACTION_OPEN_DOCUMENT);
            it.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            it.setType("audio/*");
            a.startActivityForResult(it, correct ? REQ_SOUND_OK : REQ_SOUND_WRONG);
        } catch (Exception e) {
            Ui.pillToast(a, "无法打开文件选择器");
        }
    }

    /** 校验并保存自定义音效：mp3/aac、≤5 秒、码率按 320k 上限 */
    private void handleSoundResult(int requestCode, int resultCode, android.content.Intent data) {
        boolean correct = requestCode == REQ_SOUND_OK;
        if (resultCode != android.app.Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        android.net.Uri uri = data.getData();
        String name = queryName(uri);
        String lower = name == null ? "" : name.toLowerCase();
        String[] allow = {".mp3", ".aac", ".wav", ".m4a", ".ogg", ".flac", ".amr"};
        String hitExt = null;
        for (String e : allow) {
            if (lower.endsWith(e)) {
                hitExt = e;
                break;
            }
        }
        boolean extOk = hitExt != null;
        long size = sizeOf(uri);
        int duration = -1;
        android.media.MediaPlayer mp = null;
        try {
            mp = new android.media.MediaPlayer();
            mp.setDataSource(a, uri);
            mp.prepare();
            duration = mp.getDuration();
        } catch (Throwable ignored) {
        } finally {
            if (mp != null) {
                try {
                    mp.release();
                } catch (Throwable ignored) {
                }
            }
        }
        boolean sizeOk = size > 0 && size <= 3 * 1024 * 1024;   // ≤ 3MB
        boolean durOk = duration > 0 && duration <= 10000;      // ≤ 10 秒
        if (!extOk || !sizeOk || !durOk) {
            Ui.pillToast(a, "上传的音效文件不符合要求");
            return;
        }
        String ext = hitExt;
        java.io.File out = new java.io.File(a.getFilesDir(),
                correct ? "sound_ok_custom" + ext : "sound_wrong_custom" + ext);
        try (java.io.InputStream in = a.getContentResolver().openInputStream(uri);
             java.io.OutputStream os = new java.io.FileOutputStream(out)) {
            if (in == null) throw new java.io.IOException("open failed");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        } catch (Exception e) {
            Ui.pillToast(a, "上传的音效文件不符合要求");
            return;
        }
        Prefs.customSound(correct, out.getAbsolutePath());
        SoundFx.preview(correct);
        Ui.pillToast(a, "已替换，长按可还原默认音效");
    }

    private String queryName(android.net.Uri uri) {
        try (android.database.Cursor c = a.getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Throwable ignored) {
        }
        return uri.getLastPathSegment();
    }

    private long sizeOf(android.net.Uri uri) {
        try (android.database.Cursor c = a.getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.SIZE);
                if (idx >= 0 && !c.isNull(idx)) return c.getLong(idx);
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    /** 胶囊开关：64×34dp，开启时主题色填充 */
    private void bindCapsule(int id, boolean init, BoolSetter setter) {
        android.widget.TextView t = findViewById(id);
        if (t == null) return;
        t.setTag(init);
        renderCapsule(t, init);
        t.setOnClickListener(v -> {
            boolean now = !(Boolean) t.getTag();
            t.setTag(now);
            renderCapsule(t, now);
            setter.set(now);
        });
    }

    private void renderCapsule(android.widget.TextView t, boolean on) {
        t.setText(on ? "开" : "关");
        t.setTextColor(Ui.attrColor(this, on ? R.attr.qzOnPrimary : R.attr.qzTextSub));
        t.setBackground(Ui.bg(Ui.attrColor(this, on ? R.attr.qzPrimary : R.attr.qzChipBg),
                Ui.dp(this, 17)));
    }

}