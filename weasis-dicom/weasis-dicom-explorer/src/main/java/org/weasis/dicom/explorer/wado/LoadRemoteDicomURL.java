/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.explorer.wado;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import javax.swing.SwingWorker;

import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.dicom.explorer.DicomModel;

public class LoadRemoteDicomURL extends SwingWorker<Boolean, String> {

    private final URL[] urls;
    private final DicomModel dicomModel;

    public LoadRemoteDicomURL(String[] urls, DataExplorerModel explorerModel) {
        if (urls == null || !(explorerModel instanceof DicomModel))
            throw new IllegalArgumentException("invalid parameters"); //$NON-NLS-1$
        URL[] urlRef = new URL[urls.length];
        for (int i = 0; i < urls.length; i++) {
            if (urls[i] != null) {
                try {
                    urlRef[i] = new URL(urls[i]);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        this.urls = urlRef;
        this.dicomModel = (DicomModel) explorerModel;
    }

    public LoadRemoteDicomURL(URL[] urls, DataExplorerModel explorerModel) {
        if (urls == null || !(explorerModel instanceof DicomModel))
            throw new IllegalArgumentException("invalid parameters"); //$NON-NLS-1$
        this.urls = urls;
        this.dicomModel = (DicomModel) explorerModel;
    }

    @Override
    protected Boolean doInBackground() throws Exception {
        for (int i = 0; i < urls.length; i++) {
            if (urls[i] != null) {
                URI uri = null;
                try {
                    uri = urls[i].toURI();
                    // LoadSeries s = new LoadSeries(dicomSeries, dicomModel);
                    // LoadRemoteDicomManifest.loadingQueue.offer(s);
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
            }
        }
        return true;
    }

    @Override
    protected void done() {
    }

}
