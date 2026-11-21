package com.ziruoling.quiz.util;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.ziruoling.quiz.data.Prefs;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 答题音效：答对上行双音（明亮），答错下行双音（低沉）。
 *
 * 播放链路（按兼容性优先级，逐级兜底）：
 * 1. AudioTrack「流式」模式（先 play 后 write，独立线程播完释放）——
 *    规避 MODE_STATIC 在部分国产 ROM 上静默失败的问题，全机型兼容性最好；
 * 2. AudioTrack 不可用时自动降级 ToneGenerator 系统提示音。
 * 零音频资源、零第三方依赖；任何失败均静默，绝不影响答题流程。
 */
public final class SoundFx {

    private static final int RATE = 44100;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    /** 单线程顺序播放：连续答题时提示音排队，不叠加 */
    private static final ExecutorService player = Executors.newSingleThreadExecutor();

    private static volatile short[] pcmCorrect;
    private static volatile short[] pcmWrong;

    private SoundFx() {}

    /** correct = true 播「答对」提示音，false 播「答错」提示音 */
    public static void play(final boolean correct) {
        if (!Prefs.sound()) return;
        player.execute(() -> {
            // 首选：AudioTrack 流式播放
            if (!playOnTrack(correct)) {
                // 兜底：系统提示音
                playTone(correct);
            }
        });
    }

    /** 试听入口：不受设置开关限制（排查音效问题用） */
    public static void preview(final boolean correct) {
        player.execute(() -> {
            if (!playOnTrack(correct)) playTone(correct);
        });
    }

    /** 无常驻音频资源，保留接口供界面销毁时调用 */
    public static void release() {
    }

    // ---------------- 方案一：AudioTrack 流式播放 ----------------

    private static boolean playOnTrack(boolean correct) {
        AudioTrack track = null;
        try {
            short[] pcm = correct ? correctPcm() : wrongPcm();
            int minBuf = AudioTrack.getMinBufferSize(RATE,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minBuf <= 0) minBuf = RATE / 5; // 约 200ms 数据量
            track = new AudioTrack(AudioManager.STREAM_MUSIC, RATE,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(minBuf, pcm.length * 2), AudioTrack.MODE_STREAM);
            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                track.release();
                return false;
            }
            // 流式：先启动再写入，write 会阻塞直到数据被消费
            track.play();
            if (track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                track.release();
                return false;
            }
            int off = 0;
            while (off < pcm.length) {
                int n = track.write(pcm, off, pcm.length - off);
                if (n <= 0) break;
                off += n;
            }
            // 等最后一段数据放完再释放
            SystemClock.sleep(Math.round(pcm.length * 1000f / RATE) + 60);
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            if (track != null) {
                try {
                    track.stop();
                } catch (Throwable ignored) {
                }
                try {
                    track.release();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // ---------------- 方案二：ToneGenerator 系统提示音 ----------------

    private static void playTone(boolean correct) {
        try {
            final ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_MUSIC, 90);
            tg.startTone(correct ? ToneGenerator.TONE_PROP_ACK : ToneGenerator.TONE_PROP_NACK);
            handler.postDelayed(() -> {
                try {
                    tg.release();
                } catch (Throwable ignored) {
                }
            }, 600);
        } catch (Throwable ignored) {
        }
    }

    // ---------------- PCM 合成 ----------------

    /** 答对：880Hz → 1318Hz 上扬双音（轻快） */
    private static short[] correctPcm() {
        if (pcmCorrect == null) {
            pcmCorrect = synth(new int[]{880, 1318}, new int[]{110, 150});
        }
        return pcmCorrect;
    }

    /** 答错：392Hz → 247Hz 下沉双音（低沉） */
    private static short[] wrongPcm() {
        if (pcmWrong == null) {
            pcmWrong = synth(new int[]{392, 247}, new int[]{140, 200});
        }
        return pcmWrong;
    }

    /**
     * 合成「音符1 + 30ms 静音 + 音符2」16bit 单声道 PCM。
     * 频率均 >=247Hz，兼顾手机小喇叭频响；每音首尾 12ms 淡入淡出防爆音。
     */
    private static short[] synth(int[] freqs, int[] durs) {
        int gapMs = 30;
        int totalMs = gapMs;
        for (int d : durs) totalMs += d;
        short[] out = new short[Math.round(totalMs * RATE / 1000f)];
        int pos = 0;
        double amp = 0.6 * Short.MAX_VALUE;
        for (int i = 0; i < freqs.length; i++) {
            int n = Math.round(durs[i] * RATE / 1000f);
            int fadeN = Math.max(1, Math.round(12 * RATE / 1000f));
            for (int k = 0; k < n && pos < out.length; k++, pos++) {
                double g = 1.0;
                if (k < fadeN) g = k / (double) fadeN;
                else if (k > n - fadeN) g = Math.max(0, (n - k) / (double) fadeN);
                double v = Math.sin(2 * Math.PI * freqs[i] * (k / (double) RATE)) * amp * g;
                out[pos] = (short) Math.max(Short.MIN_VALUE,
                        Math.min(Short.MAX_VALUE, Math.round(v)));
            }
            if (i == 0) pos += Math.round(gapMs * RATE / 1000f);
        }
        return out;
    }
}