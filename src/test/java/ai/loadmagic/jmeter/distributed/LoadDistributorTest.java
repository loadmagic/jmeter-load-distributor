/*
 * Licensed under the Apache License, Version 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package ai.loadmagic.jmeter.distributed;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for the distribution algorithm.
 */
public class LoadDistributorTest {

    // ---- Exact division ----

    @Test
    public void testExactDivision() {
        assertEquals(4, LoadDistributor.share(12, 1, 3));
        assertEquals(4, LoadDistributor.share(12, 2, 3));
        assertEquals(4, LoadDistributor.share(12, 3, 3));
        assertEquals(25, LoadDistributor.share(100, 1, 4));
        assertEquals(25, LoadDistributor.share(100, 4, 4));
    }

    // ---- Remainder distribution ----

    @Test
    public void testRemainder_4by3() {
        assertEquals(2, LoadDistributor.share(4, 1, 3));
        assertEquals(1, LoadDistributor.share(4, 2, 3));
        assertEquals(1, LoadDistributor.share(4, 3, 3));
    }

    @Test
    public void testRemainder_100by3() {
        assertEquals(34, LoadDistributor.share(100, 1, 3));
        assertEquals(33, LoadDistributor.share(100, 2, 3));
        assertEquals(33, LoadDistributor.share(100, 3, 3));
    }

    @Test
    public void testRemainder_7by4() {
        assertEquals(2, LoadDistributor.share(7, 1, 4));
        assertEquals(2, LoadDistributor.share(7, 2, 4));
        assertEquals(2, LoadDistributor.share(7, 3, 4));
        assertEquals(1, LoadDistributor.share(7, 4, 4));
    }

    // ---- Sum invariant ----

    @Test
    public void testSumAlwaysEqualsTotal() {
        int[][] cases = {
            {1, 1}, {1, 5}, {4, 3}, {7, 4}, {10, 3}, {100, 3},
            {100, 7}, {1000, 13}, {3, 10}, {0, 5}, {1, 100}
        };
        for (int[] c : cases) {
            int sum = 0;
            for (int id = 1; id <= c[1]; id++) {
                sum += LoadDistributor.share(c[0], id, c[1]);
            }
            assertEquals("sum(" + c[0] + "/" + c[1] + ")", c[0], sum);
        }
    }

    // ---- More generators than threads ----

    @Test
    public void testExcessGenerators() {
        assertEquals(1, LoadDistributor.share(3, 1, 5));
        assertEquals(1, LoadDistributor.share(3, 2, 5));
        assertEquals(1, LoadDistributor.share(3, 3, 5));
        assertEquals(0, LoadDistributor.share(3, 4, 5));
        assertEquals(0, LoadDistributor.share(3, 5, 5));
    }

    @Test
    public void testOneThread() {
        assertEquals(1, LoadDistributor.share(1, 1, 3));
        assertEquals(0, LoadDistributor.share(1, 2, 3));
        assertEquals(0, LoadDistributor.share(1, 3, 3));
    }

    @Test
    public void testZeroThreads() {
        assertEquals(0, LoadDistributor.share(0, 1, 3));
        assertEquals(0, LoadDistributor.share(0, 2, 3));
    }

    // ---- Single generator ----

    @Test
    public void testSingleGenerator() {
        assertEquals(100, LoadDistributor.share(100, 1, 1));
        assertEquals(1, LoadDistributor.share(1, 1, 1));
    }

    // ---- No throttling (properties not set) ----

    @Test
    public void testNoThrottling() {
        assertEquals(100, LoadDistributor.share(100, 1, 0));
        assertEquals(100, LoadDistributor.share(100, 0, 3));
    }

    // ---- Generator ID beyond count ----

    @Test
    public void testIdBeyondCount() {
        assertEquals(0, LoadDistributor.share(100, 4, 3));
        assertEquals(0, LoadDistributor.share(100, 10, 3));
    }

    // ---- Fairness: max difference is 1 ----

    @Test
    public void testMaxDifferenceIsOne() {
        int total = 101, count = 7;
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int id = 1; id <= count; id++) {
            int s = LoadDistributor.share(total, id, count);
            min = Math.min(min, s);
            max = Math.max(max, s);
        }
        assertEquals(1, max - min);
    }

    // ---- Large numbers ----

    @Test
    public void testLargeNumbers() {
        int total = 10000, count = 37;
        int sum = 0;
        for (int id = 1; id <= count; id++) {
            sum += LoadDistributor.share(total, id, count);
        }
        assertEquals(total, sum);
    }

    // ---- Realistic distributed testing scenarios ----

    @Test
    public void testRealisticScenario_500usersAcross8generators() {
        int total = 500, count = 8;
        // base = 62, remainder = 4
        assertEquals(63, LoadDistributor.share(total, 1, count));
        assertEquals(63, LoadDistributor.share(total, 2, count));
        assertEquals(63, LoadDistributor.share(total, 3, count));
        assertEquals(63, LoadDistributor.share(total, 4, count));
        assertEquals(62, LoadDistributor.share(total, 5, count));
        assertEquals(62, LoadDistributor.share(total, 6, count));
        assertEquals(62, LoadDistributor.share(total, 7, count));
        assertEquals(62, LoadDistributor.share(total, 8, count));
        // Verify sum
        int sum = 0;
        for (int id = 1; id <= count; id++) sum += LoadDistributor.share(total, id, count);
        assertEquals(total, sum);
    }
}
