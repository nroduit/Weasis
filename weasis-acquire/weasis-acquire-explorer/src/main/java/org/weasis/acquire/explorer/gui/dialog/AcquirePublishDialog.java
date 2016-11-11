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
package org.weasis.acquire.explorer.gui.dialog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.media.jai.PlanarImage;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;
import javax.swing.SwingWorker.StateValue;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.DicomizeTask;
import org.weasis.acquire.explorer.Messages;
import org.weasis.acquire.explorer.gui.control.AcquirePublishPanel;
import org.weasis.acquire.explorer.gui.model.publish.PublishTree;
import org.weasis.acquire.explorer.util.ImageInfoHelper;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.image.ZoomOp;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.api.util.ThreadUtil;

public class AcquirePublishDialog extends JDialog {
    private static final long serialVersionUID = -8736946182228791444L;

    public static final Logger LOGGER = LoggerFactory.getLogger(AcquirePublishDialog.class);

    public static final Integer MAX_RESOLUTION_THRESHOLD = 3000; // in pixels

    private final AcquirePublishPanel publishPanel;

    private PublishTree publishTree;
    private JPanel resolutionPane;
    private JComboBox<EResolution> resolutionCombo;
    private JButton publishButton;
    private JButton cancelButton;
    private JProgressBar progressBar;

    private ActionListener clearAndHideActionListener;

    public AcquirePublishDialog(AcquirePublishPanel publishPanel) {
        super(WinUtil.getParentWindow(publishPanel), "", ModalityType.APPLICATION_MODAL); //$NON-NLS-1$
        this.publishPanel = publishPanel;

        setContentPane(initContent());
        publishTree.getTree().addCheckingPath(new TreePath(publishTree.getModel().getRootNode().getPath()));

        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent we) {
                cancelButton.doClick();
            }
        });

        setPreferredSize(new Dimension(700, 400));
        pack();
    }

    private JPanel initContent() {
        JPanel contentPane = new JPanel();

        contentPane.setBorder(new EmptyBorder(10, 10, 10, 10));
        contentPane.setLayout(new BorderLayout());

        JLabel questionLabel = new JLabel(Messages.getString("AcquirePublishDialog.select_pub")); //$NON-NLS-1$
        questionLabel.setFont(FontTools.getFont12Bold());

        contentPane.add(questionLabel, BorderLayout.NORTH);

        JPanel imageTreePane = new JPanel(new BorderLayout());
        imageTreePane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        publishTree = new PublishTree();
        publishTree.addTreeCheckingListener(evt -> {
            resolutionPane.setVisible(!getOversizedSelected(publishTree).isEmpty());
            resolutionPane.repaint();
        });
        publishTree.setMinimumSize(publishTree.getPreferredSize());
        imageTreePane.add(publishTree);

        contentPane.add(imageTreePane, BorderLayout.CENTER);

        JPanel actionPane = new JPanel(new BorderLayout());
        actionPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        resolutionPane = new JPanel();
        resolutionPane.setBorder(BorderFactory.createLineBorder(Color.BLACK));

        JLabel resolutionLabel =
            new JLabel(Messages.getString("AcquirePublishDialog.resolution") + StringUtil.COLON_AND_SPACE); //$NON-NLS-1$
        resolutionPane.add(resolutionLabel);

        resolutionCombo = new JComboBox<>(EResolution.values());
        resolutionPane.add(resolutionCombo);
        resolutionPane.setVisible(Boolean.FALSE);

        actionPane.add(resolutionPane, BorderLayout.NORTH);

        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);

        actionPane.add(progressBar, BorderLayout.CENTER);

        JPanel buttonPane = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));

        publishButton = new JButton(Messages.getString("AcquirePublishDialog.publish")); //$NON-NLS-1$
        publishButton.addActionListener(e -> publishAction());

        cancelButton = new JButton(Messages.getString("AcquirePublishDialog.cancel")); //$NON-NLS-1$
        cancelButton.addActionListener(clearAndHideActionListener = e -> clearAndHide());

        buttonPane.add(publishButton);
        buttonPane.add(cancelButton);

        actionPane.add(buttonPane, BorderLayout.SOUTH);

        contentPane.add(actionPane, BorderLayout.SOUTH);

        return contentPane;
    }

    private void publishAction() {
        List<AcquireImageInfo> toPublish = getSelectedImages(publishTree);

        if (toPublish.isEmpty()) {
            JOptionPane.showMessageDialog(this, Messages.getString("AcquirePublishDialog.select_one_msg"), //$NON-NLS-1$
                "", JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
            return;
        }

        List<AcquireImageInfo> overSizedSelected = getOversizedSelected(publishTree);
        if (!overSizedSelected.isEmpty()) {
            for (AcquireImageInfo imgInfo : overSizedSelected) {
                // caculate zoom ration
                Double ratio = ImageInfoHelper.calculateRatio(imgInfo, (EResolution) resolutionCombo.getSelectedItem(),
                    MAX_RESOLUTION_THRESHOLD.doubleValue());

                imgInfo.getCurrentValues().setRatio(ratio);
                imgInfo.getPostProcessOpManager().setParamValue(ZoomOp.OP_NAME, ZoomOp.P_RATIO_X, ratio);
                imgInfo.getPostProcessOpManager().setParamValue(ZoomOp.OP_NAME, ZoomOp.P_RATIO_Y, ratio);
            }
        }

        SwingWorker<File, AcquireImageInfo> dicomizeTask = new DicomizeTask(toPublish);
        ActionListener taskCancelActionListener = e -> dicomizeTask.cancel(true);

        dicomizeTask.addPropertyChangeListener(evt -> {
            if ("progress" == evt.getPropertyName()) { //$NON-NLS-1$
                int progress = (Integer) evt.getNewValue();
                progressBar.setValue(progress);

            } else if ("state" == evt.getPropertyName()) { //$NON-NLS-1$

                if (StateValue.STARTED == evt.getNewValue()) {
                    resolutionPane.setVisible(false);
                    progressBar.setVisible(true);
                    publishButton.setEnabled(false);
                    cancelButton.removeActionListener(clearAndHideActionListener);
                    cancelButton.addActionListener(taskCancelActionListener);

                } else if (StateValue.DONE == evt.getNewValue()) {
                    File exportDirDicom = null;
                    
                    if (!dicomizeTask.isCancelled()) {
                        try {
                            exportDirDicom = dicomizeTask.get();
                        } catch (InterruptedException | ExecutionException doNothing) {
                        }

                        if (exportDirDicom != null) {
                            publishPanel.publishDirDicom(exportDirDicom);
                            clearAndHide();
                        } else {
                            JOptionPane.showMessageDialog(this,
                                Messages.getString("AcquirePublishDialog.dicomize_error_msg"), //$NON-NLS-1$
                                Messages.getString("AcquirePublishDialog.dicomize_error_title"), //$NON-NLS-1$
                                JOptionPane.ERROR_MESSAGE);
                        }
                    }

                    if (exportDirDicom == null) {
                        resolutionPane.setVisible(!getOversizedSelected(publishTree).isEmpty());
                        progressBar.setValue(0);
                        progressBar.setVisible(false);
                        publishButton.setEnabled(true);
                        cancelButton.removeActionListener(taskCancelActionListener);
                        cancelButton.addActionListener(clearAndHideActionListener);
                    }
                }
            }
        });

        ThreadUtil.buildNewSingleThreadExecutor("Dicomize").execute(dicomizeTask); //$NON-NLS-1$

    }

    private List<AcquireImageInfo> getSelectedImages(PublishTree tree) {
        return Arrays.stream(tree.getModel().getCheckingPaths())
            .map(o1 -> DefaultMutableTreeNode.class.cast(o1.getLastPathComponent()))
            .filter(o2 -> AcquireImageInfo.class.isInstance(o2.getUserObject()))
            .map(o3 -> AcquireImageInfo.class.cast(o3.getUserObject())).collect(Collectors.toList());
    }

    private List<AcquireImageInfo> getOversizedSelected(PublishTree tree) {
        return getSelectedImages(tree).stream().filter(oversizedImages()).collect(Collectors.toList());
    }

    private Predicate<AcquireImageInfo> oversizedImages() {
        return acqImg -> {
            PlanarImage img = acqImg.getImage().getImage(acqImg.getPostProcessOpManager());

            Integer width = img.getWidth();
            Integer height = img.getHeight();

            return width > AcquirePublishDialog.MAX_RESOLUTION_THRESHOLD
                || height > AcquirePublishDialog.MAX_RESOLUTION_THRESHOLD;

        };
    }

    public void clearAndHide() {
        dispose();
    }

    public enum EResolution {
        original(Messages.getString("AcquirePublishDialog.original")), //$NON-NLS-1$
        hd(Messages.getString("AcquirePublishDialog.high_res")), md(Messages.getString("AcquirePublishDialog.med_res")); //$NON-NLS-1$ //$NON-NLS-2$

        private String title;

        EResolution(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

}
