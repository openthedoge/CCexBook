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
public class SettingsActivity extends BaseActivity {

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

    private static final int REQ_BACKUP_OUT = 201;
    private static final int REQ_BACKUP_IN = 202;

    /** 备份/恢复进行中，避免重复点击 */
    private boolean busy;

    private int section = SEC_MENU;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        findViewById(R.id.btnBack).setOnClickListener(v -> {
            if (section != SEC_MENU) showSection(SEC_MENU);
            else finish();
        });
        findViewById(R.id.rowAppearance).setOnClickListener(v -> showSection(SEC_APPEARANCE));
        findViewById(R.id.rowBehavior).setOnClickListener(v -> showSection(SEC_BEHAVIOR));
        findViewById(R.id.rowData).setOnClickListener(v -> showSection(SEC_DATA));
        findViewById(R.id.rowReset).setOnClickListener(v -> showSection(SEC_RESET));

        // 关于：版本号运行时读取，避免与 build.gradle 脱节
        TextView about = findViewById(R.id.aboutVersion);
        about.setText(getString(R.string.app_name) + " v" + versionName());
        findViewById(R.id.aboutChangelogEntry).setOnClickListener(v -> ChangelogActivity.start(this));
        // 彩蛋：点「基于原版…」弹一句玩笑提示，确认后退出应用（不做任何数据操作）
        findViewById(R.id.aboutFork).setOnClickListener(v -> new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("提示")
                .setMessage("您的题库已经全部清空，感谢您的使用，祝您生活愉快，再见。")
                .setCancelable(false)
                .setPositiveButton("确定", (d, w) -> finishAffinity())
                .show());

        // 语言：多语言暂停中，选项暂不展示（恢复时把下面这段和布局里的语言卡片还原即可）
        android.view.View langGroup = findViewById(R.id.langGroup);
        if (langGroup != null && langGroup.getParent() instanceof android.view.View) {
            ((android.view.View) langGroup.getParent()).setVisibility(android.view.View.GONE);
        }
        // 主题
        RadioGroup themeGroup = findViewById(R.id.themeGroup);
        int theme = Prefs.theme();
        ((RadioButton) findViewById(R.id.themeAuto)).setChecked(theme == Prefs.THEME_AUTO);
        ((RadioButton) findViewById(R.id.themeLight)).setChecked(theme == Prefs.THEME_LIGHT);
        ((RadioButton) findViewById(R.id.themeDark)).setChecked(theme == Prefs.THEME_DARK);
        ((RadioButton) findViewById(R.id.themeBlack)).setChecked(theme == Prefs.THEME_BLACK);
        themeGroup.setOnCheckedChangeListener((g, id) -> {
            if (id == R.id.themeAuto) Prefs.theme(Prefs.THEME_AUTO);
            else if (id == R.id.themeLight) Prefs.theme(Prefs.THEME_LIGHT);
            else if (id == R.id.themeBlack) Prefs.theme(Prefs.THEME_BLACK);
            else Prefs.theme(Prefs.THEME_DARK);
            recreate();
        });

        // 主题配色（切换后立即生效）
        RadioGroup colorGroup = findViewById(R.id.colorGroup);
        int color = Prefs.themeColor();
        ((RadioButton) findViewById(R.id.colorBlue)).setChecked(color == Prefs.COLOR_BLUE);
        ((RadioButton) findViewById(R.id.colorRed)).setChecked(color == Prefs.COLOR_RED);
        ((RadioButton) findViewById(R.id.colorGreen)).setChecked(color == Prefs.COLOR_GREEN);
        ((RadioButton) findViewById(R.id.colorOrange)).setChecked(color == Prefs.COLOR_ORANGE);
        ((RadioButton) findViewById(R.id.colorPurple)).setChecked(color == Prefs.COLOR_PURPLE);
        colorGroup.setOnCheckedChangeListener((g, id) -> {
            int v;
            if (id == R.id.colorRed) v = Prefs.COLOR_RED;
            else if (id == R.id.colorGreen) v = Prefs.COLOR_GREEN;
            else if (id == R.id.colorOrange) v = Prefs.COLOR_ORANGE;
            else if (id == R.id.colorPurple) v = Prefs.COLOR_PURPLE;
            else v = Prefs.COLOR_BLUE;
            Prefs.themeColor(v);
            recreate();
        });

        // 字体
        RadioGroup fontGroup = findViewById(R.id.fontGroup);
        int font = Prefs.fontIndex();
        ((RadioButton) findViewById(R.id.fontSmall)).setChecked(font == 1);
        ((RadioButton) findViewById(R.id.fontNormal)).setChecked(font == 0);
        ((RadioButton) findViewById(R.id.fontLarge)).setChecked(font == 2);
        ((RadioButton) findViewById(R.id.fontXl)).setChecked(font == 3);
        fontGroup.setOnCheckedChangeListener((b, id) -> {
            if (id == R.id.fontSmall) Prefs.fontIndex(1);
            else if (id == R.id.fontLarge) Prefs.fontIndex(2);
            else if (id == R.id.fontXl) Prefs.fontIndex(3);
            else Prefs.fontIndex(0);
            recreate();
        });

        // 行为项
        bind(R.id.swAutoNext, Prefs.autoNext(), v -> Prefs.autoNext(v));
        bind(R.id.swHaptic, Prefs.haptic(), v -> Prefs.haptic(v));
        bind(R.id.swSound, Prefs.sound(), v -> Prefs.sound(v));
        bind(R.id.swShuffle, Prefs.shuffleOpts(), v -> Prefs.shuffleOpts(v));
        bind(R.id.swWrongRemove, Prefs.wrongAutoRemove(), v -> Prefs.wrongAutoRemove(v));
        bind(R.id.swAnalysis, Prefs.showAnalysis(), v -> Prefs.showAnalysis(v));

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

        showSection(getIntent().getIntExtra(EXTRA_SECTION, SEC_MENU));
    }

    // ==================== 分组切换 ====================

    private void showSection(int s) {
        if (s < 0 || s >= SECTION_IDS.length) s = SEC_MENU;
        section = s;
        getIntent().putExtra(EXTRA_SECTION, s);
        for (int i = 0; i < SECTION_IDS.length; i++) {
            View v = findViewById(SECTION_IDS[i]);
            if (v != null) v.setVisibility(i == s ? View.VISIBLE : View.GONE);
        }
        android.view.View about = findViewById(R.id.secAbout);
        if (about != null) about.setVisibility(s == SEC_MENU ? View.VISIBLE : View.GONE);
        TextView title = findViewById(R.id.titleTv);
        if (title != null) title.setText(SECTION_TITLES[s]);
        android.widget.ScrollView sv = findViewById(R.id.settingsScroll);
        if (sv != null) sv.scrollTo(0, 0);
        updateMenuSummary();
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

    @Override
    public void onBackPressed() {
        if (section != SEC_MENU) {
            showSection(SEC_MENU);
            return;
        }
        super.onBackPressed();
    }

    // ==================== 备份 ====================

    private void startBackup() {
        if (busy) return;
        Intent it = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType(Backup.MIME);
        it.putExtra(Intent.EXTRA_TITLE, Backup.suggestFileName());
        startActivityForResult(it, REQ_BACKUP_OUT);
    }

    private void doBackup(final Uri uri) {
        final OutputStream out;
        try {
            out = getContentResolver().openOutputStream(uri);
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
            runOnUiThread(() -> {
                busy = false;
                Ui.toast(SettingsActivity.this, ferr == null ? "备份完成" : "备份失败：" + ferr);
            });
        }).start();
    }

    // ==================== 恢复 ====================

    private void startRestorePick() {
        if (busy) return;
        Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType("*/*");
        startActivityForResult(it, REQ_BACKUP_IN);
    }

    private void doRestorePick(final Uri uri) {
        busy = true;
        Ui.toast(this, "正在读取备份…");
        new Thread(() -> {
            String err = null;
            File tmp = new File(getCacheDir(), "picked_backup.zip");
            Backup.Info info = null;
            try (InputStream in = getContentResolver().openInputStream(uri);
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
            runOnUiThread(() -> {
                busy = false;
                if (ferr != null) {
                    Ui.toast(SettingsActivity.this, "无法识别备份：" + ferr);
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
            runOnUiThread(() -> {
                busy = false;
                if (ferr != null) {
                    Ui.toast(SettingsActivity.this, "导入失败：" + ferr);
                    return;
                }
                Ui.toast(SettingsActivity.this, "已导入 " + fr.banks + " 个题库、"
                        + fr.questions + " 道题" + (questionsOnly ? "" : "，" + fr.records + " 条记录"));
            });
        }).start();
    }

    private void chooseSnapshot(final File zip) {
        new com.ziruoling.quiz.util.Ui.AlertBuilder(this)
                .setTitle("覆盖替换")
                .setMessage("将用备份整体替换当前全部数据（题库、题目、作答记录、错题、收藏、"
                        + "考试、进度、题目图片）。\n设置项不受影响。\n\n"
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
            runOnUiThread(() -> {
                busy = false;
                if (ferr != null) {
                    Ui.toast(SettingsActivity.this, "恢复失败：" + ferr);
                    return;
                }
                Ui.toast(SettingsActivity.this, "已恢复" + (snapshot ? "，原数据已留存" : ""));
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
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQ_BACKUP_OUT) {
            doBackup(data.getData());
        } else if (requestCode == REQ_BACKUP_IN) {
            doRestorePick(data.getData());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
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

    private void bind(int id, boolean init, BoolSetter setter) {
        Switch sw = findViewById(id);
        sw.setChecked(init);
        sw.setOnCheckedChangeListener((b, checked) -> setter.set(checked));
    }
}