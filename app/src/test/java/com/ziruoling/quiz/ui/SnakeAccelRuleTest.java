package com.ziruoling.quiz.ui;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 吃果子加速点必须是 1、9、18、27…（不含 81） */
public class SnakeAccelRuleTest {

    @Test
    public void accelAtFirstAndMultiplesOfNine() {
        assertTrue(SnakeActivity.isFoodAccel(1));
        for (int k = 1; k <= 8; k++) {
            int n = 9 * k;
            assertTrue("第 " + n + " 个应触发加速", SnakeActivity.isFoodAccel(n));
        }
        assertTrue(SnakeActivity.isFoodAccel(72));
    }

    @Test
    public void otherNumbersDoNotTrigger() {
        for (int n : new int[]{2, 3, 4, 5, 6, 7, 8, 10, 11, 17, 19, 28, 37, 46, 79, 80}) {
            assertFalse("第 " + n + " 个不应触发加速", SnakeActivity.isFoodAccel(n));
        }
    }
}
