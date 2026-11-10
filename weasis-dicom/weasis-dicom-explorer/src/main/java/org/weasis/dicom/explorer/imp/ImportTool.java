/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.imp;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.JButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AbstractWizardDialog;
import org.weasis.core.api.gui.util.EmbeddedWizardPanel;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.docking.SidePanel;
import org.weasis.core.ui.docking.Slot;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.LocalPersistence;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.main.DicomExplorer;

/**
 * The DICOM import as a tool of the bottom slot of the explorer side: the import pages (local
 * folder, DICOMDIR, the query/retrieve of a plugin) in a page selector over a scrollable page, with
 * the import button under them. It replaces the import dialog and the footer: one mechanism, the
 * shell slot, with the explorer above it. The pages are built when the tool is shown and kept while
 * it stays in a slot, so what the user typed survives a viewer switch.
 */
public class ImportTool extends PluginTool {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImportTool.class);

  public static final String ID = "import"; // NON-NLS
  public static final String LAST_PAGE = "last.dicom.import.page"; // NON-NLS

  private final DicomModel model;
  private EmbeddedWizardPanel wizard;

  public ImportTool(DicomModel model) {
    super(ID, Messages.getString("DicomImport.imp_dicom"), Insertable.Type.EXPLORER, 30);
    this.model = Objects.requireNonNull(model);
    setIcon(ResourceUtil.getIcon(ActionIcon.IMPORT_DICOM));
    setLayout(new BorderLayout());
  }

  @Override
  public Slot getPreferredSlot() {
    return Slot.BOTTOM;
  }

  /** Whether the site allows the DICOM import ({@code weasis.import.dicom}). */
  public static boolean isAllowed() {
    return GuiUtils.getUICore()
        .getSystemPreferences()
        .getBooleanProperty("weasis.import.dicom", true);
  }

  /** The import tool of the DICOM explorer, null before the explorer exists. */
  public static ImportTool find() {
    return GuiUtils.getUICore().getExplorerPlugin(DicomExplorer.NAME)
            instanceof DicomExplorer explorer
        ? explorer.getImportTool()
        : null;
  }

  /**
   * Opens the import tool in the bottom slot of the explorer side, on the page of that title when
   * given (the title of a page, e.g. the local device one).
   *
   * @return the wizard the tool shows, null when there is no explorer or no shell
   */
  public static EmbeddedWizardPanel open(String pageTitle) {
    ImportTool tool = find();
    return tool == null ? null : tool.openOn(pageTitle);
  }

  /** {@link #open(String)} on this tool. */
  public EmbeddedWizardPanel openOn(String pageTitle) {
    ensureContent();
    SidePanel host = GuiUtils.getUICore().getSidePanel(getRole());
    if (host != null) {
      if (!host.contains(this)) {
        host.show(this);
      }
      host.open(ID, Slot.BOTTOM);
      host.expand();
    }
    if (pageTitle != null && wizard != null) {
      wizard.selectPage(pageTitle);
    }
    return wizard;
  }

  /** The wizard the tool shows, null while the tool has no content. */
  public EmbeddedWizardPanel getWizard() {
    return wizard;
  }

  @Override
  public void addNotify() {
    super.addNotify();
    ensureContent();
  }

  @Override
  public void closeDockable() {
    release();
    super.closeDockable();
  }

  private void ensureContent() {
    if (wizard != null) {
      return;
    }
    JButton importButton = new JButton(Messages.common("import"));
    JButton help = GuiUtils.createHelpButton("dicom-import"); // NON-NLS
    EmbeddedWizardPanel panel =
        new EmbeddedWizardPanel(
            createPages(),
            GuiUtils.getFlowLayoutPanel(
                FlowLayout.TRAILING,
                AbstractWizardDialog.HORIZONTAL_GAP,
                AbstractWizardDialog.VERTICAL_GAP,
                help,
                importButton));
    importButton.addActionListener(
        e -> {
          if (panel.getSelectedPage() instanceof ImportDicom page) {
            page.importDICOM(model, null);
          }
        });
    panel.selectPage(LocalPersistence.getProperties().getProperty(LAST_PAGE));
    wizard = panel;
    add(panel, BorderLayout.CENTER);
    revalidate();
    repaint();
  }

  private void release() {
    if (wizard == null) {
      return;
    }
    if (wizard.getSelectedPage() != null) {
      LocalPersistence.getProperties().setProperty(LAST_PAGE, wizard.getSelectedPage().getTitle());
    }
    wizard.closeAllPages();
    remove(wizard);
    wizard = null;
  }

  /** Builds the sorted import pages (local, DICOMDIR and plugin contributions). */
  public static List<AbstractItemDialogPage> createPages() {
    ArrayList<AbstractItemDialogPage> list = new ArrayList<>();
    list.add(new LocalImport());
    list.add(new DicomDirImport());
    try {
      List<DicomImportFactory> factories =
          GuiUtils.getUICore().getPluginManager().getExtensions(DicomImportFactory.class);
      for (DicomImportFactory factory : factories) {
        if (factory != null) {
          ImportDicom page = factory.createDicomImportPage(null);
          if (page instanceof AbstractItemDialogPage dialogPage) {
            list.add(dialogPage);
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error("init import pages", e);
    }
    InsertableUtil.sortInsertable(list);
    return list;
  }
}
