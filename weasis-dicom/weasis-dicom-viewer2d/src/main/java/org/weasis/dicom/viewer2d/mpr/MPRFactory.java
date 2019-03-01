/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.viewer2d.mpr;

import java.awt.Component;
import java.util.Iterator;
import java.util.Map;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.mpr.MprView.SliceOrientation;

public class MPRFactory implements SeriesViewerFactory {

    public static final String NAME = Messages.getString("MPRFactory.title"); //$NON-NLS-1$
    public static final Icon ICON = new ImageIcon(MPRFactory.class.getResource("/icon/16x16/mpr.png")); //$NON-NLS-1$

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return Messages.getString("MPRFactory.desc"); //$NON-NLS-1$
    }

    @Override
    public SeriesViewer<?> createSeriesViewer(Map<String, Object> properties) {
        GridBagLayoutModel model = MPRContainer.VIEWS_2x1_mpr;
        String uid = null;
        if (properties != null) {
            Object obj = properties.get(org.weasis.core.api.image.GridBagLayoutModel.class.getName());
            if (obj instanceof GridBagLayoutModel) {
                model = (GridBagLayoutModel) obj;
            }
            // Set UID
            Object val = properties.get(ViewerPluginBuilder.UID);
            if (val instanceof String) {
                uid = (String) val;
            }
        }

        MPRContainer instance = new MPRContainer(model, uid);
        if (properties != null) {
            Object obj = properties.get(DataExplorerModel.class.getName());
            if (obj instanceof DicomModel) {
                // Register the PropertyChangeListener
                DicomModel m = (DicomModel) obj;
                m.addPropertyChangeListener(instance);
            }
        }
        int index = 0;
        Iterator<Component> enumVal = model.getConstraints().values().iterator();
        while (enumVal.hasNext()) {
            Component val = enumVal.next();
            if (val instanceof MprView) {
                SliceOrientation sliceOrientation;
                switch (index) {
                    case 1:
                        sliceOrientation = SliceOrientation.CORONAL;
                        break;
                    case 2:
                        sliceOrientation = SliceOrientation.SAGITTAL;
                        break;
                    default:
                        sliceOrientation = SliceOrientation.AXIAL;
                        break;
                }
                ((MprView) val).setType(sliceOrientation);
                index++;
            }
        }
        return instance;

    }

    public static void closeSeriesViewer(MPRContainer mprContainer) {
        // Unregister the PropertyChangeListener
        DataExplorerView dicomView = UIManager.getExplorerplugin(DicomExplorer.NAME);
        if (dicomView != null) {
            dicomView.getDataExplorerModel().removePropertyChangeListener(mprContainer);
        }
    }

    @Override
    public boolean canReadMimeType(String mimeType) {
        return DicomMediaIO.SERIES_MIMETYPE.equals(mimeType);
    }

    @Override
    public boolean isViewerCreatedByThisFactory(SeriesViewer<? extends MediaElement> viewer) {
        if (viewer instanceof MPRContainer) {
            return true;
        }
        return false;
    }

    @Override
    public int getLevel() {
        return 15;
    }

    @Override
    public boolean canAddSeries() {
        return false;
    }

    @Override
    public boolean canExternalizeSeries() {
        return true;
    }

}
