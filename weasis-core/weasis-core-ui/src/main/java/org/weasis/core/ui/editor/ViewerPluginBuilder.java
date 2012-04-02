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
package org.weasis.core.ui.editor;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.explorer.model.TreeModelNode;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.docking.UIManager;

public class ViewerPluginBuilder {
    public static final DataExplorerModel DefaultDataModel = new DataExplorerModel() {
        private PropertyChangeSupport propertyChange = null;

        @Override
        public void addPropertyChangeListener(PropertyChangeListener propertychangelistener) {
            if (propertyChange == null) {
                propertyChange = new PropertyChangeSupport(this);
            }
            propertyChange.addPropertyChangeListener(propertychangelistener);
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener propertychangelistener) {
            if (propertyChange != null) {
                propertyChange.removePropertyChangeListener(propertychangelistener);
            }
        }

        @Override
        public void firePropertyChange(final ObservableEvent event) {
            if (propertyChange != null) {
                if (event == null) {
                    throw new NullPointerException();
                }
                if (SwingUtilities.isEventDispatchThread()) {
                    propertyChange.firePropertyChange(event);
                } else {
                    SwingUtilities.invokeLater(new Runnable() {

                        @Override
                        public void run() {
                            propertyChange.firePropertyChange(event);
                        }
                    });
                }
            }
        }

        @Override
        public TreeModelNode getTreeModelNodeForNewPlugin() {
            return null;
        }

        @Override
        public List<Codec> getCodecPlugins() {
            return BundleTools.CODEC_PLUGINS;
        }

        @Override
        public boolean applySplittingRules(Series original, MediaElement media) {
            return false;
        }

    };

    private final SeriesViewerFactory factory;
    private final List<MediaSeries> series;
    private final DataExplorerModel model;
    private final boolean compareEntryToBuildNewViewer;
    private final boolean removeOldSeries;

    public ViewerPluginBuilder(SeriesViewerFactory factory, List<MediaSeries> series, DataExplorerModel model) {
        this(factory, series, model, true, true);

    }

    public ViewerPluginBuilder(SeriesViewerFactory factory, List<MediaSeries> series, DataExplorerModel model,
        boolean compareEntryToBuildNewViewer, boolean removeOldSeries) {
        this.factory = factory;
        this.series = series;
        this.model = model;
        this.compareEntryToBuildNewViewer = compareEntryToBuildNewViewer;
        this.removeOldSeries = removeOldSeries;
    }

    public SeriesViewerFactory getFactory() {
        return factory;
    }

    public List<MediaSeries> getSeries() {
        return series;
    }

    public DataExplorerModel getModel() {
        return model;
    }

    public boolean isCompareEntryToBuildNewViewer() {
        return compareEntryToBuildNewViewer;
    }

    public boolean isRemoveOldSeries() {
        return removeOldSeries;
    }

    public static void openSequenceInPlugin(SeriesViewerFactory factory, MediaSeries series, DataExplorerModel model,
        boolean compareEntryToBuildNewViewer, boolean removeOldSeries) {
        if (factory == null || series == null || model == null) {
            return;
        }
        ArrayList<MediaSeries> list = new ArrayList<MediaSeries>(1);
        list.add(series);
        openSequenceInPlugin(factory, list, model, compareEntryToBuildNewViewer, removeOldSeries);
    }

    public static void openSequenceInPlugin(SeriesViewerFactory factory, List<MediaSeries> series,
        DataExplorerModel model, boolean compareEntryToBuildNewViewer, boolean removeOldSeries) {
        if (factory == null || series == null || model == null) {
            return;
        }
        int nbImg = 0;
        for (MediaSeries m : series) {
            nbImg += m.size();
        }
        // Do not add series without medias. BUG WEA-100
        if (nbImg == 0) {
            return;
        }
        model.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Register, model, null,
            new ViewerPluginBuilder(factory, series, model, compareEntryToBuildNewViewer, removeOldSeries)));

    }

    public static void openSequenceInDefaultPlugin(List<MediaSeries> series, DataExplorerModel model,
        boolean compareEntryToBuildNewViewer, boolean removeOldSeries) {
        ArrayList<String> mimes = new ArrayList<String>();
        for (MediaSeries s : series) {
            String mime = s.getMimeType();
            if (mime != null && !mimes.contains(mime)) {
                mimes.add(mime);
            }
        }
        for (String mime : mimes) {
            SeriesViewerFactory plugin = UIManager.getViewerFactory(mime);
            if (plugin != null) {
                ArrayList<MediaSeries> seriesList = new ArrayList<MediaSeries>();
                for (MediaSeries s : series) {
                    if (mime.equals(s.getMimeType())) {
                        seriesList.add(s);
                    }
                }
                openSequenceInPlugin(plugin, seriesList, model, compareEntryToBuildNewViewer, removeOldSeries);
            }
        }
    }

    public static void openSequenceInDefaultPlugin(MediaSeries series, DataExplorerModel model,
        boolean compareEntryToBuildNewViewer, boolean removeOldSeries) {
        if (series != null) {
            String mime = series.getMimeType();
            SeriesViewerFactory plugin = UIManager.getViewerFactory(mime);
            if (plugin == null) {
                plugin = DefaultMimeAppFactory.getInstance();
            }
            openSequenceInPlugin(plugin, series, model == null ? DefaultDataModel : model,
                compareEntryToBuildNewViewer, removeOldSeries);
        }

    }

    public static void openSequenceInDefaultPlugin(MediaElement media, DataExplorerModel model,
        boolean compareEntryToBuildNewViewer, boolean removeOldSeries) {
        if (media != null) {
            openSequenceInDefaultPlugin(media.getMediaReader().getMediaSeries(), model, compareEntryToBuildNewViewer,
                removeOldSeries);
        }
    }

    public static void openSequenceInDefaultPlugin(File file) {
        MediaReader reader = getMedia(file);
        if (reader != null) {
            openSequenceInDefaultPlugin(reader.getMediaSeries(), null, true, true);
        }
    }

    public static MediaReader getMedia(File file) {
        if (file != null && file.canRead()) {
            String mimeType = MimeInspector.getMimeType(file);
            if (mimeType != null) {
                Codec codec = BundleTools.getCodec(mimeType, "dcm4che");
                if (codec != null) {
                    return codec.getMediaIO(file.toURI(), mimeType, null);
                }
            }
            return new DefaultMimeIO(file.toURI(), null);
        }
        return null;
    }
}
