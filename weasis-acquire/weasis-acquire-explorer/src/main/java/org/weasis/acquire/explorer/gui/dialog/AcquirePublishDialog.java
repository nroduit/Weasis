/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.gui.dialog;

import java.awt.BorderLayout;
import java.awt.Color;
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
import javax.swing.ComboBoxModel;
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
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.DicomizeTask;
import org.weasis.acquire.explorer.MediaImporterFactory;
import org.weasis.acquire.explorer.Messages;
import org.weasis.acquire.explorer.PublishDicomTask;
import org.weasis.acquire.explorer.gui.central.meta.model.imp.AcquireGlobalMeta;
import org.weasis.acquire.explorer.gui.central.meta.model.imp.AcquireImageMeta;
import org.weasis.acquire.explorer.gui.central.meta.model.imp.AcquireSeriesMeta;
import org.weasis.acquire.explorer.gui.control.AcquirePublishPanel;
import org.weasis.acquire.explorer.gui.model.publish.PublishTree;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.image.ImageOpNode;
import org.weasis.core.api.image.ZoomOp;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.api.util.ThreadUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.UsageType;
import org.weasis.dicom.explorer.pref.node.DefaultDicomNode;
import org.weasis.opencv.data.PlanarImage;

public class AcquirePublishDialog extends JDialog {
  private static final Logger LOGGER = LoggerFactory.getLogger(AcquirePublishDialog.class);
  private static final String LAST_SEL_NODE = "lastSelectedNode";
  public static final String P_LAST_RESOLUTION = "last.resolution";
  public static final String PREFERENCE_NODE = "publish"; // NON-NLS

  public enum Resolution {
    ORIGINAL(Messages.getString("AcquirePublishDialog.original"), Integer.MAX_VALUE),
    ULTRA_HD(Messages.getString("AcquirePublishDialog.high_res"), 3840),
    FULL_HD(Messages.getString("AcquirePublishDialog.med_res"), 1920),
    HD_DVD(Messages.getString("AcquirePublishDialog.low_res"), 1280);

    private final String title;
    private final int maxSize;

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
          LOGGER.error("Cannot find Resolution: {}", val, e);
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
    super(
        WinUtil.getParentWindow(publishPanel),
        Messages.getString("AcquirePublishDialog.publication"),
        ModalityType.APPLICATION_MODAL);
    this.publishPanel = publishPanel;

    setContentPane(initContent());
    publishTree
        .getTree()
        .addCheckingPath(new TreePath(publishTree.getModel().getRootNode().getPath()));

    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent we) {
            cancelButton.doClick();
          }
        });

    setPreferredSize(GuiUtils.getDimension(700, 400));
    pack();
  }

  private JPanel initContent() {
    JPanel contentPane = new JPanel();

    contentPane.setBorder(GuiUtils.getEmptyBorder(10, 10, 10, 10));
    contentPane.setLayout(new BorderLayout());

    JLabel questionLabel = new JLabel(Messages.getString("AcquirePublishDialog.select_pub"));
    questionLabel.setFont(FontItem.DEFAULT_SEMIBOLD.getFont());

    contentPane.add(questionLabel, BorderLayout.NORTH);

    JPanel imageTreePane = new JPanel(new BorderLayout());
    imageTreePane.setBorder(GuiUtils.getEmptyBorder(10, 10, 10, 10));

    publishTree = new PublishTree();
    publishTree.addTreeCheckingListener(
        evt -> {
          resolutionPane.setVisible(!getOversizedSelected(publishTree).isEmpty());
          resolutionPane.repaint();
        });
    publishTree.setMinimumSize(publishTree.getPreferredSize());
    imageTreePane.add(publishTree);

    contentPane.add(imageTreePane, BorderLayout.CENTER);

    JPanel actionPane = new JPanel(new BorderLayout());
    actionPane.setBorder(GuiUtils.getEmptyBorder(10, 10, 10, 10));

    resolutionPane = new JPanel();
    resolutionPane.setBorder(BorderFactory.createLineBorder(Color.BLACK));

    JLabel resolutionLabel =
        new JLabel(
            Messages.getString("AcquirePublishDialog.resolution") + StringUtil.COLON_AND_SPACE);
    resolutionPane.add(resolutionLabel);

    resolutionCombo = new JComboBox<>(Resolution.values());
    Preferences prefs =
        BundlePreferences.getDefaultPreferences(
            FrameworkUtil.getBundle(this.getClass()).getBundleContext());
    if (prefs != null) {
      Preferences p = prefs.node(PREFERENCE_NODE);
      resolutionCombo.setSelectedItem(
          Resolution.getInstance(p.get(P_LAST_RESOLUTION, Resolution.ORIGINAL.name())));
    }
    resolutionPane.add(resolutionCombo);
    resolutionPane.setVisible(Boolean.FALSE);

    actionPane.add(resolutionPane, BorderLayout.NORTH);

    progressBar = new JProgressBar();
    progressBar.setStringPainted(true);
    progressBar.setVisible(false);

    actionPane.add(progressBar, BorderLayout.CENTER);

    JPanel bottomPane = new JPanel(new BorderLayout());
    JPanel buttonPane = GuiUtils.getFlowLayoutPanel(FlowLayout.CENTER, 20, 10);

    publishButton = new JButton(Messages.getString("AcquirePublishDialog.publish"));
    publishButton.addActionListener(e -> publishAction());

    cancelButton = new JButton(Messages.getString("AcquirePublishDialog.cancel"));
    clearAndHideActionListener = e -> clearAndHide();
    cancelButton.addActionListener(clearAndHideActionListener);

    JLabel lblDestination =
        new JLabel(
            Messages.getString("AcquirePublishDialog.lblDestination.text") + StringUtil.COLON);
    AbstractDicomNode.addTooltipToComboList(comboNode);

    if (!StringUtil.hasText(
        BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.acquire.dest.host"))) {
      AbstractDicomNode.loadDicomNodes(comboNode, AbstractDicomNode.Type.DICOM, UsageType.STORAGE);
      AbstractDicomNode.loadDicomNodes(comboNode, AbstractDicomNode.Type.WEB, UsageType.STORAGE);
      String desc = MediaImporterFactory.EXPORT_PERSISTENCE.getProperty(LAST_SEL_NODE);
      if (StringUtil.hasText(desc)) {
        ComboBoxModel<AbstractDicomNode> model = comboNode.getModel();
        for (int i = 0; i < model.getSize(); i++) {
          if (desc.equals(model.getElementAt(i).getDescription())) {
            model.setSelectedItem(model.getElementAt(i));
            break;
          }
        }
      }

      if (comboNode.getItemCount() == 0) {
        comboNode.addItem(getDestinationConfiguration());
      }
    } else {
      comboNode.addItem(getDestinationConfiguration());
    }

    JPanel destPane = GuiUtils.getFlowLayoutPanel(2, 10, lblDestination, comboNode);
    bottomPane.add(destPane, BorderLayout.WEST);

    buttonPane.add(publishButton);
    buttonPane.add(cancelButton);

    bottomPane.add(buttonPane, BorderLayout.EAST);
    actionPane.add(bottomPane, BorderLayout.SOUTH);
    contentPane.add(actionPane, BorderLayout.SOUTH);
    return contentPane;
  }

  private static AbstractDicomNode getDestinationConfiguration() {
    String host =
        BundleTools.SYSTEM_PREFERENCES.getProperty(
            "weasis.acquire.dest.host", "localhost"); // NON-NLS
    String aet =
        BundleTools.SYSTEM_PREFERENCES.getProperty(
            "weasis.acquire.dest.aet", "DCM4CHEE"); // NON-NLS
    String port =
        BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.acquire.dest.port", "11112"); // NON-NLS
    return new DefaultDicomNode(
        Messages.getString("AcquirePublishDialog.def_archive"),
        aet,
        host,
        Integer.parseInt(port),
        UsageType.BOTH);
  }

  private void publishAction() {
    List<AcquireImageInfo> toPublish = getSelectedImages(publishTree);

    if (toPublish.isEmpty()) {
      JOptionPane.showMessageDialog(
          this,
          Messages.getString("AcquirePublishDialog.select_one_msg"),
          this.getTitle(),
          JOptionPane.ERROR_MESSAGE);
      return;
    }

    boolean publishable = AcquireGlobalMeta.isPublishable(AcquireManager.GLOBAL);
    if (publishable) {
      for (AcquireImageInfo info : toPublish) {
        publishable = AcquireSeriesMeta.isPublishable(info.getSeries());
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
      JOptionPane.showMessageDialog(
          this,
          Messages.getString("AcquirePublishDialog.pub_warn_msg"),
          this.getTitle(),
          JOptionPane.ERROR_MESSAGE);
      return;
    }

    for (AcquireImageInfo imgInfo : toPublish) {
      setZoomRatio(imgInfo, null);
    }
    Resolution resolution = (Resolution) resolutionCombo.getSelectedItem();
    if (resolution != Resolution.ORIGINAL) {
      List<AcquireImageInfo> overSizedSelected = getOversizedSelected(publishTree);
      if (!overSizedSelected.isEmpty()) {
        for (AcquireImageInfo imgInfo : overSizedSelected) {
          // calculate zoom ration
          setZoomRatio(imgInfo, PublishDicomTask.calculateRatio(imgInfo, resolution));
        }
      }
    }

    SwingWorker<File, AcquireImageInfo> dicomizeTask = new DicomizeTask(toPublish);
    ActionListener taskCancelActionListener = e -> dicomizeTask.cancel(true);

    dicomizeTask.addPropertyChangeListener(
        evt -> {
          if ("progress".equals(evt.getPropertyName())) {
            int progress = (Integer) evt.getNewValue();
            progressBar.setValue(progress);

          } else if ("state".equals(evt.getPropertyName())) {

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
                  LOGGER.warn("Dicomizing task Interruption");
                  Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                  LOGGER.error("Dicomizing task", e);
                }

                if (exportDirDicom != null) {
                  AbstractDicomNode node = (AbstractDicomNode) comboNode.getSelectedItem();
                  publishPanel.publishDirDicom(exportDirDicom, node, toPublish);
                  clearAndHide();
                } else {
                  JOptionPane.showMessageDialog(
                      this,
                      Messages.getString("AcquirePublishDialog.dicomize_error_msg"),
                      Messages.getString("AcquirePublishDialog.dicomize_error_title"),
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

    ThreadUtil.buildNewSingleThreadExecutor("Dicomize").execute(dicomizeTask); // NON-NLS
  }

  private static void setZoomRatio(AcquireImageInfo imgInfo, Double ratio) {
    imgInfo.getCurrentValues().setRatio(ratio);
    ImageOpNode node = imgInfo.getPostProcessOpManager().getNode(ZoomOp.OP_NAME);
    if (node != null) {
      node.clearIOCache();
      node.setParam(ZoomOp.P_RATIO_X, ratio);
      node.setParam(ZoomOp.P_RATIO_Y, ratio);
    }
  }

  private List<AcquireImageInfo> getSelectedImages(PublishTree tree) {
    return Arrays.stream(tree.getModel().getCheckingPaths())
        .map(o1 -> (DefaultMutableTreeNode) o1.getLastPathComponent())
        .filter(o2 -> o2.getUserObject() instanceof AcquireImageInfo)
        .map(o3 -> (AcquireImageInfo) o3.getUserObject())
        .collect(Collectors.toList());
  }

  private List<AcquireImageInfo> getOversizedSelected(PublishTree tree) {
    return getSelectedImages(tree).stream().filter(oversizedImages()).collect(Collectors.toList());
  }

  private Predicate<AcquireImageInfo> oversizedImages() {
    return acqImg -> {
      PlanarImage img = acqImg.getImage().getImage(acqImg.getPostProcessOpManager());
      return img.width() > Resolution.ULTRA_HD.maxSize
          || img.height() > Resolution.ULTRA_HD.maxSize;
    };
  }

  public void clearAndHide() {
    final AbstractDicomNode node = (AbstractDicomNode) comboNode.getSelectedItem();
    if (node != null) {
      MediaImporterFactory.EXPORT_PERSISTENCE.setProperty(LAST_SEL_NODE, node.getDescription());
    }
    Preferences prefs =
        BundlePreferences.getDefaultPreferences(
            FrameworkUtil.getBundle(this.getClass()).getBundleContext());
    if (prefs != null) {
      Preferences p = prefs.node(PREFERENCE_NODE);
      Resolution resolution = (Resolution) resolutionCombo.getSelectedItem();
      if (resolution != null) {
        BundlePreferences.putStringPreferences(p, P_LAST_RESOLUTION, resolution.name());
      }
    }
    dispose();
  }
}
