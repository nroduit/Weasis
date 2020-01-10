/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.codec;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.image.cv.CvUtil;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesEvent;
import org.weasis.core.api.media.data.TagView;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.codec.TagD.Level;

public class DicomSeries extends Series<DicomImageElement> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomSeries.class);

    static final TagView defaultTagView =
        new TagView(TagD.getTagFromIDs(Tag.SeriesDescription, Tag.SeriesNumber, Tag.SeriesTime));

    private static PreloadingTask preloadingTask;

    public DicomSeries(String subseriesInstanceUID) {
        this(subseriesInstanceUID, null, defaultTagView);
    }

    public DicomSeries(String subseriesInstanceUID, List<DicomImageElement> c, TagView displayTag) {
        super(TagD.getUID(Level.SERIES), subseriesInstanceUID, displayTag, c, SortSeriesStack.instanceNumber);
    }

    public boolean[] getImageInMemoryList() {
        boolean[] list;
        synchronized (this) {
            list = new boolean[medias.size()];
            for (int i = 0; i < medias.size(); i++) {
                if (medias.get(i).isImageInCache()) {
                    list[i] = true;
                }
            }
        }
        return list;
    }

    @Override
    public void addMedia(DicomImageElement media) {
        if (media != null && media.getMediaReader() instanceof DcmMediaReader) {
            int insertIndex;
            synchronized (this) {
                // add image or multi-frame sorted by Instance Number (0020,0013) order
                int index = Collections.binarySearch(medias, media, SortSeriesStack.instanceNumber);
                if (index < 0) {
                    insertIndex = -(index + 1);
                } else {
                    // Should not happen because the instance number must be unique
                    insertIndex = index + 1;
                }
                if (insertIndex < 0 || insertIndex > medias.size()) {
                    insertIndex = medias.size();
                }
                add(insertIndex, media);
            }
            DataExplorerModel model = (DataExplorerModel) getTagValue(TagW.ExplorerModel);
            if (model != null) {
                model.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.ADD, model, null,
                    new SeriesEvent(SeriesEvent.Action.ADD_IMAGE, this, media)));
            }
        }
    }

    @Override
    public String getToolTips() {
        StringBuilder toolTips = new StringBuilder("<html>"); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.pat"), TagD.get(Tag.PatientName)); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.mod"), TagD.get(Tag.Modality)); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.series_nb"), TagD.get(Tag.SeriesNumber)); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.study"), TagD.get(Tag.StudyDescription)); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.series"), TagD.get(Tag.SeriesDescription)); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.date"), TagD.get(Tag.SeriesDate), TagD.get(Tag.SeriesTime)); //$NON-NLS-1$

        if (getFileSize() > 0.0) {
            toolTips.append(Messages.getString("DicomSeries.size")); //$NON-NLS-1$
            toolTips.append(StringUtil.COLON_AND_SPACE);
            toolTips.append(FileUtil.humanReadableByte(getFileSize(), false));
            toolTips.append("<br>"); //$NON-NLS-1$
        }
        toolTips.append("</html>"); //$NON-NLS-1$
        return toolTips.toString();
    }

    @Override
    public String getSeriesNumber() {
        Integer splitNb = (Integer) getTagValue(TagW.SplitSeriesNumber);
        Integer val = TagD.getTagValue(this, Tag.SeriesNumber, Integer.class);
        String result = val == null ? "" : val.toString(); //$NON-NLS-1$
        return splitNb == null ? result : result + "-" + splitNb.toString(); //$NON-NLS-1$
    }

    @Override
    public String getMimeType() {
        String modality = TagD.getTagValue(this, Tag.Modality, String.class);
        DicomSpecialElementFactory factory = DicomMediaIO.DCM_ELEMENT_FACTORIES.get(modality);
        if (factory != null) {
            return factory.getSeriesMimeType();
        }
        // Type for the default 2D viewer
        return DicomMediaIO.SERIES_MIMETYPE;
    }

    @Override
    public void dispose() {
        stopPreloading(this);
        super.dispose();
    }

    @Override
    public DicomImageElement getNearestImage(double location, int offset, Filter<DicomImageElement> filter,
        Comparator<DicomImageElement> sort) {
        Iterable<DicomImageElement> mediaList = getMedias(filter, sort);
        DicomImageElement nearest = null;
        int index = 0;
        int bestIndex = -1;
        synchronized (this) {
            double bestDiff = Double.MAX_VALUE;
            for (Iterator<DicomImageElement> iter = mediaList.iterator(); iter.hasNext();) {
                DicomImageElement dcm = iter.next();
                double[] val = (double[]) dcm.getTagValue(TagW.SlicePosition);
                if (val != null) {
                    double diff = Math.abs(location - (val[0] + val[1] + val[2]));
                    if (diff < bestDiff) {
                        bestDiff = diff;
                        nearest = dcm;
                        bestIndex = index;
                        if (MathUtil.isEqualToZero(diff)) {
                            break;
                        }
                    }
                }
                index++;
            }
        }
        if (offset > 0) {
            return getMedia(bestIndex + offset, filter, sort);
        }
        return nearest;
    }

    @Override
    public int getNearestImageIndex(double location, int offset, Filter<DicomImageElement> filter,
        Comparator<DicomImageElement> sort) {
        Iterable<DicomImageElement> mediaList = getMedias(filter, sort);
        int index = 0;
        int bestIndex = -1;
        synchronized (this) {
            double bestDiff = Double.MAX_VALUE;
            for (Iterator<DicomImageElement> iter = mediaList.iterator(); iter.hasNext();) {
                DicomImageElement dcm = iter.next();
                double[] val = (double[]) dcm.getTagValue(TagW.SlicePosition);
                if (val != null) {
                    double diff = Math.abs(location - (val[0] + val[1] + val[2]));
                    if (diff < bestDiff) {
                        bestDiff = diff;
                        bestIndex = index;
                        if (MathUtil.isEqualToZero(diff)) {
                            break;
                        }
                    }
                }
                index++;
            }
        }

        return (offset > 0) ? (bestIndex + offset) : bestIndex;
    }

    public static synchronized void startPreloading(DicomSeries series, List<DicomImageElement> imageList,
        int currentIndex) {
        if (series != null && imageList != null) {
            if (preloadingTask != null) {
                if (preloadingTask.getSeries() == series) {
                    return;
                }
                stopPreloading(preloadingTask.getSeries());
            }
            preloadingTask = new PreloadingTask(series, imageList, currentIndex);
            preloadingTask.start();
        }
    }

    public static synchronized void stopPreloading(DicomSeries series) {
        if (preloadingTask != null && preloadingTask.getSeries() == series) {
            PreloadingTask moribund = preloadingTask;
            preloadingTask = null;
            if (moribund != null) {
                moribund.setPreloading(false);
                moribund.interrupt();
            }
        }
    }

    static class PreloadingTask extends Thread {
        private volatile boolean preloading = true;
        private final int index;
        private final List<DicomImageElement> imageList;
        private final DicomSeries series;

        public PreloadingTask(DicomSeries series, List<DicomImageElement> imageList, int currentIndex) {
            this.series = series;
            this.imageList = imageList;
            this.index = currentIndex;
        }

        public synchronized boolean isPreloading() {
            return preloading;
        }

        public DicomSeries getSeries() {
            return series;
        }

        public List<DicomImageElement> getImageList() {
            return imageList;
        }

        public synchronized void setPreloading(boolean preloading) {
            this.preloading = preloading;
        }



        private static long evaluateImageSize(DicomImageElement image) {
            Integer allocated = TagD.getTagValue(image, Tag.BitsAllocated, Integer.class);
            Integer sample = TagD.getTagValue(image, Tag.SamplesPerPixel, Integer.class);
            Integer rows = TagD.getTagValue(image, Tag.Rows, Integer.class);
            Integer columns = TagD.getTagValue(image, Tag.Columns, Integer.class);
            if (allocated != null && sample != null && rows != null && columns != null) {
                return (rows * columns * sample * allocated) / 8L;
            }
            return 0L;
        }

        private void loadArrays(DicomImageElement img, DataExplorerModel model) {
            // Do not load an image if another process already loading it
            if (preloading && !img.isLoading()) {
                Boolean cache = (Boolean) img.getTagValue(TagW.ImageCache);
                if (cache == null || !cache) {
                    long start = System.currentTimeMillis();
                    try {
                        img.getImage();
                    } catch (OutOfMemoryError e) {
                        LOGGER.error("Out of memory when loading image: {}", img, e); //$NON-NLS-1$
                        CvUtil.runGarbageCollectorAndWait(50);
                        return;
                    }
                    long stop = System.currentTimeMillis();
                    LOGGER.debug("Reading time: {} ms of image: {}", stop - start, img); //$NON-NLS-1$
                    if (model != null) {
                        model.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.ADD, model, null,
                            new SeriesEvent(SeriesEvent.Action.PRELOADING, series, img)));
                    }
                }
            }
        }

        @Override
        public void run() {
            if (imageList != null) {
                DataExplorerModel model = (DataExplorerModel) series.getTagValue(TagW.ExplorerModel);
                int size = imageList.size();
                if (model == null || index < 0 || index >= size) {
                    return;
                }
                long imgSize = evaluateImageSize(imageList.get(index)) * size + 5000;
                long heapSize = Runtime.getRuntime().totalMemory();
                long heapFreeSize = Runtime.getRuntime().freeMemory();
                if (imgSize > heapSize / 3) {
                    if (imgSize > heapFreeSize) {
                        CvUtil.runGarbageCollectorAndWait(50);
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
                        CvUtil.runGarbageCollectorAndWait(50);
                    }
                    for (DicomImageElement img : imageList) {
                        loadArrays(img, model);
                    }
                }
            }
        }
    }
}
