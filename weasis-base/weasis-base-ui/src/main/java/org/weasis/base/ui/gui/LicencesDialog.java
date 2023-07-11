package org.weasis.base.ui.gui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import javax.swing.AbstractAction;
import javax.swing.ButtonModel;
import javax.swing.DefaultButtonModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;
import javax.swing.text.PlainDocument;

import org.weasis.base.ui.Messages;

/**
 * @author João Bolsson (joaobolsson@animati.com.br)
 * @author Pedro Costa (pedro.costa@animati.com.br)
 * @version 2023, May 04.
 */
public class LicencesDialog extends JDialog{

    private final JTextArea textField;
    private final JScrollPane textScroll;
    private final JButton btnTest;
    private final JButton okButton;
    private final JButton cancelButton;
    private LicenseDialogController licenseDialogController;

    /**
     * Creates a dialog to insert third party licences.
     *
     * @param owner Dialog parent.
     */
    public LicencesDialog(final Frame owner) {
        super(owner, Messages.getString("LicencesDialog.title"), true);

        textField = new JTextArea();
        textField.setLineWrap(false);
        textField.setDocument(new PlainDocument());

        textScroll = new JScrollPane(textField);

        btnTest = new JButton(Messages.getString("LicencesDialog.btnTest"));
        DefaultButtonModel newModel = new DefaultButtonModel();
        newModel.setActionCommand(LicenseDialogController.TEST_COMMAND);
        newModel.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                licenseDialogController.test();
            }
        });
        btnTest.setModel(newModel);

        okButton = new JButton(Messages.getString("LicencesDialog.btnSave"));
        newModel = new DefaultButtonModel();
        newModel.setActionCommand(LicenseDialogController.OK_COMMAND);
        newModel.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                licenseDialogController.save();
            }
        });
        okButton.setModel(newModel);

        cancelButton = new JButton(Messages.getString("LicencesDialog.btnCancel"));
        newModel = new DefaultButtonModel();
        newModel.setActionCommand(LicenseDialogController.CANCEL_COMMAND);
        cancelButton.setModel(newModel);

        licenseDialogController = new LicenseDialogController(textField.getDocument(), getButtonModels(), s -> {
            if (s == LicenseDialogController.STATUS.START_PROCESSING) {
                textField.setEnabled(false);
            } else if (s == LicenseDialogController.STATUS.END_PROCESSING) {
                textField.setEnabled(true);
            }
        });

        initGUI();
        pack();

    }

    private ButtonModel[] getButtonModels() {
        Component[] components = this.getComponents();
        List<ButtonModel> bmList = new ArrayList<ButtonModel>();
        try (Stream<Component> s = Arrays.asList(components).stream()) {
            s.forEach( c -> {
                if (c instanceof JButton) {
                    bmList.add(((JButton)c).getModel());
                }
            });
        }
        return bmList.toArray(new ButtonModel[bmList.size()]);
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
        textScroll.setPreferredSize(new Dimension(400, 200));
        add(textScroll, gbc_textField);


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
