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

import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.media.data.MediaSeries;

public class ViewerPluginBuilder<E> {

    private final SeriesViewerFactory factory;
    private final MediaSeries<E>[] series;
    private final DataExplorerModel model;
    private final boolean compareEntryToBuildNewViewer;

    public ViewerPluginBuilder(SeriesViewerFactory factory, MediaSeries<E>[] series, DataExplorerModel model) {
        this(factory, series, model, true);
    }

    public ViewerPluginBuilder(SeriesViewerFactory factory, MediaSeries<E>[] series, DataExplorerModel model,
        boolean compareEntryToBuildNewViewer) {
        this.factory = factory;
        this.series = series;
        this.model = model;
        this.compareEntryToBuildNewViewer = compareEntryToBuildNewViewer;
    }

    public SeriesViewerFactory getFactory() {
        return factory;
    }

    public MediaSeries<E>[] getSeries() {
        return series;
    }

    public DataExplorerModel getModel() {
        return model;
    }

    public boolean isCompareEntryToBuildNewViewer() {
        return compareEntryToBuildNewViewer;
    }

}
