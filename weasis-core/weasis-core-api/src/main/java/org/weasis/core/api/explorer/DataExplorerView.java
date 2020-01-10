/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.weasis.core.api.explorer;

import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.List;

import javax.swing.Action;

import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.GUIEntry;

public interface DataExplorerView extends PropertyChangeListener, GUIEntry {

    /**
     * Dispose must unregister listeners and close model if it not use by other view
     */
    void dispose();

    DataExplorerModel getDataExplorerModel();

    List<Action> getOpenImportDialogAction();

    List<Action> getOpenExportDialogAction();

    void importFiles(File[] files, boolean recursive);

    boolean canImportFiles();
}
