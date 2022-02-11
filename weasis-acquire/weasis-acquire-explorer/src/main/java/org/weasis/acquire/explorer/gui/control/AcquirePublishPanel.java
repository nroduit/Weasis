/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.gui.control;

import java.beans.PropertyChangeEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.SwingWorker.StateValue;
import org.dcm4che3.net.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireImageStatus;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.Messages;
import org.weasis.acquire.explorer.PublishDicomTask;
import org.weasis.acquire.explorer.gui.dialog.AcquirePublishDialog;
import org.weasis.core.api.auth.AuthMethod;
import org.weasis.core.api.auth.OAuth2ServiceFactory;
import org.weasis.core.api.gui.task.CircularProgressBar;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.ThreadUtil;
import org.weasis.core.util.FileUtil;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode;
import org.weasis.dicom.explorer.pref.node.AuthenticationPersistence;
import org.weasis.dicom.explorer.pref.node.DefaultDicomNode;
import org.weasis.dicom.explorer.pref.node.DicomWebNode;
import org.weasis.dicom.op.CStore;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.ConnectOptions;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.send.StowRS;
import org.weasis.dicom.web.ContentType;

public class AcquirePublishPanel extends JPanel {
  private static final Logger LOGGER = LoggerFactory.getLogger(AcquirePublishPanel.class);

  private final JButton publishBtn = new JButton(Messages.getString("AcquirePublishPanel.publish"));
  private final CircularProgressBar progressBar = new CircularProgressBar(0, 100);
  private AuthMethod authMethod;

  public static final ExecutorService PUBLISH_DICOM =
      ThreadUtil.buildNewSingleThreadExecutor("Publish Dicom"); // NON-NLS

  public AcquirePublishPanel() {
    publishBtn.addActionListener(
        e -> {
          final AcquirePublishDialog dialog = new AcquirePublishDialog(AcquirePublishPanel.this);
          GuiUtils.showCenterScreen(dialog, WinUtil.getParentWindow(AcquirePublishPanel.this));
        });

    publishBtn.setPreferredSize(GuiUtils.getDimension(150, 40));

    add(publishBtn);
    add(progressBar);

    progressBar.setVisible(false);
  }

  public void publishDirDicom(
      File exportDirDicom, AbstractDicomNode destinationNode, List<AcquireImageInfo> toPublish) {
    SwingWorker<DicomState, File> publishDicomTask = null;
    if (destinationNode instanceof DefaultDicomNode defaultDicomNode) {
      publishDicomTask = publishDicomDimse(exportDirDicom, defaultDicomNode.getDicomNode());
    } else if (destinationNode instanceof final DicomWebNode node) {
      publishDicomTask = publishStow(exportDirDicom, node, toPublish);
    }

    if (publishDicomTask != null) {
      publishDicomTask.addPropertyChangeListener(this::publishChanged);
      PUBLISH_DICOM.execute(publishDicomTask);
    }
  }

  public PublishDicomTask publishDicomDimse(File exportDirDicom, DicomNode destNdde) {
    DicomProgress dicomProgress = new DicomProgress();
    Supplier<DicomState> publish =
        () -> {
          List<String> exportFilesDicomPath = new ArrayList<>();
          exportFilesDicomPath.add(exportDirDicom.getPath());
          AdvancedParams params = new AdvancedParams();
          ConnectOptions connectOptions = new ConnectOptions();
          connectOptions.setConnectTimeout(3000);
          connectOptions.setAcceptTimeout(5000);
          params.setConnectOptions(connectOptions);
          DicomNode callingNode =
              new DicomNode(
                  BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.aet", "WEASIS_AE")); // NON-NLS
          try {
            return CStore.process(
                params, callingNode, destNdde, exportFilesDicomPath, dicomProgress);
          } finally {
            FileUtil.recursiveDelete(exportDirDicom);
          }
        };
    return new PublishDicomTask(publish, dicomProgress);
  }

  public PublishDicomTask publishStow(
      File exportDirDicom, DicomWebNode node, List<AcquireImageInfo> toPublish) {
    AuthMethod auth = AuthenticationPersistence.getAuthMethod(node.getAuthMethodUid());
    if (!OAuth2ServiceFactory.noAuth.equals(auth)) {
      String oldCode = auth.getCode();
      authMethod = auth;
      if (authMethod.getToken() == null) {
        return null;
      }
      if (!Objects.equals(oldCode, authMethod.getCode())) {
        AuthenticationPersistence.saveMethod();
      }
    }

    Supplier<DicomState> publish =
        () -> {
          try (StowRS stowRS =
              new StowRS(
                  node.getUrl().toString(),
                  ContentType.APPLICATION_DICOM,
                  AppProperties.WEASIS_NAME,
                  node.getHeaders())) {

            DicomState state =
                stowRS.uploadDicom(
                    Collections.singletonList(exportDirDicom.getAbsolutePath()), true, authMethod);
            if (state.getStatus() == Status.Success) {
              toPublish.forEach(
                  i -> {
                    i.setStatus(AcquireImageStatus.PUBLISHED);
                    i.getImage().setTag(TagW.Checked, Boolean.TRUE);
                    AcquireManager.getInstance().removeImage(i);
                  });
            }
            return state;
          } catch (Exception e) {
            LOGGER.error("STOW-RS publish", e);
            return DicomState.buildMessage(null, e.getMessage(), null);
          } finally {
            FileUtil.recursiveDelete(exportDirDicom);
          }
        };
    return new PublishDicomTask(publish, new DicomProgress());
  }

  private void publishChanged(PropertyChangeEvent evt) {
    if ("progress".equals(evt.getPropertyName())) {
      int progress = (Integer) evt.getNewValue();
      progressBar.setValue(progress);

    } else if ("state".equals(evt.getPropertyName())) {
      if (StateValue.STARTED == evt.getNewValue()) {
        publishBtn.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setValue(0);
      } else if (StateValue.DONE == evt.getNewValue()) {
        try {
          SwingWorker<DicomState, File> publishDicomTask =
              (SwingWorker<DicomState, File>) evt.getSource();
          final DicomState dicomState = publishDicomTask.get();
          if (dicomState.getStatus() != Status.Success && dicomState.getStatus() != Status.Cancel) {
            LOGGER.error("Dicom send error: {}", dicomState.getMessage());
            JOptionPane.showMessageDialog(
                WinUtil.getParentWindow(AcquirePublishPanel.this),
                dicomState.getMessage(),
                null,
                JOptionPane.ERROR_MESSAGE);
          }
        } catch (InterruptedException e) {
          LOGGER.warn("Retrieving task Interruption");
          Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
          LOGGER.error("Retrieving task", e);
        }
        publishBtn.setEnabled(true);
        progressBar.setVisible(false);
      }
    }
  }
}
