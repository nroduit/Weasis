/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.send;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Status;
import org.dcm4che3.util.UIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.auth.AuthMethod;
import org.weasis.core.api.auth.OAuth2ServiceFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.task.CircularProgressBar;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.ThreadUtil;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.LangUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.core.util.StringUtil.Suffix;
import org.weasis.dicom.codec.DicomElement;
import org.weasis.dicom.codec.DicomElement.DicomExportParameters;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.CheckTreeModel;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExplorerTask;
import org.weasis.dicom.explorer.ExportDicom;
import org.weasis.dicom.explorer.ExportTree;
import org.weasis.dicom.explorer.LocalExport;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.UsageType;
import org.weasis.dicom.explorer.pref.node.AuthenticationPersistence;
import org.weasis.dicom.explorer.pref.node.DefaultDicomNode;
import org.weasis.dicom.explorer.pref.node.DicomWebNode;
import org.weasis.dicom.op.CStore;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.ConnectOptions;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.web.ContentType;

public class SendDicomView extends AbstractItemDialogPage implements ExportDicom {

  private static final Logger LOGGER = LoggerFactory.getLogger(SendDicomView.class);

  private static final String LAST_SEL_NODE = "lastSelNode";

  private final DicomModel dicomModel;
  private final ExportTree exportTree;
  private final ExecutorService executor =
      ThreadUtil.buildNewFixedThreadExecutor(3, "Dicom Send task"); // NON-NLS

  private final JComboBox<AbstractDicomNode> comboNode = new JComboBox<>();
  private AuthMethod authMethod;

  public SendDicomView(DicomModel dicomModel, CheckTreeModel treeModel) {
    super(Messages.getString("SendDicomView.title"), 5);
    this.dicomModel = dicomModel;
    this.exportTree = new ExportTree(treeModel);
    initGUI();
    initialize(true);
  }

  public void initGUI() {
    final JLabel lblDest =
        new JLabel(Messages.getString("SendDicomView.destination") + StringUtil.COLON);
    AbstractDicomNode.addTooltipToComboList(comboNode);

    add(GuiUtils.getFlowLayoutPanel(ITEM_SEPARATOR_SMALL, 0, lblDest, comboNode));
    add(GuiUtils.boxVerticalStrut(ITEM_SEPARATOR));
    exportTree.setBorder(UIManager.getBorder("ScrollPane.border"));
    add(exportTree);
  }

  protected void initialize(boolean firstTime) {
    if (firstTime) {
      AbstractDicomNode.loadDicomNodes(comboNode, AbstractDicomNode.Type.DICOM, UsageType.STORAGE);
      AbstractDicomNode.loadDicomNodes(comboNode, AbstractDicomNode.Type.WEB, UsageType.STORAGE);
      String desc = SendDicomFactory.EXPORT_PERSISTENCE.getProperty(LAST_SEL_NODE);
      if (StringUtil.hasText(desc)) {
        ComboBoxModel<AbstractDicomNode> model = comboNode.getModel();
        for (int i = 0; i < model.getSize(); i++) {
          if (desc.equals(model.getElementAt(i).getDescription())) {
            model.setSelectedItem(model.getElementAt(i));
            break;
          }
        }
      }
    }
  }

  public void resetSettingsToDefault() {
    initialize(false);
  }

  public void applyChange() {
    final AbstractDicomNode node = (AbstractDicomNode) comboNode.getSelectedItem();
    if (node != null) {
      SendDicomFactory.EXPORT_PERSISTENCE.setProperty(LAST_SEL_NODE, node.getDescription());
    }
  }

  protected void updateChanges() {}

  @Override
  public void closeAdditionalWindow() {
    applyChange();
    executor.shutdown();
  }

  @Override
  public void resetToDefaultValues() {}

  @Override
  public void exportDICOM(final CheckTreeModel model, JProgressBar info) throws IOException {

    ExplorerTask<Boolean, String> task =
        new ExplorerTask<>(getTitle(), false) {

          @Override
          protected Boolean doInBackground() throws Exception {
            return sendDicomFiles(model, this);
          }

          @Override
          protected void done() {
            dicomModel.firePropertyChange(
                new ObservableEvent(
                    ObservableEvent.BasicAction.LOADING_STOP, dicomModel, null, this));
          }
        };
    executor.execute(task);
  }

  private boolean sendDicomFiles(final CheckTreeModel model, final ExplorerTask<Boolean, String> t)
      throws IOException {
    dicomModel.firePropertyChange(
        new ObservableEvent(ObservableEvent.BasicAction.LOADING_START, dicomModel, null, t));
    File exportDir =
        FileUtil.createTempDir(
            AppProperties.buildAccessibleTempDirectory("tmp", "send")); // NON-NLS
    try {
      writeDicom(t, exportDir, model);

      if (t.isCancelled()) {
        return false;
      }

      String weasisAet =
          BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.aet", "WEASIS_AE"); // NON-NLS

      List<String> files = new ArrayList<>();
      files.add(exportDir.getAbsolutePath());

      final CircularProgressBar progressBar = t.getBar();
      DicomProgress dicomProgress = new DicomProgress();
      dicomProgress.addProgressListener(
          p ->
              GuiExecutor.instance()
                  .execute(
                      () -> {
                        int c =
                            p.getNumberOfCompletedSuboperations()
                                + p.getNumberOfFailedSuboperations();
                        int r = p.getNumberOfRemainingSuboperations();
                        progressBar.setValue((c * 100) / (c + r));
                      }));
      t.addCancelListener(dicomProgress);

      Object selectedItem = comboNode.getSelectedItem();
      if (selectedItem instanceof final DefaultDicomNode node) {
        AdvancedParams params = new AdvancedParams();
        ConnectOptions connectOptions = new ConnectOptions();
        connectOptions.setConnectTimeout(3000);
        connectOptions.setAcceptTimeout(5000);
        params.setConnectOptions(connectOptions);
        final DicomState state =
            CStore.process(
                params, new DicomNode(weasisAet), node.getDicomNode(), files, dicomProgress);
        if (state.getStatus() != Status.Success && state.getStatus() != Status.Cancel) {
          showErrorMessage(null, null, state);
        } else {
          LOGGER.info("Dicom send: {}", state.getMessage());
        }
      } else if (selectedItem instanceof final DicomWebNode node) {
        AuthMethod auth = AuthenticationPersistence.getAuthMethod(node.getAuthMethodUid());
        if (!OAuth2ServiceFactory.noAuth.equals(auth)) {
          String oldCode = auth.getCode();
          authMethod = auth;
          if (authMethod.getToken() == null) {
            return false;
          }
          if (!Objects.equals(oldCode, authMethod.getCode())) {
            AuthenticationPersistence.saveMethod();
          }
        }

        try (StowRS stowRS =
            new StowRS(
                node.getUrl().toString(),
                ContentType.APPLICATION_DICOM,
                AppProperties.WEASIS_NAME,
                node.getHeaders())) {
          DicomState state = stowRS.uploadDicom(files, true, authMethod);
          if (state.getStatus() != Status.Success && state.getStatus() != Status.Cancel) {
            showErrorMessage(null, null, state);
          }
        } catch (Exception e) {
          showErrorMessage("StowRS error: {}", e, null); // NON-NLS
        }
      }
    } finally {
      FileUtil.recursiveDelete(exportDir);
    }

    return true;
  }

  private void showErrorMessage(String title, Exception e, DicomState state) {
    if (e != null) {
      LOGGER.error(title, e.getMessage());
    }
    GuiExecutor.instance()
        .execute(
            () ->
                JOptionPane.showMessageDialog(
                    exportTree,
                    state == null
                        ? Objects.requireNonNull(e).getMessage()
                        : StringUtil.getTruncatedString(state.getMessage(), 150, Suffix.THREE_PTS),
                    getTitle(),
                    JOptionPane.ERROR_MESSAGE));
  }

  private void writeDicom(ExplorerTask<Boolean, String> task, File writeDir, CheckTreeModel model)
      throws IOException {
    synchronized (this) {
      ArrayList<String> uids = new ArrayList<>();
      TreePath[] paths = model.getCheckingPaths();
      for (TreePath treePath : paths) {
        if (task.isCancelled()) {
          return;
        }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();

        if (node.getUserObject() instanceof DicomImageElement img) {
          String iuid = TagD.getTagValue(img, Tag.SOPInstanceUID, String.class);
          int index = uids.indexOf(iuid);
          if (index == -1) {
            uids.add(iuid);
          } else {
            // Write only once the file for multiframes
            continue;
          }

          String path = LocalExport.buildPath(img, false, false, node, null);
          File destinationDir = new File(writeDir, path);
          destinationDir.mkdirs();
          DicomExportParameters dicomExportParameters =
              new DicomExportParameters(null, true, null, 0, 0);
          img.saveToFile(new File(destinationDir, iuid), dicomExportParameters);
        } else if (node.getUserObject() instanceof DicomElement dcm) {
          String iuid = TagD.getTagValue((TagReadable) dcm, Tag.SOPInstanceUID, String.class);

          String path = LocalExport.buildPath((MediaElement) dcm, false, false, node, null);
          File destinationDir = new File(writeDir, path);
          destinationDir.mkdirs();

          DicomExportParameters dicomExportParameters =
              new DicomExportParameters(null, true, null, 0, 0);
          dcm.saveToFile(new File(destinationDir, iuid), dicomExportParameters);
        } else if (node.getUserObject() instanceof Series) {
          MediaSeries<?> s = (MediaSeries<?>) node.getUserObject();
          if (LangUtil.getNULLtoFalse((Boolean) s.getTagValue(TagW.ObjectToSave))) {
            Series<?> series = (Series<?>) s.getTagValue(CheckTreeModel.SourceSeriesForPR);
            if (series != null) {
              String seriesInstanceUID = UIDUtils.createUID();
              for (MediaElement dcm : series.getMedias(null, null)) {
                GraphicModel grModel = (GraphicModel) dcm.getTagValue(TagW.PresentationModel);
                if (grModel != null && grModel.hasSerializableGraphics()) {
                  String path = LocalExport.buildPath(dcm, false, false, node, null);
                  LocalExport.buildAndWritePR(
                      dcm, false, new File(writeDir, path), null, node, seriesInstanceUID);
                }
              }
            }
          }
        }
      }
    }
  }
}
