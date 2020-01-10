/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.explorer.gui.control;

import java.awt.Dimension;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.SwingWorker.StateValue;

import org.dcm4che3.net.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.Messages;
import org.weasis.acquire.explorer.PublishDicomTask;
import org.weasis.acquire.explorer.gui.dialog.AcquirePublishDialog;
import org.weasis.core.api.gui.task.CircularProgressBar;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.api.util.ThreadUtil;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomState;

@SuppressWarnings("serial")
public class AcquirePublishPanel extends JPanel {
    private static final Logger LOGGER = LoggerFactory.getLogger(AcquirePublishPanel.class);

    private final JButton publishBtn = new JButton(Messages.getString("AcquirePublishPanel.publish")); //$NON-NLS-1$
    private final CircularProgressBar progressBar = new CircularProgressBar(0, 100);

    public static final ExecutorService PUBLISH_DICOM = ThreadUtil.buildNewSingleThreadExecutor("Publish Dicom"); //$NON-NLS-1$

    public AcquirePublishPanel() {
        // setBorder(new TitledBorder(null, "Publish", TitledBorder.LEADING, TitledBorder.TOP, null, null));

        publishBtn.addActionListener(e -> {
            final AcquirePublishDialog dialog = new AcquirePublishDialog(AcquirePublishPanel.this);
            JMVUtils.showCenterScreen(dialog, WinUtil.getParentWindow(AcquirePublishPanel.this));
        });

        publishBtn.setPreferredSize(new Dimension(150, 40));
        publishBtn.setFont(FontTools.getFont12Bold());

        add(publishBtn);
        add(progressBar);

        progressBar.setVisible(false);
    }

    public void publishDirDicom(File exportDirDicom, DicomNode destinationNode) {

        SwingWorker<DicomState, File> publishDicomTask = new PublishDicomTask(exportDirDicom, destinationNode);
        publishDicomTask.addPropertyChangeListener(this::publishChanged);

        PUBLISH_DICOM.execute(publishDicomTask);
    }

    private void publishChanged(PropertyChangeEvent evt) {
        if ("progress".equals(evt.getPropertyName())) { //$NON-NLS-1$
            int progress = (Integer) evt.getNewValue();
            progressBar.setValue(progress);

        } else if ("state".equals(evt.getPropertyName())) { //$NON-NLS-1$
            if (StateValue.STARTED == evt.getNewValue()) {
                publishBtn.setEnabled(false);
                progressBar.setVisible(true);
                progressBar.setValue(0);
            } else if (StateValue.DONE == evt.getNewValue()) {
                try {
                    SwingWorker<DicomState, File> publishDicomTask = (SwingWorker<DicomState, File>) evt.getSource();
                    final DicomState dicomState = publishDicomTask.get();
                    if (dicomState.getStatus() != Status.Success && dicomState.getStatus() != Status.Cancel) {
                        LOGGER.error("Dicom send error: {}", dicomState.getMessage()); //$NON-NLS-1$
                        JOptionPane.showMessageDialog(WinUtil.getParentWindow(AcquirePublishPanel.this),
                            dicomState.getMessage(), null, // $NON-NLS-1$
                            JOptionPane.ERROR_MESSAGE);
                    }
                } catch (InterruptedException e) {
                    LOGGER.warn("Retrieving task Interruption"); //$NON-NLS-1$
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    LOGGER.error("Retrieving task", e); //$NON-NLS-1$
                }
                publishBtn.setEnabled(true);
                progressBar.setVisible(false);
            }
        }
    }
}
