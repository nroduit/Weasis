package org.weasis.core.ui.graphic;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;

import org.weasis.core.api.gui.util.JMVUtils;

public abstract class PropertiesDialog extends JDialog {
    private JPanel panel1 = new JPanel();
    private BorderLayout borderLayout1 = new BorderLayout();

    private JPanel jPanelFooter = new JPanel();
    private JButton jButtonOk = new JButton();
    private JButton jButtonCancel = new JButton();
    private GridBagLayout gridBagLayout2 = new GridBagLayout();
    private JPanel jPanel1 = new JPanel();
    private GridBagLayout gridBagLayout1 = new GridBagLayout();
    protected JSpinner jPVSpinLineWidth = new JSpinner();
    private JLabel jLabelLineWidth1 = new JLabel();
    protected JLabel jLabelLineColor1 = new JLabel();
    protected JButton jPVButtonColor = new JButton();
    protected JCheckBox jCheckBoxFilled = new JCheckBox();

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
        jButtonOk.setText("OK");
        jButtonOk.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                jButtonOk_actionPerformed(e);
            }
        });
        jButtonCancel.setText("Cancel");
        jButtonCancel.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                jButtonCancel_actionPerformed(e);
            }
        });
        jPanelFooter.setLayout(gridBagLayout2);

        jPanel1.setLayout(gridBagLayout1);
        JMVUtils.setNumberModel(jPVSpinLineWidth, 1, 1, 8, 1);
        jLabelLineWidth1.setText("Line Width :");
        jLabelLineColor1.setText("Line Color :");
        jPVButtonColor.setText("Pick");

        jPVButtonColor.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JButton button = (JButton) e.getSource();
                Color newColor =
                    JColorChooser.showDialog(PropertiesDialog.this, "Pick a color", button.getBackground());
                if (newColor != null) {
                    button.setBackground(newColor);
                }
            }
        });

        jCheckBoxFilled.setOpaque(false);
        jCheckBoxFilled.setText("Fill shape");
        getContentPane().add(panel1);
        panel1.add(jPanelFooter, BorderLayout.SOUTH);
        jPanelFooter.add(jButtonCancel, new GridBagConstraints(1, 1, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER,
            GridBagConstraints.NONE, new Insets(30, 15, 15, 15), 0, 0));
        jPanelFooter.add(jButtonOk, new GridBagConstraints(0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER,
            GridBagConstraints.NONE, new Insets(30, 15, 15, 15), 0, 0));
        panel1.add(jPanel1, BorderLayout.CENTER);
        jPanel1.add(jLabelLineColor1, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.EAST,
            GridBagConstraints.NONE, new Insets(24, 25, 0, 0), 0, 0));
        jPanel1.add(jPVButtonColor, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, new Insets(25, 2, 0, 25), 0, 0));
        jPanel1.add(jLabelLineWidth1, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, new Insets(10, 25, 0, 0), 0, 0));
        jPanel1.add(jPVSpinLineWidth, new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, new Insets(10, 2, 0, 25), 0, 0));
        jPanel1.add(jCheckBoxFilled, new GridBagConstraints(0, 2, 2, 1, 0.0, 0.0, GridBagConstraints.CENTER,
            GridBagConstraints.NONE, new Insets(10, 0, 0, 0), 0, 0));
    }

    // Overridden so we can exit when window is closed
    @Override
    protected void processWindowEvent(WindowEvent e) {
        if (e.getID() == WindowEvent.WINDOW_CLOSING) {
            closeWin();
        }
        super.processWindowEvent(e);
    }

    // Close the dialog
    private void closeWin() {
        if (hasChanged()) {
            int response =
                JOptionPane.showConfirmDialog(this, "Do you want to save changes ?", "Save Changes",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (response == 0) {
                okAction();
            } else if (response == 1) {
                quitWithoutSaving();
            }
        } else {
            quitWithoutSaving();
        }
    }

    /**
     * hasChanged
     * 
     * @return boolean
     */
    protected abstract boolean hasChanged();

    protected abstract void okAction();

    protected void quitWithoutSaving() {
        dispose();
    }

    private void jButtonOk_actionPerformed(ActionEvent e) {
        okAction();
    }

    private void jButtonCancel_actionPerformed(ActionEvent e) {
        quitWithoutSaving();
    }

}
