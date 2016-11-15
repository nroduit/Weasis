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
package org.weasis.acquire.explorer;

import java.awt.Component;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.weasis.acquire.explorer.core.bean.Global;
import org.weasis.acquire.explorer.core.bean.SeriesGroup;
import org.weasis.core.api.command.Option;
import org.weasis.core.api.command.Options;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.GzipManager;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.xml.sax.SAXException;

/**
 *
 * @author Yannick LARVOR
 * @version 2.5.0
 * @since 2.5.0 - 2016-04-13 - ylar - Creation
 */
public class AcquireManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AcquireManager.class);

    public static final String[] functions = { "patient" }; //$NON-NLS-1$
    public static final Global GLOBAL = new Global();

    private static final int OPT_NONE = 0;
    private static final int OPT_B64 = 1;
    private static final int OPT_ZIP = 2;
    private static final int OPT_URLSAFE = 4;

    private static final int OPT_B64ZIP = 3;
    private static final int OPT_B64URLSAFE = 5;
    private static final int OPT_B64URLSAFEZIP = 7;

    private static final AcquireManager instance = new AcquireManager();
    private static final Map<String, AcquireImageInfo> imagesInfoByUID = new HashMap<>();
    private static final Map<URI, AcquireImageInfo> imagesInfoByURI = new HashMap<>();

    private AcquireImageInfo currentAcquireImageInfo = null;
    private ViewCanvas<ImageElement> currentView = null;

    private PropertyChangeSupport propertyChange = null;
    private AcquireExplorer acquireExplorer = null;

    private AcquireManager() {
    }

    public static AcquireManager getInstance() {
        return instance;
    }

    public static AcquireImageInfo getCurrentAcquireImageInfo() {
        return getInstance().currentAcquireImageInfo;
    }

    public static void setCurrentAcquireImageInfo(AcquireImageInfo imageInfo) {
        getInstance().currentAcquireImageInfo = imageInfo;
    }

    public static ViewCanvas<ImageElement> getCurrentView() {
        return getInstance().currentView;
    }

    public static void setCurrentView(ViewCanvas<ImageElement> view) {
        getInstance().currentView = view;
        // Remove capabilities to open a view by dragging a thumbnail from the import panel.
        view.getJComponent().setTransferHandler(null);
    }

    public static Collection<AcquireImageInfo> getAllAcquireImageInfo() {
        return imagesInfoByURI.values();
    }

    public static AcquireImageInfo findByUId(String uid) {
        return imagesInfoByUID.get(uid);
    }

    public static AcquireImageInfo findByImage(ImageElement image) {
        return getInstance().getAcquireImageInfo(image);
    }

    public static List<AcquireImageInfo> findbySerie(SeriesGroup seriesGroup) {
        return getAcquireImageInfoList().stream().filter(i -> i.getSeries().equals(seriesGroup))
            .collect(Collectors.toList());
    }

    public static List<SeriesGroup> getBySeries() {
        return imagesInfoByURI.entrySet().stream().map(e -> e.getValue().getSeries()).filter(Objects::nonNull)
            .distinct().sorted().collect(Collectors.toList());
    }

    public static Map<SeriesGroup, List<AcquireImageInfo>> groupBySeries() {
        return getAcquireImageInfoList().stream().collect(Collectors.groupingBy(AcquireImageInfo::getSeries));
    }

    public static SeriesGroup getSeries(SeriesGroup searched) {
        return getBySeries().stream().filter(s -> s.equals(searched)).findFirst().orElse(searched);
    }

    public static SeriesGroup getDefaultSeries() {
        return getBySeries().stream().filter(s -> SeriesGroup.Type.NONE.equals(s.getType())).findFirst()
            .orElseGet(SeriesGroup::new);
    }

    public void removeMedias(List<? extends MediaElement> mediaList) {
        removeImages(toImageElement(mediaList));
    }

    public void removeAllImages() {
        imagesInfoByURI.clear();
        imagesInfoByUID.clear();
        notifyPatientContextChanged();
    }

    public void removeImages(Collection<ImageElement> imageCollection) {
        imageCollection.stream().filter(Objects::nonNull).forEach(this::removeImageFromDataMapping);
        notifyImagesRemoved(imageCollection);
    }

    public void removeImage(ImageElement imageElement) {
        Optional.ofNullable(imageElement).ifPresent(this::removeImageFromDataMapping);
        notifyImageRemoved(imageElement);
    }

    public static void importImages(SeriesGroup searched, List<ImageElement> selected, int maxRangeInMinutes) {
        SeriesGroup seriesGroup = null;

        if (!SeriesGroup.Type.DATE.equals(searched.getType())) {
            seriesGroup = AcquireManager.getSeries(searched);
        }

        for (ImageElement element : selected) {
            AcquireImageInfo imageInfo = AcquireManager.findByImage(element);
            if (imageInfo == null) {
                continue;
            }

            if (SeriesGroup.Type.DATE.equals(searched.getType())) {
                LocalDateTime date = TagD.dateTime(Tag.ContentDate, Tag.ContentTime, imageInfo.getImage());
                Optional<SeriesGroup> ser =
                    getBySeries().stream().filter(s -> SeriesGroup.Type.DATE.equals(s.getType())).filter(s -> {
                        LocalDateTime start = s.getDate();
                        LocalDateTime end = date;
                        if (end.isBefore(start)) {
                            start = date;
                            end = s.getDate();
                        }
                        Duration duration = Duration.between(start, end);
                        return duration.toMinutes() < maxRangeInMinutes;
                    }).findFirst();

                seriesGroup = ser.isPresent() ? ser.get() : AcquireManager.getSeries(new SeriesGroup(date));
                imageInfo.setSeries(seriesGroup);
                if (ser.isPresent()) {
                    List<AcquireImageInfo> list = findbySerie(seriesGroup);
                    if (list.size() > 2) {
                        recalculateCentralTime(list);
                    }
                }
            } else {
                imageInfo.setSeries(seriesGroup);
            }
        }
    }

    private static void recalculateCentralTime(List<AcquireImageInfo> list) {
        List<AcquireImageInfo> sortedList = list.stream()
            .sorted(Comparator.comparing(i -> TagD.dateTime(Tag.ContentDate, Tag.ContentTime, i.getImage())))
            .collect(Collectors.toList());
        AcquireImageInfo info = sortedList.get(sortedList.size() / 2);
        info.getSeries().setDate(TagD.dateTime(Tag.ContentDate, Tag.ContentTime, info.getImage()));
    }

    public static List<ImageElement> toImageElement(List<? extends MediaElement> medias) {
        return medias.stream().filter(ImageElement.class::isInstance).map(ImageElement.class::cast)
            .collect(Collectors.toList());
    }

    public static String getPatientContextName() {
        String patientName =
            TagD.getDicomPersonName(TagD.getTagValue(AcquireManager.GLOBAL, Tag.PatientName, String.class));
        if (!org.weasis.core.api.util.StringUtil.hasLength(patientName)) {
            patientName = TagW.NO_VALUE;
        }
        return patientName;
    }

    public void registerDataExplorerView(AcquireExplorer explorer) {
        unRegisterDataExplorerView();

        if (Objects.nonNull(explorer)) {
            this.acquireExplorer = explorer;
            this.addPropertyChangeListener(explorer);
        }
    }

    public void unRegisterDataExplorerView() {
        if (Objects.nonNull(this.acquireExplorer)) {
            this.removePropertyChangeListener(acquireExplorer);
        }
    }

    private void addPropertyChangeListener(PropertyChangeListener propertychangelistener) {
        if (propertyChange == null) {
            propertyChange = new PropertyChangeSupport(this);
        }
        propertyChange.addPropertyChangeListener(propertychangelistener);
    }

    private void removePropertyChangeListener(PropertyChangeListener propertychangelistener) {
        if (propertyChange != null) {
            propertyChange.removePropertyChangeListener(propertychangelistener);
        }

    }

    private void firePropertyChange(final ObservableEvent event) {
        if (propertyChange != null) {
            if (event == null) {
                throw new NullPointerException();
            }
            if (SwingUtilities.isEventDispatchThread()) {
                propertyChange.firePropertyChange(event);
            } else {
                SwingUtilities.invokeLater(() -> propertyChange.firePropertyChange(event));
            }
        }
    }

    private void notifyImageRemoved(ImageElement imageElement) {
        firePropertyChange(
            new ObservableEvent(ObservableEvent.BasicAction.REMOVE, AcquireManager.this, null, imageElement));
    }

    private void notifyImagesRemoved(Collection<ImageElement> imageCollection) {
        firePropertyChange(
            new ObservableEvent(ObservableEvent.BasicAction.REMOVE, AcquireManager.this, null, imageCollection));
    }

    private void notifyPatientContextChanged() {
        firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.REPLACE, AcquireManager.this, null,
            getPatientContextName()));
    }

    private void notifyPatientContextUpdated() {
        firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.UPDATE, AcquireManager.this, null, null));
    }

    /**
     * Set a new Patient Context and in case current state job is not finished ask user if cleaning unpublished images
     * should be done or canceled.
     *
     * @param argv
     * @throws IOException
     */
    public void patient(String[] argv) throws IOException {
        final String[] usage = { "Load Patient Context", "Usage: acquire:patient [Options] SOURCE", //$NON-NLS-1$ //$NON-NLS-2$
            "  -x --xml       Open Patient Context from an XML data containing all DICOM Tags ", //$NON-NLS-1$
            "  -i --inbound       Open Patient Context from an XML data containing all DICOM Tags, supported syntax is [Base64/GZip]", //$NON-NLS-1$
            "  -s --iurlsafe       Open Patient Context from an XML data containing all DICOM Tags, supported syntax is [Base64_URL_SAFE/GZip]", //$NON-NLS-1$
            "  -u --url       Open Patient Context from an XML (URL) file containing all DICOM TAGs", //$NON-NLS-1$
            "  -? --help       show help" }; //$NON-NLS-1$

        final Option opt = Options.compile(usage).parse(argv);
        final List<String> args = opt.args();

        if (opt.isSet("help") || args.isEmpty()) { //$NON-NLS-1$
            opt.usage();
            return;
        }

        GuiExecutor.instance().execute(() -> {
            patientCommand(opt, args.get(0));
        });
    }

    private void patientCommand(Option opt, String arg) {

        final Document newPatientContext;

        if (opt.isSet("xml")) { //$NON-NLS-1$
            newPatientContext = getPatientContext(arg, OPT_NONE);
        } else if (opt.isSet("inbound")) { //$NON-NLS-1$
            newPatientContext = getPatientContext(arg, OPT_B64ZIP);
        } else if (opt.isSet("iurlsafe")) { //$NON-NLS-1$
            newPatientContext = getPatientContext(arg, OPT_B64URLSAFEZIP);
        } else if (opt.isSet("url")) { //$NON-NLS-1$
            newPatientContext = getPatientContextFromUrl(arg);
        } else {
            newPatientContext = null;
        }

        if (newPatientContext != null) {
            if (!isPatientContextIdentical(newPatientContext)) {

                if (!isAcquireImagesAllPublished()) {
                    if (JOptionPane.showConfirmDialog(getExplorerViewComponent(),
                        Messages.getString("AcquireManager.new_patient_load_warn"), //$NON-NLS-1$
                        Messages.getString("AcquireManager.new_patient_load_title"), //$NON-NLS-1$
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION) {
                        return;
                    }
                }

                imagesInfoByURI.clear();
                imagesInfoByUID.clear();

                GLOBAL.init(newPatientContext);
                notifyPatientContextChanged();
            } else {
                GLOBAL.updateAllButPatient(newPatientContext);
                getBySeries().stream().forEach(SeriesGroup::updateDicomTags);
                notifyPatientContextUpdated();
            }

        }
    }

    public Component getExplorerViewComponent() {
        return Optional.ofNullable(acquireExplorer).map(AcquireExplorer::getCentralPane).map(Component.class::cast)
            .orElse(UIManager.getApplicationWindow());
    }

    /**
     * Evaluates if patientContext currently loaded is identical to the one that's expected to be loaded according to
     * the Dicom Patient Group Only
     *
     * @return
     */

    private static boolean isPatientContextIdentical(Document newPatientContext) {
        return GLOBAL.containsSamePatientTagValues(newPatientContext);
    }

    /**
     * Evaluate if all imported acquired images habe been published without any work in progress state.
     *
     * @return
     */
    private boolean isAcquireImagesAllPublished() {
        return getAllAcquireImageInfo().stream().allMatch(i -> i.getStatus() == AcquireImageStatus.PUBLISHED);
    }

    /**
     *
     * @param inputString
     * @param codeOption
     * @return
     */
    private static Document getPatientContext(String inputString, int codeOption) {
        return getPatientContext(inputString.getBytes(StandardCharsets.UTF_8), codeOption);
    }

    /**
     *
     * @param byteArray
     * @param codeOption
     * @return
     */
    private static Document getPatientContext(byte[] byteArray, int codeOption) {
        if (byteArray == null || byteArray.length == 0) {
            throw new IllegalArgumentException("empty byteArray parameter"); //$NON-NLS-1$
        }

        if (codeOption != OPT_NONE) {
            try {
                if ((codeOption & OPT_B64) == OPT_B64) {
                    if ((codeOption & OPT_URLSAFE) == OPT_URLSAFE) {
                        byteArray = Base64.getUrlDecoder().decode(byteArray);
                    } else {
                        byteArray = Base64.getDecoder().decode(byteArray);
                    }
                }

                if ((codeOption & OPT_ZIP) == OPT_ZIP) {
                    byteArray = GzipManager.gzipUncompressToByte(byteArray);
                }
            } catch (Exception e) {
                LOGGER.error("Decode Patient Context", e); //$NON-NLS-1$
                return null;
            }
        }

        try (InputStream inputStream = new ByteArrayInputStream(byteArray)) {
            LOGGER.debug("Source XML :\n{}", new String(byteArray)); //$NON-NLS-1$

            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inputStream);
        } catch (SAXException | IOException | ParserConfigurationException e) {
            LOGGER.error("Parsing Patient Context XML", e); //$NON-NLS-1$
        }

        return null;
    }

    /**
     *
     * @param uri
     * @return
     */
    private static Document getPatientContextFromUri(URI uri) {
        // TODO could be more secure to limit the loading buffer size !!!
        byte[] byteArray = getURIContent(Objects.requireNonNull(uri));
        String uriPath = uri.getPath();

        if (uriPath.endsWith(".gz") || !(uriPath.endsWith(".xml") //$NON-NLS-1$ //$NON-NLS-2$
            && MimeInspector.isMatchingMimeTypeFromMagicNumber(byteArray, "application/x-gzip"))) { //$NON-NLS-1$
            return getPatientContext(byteArray, OPT_ZIP);
        } else {
            return getPatientContext(byteArray, OPT_NONE);
        }
    }

    /**
     *
     * @param url
     * @return
     */
    private static Document getPatientContextFromUrl(String url) {
        return getPatientContextFromUri(getURIFromURL(url));
    }

    /**
     *
     * @param urlStr
     * @return
     */
    private static URI getURIFromURL(String urlStr) {
        if (!StringUtil.hasText(urlStr)) {
            throw new IllegalArgumentException("empty urlString parameter"); //$NON-NLS-1$
        }

        URI uri = null;

        if (!urlStr.startsWith("http")) { //$NON-NLS-1$
            try {
                File file = new File(urlStr);
                if (file.canRead()) {
                    uri = file.toURI();
                }
            } catch (Exception e) {
                LOGGER.error("{} is supposed to be a file URL but cannot be converted to a valid URI", urlStr, e); //$NON-NLS-1$
            }
        }
        if (uri == null) {
            try {
                uri = new URL(urlStr).toURI();
            } catch (MalformedURLException | URISyntaxException e) {
                LOGGER.error("getURIFromURL : {}", urlStr, e); //$NON-NLS-1$
            }
        }

        return uri;
    }

    /**
     *
     * @param uri
     * @return
     */
    private static byte[] getURIContent(URI uri) {
        try {
            URL url = Objects.requireNonNull(uri).toURL();
            URLConnection urlConnection = url.openConnection();

            LOGGER.debug("download from URL: {}", url); //$NON-NLS-1$
            logHttpError(urlConnection);

            // TODO urlConnection.setRequestProperty with session TAG ??
            // @see >> org.weasis.dicom.explorer.wado.downloadmanager.buildDicomSeriesFromXml

            // note: fastest way to convert inputStream to string according to :
            // http://stackoverflow.com/questions/309424/read-convert-an-inputstream-to-a-string
            try (InputStream inputStream = urlConnection.getInputStream()) {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, length);
                }
                return outputStream.toByteArray();
            }

        } catch (Exception e) {
            LOGGER.error("getURIContent from : {}", uri.getPath(), e); //$NON-NLS-1$
        }

        return null;
    }

    /**
     * @param urlConnection
     */
    private static void logHttpError(URLConnection urlConnection) {
        // TODO same method as in dicom.explorer.wado.downloadmanager => move this in a common place

        if (urlConnection instanceof HttpURLConnection) {
            HttpURLConnection httpURLConnection = (HttpURLConnection) urlConnection;
            httpURLConnection.setConnectTimeout(5000);

            try {
                int responseCode = httpURLConnection.getResponseCode();
                if (responseCode < HttpURLConnection.HTTP_OK || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                    // Following is only intended LOG more info about Http
                    // Server Error

                    InputStream errorStream = httpURLConnection.getErrorStream();
                    if (errorStream != null) {
                        try (InputStreamReader inputStream = new InputStreamReader(errorStream, "UTF-8"); //$NON-NLS-1$
                                        BufferedReader reader = new BufferedReader(inputStream)) {
                            StringBuilder stringBuilder = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                stringBuilder.append(line);
                            }
                            String errorDescription = stringBuilder.toString();
                            if (StringUtil.hasText(errorDescription)) {
                                LOGGER.warn("HttpURLConnection - HTTP Status {} - {}", responseCode + " [" //$NON-NLS-1$//$NON-NLS-2$
                                    + httpURLConnection.getResponseMessage() + "]", errorDescription); //$NON-NLS-1$
                            }
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.error("LOG http response message", e); //$NON-NLS-1$
            }
        }
    }

    private void addImageToDataMapping(AcquireImageInfo imageInfo) {
        Objects.requireNonNull(imageInfo);
        imagesInfoByURI.put(imageInfo.getImage().getMediaURI(), imageInfo);
        imagesInfoByUID.put((String) imageInfo.getImage().getTagValue(TagD.getUID(Level.INSTANCE)), imageInfo);
    }

    private void removeImageFromDataMapping(ImageElement image) {
        imagesInfoByURI.remove(image.getMediaURI());
        imagesInfoByUID.remove(image.getTagValue(TagD.getUID(Level.INSTANCE)));
    }

    private static List<AcquireImageInfo> getAcquireImageInfoList() {
        return imagesInfoByURI.entrySet().stream().map(e -> e.getValue()).collect(Collectors.toList());
    }

    /**
     * Get AcquireImageInfo from the data model and create it lazily if not yet available<br>
     * All the AcquireImageInfo value objects are unique according to the imageElement URI
     *
     * @param image
     * @return
     */
    private AcquireImageInfo getAcquireImageInfo(ImageElement image) {
        if (image == null || image.getImage() == null) {
            return null;
        }

        AcquireImageInfo imageInfo = imagesInfoByURI.get(image.getMediaURI());
        if (imageInfo == null) {
            imageInfo = new AcquireImageInfo(image);
            addImageToDataMapping(imageInfo);
        }
        return imageInfo;
    }
}
