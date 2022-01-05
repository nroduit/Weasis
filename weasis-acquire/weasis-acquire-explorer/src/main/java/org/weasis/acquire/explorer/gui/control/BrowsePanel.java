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

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import net.samuelcampos.usbdrivedetector.USBDeviceDetectorManager;
import net.samuelcampos.usbdrivedetector.USBStorageDevice;
import net.samuelcampos.usbdrivedetector.events.IUSBDriveListener;
import net.samuelcampos.usbdrivedetector.events.USBStorageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.AcquireExplorer;
import org.weasis.acquire.explorer.core.ItemList;
import org.weasis.acquire.explorer.gui.model.actions.ChangePathSelectionAction;
import org.weasis.acquire.explorer.gui.model.list.ItemListComboBoxModel;
import org.weasis.acquire.explorer.gui.model.renderer.MediaSourceListCellRenderer;
import org.weasis.acquire.explorer.media.FileSystemDrive;
import org.weasis.acquire.explorer.media.MediaSource;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.FontTools;

public class BrowsePanel extends JPanel implements IUSBDriveListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(BrowsePanel.class);

  private final ItemList<MediaSource> mediaSourceList = new ItemList<>();
  private final JComboBox<MediaSource> mediaSourceSelectionCombo = new JComboBox<>();

  public BrowsePanel(AcquireExplorer acquisitionView) {
    try {
      acquisitionView.setSystemDrive(new FileSystemDrive(AcquireExplorer.getLastPath()));
      mediaSourceList.addItem(acquisitionView.getSystemDrive());
    } catch (Exception e) {
      LOGGER.warn(e.getMessage(), e);
    }

    USBDeviceDetectorManager driveDetector = new USBDeviceDetectorManager(2000);
    driveDetector.addDriveListener(this);

    ItemListComboBoxModel<MediaSource> mediaSourceListComboModel =
        new ItemListComboBoxModel<>(mediaSourceList);
    GridBagLayout gridBagLayout = new GridBagLayout();
    setLayout(gridBagLayout);
    mediaSourceSelectionCombo.setModel(mediaSourceListComboModel);
    mediaSourceSelectionCombo.setRenderer(
        new MediaSourceListCellRenderer(mediaSourceSelectionCombo));
    mediaSourceSelectionCombo.setMaximumRowCount(15);
    mediaSourceSelectionCombo.setFont(FontTools.getFont11());
    mediaSourceSelectionCombo.addActionListener(
        e -> {
          acquisitionView.setSystemDrive(
              (FileSystemDrive) mediaSourceSelectionCombo.getSelectedItem());
          acquisitionView.loadSystemDrive();
        });
    GuiUtils.addTooltipToComboList(mediaSourceSelectionCombo);

    GridBagConstraints gbcMediaSourceSelectionCombo = new GridBagConstraints();
    gbcMediaSourceSelectionCombo.fill = GridBagConstraints.HORIZONTAL;
    gbcMediaSourceSelectionCombo.weightx = 1.0;
    gbcMediaSourceSelectionCombo.anchor = GridBagConstraints.NORTHWEST;
    gbcMediaSourceSelectionCombo.insets = new Insets(5, 5, 5, 5);
    gbcMediaSourceSelectionCombo.gridx = 0;
    gbcMediaSourceSelectionCombo.gridy = 0;
    add(mediaSourceSelectionCombo, gbcMediaSourceSelectionCombo);

    final JButton pathSelectionBtn = new JButton(new ChangePathSelectionAction(acquisitionView));
    pathSelectionBtn.setFont(FontTools.getFont11());
    GridBagConstraints gbcPathSelectionBtn = new GridBagConstraints();
    gbcPathSelectionBtn.insets = new Insets(5, 5, 5, 5);
    gbcPathSelectionBtn.anchor = GridBagConstraints.NORTHWEST;
    gbcPathSelectionBtn.gridx = 1;
    gbcPathSelectionBtn.gridy = 0;
    add(pathSelectionBtn, gbcPathSelectionBtn);

    // Allow combo to limit the size with long path
    GuiUtils.setPreferredWidth(
        mediaSourceSelectionCombo,
        mediaSourceSelectionCombo.getPreferredSize().width
            - pathSelectionBtn.getPreferredSize().width
            - 5);
  }

  public JComboBox<MediaSource> getMediaSourceSelectionCombo() {
    return mediaSourceSelectionCombo;
  }

  public ItemList<MediaSource> getMediaSourceList() {
    return mediaSourceList;
  }

  @Override
  public void usbDriveEvent(USBStorageEvent event) {
    LOGGER.debug(event.toString());

    GuiExecutor.instance()
        .execute(
            () -> {
              switch (event.getEventType()) {
                case CONNECTED:
                  addUsbDevice(event.getStorageDevice());
                  break;
                case REMOVED:
                  removeUsbDevice(event.getStorageDevice());
                  break;
                default:
                  break;
              }
            });
  }

  private void addUsbDevice(USBStorageDevice storageDevice) {
    FileSystemDrive item = new FileSystemDrive(storageDevice.getRootDirectory().getPath());
    mediaSourceList.addItem(item);
    mediaSourceSelectionCombo.setSelectedItem(item);
  }

  private void removeUsbDevice(USBStorageDevice storageDevice) {
    MediaSource selected = (MediaSource) mediaSourceSelectionCombo.getSelectedItem();
    String id = storageDevice.getRootDirectory().getPath();
    mediaSourceList.getList().removeIf(m -> m.getPath().startsWith(id));
    if (mediaSourceList.isEmpty()) {
      mediaSourceSelectionCombo.setSelectedItem(null);
    }

    if (selected == null || selected.getPath().startsWith(id)) {
      mediaSourceSelectionCombo.setSelectedIndex(0);
    }
  }
}
