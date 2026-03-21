/*
 * Licensed under the Apache License, Version 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package ai.loadmagic.jmeter.distributed;

import org.apache.jmeter.engine.StandardJMeterEngine;
import org.apache.jmeter.testelement.AbstractTestElement;
import org.apache.jmeter.testelement.TestStateListener;
import org.apache.jmeter.testelement.property.CollectionProperty;
import org.apache.jmeter.testelement.property.JMeterProperty;
import org.apache.jmeter.testelement.property.NullProperty;
import org.apache.jmeter.threads.AbstractThreadGroup;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.ThreadGroup;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;
import org.apache.jorphan.collections.SearchByClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Distributed Load Throttler — automatically distributes thread counts
 * across JMeter generators for distributed testing.
 *
 * <p>Place this element at the Test Plan level. At test start, it reads
 * two JMeter properties ({@code generator.id} and {@code generator.count}),
 * then adjusts every ThreadGroup's thread count so this generator runs
 * exactly its fair share of the total load.</p>
 *
 * <p>Usage: {@code jmeter -Jgenerator.id=2 -Jgenerator.count=3 -t test.jmx}</p>
 *
 * <p>Supports: Standard ThreadGroup, Ultimate Thread Group (jpgc-casutg),
 * Concurrency Thread Group, Stepping Thread Group.</p>
 *
 * <p>Thread count modifications are non-destructive — JMeter's running version
 * mechanism automatically restores original values when the test ends.
 * The .jmx file is never modified.</p>
 */
public class LoadThrottler extends AbstractTestElement implements TestStateListener {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(LoadThrottler.class);

    /** JMeter property: which generator am I? (1-indexed) */
    public static final String PROP_GENERATOR_ID = "generator.id";

    /** JMeter property: how many generators total? */
    public static final String PROP_GENERATOR_COUNT = "generator.count";

    /** UTG stores schedule rows under this property key */
    private static final String UTG_DATA_PROPERTY = "ultimatethreadgroupdata";

    /** Concurrency Thread Group target level property */
    private static final String CTG_TARGET_LEVEL = "TargetLevel";

    /** Stepping Thread Group properties */
    private static final String STG_NUM_THREADS = "NumThreads";

    @Override
    public void testStarted() {
        throttle();
    }

    @Override
    public void testStarted(String host) {
        throttle();
    }

    @Override
    public void testEnded() {
        // Nothing to do — recoverRunningVersion() restores original values
    }

    @Override
    public void testEnded(String host) {
        // Nothing to do
    }

    private void throttle() {
        int generatorId = parseIntProperty(PROP_GENERATOR_ID, 0);
        int generatorCount = parseIntProperty(PROP_GENERATOR_COUNT, 0);

        if (generatorCount <= 0 || generatorId <= 0) {
            log.info("Load Throttler: No generator properties set "
                    + "(-Jgenerator.id and -Jgenerator.count). "
                    + "Running full load on this node.");
            return;
        }

        if (generatorId > generatorCount) {
            log.warn("Load Throttler: generator.id={} > generator.count={}. "
                    + "This generator will run 0 threads for all groups.",
                    generatorId, generatorCount);
        }

        log.info("Load Throttler: Generator {}/{} — distributing load.",
                generatorId, generatorCount);

        // Walk the test tree to find all ThreadGroups
        HashTree testTree = getTestTree();
        if (testTree == null) {
            log.warn("Load Throttler: Could not access test tree. No throttling applied.");
            return;
        }

        SearchByClass<AbstractThreadGroup> searcher =
                new SearchByClass<>(AbstractThreadGroup.class);
        testTree.traverse(searcher);
        Collection<AbstractThreadGroup> groups = searcher.getSearchResults();

        if (groups.isEmpty()) {
            log.warn("Load Throttler: No ThreadGroups found in test tree.");
            return;
        }

        for (AbstractThreadGroup group : groups) {
            throttleGroup(group, generatorId, generatorCount);
        }
    }

    private void throttleGroup(AbstractThreadGroup group, int generatorId, int generatorCount) {
        String groupName = group.getName();
        String className = group.getClass().getSimpleName();

        // Detect ThreadGroup type and apply appropriate throttling
        if (isUltimateThreadGroup(group)) {
            throttleUTG(group, groupName, generatorId, generatorCount);
        } else if (isConcurrencyThreadGroup(group)) {
            throttleCTG(group, groupName, generatorId, generatorCount);
        } else if (isSteppingThreadGroup(group)) {
            throttleSTG(group, groupName, generatorId, generatorCount);
        } else {
            // Standard ThreadGroup or unknown — use getNumThreads/setNumThreads
            throttleStandard(group, groupName, generatorId, generatorCount);
        }
    }

    // ---- Standard ThreadGroup ----

    private void throttleStandard(AbstractThreadGroup group, String name,
                                  int generatorId, int generatorCount) {
        int totalThreads = group.getNumThreads();
        int localThreads = distribute(totalThreads, generatorId, generatorCount);

        group.setNumThreads(localThreads);
        log.info("Load Throttler: [{}] {} threads -> {} (generator {}/{})",
                name, totalThreads, localThreads, generatorId, generatorCount);
    }

    // ---- Ultimate Thread Group ----

    private boolean isUltimateThreadGroup(AbstractThreadGroup group) {
        return group.getClass().getName().equals(
                "kg.apc.jmeter.threads.UltimateThreadGroup");
    }

    private void throttleUTG(AbstractThreadGroup group, String name,
                             int generatorId, int generatorCount) {
        JMeterProperty dataProp = group.getProperty(UTG_DATA_PROPERTY);
        if (dataProp instanceof NullProperty) {
            log.warn("Load Throttler: [{}] UTG has no schedule data.", name);
            return;
        }

        if (!(dataProp instanceof CollectionProperty)) {
            log.warn("Load Throttler: [{}] UTG data is not CollectionProperty: {}",
                    name, dataProp.getClass().getSimpleName());
            return;
        }

        CollectionProperty rows = (CollectionProperty) dataProp;
        int totalThreads = 0;
        int localThreads = 0;

        for (int i = 0; i < rows.size(); i++) {
            JMeterProperty rowProp = rows.get(i);
            if (!(rowProp instanceof CollectionProperty)) {
                continue;
            }

            CollectionProperty row = (CollectionProperty) rowProp;
            @SuppressWarnings("unchecked")
            List<JMeterProperty> rowValues =
                    (List<JMeterProperty>) row.getObjectValue();

            if (rowValues.isEmpty()) {
                continue;
            }

            int rowThreads = rowValues.get(0).getIntValue();
            int rowLocal = distribute(rowThreads, generatorId, generatorCount);

            totalThreads += rowThreads;
            localThreads += rowLocal;

            // Modify the thread count in-place (running version — auto-restored)
            rowValues.get(0).setObjectValue(String.valueOf(rowLocal));
        }

        log.info("Load Throttler: [{}] UTG {} total threads -> {} (generator {}/{}, {} rows)",
                name, totalThreads, localThreads, generatorId, generatorCount, rows.size());
    }

    // ---- Concurrency Thread Group ----

    private boolean isConcurrencyThreadGroup(AbstractThreadGroup group) {
        String className = group.getClass().getName();
        return className.equals("com.blazemeter.jmeter.threads.concurrency.ConcurrencyThreadGroup");
    }

    private void throttleCTG(AbstractThreadGroup group, String name,
                             int generatorId, int generatorCount) {
        String targetStr = group.getPropertyAsString(CTG_TARGET_LEVEL, "0");
        int totalThreads;
        try {
            totalThreads = Integer.parseInt(targetStr.trim());
        } catch (NumberFormatException e) {
            // Could be a ${__P()} expression — can't throttle dynamically
            log.warn("Load Throttler: [{}] CTG TargetLevel is '{}' (not numeric). Skipping.",
                    name, targetStr);
            return;
        }

        int localThreads = distribute(totalThreads, generatorId, generatorCount);
        group.setProperty(CTG_TARGET_LEVEL, String.valueOf(localThreads));

        log.info("Load Throttler: [{}] CTG {} threads -> {} (generator {}/{})",
                name, totalThreads, localThreads, generatorId, generatorCount);
    }

    // ---- Stepping Thread Group ----

    private boolean isSteppingThreadGroup(AbstractThreadGroup group) {
        return group.getClass().getName().equals(
                "kg.apc.jmeter.threads.SteppingThreadGroup");
    }

    private void throttleSTG(AbstractThreadGroup group, String name,
                             int generatorId, int generatorCount) {
        // STG uses "NumThreads" for total thread count
        String numStr = group.getPropertyAsString(STG_NUM_THREADS, "0");
        int totalThreads;
        try {
            totalThreads = Integer.parseInt(numStr.trim());
        } catch (NumberFormatException e) {
            log.warn("Load Throttler: [{}] STG NumThreads is '{}' (not numeric). Skipping.",
                    name, numStr);
            return;
        }

        int localThreads = distribute(totalThreads, generatorId, generatorCount);
        group.setProperty(STG_NUM_THREADS, String.valueOf(localThreads));

        log.info("Load Throttler: [{}] STG {} threads -> {} (generator {}/{})",
                name, totalThreads, localThreads, generatorId, generatorCount);
    }

    // ---- Distribution algorithm ----

    /**
     * Distributes {@code total} items across {@code count} generators,
     * returning the share for generator {@code id}.
     *
     * <p>Guarantees: sum across all generators == total. Lower-numbered
     * generators receive the remainder threads.</p>
     *
     * @param total total thread count to distribute
     * @param id    this generator's ID (1-indexed)
     * @param count total number of generators
     * @return number of threads for this generator (0 if id > total)
     */
    static int distribute(int total, int id, int count) {
        if (count <= 0 || id <= 0) {
            return total;
        }
        if (id > count) {
            return 0;
        }

        int base = total / count;
        int remainder = total % count;
        return (id <= remainder) ? base + 1 : base;
    }

    // ---- Utilities ----

    private int parseIntProperty(String propName, int defaultValue) {
        String value = JMeterUtils.getProperty(propName);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Load Throttler: Property '{}' value '{}' is not a valid integer.",
                    propName, value);
            return defaultValue;
        }
    }

    private HashTree getTestTree() {
        try {
            // Access the test tree via StandardJMeterEngine's static field
            java.lang.reflect.Field field =
                    StandardJMeterEngine.class.getDeclaredField("test");
            // The test tree is not directly accessible — fall back to context
        } catch (NoSuchFieldException e) {
            // Expected — the field name may vary
        }

        // The test plan tree is available through JMeterContextService
        // when testStarted() is called. Walk up from this element.
        // Since we're a TestStateListener found by SearchByClass,
        // we need the engine's tree. Use the JMeterContext thread group list.
        try {
            // Access via the engine — StandardJMeterEngine stores the tree
            // We need a reference to it. The cleanest way is to use
            // the fact that our element is IN the tree, and the engine
            // traverses it to find us.
            StandardJMeterEngine engine = getEngine();
            if (engine != null) {
                java.lang.reflect.Field testField =
                        StandardJMeterEngine.class.getDeclaredField("test");
                testField.setAccessible(true);
                return (HashTree) testField.get(engine);
            }
        } catch (Exception e) {
            log.debug("Load Throttler: Could not access test tree via reflection: {}",
                    e.getMessage());
        }

        return null;
    }

    private StandardJMeterEngine getEngine() {
        try {
            java.lang.reflect.Field engineField =
                    JMeterContextService.class.getDeclaredField("engine");
            engineField.setAccessible(true);

            // Try thread-local context first
            Object ctx = JMeterContextService.getContext();
            if (ctx != null) {
                java.lang.reflect.Method getEngine =
                        ctx.getClass().getMethod("getEngine");
                Object engine = getEngine.invoke(ctx);
                if (engine instanceof StandardJMeterEngine) {
                    return (StandardJMeterEngine) engine;
                }
            }
        } catch (Exception e) {
            log.debug("Load Throttler: Could not get engine from context: {}",
                    e.getMessage());
        }
        return null;
    }
}
