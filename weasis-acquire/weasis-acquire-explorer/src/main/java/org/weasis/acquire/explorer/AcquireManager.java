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
package org.weasis.acquire.explorer;

import java.awt.Component;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.weasis.acquire.explorer.core.bean.DefaultTagable;
import org.weasis.acquire.explorer.core.bean.Global;
import org.weasis.acquire.explorer.core.bean.SeriesGroup;
import org.weasis.core.api.command.Option;
import org.weasis.core.api.command.Options;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Tagable;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.ClosableURLConnection;
import org.weasis.core.api.util.GzipManager;
import org.weasis.core.api.util.NetworkUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.api.util.URLParameters;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.TagSeq;
import org.weasis.dicom.param.DicomNode;
import org.xml.sax.SAXException;

/**
 *
 * @author Yannick LARVOR
 * @version 2.5.0
 * @since 2.5.0 - 2016-04-13 - ylar - Creation
 *
 */
public class AcquireManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AcquireManager.class);

    public static final List<String> functions = Collections.unmodifiableList(Arrays.asList("patient")); //$NON-NLS-1$
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
        return getAcquireImageInfo(image);
    }

    public static List<AcquireImageInfo> findbySeries(SeriesGroup seriesGroup) {
        return getAcquireImageInfoList().stream()
            .filter(i -> i.getSeries() != null && i.getSeries().equals(seriesGroup)).collect(Collectors.toList());
    }

    public static List<SeriesGroup> getBySeries() {
        return imagesInfoByURI.entrySet().stream().map(e -> e.getValue().getSeries()).filter(Objects::nonNull)
            .distinct().sorted().collect(Collectors.toList());
    }

    public static Map<SeriesGroup, List<AcquireImageInfo>> groupBySeries() {
        return getAcquireImageInfoList().stream().filter(e -> e.getSeries() != null)
            .collect(Collectors.groupingBy(AcquireImageInfo::getSeries));
    }

    public static SeriesGroup getSeries(SeriesGroup searched) {
        return getBySeries().stream().filter(s -> s.equals(searched)).findFirst().orElse(searched);
    }

    public static SeriesGroup getDefaultSeries() {
        return getBySeries().stream().filter(s -> SeriesGroup.Type.NONE.equals(s.getType())).findFirst()
            .orElseGet(SeriesGroup::new);
    }

    public void removeMedias(List<? extends MediaElement> mediaList) {
        removeImages(toAcquireImageInfo(mediaList));
    }

    public void removeAllImages() {
        imagesInfoByURI.clear();
        imagesInfoByUID.clear();
        notifyPatientContextChanged();
    }

    public void removeImages(Collection<AcquireImageInfo> imageCollection) {
        imageCollection.stream().filter(Objects::nonNull).forEach(AcquireManager::removeImageFromDataMapping);
        notifyImagesRemoved(imageCollection);
    }

    public void removeImage(AcquireImageInfo imageElement) {
        Optional.ofNullable(imageElement).ifPresent(AcquireManager::removeImageFromDataMapping);
        notifyImageRemoved(imageElement);
    }

    private static boolean isImageInfoPresent(AcquireImageInfo imageInfo) {
        return Optional.of(imageInfo).map(AcquireImageInfo::getImage).map(ImageElement::getMediaURI)
            .map(imagesInfoByURI::get).isPresent();
    }

    public static void importImages(Collection<AcquireImageInfo> toImport, SeriesGroup searchedSeries,
        int maxRangeInMinutes) {
        if (imagesInfoByURI.isEmpty() || GLOBAL.isAllowFullEdition()) {
            AcquireManager.showWorklist();
        }

        boolean isSearchSeriesByDate = false;
        SeriesGroup commonSeries = null;
        if (searchedSeries != null) {
            isSearchSeriesByDate = SeriesGroup.Type.DATE.equals(searchedSeries.getType());
            commonSeries = isSearchSeriesByDate ? null : getSeries(searchedSeries);
        }

        if (commonSeries == null) {
            commonSeries = getDefaultSeries();
        }

        List<AcquireImageInfo> imageImportedList = new ArrayList<>(toImport.size());

        for (AcquireImageInfo newImageInfo : toImport) {
            if (isImageInfoPresent(newImageInfo)) {
                getInstance().getAcquireExplorer().getCentralPane().tabbedPane.removeImage(newImageInfo);
            } else {
                addImageToDataMapping(newImageInfo);
            }

            SeriesGroup group =
                isSearchSeriesByDate ? findSeries(searchedSeries, newImageInfo, maxRangeInMinutes) : commonSeries;
            if (group.isNeedUpateFromGlobaTags()) {
                group.setNeedUpateFromGlobaTags(false);
                group.updateDicomTags();
            }
            newImageInfo.setSeries(group);

            if (isSearchSeriesByDate) {
                List<AcquireImageInfo> imageInfoList = AcquireManager.findbySeries(newImageInfo.getSeries());
                if (imageInfoList.size() > 2) {
                    recalculateCentralTime(imageInfoList);
                }
            }
            imageImportedList.add(newImageInfo);
        }

        getInstance().notifyImagesAdded(imageImportedList);
    }

    public static void importImage(AcquireImageInfo newImageInfo, SeriesGroup searchedSeries, int maxRangeInMinutes) {
        Objects.requireNonNull(newImageInfo);
        if (imagesInfoByURI.isEmpty() || GLOBAL.isAllowFullEdition()) {
            AcquireManager.showWorklist();
        }

        if (isImageInfoPresent(newImageInfo)) {
            getInstance().getAcquireExplorer().getCentralPane().tabbedPane.removeImage(newImageInfo);
        } else {
            addImageToDataMapping(newImageInfo);
        }
        SeriesGroup group = searchedSeries == null ? getDefaultSeries() : searchedSeries;
        if (group.isNeedUpateFromGlobaTags()) {
            group.setNeedUpateFromGlobaTags(false);
            group.updateDicomTags();
        }
        newImageInfo.setSeries(group);

        getInstance().notifyImageAdded(newImageInfo);
    }

    private static SeriesGroup findSeries(SeriesGroup searchedSeries, AcquireImageInfo imageInfo,
        int maxRangeInMinutes) {

        Objects.requireNonNull(imageInfo, "findSeries imageInfo should not be null"); //$NON-NLS-1$

        if (SeriesGroup.Type.DATE.equals(searchedSeries.getType())) {
            LocalDateTime imageDate = TagD.dateTime(Tag.ContentDate, Tag.ContentTime, imageInfo.getImage());

            Optional<SeriesGroup> series =
                getBySeries().stream().filter(s -> SeriesGroup.Type.DATE.equals(s.getType())).filter(s -> {
                    LocalDateTime start = s.getDate();
                    LocalDateTime end = imageDate;
                    if (end.isBefore(start)) {
                        start = imageDate;
                        end = s.getDate();
                    }
                    Duration duration = Duration.between(start, end);
                    return duration.toMinutes() < maxRangeInMinutes;
                }).findFirst();

            return series.isPresent() ? series.get() : getSeries(new SeriesGroup(imageDate));

        } else {
            return getSeries(searchedSeries);
        }
    }

    private static void recalculateCentralTime(List<AcquireImageInfo> imageInfoList) {
        Objects.requireNonNull(imageInfoList);
        List<AcquireImageInfo> sortedList = imageInfoList.stream()
            .sorted(Comparator.comparing(i -> TagD.dateTime(Tag.ContentDate, Tag.ContentTime, i.getImage())))
            .collect(Collectors.toList());

        AcquireImageInfo info = sortedList.get(sortedList.size() / 2);
        info.getSeries().setDate(TagD.dateTime(Tag.ContentDate, Tag.ContentTime, info.getImage()));
    }

    public static List<ImageElement> toImageElement(List<? extends MediaElement> medias) {
        return medias.stream().filter(ImageElement.class::isInstance).map(ImageElement.class::cast)
            .collect(Collectors.toList());
    }

    public static List<AcquireImageInfo> toAcquireImageInfo(List<? extends MediaElement> medias) {
        return medias.stream().filter(ImageElement.class::isInstance).map(ImageElement.class::cast)
            .map(AcquireManager::findByImage).collect(Collectors.toList());
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

    private void notifyImageRemoved(AcquireImageInfo imageElement) {
        firePropertyChange(
            new ObservableEvent(ObservableEvent.BasicAction.REMOVE, AcquireManager.this, null, imageElement));
    }

    private void notifyImagesRemoved(Collection<AcquireImageInfo> imageCollection) {
        firePropertyChange(
            new ObservableEvent(ObservableEvent.BasicAction.REMOVE, AcquireManager.this, null, imageCollection));
    }

    private void notifyImageAdded(AcquireImageInfo imageInfo) {
        firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.ADD, AcquireManager.this, null, imageInfo));
    }

    private void notifyImagesAdded(Collection<AcquireImageInfo> imageInfoCollection) {
        firePropertyChange(
            new ObservableEvent(ObservableEvent.BasicAction.ADD, AcquireManager.this, null, imageInfoCollection));
    }

    private void notifyPatientContextChanged() {
        firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.REPLACE, AcquireManager.this, null,
            getPatientContextName()));
    }

    private void notifyPatientContextUpdated() {
        firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.UPDATE, AcquireManager.this, null, null));
    }

    private static void showWorklist() {
        String host = BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.acquire.wkl.host"); //$NON-NLS-1$
        String aet = BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.acquire.wkl.aet"); //$NON-NLS-1$
        String port = BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.acquire.wkl.port"); //$NON-NLS-1$
        if (StringUtil.hasText(aet) && StringUtil.hasText(host) && StringUtil.hasText(port)) {
            DicomNode called = new DicomNode(aet, host, Integer.parseInt(port));
            DicomNode calling = new DicomNode(
                BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.acquire.wkl.station.aet", "WEASIS-WL")); //$NON-NLS-1$ //$NON-NLS-2$

            try {
                WorklistDialog dialog = new WorklistDialog(UIManager.getApplicationWindow(),
                    Messages.getString("AcquireManager.dcm_worklist"), calling, called); //$NON-NLS-1$
                JMVUtils.showCenterScreen(dialog);
            } catch (Exception e) {
                LOGGER.error("Cannot get items from worklist", e); //$NON-NLS-1$
            }
        }
    }

    /**
     * Set a new Patient Context and in case current state job is not finished ask user if cleaning unpublished images
     * should be done or canceled.
     *
     * @param argv
     * @throws IOException
     */
    public void patient(String[] argv) throws IOException {
        final String[] usage = { "Load Patient Context from the first argument", //$NON-NLS-1$
            "Usage: acquire:patient (-x | -i | -s | -u) arg", //$NON-NLS-1$
            "arg is an XML text in UTF8 or an url with the option '--url'", //$NON-NLS-1$
            "  -x --xml         Open Patient Context from an XML data containing all DICOM Tags ", //$NON-NLS-1$
            "  -i --inbound     Open Patient Context from an XML data containing all DICOM Tags, decoding syntax is [Base64/GZip]", //$NON-NLS-1$
            "  -s --iurlsafe    Open Patient Context from an XML data containing all DICOM Tags, decoding syntax is [Base64_URL_SAFE/GZip]", //$NON-NLS-1$
            "  -u --url         Open Patient Context from an URL (XML file containing all DICOM TAGs)", //$NON-NLS-1$
            "  -? --help        show help" }; //$NON-NLS-1$

        final Option opt = Options.compile(usage).parse(argv);
        final List<String> args = opt.args();

        if (opt.isSet("help") || args.isEmpty()) { //$NON-NLS-1$
            opt.usage();
            return;
        }

        GuiExecutor.instance().execute(() -> patientCommand(opt, args.get(0)));
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
            applyToGlobal(convert(newPatientContext));
        }
    }

    private DefaultTagable convert(Document xml) {
        DefaultTagable def = new DefaultTagable();
        Optional.ofNullable(xml).map(Document::getDocumentElement).ifPresent(element -> {

            NodeList nodeList = element.getChildNodes();
            if (nodeList != null) {
                for (int i = 0; i < nodeList.getLength(); i++) {
                    Node node = nodeList.item(i);
                    if (node != null) {
                        Optional.ofNullable(TagD.get(node.getNodeName())).ifPresent(t -> readXmlTag(t, node, def));
                    }
                }
            }
        });
        return def;
    }

    private void readXmlTag(TagW tag, Node node, DefaultTagable def) {
        // TODO implement DICOM XML : http://dicom.nema.org/medical/dicom/current/output/chtml/part19/chapter_A.html
        if (tag instanceof TagSeq && node.hasChildNodes()) {
            NodeList nodeList = node.getChildNodes();
            Attributes attributes = new Attributes();
            // FIXME handle only one sequence element
            Attributes[] list = new Attributes[1];
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node n = nodeList.item(i);
                if (n != null) {
                    Optional.ofNullable(TagD.get(n.getNodeName())).ifPresent(t -> attributes.setValue(t.getId(),
                        ElementDictionary.vrOf(t.getId(), null), n.getTextContent()));
                }
            }
            list[0] = attributes.getParent() == null ? attributes : new Attributes(attributes);
            def.setTagNoNull(tag, list);

        } else {
            tag.readValue(node.getTextContent(), def);
        }
    }

    public void applyToGlobal(Tagable tagable) {
        if (tagable != null) {
            if (GLOBAL.containsSameTagValues(tagable, Global.patientDicomGroupNumber)) {
                GLOBAL.updateAllButPatient(tagable);
                getBySeries().stream().forEach(SeriesGroup::updateDicomTags);
                notifyPatientContextUpdated();
            } else {
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
                GLOBAL.init(tagable);
                // Ensure to update all the existing SeriesGroup
                AcquireManager.getInstance().getAcquireExplorer().getCentralPane().tabbedPane
                    .updateSeriesFromGlobaTags();
                notifyPatientContextChanged();
            }
        }
    }

    public Component getExplorerViewComponent() {
        return Optional.ofNullable(acquireExplorer).map(AcquireExplorer::getCentralPane).map(Component.class::cast)
            .orElse(UIManager.getApplicationWindow());
    }

    public AcquireExplorer getAcquireExplorer() {
        return acquireExplorer;
    }

    /**
     * Evaluate if all imported acquired images habe been published without any work in progress state.
     *
     * @return
     */
    private static boolean isAcquireImagesAllPublished() {
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
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Source XML :\n{}", new String(byteArray)); //$NON-NLS-1$
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            return factory.newDocumentBuilder().parse(inputStream);
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
            LOGGER.debug("Download from URL: {}", url); //$NON-NLS-1$
            ClosableURLConnection urlConnection = NetworkUtil.getUrlConnection(url, new URLParameters(BundleTools.SESSION_TAGS_FILE));
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
            LOGGER.error("Downloading URI content", e); //$NON-NLS-1$
        }

        return null;
    }

    private static void addImageToDataMapping(AcquireImageInfo imageInfo) {
        Objects.requireNonNull(imageInfo);
        imagesInfoByURI.put(imageInfo.getImage().getMediaURI(), imageInfo);
        imagesInfoByUID.put((String) imageInfo.getImage().getTagValue(TagD.getUID(Level.INSTANCE)), imageInfo);
    }

    private static void removeImageFromDataMapping(AcquireImageInfo imageInfo) {
        imagesInfoByURI.remove(imageInfo.getImage().getMediaURI());
        imagesInfoByUID.remove(imageInfo.getImage().getTagValue(TagD.getUID(Level.INSTANCE)));
    }

    private static List<AcquireImageInfo> getAcquireImageInfoList() {
        return imagesInfoByURI.entrySet().stream().map(Entry<URI, AcquireImageInfo>::getValue)
            .collect(Collectors.toList());
    }

    /**
     * Get AcquireImageInfo from the data model and create lazily the JAI.PlanarImage if not yet available<br>
     *
     * @note All the AcquireImageInfo value objects are unique according to the imageElement URI
     *
     * @param image
     * @return
     */

    // TODO be careful not to execute this method on the EDT
    private static AcquireImageInfo getAcquireImageInfo(ImageElement image) {
        if (image == null || image.getImage() == null) {
            return null;
        }

        AcquireImageInfo imageInfo = imagesInfoByURI.get(image.getMediaURI());
        if (imageInfo == null) {
            imageInfo = new AcquireImageInfo(image);
        }
        return imageInfo;
    }
}
