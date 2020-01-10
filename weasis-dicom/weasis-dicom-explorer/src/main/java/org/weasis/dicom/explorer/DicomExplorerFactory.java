/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.explorer;

import java.util.Hashtable;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.DataExplorerViewFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.ViewerPluginBuilder;

@org.osgi.service.component.annotations.Component(service = DataExplorerViewFactory.class, immediate = false)
public class DicomExplorerFactory implements DataExplorerViewFactory {

    private DicomExplorer explorer = null;

    @org.osgi.service.component.annotations.Reference
    private DicomModel model;

    @Override
    public DataExplorerView createDataExplorerView(Hashtable<String, Object> properties) {
        if (explorer == null) {
            explorer = new DicomExplorer(model);
            model.addPropertyChangeListener(explorer);
            UIManager.EXPLORER_PLUGIN_TOOLBARS.add(new ImportToolBar(5, explorer));
            UIManager.EXPLORER_PLUGIN_TOOLBARS.add(new ExportToolBar(7, explorer));
            ViewerPluginBuilder.DefaultDataModel.firePropertyChange(
                new ObservableEvent(ObservableEvent.BasicAction.NULL_SELECTION, explorer, null, null));
        }
        return explorer;
    }

    // ================================================================================
    // OSGI service implementation
    // ================================================================================

    @Activate
    protected void activate(ComponentContext context) {
        // Do nothing
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        if (explorer != null) {
            DataExplorerModel dataModel = explorer.getDataExplorerModel();
            dataModel.removePropertyChangeListener(explorer);
            UIManager.EXPLORER_PLUGIN_TOOLBARS.removeIf(b -> b.getComponent().getAttachedInsertable() == explorer);
        }
    }
}