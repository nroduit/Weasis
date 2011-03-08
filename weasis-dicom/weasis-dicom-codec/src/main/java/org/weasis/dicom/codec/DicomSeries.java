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
package org.weasis.dicom.codec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.media.jai.PlanarImage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesEvent;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;

public class DicomSeries extends Series<DicomImageElement> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomSeries.class);

    protected PreloadingTask preloadingTask;

    public DicomSeries(String subseriesInstanceUID) {
        this(TagW.SubseriesInstanceUID, subseriesInstanceUID, null);
    }

    public DicomSeries(TagW displayTag, String subseriesInstanceUID, List<DicomImageElement> c) {
        super(TagW.SubseriesInstanceUID, subseriesInstanceUID, displayTag, c);
    }

    @Override
    public String toString() {
        return (String) getTagValue(TagW.SubseriesInstanceUID);
    }

    public boolean[] getImageInMemoryList() {
        boolean[] list;
        synchronized (medias) {
            list = new boolean[medias.size()];
            for (int i = 0; i < medias.size(); i++) {
                if (medias.get(i).isImageInMemory()) {
                    list[i] = true;
                }
            }
        }
        return list;
    }

    @Override
    public void addMedia(MediaElement media) {
        if (media != null && media.getMediaReader() instanceof DicomMediaIO) {
            DicomMediaIO dicomImageLoader = (DicomMediaIO) media.getMediaReader();

            if (media instanceof DicomImageElement) {

                int insertIndex;

                synchronized (medias) {
                    // add image or multi-frame sorted by Instance Number (0020,0013) order
                    int index =
                        Collections.binarySearch(medias, (DicomImageElement) media, SortSeriesStack.instanceNumber);
                    if (index < 0) {
                        insertIndex = -(index + 1);
                    } else {
                        // Should not happen because the instance number must be unique
                        insertIndex = index + 1;
                    }
                    if (insertIndex < 0 || insertIndex > size()) {
                        insertIndex = medias.size();
                    }
                    medias.add(insertIndex, (DicomImageElement) media);
                }
                DataExplorerModel model = (DataExplorerModel) getTagValue(TagW.ExplorerModel);
                if (model != null) {
                    model.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Add, model, null,
                        new SeriesEvent(SeriesEvent.Action.AddImage, this, insertIndex)));
                }
            } else if (media instanceof DicomSpecialElement) {
                setTag(TagW.DicomSpecialElement, media);
            }
        }
    }

    @Override
    public String getToolTips() {
        StringBuffer toolTips = new StringBuffer();
        toolTips.append("<html>"); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.pat"), TagW.PatientName); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.mod"), TagW.Modality); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.series_nb"), TagW.SeriesNumber); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.study"), TagW.StudyDescription); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.series"), TagW.SeriesDescription); //$NON-NLS-1$
        String date = TagW.formatDate((Date) getTagValue(TagW.SeriesDate));
        toolTips.append(Messages.getString("DicomSeries.date") + (date == null ? "" : date) + "<br>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        if (getFileSize() > 0.0) {
            toolTips.append("Size: " + FileUtil.formatSize(getFileSize()) + "<br>"); //$NON-NLS-2$
        }
        toolTips.append("</html>"); //$NON-NLS-1$
        return toolTips.toString();
    }

    public String getMimeType() {
        String modality = (String) getTagValue(TagW.Modality);
        if ("PR".equals(modality)) { //$NON-NLS-1$
            return DicomMediaIO.SERIES_PR_MIMETYPE;
        } else if ("KO".equals(modality)) { //$NON-NLS-1$
            return DicomMediaIO.SERIES_KO_MIMETYPE;
        } else if ("SR".equals(modality)) { //$NON-NLS-1$
            return DicomMediaIO.SERIES_SR_MIMETYPE;
        }
        return DicomMediaIO.SERIES_MIMETYPE;
    }

    @Override
    public void setSelected(boolean selected, int selectedImage) {
        if (this.isSelected() != selected) {
            super.setSelected(selected, selectedImage);
            if (selected) {
                start(selectedImage);
            } else {
                stop();
            }
        }
    }

    @Override
    public void dispose() {
        stop();
        super.dispose();
    }

    @Override
    public int getNearestIndex(double location) {
        synchronized (medias) {
            int index = -1;
            double bestDiff = Double.MAX_VALUE;
            for (int i = 0; i < medias.size(); i++) {
                double[] val = (double[]) medias.get(i).getTagValue(TagW.SlicePosition);
                if (val != null) {
                    double diff = Math.abs(location - (val[0] + val[1] + val[2]));
                    if (diff < bestDiff) {
                        bestDiff = diff;
                        index = i;
                    }
                }
            }
            return index;
        }
    }

    public synchronized void start(int index) {
        if (preloadingTask != null) {
            stop();
        }
        // System.err.println("Start preloading :" + getTagValue(getTagID()));
        preloadingTask = new PreloadingTask(new ArrayList<DicomImageElement>(medias), index);
        preloadingTask.start();
    }

    public synchronized void stop() {
        PreloadingTask moribund = preloadingTask;
        preloadingTask = null;
        if (moribund != null) {
            // System.err.println("Stop preloading :" + getTagValue(getTagID()));
            moribund.setPreloading(false);
            moribund.interrupt();
        }

    }

    class PreloadingTask extends Thread {
        private volatile boolean preloading = true;
        private final int index;
        private final ArrayList<DicomImageElement> imageList;

        public PreloadingTask(ArrayList<DicomImageElement> imageList, int index) {
            this.imageList = imageList;
            this.index = index;
        }

        public synchronized boolean isPreloading() {
            return preloading;
        }

        public synchronized void setPreloading(boolean preloading) {
            this.preloading = preloading;
        }

        private void freeMemory() {
            System.gc();
            try {
                Thread.sleep(50);
            } catch (InterruptedException ex) {
            }
        }

        private long evaluateImageSize(DicomImageElement image) {
            Integer allocated = (Integer) image.getTagValue(TagW.BitsAllocated);
            Integer sample = (Integer) image.getTagValue(TagW.SamplesPerPixel);
            Integer rows = (Integer) image.getTagValue(TagW.Rows);
            Integer columns = (Integer) image.getTagValue(TagW.Columns);
            if (allocated != null && sample != null && rows != null && columns != null) {
                return (rows * columns * sample * allocated) / 8;
            }
            return 0L;
        }

        private void loadArrays(DicomImageElement img, DataExplorerModel model) {
            // Do not load an image if another process already loading it
            if (preloading && !img.isLoading()) {
                Boolean cache = (Boolean) img.getTagValue(TagW.ImageCache);
                if (cache == null || !cache) {
                    long start = System.currentTimeMillis();
                    PlanarImage i = img.getImage();
                    if (i != null) {
                        int tymin = i.getMinTileY();
                        int tymax = i.getMaxTileY();
                        int txmin = i.getMinTileX();
                        int txmax = i.getMaxTileX();
                        for (int tj = tymin; tj <= tymax; tj++) {
                            for (int ti = txmin; ti <= txmax; ti++) {
                                try {
                                    i.getTile(ti, tj);
                                } catch (OutOfMemoryError e) {
                                    LOGGER.error("Out of memory when loading image: {}", img); //$NON-NLS-1$
                                    freeMemory();
                                    return;
                                }
                            }
                        }
                    }
                    long stop = System.currentTimeMillis();
                    LOGGER.debug("Reading time: {} ms of image: {}", (stop - start), img.getMediaURI()); //$NON-NLS-1$
                    if (model != null) {
                        model.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Add, model, null,
                            new SeriesEvent(SeriesEvent.Action.loadImageInMemory, DicomSeries.this, img)));
                    }
                }
            }
        }

        @Override
        public void run() {
            if (imageList != null) {
                DataExplorerModel model = (DataExplorerModel) getTagValue(TagW.ExplorerModel);
                int size = imageList.size();
                if (model == null || index < 0 || index >= size) {
                    return;
                }
                long imgSize = evaluateImageSize(imageList.get(index)) * size + 5000;
                long heapSize = Runtime.getRuntime().totalMemory();
                long heapFreeSize = Runtime.getRuntime().freeMemory();
                if (imgSize > heapSize / 3) {
                    if (imgSize > heapFreeSize) {
                        freeMemory();
                    }
                    double val = (double) heapFreeSize / imgSize;
                    int ajustSize = (int) (size * val) / 2;
                    int start = index - ajustSize;
                    if (start < 0) {
                        ajustSize -= start;
                        start = 0;
                    }
                    if (ajustSize > size) {
                        ajustSize = size;
                    }
                    for (int i = start; i < ajustSize; i++) {
                        loadArrays(imageList.get(i), model);
                    }
                } else {
                    if (imgSize > heapFreeSize) {
                        freeMemory();
                    }
                    for (DicomImageElement img : imageList) {
                        loadArrays(img, model);
                    }
                }
            }
        }
    }
}
