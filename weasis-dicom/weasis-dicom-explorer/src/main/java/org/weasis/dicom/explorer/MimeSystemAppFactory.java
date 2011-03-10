/*******************************************************************************
 * Copyright (c) 2011 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.explorer;

import java.util.Hashtable;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.dicom.codec.DicomMediaIO;

public class MimeSystemAppFactory implements SeriesViewerFactory {

    public final static String NAME = "default system application"; //$NON-NLS-1$
    public static final Icon ICON = new ImageIcon(MimeInspector.class.getResource("/icon/16x16/apps-system.png")); //$NON-NLS-1$
    public final static MimeSystemAppViewer mimeSystemViewer = new MimeSystemAppViewer();

    public MimeSystemAppFactory() {
    }

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
        return ""; //$NON-NLS-1$
    }

    @Override
    public boolean canReadMimeType(String mimeType) {
        return DicomMediaIO.SERIES_VIDEO_MIMETYPE.equals(mimeType)
            || DicomMediaIO.SERIES_ENCAP_DOC_MIMETYPE.equals(mimeType);
    }

    @Override
    public boolean isViewerCreatedByThisFactory(SeriesViewer viewer) {
        return false;
    }

    @Override
    public SeriesViewer createSeriesViewer(Hashtable<String, Object> properties) {
        return mimeSystemViewer;
    }

    @Override
    public int getLevel() {
        return 100;
    }
}
