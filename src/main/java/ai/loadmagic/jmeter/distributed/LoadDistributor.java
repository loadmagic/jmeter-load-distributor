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
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;
import org.apache.jorphan.collections.SearchByClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Distributed Load Distributor — automatically distributes thread counts
 * across JMeter generators for distributed testing.
 *
 * <p>Can be activated in two ways:</p>
 * <ol>
 *   <li><strong>Auto-activation (recommended):</strong> Simply drop the JAR into
 *       {@code lib/ext/} and pass {@code -Jgenerator.id} and {@code -Jgenerator.count}
 *       at the command line. The plugin registers itself automatically via
 *       {@link LoadDistributorAutoActivator} — no test plan changes needed.</li>
 *   <li><strong>Test plan element:</strong> Add as a Config Element at the Test Plan
 *       level. This is optional but supported for backward compatibility.</li>
 * </ol>
 *
 * <p>At test start, it reads two JMeter properties ({@code generator.id} and
 * {@code generator.count}), then adjusts every ThreadGroup's thread count so
 * this generator runs exactly its fair share of the total load.</p>
 *
 * <p>Usage: {@code jmeter -Jgenerator.id=2 -Jgenerator.count=3 -t test.jmx}</p>
 *
 * <p>Supports: Standard ThreadGroup, Ultimate Thread Group (jpgc-casutg),
 * Concurrency Thread Group, Stepping Thread Group.</p>
 *
 * <p>Thread count modifications are non-destructive — JMeter's running version
 * mechanism automatically restores original values when the test ends.
 * The .jmx file is never modified.</p>
 *
 * <p>If both auto-activation and a test plan element are present, distribution
 * runs only once — duplicate invocation is prevented automatically.</p>
 */
public class LoadDistributor extends AbstractTestElement implements TestStateListener {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(LoadDistributor.class);

    // JMeter properties (set via -J on command line)
    public static final String PROP_GENERATOR_ID = "generator.id";
    public static final String PROP_GENERATOR_COUNT = "generator.count";

    // ThreadGroup type detection — by class name to avoid compile-time dependency
    private static final String UTG_CLASS = "kg.apc.jmeter.threads.UltimateThreadGroup";
    private static final String CTG_CLASS = "com.blazemeter.jmeter.threads.concurrency.ConcurrencyThreadGroup";
    private static final String STG_CLASS = "kg.apc.jmeter.threads.SteppingThreadGroup";

    // Property keys for each ThreadGroup type
    private static final String UTG_DATA_PROPERTY = "ultimatethreadgroupdata";
    private static final String CTG_TARGET_LEVEL = "TargetLevel";
    private static final String STG_NUM_THREADS = "NumThreads";

    // Cached reflection fields — resolved once, reused
    private static volatile Field engineField;
    private static volatile Field testTreeField;

    // Prevents duplicate distribution when both auto-activator and test plan element are present
    private static volatile boolean distributed = false;

    @Override
    public void testStarted() {
        distribute();
    }

    @Override
    public void testStarted(String host) {
        distribute();
    }

    @Override
    public void testEnded() {
        distributed = false; // Reset for next test run in same JVM (GUI mode)
    }

    @Override
    public void testEnded(String host) {
        distributed = false;
    }

    // ---- Main entry point ----

    private synchronized void distribute() {
        if (distributed) {
            log.info("Load Distributor: Already distributed by another instance "
                    + "(auto-activator or test plan element). Skipping.");
            return;
        }

        int id = parseIntProperty(PROP_GENERATOR_ID, 0);
        int count = parseIntProperty(PROP_GENERATOR_COUNT, 0);

        if (count <= 0 || id <= 0) {
            log.info("Load Distributor: No generator properties set "
                    + "(-Jgenerator.id and -Jgenerator.count). "
                    + "Running full load on this node.");
            return;
        }

        if (id > count) {
            log.warn("Load Distributor: generator.id={} exceeds generator.count={}. "
                    + "This generator will run 0 threads.", id, count);
        }

        HashTree tree = resolveTestTree();
        if (tree == null) {
            log.error("Load Distributor: Cannot access JMeter test tree. "
                    + "No load distribution applied. "
                    + "This may indicate an incompatible JMeter version.");
            return;
        }

        SearchByClass<AbstractThreadGroup> search =
                new SearchByClass<>(AbstractThreadGroup.class);
        tree.traverse(search);

        int groupCount = 0;
        for (AbstractThreadGroup group : search.getSearchResults()) {
            distributeGroup(group, id, count);
            groupCount++;
        }

        distributed = true;
        // Prevent auto-activator from registering again if Function SPI re-initialises
        LoadDistributorAutoActivator.markRegisteredByTestPlan();

        log.info("Load Distributor: Generator {}/{} — distributed load across {} thread group(s).",
                id, count, groupCount);
    }

    // ---- Per-ThreadGroup distribution ----

    private void distributeGroup(AbstractThreadGroup group, int id, int count) {
        String className = group.getClass().getName();

        switch (className) {
            case UTG_CLASS:
                distributeUTG(group, id, count);
                break;
            case CTG_CLASS:
                distributeCTG(group, id, count);
                break;
            case STG_CLASS:
                distributeSTG(group, id, count);
                break;
            default:
                distributeStandard(group, id, count);
                break;
        }
    }

    private void distributeStandard(AbstractThreadGroup group, int id, int count) {
        int total = group.getNumThreads();
        int local = share(total, id, count);
        group.setNumThreads(local);
        log.info("  [{}] {} → {} threads", group.getName(), total, local);
    }

    private void distributeUTG(AbstractThreadGroup group, int id, int count) {
        JMeterProperty prop = group.getProperty(UTG_DATA_PROPERTY);
        if (prop instanceof NullProperty || !(prop instanceof CollectionProperty)) {
            log.warn("  [{}] UTG: no schedule data, skipping.", group.getName());
            return;
        }

        CollectionProperty rows = (CollectionProperty) prop;
        int totalAll = 0, localAll = 0;

        for (int i = 0; i < rows.size(); i++) {
            JMeterProperty rowProp = rows.get(i);
            if (!(rowProp instanceof CollectionProperty)) continue;

            @SuppressWarnings("unchecked")
            List<JMeterProperty> cells =
                    (List<JMeterProperty>) ((CollectionProperty) rowProp).getObjectValue();
            if (cells.isEmpty()) continue;

            int rowTotal = cells.get(0).getIntValue();
            int rowLocal = share(rowTotal, id, count);
            totalAll += rowTotal;
            localAll += rowLocal;

            cells.get(0).setObjectValue(String.valueOf(rowLocal));
        }

        log.info("  [{}] UTG {} → {} threads ({} rows)",
                group.getName(), totalAll, localAll, rows.size());
    }

    private void distributeCTG(AbstractThreadGroup group, int id, int count) {
        int total = parsePropertyInt(group, CTG_TARGET_LEVEL);
        if (total < 0) {
            log.warn("  [{}] CTG: TargetLevel is non-numeric, skipping.", group.getName());
            return;
        }
        int local = share(total, id, count);
        group.setProperty(CTG_TARGET_LEVEL, String.valueOf(local));
        log.info("  [{}] CTG {} → {} threads", group.getName(), total, local);
    }

    private void distributeSTG(AbstractThreadGroup group, int id, int count) {
        int total = parsePropertyInt(group, STG_NUM_THREADS);
        if (total < 0) {
            log.warn("  [{}] STG: NumThreads is non-numeric, skipping.", group.getName());
            return;
        }
        int local = share(total, id, count);
        group.setProperty(STG_NUM_THREADS, String.valueOf(local));
        log.info("  [{}] STG {} → {} threads", group.getName(), total, local);
    }

    // ---- Distribution algorithm ----

    /**
     * Calculates this generator's share of the total thread count.
     *
     * <p>Guarantees: sum of {@code share(total, id, count)} for id=1..count
     * equals {@code total}. Lower-numbered generators receive remainder threads.
     * Returns 0 if this generator has nothing to run.</p>
     */
    static int share(int total, int id, int count) {
        if (count <= 0 || id <= 0) return total;
        if (id > count) return 0;
        int base = total / count;
        int remainder = total % count;
        return (id <= remainder) ? base + 1 : base;
    }

    // ---- Test tree access via reflection ----

    /**
     * Resolves the JMeter test tree via reflection on StandardJMeterEngine.
     *
     * <p>Accesses two private fields: the static {@code engine} singleton and
     * its instance field {@code test} (the HashTree). These fields have been
     * stable since JMeter 2.x. Field references are cached to avoid repeated
     * lookups.</p>
     *
     * @return the test tree, or null if reflection fails
     */
    private HashTree resolveTestTree() {
        try {
            // Resolve and cache field references (once per JVM)
            if (engineField == null) {
                engineField = StandardJMeterEngine.class.getDeclaredField("engine");
                engineField.setAccessible(true);
            }
            if (testTreeField == null) {
                testTreeField = StandardJMeterEngine.class.getDeclaredField("test");
                testTreeField.setAccessible(true);
            }

            // Read: static StandardJMeterEngine.engine
            StandardJMeterEngine engine = (StandardJMeterEngine) engineField.get(null);
            if (engine == null) {
                log.warn("Load Distributor: StandardJMeterEngine.engine is null.");
                return null;
            }

            // Read: engine.test
            return (HashTree) testTreeField.get(engine);

        } catch (NoSuchFieldException e) {
            log.error("Load Distributor: JMeter internal field not found: '{}'. "
                    + "This JMeter version may not be compatible.", e.getMessage());
        } catch (IllegalAccessException e) {
            log.error("Load Distributor: Cannot access JMeter internal field: '{}'. "
                    + "Check SecurityManager settings.", e.getMessage());
        } catch (Exception e) {
            log.error("Load Distributor: Unexpected error accessing test tree.", e);
        }
        return null;
    }

    // ---- Utilities ----

    private int parseIntProperty(String propName, int defaultValue) {
        String value = JMeterUtils.getProperty(propName);
        if (value == null || value.trim().isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Load Distributor: Property '{}' = '{}' is not a valid integer.",
                    propName, value);
            return defaultValue;
        }
    }

    private int parsePropertyInt(AbstractTestElement element, String key) {
        String val = element.getPropertyAsString(key, "");
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
