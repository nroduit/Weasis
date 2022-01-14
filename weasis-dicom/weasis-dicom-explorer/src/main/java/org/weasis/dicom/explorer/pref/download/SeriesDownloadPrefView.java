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

import javax.swing.JCheckBox;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.pref.PreferenceDialog;
import org.weasis.dicom.explorer.Messages;

public class SeriesDownloadPrefView extends AbstractItemDialogPage {
  public static final String DOWNLOAD_IMMEDIATELY = "weasis.download.immediately";

  private final JCheckBox downloadImmediatelyCheckbox =
      new JCheckBox(Messages.getString("SeriesDownloadPrefView.downloadImmediatelyCheckbox"));

  public SeriesDownloadPrefView() {
    super(Messages.getString("SeriesDownloadPrefView.title"), 507);

    downloadImmediatelyCheckbox.setSelected(
        BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(DOWNLOAD_IMMEDIATELY, true));
    add(GuiUtils.getComponentsInJPanel(0, 0, downloadImmediatelyCheckbox));

    add(GuiUtils.getBoxYLastElement(5));

    getProperties().setProperty(PreferenceDialog.KEY_SHOW_RESTORE, Boolean.TRUE.toString());
  }

  @Override
  public void resetToDefaultValues() {
    BundleTools.SYSTEM_PREFERENCES.resetProperty(DOWNLOAD_IMMEDIATELY, Boolean.TRUE.toString());
    downloadImmediatelyCheckbox.setSelected(
        BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(DOWNLOAD_IMMEDIATELY, true));
  }

  @Override
  public void closeAdditionalWindow() {
    BundleTools.SYSTEM_PREFERENCES.putBooleanProperty(
        DOWNLOAD_IMMEDIATELY, downloadImmediatelyCheckbox.isSelected());
    BundleTools.saveSystemPreferences();
  }
}
