/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.explorer.wado;

import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import javax.swing.JProgressBar;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.SafeClose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.task.SeriesProgressMonitor;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesImporter;
import org.weasis.core.api.media.data.SeriesThumbnail;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.ClosableURLConnection;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.core.api.util.NetworkUtil;
import org.weasis.core.api.util.StreamIOException;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.api.util.ThreadUtil;
import org.weasis.core.api.util.URLParameters;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.ReferencedImage;
import org.weasis.core.ui.model.ReferencedSeries;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.TransferSyntax;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExplorerTask;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.MimeSystemAppFactory;
import org.weasis.dicom.explorer.ThumbnailMouseAndKeyAdapter;
import org.weasis.dicom.mf.HttpTag;
import org.weasis.dicom.mf.SopInstance;
import org.weasis.dicom.mf.WadoParameters;
import org.weasis.dicom.web.Multipart;
import org.weasis.dicom.web.Multipart.Handler;
import org.weasis.dicom.web.MultipartReader;

public class LoadSeries extends ExplorerTask<Boolean, String> implements SeriesImporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadSeries.class);
    public static final String CONCURRENT_DOWNLOADS_IN_SERIES = "download.concurrent.series.images"; //$NON-NLS-1$

    public static final File DICOM_TMP_DIR = AppProperties.buildAccessibleTempDirectory("downloading"); //$NON-NLS-1$
    public static final TagW DOWNLOAD_START_TIME = new TagW("DownloadSartTime", TagType.TIME); //$NON-NLS-1$

    public enum Status {
        DOWNLOADING, PAUSED, COMPLETE, CANCELLED, ERROR
    }

    public final int concurrentDownloads;
    private final DicomModel dicomModel;
    private final Series<?> dicomSeries;
    private final SeriesInstanceList seriesInstanceList;
    private final JProgressBar progressBar;
    private final URLParameters urlParams;
    private DownloadPriority priority = null;
    private final boolean writeInCache;
    private final boolean startDownloading;

    private volatile boolean hasError = false;

    public LoadSeries(Series<?> dicomSeries, DicomModel dicomModel, int concurrentDownloads, boolean writeInCache) {
        this(dicomSeries, dicomModel, concurrentDownloads, writeInCache, true);
    }
    
    public LoadSeries(Series<?> dicomSeries, DicomModel dicomModel, int concurrentDownloads, boolean writeInCache, boolean startDownloading) {
        super(Messages.getString("DicomExplorer.loading"), writeInCache, true); //$NON-NLS-1$
        if (dicomModel == null || dicomSeries == null) {
            throw new IllegalArgumentException("null parameters"); //$NON-NLS-1$
        }
        this.dicomModel = dicomModel;
        this.dicomSeries = dicomSeries;
        this.seriesInstanceList =
            Optional.ofNullable((SeriesInstanceList) dicomSeries.getTagValue(TagW.WadoInstanceReferenceList))
                .orElseGet(SeriesInstanceList::new);
        this.writeInCache = writeInCache;
        this.progressBar = getBar();
        if (!writeInCache) {
            progressBar.setVisible(false);
        }
        this.dicomSeries.setSeriesLoader(this);
        this.concurrentDownloads = concurrentDownloads;
        this.urlParams = new URLParameters(getHttpTags((WadoParameters) dicomSeries.getTagValue(TagW.WadoParameters)));
        this.startDownloading = startDownloading;
    }

    public LoadSeries(Series<?> dicomSeries, DicomModel dicomModel, JProgressBar progressBar, int concurrentDownloads,
        boolean writeInCache, boolean startDownloading) {
        super(Messages.getString("DicomExplorer.loading"), writeInCache, true); //$NON-NLS-1$
        if (dicomModel == null || dicomSeries == null || progressBar == null) {
            throw new IllegalArgumentException("null parameters"); //$NON-NLS-1$
        }
        this.dicomModel = dicomModel;
        this.dicomSeries = dicomSeries;
        this.seriesInstanceList =
            Optional.ofNullable((SeriesInstanceList) dicomSeries.getTagValue(TagW.WadoInstanceReferenceList))
                .orElseGet(SeriesInstanceList::new);
        this.progressBar = progressBar;
        this.writeInCache = writeInCache;
        this.dicomSeries.setSeriesLoader(this);
        this.concurrentDownloads = concurrentDownloads;
        this.urlParams = new URLParameters(getHttpTags((WadoParameters) dicomSeries.getTagValue(TagW.WadoParameters)));
        this.startDownloading = startDownloading;
    }

    @Override
    protected Boolean doInBackground() {
        return startDownload();
    }

    @Override
    public JProgressBar getProgressBar() {
        return progressBar;
    }

    @Override
    public boolean isStopped() {
        return isCancelled();
    }

    @Override
    public boolean stop() {
        if (!isDone()) {
            boolean val = cancel();
            dicomSeries.setSeriesLoader(this);
            return val;
        }
        return true;
    }

    @Override
    public void resume() {
        if (isStopped()) {
            this.getPriority().setPriority(DownloadPriority.COUNTER.getAndDecrement());
            cancelAndReplace(this);
        }
    }

    @Override
    protected void done() {
        if (!isStopped()) {
            // Ensure to stop downloading and must be set before reusing LoadSeries to download again
            progressBar.setIndeterminate(false);
            this.dicomSeries.setSeriesLoader(null);
            DownloadManager.removeLoadSeries(this, dicomModel);

            LOGGER.info("{} type:{} seriesUID:{} modality:{} nbImages:{} size:{} {}", //$NON-NLS-1$
                new Object[] { AuditLog.MARKER_PERF, getLoadType(), dicomSeries.getTagValue(dicomSeries.getTagID()),
                    TagD.getTagValue(dicomSeries, Tag.Modality, String.class), getImageNumber(),
                    (long) dicomSeries.getFileSize(), getDownloadTime() });
            dicomSeries.removeTag(DOWNLOAD_START_TIME);

            final SeriesThumbnail thumbnail = (SeriesThumbnail) dicomSeries.getTagValue(TagW.Thumbnail);

            if (thumbnail != null) {
                thumbnail.setProgressBar(null);
                if (thumbnail.getThumbnailPath() == null
                    || dicomSeries.getTagValue(TagW.DirectDownloadThumbnail) != null) {
                    thumbnail.reBuildThumbnail(MediaSeries.MEDIA_POSITION.MIDDLE);
                } else {
                    thumbnail.repaint();
                }

            }

            if (DicomModel.isSpecialModality(dicomSeries)) {
                dicomModel.addSpecialModality(dicomSeries);
                List<DicomSpecialElement> list =
                    (List<DicomSpecialElement>) dicomSeries.getTagValue(TagW.DicomSpecialElementList);
                if (list != null) {
                    list.stream().filter(DicomSpecialElement.class::isInstance).map(DicomSpecialElement.class::cast)
                        .findFirst().ifPresent(d -> dicomModel.firePropertyChange(
                            new ObservableEvent(ObservableEvent.BasicAction.UPDATE, dicomModel, null, d)));
                }
            }

            Integer splitNb = (Integer) dicomSeries.getTagValue(TagW.SplitSeriesNumber);
            if (splitNb != null) {
                dicomModel.firePropertyChange(
                    new ObservableEvent(ObservableEvent.BasicAction.UPDATE, dicomModel, null, dicomSeries));
            } else if (dicomSeries.size(null) == 0 && dicomSeries.getTagValue(TagW.DicomSpecialElementList) == null
                && !hasDownloadFailed()) {
                // Remove in case of split Series and all the SopInstanceUIDs already exist
                dicomModel.firePropertyChange(
                    new ObservableEvent(ObservableEvent.BasicAction.REMOVE, dicomModel, null, dicomSeries));
            }
        }
    }

    public boolean hasDownloadFailed() {
        return hasError;
    }

    public boolean isStartDownloading() {
        return startDownloading;
    }

    private String getLoadType() {
        final WadoParameters wado = (WadoParameters) dicomSeries.getTagValue(TagW.WadoParameters);
        if (wado == null || !StringUtil.hasText(wado.getBaseURL())) {
            if (wado != null) {
                return wado.isRequireOnlySOPInstanceUID() ? "DICOMDIR" : "URL"; //$NON-NLS-1$ //$NON-NLS-2$
            }
            return "local"; //$NON-NLS-1$
        } else {
            return "WADO"; //$NON-NLS-1$
        }

    }

    private int getImageNumber() {
        int val = dicomSeries.size(null);
        Integer splitNb = (Integer) dicomSeries.getTagValue(TagW.SplitSeriesNumber);
        if (splitNb != null) {
            MediaSeriesGroup study = dicomModel.getParent(dicomSeries, DicomModel.study);
            if (study != null) {
                String uid = TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class);
                if (uid != null) {
                    Collection<MediaSeriesGroup> list = dicomModel.getChildren(study);
                    list.remove(dicomSeries);
                    for (MediaSeriesGroup s : list) {
                        if (s instanceof Series && uid.equals(TagD.getTagValue(s, Tag.SeriesInstanceUID))) {
                            val += ((Series<?>) s).size(null);
                        }
                    }
                }
            }
        }
        return val;
    }

    private String getDownloadTime() {
        Long val = (Long) dicomSeries.getTagValue(DOWNLOAD_START_TIME);
        long time = val == null ? 0 : System.currentTimeMillis() - val;
        StringBuilder buf = new StringBuilder();
        buf.append("time:"); //$NON-NLS-1$
        buf.append(time);
        buf.append(" rate:"); //$NON-NLS-1$
        // rate in kB/s or B/ms
        DecimalFormat format = new DecimalFormat("#.##", LocalUtil.getDecimalFormatSymbols()); //$NON-NLS-1$
        buf.append(val == null ? 0 : format.format(dicomSeries.getFileSize() / time));
        return buf.toString();
    }

    private boolean isSOPInstanceUIDExist(MediaSeriesGroup study, Series<?> dicomSeries, String sopUID) {
        TagW sopTag = TagD.getUID(Level.INSTANCE);
        if (dicomSeries.hasMediaContains(sopTag, sopUID)) {
            return true;
        }
        // Search in split Series, cannot use "has this series a SplitNumber" because splitting can be executed later
        // for Dicom Video and other special Dicom
        String uid = TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class);
        if (study != null && uid != null) {
            for (MediaSeriesGroup group : dicomModel.getChildren(study)) {
                if (dicomSeries != group && group instanceof Series) {
                    Series s = (Series) group;
                    if (uid.equals(TagD.getTagValue(group, Tag.SeriesInstanceUID))
                        && s.hasMediaContains(sopTag, sopUID)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private void incrementProgressBarValue() {
        GuiExecutor.instance().execute(() -> progressBar.setValue(progressBar.getValue() + 1));
    }

    private Boolean startDownload() {

        MediaSeriesGroup patient = dicomModel.getParent(dicomSeries, DicomModel.patient);
        MediaSeriesGroup study = dicomModel.getParent(dicomSeries, DicomModel.study);
        LOGGER.info("Downloading series of {} [{}]", patient, dicomSeries); //$NON-NLS-1$

        WadoParameters wado = (WadoParameters) dicomSeries.getTagValue(TagW.WadoParameters);
        if (wado == null) {
            return false;
        }

        List<SopInstance> sopList = seriesInstanceList.getSortedList();

        ExecutorService imageDownloader =
            ThreadUtil.buildNewFixedThreadExecutor(concurrentDownloads, "Image Downloader"); //$NON-NLS-1$
        ArrayList<Callable<Boolean>> tasks = new ArrayList<>(sopList.size());
        int[] dindex = generateDownladOrder(sopList.size());
        GuiExecutor.instance().execute(() -> {
            progressBar.setMaximum(sopList.size());
            progressBar.setValue(0);
        });
        for (int k = 0; k < sopList.size(); k++) {
            SopInstance instance = sopList.get(dindex[k]);
            if (isCancelled()) {
                return true;
            }

            if (seriesInstanceList.isContainsMultiframes()
                && seriesInstanceList.getSopInstance(instance.getSopInstanceUID()) != instance) {
                // Do not handle wado query for multiframes
                continue;
            }

            // Test if SOPInstanceUID already exists
            if (isSOPInstanceUIDExist(study, dicomSeries, instance.getSopInstanceUID())) {
                incrementProgressBarValue();
                LOGGER.debug("DICOM instance {} already exists, skip.", instance.getSopInstanceUID()); //$NON-NLS-1$
                continue;
            }

            String studyUID = ""; //$NON-NLS-1$
            String seriesUID = ""; //$NON-NLS-1$
            if (!wado.isRequireOnlySOPInstanceUID()) {
                studyUID = TagD.getTagValue(study, Tag.StudyInstanceUID, String.class);
                seriesUID = TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class);
            }
            StringBuilder request = new StringBuilder(wado.getBaseURL());
            if (instance.getDirectDownloadFile() == null) {
                request.append("?requestType=WADO&studyUID="); //$NON-NLS-1$
                request.append(studyUID);
                request.append("&seriesUID="); //$NON-NLS-1$
                request.append(seriesUID);
                request.append("&objectUID="); //$NON-NLS-1$
                request.append(instance.getSopInstanceUID());
                request.append("&contentType=application%2Fdicom"); //$NON-NLS-1$

                // for dcm4chee: it gets original DICOM files when no TransferSyntax is specified
                String wadoTsuid = (String) dicomSeries.getTagValue(TagW.WadoTransferSyntaxUID);
                if (StringUtil.hasText(wadoTsuid)) {
                    request.append("&transferSyntax="); //$NON-NLS-1$
                    request.append(wadoTsuid);
                    Integer rate = (Integer) dicomSeries.getTagValue(TagW.WadoCompressionRate);
                    if (rate != null && rate > 0) {
                        request.append("&imageQuality="); //$NON-NLS-1$
                        request.append(rate);
                    }
                }
            } else {
                request.append(instance.getDirectDownloadFile());
            }
            request.append(wado.getAdditionnalParameters());
            String url = request.toString();

            LOGGER.debug("Download DICOM instance {} index {}.", url, k); //$NON-NLS-1$
            Download ref = new Download(url);
            tasks.add(ref);
        }

        try {
            dicomSeries.setTag(DOWNLOAD_START_TIME, System.currentTimeMillis());
            imageDownloader.invokeAll(tasks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        imageDownloader.shutdown();
        return true;
    }

    private static Map<String, String> getHttpTags(WadoParameters wadoParams) {
        boolean hasBundleTags = !BundleTools.SESSION_TAGS_FILE.isEmpty();
        boolean hasWadoTags = wadoParams != null && wadoParams.getHttpTaglist() != null;
        boolean hasWadoLogin = wadoParams != null && wadoParams.getWebLogin() != null;

        if (hasWadoTags || hasWadoLogin || hasBundleTags) {
            HashMap<String, String> map = new HashMap<>(BundleTools.SESSION_TAGS_FILE);
            if (hasWadoTags) {
                for (HttpTag tag : wadoParams.getHttpTaglist()) {
                    map.put(tag.getKey(), tag.getValue());
                }
            }
            if (hasWadoLogin) {
                // Set http login (no protection, only convert in base64)
                map.put("Authorization", "Basic " + wadoParams.getWebLogin()); //$NON-NLS-1$ //$NON-NLS-2$
            }
            return map;
        }
        return null;
    }

    public void startDownloadImageReference(final WadoParameters wadoParameters) {
        if (!seriesInstanceList.isEmpty()) {
            // Sort the UIDs for building the thumbnail that is in the middle of the Series
            List<SopInstance> sopList = seriesInstanceList.getSortedList();
            final SopInstance instance = sopList.get(sopList.size() / 2);

            GuiExecutor.instance().execute(() -> {
                SeriesThumbnail thumbnail = (SeriesThumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
                if (thumbnail == null) {
                    thumbnail = new SeriesThumbnail(dicomSeries, Thumbnail.DEFAULT_SIZE);
                }
                // In case series is downloaded or canceled
                thumbnail.setProgressBar(LoadSeries.this.isDone() ? null : progressBar);
                thumbnail.registerListeners();
                addListenerToThumbnail(thumbnail, LoadSeries.this, dicomModel);
                dicomSeries.setTag(TagW.Thumbnail, thumbnail);
                dicomModel.firePropertyChange(
                    new ObservableEvent(ObservableEvent.BasicAction.ADD, dicomModel, null, dicomSeries));
            });

            loadThumbnail(instance, wadoParameters);
        }
    }

    public void loadThumbnail(SopInstance instance, WadoParameters wadoParameters) {
        File file = null;
        URLParameters params = urlParams;
        if (instance.getDirectDownloadFile() == null) {
            String studyUID = ""; //$NON-NLS-1$
            String seriesUID = ""; //$NON-NLS-1$
            if (!wadoParameters.isRequireOnlySOPInstanceUID()) {
                MediaSeriesGroup study = dicomModel.getParent(dicomSeries, DicomModel.study);
                studyUID = TagD.getTagValue(study, Tag.StudyInstanceUID, String.class);
                seriesUID = TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class);
            }
            try {
                file = getJPEGThumnails(wadoParameters, studyUID, seriesUID, instance.getSopInstanceUID());
            } catch (Exception e) {
                LOGGER.error("Downloading thumbnail", e); //$NON-NLS-1$
            }
        } else {
            String thumURL = null;
            String extension = ".jpg";
            if (wadoParameters.isWadoRS()) {
                thumURL = TagD.getTagValue(dicomSeries, Tag.RetrieveURL, String.class);
                if (thumURL != null) {
                    thumURL += "/thumbnail?viewport=" + Thumbnail.MAX_SIZE +"%2C" + + Thumbnail.MAX_SIZE;
                    params = new URLParameters(new HashMap<>(urlParams.getHeaders()));
                    params.getHeaders().put("Accept", "image/jpeg");
                }
            } else {
                thumURL = (String) dicomSeries.getTagValue(TagW.DirectDownloadThumbnail);
                if (thumURL.startsWith(Thumbnail.THUMBNAIL_CACHE_DIR.getPath())) {
                    file = new File(thumURL);
                    thumURL = null;
                } else {
                    thumURL = wadoParameters.getBaseURL() + thumURL;
                    extension = FileUtil.getExtension(thumURL);
                }
            }

            if (thumURL != null) {
                try {
                    File outFile = File.createTempFile("tumb_", extension, //$NON-NLS-1$
                        Thumbnail.THUMBNAIL_CACHE_DIR);
                    ClosableURLConnection httpCon = NetworkUtil.getUrlConnection(thumURL, params);
                    FileUtil.writeStreamWithIOException(httpCon.getInputStream(), outFile);
                    if (outFile.length() == 0) {
                        throw new IllegalStateException("Thumbnail file is empty"); //$NON-NLS-1$
                    }
                    file = outFile;
                } catch (Exception e) {
                    LOGGER.error("Downloading thumbnail with {}", thumURL, e); //$NON-NLS-1$
                }
            }
        }
        if (file != null) {
            final File finalfile = file;
            GuiExecutor.instance().execute(() -> {
                SeriesThumbnail thumbnail = (SeriesThumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
                if (thumbnail != null) {
                    thumbnail.reBuildThumbnail(finalfile, MediaSeries.MEDIA_POSITION.MIDDLE);
                }
            });
        }
    }

    public static void removeThumbnailMouseAndKeyAdapter(Thumbnail tumbnail) {
        MouseListener[] listener = tumbnail.getMouseListeners();
        MouseMotionListener[] motionListeners = tumbnail.getMouseMotionListeners();
        KeyListener[] keyListeners = tumbnail.getKeyListeners();
        MouseWheelListener[] wheelListeners = tumbnail.getMouseWheelListeners();
        for (int i = 0; i < listener.length; i++) {
            if (listener[i] instanceof ThumbnailMouseAndKeyAdapter) {
                tumbnail.removeMouseListener(listener[i]);
            }
        }
        for (int i = 0; i < motionListeners.length; i++) {
            if (motionListeners[i] instanceof ThumbnailMouseAndKeyAdapter) {
                tumbnail.removeMouseMotionListener(motionListeners[i]);
            }
        }
        for (int i = 0; i < wheelListeners.length; i++) {
            if (wheelListeners[i] instanceof ThumbnailMouseAndKeyAdapter) {
                tumbnail.removeMouseWheelListener(wheelListeners[i]);
            }
        }
        for (int i = 0; i < keyListeners.length; i++) {
            if (keyListeners[i] instanceof ThumbnailMouseAndKeyAdapter) {
                tumbnail.removeKeyListener(keyListeners[i]);
            }
        }
    }

    private static void addListenerToThumbnail(final Thumbnail thumbnail, final LoadSeries loadSeries,
        final DicomModel dicomModel) {
        ThumbnailMouseAndKeyAdapter thumbAdapter =
            new ThumbnailMouseAndKeyAdapter(loadSeries.getDicomSeries(), dicomModel, loadSeries);
        thumbnail.addMouseListener(thumbAdapter);
        thumbnail.addKeyListener(thumbAdapter);
        if (thumbnail instanceof SeriesThumbnail) {
            ((SeriesThumbnail) thumbnail).setProgressBar(loadSeries.getProgressBar());
        }
    }

    public Series<?> getDicomSeries() {
        return dicomSeries;
    }

    public File getJPEGThumnails(WadoParameters wadoParameters, String studyUID, String seriesUID,
        String sopInstanceUID) throws IOException {
        // TODO set quality as a preference
        URL url =
            new URL(wadoParameters.getBaseURL() + "?requestType=WADO&studyUID=" + studyUID + "&seriesUID=" + seriesUID //$NON-NLS-1$ //$NON-NLS-2$
                + "&objectUID=" + sopInstanceUID + "&contentType=image/jpeg&imageQuality=70" + "&rows=" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + Thumbnail.MAX_SIZE + "&columns=" + Thumbnail.MAX_SIZE + wadoParameters.getAdditionnalParameters()); //$NON-NLS-1$

        ClosableURLConnection httpCon = NetworkUtil.getUrlConnection(url, urlParams);
        File outFile = File.createTempFile("tumb_", ".jpg", Thumbnail.THUMBNAIL_CACHE_DIR); //$NON-NLS-1$ //$NON-NLS-2$
        LOGGER.debug("Start to download JPEG thbumbnail {} to {}.", url, outFile.getName()); //$NON-NLS-1$
        FileUtil.writeStreamWithIOException(httpCon.getInputStream(), outFile);
        if (outFile.length() == 0) {
            throw new IllegalStateException("Thumbnail file is empty"); //$NON-NLS-1$
        }
        return outFile;
    }

    private int[] generateDownladOrder(final int size) {
        int[] dindex = new int[size];
        if (size < 4) {
            for (int i = 0; i < dindex.length; i++) {
                dindex[i] = i;
            }
            return dindex;
        }
        boolean[] map = new boolean[size];
        int pos = 0;
        dindex[pos++] = 0;
        map[0] = true;
        dindex[pos++] = size - 1;
        map[size - 1] = true;
        int k = (size - 1) / 2;
        dindex[pos++] = k;
        map[k] = true;

        while (k > 0) {
            int i = 1;
            int start = 0;
            while (i < map.length) {
                if (map[i]) {
                    if (!map[i - 1]) {
                        int mid = start + (i - start) / 2;
                        map[mid] = true;
                        dindex[pos++] = mid;
                    }
                    start = i;
                }
                i++;
            }
            k /= 2;
        }
        return dindex;
    }

    class Download implements Callable<Boolean> {

        private final String url; // download URL
        private Status status; // current status of download

        public Download(String url) {
            this.url = url;
            this.status = Status.DOWNLOADING;
        }

        public void pause() {
            status = Status.PAUSED;
        }

        public void resume() {
            status = Status.DOWNLOADING;
        }

        public void cancel() {
            status = Status.CANCELLED;
        }

        private void error() {
            status = Status.ERROR;
        }

        private ClosableURLConnection replaceToDefaultTSUID() throws IOException {
            StringBuilder buffer = new StringBuilder();
            int start = url.indexOf("&transferSyntax="); //$NON-NLS-1$
            if (start != -1) {
                int end = url.indexOf('&', start + 16);
                buffer.append(url.substring(0, start + 16));
                buffer.append(TransferSyntax.EXPLICIT_VR_LE.getTransferSyntaxUID());
                if (end != -1) {
                    buffer.append(url.substring(end));
                }
            } else {
                buffer.append(url);
                buffer.append("&transferSyntax="); //$NON-NLS-1$
                buffer.append(TransferSyntax.EXPLICIT_VR_LE.getTransferSyntaxUID());
            }

            return NetworkUtil.getUrlConnection(new URL(buffer.toString()), urlParams);
        }

        @Override
        public Boolean call() throws Exception {
            try {
                process();
            } catch (StreamIOException es) {
                hasError = true; // network issue (allow to retry)
                error();
                LOGGER.error("Downloading", es); //$NON-NLS-1$
            } catch (IOException | URISyntaxException e) {
                error();
                LOGGER.error("Downloading", e); //$NON-NLS-1$
            }
            return Boolean.TRUE;
        }

        // Solves missing tmp folder problem (on Windows).
        private File getDicomTmpDir() {
            if (!DICOM_TMP_DIR.exists()) {
                LOGGER.info("DICOM tmp dir not foud. Re-creating it!"); //$NON-NLS-1$
                AppProperties.buildAccessibleTempDirectory("downloading"); //$NON-NLS-1$
            }
            return DICOM_TMP_DIR;
        }

        /**
         * Download file.
         *
         * @return
         * @throws IOException
         * @throws URISyntaxException
         */
        private boolean process() throws IOException, URISyntaxException {
            boolean cache = true;
            File tempFile = null;
            DicomMediaIO dicomReader = null;
            ClosableURLConnection urlcon = NetworkUtil.getUrlConnection(new URL(url), urlParams);
            try (InputStream stream = urlcon.getInputStream()) {

                if (!writeInCache && url.startsWith("file:")) { //$NON-NLS-1$
                    cache = false;
                }
                if (cache) {
                    tempFile = File.createTempFile("image_", ".dcm", getDicomTmpDir()); //$NON-NLS-1$ //$NON-NLS-2$
                }

                // Cannot resume with WADO because the stream is modified on the fly by the wado server. In dcm4chee,
                // see
                // http://www.dcm4che.org/jira/browse/DCMEE-421
                progressBar.setIndeterminate(progressBar.getMaximum() < 3);

                if (dicomSeries != null) {
                    if (cache) {
                        LOGGER.debug("Start to download DICOM instance {} to {}.", url, tempFile.getName()); //$NON-NLS-1$
                        int bytesTransferred = downloadInFileCache(urlcon, tempFile);
                        if (bytesTransferred == -1) {
                            LOGGER.info("End of downloading {} ", url); //$NON-NLS-1$
                        } else if (bytesTransferred >= 0) {
                            return false;
                        }

                        File renameFile = new File(DicomMediaIO.DICOM_EXPORT_DIR, tempFile.getName());
                        if (tempFile.renameTo(renameFile)) {
                            tempFile = renameFile;
                        }
                    } else {
                        tempFile = new File(NetworkUtil.getURI(url));
                    }
                    // Ensure the stream is closed if image is not written in cache
                    FileUtil.safeClose(stream);

                    dicomReader = new DicomMediaIO(tempFile);
                    if (dicomReader.isReadableDicom() && dicomSeries.size(null) == 0) {
                        // Override the group (patient, study and series) by the dicom fields except the UID of the
                        // group
                        MediaSeriesGroup patient = dicomModel.getParent(dicomSeries, DicomModel.patient);
                        dicomReader.writeMetaData(patient);
                        MediaSeriesGroup study = dicomModel.getParent(dicomSeries, DicomModel.study);
                        dicomReader.writeMetaData(study);
                        dicomReader.writeMetaData(dicomSeries);
                        GuiExecutor.instance().invokeAndWait(() -> {
                            Thumbnail thumb = (Thumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
                            if (thumb != null) {
                                thumb.repaint();
                            }
                            dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.UDPATE_PARENT,
                                dicomModel, null, dicomSeries));
                        });
                    }
                }
            }

            // Change status to complete if this point was reached because downloading has finished.
            if (status == Status.DOWNLOADING) {
                status = Status.COMPLETE;
                if (tempFile != null && dicomSeries != null && dicomReader.isReadableDicom()) {
                    if (cache) {
                        dicomReader.getFileCache().setOriginalTempFile(tempFile);
                    }
                    final DicomMediaIO reader = dicomReader;
                    // Necessary to wait the runnable because the dicomSeries must be added to the dicomModel
                    // before reaching done() of SwingWorker
                    GuiExecutor.instance().invokeAndWait(() -> updateUI(reader));
                }
            }
            // Increment progress bar in EDT and repaint when downloaded
            incrementProgressBarValue();
            return true;
        }

        private int downloadInFileCache(ClosableURLConnection urlcon, File tempFile) throws IOException {
            final WadoParameters wadoParams = (WadoParameters) dicomSeries.getTagValue(TagW.WadoParameters);
            int[] overrideList =
                Optional.ofNullable(wadoParams).map(WadoParameters::getOverrideDicomTagIDList).orElse(null);

            int bytesTransferred;
            if (overrideList == null) {
                if (wadoParams.isWadoRS()) {
                    int[] readBytes = { 0 };
                    Multipart.Handler handler = new Handler() {

                        @Override
                        public void readBodyPart(MultipartReader multipartReader, int partNumber,
                            Map<String, String> headers) throws IOException {
                            // At sop instance level must have only one part
                            try (InputStream in = multipartReader.newPartInputStream()) {
                                readBytes[0] =
                                    FileUtil.writeStream(new SeriesProgressMonitor(dicomSeries, in), tempFile, false);
                            }
                        }
                    };

                    Multipart.parseMultipartRelated(urlcon.getUrlConnection(), urlcon.getInputStream(), handler);
                    bytesTransferred = readBytes[0];
                } else {
                    bytesTransferred = FileUtil.writeStream(
                        new DicomSeriesProgressMonitor(dicomSeries, urlcon.getInputStream(), false), tempFile);
                }
            } else {
                bytesTransferred = writFile(new DicomSeriesProgressMonitor(dicomSeries, urlcon.getInputStream(), false),
                    tempFile, overrideList);
            }

            if (bytesTransferred == Integer.MIN_VALUE) {
                LOGGER.warn("Stop downloading unsupported TSUID, retry to download non compressed TSUID"); //$NON-NLS-1$
                InputStream stream2 = replaceToDefaultTSUID().getInputStream();
                if (overrideList == null) {
                    bytesTransferred =
                        FileUtil.writeStream(new DicomSeriesProgressMonitor(dicomSeries, stream2, false), tempFile);
                } else {
                    bytesTransferred =
                        writFile(new DicomSeriesProgressMonitor(dicomSeries, stream2, false), tempFile, overrideList);
                }
            }
            return bytesTransferred;
        }

        /**
         * @param in
         * @param tempFile
         * @param overrideList
         * @return bytes transferred. O = error, -1 = all bytes has been transferred, other = bytes transferred before
         *         interruption
         * @throws StreamIOException
         */
        public int writFile(InputStream in, File tempFile, int[] overrideList) throws StreamIOException {
            if (in == null && tempFile == null) {
                return 0;
            }

            DicomInputStream dis = null;
            DicomOutputStream dos = null;

            try {
                String tsuid = null;
                Attributes dataset;
                dis = new DicomInputStream(in);
                try {
                    dis.setIncludeBulkData(IncludeBulkData.URI);
                    dataset = dis.readDataset(-1, -1);
                    tsuid = dis.getTransferSyntax();
                } finally {
                    dis.close();
                }

                dos = new DicomOutputStream(tempFile);

                if (overrideList != null) {
                    MediaSeriesGroup study = dicomModel.getParent(dicomSeries, DicomModel.study);
                    MediaSeriesGroup patient = dicomModel.getParent(dicomSeries, DicomModel.patient);
                    ElementDictionary dic = ElementDictionary.getStandardElementDictionary();

                    for (int tag : overrideList) {
                        TagW tagElement = patient.getTagElement(tag);
                        Object value = null;
                        if (tagElement == null) {
                            tagElement = study.getTagElement(tag);
                            value = study.getTagValue(tagElement);
                        } else {
                            value = patient.getTagValue(tagElement);
                        }

                        DicomMediaUtils.fillAttributes(dataset, tagElement, value, dic);
                    }
                }
                dos.writeDataset(dataset.createFileMetaInformation(tsuid), dataset);
                dos.finish();
                dos.flush();
                return -1;
            } catch (InterruptedIOException e) {
                FileUtil.delete(tempFile);
                LOGGER.error("Interruption when writing file: {}", e.getMessage()); //$NON-NLS-1$
                return e.bytesTransferred;
            } catch (IOException e) {
                FileUtil.delete(tempFile);
                throw new StreamIOException(e);
            } catch (Exception e) {
                FileUtil.delete(tempFile);
                LOGGER.error("Writing DICOM temp file", e); //$NON-NLS-1$
                return 0;
            } finally {
                SafeClose.close(dos);
                if (dis != null) {
                    List<File> blkFiles = dis.getBulkDataFiles();
                    if (blkFiles != null) {
                        for (File file : blkFiles) {
                            FileUtil.delete(file);
                        }
                    }
                }
            }
        }

        private void updateUI(final DicomMediaIO reader) {
            boolean firstImageToDisplay = false;
            MediaElement[] medias = reader.getMediaElement();
            if (medias != null) {
                firstImageToDisplay = dicomSeries.size(null) == 0;
                if (firstImageToDisplay) {
                    MediaSeriesGroup patient = dicomModel.getParent(dicomSeries, DicomModel.patient);
                    if (patient != null) {
                        String dicomPtUID = (String) reader.getTagValue(TagW.PatientPseudoUID);
                        if (!patient.getTagValue(TagW.PatientPseudoUID).equals(dicomPtUID)) {
                            // Fix when patientUID in xml have different patient name
                            dicomModel.mergePatientUID((String) patient.getTagValue(TagW.PatientPseudoUID), dicomPtUID);
                        }
                    }
                }

                for (MediaElement media : medias) {
                    applyPresentationModel(media);
                    dicomModel.applySplittingRules(dicomSeries, media);
                }
                if (firstImageToDisplay && dicomSeries.size(null) == 0) {
                    firstImageToDisplay = false;
                }
            }

            Thumbnail thumb = (Thumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
            if (thumb != null) {
                thumb.repaint();
            }

            if (firstImageToDisplay) {
                boolean openNewTab = true;
                MediaSeriesGroup entry1 = dicomModel.getParent(dicomSeries, DicomModel.patient);
                if (entry1 != null) {
                    synchronized (UIManager.VIEWER_PLUGINS) {
                        for (final ViewerPlugin p : UIManager.VIEWER_PLUGINS) {
                            if (entry1.equals(p.getGroupID())) {
                                if (p instanceof ImageViewerPlugin) {
                                    ViewCanvas pane = ((ImageViewerPlugin) p).getSelectedImagePane();
                                    if (pane != null && pane.getImageLayer() != null
                                        && pane.getImageLayer().getSourceImage() == null) {
                                        // When the selected view has no image send, open in it.
                                        break;
                                    }
                                }
                                openNewTab = false;
                                break;
                            }
                        }
                    }
                }
                if (openNewTab) {
                    SeriesViewerFactory plugin = UIManager.getViewerFactory(dicomSeries.getMimeType());
                    if (plugin != null && !(plugin instanceof MimeSystemAppFactory)) {
                        ViewerPluginBuilder.openSequenceInPlugin(plugin, dicomSeries, dicomModel, true, true);
                    } else if (plugin != null) {
                        // Send event to select the related patient in Dicom Explorer.
                        dicomModel.firePropertyChange(
                            new ObservableEvent(ObservableEvent.BasicAction.SELECT, dicomModel, null, dicomSeries));
                    }
                }
            }
        }
    }

    private void applyPresentationModel(MediaElement media) {
        String sopUID = TagD.getTagValue(media, Tag.SOPInstanceUID, String.class);

        SopInstance sop;
        if (seriesInstanceList.isContainsMultiframes()) {
            sop = seriesInstanceList.getSopInstance(sopUID, TagD.getTagValue(media, Tag.InstanceNumber, Integer.class));
        } else {
            sop = seriesInstanceList.getSopInstance(sopUID);
        }

        if (sop != null && sop.getGraphicModel() instanceof GraphicModel) {
            GraphicModel model = (GraphicModel) sop.getGraphicModel();
            int frames = media.getMediaReader().getMediaElementNumber();
            if (frames > 1 && media.getKey() instanceof Integer) {
                String seriesUID = TagD.getTagValue(media, Tag.SeriesInstanceUID, String.class);

                for (ReferencedSeries s : model.getReferencedSeries()) {
                    if (s.getUuid().equals(seriesUID)) {
                        for (ReferencedImage refImg : s.getImages()) {
                            if (refImg.getUuid().equals(sopUID)) {
                                List<Integer> f = refImg.getFrames();
                                if (f == null || f.contains(media.getKey())) {
                                    media.setTag(TagW.PresentationModel, model);
                                }
                                break;
                            }
                        }
                    }
                }
            } else {
                media.setTag(TagW.PresentationModel, model);
            }
        }
    }

    public synchronized DownloadPriority getPriority() {
        return priority;
    }

    public synchronized void setPriority(DownloadPriority priority) {
        this.priority = priority;
    }

    @Override
    public void setPriority() {
        DownloadPriority p = getPriority();
        if (p != null && StateValue.PENDING.equals(getState())) {
            boolean change = DownloadManager.removeSeriesInQueue(this);
            if (change) {
                // Set the priority to the current loadingSeries and stop a task.
                p.setPriority(DownloadPriority.COUNTER.getAndDecrement());
                DownloadManager.offerSeriesInQueue(this);
                synchronized (DownloadManager.TASKS) {
                    for (LoadSeries s : DownloadManager.TASKS) {
                        if (s != this && StateValue.STARTED.equals(s.getState())) {
                            cancelAndReplace(s);
                            break;
                        }
                    }
                }
            }
        }
    }

    public LoadSeries cancelAndReplace(LoadSeries s) {
        LoadSeries taskResume = new LoadSeries(s.getDicomSeries(), dicomModel, s.getProgressBar(),
            s.getConcurrentDownloads(), s.writeInCache, s.startDownloading);
        s.cancel();
        taskResume.setPriority(s.getPriority());
        Thumbnail thumbnail = (Thumbnail) s.getDicomSeries().getTagValue(TagW.Thumbnail);
        if (thumbnail != null) {
            LoadSeries.removeThumbnailMouseAndKeyAdapter(thumbnail);
            addListenerToThumbnail(thumbnail, taskResume, dicomModel);
        }
        DownloadManager.addLoadSeries(taskResume, dicomModel, true);
        DownloadManager.removeLoadSeries(s, dicomModel);

        return taskResume;
    }

    public int getConcurrentDownloads() {
        return concurrentDownloads;
    }

}
