/*
 * Licensed under the Apache License, Version 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package ai.loadmagic.jmeter.distributed;

import org.apache.jmeter.engine.StandardJMeterEngine;
import org.apache.jmeter.engine.util.CompoundVariable;
import org.apache.jmeter.functions.AbstractFunction;
import org.apache.jmeter.functions.InvalidVariableException;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.util.JMeterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Auto-activates the Load Distributor without requiring a test plan element.
 *
 * <p>JMeter loads all {@link org.apache.jmeter.functions.Function} implementations
 * via {@link java.util.ServiceLoader} during {@link CompoundVariable} static
 * initialisation. This class uses that mechanism as a bootstrap hook: its
 * constructor checks for {@code -Jgenerator.id} and {@code -Jgenerator.count}
 * properties, and if both are set, registers a {@link LoadDistributor} instance
 * with {@link StandardJMeterEngine#register(org.apache.jmeter.testelement.TestStateListener)}.</p>
 *
 * <p>The registered listener receives {@code testStarted()} and {@code testEnded()}
 * callbacks exactly like a test plan element, but without needing to exist in the
 * .jmx file. This means users can drop the JAR into {@code lib/ext/} and use
 * load distribution on any test plan with zero modifications.</p>
 *
 * <p>If the test plan also contains a manually-added Load Distributor config element,
 * duplicate registration is prevented — only one instance will activate.</p>
 *
 * @see LoadDistributor
 * @see StandardJMeterEngine#register(org.apache.jmeter.testelement.TestStateListener)
 */
public class LoadDistributorAutoActivator extends AbstractFunction {

    private static final Logger log = LoggerFactory.getLogger(LoadDistributorAutoActivator.class);
    private static final String FUNCTION_KEY = "__loadDistributorActivated";
    private static volatile boolean registered = false;

    public LoadDistributorAutoActivator() {
        autoRegister();
    }

    private static synchronized void autoRegister() {
        if (registered) {
            return;
        }

        String id = JMeterUtils.getProperty(LoadDistributor.PROP_GENERATOR_ID);
        String count = JMeterUtils.getProperty(LoadDistributor.PROP_GENERATOR_COUNT);
        String hosts = JMeterUtils.getProperty(LoadDistributor.PROP_GENERATOR_HOSTS);

        boolean hasIdCount = id != null && !id.trim().isEmpty()
                && count != null && !count.trim().isEmpty();
        boolean hasHosts = hosts != null && !hosts.trim().isEmpty();

        if (hasIdCount || hasHosts) {
            LoadDistributor distributor = new LoadDistributor();
            distributor.setName("Load Distributor (auto-activated)");
            StandardJMeterEngine.register(distributor);
            registered = true;
            if (hasHosts) {
                log.info("Load Distributor: Auto-activated via generator.hosts={}. "
                        + "No test plan element required.", hosts.trim());
            } else {
                log.info("Load Distributor: Auto-activated via properties "
                        + "(generator.id={}, generator.count={}). "
                        + "No test plan element required.", id.trim(), count.trim());
            }
        }
    }

    /**
     * Resets registration state. Called by LoadDistributor when it activates
     * from a test plan element, to prevent duplicate distribution.
     */
    static synchronized void markRegisteredByTestPlan() {
        registered = true;
    }

    /**
     * Returns whether auto-registration has occurred.
     */
    static boolean isRegistered() {
        return registered;
    }

    // ---- AbstractFunction stubs (this is not a user-callable function) ----

    @Override
    public String getReferenceKey() {
        return FUNCTION_KEY;
    }

    @Override
    public String execute(SampleResult previousResult, Sampler currentSampler) {
        return "";
    }

    @Override
    public void setParameters(Collection<CompoundVariable> parameters)
            throws InvalidVariableException {
        // No parameters
    }

    @Override
    public List<String> getArgumentDesc() {
        return Collections.emptyList();
    }
}
