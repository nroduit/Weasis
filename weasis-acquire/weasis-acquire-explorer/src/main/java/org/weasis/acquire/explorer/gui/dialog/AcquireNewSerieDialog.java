/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.explorer.gui.dialog;

import java.awt.BorderLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.Messages;
import org.weasis.acquire.explorer.core.bean.SeriesGroup;
import org.weasis.acquire.explorer.gui.central.AcquireTabPanel;
import org.weasis.core.api.media.data.ImageElement;

@SuppressWarnings("serial")
public class AcquireNewSerieDialog extends JDialog implements PropertyChangeListener {
    private final JTextField serieName = new JTextField();
    private JOptionPane optionPane;

    private AcquireTabPanel acquireTabPanel;
    private List<ImageElement> medias;

    public AcquireNewSerieDialog(AcquireTabPanel acquireTabPanel, final List<ImageElement> medias) {
        this.acquireTabPanel = acquireTabPanel;
        this.medias = medias;
        optionPane = new JOptionPane(initPanel(), JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION, null,
            AcquireImportDialog.OPTIONS, AcquireImportDialog.OPTIONS[0]);
        optionPane.addPropertyChangeListener(this);

        setContentPane(optionPane);
        setModal(true);
        pack();
    }

    private JPanel initPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        JLabel question = new JLabel(Messages.getString("AcquireNewSerieDialog.enter_name")); //$NON-NLS-1$
        panel.add(question, BorderLayout.NORTH);

        panel.add(serieName, BorderLayout.CENTER);

        return panel;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        Object action = evt.getNewValue();
        boolean close = true;
        if (action != null) {
            if (AcquireImportDialog.OPTIONS[0].equals(action)) {
                if (serieName.getText() != null && !serieName.getText().isEmpty()) {
                    acquireTabPanel.moveElements(new SeriesGroup(serieName.getText()),
                        AcquireManager.toAcquireImageInfo(medias));
                } else {
                    JOptionPane.showMessageDialog(this, Messages.getString("AcquireImportDialog.add_name_msg"), //$NON-NLS-1$
                        Messages.getString("AcquireImportDialog.add_name_title"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
                    optionPane.setValue(AcquireImportDialog.REVALIDATE);
                    close = false;
                }
            } else if (action.equals(AcquireImportDialog.REVALIDATE)) {
                close = false;
            }
            if (close) {
                clearAndHide();
            }
        }
    }

    public void clearAndHide() {
        serieName.setText(null);
        setVisible(false);
    }

}
