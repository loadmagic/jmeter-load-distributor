/*
 * Licensed under the Apache License, Version 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package ai.loadmagic.jmeter.distributed;

import org.apache.jmeter.config.gui.AbstractConfigGui;
import org.apache.jmeter.testelement.TestElement;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * GUI for the Distributed Load Throttler.
 *
 * <p>This is a configuration-free element — all settings come from JMeter
 * properties at runtime. The GUI just explains usage.</p>
 */
public class LoadThrottlerGui extends AbstractConfigGui {

    private static final long serialVersionUID = 1L;

    public LoadThrottlerGui() {
        init();
    }

    private void init() {
        setLayout(new BorderLayout(0, 10));
        setBorder(makeBorder());
        add(makeTitlePanel(), BorderLayout.NORTH);
        add(createInfoPanel(), BorderLayout.CENTER);
    }

    private JPanel createInfoPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new TitledBorder("Distributed Load Distribution"));

        JTextArea info = new JTextArea();
        info.setEditable(false);
        info.setLineWrap(true);
        info.setWrapStyleWord(true);
        info.setBackground(panel.getBackground());
        info.setFont(info.getFont().deriveFont(Font.PLAIN, 13f));
        info.setText(
            "This element automatically distributes thread counts across "
            + "JMeter generators for distributed testing.\n\n"
            + "HOW TO USE:\n\n"
            + "1. Add this element to your Test Plan (top level)\n"
            + "2. Configure your ThreadGroups with the TOTAL desired load\n"
            + "3. Start each generator with these properties:\n\n"
            + "   jmeter -Jgenerator.id=1 -Jgenerator.count=3 -t test.jmx\n"
            + "   jmeter -Jgenerator.id=2 -Jgenerator.count=3 -t test.jmx\n"
            + "   jmeter -Jgenerator.id=3 -Jgenerator.count=3 -t test.jmx\n\n"
            + "Each generator automatically calculates its fair share.\n\n"
            + "EXAMPLE:\n"
            + "  ThreadGroup with 100 threads, 3 generators:\n"
            + "    Generator 1 → 34 threads\n"
            + "    Generator 2 → 33 threads\n"
            + "    Generator 3 → 33 threads\n"
            + "    Total = 100 (exact)\n\n"
            + "SUPPORTED THREAD GROUPS:\n"
            + "  • Standard ThreadGroup\n"
            + "  • Ultimate Thread Group (per-row distribution)\n"
            + "  • Concurrency Thread Group\n"
            + "  • Stepping Thread Group\n\n"
            + "NOTES:\n"
            + "  • Without -Jgenerator.id and -Jgenerator.count, runs full load\n"
            + "  • Thread counts are modified in memory only — your .jmx is never changed\n"
            + "  • Ramp-up timings are preserved (each generator ramps its share\n"
            + "    over the full ramp-up duration)\n"
            + "  • If generators > threads, excess generators sit idle (0 threads)"
        );

        panel.add(new JScrollPane(info), BorderLayout.CENTER);
        return panel;
    }

    @Override
    public String getLabelResource() {
        return getClass().getSimpleName();
    }

    @Override
    public String getStaticLabel() {
        return "Distributed Load Throttler";
    }

    @Override
    public TestElement createTestElement() {
        LoadThrottler throttler = new LoadThrottler();
        modifyTestElement(throttler);
        return throttler;
    }

    @Override
    public void modifyTestElement(TestElement element) {
        configureTestElement(element);
    }
}
