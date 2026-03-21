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
public class LoadThrottlerTest {

    // ---- Exact division ----

    @Test
    public void testExactDivision_12by3() {
        // 12 threads across 3 generators = 4 each
        assertEquals(4, LoadThrottler.distribute(12, 1, 3));
        assertEquals(4, LoadThrottler.distribute(12, 2, 3));
        assertEquals(4, LoadThrottler.distribute(12, 3, 3));
    }

    @Test
    public void testExactDivision_100by4() {
        assertEquals(25, LoadThrottler.distribute(100, 1, 4));
        assertEquals(25, LoadThrottler.distribute(100, 2, 4));
        assertEquals(25, LoadThrottler.distribute(100, 3, 4));
        assertEquals(25, LoadThrottler.distribute(100, 4, 4));
    }

    // ---- Remainder distribution ----

    @Test
    public void testRemainder_4by3() {
        // 4 threads across 3 generators: 2, 1, 1
        assertEquals(2, LoadThrottler.distribute(4, 1, 3));
        assertEquals(1, LoadThrottler.distribute(4, 2, 3));
        assertEquals(1, LoadThrottler.distribute(4, 3, 3));
    }

    @Test
    public void testRemainder_10by3() {
        // 10 threads across 3 generators: 4, 3, 3
        assertEquals(4, LoadThrottler.distribute(10, 1, 3));
        assertEquals(3, LoadThrottler.distribute(10, 2, 3));
        assertEquals(3, LoadThrottler.distribute(10, 3, 3));
    }

    @Test
    public void testRemainder_7by4() {
        // 7 threads across 4 generators: 2, 2, 2, 1
        assertEquals(2, LoadThrottler.distribute(7, 1, 4));
        assertEquals(2, LoadThrottler.distribute(7, 2, 4));
        assertEquals(2, LoadThrottler.distribute(7, 3, 4));
        assertEquals(1, LoadThrottler.distribute(7, 4, 4));
    }

    @Test
    public void testRemainder_100by3() {
        // 100 threads across 3 generators: 34, 33, 33
        assertEquals(34, LoadThrottler.distribute(100, 1, 3));
        assertEquals(33, LoadThrottler.distribute(100, 2, 3));
        assertEquals(33, LoadThrottler.distribute(100, 3, 3));
    }

    // ---- Sum invariant: total across all generators == original ----

    @Test
    public void testSumInvariant() {
        int[][] cases = {
            {1, 1}, {1, 5}, {4, 3}, {7, 4}, {10, 3}, {100, 3},
            {100, 7}, {1000, 13}, {3, 10}, {0, 5}, {1, 100}
        };

        for (int[] c : cases) {
            int total = c[0];
            int count = c[1];
            int sum = 0;
            for (int id = 1; id <= count; id++) {
                sum += LoadThrottler.distribute(total, id, count);
            }
            assertEquals("Sum should equal total for " + total + "/" + count,
                    total, sum);
        }
    }

    // ---- Edge cases: more generators than threads ----

    @Test
    public void testMoreGeneratorsThanThreads() {
        // 3 threads across 5 generators: 1, 1, 1, 0, 0
        assertEquals(1, LoadThrottler.distribute(3, 1, 5));
        assertEquals(1, LoadThrottler.distribute(3, 2, 5));
        assertEquals(1, LoadThrottler.distribute(3, 3, 5));
        assertEquals(0, LoadThrottler.distribute(3, 4, 5));
        assertEquals(0, LoadThrottler.distribute(3, 5, 5));
    }

    @Test
    public void testOneThread() {
        // 1 thread across 3 generators: 1, 0, 0
        assertEquals(1, LoadThrottler.distribute(1, 1, 3));
        assertEquals(0, LoadThrottler.distribute(1, 2, 3));
        assertEquals(0, LoadThrottler.distribute(1, 3, 3));
    }

    @Test
    public void testZeroThreads() {
        // 0 threads across any number of generators = 0 each
        assertEquals(0, LoadThrottler.distribute(0, 1, 3));
        assertEquals(0, LoadThrottler.distribute(0, 2, 3));
        assertEquals(0, LoadThrottler.distribute(0, 3, 3));
    }

    // ---- Edge cases: single generator ----

    @Test
    public void testSingleGenerator() {
        // 1 generator gets everything
        assertEquals(100, LoadThrottler.distribute(100, 1, 1));
        assertEquals(1, LoadThrottler.distribute(1, 1, 1));
        assertEquals(0, LoadThrottler.distribute(0, 1, 1));
    }

    // ---- Edge cases: no throttling ----

    @Test
    public void testNoThrottling_zeroCount() {
        // generator.count=0 means no throttling — return full total
        assertEquals(100, LoadThrottler.distribute(100, 1, 0));
    }

    @Test
    public void testNoThrottling_zeroId() {
        // generator.id=0 means no throttling — return full total
        assertEquals(100, LoadThrottler.distribute(100, 0, 3));
    }

    // ---- Edge case: generator ID beyond count ----

    @Test
    public void testIdBeyondCount() {
        // generator.id > generator.count = 0 threads
        assertEquals(0, LoadThrottler.distribute(100, 4, 3));
        assertEquals(0, LoadThrottler.distribute(100, 10, 3));
    }

    // ---- Fairness: no generator gets more than 1 extra ----

    @Test
    public void testFairness() {
        int total = 101;
        int count = 7;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        for (int id = 1; id <= count; id++) {
            int share = LoadThrottler.distribute(total, id, count);
            min = Math.min(min, share);
            max = Math.max(max, share);
        }

        // Difference between max and min should be at most 1
        assertEquals("Max-min difference should be <= 1", 1, max - min);
    }

    // ---- Large numbers ----

    @Test
    public void testLargeNumbers() {
        int total = 10000;
        int count = 37;
        int sum = 0;
        for (int id = 1; id <= count; id++) {
            sum += LoadThrottler.distribute(total, id, count);
        }
        assertEquals(total, sum);
    }
}
