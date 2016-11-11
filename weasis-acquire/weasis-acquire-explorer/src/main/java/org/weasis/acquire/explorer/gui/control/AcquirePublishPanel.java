/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.acquire.explorer.gui.control;

import java.awt.Dimension;
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
import org.weasis.dicom.param.DicomState;

public class AcquirePublishPanel extends JPanel {
    private static final long serialVersionUID = 7909124238543156489L;

    public static final Logger LOGGER = LoggerFactory.getLogger(AcquirePublishPanel.class);

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

    public void publishDirDicom(File exportDirDicom) {

        SwingWorker<DicomState, File> publishDicomTask = new PublishDicomTask(exportDirDicom);
        publishDicomTask.addPropertyChangeListener(evt -> {
            if ("progress" == evt.getPropertyName()) { //$NON-NLS-1$
                int progress = (Integer) evt.getNewValue();
                progressBar.setValue(progress);

            } else if ("state" == evt.getPropertyName()) { //$NON-NLS-1$

                if (StateValue.STARTED == evt.getNewValue()) {
                    publishBtn.setEnabled(false);
                    progressBar.setVisible(true);
                    progressBar.setValue(0);

                } else if (StateValue.DONE == evt.getNewValue()) {
                    try {
                        final DicomState dicomState = publishDicomTask.get();

                        if (dicomState.getStatus() != Status.Success) {
                            LOGGER.error("Dicom send error: {}", dicomState.getMessage()); //$NON-NLS-1$

                            JOptionPane.showOptionDialog(WinUtil.getParentWindow(AcquirePublishPanel.this),
                                String.format("Dicom send error: %s", dicomState.getMessage()), null, //$NON-NLS-1$
                                JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, null, null);
                        }
                    } catch (InterruptedException | ExecutionException doNothing) {
                    }
                    publishBtn.setEnabled(true);
                    progressBar.setVisible(false);
                }

            }
        });

        PUBLISH_DICOM.execute(publishDicomTask);
    }

    // public void publishForTest(List<AcquireImageInfo> toPublish) {
    // File exportDirImage =
    // FileUtil.createTempDir(AppProperties.buildAccessibleTempDirectory("tmp", "acquire", "img"));
    // File exportDirXml = FileUtil.createTempDir(AppProperties.buildAccessibleTempDirectory("tmp", "acquire", "xml"));
    //
    // toPublish.stream().forEach(imageInfo -> {
    // writteJpeg(exportDirImage, imageInfo);
    // writeXml(exportDirXml, imageInfo);
    // });
    // }
    //
    // private void writeXml(File exportDirXml, AcquireImageInfo imgInfo) {
    // ImageElement img = imgInfo.getImage();
    // GraphicModel graphicManager = (GraphicModel) img.getTagValue(TagW.PresentationModel);
    //
    // if (Objects.nonNull(graphicManager)) {
    // XmlSerializer.writePresentation(img, new File(exportDirXml, img.getName()));
    // }
    // }
    //
    // private void writteJpeg(File exportDirImage, AcquireImageInfo imageInfo) {
    // ImageElement img = imageInfo.getImage();
    // TagW tagUid = TagD.getUID(Level.INSTANCE);
    // String uid = (String) img.getTagValue(tagUid);
    //
    // if (!imageInfo.getCurrentValues().equals(imageInfo.getDefaultValues())) {
    // File imgFile = new File(exportDirImage, uid + ".jpg");
    // PlanarImage transformedImage = img.getImage(imageInfo.getPostProcessOpManager(), false);
    //
    // if (!ImageFiler.writeJPG(imgFile, transformedImage, 0.8f)) {
    // // out of memory
    // imgFile.delete();
    // }
    // }
    // }

}
