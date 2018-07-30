/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */

package br.com.animati.texture.mpr3dview;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

import org.weasis.core.api.gui.util.AbstractItemDialogPage;

import br.com.animati.texture.mpr3dview.internal.Messages;

/**
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2013, 25 Nov.
 */
public class HaPrefsPage extends AbstractItemDialogPage {

    public static final String PAGE_NAME = "Hardware Acceleration";

    public HaPrefsPage() {
        super(PAGE_NAME);
        setComponentPosition(10);
        setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        add(buildHAPanel(), gbc);

        gbc.gridy++;
        add(buildInfoPanel(), gbc);

        validate();
    }

    @Override
    public void resetoDefaultValues() {
        // Do nothing
    }

    @Override
    public void closeAdditionalWindow() {
        // Do nothing
    }

    private void saveHa(boolean selected) {
        View3DFactory.localConfiguration.putBooleanProperty(View3DFactory.HA_PROP_NAME, selected);
        if (selected) {
            // Set that flag to false, so we will try again to create context,
            // as the user is asking to activate HA.
            View3DFactory.localConfiguration.putBooleanProperty(View3DFactory.CRASH_FLAG, false);
        }
    }

    private void saveCL(boolean selected) {
        View3DFactory.localConfiguration.putBooleanProperty(View3DFactory.CL_PROP_NAME, selected);
    }

    private JPanel buildHAPanel() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(10, 5, 5, 5);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(
            BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), Messages.getString("HaPrefsPage.HA")));
        panel.setPreferredSize(new Dimension(630, 100));

        final JCheckBox enableHA = new JCheckBox(Messages.getString("HaPrefsPage.enableHA"));
        enableHA.setSelected(View3DFactory.isUseHardwareAcceleration());
        panel.add(enableHA, gbc);

        gbc.gridx++;
        final JCheckBox enableOpenCL = new JCheckBox(Messages.getString("HaPrefsPage.enableOpenCL"));
        enableOpenCL.setEnabled(View3DFactory.isUseHardwareAcceleration());
        enableOpenCL.setSelected(View3DFactory.isUseHardwareAcceleration() && View3DFactory.isUseOpenCL());
        panel.add(enableOpenCL, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        panel.add(new JLabel(Messages.getString("HaPrefsPage.mustRestart")), gbc);

        enableHA.addActionListener(e -> {
            boolean selected = enableHA.isSelected();
            saveHa(selected);

            enableOpenCL.setEnabled(selected);
            enableOpenCL.setSelected(selected);
        });

        enableOpenCL.addActionListener(e -> saveCL(enableOpenCL.isSelected()));

        return panel;
    }

    private Component buildInfoPanel() {
        final JPanel info = new JPanel();
        info.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(),
            Messages.getString("HaPrefsPage.info")));
        info.setPreferredSize(new Dimension(540, 120));

        JTextArea label = new JTextArea(View3DFactory.CONFIG_HARDWARE);
        label.setPreferredSize(new Dimension(530, 70));
        label.setEditable(false);
        label.setLineWrap(true);

        info.add(label);

        return info;
    }

}
