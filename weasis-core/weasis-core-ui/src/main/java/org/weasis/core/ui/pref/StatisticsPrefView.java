package org.weasis.core.ui.pref;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.graphic.ImageStatistics;
import org.weasis.core.ui.graphic.Measurement;

public class StatisticsPrefView extends AbstractItemDialogPage {
    private final Map<JCheckBox, Measurement> map = new HashMap<JCheckBox, Measurement>(
        ImageStatistics.ALL_MEASUREMENTS.length);

    public StatisticsPrefView() {
        setBorder(new EmptyBorder(15, 10, 10, 10));
        setTitle(Messages.getString("MeasureTool.pix_stats")); //$NON-NLS-1$
        init();
    }

    private void init() {

        setLayout(new BorderLayout(0, 0));

        JPanel panel = new JPanel();
        panel.setBorder(new TitledBorder(null, getTitle(), TitledBorder.LEADING, TitledBorder.TOP, null, null));
        add(panel, BorderLayout.CENTER);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        for (Measurement m : ImageStatistics.ALL_MEASUREMENTS) {
            JCheckBox box = new JCheckBox(m.getName(), m.isGraphicLabel());
            panel.add(box);
            map.put(box, m);
            box.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    Object source = e.getSource();
                    if (source instanceof JCheckBox) {
                        Measurement measure = map.get(e.getSource());
                        if (measure != null) {
                            measure.setGraphicLabel(((JCheckBox) source).isSelected());
                        }
                    }
                }
            });
        }

        JPanel panel_2 = new JPanel();
        FlowLayout flowLayout_1 = (FlowLayout) panel_2.getLayout();
        flowLayout_1.setHgap(10);
        flowLayout_1.setAlignment(FlowLayout.RIGHT);
        flowLayout_1.setVgap(7);
        add(panel_2, BorderLayout.SOUTH);

        JButton btnNewButton = new JButton(Messages.getString("restore.values")); //$NON-NLS-1$
        panel_2.add(btnNewButton);
        btnNewButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetoDefaultValues();
            }
        });
    }

    @Override
    public void closeAdditionalWindow() {

    }

    @Override
    public void resetoDefaultValues() {
        for (Measurement m : ImageStatistics.ALL_MEASUREMENTS) {
            m.resetToGraphicLabelValue();
        }
        Iterator<Entry<JCheckBox, Measurement>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Entry<JCheckBox, Measurement> entry = it.next();
            entry.getKey().setSelected(entry.getValue().isGraphicLabel());
        }
    }
}
