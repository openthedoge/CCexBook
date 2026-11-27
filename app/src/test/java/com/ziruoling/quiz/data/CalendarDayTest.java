package com.ziruoling.quiz.data;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Calendar;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 日历与「今日」统计必须落在同一天（时区错位会导致差一天） */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CalendarDayTest {

    private long bankId;

    @Before
    public void setUp() {
        Prefs.resetForTest();
        Prefs.init(org.robolectric.RuntimeEnvironment.getApplication());
        Db.resetForTest();
        Db.init(org.robolectric.RuntimeEnvironment.getApplication());
        bankId = Db.createBank("日历测试", "");
        Db.importQuestions(bankId, DemoBank.build());
    }

    @Test
    public void todayDetailMatchesTodayCount() {
        List<Long> ids = Db.questionIds(bankId);
        Db.addRecord(bankId, ids.get(0), 0, "A", true, false);
        Db.addRecord(bankId, ids.get(1), 0, "B", false, false);

        Calendar c = Calendar.getInstance();
        int[] detail = Db.dayDetail(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1,
                c.get(Calendar.DAY_OF_MONTH));
        assertEquals("日历当日数量应等于今日做题数", Db.todayCount(), detail[0]);
        assertTrue("今日应有做题记录", detail[0] > 0);
    }

    @Test
    public void monthSeriesCellMatchesDetail() {
        List<Long> ids = Db.questionIds(bankId);
        Db.addRecord(bankId, ids.get(0), 0, "A", true, false);

        Calendar c = Calendar.getInstance();
        int day = c.get(Calendar.DAY_OF_MONTH);
        int[] series = Db.monthSeries(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1);
        int[] detail = Db.dayDetail(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, day);
        assertEquals("日历格子应与当日详情一致", detail[0], series[day]);
        assertTrue("今日格子应有数据", series[day] > 0);
    }

    @Test
    public void yesterdayHasNoTodaysData() {
        List<Long> ids = Db.questionIds(bankId);
        Db.addRecord(bankId, ids.get(0), 0, "A", true, false);

        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, -1);
        int[] detail = Db.dayDetail(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1,
                c.get(Calendar.DAY_OF_MONTH));
        assertEquals("昨天的格子不应出现今天的做题量", 0, detail[0]);
    }
}
