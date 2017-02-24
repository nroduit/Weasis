/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.explorer.wado;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JOptionPane;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExplorerTask;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.pref.download.SeriesDownloadPrefView;
import org.weasis.dicom.explorer.wado.DownloadManager.PriorityTaskComparator;

public class LoadRemoteDicomManifest extends ExplorerTask {

    private final String[] xmlFiles;
    private final DicomModel dicomModel;
    private final List<LoadSeries> loadSeriesList = new ArrayList<>();
    private boolean callAggain;

    public LoadRemoteDicomManifest(String[] xmlFiles, DataExplorerModel explorerModel) {
        super(Messages.getString("DicomExplorer.loading"), true); //$NON-NLS-1$
        if (xmlFiles == null || !(explorerModel instanceof DicomModel)) {
            throw new IllegalArgumentException("invalid parameters"); //$NON-NLS-1$
        }
        this.xmlFiles = xmlFiles;
        this.dicomModel = (DicomModel) explorerModel;

        createDicomModelListener();

    }

    public LoadRemoteDicomManifest(File[] xmlFiles, DataExplorerModel explorerModel) {
        super(Messages.getString("DicomExplorer.loading"), true); //$NON-NLS-1$
        if (xmlFiles == null || !(explorerModel instanceof DicomModel)) {
            throw new IllegalArgumentException("invalid parameters"); //$NON-NLS-1$
        }
        String[] xmlRef = new String[xmlFiles.length];
        for (int i = 0; i < xmlFiles.length; i++) {
            if (xmlFiles[i] != null) {
                xmlRef[i] = xmlFiles[i].getAbsolutePath();
            }
        }
        this.xmlFiles = xmlRef;
        this.dicomModel = (DicomModel) explorerModel;

        createDicomModelListener();
    }

    private void createDicomModelListener() {
        propertyChangeListener = new PropertyChangeListener() {

            @Override
            public void propertyChange(final PropertyChangeEvent evt) {
                //System.out.println(" Property change do dicom model: " + evt);
                // Esperar por eventod LOAD_FINISHED de cada loader (vai precisar guardar uma lista).
                // A cada um que chega ver se tem erro...
                // Depois que terminar de vir todos, se tiver erro oferece para tentar de novo!
                if (evt instanceof ObservableEvent) {
                    ObservableEvent event = (ObservableEvent) evt;
                    if (ObservableEvent.BasicAction.LOADING_STOP.equals(event.getActionCommand())) {
                        if (event.getNewValue() instanceof LoadSeries) {
                            removeLoadSer(event);
                        }
                    }
                }
            }

        };

    }
    private PropertyChangeListener propertyChangeListener;

    private void removeLoadSer(ObservableEvent event) {
        LoadSeries loads = (LoadSeries) event.getNewValue();
        if (loadSeriesList.contains(loads)) {
            loadSeriesList.remove(loads);
            if (loads.hasDownloadFail()) {
                callAggain = true;
            }
        }
        System.out.println("LoadSeries list now: " + loadSeriesList.size());
        if (loadSeriesList.isEmpty()) {
            dicomModel.removePropertyChangeListener(propertyChangeListener);

            if (callAggain) {
                String message = "One or more downloads have failed.";
                offerToTryAggain(message);
            }
        }
    }

    private void offerToTryAggain(final String message) {

        GuiExecutor.instance().execute(() -> {
            PluginTool explorer = null;
            DataExplorerView dicomExplorer = UIManager.getExplorerplugin(DicomExplorer.NAME);
            if (dicomExplorer instanceof PluginTool) {
                explorer = (PluginTool) dicomExplorer;
            }
            ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(explorer);
            String[] options = {"Try now", "No, thanks"};
            int confirm = JOptionPane.showOptionDialog(ColorLayerUI.getContentPane(layer),
                    message + "\n You can check your connection and try again:", null,
                    JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE, null, options, options[0]);
            if (layer != null) {
                layer.hideUI();
            }
            if (JOptionPane.YES_OPTION == confirm) {
                System.out.println(" confirmed!");
                new LoadRemoteDicomManifest(xmlFiles, dicomModel).execute();
            }

        });

    }

    @Override
    protected Boolean doInBackground() throws Exception {
        try {
            for (int i = 0; i < xmlFiles.length; i++) {

                if (xmlFiles[i] != null) {
                    URI uri = null;
                    try {
                        if (!xmlFiles[i].startsWith("http")) { //$NON-NLS-1$
                            try {
                                File file = new File(xmlFiles[i]);
                                if (file.canRead()) {
                                    uri = file.toURI();
                                }
                            } catch (Exception e) {
                            }
                        }
                        if (uri == null) {
                            uri = new URL(xmlFiles[i]).toURI();
                        }

                        List<LoadSeries> wadoTasks = DownloadManager.buildDicomSeriesFromXml(uri, dicomModel);

                        if (wadoTasks != null) {
                            boolean downloadImmediately = BundleTools.SYSTEM_PREFERENCES
                                    .getBooleanProperty(SeriesDownloadPrefView.DOWNLOAD_IMMEDIATELY, true);
                            for (final LoadSeries loadSeries : wadoTasks) {
                                loadSeriesList.add(loadSeries);
                                DownloadManager.addLoadSeries(loadSeries, dicomModel, downloadImmediately);
                            }

                            // Sort tasks from the download priority order (low number has a higher priority), TASKS
                            // is sorted from low to high priority).
                            Collections.sort(DownloadManager.TASKS, Collections.reverseOrder(new PriorityTaskComparator()));
                        }

                    } catch (URISyntaxException | MalformedURLException e) {
                        e.printStackTrace();
                    }
                }

            }
        } catch (WeasisDownloadException ex) {
            offerToTryAggain(ex.getMessage());
        }

        // Adicionar listener ao dicomModel!
        dicomModel.addPropertyChangeListener(propertyChangeListener);

        DownloadManager.CONCURRENT_EXECUTOR.prestartAllCoreThreads();
        return true;
    }
}
