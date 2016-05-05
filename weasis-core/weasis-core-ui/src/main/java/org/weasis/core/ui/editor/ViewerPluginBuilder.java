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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.AbstractFileModel;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.serialize.XmlSerializer;

public class ViewerPluginBuilder {
    public static final String CMP_ENTRY_BUILD_NEW_VIEWER = "cmp.entry.viewer"; //$NON-NLS-1$
    public static final String BEST_DEF_LAYOUT = "best.def.layout"; //$NON-NLS-1$
    public static final String OPEN_IN_SELECTION = "add.in.selected.view"; // For only one image //$NON-NLS-1$
    public static final String ADD_IN_SELECTED_VIEW = "add.in.selected.view"; // For non DICOM images //$NON-NLS-1$
    public static final String SCREEN_BOUND = "screen.bound"; //$NON-NLS-1$
    public static final String ICON = "plugin.icon"; //$NON-NLS-1$
    public static final String UID = "plugin.uid"; //$NON-NLS-1$

    public static final AbstractFileModel DefaultDataModel = new AbstractFileModel();
    private final SeriesViewerFactory factory;
    private final List<MediaSeries<? extends MediaElement<?>>> series;
    private final DataExplorerModel model;
    private final Map<String, Object> properties;

    public ViewerPluginBuilder(SeriesViewerFactory factory, List<MediaSeries<? extends MediaElement<?>>> series,
        DataExplorerModel model, Map<String, Object> props) {
        if (factory == null || series == null || model == null) {
            throw new IllegalArgumentException();
        }
        this.factory = factory;
        this.series = series;
        this.model = model;
        this.properties = props == null ? Collections.synchronizedMap(new HashMap<String, Object>()) : props;
    }

    public SeriesViewerFactory getFactory() {
        return factory;
    }

    public List<MediaSeries<? extends MediaElement<?>>> getSeries() {
        return series;
    }

    public DataExplorerModel getModel() {
        return model;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public static void openSequenceInPlugin(SeriesViewerFactory factory, MediaSeries<? extends MediaElement<?>> series,
        DataExplorerModel model, boolean compareEntryToBuildNewViewer, boolean removeOldSeries) {
        if (factory == null || series == null || model == null) {
            return;
        }
        ArrayList<MediaSeries<? extends MediaElement<?>>> list =
            new ArrayList<MediaSeries<? extends MediaElement<?>>>(1);
        list.add(series);
        openSequenceInPlugin(factory, list, model, compareEntryToBuildNewViewer, removeOldSeries);
    }

    public static void openSequenceInPlugin(SeriesViewerFactory factory,
        List<MediaSeries<? extends MediaElement<?>>> series, DataExplorerModel model,
        boolean compareEntryToBuildNewViewer, boolean removeOldSeries) {
        openSequenceInPlugin(factory, series, model, compareEntryToBuildNewViewer, removeOldSeries, null);
    }

    public static void openSequenceInPlugin(SeriesViewerFactory factory,
        List<MediaSeries<? extends MediaElement<?>>> series, DataExplorerModel model,
        boolean compareEntryToBuildNewViewer, boolean removeOldSeries, Rectangle screenBound) {
        if (factory == null || series == null || model == null) {
            return;
        }
        Map<String, Object> props = Collections.synchronizedMap(new HashMap<String, Object>());
        props.put(CMP_ENTRY_BUILD_NEW_VIEWER, compareEntryToBuildNewViewer);
        props.put(BEST_DEF_LAYOUT, removeOldSeries);
        props.put(SCREEN_BOUND, screenBound);
        ViewerPluginBuilder builder = new ViewerPluginBuilder(factory, series, model, props);
        model.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Register, model, null, builder));
    }

    public static void openSequenceInPlugin(ViewerPluginBuilder builder) {
        if (builder == null) {
            return;
        }
        DataExplorerModel model = builder.getModel();
        model.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Register, model, null, builder));
    }

    public static void openSequenceInDefaultPlugin(List<MediaSeries<? extends MediaElement<?>>> series,
        DataExplorerModel model, boolean compareEntryToBuildNewViewer, boolean removeOldSeries) {
        ArrayList<String> mimes = new ArrayList<String>();
        for (MediaSeries<? extends MediaElement<?>> s : series) {
            String mime = s.getMimeType();
            if (mime != null && !mimes.contains(mime)) {
                mimes.add(mime);
            }
        }
        for (String mime : mimes) {
            SeriesViewerFactory plugin = UIManager.getViewerFactory(mime);
            if (plugin != null) {
                ArrayList<MediaSeries<? extends MediaElement<?>>> seriesList =
                    new ArrayList<MediaSeries<? extends MediaElement<?>>>();
                for (MediaSeries<? extends MediaElement<?>> s : series) {
                    if (mime.equals(s.getMimeType())) {
                        seriesList.add(s);
                    }
                }
                openSequenceInPlugin(plugin, seriesList, model, compareEntryToBuildNewViewer, removeOldSeries);
            }
        }
    }

    public static void openSequenceInDefaultPlugin(MediaSeries<? extends MediaElement<?>> series,
        DataExplorerModel model, boolean compareEntryToBuildNewViewer, boolean removeOldSeries) {
        if (series != null) {
            String mime = series.getMimeType();
            SeriesViewerFactory plugin = UIManager.getViewerFactory(mime);
            if (plugin == null) {
                plugin = DefaultMimeAppFactory.getInstance();
            }
            openSequenceInPlugin(plugin, series, model == null ? DefaultDataModel : model, compareEntryToBuildNewViewer,
                removeOldSeries);
        }

    }

    public static void openSequenceInDefaultPlugin(MediaElement<?> media, DataExplorerModel model,
        boolean compareEntryToBuildNewViewer, boolean bestDefaultLayout) {
        if (media != null) {
            openSequenceInDefaultPlugin(media.getMediaReader().getMediaSeries(), model, compareEntryToBuildNewViewer,
                bestDefaultLayout);
        }
    }

    public static void openSequenceInDefaultPlugin(File file, boolean compareEntryToBuildNewViewer,
        boolean bestDefaultLayout) {
        MediaReader<?> reader = getMedia(file);
        if (reader != null) {
            MediaSeries<? extends MediaElement<?>> s = buildMediaSeriesWithDefaultModel(reader);
            openSequenceInDefaultPlugin(s, DefaultDataModel, compareEntryToBuildNewViewer, bestDefaultLayout);
        }
    }

    public static MediaReader<?> getMedia(File file) {
        return getMedia(file, true);
    }

    public static MediaReader<?> getMedia(File file, boolean systemReader) {
        if (file != null && file.canRead()) {
            String mimeType = MimeInspector.getMimeType(file);
            if (mimeType != null) {
                Codec codec = BundleTools.getCodec(mimeType, "dcm4che"); //$NON-NLS-1$
                if (codec != null) {
                    return codec.getMediaIO(file.toURI(), mimeType, null);
                }
            }
            if (systemReader) {
                return new DefaultMimeIO<>(file.toURI(), null);
            }
        }
        return null;
    }

    public static MediaSeries<? extends MediaElement<?>> buildMediaSeriesWithDefaultModel(MediaReader<?> reader) {
        return buildMediaSeriesWithDefaultModel(reader, null, null, null);
    }

    public static MediaSeries<? extends MediaElement<?>> buildMediaSeriesWithDefaultModel(MediaReader<?> reader,
        String groupUID, TagW groupName, String groupValue) {
        return buildMediaSeriesWithDefaultModel(reader, groupUID, groupName, groupValue, null);
    }

    public static MediaSeries<? extends MediaElement<?>> buildMediaSeriesWithDefaultModel(MediaReader<?> reader,
        String groupUID, TagW groupName, String groupValue, String seriesUID) {
        if (reader instanceof DefaultMimeIO) {
            return reader.getMediaSeries();
        }
        MediaSeries<? extends MediaElement<?>> series = null;
        // Require to read the header
        MediaElement<?>[] medias = reader.getMediaElement();
        if (medias == null) {
            return null;
        }

        String sUID = seriesUID == null ? UUID.randomUUID().toString() : seriesUID;
        String gUID = groupUID == null ? UUID.randomUUID().toString() : groupUID;
        MediaSeriesGroup group1 = DefaultDataModel.getHierarchyNode(MediaSeriesGroupNode.rootNode, gUID);
        if (group1 == null) {
            group1 = new MediaSeriesGroupNode(TagW.Group, gUID, AbstractFileModel.group.getTagView());
            group1.setTagNoNull(groupName, groupValue);
            DefaultDataModel.addHierarchyNode(MediaSeriesGroupNode.rootNode, group1);
        }

        MediaSeriesGroup group2 = DefaultDataModel.getHierarchyNode(group1, sUID);
        if (group2 instanceof Series) {
            series = (Series<?>) group2;
        }

        try {

            if (series == null) {
                series = reader.getMediaSeries();
                series.setTag(TagW.ExplorerModel, DefaultDataModel);
                DefaultDataModel.addHierarchyNode(group1, series);
            } else {
                // Test if SOPInstanceUID already exists
                TagW sopTag = TagW.get("SOPInstanceUID");
                if (series instanceof Series
                    && ((Series<?>) series).hasMediaContains(sopTag, reader.getTagValue(sopTag))) {
                    return series;
                }

                for (MediaElement<?> media : medias) {
                    series.addMedia(media);
                }

            }

            for (MediaElement<?> media : medias) {
                if (media instanceof ImageElement) {
                    XmlSerializer.readMeasurementGraphics((ImageElement) media, media.getFile());
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
