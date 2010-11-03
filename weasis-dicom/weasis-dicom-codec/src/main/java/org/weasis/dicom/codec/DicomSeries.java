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
import java.util.Date;
import java.util.List;

import javax.media.jai.PlanarImage;

import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesEvent;
import org.weasis.core.api.media.data.TagElement;

public class DicomSeries extends Series<DicomImageElement> {

    private volatile boolean preloading = true;
    protected PreloadingTask preloadingTask;

    public DicomSeries(String subseriesInstanceUID) {
        this(TagElement.SubseriesInstanceUID, subseriesInstanceUID, null);
    }

    public DicomSeries(TagElement displayTag, String subseriesInstanceUID, List<DicomImageElement> c) {
        super(TagElement.SubseriesInstanceUID, subseriesInstanceUID, displayTag, c);
    }

    @Override
    public String toString() {
        return (String) getTagValue(TagElement.SubseriesInstanceUID);
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
    public void addMedia(MediaReader mediaLoader) {
        if (mediaLoader instanceof DicomMediaIO) {
            DicomMediaIO dicomImageLoader = (DicomMediaIO) mediaLoader;
            int frames = dicomImageLoader.getMediaElementNumber();
            if (frames > 0) {
                int insertIndex;
                int nb = dicomImageLoader.getImageNumber();

                synchronized (medias) {
                    // add image or multi-frame by Instance Number (0020,0013) order
                    insertIndex = medias.size();
                    if (insertIndex > 0) {
                        while (nb < (Integer) medias.get(insertIndex - 1).getTagValue(TagElement.InstanceNumber)) {
                            insertIndex -= 1;
                            if (insertIndex == 0) {
                                break;
                            }
                        }
                    }
                    medias.add(insertIndex, (DicomImageElement) dicomImageLoader.getMediaElement());
                    for (int i = 1; i < frames; i++) {
                        medias.add(insertIndex + i, new DicomImageElement(dicomImageLoader, i));
                    }
                }
                DataExplorerModel model = (DataExplorerModel) getTagValue(TagElement.ExplorerModel);
                if (model != null) {
                    model.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Add, model, null,
                        new SeriesEvent(SeriesEvent.Action.AddImage, this, insertIndex + frames)));
                }
            } else {
                String modality = (String) getTagValue(TagElement.Modality);
                boolean ps =
                    modality != null && ("PR".equals(modality) || "KO".equals(modality) || "SR".equals(modality)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                if (ps) {
                    DicomSpecialElement dicom = new DicomSpecialElement(mediaLoader, null);
                    setTag(TagElement.DicomSpecialElement, dicom);
                }
            }
        }
    }

    @Override
    public String getToolTips() {
        StringBuffer toolTips = new StringBuffer();
        toolTips.append("<html>"); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.pat"), TagElement.PatientName); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.mod"), TagElement.Modality); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.series_nb"), TagElement.SeriesNumber); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.study"), TagElement.StudyDescription); //$NON-NLS-1$
        addToolTipsElement(toolTips, Messages.getString("DicomSeries.series"), TagElement.SeriesDescription); //$NON-NLS-1$
        String date = TagElement.formatDate((Date) getTagValue(TagElement.SeriesDate));
        toolTips.append(Messages.getString("DicomSeries.date") + (date == null ? "" : date) + "<br>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        toolTips.append(Messages.getString("DicomSeries.nb_frame") //$NON-NLS-1$
            + (seriesLoader == null ? medias.size() : seriesLoader.getProgressBar().getMaximum()) + "<br>"); //$NON-NLS-1$
        toolTips.append("</html>"); //$NON-NLS-1$
        return toolTips.toString();
    }

    public String getMimeType() {
        String modality = (String) getTagValue(TagElement.Modality);
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
                double[] val = (double[]) medias.get(i).getTagValue(TagElement.SlicePosition);
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
        preloading = true;
        preloadingTask = new PreloadingTask(new ArrayList<DicomImageElement>(medias), index);
        preloadingTask.start();
    }

    public synchronized void stop() {
        PreloadingTask moribund = preloadingTask;
        preloadingTask = null;
        if (moribund != null) {
            preloading = false;
            moribund.interrupt();
        }

    }

    class PreloadingTask extends Thread {
        private final int index;
        private final ArrayList<DicomImageElement> imageList;

        public PreloadingTask(ArrayList<DicomImageElement> imageList, int index) {
            this.imageList = imageList;
            this.index = index;
        }

        private void freeMemory() {
            System.gc();
            try {
                Thread.sleep(50);
            } catch (InterruptedException ex) {
            }
        }

        private long evaluateImageSize(DicomImageElement image) {
            int allocated = (Integer) image.getTagValue(TagElement.BitsAllocated);
            int sample = (Integer) image.getTagValue(TagElement.SamplesPerPixel);
            int rows = (Integer) image.getTagValue(TagElement.Rows);
            int columns = (Integer) image.getTagValue(TagElement.Columns);
            return (rows * columns * sample * allocated) / 8;
        }

        private void loadArrays(DicomImageElement img, DataExplorerModel model) {
            // Do not load an image if another process already loading it
            if (preloading && !img.isLoading()) {
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
                                System.out.println("Out of memory when loading image: " + img); //$NON-NLS-1$
                                freeMemory();
                                return;
                            }
                        }
                    }
                }
                if (model != null) {
                    model.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Add, model, null,
                        new SeriesEvent(SeriesEvent.Action.loadImageInMemory, DicomSeries.this, img)));
                }
            }
        }

        @Override
        public void run() {
            if (imageList != null) {
                DataExplorerModel model = (DataExplorerModel) getTagValue(TagElement.ExplorerModel);
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
