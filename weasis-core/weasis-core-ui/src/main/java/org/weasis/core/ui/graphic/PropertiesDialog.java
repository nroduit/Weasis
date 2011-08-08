package org.weasis.core.ui.graphic;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;

import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.ui.Messages;

public abstract class PropertiesDialog extends JDialog {
    private final JPanel panel1 = new JPanel();
    private final BorderLayout borderLayout1 = new BorderLayout();

    private final JPanel jPanelFooter = new JPanel();
    private final JButton jButtonOk = new JButton();
    private final JButton jButtonCancel = new JButton();
    private final GridBagLayout gridBagLayout2 = new GridBagLayout();
    private final JPanel jPanel1 = new JPanel();
    private final GridBagLayout gridBagLayout1 = new GridBagLayout();
    protected final JSpinner spinnerLineWidth = new JSpinner();
    protected final JLabel jLabelLineWidth = new JLabel();
    protected final JLabel jLabelLineColor = new JLabel();
    protected final JButton jButtonColor = new JButton();
    protected final JCheckBox jCheckBoxFilled = new JCheckBox();
    protected final JLabel lbloverridesmultipleValues = new JLabel(
        Messages.getString("PropertiesDialog.header_override")); //$NON-NLS-1$
    protected final JCheckBox checkBox_color = new JCheckBox();
    protected final JCheckBox checkBox_width = new JCheckBox();
    protected final JCheckBox checkBox_fill = new JCheckBox();

    public PropertiesDialog(Window parent, String title) {
        super(parent, title, ModalityType.APPLICATION_MODAL);
        try {
            jbInit();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void jbInit() throws Exception {
        panel1.setLayout(borderLayout1);
        jButtonOk.setText(Messages.getString("PropertiesDialog.ok")); //$NON-NLS-1$
        jButtonOk.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                okAction();
            }
        });
        jButtonCancel.setText(Messages.getString("PropertiesDialog.cancel")); //$NON-NLS-1$
        jButtonCancel.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                quitWithoutSaving();
            }
        });
        jPanelFooter.setLayout(gridBagLayout2);

        jPanel1.setLayout(gridBagLayout1);
        JMVUtils.setNumberModel(spinnerLineWidth, 1, 1, 8, 1);
        jLabelLineWidth.setText(Messages.getString("PropertiesDialog.line_width")); //$NON-NLS-1$
        jLabelLineColor.setText(Messages.getString("PropertiesDialog.line_color")); //$NON-NLS-1$
        jButtonColor.setText(Messages.getString("MeasureTool.pick")); //$NON-NLS-1$

        jButtonColor.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JButton button = (JButton) e.getSource();
                Color newColor =
                    JColorChooser.showDialog(PropertiesDialog.this,
                        Messages.getString("MeasureTool.pick_color"), button.getBackground()); //$NON-NLS-1$
                if (newColor != null) {
                    button.setBackground(newColor);
                }
            }
        });

        jCheckBoxFilled.setText(Messages.getString("PropertiesDialog.fill_shape")); //$NON-NLS-1$
        getContentPane().add(panel1);
        panel1.add(jPanelFooter, BorderLayout.SOUTH);
        jPanelFooter.add(jButtonCancel, new GridBagConstraints(1, 1, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER,
            GridBagConstraints.NONE, new Insets(30, 15, 15, 15), 0, 0));
        jPanelFooter.add(jButtonOk, new GridBagConstraints(0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER,
            GridBagConstraints.NONE, new Insets(30, 15, 15, 15), 0, 0));
        panel1.add(jPanel1, BorderLayout.CENTER);

        GridBagConstraints gbc_lbloverridesmultipleValues = new GridBagConstraints();
        gbc_lbloverridesmultipleValues.insets = new Insets(15, 10, 0, 25);
        gbc_lbloverridesmultipleValues.gridx = 2;
        gbc_lbloverridesmultipleValues.gridy = 0;
        jPanel1.add(lbloverridesmultipleValues, gbc_lbloverridesmultipleValues);
        jPanel1.add(jLabelLineColor, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.EAST,
            GridBagConstraints.NONE, new Insets(10, 25, 0, 0), 0, 0));
        jPanel1.add(jButtonColor, new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, new Insets(10, 2, 0, 0), 0, 0));

        GridBagConstraints gbc_checkBox_color = new GridBagConstraints();
        gbc_checkBox_color.insets = new Insets(10, 0, 0, 0);
        gbc_checkBox_color.gridx = 2;
        gbc_checkBox_color.gridy = 1;
        checkBox_color.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JCheckBox box = (JCheckBox) e.getSource();
                jButtonColor.setEnabled(box.isSelected());
            }
        });
        checkBox_width.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JCheckBox box = (JCheckBox) e.getSource();
                spinnerLineWidth.setEnabled(box.isSelected());
            }
        });
        checkBox_fill.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JCheckBox box = (JCheckBox) e.getSource();
                jCheckBoxFilled.setEnabled(box.isSelected());
            }
        });
        jPanel1.add(checkBox_color, gbc_checkBox_color);
        jPanel1.add(jLabelLineWidth, new GridBagConstraints(0, 2, 1, 1, 0.0, 0.0, GridBagConstraints.EAST,
            GridBagConstraints.NONE, new Insets(10, 25, 0, 0), 0, 0));
        jPanel1.add(spinnerLineWidth, new GridBagConstraints(1, 2, 1, 1, 0.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, new Insets(10, 2, 0, 0), 0, 0));

        GridBagConstraints gbc_checkBox_width = new GridBagConstraints();
        gbc_checkBox_width.insets = new Insets(10, 0, 0, 0);
        gbc_checkBox_width.gridx = 2;
        gbc_checkBox_width.gridy = 2;
        jPanel1.add(checkBox_width, gbc_checkBox_width);
        jPanel1.add(jCheckBoxFilled, new GridBagConstraints(0, 3, 2, 1, 0.0, 0.0, GridBagConstraints.CENTER,
            GridBagConstraints.NONE, new Insets(10, 0, 0, 0), 0, 0));

        GridBagConstraints gbc_checkBox_fill = new GridBagConstraints();
        gbc_checkBox_fill.insets = new Insets(10, 0, 0, 0);
        gbc_checkBox_fill.gridx = 2;
        gbc_checkBox_fill.gridy = 3;
        jPanel1.add(checkBox_fill, gbc_checkBox_fill);
    }

    // Overridden so we can exit when window is closed
    @Override
    protected void processWindowEvent(WindowEvent e) {
        if (e.getID() == WindowEvent.WINDOW_CLOSING) {
            quitWithoutSaving();
        }
        super.processWindowEvent(e);
    }

    protected abstract void okAction();

    protected void quitWithoutSaving() {
        dispose();
    }

}
