/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.pref.download;

import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.SpinnerListModel;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.pref.PreferenceDialog;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.Messages;

public class SeriesDownloadPrefView extends AbstractItemDialogPage {
  public static final String DOWNLOAD_IMMEDIATELY = "weasis.download.immediately";

  private final JCheckBox downloadImmediatelyCheckbox =
      new JCheckBox(Messages.getString("SeriesDownloadPrefView.downloadImmediatelyCheckbox"));
  private final JSpinner spinner;

  public SeriesDownloadPrefView() {
    super(Messages.getString("DicomExplorer.title"), 607);

    int thumbnailSize =
        BundleTools.SYSTEM_PREFERENCES.getIntProperty(Thumbnail.KEY_SIZE, Thumbnail.DEFAULT_SIZE);
    JLabel thumbSize = new JLabel(Messages.getString("DicomExplorer.thmb_size"));
    SpinnerListModel model =
        new SpinnerListModel(
            List.of(
                Thumbnail.MIN_SIZE,
                Thumbnail.DEFAULT_SIZE,
                160,
                176,
                192,
                208,
                224,
                240,
                Thumbnail.MAX_SIZE));
    spinner = new JSpinner(model);
    model.setValue(thumbnailSize);
    add(GuiUtils.getFlowLayoutPanel(thumbSize, spinner));
    add(GuiUtils.boxVerticalStrut(15));

    downloadImmediatelyCheckbox.setSelected(
        BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(DOWNLOAD_IMMEDIATELY, true));
    add(GuiUtils.getFlowLayoutPanel(0, 0, downloadImmediatelyCheckbox));
    add(GuiUtils.boxYLastElement(LAST_FILLER_HEIGHT));

    getProperties().setProperty(PreferenceDialog.KEY_SHOW_APPLY, Boolean.TRUE.toString());
    getProperties().setProperty(PreferenceDialog.KEY_SHOW_RESTORE, Boolean.TRUE.toString());
  }

  @Override
  public void resetToDefaultValues() {
    BundleTools.SYSTEM_PREFERENCES.resetProperty(DOWNLOAD_IMMEDIATELY, Boolean.TRUE.toString());
    downloadImmediatelyCheckbox.setSelected(
        BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(DOWNLOAD_IMMEDIATELY, true));

    spinner.setValue(Thumbnail.DEFAULT_SIZE);
  }

  @Override
  public void closeAdditionalWindow() {
    BundleTools.SYSTEM_PREFERENCES.putBooleanProperty(
        DOWNLOAD_IMMEDIATELY, downloadImmediatelyCheckbox.isSelected());

    DataExplorerView dicomView = UIManager.getExplorerplugin(DicomExplorer.NAME);
    if (dicomView instanceof DicomExplorer explorer) {
      int size = (int) spinner.getValue();
      BundleTools.SYSTEM_PREFERENCES.putIntProperty(Thumbnail.KEY_SIZE, size);
      explorer.updateThumbnailSize(size);
    }

    BundleTools.saveSystemPreferences();
  }
}
