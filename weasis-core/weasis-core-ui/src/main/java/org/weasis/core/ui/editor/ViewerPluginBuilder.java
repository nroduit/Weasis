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

import java.awt.Rectangle;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.AbstractFileModel;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.docking.UIManager;

public class ViewerPluginBuilder {
    public static final AbstractFileModel DefaultDataModel = new AbstractFileModel();
    private final SeriesViewerFactory factory;
    private final List<MediaSeries> series;
    private final DataExplorerModel model;
    private final boolean compareEntryToBuildNewViewer;
    private final boolean removeOldSeries;
    private Rectangle screenBound;

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
        this.screenBound = null;
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

    public Rectangle getScreenBound() {
        return screenBound;
    }

    public void setScreenBound(Rectangle screenBound) {
        this.screenBound = screenBound;
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
        openSequenceInPlugin(factory, series, model, compareEntryToBuildNewViewer, removeOldSeries, null);
    }

    public static void openSequenceInPlugin(SeriesViewerFactory factory, List<MediaSeries> series,
        DataExplorerModel model, boolean compareEntryToBuildNewViewer, boolean removeOldSeries, Rectangle screenBound) {
        if (factory == null || series == null || model == null) {
            return;
        }
        int nbImg = 0;
        for (MediaSeries m : series) {
            nbImg += m.size(null);
        }
        // Do not add series without medias. BUG WEA-100
        if (nbImg == 0) {
            return;
        }
        ViewerPluginBuilder builder =
            new ViewerPluginBuilder(factory, series, model, compareEntryToBuildNewViewer, removeOldSeries);
        builder.setScreenBound(screenBound);
        model.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Register, model, null, builder));

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
            MediaSeries s = buildMediaSeriesWithDefaultModel(reader);
            openSequenceInDefaultPlugin(s, DefaultDataModel, true, true);
        }
    }

    public static MediaReader getMedia(File file) {
        return getMedia(file, true);
    }

    public static MediaReader getMedia(File file, boolean systemReader) {
        if (file != null && file.canRead()) {
            String mimeType = MimeInspector.getMimeType(file);
            if (mimeType != null) {
                Codec codec = BundleTools.getCodec(mimeType, "dcm4che"); //$NON-NLS-1$
                if (codec != null) {
                    return codec.getMediaIO(file.toURI(), mimeType, null);
                }
            }
            if (systemReader) {
                return new DefaultMimeIO(file.toURI(), null);
            }
        }
        return null;
    }

    public static MediaSeries buildMediaSeriesWithDefaultModel(MediaReader reader) {
        if (reader instanceof DefaultMimeIO) {
            return reader.getMediaSeries();
        }
        MediaSeries series = null;
        // Require to read the header
        MediaElement[] medias = reader.getMediaElement();
        if (medias == null) {
            return null;
        }
        String seriesUID = (String) reader.getTagValue(TagW.SeriesInstanceUID);
        if (seriesUID == null) {
            for (MediaElement media : medias) {
                URI uri = media.getMediaURI();
                if (uri != null) {
                    media.setTag(TagW.SeriesInstanceUID, uri.toString());
                    media.setTag(TagW.SOPInstanceUID, uri.toString());
                }
            }
            seriesUID = (String) reader.getTagValue(TagW.SeriesInstanceUID);
        }
        if (seriesUID != null) {
            MediaSeriesGroup group = DefaultDataModel.getHierarchyNode(TreeModel.rootNode, seriesUID);
            if (group instanceof Series) {
                series = (Series) group;
            }
        }
        try {

            if (series == null) {
                series = reader.getMediaSeries();
                series.setTag(TagW.ExplorerModel, DefaultDataModel);
                DefaultDataModel.addHierarchyNode(DefaultDataModel.rootNode, series);
            } else {
                // Test if SOPInstanceUID already exists
                if (series instanceof Series
                    && ((Series) series).hasMediaContains(TagW.SOPInstanceUID, reader.getTagValue(TagW.SOPInstanceUID))) {
                    return series;
                }
                if (medias != null) {
                    for (MediaElement media : medias) {
                        series.addMedia(media);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            reader.reset();
        }
        return series;
    }
}
