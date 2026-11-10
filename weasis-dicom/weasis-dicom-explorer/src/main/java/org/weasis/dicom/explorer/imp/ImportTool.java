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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.EmbeddedWizardPanel;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.TransferKind;
import org.weasis.dicom.explorer.TransferTool;

/**
 * The import from the local device: files, folders, archives and DICOMDIR media in one page, plus
 * the local pages of the import plugins. The pages stay alive while the tool is docked, so what the
 * user typed survives a viewer switch.
 */
public class ImportTool extends TransferTool {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImportTool.class);

  public static final String ID = "import"; // NON-NLS

  /** The site switch of the DICOM import, local and network alike. */
  public static final String FEATURE = "weasis.import.dicom"; // NON-NLS

  public ImportTool(DicomModel model) {
    super(
        ID,
        Messages.getString("DicomImport.imp_dicom"),
        30,
        ResourceUtil.getIcon(ActionIcon.IMPORT_DICOM),
        0.25,
        Messages.common("import"),
        "dicom-import", // NON-NLS
        model);
  }

  /** Whether the site allows the DICOM import ({@value #FEATURE}). */
  public static boolean isAllowed() {
    return isAllowed(FEATURE);
  }

  @Override
  public boolean isOffered() {
    return isAllowed();
  }

  /** Opens the import tool, on the page of that title when given. */
  public static EmbeddedWizardPanel open(String pageTitle) {
    return open(ID, pageTitle);
  }

  @Override
  protected List<AbstractItemDialogPage> createPages() {
    List<AbstractItemDialogPage> list = new ArrayList<>();
    list.add(new LocalImport());
    list.addAll(createPluginPages(TransferKind.LOCAL));
    InsertableUtil.sortInsertable(list);
    return list;
  }

  @Override
  protected void run(AbstractItemDialogPage page) {
    if (page instanceof ImportDicom importPage) {
      importPage.importDICOM(getModel(), null);
    }
  }

  /** The import factories of that kind the site enables. */
  public static List<DicomImportFactory> factories(TransferKind kind) {
    try {
      return GuiUtils.getUICore()
          .getPluginManager()
          .getExtensions(DicomImportFactory.class)
          .stream()
          .filter(Objects::nonNull)
          .filter(factory -> factory.getKind() == kind && factory.isAvailable())
          .toList();
    } catch (Exception e) {
      LOGGER.error("List import factories", e);
      return List.of();
    }
  }

  /** The pages of the import factories of that kind. */
  public static List<AbstractItemDialogPage> createPluginPages(TransferKind kind) {
    List<AbstractItemDialogPage> list = new ArrayList<>();
    for (DicomImportFactory factory : factories(kind)) {
      try {
        if (factory.createDicomImportPage(null) instanceof AbstractItemDialogPage page) {
          list.add(page);
        }
      } catch (Exception e) {
        LOGGER.error("Create import page of {}", factory.getClass().getName(), e);
      }
    }
    return list;
  }
}
