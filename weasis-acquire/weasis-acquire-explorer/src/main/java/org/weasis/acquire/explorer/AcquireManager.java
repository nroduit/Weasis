/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
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
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.weasis.acquire.explorer.core.bean.DefaultTaggable;
import org.weasis.acquire.explorer.core.bean.Global;
import org.weasis.acquire.explorer.core.bean.SeriesGroup;
import org.weasis.core.api.command.Option;
import org.weasis.core.api.command.Options;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Taggable;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.ClosableURLConnection;
import org.weasis.core.api.util.GzipManager;
import org.weasis.core.api.util.NetworkUtil;
import org.weasis.core.api.util.URLParameters;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.layer.GraphicLayer;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.TagSeq;
import org.weasis.dicom.param.DicomNode;
import org.xml.sax.SAXException;

/**
 * @author Yannick LARVOR
 * @since 2.5.0
 */
public class AcquireManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(AcquireManager.class);

  public static final List<String> functions = Collections.singletonList("patient"); // NON-NLS
  public static final Global GLOBAL = new Global();

  private static final int OPT_NONE = 0;
  private static final int OPT_B64 = 1;
  private static final int OPT_ZIP = 2;
  private static final int OPT_URL_SAFE = 4;

  private static final int OPT_B64ZIP = 3;
  private static final int OPT_B64URL_SAFE = 5;
  private static final int OPT_B64URL_SAFE_ZIP = 7;

  private static final AcquireManager instance = new AcquireManager();
  private static final Map<String, AcquireImageInfo> imagesInfoByUID = new HashMap<>();
  private static final Map<URI, AcquireImageInfo> imagesInfoByURI = new HashMap<>();

  private AcquireImageInfo currentAcquireImageInfo = null;
  private ViewCanvas<ImageElement> currentView = null;

  private PropertyChangeSupport propertyChange = null;
  private AcquireExplorer acquireExplorer = null;

  private AcquireManager() {}

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
    if (view != null) {
      // Remove capabilities to open a view by dragging a thumbnail from the import panel.
      view.getJComponent().setTransferHandler(null);
    }
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

  public static List<AcquireImageInfo> findBySeries(SeriesGroup seriesGroup) {
    return getAcquireImageInfoList().stream()
        .filter(i -> i.getSeries() != null && i.getSeries().equals(seriesGroup))
        .collect(Collectors.toList());
  }

  public static List<SeriesGroup> getBySeries() {
    return imagesInfoByURI.values().stream()
        .map(AcquireImageInfo::getSeries)
        .filter(Objects::nonNull)
        .distinct()
        .sorted()
        .collect(Collectors.toList());
  }

  public static Map<SeriesGroup, List<AcquireImageInfo>> groupBySeries() {
    return getAcquireImageInfoList().stream()
        .filter(e -> e.getSeries() != null)
        .collect(Collectors.groupingBy(AcquireImageInfo::getSeries));
  }

  public static SeriesGroup getSeries(SeriesGroup searched) {
    return getBySeries().stream().filter(s -> s.equals(searched)).findFirst().orElse(searched);
  }

  public static SeriesGroup getDefaultSeries() {
    return getBySeries().stream()
        .filter(s -> SeriesGroup.Type.NONE.equals(s.getType()))
        .findFirst()
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
    imageCollection.stream()
        .filter(Objects::nonNull)
        .forEach(AcquireManager::removeImageFromDataMapping);
    notifyImagesRemoved(imageCollection);
  }

  public void removeImage(AcquireImageInfo imageElement) {
    Optional.ofNullable(imageElement).ifPresent(AcquireManager::removeImageFromDataMapping);
    notifyImageRemoved(imageElement);
  }

  private static boolean isImageInfoPresent(AcquireImageInfo imageInfo) {
    return Optional.of(imageInfo)
        .map(AcquireImageInfo::getImage)
        .map(ImageElement::getMediaURI)
        .map(imagesInfoByURI::get)
        .isPresent();
  }

  public static void importImages(
      Collection<AcquireImageInfo> toImport, SeriesGroup searchedSeries, int maxRangeInMinutes) {
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
          isSearchSeriesByDate
              ? findSeries(searchedSeries, newImageInfo, maxRangeInMinutes)
              : commonSeries;
      if (group.isNeedUpdateFromGlobalTags()) {
        group.setNeedUpdateFromGlobalTags(false);
        group.updateDicomTags();
      }
      newImageInfo.setSeries(group);

      if (isSearchSeriesByDate) {
        List<AcquireImageInfo> imageInfoList =
            AcquireManager.findBySeries(newImageInfo.getSeries());
        if (imageInfoList.size() > 2) {
          recalculateCentralTime(imageInfoList);
        }
      }
      imageImportedList.add(newImageInfo);
    }

    getInstance().notifyImagesAdded(imageImportedList);
  }

  public static void importImage(AcquireImageInfo newImageInfo, SeriesGroup searchedSeries) {
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
    if (group.isNeedUpdateFromGlobalTags()) {
      group.setNeedUpdateFromGlobalTags(false);
      group.updateDicomTags();
    }
    newImageInfo.setSeries(group);

    getInstance().notifyImageAdded(newImageInfo);
  }

  private static SeriesGroup findSeries(
      SeriesGroup searchedSeries, AcquireImageInfo imageInfo, int maxRangeInMinutes) {

    Objects.requireNonNull(imageInfo, "findSeries imageInfo should not be null");

    if (SeriesGroup.Type.DATE.equals(searchedSeries.getType())) {
      LocalDateTime imageDate =
          TagD.dateTime(Tag.ContentDate, Tag.ContentTime, imageInfo.getImage());

      Optional<SeriesGroup> series =
          getBySeries().stream()
              .filter(s -> SeriesGroup.Type.DATE.equals(s.getType()))
              .filter(
                  s -> {
                    LocalDateTime start = s.getDate();
                    LocalDateTime end = imageDate;
                    if (end != null && end.isBefore(start)) {
                      start = imageDate;
                      end = s.getDate();
                    }
                    Duration duration = Duration.between(start, end);
                    return duration.toMinutes() < maxRangeInMinutes;
                  })
              .findFirst();

      return series.orElseGet(() -> getSeries(new SeriesGroup(imageDate)));

    } else {
      return getSeries(searchedSeries);
    }
  }

  private static void recalculateCentralTime(List<AcquireImageInfo> imageInfoList) {
    Objects.requireNonNull(imageInfoList);
    List<AcquireImageInfo> sortedList =
        imageInfoList.stream()
            .sorted(
                Comparator.comparing(
                    i -> {
                      LocalDateTime val =
                          TagD.dateTime(Tag.ContentDate, Tag.ContentTime, i.getImage());
                      if (val == null) {
                        val = LocalDateTime.now();
                      }
                      return val;
                    }))
            .toList();

    AcquireImageInfo info = sortedList.get(sortedList.size() / 2);
    info.getSeries().setDate(TagD.dateTime(Tag.ContentDate, Tag.ContentTime, info.getImage()));
  }

  public static List<ImageElement> toImageElement(List<? extends MediaElement> medias) {
    return medias.stream()
        .filter(ImageElement.class::isInstance)
        .map(ImageElement.class::cast)
        .collect(Collectors.toList());
  }

  public static List<AcquireImageInfo> toAcquireImageInfo(List<? extends MediaElement> medias) {
    return medias.stream()
        .filter(ImageElement.class::isInstance)
        .map(ImageElement.class::cast)
        .map(AcquireManager::findByImage)
        .collect(Collectors.toList());
  }

  public static String getPatientContextName() {
    String patientName =
        TagD.getDicomPersonName(
            TagD.getTagValue(AcquireManager.GLOBAL, Tag.PatientName, String.class));
    if (!org.weasis.core.util.StringUtil.hasLength(patientName)) {
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
        new ObservableEvent(
            ObservableEvent.BasicAction.REMOVE, AcquireManager.this, null, imageElement));
  }

  private void notifyImagesRemoved(Collection<AcquireImageInfo> imageCollection) {
    firePropertyChange(
        new ObservableEvent(
            ObservableEvent.BasicAction.REMOVE, AcquireManager.this, null, imageCollection));
  }

  private void notifyImageAdded(AcquireImageInfo imageInfo) {
    firePropertyChange(
        new ObservableEvent(ObservableEvent.BasicAction.ADD, AcquireManager.this, null, imageInfo));
  }

  private void notifyImagesAdded(Collection<AcquireImageInfo> imageInfoCollection) {
    firePropertyChange(
        new ObservableEvent(
            ObservableEvent.BasicAction.ADD, AcquireManager.this, null, imageInfoCollection));
  }

  private void notifyPatientContextChanged() {
    firePropertyChange(
        new ObservableEvent(
            ObservableEvent.BasicAction.REPLACE,
            AcquireManager.this,
            null,
            getPatientContextName()));
  }

  private void notifyPatientContextUpdated() {
    firePropertyChange(
        new ObservableEvent(ObservableEvent.BasicAction.UPDATE, AcquireManager.this, null, null));
  }

  private static void showWorklist() {
    String host = BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.acquire.wkl.host");
    String aet = BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.acquire.wkl.aet");
    String port = BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.acquire.wkl.port");
    if (StringUtil.hasText(aet) && StringUtil.hasText(host) && StringUtil.hasText(port)) {
      DicomNode called = new DicomNode(aet, host, Integer.parseInt(port));
      DicomNode calling =
          new DicomNode(
              BundleTools.SYSTEM_PREFERENCES.getProperty(
                  "weasis.acquire.wkl.station.aet", "WEASIS-WL"));

      try {
        WorklistDialog dialog =
            new WorklistDialog(
                UIManager.getApplicationWindow(),
                Messages.getString("AcquireManager.dcm_worklist"),
                calling,
                called);
        GuiUtils.showCenterScreen(dialog);
      } catch (Exception e) {
        LOGGER.error("Cannot get items from worklist", e);
      }
    }
  }

  /**
   * Set a new Patient Context and in case current state job is not finished ask user if cleaning
   * unpublished images should be done or canceled.
   *
   * @param argv the main arguments
   */
  public void patient(String[] argv) {
    final String[] usage = {
      "Load Patient Context from the first argument", // NON-NLS
      "Usage: acquire:patient (-x | -i | -s | -u) arg", // NON-NLS
      "arg is an XML text in UTF8 or an url with the option '--url'", // NON-NLS
      "  -x --xml         Open Patient Context from an XML data containing all DICOM Tags ", // NON-NLS
      "  -i --inbound     Open Patient Context from an XML data containing all DICOM Tags, decoding syntax is [Base64/GZip]", // NON-NLS
      "  -s --iurlsafe    Open Patient Context from an XML data containing all DICOM Tags, decoding syntax is [Base64_URL_SAFE/GZip]", // NON-NLS
      "  -u --url         Open Patient Context from an URL (XML file containing all DICOM TAGs)", // NON-NLS
      "  -? --help        show help" // NON-NLS
    };

    final Option opt = Options.compile(usage).parse(argv);
    final List<String> args = opt.args();

    if (opt.isSet("help") || args.isEmpty()) {
      opt.usage();
      return;
    }

    GuiExecutor.instance().execute(() -> patientCommand(opt, args.get(0)));
  }

  private void patientCommand(Option opt, String arg) {

    final Document newPatientContext;

    if (opt.isSet("xml")) {
      newPatientContext = getPatientContext(arg, OPT_NONE);
    } else if (opt.isSet("inbound")) { // NON-NLS
      newPatientContext = getPatientContext(arg, OPT_B64ZIP);
    } else if (opt.isSet("iurlsafe")) { // NON-NLS
      newPatientContext = getPatientContext(arg, OPT_B64URL_SAFE_ZIP);
    } else if (opt.isSet("url")) { // NON-NLS
      newPatientContext = getPatientContextFromUrl(arg);
    } else {
      newPatientContext = null;
    }

    if (newPatientContext != null) {
      applyToGlobal(convert(newPatientContext));
    }
  }

  private DefaultTaggable convert(Document xml) {
    DefaultTaggable def = new DefaultTaggable();
    Optional.ofNullable(xml)
        .map(Document::getDocumentElement)
        .ifPresent(
            element -> {
              NodeList nodeList = element.getChildNodes();
              for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node != null) {
                  Optional.ofNullable(TagD.get(node.getNodeName()))
                      .ifPresent(t -> readXmlTag(t, node, def));
                }
              }
            });
    return def;
  }

  private void readXmlTag(TagW tag, Node node, DefaultTaggable def) {
    // TODO implement DICOM XML :
    // http://dicom.nema.org/medical/dicom/current/output/chtml/part19/chapter_A.html
    if (tag instanceof TagSeq && node.hasChildNodes()) {
      NodeList nodeList = node.getChildNodes();
      Attributes attributes = new Attributes();
      // FIXME handle only one sequence element
      Attributes[] list = new Attributes[1];
      for (int i = 0; i < nodeList.getLength(); i++) {
        Node n = nodeList.item(i);
        if (n != null) {
          Optional.ofNullable(TagD.get(n.getNodeName()))
              .ifPresent(
                  t ->
                      attributes.setValue(
                          t.getId(), ElementDictionary.vrOf(t.getId(), null), n.getTextContent()));
        }
      }
      list[0] = attributes.getParent() == null ? attributes : new Attributes(attributes);
      def.setTagNoNull(tag, list);

    } else {
      tag.readValue(node.getTextContent(), def);
    }
  }

  public void applyToGlobal(Taggable taggable) {
    if (taggable != null) {
      if (GLOBAL.containsSameTagValues(taggable, Global.PATIENT_DICOM_GROUP_NUMBER)) {
        GLOBAL.updateAllButPatient(taggable);
        getBySeries().forEach(SeriesGroup::updateDicomTags);
        notifyPatientContextUpdated();
      } else {
        if (!isAcquireImagesAllPublished()
            && JOptionPane.showConfirmDialog(
                    getExplorerViewComponent(),
                    Messages.getString("AcquireManager.new_patient_load_warn"),
                    Messages.getString("AcquireManager.new_patient_load_title"),
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE)
                != JOptionPane.OK_OPTION) {
          return;
        }

        imagesInfoByURI.clear();
        imagesInfoByUID.clear();
        GLOBAL.init(taggable);
        // Ensure to update all the existing SeriesGroup
        AcquireManager.getInstance()
            .getAcquireExplorer()
            .getCentralPane()
            .tabbedPane
            .updateSeriesFromGlobalTags();
        notifyPatientContextChanged();
      }
    }
  }

  public Component getExplorerViewComponent() {
    return Optional.ofNullable(acquireExplorer)
        .map(AcquireExplorer::getCentralPane)
        .map(Component.class::cast)
        .orElse(UIManager.getApplicationWindow());
  }

  public AcquireExplorer getAcquireExplorer() {
    return acquireExplorer;
  }

  /**
   * Evaluate if all imported acquired images have been published without any work in progress
   * state.
   *
   * @return true when all images have been published
   */
  private static boolean isAcquireImagesAllPublished() {
    return getAllAcquireImageInfo().stream()
        .allMatch(i -> i.getStatus() == AcquireImageStatus.PUBLISHED);
  }

  private static Document getPatientContext(String inputString, int codeOption) {
    return getPatientContext(inputString.getBytes(StandardCharsets.UTF_8), codeOption);
  }

  private static Document getPatientContext(byte[] byteArray, int codeOption) {
    if (byteArray == null || byteArray.length == 0) {
      throw new IllegalArgumentException("empty byteArray parameter");
    }

    if (codeOption != OPT_NONE) {
      try {
        if ((codeOption & OPT_B64) == OPT_B64) {
          if ((codeOption & OPT_URL_SAFE) == OPT_URL_SAFE) {
            byteArray = Base64.getUrlDecoder().decode(byteArray);
          } else {
            byteArray = Base64.getDecoder().decode(byteArray);
          }
        }

        if ((codeOption & OPT_ZIP) == OPT_ZIP) {
          byteArray = GzipManager.gzipUncompressToByte(byteArray);
        }
      } catch (Exception e) {
        LOGGER.error("Decode Patient Context", e);
        return null;
      }
    }

    try (InputStream inputStream = new ByteArrayInputStream(byteArray)) {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      return factory.newDocumentBuilder().parse(inputStream);
    } catch (SAXException | IOException | ParserConfigurationException e) {
      LOGGER.error("Parsing Patient Context XML", e);
    }

    return null;
  }

  private static Document getPatientContextFromUri(URI uri) {
    byte[] byteArray = getURIContent(Objects.requireNonNull(uri));
    String uriPath = uri.getPath();

    if (uriPath.endsWith(".gz")
        || !(uriPath.endsWith(".xml")
            && MimeInspector.isMatchingMimeTypeFromMagicNumber(
                byteArray, "application/x-gzip"))) { // NON-NLS
      return getPatientContext(byteArray, OPT_ZIP);
    } else {
      return getPatientContext(byteArray, OPT_NONE);
    }
  }

  private static Document getPatientContextFromUrl(String url) {
    return getPatientContextFromUri(getURIFromURL(url));
  }

  private static URI getURIFromURL(String urlStr) {
    if (!StringUtil.hasText(urlStr)) {
      throw new IllegalArgumentException("empty urlString parameter");
    }

    URI uri = null;

    if (!urlStr.startsWith("http")) { // NON-NLS
      try {
        File file = new File(urlStr);
        if (file.canRead()) {
          uri = file.toURI();
        }
      } catch (Exception e) {
        LOGGER.error(
            "{} is supposed to be a file URL but cannot be converted to a valid URI", urlStr, e);
      }
    }
    if (uri == null) {
      try {
        uri = new URL(urlStr).toURI();
      } catch (MalformedURLException | URISyntaxException e) {
        LOGGER.error("getURIFromURL : {}", urlStr, e);
      }
    }

    return uri;
  }

  private static byte[] getURIContent(URI uri) {
    try {
      URL url = Objects.requireNonNull(uri).toURL();
      LOGGER.debug("Download from URL: {}", url);
      ClosableURLConnection urlConnection =
          NetworkUtil.getUrlConnection(url, new URLParameters(BundleTools.SESSION_TAGS_FILE));
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
      LOGGER.error("Downloading URI content", e);
    }

    return null;
  }

  private static void addImageToDataMapping(AcquireImageInfo imageInfo) {
    Objects.requireNonNull(imageInfo);
    imagesInfoByURI.put(imageInfo.getImage().getMediaURI(), imageInfo);
    imagesInfoByUID.put(
        (String) imageInfo.getImage().getTagValue(TagD.getUID(Level.INSTANCE)), imageInfo);
  }

  private static void removeImageFromDataMapping(AcquireImageInfo imageInfo) {
    imagesInfoByURI.remove(imageInfo.getImage().getMediaURI());
    imagesInfoByUID.remove(imageInfo.getImage().getTagValue(TagD.getUID(Level.INSTANCE)));
    GraphicModel modelList =
        (GraphicModel) imageInfo.getImage().getTagValue(TagW.PresentationModel);
    if (modelList != null) {
      for (GraphicLayer layer : new ArrayList<>(modelList.getLayers())) {
        modelList.deleteByLayer(layer);
      }
    }
  }

  private static List<AcquireImageInfo> getAcquireImageInfoList() {
    return new ArrayList<>(imagesInfoByURI.values());
  }

  /**
   * Get AcquireImageInfo from the data model and create lazily the JAI.PlanarImage if not yet
   * available<br>
   * All the AcquireImageInfo value objects are unique according to the imageElement URI
   *
   * @param image the ImageElement
   * @return the AcquireImageInfo based on the image
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
