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
 * GUI for the Distributed Load Distributor.
 *
 * <p>Configuration-free element — all settings come from JMeter properties
 * at runtime. The GUI explains usage.</p>
 */
public class LoadDistributorGui extends AbstractConfigGui {

    private static final long serialVersionUID = 1L;

    public LoadDistributorGui() {
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
            "Automatically distributes thread counts across JMeter generators.\n\n"
            + "HOW TO USE:\n\n"
            + "1. Add this element to your Test Plan (top level)\n"
            + "2. Configure your ThreadGroups with the TOTAL desired load\n"
            + "3. Start each generator with:\n\n"
            + "   jmeter -Jgenerator.id=1 -Jgenerator.count=3 -n -t test.jmx\n"
            + "   jmeter -Jgenerator.id=2 -Jgenerator.count=3 -n -t test.jmx\n"
            + "   jmeter -Jgenerator.id=3 -Jgenerator.count=3 -n -t test.jmx\n\n"
            + "Each generator calculates its exact share. The sum always\n"
            + "equals the original total — no rounding errors.\n\n"
            + "EXAMPLE:\n"
            + "  100 threads across 3 generators:\n"
            + "    Generator 1: 34 | Generator 2: 33 | Generator 3: 33\n\n"
            + "SUPPORTED:\n"
            + "  Standard ThreadGroup | Ultimate Thread Group (per-row)\n"
            + "  Concurrency Thread Group | Stepping Thread Group\n\n"
            + "NOTES:\n"
            + "  Without -Jgenerator.id and -Jgenerator.count, full load runs.\n"
            + "  Thread counts are modified in memory only — .jmx unchanged.\n"
            + "  Timings (ramp-up, hold, shutdown) are preserved."
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
        return "Distributed Load Distributor";
    }

    @Override
    public TestElement createTestElement() {
        LoadDistributor distributor = new LoadDistributor();
        modifyTestElement(distributor);
        return distributor;
    }

    @Override
    public void modifyTestElement(TestElement element) {
        configureTestElement(element);
    }
}
