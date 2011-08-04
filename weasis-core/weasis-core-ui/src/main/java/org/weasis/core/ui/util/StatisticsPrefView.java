package org.weasis.core.ui.util;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.graphic.ImageStatistics;
import org.weasis.core.ui.graphic.Measurement;

public class StatisticsPrefView extends AbstractItemDialogPage {

    public StatisticsPrefView() {
        setBorder(new EmptyBorder(15, 10, 10, 10));
        setTitle("Pixel Statistics");
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        init();
    }

    private void init() {
        for (Measurement m : ImageStatistics.ALL_MEASUREMENTS) {
            JCheckBox box = new JCheckBox(m.getName(), m.isGraphicLabel());
            this.add(box);
        }

        JPanel panel_2 = new JPanel();
        FlowLayout flowLayout_1 = (FlowLayout) panel_2.getLayout();
        flowLayout_1.setHgap(10);
        flowLayout_1.setAlignment(FlowLayout.RIGHT);
        flowLayout_1.setVgap(7);
        add(panel_2);

        JButton btnNewButton = new JButton(Messages.getString("ViewerPrefView.btnNewButton.text"));
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
        // TODO Auto-generated method stub

    }

}
