package org.weasis.base.ui.gui;

import org.weasis.base.ui.Messages;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowEvent;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;

/**
 * @author João Bolsson (joaobolsson@animati.com.br)
 * @author Pedro Costa (pedro.costa@animati.com.br)
 * @version 2023, May 04.
 */
public class LicencesDialog extends JDialog{

    private final JTextArea textField;
    private final JButton btnTest;
    private final JButton okButton;
    private final JButton cancelButton;

    /**
     * Creates a dialog to insert third party licences.
     *
     * @param owner Dialog parent.
     */
    public LicencesDialog(final Frame owner) {
        super(owner, Messages.getString("LicencesDialog.title"), true);

        textField = new JTextArea();
        textField.setLineWrap(true);
        btnTest = new JButton(Messages.getString("LicencesDialog.btnTest"));
        okButton = new JButton(Messages.getString("LicencesDialog.btnSave"));
        cancelButton = new JButton(Messages.getString("LicencesDialog.btnCancel"));

        initGUI();
        pack();
    }

    private void initGUI() {
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        GridBagLayout gridBagLayout = new GridBagLayout();
        setLayout(gridBagLayout);
        setSize(new Dimension(500, 350));

        GridBagConstraints gbc_textField = new GridBagConstraints();
        gbc_textField.anchor = GridBagConstraints.LINE_START;
        gbc_textField.insets = new Insets(5, 5, 5, 5);
        gbc_textField.fill = GridBagConstraints.HORIZONTAL;
        gbc_textField.gridx = 1;
        gbc_textField.gridy = 0;
        textField.setPreferredSize(new Dimension(400, 200));
        add(textField, gbc_textField);


        GridBagConstraints gbc_btnNewButton = new GridBagConstraints();
        gbc_btnNewButton.gridwidth = 3;
        gbc_btnNewButton.anchor = GridBagConstraints.WEST;
        gbc_btnNewButton.insets = new Insets(5, 5, 5, 5);
        gbc_btnNewButton.gridx = 0;
        gbc_btnNewButton.gridy = 1;
        add(btnTest, gbc_btnNewButton);
        //btnTest.addActionListener(e -> testAction());

        //okButton.addActionListener(e -> okAction());

        cancelButton.addActionListener(e -> close());

        GridBagConstraints gbc_btnOptions = new GridBagConstraints();
        gbc_btnOptions.gridwidth = 2;
        gbc_btnOptions.weightx = 1;
        gbc_btnOptions.anchor = GridBagConstraints.EAST;
        gbc_btnOptions.insets = new Insets(5, 5, 5, 5);
        gbc_btnOptions.gridx = 0;
        gbc_btnOptions.gridy = 1;

        JPanel optionsButtons = new JPanel();
        optionsButtons.add(cancelButton);
        optionsButtons.add(okButton);

        add(optionsButtons, gbc_btnOptions);
    }

    // Overridden so we can exit when window is closed
    @Override
    protected void processWindowEvent(WindowEvent e) {
        if (WindowEvent.WINDOW_CLOSING == e.getID()) {
            close();
        }
        super.processWindowEvent(e);
    }

    private void close() {
        dispose();
    }

}
