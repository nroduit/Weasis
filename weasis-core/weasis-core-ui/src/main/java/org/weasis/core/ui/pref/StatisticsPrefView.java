/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.ui.pref;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.model.utils.ImageStatistics;
import org.weasis.core.ui.model.utils.bean.Measurement;

@SuppressWarnings("serial")
public class StatisticsPrefView extends AbstractItemDialogPage {

    private final Map<JCheckBox, Measurement> map = new HashMap<>(ImageStatistics.ALL_MEASUREMENTS.length);

    public StatisticsPrefView() {
        super(Messages.getString("MeasureTool.pix_stats")); //$NON-NLS-1$
        setComponentPosition(10);
        setBorder(new EmptyBorder(15, 10, 10, 10));
        init();
    }

    private void init() {

        setLayout(new BorderLayout(0, 0));

        JPanel panel = new JPanel();
        panel.setBorder(new TitledBorder(null, getTitle(), TitledBorder.LEADING, TitledBorder.TOP, null, null));
        add(panel, BorderLayout.CENTER);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        for (Measurement m : ImageStatistics.ALL_MEASUREMENTS) {
            JCheckBox box = new JCheckBox(m.getName(), m.getGraphicLabel());
            panel.add(box);
            map.put(box, m);
            box.addActionListener(e -> {
                Object source = e.getSource();
                if (source instanceof JCheckBox) {
                    Measurement measure = map.get(e.getSource());
                    if (measure != null) {
                        measure.setGraphicLabel(((JCheckBox) source).isSelected());
                    }
                }
            });
        }

        JPanel panel2 = new JPanel();
        FlowLayout flowLayout1 = (FlowLayout) panel2.getLayout();
        flowLayout1.setHgap(10);
        flowLayout1.setAlignment(FlowLayout.RIGHT);
        flowLayout1.setVgap(7);
        add(panel2, BorderLayout.SOUTH);

        JButton btnNewButton = new JButton(Messages.getString("restore.values")); //$NON-NLS-1$
        panel2.add(btnNewButton);
        btnNewButton.addActionListener(e -> resetoDefaultValues());
    }

    @Override
    public void closeAdditionalWindow() {
        // Do nothing
    }

    @Override
    public void resetoDefaultValues() {
        Arrays.stream(ImageStatistics.ALL_MEASUREMENTS).forEach(Measurement::resetToGraphicLabelValue);
        map.entrySet().forEach(entry -> entry.getKey().setSelected(entry.getValue().getGraphicLabel()));
    }
}
