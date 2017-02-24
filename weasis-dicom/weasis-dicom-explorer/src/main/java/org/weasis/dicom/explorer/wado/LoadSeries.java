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

import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
import org.weasis.core.api.gui.task.CircularProgressBar;
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
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.api.util.ThreadUtil;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.dicom.codec.DicomInstance;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.TransferSyntax;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.codec.wado.WadoParameters;
import org.weasis.dicom.codec.wado.WadoParameters.HttpTag;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExplorerTask;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.MimeSystemAppFactory;
import org.weasis.dicom.explorer.ThumbnailMouseAndKeyAdapter;

public class LoadSeries extends ExplorerTask implements SeriesImporter {

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
    private final JProgressBar progressBar;
    private volatile DownloadPriority priority = null;
    private final boolean writeInCache;

    private boolean hasError = false;

    public LoadSeries(Series<?> dicomSeries, DicomModel dicomModel, int concurrentDownloads, boolean writeInCache) {
        super(Messages.getString("DicomExplorer.loading"), writeInCache, null, true); //$NON-NLS-1$
        if (dicomModel == null || dicomSeries == null) {
            throw new IllegalArgumentException("null parameters"); //$NON-NLS-1$
        }
        this.dicomModel = dicomModel;
        this.dicomSeries = dicomSeries;
        this.writeInCache = writeInCache;
        final List<DicomInstance> sopList =
            (List<DicomInstance>) dicomSeries.getTagValue(TagW.WadoInstanceReferenceList);
        // Trick to keep progressBar with a final modifier to be instantiated in EDT (required by substance)
        final CircularProgressBar[] bar = new CircularProgressBar[1];
        GuiExecutor.instance().invokeAndWait(() -> bar[0] = new CircularProgressBar(0, sopList.size()));
        this.progressBar = bar[0];
        if (!writeInCache) {
            progressBar.setVisible(false);
        }
        this.dicomSeries.setSeriesLoader(this);
        this.concurrentDownloads = concurrentDownloads;
    }

    public LoadSeries(Series<?> dicomSeries, DicomModel dicomModel, JProgressBar progressBar, int concurrentDownloads,
        boolean writeInCache) {
        super(Messages.getString("DicomExplorer.loading"), writeInCache, null, true); //$NON-NLS-1$
        if (dicomModel == null || dicomSeries == null || progressBar == null) {
            throw new IllegalArgumentException("null parameters"); //$NON-NLS-1$
        }
        this.dicomModel = dicomModel;
        this.dicomSeries = dicomSeries;
        this.progressBar = progressBar;
        this.writeInCache = writeInCache;
        this.dicomSeries.setSeriesLoader(this);
        this.concurrentDownloads = concurrentDownloads;
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
            boolean val = cancel(true);
            dicomSeries.setSeriesLoader(this);
            return val;
        }
        return true;
    }

    @Override
    public void resume() {
        if (isStopped()) {
            LoadSeries taskResume =
                new LoadSeries(dicomSeries, dicomModel, progressBar, concurrentDownloads, writeInCache);
            DownloadPriority p = this.getPriority();
            p.setPriority(DownloadPriority.COUNTER.getAndDecrement());
            taskResume.setPriority(p);
            Thumbnail thumbnail = (Thumbnail) this.getDicomSeries().getTagValue(TagW.Thumbnail);
            if (thumbnail != null) {
                LoadSeries.removeThumbnailMouseAndKeyAdapter(thumbnail);
                ThumbnailMouseAndKeyAdapter thumbAdapter =
                    new ThumbnailMouseAndKeyAdapter(taskResume.getDicomSeries(), dicomModel, taskResume);
                thumbnail.addMouseListener(thumbAdapter);
                thumbnail.addKeyListener(thumbAdapter);
            }
            DownloadManager.addLoadSeries(taskResume, dicomModel, true);
            DownloadManager.removeLoadSeries(this, dicomModel);
        }
    }

    @Override
    protected void done() {
        if (!isStopped()) {
            DownloadManager.removeLoadSeries(this, dicomModel);

            AuditLog.LOGGER.info("{}:series uid:{} modality:{} nbImages:{} size:{} {}", //$NON-NLS-1$
                new Object[] { getLoadType(), dicomSeries.toString(),
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
            } else if (dicomSeries.size(null) == 0 && dicomSeries.getTagValue(TagW.DicomSpecialElementList) == null) {
                // Remove in case of split Series and all the SopInstanceUIDs already exist
                dicomModel.firePropertyChange(
                    new ObservableEvent(ObservableEvent.BasicAction.REMOVE, dicomModel, null, dicomSeries));
            }
            this.dicomSeries.setSeriesLoader(null);
        }
    }

    public boolean hasDownloadFail() {
        return hasError;
    }

    private String getLoadType() {
        final WadoParameters wado = (WadoParameters) dicomSeries.getTagValue(TagW.WadoParameters);
        if (wado == null || !StringUtil.hasText(wado.getWadoURL())) {
            if (wado != null) {
                return wado.isRequireOnlySOPInstanceUID() ? "DICOMDIR" : "URL"; //$NON-NLS-1$ //$NON-NLS-2$
            }
            return "local"; //$NON-NLS-1$
        } else {
            final List<DicomInstance> sopList =
                (List<DicomInstance>) dicomSeries.getTagValue(TagW.WadoInstanceReferenceList);
            if (sopList != null && !sopList.isEmpty() && sopList.get(0).getDirectDownloadFile() != null) {
                return "URL"; //$NON-NLS-1$
            }
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
        DecimalFormat format = new DecimalFormat("#.##"); //$NON-NLS-1$
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

        final List<DicomInstance> sopList =
            (List<DicomInstance>) dicomSeries.getTagValue(TagW.WadoInstanceReferenceList);
        final WadoParameters wado = (WadoParameters) dicomSeries.getTagValue(TagW.WadoParameters);
        if (wado == null) {
            return false;
        }
        ExecutorService imageDownloader =
            ThreadUtil.buildNewFixedThreadExecutor(concurrentDownloads, "Image Downloader"); //$NON-NLS-1$
        ArrayList<Callable<Boolean>> tasks = new ArrayList<>(sopList.size());
        int[] dindex = generateDownladOrder(sopList.size());
        GuiExecutor.instance().execute(() -> progressBar.setValue(0));
        for (int k = 0; k < sopList.size(); k++) {
            DicomInstance instance = sopList.get(dindex[k]);
            if (isCancelled()) {
                return true;
            }
            // Test if SOPInstanceUID already exists
            if (isSOPInstanceUIDExist(study, dicomSeries, instance.getSopInstanceUID())) {
                incrementProgressBarValue();
                LOGGER.debug("DICOM instance {} already exists, skip.", instance.getSopInstanceUID()); //$NON-NLS-1$
                continue;
            }

            URL url = null;
            try {
                String studyUID = ""; //$NON-NLS-1$
                String seriesUID = ""; //$NON-NLS-1$
                if (!wado.isRequireOnlySOPInstanceUID()) {
                    studyUID = TagD.getTagValue(study, Tag.StudyInstanceUID, String.class);
                    seriesUID = TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class);
                }
                StringBuilder request = new StringBuilder(wado.getWadoURL());
                if (instance.getDirectDownloadFile() == null) {
                    request.append("?requestType=WADO&studyUID="); //$NON-NLS-1$
                    request.append(studyUID);
                    request.append("&seriesUID="); //$NON-NLS-1$
                    request.append(seriesUID);
                    request.append("&objectUID="); //$NON-NLS-1$
                    request.append(instance.getSopInstanceUID());
                    request.append("&contentType=application%2Fdicom"); //$NON-NLS-1$
                    TransferSyntax transcoding = DicomManager.getInstance().getWadoTSUID();
                    if (transcoding.getTransferSyntaxUID() != null) {
                        dicomSeries.setTag(TagW.WadoTransferSyntaxUID, transcoding.getTransferSyntaxUID());
                    }
                    // for dcm4chee: it gets original DICOM files when no TransferSyntax is specified
                    String wadoTsuid = (String) dicomSeries.getTagValue(TagW.WadoTransferSyntaxUID);
                    if (StringUtil.hasText(wadoTsuid)) {
                        // On Mac and Win 64 some decoders (JPEGImageReaderCodecLib) are missing, ask for uncompressed
                        // syntax for TSUID: 1.2.840.10008.1.2.4.51, 1.2.840.10008.1.2.4.57
                        // 1.2.840.10008.1.2.4.70 1.2.840.10008.1.2.4.80, 1.2.840.10008.1.2.4.81
                        // Solaris has all the decoders, but no bundle has been built for Weasis
                        if (!TransferSyntax.containsImageioCodec(wadoTsuid)) {
                            wadoTsuid = TransferSyntax.EXPLICIT_VR_LE.getTransferSyntaxUID();
                        }

                        request.append("&transferSyntax="); //$NON-NLS-1$
                        request.append(wadoTsuid);
                        if (transcoding.getTransferSyntaxUID() != null) {
                            dicomSeries.setTag(TagW.WadoCompressionRate, transcoding.getCompression());
                        }
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

                url = new URL(request.toString());

            } catch (MalformedURLException e1) {
                LOGGER.error(e1.getMessage(), e1.getCause());
                continue;
            }
            LOGGER.debug("Download DICOM instance {} index {}.", url, k); //$NON-NLS-1$
            Download ref = new Download(url, wado);
            tasks.add(ref);
        }

        try {
            dicomSeries.setTag(DOWNLOAD_START_TIME, System.currentTimeMillis());
            imageDownloader.invokeAll(tasks);
        } catch (InterruptedException e) {
        }

        imageDownloader.shutdown();
        return true;
    }

    public void startDownloadImageReference(final WadoParameters wadoParameters) {
        final List<DicomInstance> sopList =
            (List<DicomInstance>) dicomSeries.getTagValue(TagW.WadoInstanceReferenceList);
        if (!sopList.isEmpty()) {
            // Sort the UIDs for building the thumbnail that is in the middle of the Series
            Collections.sort(sopList);
            final DicomInstance instance = sopList.get(sopList.size() / 2);

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

    public void loadThumbnail(DicomInstance instance, WadoParameters wadoParameters) {
        File file = null;
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
                LOGGER.error("Error on downloading thbumbnail", e); //$NON-NLS-1$
            }
        } else {
            String thumURL = (String) dicomSeries.getTagValue(TagW.DirectDownloadThumbnail);
            if (thumURL != null) {
                try {
                    if (thumURL.startsWith(Thumbnail.THUMBNAIL_CACHE_DIR.getPath())) {
                        file = new File(thumURL);
                    } else {
                        File outFile = File.createTempFile("tumb_", FileUtil.getExtension(thumURL), //$NON-NLS-1$
                            Thumbnail.THUMBNAIL_CACHE_DIR);
                        int resp = FileUtil.writeFile(new URL(wadoParameters.getWadoURL() + thumURL), outFile);
                        if (resp == -1) {
                            file = outFile;
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Error on getting thbumbnail", e); //$NON-NLS-1$
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
    }

    public Series<?> getDicomSeries() {
        return dicomSeries;
    }

    public File getJPEGThumnails(WadoParameters wadoParameters, String StudyUID, String SeriesUID,
        String SOPInstanceUID) throws IOException {
        // TODO set quality as a preference
        URL url =
            new URL(wadoParameters.getWadoURL() + "?requestType=WADO&studyUID=" + StudyUID + "&seriesUID=" + SeriesUID //$NON-NLS-1$ //$NON-NLS-2$
                + "&objectUID=" + SOPInstanceUID + "&contentType=image/jpeg&imageQuality=70" + "&rows=" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + Thumbnail.MAX_SIZE + "&columns=" + Thumbnail.MAX_SIZE + wadoParameters.getAdditionnalParameters()); //$NON-NLS-1$

        HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
        httpCon.setDoInput(true);
        httpCon.setRequestMethod("GET"); //$NON-NLS-1$
        // Set http login (no protection, only convert in base64)
        if (wadoParameters.getWebLogin() != null) {
            httpCon.setRequestProperty("Authorization", "Basic " + wadoParameters.getWebLogin()); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (!wadoParameters.getHttpTaglist().isEmpty()) {
            for (HttpTag tag : wadoParameters.getHttpTaglist()) {
                httpCon.setRequestProperty(tag.getKey(), tag.getValue());
            }
        }
        // Connect to server.
        httpCon.connect();

        // Make sure response code is in the 200 range.
        if (httpCon.getResponseCode() / 100 != 2) {
            return null;
        }

        File outFile = File.createTempFile("tumb_", ".jpg", Thumbnail.THUMBNAIL_CACHE_DIR); //$NON-NLS-1$ //$NON-NLS-2$
        LOGGER.debug("Start to download JPEG thbumbnail {} to {}.", url, outFile.getName()); //$NON-NLS-1$
        if (FileUtil.writeFile(httpCon, outFile) == 0) {
            return null;
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

        private final URL url; // download URL
        private final WadoParameters wadoParameters;
        private Status status; // current status of download
        private File tempFile;

        public Download(URL url, final WadoParameters wadoParameters) {
            this.url = url;
            this.wadoParameters = wadoParameters;
            status = Status.DOWNLOADING;
        }

        public String getUrl() {
            return url.toString();
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
            hasError = true;
        }

        private String replaceToDefaultTSUID(URL url) {
            String old = url.toString();
            StringBuilder buffer = new StringBuilder();
            int start = old.indexOf("&transferSyntax="); //$NON-NLS-1$
            if (start != -1) {
                int end = old.indexOf("&", start + 16); //$NON-NLS-1$
                buffer.append(old.substring(0, start + 16));
                buffer.append(TransferSyntax.EXPLICIT_VR_LE.getTransferSyntaxUID());
                if (end != -1) {
                    buffer.append(old.substring(end));
                }
            } else {
                buffer.append(old);
                buffer.append("&transferSyntax="); //$NON-NLS-1$
                buffer.append(TransferSyntax.EXPLICIT_VR_LE.getTransferSyntaxUID());
            }
            return buffer.toString();
        }

        private URLConnection initConnection(URL url) throws IOException {
            URLConnection httpCon = null;
            try {
                // If there is a proxy, it should be already configured
                httpCon = url.openConnection();
                // Set http login (no protection, only convert in base64)
                if (wadoParameters.getWebLogin() != null) {
                    httpCon.setRequestProperty("Authorization", "Basic " + wadoParameters.getWebLogin()); //$NON-NLS-1$ //$NON-NLS-2$
                }
                if (!wadoParameters.getHttpTaglist().isEmpty()) {
                    for (HttpTag tag : wadoParameters.getHttpTaglist()) {
                        httpCon.setRequestProperty(tag.getKey(), tag.getValue());
                    }
                }
                // Connect to server.
                httpCon.connect();
            } catch (IOException e) {
                error();
                LOGGER.error("Init connection for {}", url, e); //$NON-NLS-1$
                return null;
            }
            if (httpCon instanceof HttpURLConnection) {
                int responseCode = ((HttpURLConnection) httpCon).getResponseCode();
                // Make sure response code is in the 200 range.
                if (responseCode / 100 != 2) {
                    error();
                    LOGGER.error("Http Response error {} for {}", responseCode, url); //$NON-NLS-1$
                    return null;
                }
            }
            return httpCon;
        }

        // Download file.
        @Override
        public Boolean call() throws Exception {

            InputStream stream;
            URLConnection httpCon = initConnection(url);
            if (httpCon == null) {
                return false;
            }

            boolean cache = true;
            if (!writeInCache && getUrl().startsWith("file:")) { //$NON-NLS-1$
                cache = false;
            }
            if (cache && tempFile == null) {
                tempFile = File.createTempFile("image_", ".dcm", DICOM_TMP_DIR); //$NON-NLS-1$ //$NON-NLS-2$
            }

            stream = httpCon.getInputStream();
            // Cannot resume with WADO because the stream is modified on the fly by the wado server. In dcm4chee, see
            // http://www.dcm4che.org/jira/browse/DCMEE-421
            progressBar.setIndeterminate(progressBar.getMaximum() < 3);

            DicomMediaIO dicomReader = null;
            LOGGER.debug("Start to download DICOM instance {} to {}.", url, cache ? tempFile.getName() : "null"); //$NON-NLS-1$ //$NON-NLS-2$
            if (dicomSeries != null) {
                final WadoParameters wado = (WadoParameters) dicomSeries.getTagValue(TagW.WadoParameters);
                int[] overrideList = wado.getOverrideDicomTagIDList();
                if (cache) {
                    int bytesTransferred = 0;
                    if (overrideList == null && wado != null) {
                        bytesTransferred = FileUtil.writeStream(new DicomSeriesProgressMonitor(dicomSeries, stream,
                            url.toString().contains("?requestType=WADO")), new FileOutputStream(tempFile)); //$NON-NLS-1$
                    } else if (wado != null) {
                        bytesTransferred = writFile(new DicomSeriesProgressMonitor(dicomSeries, stream,
                            url.toString().contains("?requestType=WADO")), tempFile, overrideList); //$NON-NLS-1$
                    }
                    if (bytesTransferred == -1) {
                        LOGGER.info("End of downloading {} ", url); //$NON-NLS-1$
                    } else if (bytesTransferred >= 0) {
                        LOGGER.warn("Download interruption {} ", url); //$NON-NLS-1$
                        FileUtil.delete(tempFile);
                        return false;
                    } else if (bytesTransferred == Integer.MIN_VALUE) {
                        LOGGER.warn("Stop downloading unsupported TSUID, retry to download non compressed TSUID"); //$NON-NLS-1$
                        httpCon = initConnection(new URL(replaceToDefaultTSUID(url)));
                        if (httpCon == null) {
                            return false;
                        }
                        stream = httpCon.getInputStream();
                        if (overrideList == null && wado != null) {
                            bytesTransferred =
                                FileUtil.writeStream(new DicomSeriesProgressMonitor(dicomSeries, stream, false),
                                    new FileOutputStream(tempFile));
                        } else if (wado != null) {
                            bytesTransferred = writFile(new DicomSeriesProgressMonitor(dicomSeries, stream, false),
                                tempFile, overrideList);
                        }
                        if (bytesTransferred == -1) {
                            LOGGER.info("End of downloading {} ", url); //$NON-NLS-1$
                        } else if (bytesTransferred >= 0) {
                            LOGGER.warn("Download interruption {} ", url); //$NON-NLS-1$
                            FileUtil.delete(tempFile);
                            return false;
                        }
                    }
                    File renameFile = new File(DicomMediaIO.DICOM_EXPORT_DIR, tempFile.getName());
                    if (tempFile.renameTo(renameFile)) {
                        tempFile = renameFile;
                    }
                } else {
                    tempFile = new File(url.toURI());
                }
                // Ensure the stream is closed if image is not written in cache
                FileUtil.safeClose(stream);

                dicomReader = new DicomMediaIO(tempFile);
                if (dicomReader.isReadableDicom() && dicomSeries.size(null) == 0) {
                    // Override the group (patient, study and series) by the dicom fields except the UID of the group
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

            // Change status to complete if this point was reached because downloading has finished.
            if (status == Status.DOWNLOADING) {
                status = Status.COMPLETE;
                if (tempFile != null) {
                    if (dicomSeries != null && dicomReader.isReadableDicom()) {
                        if (cache) {
                            dicomReader.getFileCache().setOriginalTempFile(tempFile);
                        }
                        final DicomMediaIO reader = dicomReader;
                        // Necessary to wait the runnable because the dicomSeries must be added to the dicomModel
                        // before reaching done() of SwingWorker
                        GuiExecutor.instance().invokeAndWait(() -> updateUI(reader));
                    }
                }
            }
            // Increment progress bar in EDT and repaint when downloaded
            incrementProgressBarValue();
            return true;
        }

        /**
         * @param in
         * @param tempFile
         * @param overrideList
         * @return bytes transferred. O = error, -1 = all bytes has been transferred, other = bytes transferred before
         *         interruption
         */
        public int writFile(InputStream in, File tempFile, int[] overrideList) {
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
                return e.bytesTransferred;
            } catch (Exception e) {
                LOGGER.error("Error when writing DICOM temp file", e); //$NON-NLS-1$
                return 0;
            } finally {
                SafeClose.close(dos);
                if (dis != null) {
                    List<File> blkFiles = dis.getBulkDataFiles();
                    if (blkFiles != null) {
                        for (File file : blkFiles) {
                            file.delete();
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
                            dicomModel.replacePatientUID((String) patient.getTagValue(TagW.PatientPseudoUID),
                                dicomPtUID);
                        }
                    }
                }

                for (MediaElement media : medias) {
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
                                    if (pane != null && pane.getImageLayer().getSourceImage() == null) {
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
                            LoadSeries taskResume = new LoadSeries(s.getDicomSeries(), dicomModel, s.getProgressBar(),
                                s.getConcurrentDownloads(), s.writeInCache);
                            s.cancel(true);
                            taskResume.setPriority(s.getPriority());
                            Thumbnail thumbnail = (Thumbnail) s.getDicomSeries().getTagValue(TagW.Thumbnail);
                            if (thumbnail != null) {
                                LoadSeries.removeThumbnailMouseAndKeyAdapter(thumbnail);
                                addListenerToThumbnail(thumbnail, taskResume, dicomModel);
                            }
                            DownloadManager.addLoadSeries(taskResume, dicomModel, true);
                            DownloadManager.removeLoadSeries(s, dicomModel);
                            break;
                        }
                    }
                }
            }
        }
    }

    public int getConcurrentDownloads() {
        return concurrentDownloads;
    }

}
