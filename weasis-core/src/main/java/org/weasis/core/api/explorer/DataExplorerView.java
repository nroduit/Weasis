/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.explorer;

import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.List;
import javax.swing.Action;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.GUIEntry;

public interface DataExplorerView extends PropertyChangeListener, GUIEntry {

  /** Dispose must unregister listeners and close model if it is not used by other view */
  void dispose();

  DataExplorerModel getDataExplorerModel();

  List<Action> getOpenImportDialogAction();

  List<Action> getOpenExportDialogAction();

  void importFiles(File[] files, boolean recursive);

  boolean canImportFiles();

  /**
   * Whether this explorer can import files of that MIME type into its model, so that another
   * explorer which only browses files hands them over instead of opening them outside the model.
   */
  default boolean canImport(String mimeType) {
    return false;
  }

  /**
   * Whether this explorer is a source browser: it finds data and hands it to the model of another
   * explorer (the file explorer, a catalog of cases) rather than holding a model the viewers belong
   * to. Data arriving in that model while a source browser is shown keeps it shown.
   */
  default boolean isSource() {
    return false;
  }

  /** Whether this explorer feeds that model, so data loaded into it keeps this explorer shown. */
  default boolean isSourceFor(DataExplorerModel model) {
    return isSource() && model != null && model != getDataExplorerModel();
  }
}
