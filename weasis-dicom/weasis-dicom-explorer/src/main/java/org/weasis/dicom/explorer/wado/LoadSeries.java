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
package org.weasis.dicom.explorer.wado;

import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JProgressBar;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
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
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.dicom.codec.DicomInstance;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.TransferSyntax;
import org.weasis.dicom.codec.utils.DicomImageUtils;
import org.weasis.dicom.codec.wado.WadoParameters;
import org.weasis.dicom.codec.wado.WadoParameters.HttpTag;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExplorerTask;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.MimeSystemAppFactory;

public class LoadSeries extends ExplorerTask implements SeriesImporter {

    private static final Logger log = LoggerFactory.getLogger(LoadSeries.class);
    public static final String CONCURRENT_DOWNLOADS_IN_SERIES = "download.concurrent.series.images"; //$NON-NLS-1$

    public static final File DICOM_TMP_DIR = AppProperties.buildAccessibleTempDirectory("downloading"); //$NON-NLS-1$
    public static final TagW DOWNLOAD_START_TIME = new TagW("", TagType.Time, 3); //$NON-NLS-1$

    private static final ExecutorService executor = Executors.newFixedThreadPool(3);

    public enum Status {
        Downloading, Paused, Complete, Cancelled, Error
    };

    public final int concurrentDownloads;
    private final DicomModel dicomModel;
    private final Series dicomSeries;
    private final JProgressBar progressBar;
    private volatile DownloadPriority priority = null;
    private final boolean writeInCache;

    public LoadSeries(Series dicomSeries, DicomModel dicomModel, int concurrentDownloads, boolean writeInCache) {
        super(Messages.getString("DicomExplorer.loading"), writeInCache, null, true); //$NON-NLS-1$
        if (dicomModel == null || dicomSeries == null) {
            throw new IllegalArgumentException("null parameters"); //$NON-NLS-1$
        }
        this.dicomModel = dicomModel;
        this.dicomSeries = dicomSeries;
        this.writeInCache = writeInCache;
        final List<DicomInstance> sopList =
            (List<DicomInstance>) dicomSeries.getTagValue(TagW.WadoInstanceReferenceList);
        // Trick to keep progressBar with a final modifier. The progressBar must be instantiated in EDT (required by
        // substance)
        final CircularProgressBar[] bar = new CircularProgressBar[1];
        GuiExecutor.instance().invokeAndWait(new Runnable() {

            @Override
            public void run() {
                bar[0] = new CircularProgressBar(0, sopList.size());
            }
        });
        this.progressBar = bar[0];
        if (!writeInCache) {
            progressBar.setVisible(false);
        }
        this.dicomSeries.setSeriesLoader(this);
        this.concurrentDownloads = concurrentDownloads;
    }

    public LoadSeries(Series dicomSeries, DicomModel dicomModel, JProgressBar progressBar, int concurrentDownloads,
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
                LoadSeries.removeAnonymousMouseAndKeyListener(thumbnail);
                thumbnail.addMouseListener(
                    DicomExplorer.createThumbnailMouseAdapter(taskResume.getDicomSeries(), dicomModel, taskResume));
                thumbnail
                    .addKeyListener(DicomExplorer.createThumbnailKeyListener(taskResume.getDicomSeries(), dicomModel));
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
                new Object[] { getLoadType(), dicomSeries.toString(), dicomSeries.getTagValue(TagW.Modality),
                    getImageNumber(), (long) dicomSeries.getFileSize(), getDownloadTime() });
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
            }

            Integer splitNb = (Integer) dicomSeries.getTagValue(TagW.SplitSeriesNumber);
            Object dicomObject = dicomSeries.getTagValue(TagW.DicomSpecialElementList);
            if (splitNb != null || dicomObject != null) {
                dicomModel.firePropertyChange(
                    new ObservableEvent(ObservableEvent.BasicAction.Update, dicomModel, null, dicomSeries));
            } else if (dicomSeries.size(null) == 0) {
                // Remove in case of split Series and all the SopInstanceUIDs already exist
                dicomModel.firePropertyChange(
                    new ObservableEvent(ObservableEvent.BasicAction.Remove, dicomModel, null, dicomSeries));
            }
            this.dicomSeries.setSeriesLoader(null);
        }
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
            if (sopList.size() > 0) {
                if (sopList.get(0).getDirectDownloadFile() != null) {
                    return "URL"; //$NON-NLS-1$
                }
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
                String uid = (String) dicomSeries.getTagValue(TagW.SeriesInstanceUID);
                if (uid != null) {
                    Collection<MediaSeriesGroup> list = dicomModel.getChildren(study);
                    list.remove(dicomSeries);
                    for (MediaSeriesGroup s : list) {
                        if (s instanceof Series && uid.equals(s.getTagValue(TagW.SeriesInstanceUID))) {
                            val += ((Series) s).size(null);
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

    private boolean isSOPInstanceUIDExist(MediaSeriesGroup study, Series dicomSeries, String sopUID) {
        if (dicomSeries.hasMediaContains(TagW.SOPInstanceUID, sopUID)) {
            return true;
        }
        // Search in split Series, cannot use "has this series a SplitNumber" because splitting can be executed later
        // for Dicom Video and other special Dicom
        String uid = (String) dicomSeries.getTagValue(TagW.SeriesInstanceUID);
        if (study != null && uid != null) {
            Collection<MediaSeriesGroup> seriesList = dicomModel.getChildren(study);
            for (Iterator<MediaSeriesGroup> it = seriesList.iterator(); it.hasNext();) {
                MediaSeriesGroup group = it.next();
                if (dicomSeries != group && group instanceof Series) {
                    Series s = (Series) group;
                    if (uid.equals(s.getTagValue(TagW.SeriesInstanceUID))) {
                        if (s.hasMediaContains(TagW.SOPInstanceUID, sopUID)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private void incrementProgressBarValue() {
        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                progressBar.setValue(progressBar.getValue() + 1);
            }
        });
    }

    private Boolean startDownload() {

        MediaSeriesGroup patient = dicomModel.getParent(dicomSeries, DicomModel.patient);
        MediaSeriesGroup study = dicomModel.getParent(dicomSeries, DicomModel.study);
        log.info("Downloading series of {} [{}]", patient, dicomSeries); //$NON-NLS-1$

        final List<DicomInstance> sopList =
            (List<DicomInstance>) dicomSeries.getTagValue(TagW.WadoInstanceReferenceList);
        final WadoParameters wado = (WadoParameters) dicomSeries.getTagValue(TagW.WadoParameters);
        if (wado == null) {
            return false;
        }
        ExecutorService imageDownloader = Executors.newFixedThreadPool(concurrentDownloads);
        ArrayList<Callable<Boolean>> tasks = new ArrayList<Callable<Boolean>>(sopList.size());
        int[] dindex = generateDownladOrder(sopList.size());
        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                progressBar.setValue(0);
            }
        });
        for (int k = 0; k < sopList.size(); k++) {
            DicomInstance instance = sopList.get(dindex[k]);
            if (isCancelled()) {
                return true;
            }
            // Test if SOPInstanceUID already exists
            if (isSOPInstanceUIDExist(study, dicomSeries, instance.getSopInstanceUID())) {
                incrementProgressBarValue();
                log.debug("DICOM instance {} already exists, skip.", instance.getSopInstanceUID()); //$NON-NLS-1$
                continue;
            }

            URL url = null;
            try {
                String studyUID = ""; //$NON-NLS-1$
                String seriesUID = ""; //$NON-NLS-1$
                if (!wado.isRequireOnlySOPInstanceUID()) {
                    studyUID = (String) study.getTagValue(TagW.StudyInstanceUID);
                    seriesUID = (String) dicomSeries.getTagValue(TagW.SeriesInstanceUID);
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
                    String wado_tsuid = (String) dicomSeries.getTagValue(TagW.WadoTransferSyntaxUID);
                    if (wado_tsuid != null && !wado_tsuid.equals("")) { //$NON-NLS-1$
                        // On Mac and Win 64 some decoders (JPEGImageReaderCodecLib) are missing, ask for uncompressed
                        // syntax for TSUID: 1.2.840.10008.1.2.4.51, 1.2.840.10008.1.2.4.57
                        // 1.2.840.10008.1.2.4.70 1.2.840.10008.1.2.4.80, 1.2.840.10008.1.2.4.81
                        // Solaris has all the decoders, but no bundle has been built for Weasis
                        if (!DicomImageUtils.hasPlatformNativeImageioCodecs()) {
                            if (TransferSyntax.requiresNativeImageioCodecs(wado_tsuid)) {
                                wado_tsuid = TransferSyntax.EXPLICIT_VR_LE.getTransferSyntaxUID();
                            }
                        }
                        request.append("&transferSyntax="); //$NON-NLS-1$
                        request.append(wado_tsuid);
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
                log.error(e1.getMessage(), e1.getCause());
                continue;
            }
            log.debug("Download DICOM instance {} index {}.", url, k); //$NON-NLS-1$
            Download ref = new Download(url, wado);
            tasks.add(ref);
            // Future future = imageDownloader.submit(ref);
            // try {
            // Object series = future.get();
            //
            // }
            // catch (InterruptedException e) {
            // // Re-assert the thread's interrupted status
            // Thread.currentThread().interrupt();
            // // We don't need the result, so cancel the task too
            // future.cancel(true);
            // }
            // catch (ExecutionException e) {
            //
            // }

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
        if (sopList.size() > 0) {
            // Sort the UIDs for building the thumbnail that is in the middle of
            // the Series
            Collections.sort(sopList, new Comparator<DicomInstance>() {

                @Override
                public int compare(DicomInstance dcm1, DicomInstance dcm2) {
                    int number1 = dcm1.getInstanceNumber();
                    int number2 = dcm2.getInstanceNumber();
                    if (number1 == -1 && number2 == -1) {
                        String str1 = dcm1.getSopInstanceUID();
                        String str2 = dcm2.getSopInstanceUID();
                        int length1 = str1.length();
                        int length2 = str2.length();
                        if (length1 < length2) {
                            char[] c = new char[length2 - length1];
                            for (int i = 0; i < c.length; i++) {
                                c[i] = '0';
                            }
                            int index = str1.lastIndexOf(".") + 1; //$NON-NLS-1$
                            str1 = str1.substring(0, index) + new String(c) + str1.substring(index);
                        } else if (length1 > length2) {
                            char[] c = new char[length1 - length2];
                            for (int i = 0; i < c.length; i++) {
                                c[i] = '0';
                            }
                            int index = str2.lastIndexOf(".") + 1; //$NON-NLS-1$
                            str2 = str2.substring(0, index) + new String(c) + str2.substring(index);
                        }
                        return str1.compareTo(str2);
                    } else {
                        return (number1 < number2 ? -1 : (number1 == number2 ? 0 : 1));
                    }
                }
            });
            final DicomInstance instance = sopList.get(sopList.size() / 2);

            GuiExecutor.instance().execute(new Runnable() {

                @Override
                public void run() {

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
                        new ObservableEvent(ObservableEvent.BasicAction.Add, dicomModel, null, dicomSeries));
                }

            });

            Runnable thumbnailLoader = new Runnable() {

                @Override
                public void run() {
                    String studyUID = ""; //$NON-NLS-1$
                    String seriesUID = ""; //$NON-NLS-1$
                    if (!wadoParameters.isRequireOnlySOPInstanceUID()) {
                        MediaSeriesGroup study = dicomModel.getParent(dicomSeries, DicomModel.study);
                        studyUID = (String) study.getTagValue(TagW.StudyInstanceUID);
                        seriesUID = (String) dicomSeries.getTagValue(TagW.SeriesInstanceUID);
                    }
                    File file = null;
                    if (instance.getDirectDownloadFile() == null) {
                        try {
                            file = getJPEGThumnails(wadoParameters, studyUID, seriesUID, instance.getSopInstanceUID());
                        } catch (Exception e) {
                            e.printStackTrace();
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
                                    int resp =
                                        FileUtil.writeFile(new URL(wadoParameters.getWadoURL() + thumURL), outFile);
                                    if (resp == -1) {
                                        file = outFile;
                                    }
                                }
                            } catch (MalformedURLException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    if (file != null) {
                        final File finalfile = file;
                        GuiExecutor.instance().execute(new Runnable() {

                            @Override
                            public void run() {
                                SeriesThumbnail thumbnail = (SeriesThumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
                                if (thumbnail != null) {
                                    thumbnail.reBuildThumbnail(finalfile, MediaSeries.MEDIA_POSITION.MIDDLE);
                                }
                            }
                        });
                    }
                }
            };
            executor.submit(thumbnailLoader);
        }
    }

    public static void removeAnonymousMouseAndKeyListener(Thumbnail tumbnail) {
        MouseListener[] listener = tumbnail.getMouseListeners();
        MouseMotionListener[] motionListeners = tumbnail.getMouseMotionListeners();
        KeyListener[] keyListeners = tumbnail.getKeyListeners();
        MouseWheelListener[] wheelListeners = tumbnail.getMouseWheelListeners();
        for (int i = 0; i < listener.length; i++) {
            if (listener[i].getClass().isAnonymousClass()) {
                tumbnail.removeMouseListener(listener[i]);
            }
        }
        for (int i = 0; i < motionListeners.length; i++) {
            if (motionListeners[i].getClass().isAnonymousClass()) {
                tumbnail.removeMouseMotionListener(motionListeners[i]);
            }
        }
        for (int i = 0; i < wheelListeners.length; i++) {
            if (wheelListeners[i].getClass().isAnonymousClass()) {
                tumbnail.removeMouseWheelListener(wheelListeners[i]);
            }
        }
        for (int i = 0; i < keyListeners.length; i++) {
            if (keyListeners[i].getClass().isAnonymousClass()) {
                tumbnail.removeKeyListener(keyListeners[i]);
            }
        }
    }

    private static void addListenerToThumbnail(final Thumbnail thumbnail, final LoadSeries loadSeries,
        final DicomModel dicomModel) {
        final Series series = loadSeries.getDicomSeries();
        thumbnail.addMouseListener(DicomExplorer.createThumbnailMouseAdapter(series, dicomModel, loadSeries));
        thumbnail.addKeyListener(DicomExplorer.createThumbnailKeyListener(series, dicomModel));
    }

    public Series getDicomSeries() {
        return dicomSeries;
    }

    // public File getDICOMFile(String StudyUID, String SeriesUID, String
    // SOPInstanceUID) throws Exception {
    // URL url = new URL(dicomSeries.getWadoParameters().getWadoURL() +
    // "?requestType=WADO&studyUID=" + StudyUID
    // + "&seriesUID=" + SeriesUID + "&objectUID=" + SOPInstanceUID +
    // "&contentType=application%2Fdicom");
    //
    // HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
    // // HttpURLConnection httpCon = (HttpURLConnection)
    // // url.openConnection(DownloadManager.PROXY);
    // // httpCon.setDoOutput(true);
    // // httpCon.setDoInput(true);
    // // httpCon.setRequestMethod("GET");
    //
    // // JProgressBar statusBar = new JProgressBar();
    // // statusBar.setMinimum(0);
    // // statusBar.setMaximum(httpCon.getContentLength());
    // // statusBar.setValue(0);
    // // statusBar.setStringPainted(true);
    //
    // OutputStream tempFileStream = null;
    // InputStream httpStream = null;
    // // File outFile = new File(TEMP_DIR + SOPInstanceUID);
    // File tempFile = File.createTempFile("image_", ".dcm",
    // AppProperties.APP_TEMP_DIR);
    // tempFile.deleteOnExit();
    // try {
    // tempFileStream = new BufferedOutputStream(new
    // FileOutputStream(tempFile));
    // httpStream = httpCon.getInputStream();
    // byte[] buffer = new byte[4096];
    // int numRead;
    // long numWritten = 0;
    // while ((numRead = httpStream.read(buffer)) != -1) {
    // tempFileStream.write(buffer, 0, numRead);
    // numWritten += numRead;
    // // statusBar.setValue((int) numWritten);
    // }
    // }
    // catch (Exception e) {
    // e.printStackTrace();
    // }
    // finally {
    // FileUtil.safeClose(tempFileStream);
    // FileUtil.safeClose(httpStream);
    // }
    // return tempFile;
    // }

    public File getJPEGThumnails(WadoParameters wadoParameters, String StudyUID, String SeriesUID,
        String SOPInstanceUID) throws Exception {
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
        if (wadoParameters.getHttpTaglist().size() > 0) {
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

        OutputStream out = null;
        InputStream in = null;

        File outFile = File.createTempFile("tumb_", ".jpg", Thumbnail.THUMBNAIL_CACHE_DIR); //$NON-NLS-1$ //$NON-NLS-2$
        log.debug("Start to download JPEG thbumbnail {} to {}.", url, outFile.getName()); //$NON-NLS-1$
        try {
            out = new BufferedOutputStream(new FileOutputStream(outFile));
            in = httpCon.getInputStream();
            byte[] buffer = new byte[1024];
            int numRead;
            long numWritten = 0;
            while ((numRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, numRead);
                numWritten += numRead;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            FileUtil.safeClose(in);
            FileUtil.safeClose(out);
        }
        return outFile;
    }

    // public void interruptionRequested() {
    // if (isCancelled()) {
    // return;
    // }
    // int response = JOptionPane.showConfirmDialog(WeasisWin.getInstance(),
    // "Do you really want to stop this process?",
    // "Stopping Process", 0);
    // if (response == 0) {
    // cancel(true);
    // }
    // }

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
        private int size; // size of download in bytes
        private final int downloaded; // number of bytes downloaded
        private Status status; // current status of download
        private File tempFile;
        private final WadoParameters wadoParameters;

        // private Thread thread;

        public Download(URL url, final WadoParameters wadoParameters) {
            this.url = url;
            this.wadoParameters = wadoParameters;
            size = -1;
            downloaded = 0;
            status = Status.Downloading;
        }

        public File getTempFile() {
            return tempFile;
        }

        public String getUrl() {
            return url.toString();
        }

        // Get this download's size.
        public int getSize() {
            return size;
        }

        // Get this download's progress.
        public float getProgress() {
            return ((float) downloaded / size) * 100;
        }

        public Status getStatus() {
            return status;
        }

        public void pause() {
            status = Status.Paused;
        }

        public void resume() {
            status = Status.Downloading;
        }

        public void cancel() {
            status = Status.Cancelled;
        }

        private void error() {
            status = Status.Error;
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
                if (wadoParameters.getHttpTaglist().size() > 0) {
                    for (HttpTag tag : wadoParameters.getHttpTaglist()) {
                        httpCon.setRequestProperty(tag.getKey(), tag.getValue());
                    }
                }
                // Specify what portion of file to download.
                httpCon.setRequestProperty("Range", "bytes=" + downloaded + "-"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

                // Connect to server.
                httpCon.connect();
            } catch (IOException e) {
                error();
                log.error("IOException for {}: {} ", url, e.getMessage()); //$NON-NLS-1$
                return null;
            }
            if (httpCon instanceof HttpURLConnection) {
                int responseCode = ((HttpURLConnection) httpCon).getResponseCode();
                // Make sure response code is in the 200 range.
                if (responseCode / 100 != 2) {
                    error();
                    log.error("Http Response error {} for {}", responseCode, url); //$NON-NLS-1$
                    return null;
                }
            }
            return httpCon;
        }

        // Download file.
        @Override
        public Boolean call() throws Exception {

            InputStream stream = null;
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
            // Does not work with WADO because the stream is modified on the fly by the wado server. In dcm4chee, see
            // http://www.dcm4che.org/jira/browse/DCMEE-421
            int contentLength = httpCon.getContentLength();
            contentLength = -1;
            if (contentLength == -1) {
                progressBar.setIndeterminate(progressBar.getMaximum() < 3);
            } else {
                // TODO add external circle progression
            }

            // Set the size for this download if it hasn't been already set.
            if (size == -1) {
                size = contentLength;
                // stateChanged();
            }
            DicomMediaIO dicomReader = null;
            log.debug("Start to download DICOM instance {} to {}.", url, cache ? tempFile.getName() : "null"); //$NON-NLS-1$ //$NON-NLS-2$
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
                        log.info("End of downloading {} ", url); //$NON-NLS-1$
                    } else if (bytesTransferred >= 0) {
                        log.warn("Download interruption {} ", url); //$NON-NLS-1$
                        try {
                            tempFile.delete();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        return false;
                    } else if (bytesTransferred == Integer.MIN_VALUE) {
                        log.warn("Stop downloading unsupported TSUID, retry to download non compressed TSUID"); //$NON-NLS-1$
                        httpCon = initConnection(new URL(replaceToDefaultTSUID(url)));
                        if (httpCon == null) {
                            return false;
                        }
                        stream = httpCon.getInputStream();
                        size = -1;
                        if (overrideList == null && wado != null) {
                            bytesTransferred =
                                FileUtil.writeStream(new DicomSeriesProgressMonitor(dicomSeries, stream, false),
                                    new FileOutputStream(tempFile));
                        } else if (wado != null) {
                            bytesTransferred = writFile(new DicomSeriesProgressMonitor(dicomSeries, stream, false),
                                tempFile, overrideList);
                        }
                        if (bytesTransferred == -1) {
                            log.info("End of downloading {} ", url); //$NON-NLS-1$
                        } else if (bytesTransferred >= 0) {
                            log.warn("Download interruption {} ", url); //$NON-NLS-1$
                            try {
                                tempFile.delete();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
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
                if (dicomReader.isReadableDicom()) {
                    if (dicomSeries.size(null) == 0) {
                        // Override the group (patient, study and series) by the dicom fields except the UID of
                        // the group
                        MediaSeriesGroup patient = dicomModel.getParent(dicomSeries, DicomModel.patient);
                        dicomReader.writeMetaData(patient);
                        MediaSeriesGroup study = dicomModel.getParent(dicomSeries, DicomModel.study);
                        dicomReader.writeMetaData(study);
                        dicomReader.writeMetaData(dicomSeries);
                        GuiExecutor.instance().invokeAndWait(new Runnable() {

                            @Override
                            public void run() {
                                Thumbnail thumb = (Thumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
                                if (thumb != null) {
                                    thumb.repaint();
                                }
                                dicomModel.firePropertyChange(new ObservableEvent(
                                    ObservableEvent.BasicAction.UpdateParent, dicomModel, null, dicomSeries));
                            }
                        });
                    }
                }
            }

            // Change status to complete if this point was reached because
            // downloading has finished.
            if (status == Status.Downloading) {
                status = Status.Complete;
                if (tempFile != null) {
                    if (dicomSeries != null && dicomReader.isReadableDicom()) {
                        final DicomMediaIO reader = dicomReader;
                        // Necessary to wait the runnable because the dicomSeries must be added to the dicomModel
                        // before reaching done() of SwingWorker
                        GuiExecutor.instance().invokeAndWait(new Runnable() {

                            @Override
                            public void run() {
                                boolean firstImageToDisplay = false;
                                MediaElement[] medias = reader.getMediaElement();
                                if (medias != null) {
                                    firstImageToDisplay = dicomSeries.size(null) == 0;
                                    for (MediaElement media : medias) {
                                        dicomModel.applySplittingRules(dicomSeries, media);
                                    }
                                    if (firstImageToDisplay && dicomSeries.size(null) == 0) {
                                        firstImageToDisplay = false;
                                    }
                                }

                                // reader.reset();
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
                                                        DefaultView2d pane =
                                                            ((ImageViewerPlugin) p).getSelectedImagePane();
                                                        if (pane != null
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
                                        SeriesViewerFactory plugin =
                                            UIManager.getViewerFactory(dicomSeries.getMimeType());
                                        if (plugin != null && !(plugin instanceof MimeSystemAppFactory)) {
                                            ViewerPluginBuilder.openSequenceInPlugin(plugin, dicomSeries, dicomModel,
                                                true, true);
                                        } else if (plugin != null) {
                                            // Send event to select the related patient in Dicom Explorer.
                                            dicomModel.firePropertyChange(new ObservableEvent(
                                                ObservableEvent.BasicAction.Select, dicomModel, null, dicomSeries));
                                        }
                                    }
                                }
                            }
                        });

                    }
                    // stateChanged();
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
                    dis.setSkipPrivateTagLength(1000);
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
                        if (value != null) {
                            TagType type = tagElement.getType();
                            if (TagType.String.equals(type)) {
                                dataset.setString(tag, dic.vrOf(tag), value.toString());
                            } else if (TagType.Date.equals(type) || TagType.Time.equals(type)) {
                                dataset.setDate(tag, (Date) value);
                            } else if (TagType.Integer.equals(type)) {
                                dataset.setInt(tag, dic.vrOf(tag), (Integer) value);
                            } else if (TagType.Float.equals(type)) {
                                dataset.setFloat(tag, dic.vrOf(tag), (Float) value);
                            }
                        }
                    }
                }
                dos.writeDataset(dataset.createFileMetaInformation(tsuid), dataset);
                dos.finish();
                dos.flush();
                return -1;
            } catch (InterruptedIOException e) {
                return e.bytesTransferred;
            } catch (Exception e) {
                e.printStackTrace();
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
        if (p != null) {
            if (StateValue.PENDING.equals(getState())) {
                boolean change = DownloadManager.removeSeriesInQueue(this);
                if (change) {
                    // Set the priority to the current loadingSeries and stop a task.
                    p.setPriority(DownloadPriority.COUNTER.getAndDecrement());
                    DownloadManager.offerSeriesInQueue(this);
                    synchronized (DownloadManager.TASKS) {
                        for (LoadSeries s : DownloadManager.TASKS) {
                            if (s != this && StateValue.STARTED.equals(s.getState())) {
                                LoadSeries taskResume = new LoadSeries(s.getDicomSeries(), dicomModel,
                                    s.getProgressBar(), s.getConcurrentDownloads(), s.writeInCache);
                                s.cancel(true);
                                taskResume.setPriority(s.getPriority());
                                Thumbnail thumbnail = (Thumbnail) s.getDicomSeries().getTagValue(TagW.Thumbnail);
                                if (thumbnail != null) {
                                    LoadSeries.removeAnonymousMouseAndKeyListener(thumbnail);
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
    }

    public int getConcurrentDownloads() {
        return concurrentDownloads;
    }

}
