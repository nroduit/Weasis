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
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import org.osgi.framework.FrameworkUtil;
import org.osgi.service.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.DicomizeTask;
import org.weasis.acquire.explorer.Messages;
import org.weasis.acquire.explorer.gui.central.meta.model.imp.AcquireGlobalMeta;
import org.weasis.acquire.explorer.gui.central.meta.model.imp.AcquireImageMeta;
import org.weasis.acquire.explorer.gui.central.meta.model.imp.AcquireSerieMeta;
import org.weasis.acquire.explorer.gui.control.AcquirePublishPanel;
import org.weasis.acquire.explorer.gui.model.publish.PublishTree;
import org.weasis.acquire.explorer.util.ImageInfoHelper;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.image.ZoomOp;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.util.StringUtil;
import org.weasis.core.api.util.ThreadUtil;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.UsageType;
import org.weasis.dicom.explorer.pref.node.DefaultDicomNode;
import org.weasis.opencv.data.PlanarImage;

@SuppressWarnings("serial")
public class AcquirePublishDialog extends JDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger(AcquirePublishDialog.class);

    public static final String P_LAST_RESOLUTION = "last.resolution"; //$NON-NLS-1$
    public static final String PREFERENCE_NODE = "publish"; //$NON-NLS-1$

    public enum Resolution {
        ORIGINAL(Messages.getString("AcquirePublishDialog.original"), Integer.MAX_VALUE), //$NON-NLS-1$
        ULTRA_HD(Messages.getString("AcquirePublishDialog.high_res"), 3840), //$NON-NLS-1$
        FULL_HD(Messages.getString("AcquirePublishDialog.med_res"), 1920), //$NON-NLS-1$
        HD_DVD(Messages.getString("AcquirePublishDialog.low_res"), 1280); //$NON-NLS-1$

        private String title;
        private int maxSize;

        Resolution(String title, int size) {
            this.title = title;
            this.maxSize = size;
        }

        @Override
        public String toString() {
            return title;
        }

        public String getTitle() {
            return title;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public static Resolution getInstance(String val) {
            if (StringUtil.hasText(val)) {
                try {
                    return Resolution.valueOf(val);
                } catch (Exception e) {
                    LOGGER.error("Cannot find Resolution: {}", val, e); //$NON-NLS-1$
                }
            }
            return Resolution.ORIGINAL;
        }
    }

    private final AcquirePublishPanel publishPanel;

    private PublishTree publishTree;
    private JPanel resolutionPane;
    private JComboBox<Resolution> resolutionCombo;
    private JButton publishButton;
    private JButton cancelButton;
    private JProgressBar progressBar;

    private transient ActionListener clearAndHideActionListener;
    private final JComboBox<AbstractDicomNode> comboNode = new JComboBox<>();

    public AcquirePublishDialog(AcquirePublishPanel publishPanel) {
        super(WinUtil.getParentWindow(publishPanel), Messages.getString("AcquirePublishDialog.publication"), //$NON-NLS-1$
            ModalityType.APPLICATION_MODAL);
        this.publishPanel = publishPanel;

        setContentPane(initContent());
        publishTree.getTree().addCheckingPath(new TreePath(publishTree.getModel().getRootNode().getPath()));

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
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

        resolutionCombo = new JComboBox<>(Resolution.values());
        Preferences prefs =
            BundlePreferences.getDefaultPreferences(FrameworkUtil.getBundle(this.getClass()).getBundleContext());
        if (prefs != null) {
            Preferences p = prefs.node(PREFERENCE_NODE);
            resolutionCombo.setSelectedItem(Resolution.getInstance(p.get(P_LAST_RESOLUTION, Resolution.ORIGINAL.name())));
        }
        resolutionPane.add(resolutionCombo);
        resolutionPane.setVisible(Boolean.FALSE);

        actionPane.add(resolutionPane, BorderLayout.NORTH);

        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);

        actionPane.add(progressBar, BorderLayout.CENTER);

        JPanel bottomPane = new JPanel(new BorderLayout());
        JPanel buttonPane = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));

        publishButton = new JButton(Messages.getString("AcquirePublishDialog.publish")); //$NON-NLS-1$
        publishButton.addActionListener(e -> publishAction());

        cancelButton = new JButton(Messages.getString("AcquirePublishDialog.cancel")); //$NON-NLS-1$
        clearAndHideActionListener = e -> clearAndHide();
        cancelButton.addActionListener(clearAndHideActionListener);

        JPanel destPane = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 10));
        JLabel lblDestination =
            new JLabel(Messages.getString("AcquirePublishDialog.lblDestination.text") + StringUtil.COLON); //$NON-NLS-1$
        destPane.add(lblDestination);
        AbstractDicomNode.addTooltipToComboList(comboNode);

        if (!StringUtil.hasText(BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.acquire.dest.host"))) { //$NON-NLS-1$
            AbstractDicomNode.loadDicomNodes(comboNode, AbstractDicomNode.Type.DICOM, UsageType.STORAGE);
            if (comboNode.getItemCount() == 0) {
                comboNode.addItem(getDestinationConfiguration());
            }
        } else {
            comboNode.addItem(getDestinationConfiguration());
        }

        destPane.add(comboNode);
        bottomPane.add(destPane, BorderLayout.WEST);

        buttonPane.add(publishButton);
        buttonPane.add(cancelButton);

        bottomPane.add(buttonPane, BorderLayout.EAST);

        actionPane.add(bottomPane, BorderLayout.SOUTH);

        contentPane.add(actionPane, BorderLayout.SOUTH);

        return contentPane;
    }

    private static AbstractDicomNode getDestinationConfiguration() {
        String host = BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.acquire.dest.host", "localhost"); //$NON-NLS-1$ //$NON-NLS-2$
        String aet = BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.acquire.dest.aet", "DCM4CHEE"); //$NON-NLS-1$ //$NON-NLS-2$
        String port = BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.acquire.dest.port", "11112"); //$NON-NLS-1$ //$NON-NLS-2$
        return new DefaultDicomNode(Messages.getString("AcquirePublishDialog.def_archive"), aet, host, //$NON-NLS-1$
            Integer.parseInt(port), UsageType.BOTH);
    }

    private void publishAction() {
        List<AcquireImageInfo> toPublish = getSelectedImages(publishTree);

        if (toPublish.isEmpty()) {
            JOptionPane.showMessageDialog(this, Messages.getString("AcquirePublishDialog.select_one_msg"), //$NON-NLS-1$
                this.getTitle(), JOptionPane.ERROR_MESSAGE);
            return;
        }

        boolean publishable = AcquireGlobalMeta.isPublishable(AcquireManager.GLOBAL);
        if (publishable) {
            for (AcquireImageInfo info : toPublish) {
                publishable = AcquireSerieMeta.isPublishable(info.getSeries());
                if (!publishable) {
                    break;
                }
                publishable = AcquireImageMeta.isPublishable(info.getImage());
                if (!publishable) {
                    break;
                }
            }
        }
        if (!publishable) {
            JOptionPane.showMessageDialog(this, Messages.getString("AcquirePublishDialog.pub_warn_msg"), //$NON-NLS-1$
                this.getTitle(), JOptionPane.ERROR_MESSAGE);
            return;
        }

        List<AcquireImageInfo> overSizedSelected = getOversizedSelected(publishTree);
        if (!overSizedSelected.isEmpty()) {
            Resolution resolution = (Resolution) resolutionCombo.getSelectedItem();
            for (AcquireImageInfo imgInfo : overSizedSelected) {
                // calculate zoom ration
                Double ratio = ImageInfoHelper.calculateRatio(imgInfo, resolution);

                imgInfo.getCurrentValues().setRatio(ratio);
                imgInfo.getPostProcessOpManager().setParamValue(ZoomOp.OP_NAME, ZoomOp.P_RATIO_X, ratio);
                imgInfo.getPostProcessOpManager().setParamValue(ZoomOp.OP_NAME, ZoomOp.P_RATIO_Y, ratio);
            }
        }

        SwingWorker<File, AcquireImageInfo> dicomizeTask = new DicomizeTask(toPublish);
        ActionListener taskCancelActionListener = e -> dicomizeTask.cancel(true);

        dicomizeTask.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) { //$NON-NLS-1$
                int progress = (Integer) evt.getNewValue();
                progressBar.setValue(progress);

            } else if ("state".equals(evt.getPropertyName())) { //$NON-NLS-1$

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
                        } catch (InterruptedException doNothing) {
                            LOGGER.warn("Dicomizing task Interruption"); //$NON-NLS-1$
                            Thread.currentThread().interrupt();
                        } catch (ExecutionException e) {
                            LOGGER.error("Dicomizing task", e); //$NON-NLS-1$
                        }

                        if (exportDirDicom != null) {
                            AbstractDicomNode node = (AbstractDicomNode) comboNode.getSelectedItem();
                            if (node instanceof DefaultDicomNode) {
                                publishPanel.publishDirDicom(exportDirDicom, ((DefaultDicomNode) node).getDicomNode());
                                clearAndHide();
                            }
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
            return img.width() > Resolution.ULTRA_HD.maxSize || img.height() > Resolution.ULTRA_HD.maxSize;
        };
    }

    public void clearAndHide() {
        Resolution resolution = (Resolution) resolutionCombo.getSelectedItem();
        dispose();

        Preferences prefs =
            BundlePreferences.getDefaultPreferences(FrameworkUtil.getBundle(this.getClass()).getBundleContext());
        if (prefs != null) {
            Preferences p = prefs.node(PREFERENCE_NODE);
            BundlePreferences.putStringPreferences(p, P_LAST_RESOLUTION, resolution.name());
        }
    }

}
