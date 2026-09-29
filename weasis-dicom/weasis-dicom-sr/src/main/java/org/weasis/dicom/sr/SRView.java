/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.sr;

import java.awt.BorderLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.event.HyperlinkEvent;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.ViewerOpenOptions;
import org.weasis.core.ui.editor.ViewerPlacement;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.SequenceHandler;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.imp.seg.SegRegion;
import org.weasis.core.ui.model.imp.XmlGraphicModel;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.dicom.codec.AbstractKOSpecialElement.Reference;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.HiddenSeriesManager;
import org.weasis.dicom.codec.KOSpecialElement;
import org.weasis.dicom.codec.PRSpecialElement;
import org.weasis.dicom.codec.SpecialElementOverlay;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.codec.seg.SegSpecialElement;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.DicomSeriesHandler;
import org.weasis.dicom.explorer.HangingProtocols.OpeningViewer;
import org.weasis.dicom.explorer.LoadDicomObjects;
import org.weasis.dicom.explorer.MimeSystemAppFactory;
import org.weasis.dicom.explorer.main.DicomExplorer;
import org.weasis.dicom.macro.SOPInstanceReference;

public class SRView extends JScrollPane implements SeriesViewerListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(SRView.class);

  private final JTextPane htmlPanel = new JTextPane();

  /** Link targets of the rendered report, keyed as in {@link SRReader#LINK_PREFIX} hyperlinks. */
  private final Map<String, SRImageReference> map = new HashMap<>();

  private Series<?> series;

  /** The report is rendered once into an HTML document, so a masking change has to rebuild it. */
  private final Runnable maskListener =
      () ->
          GuiExecutor.execute(
              () ->
                  displayLimitedDicomInfo(
                      DicomModel.getFirstSpecialElement(series, DicomSpecialElement.class)));

  private KOSpecialElement keyReferences;

  public SRView() {
    this(null);
  }

  public SRView(Series<?> series) {
    JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    htmlPanel.setBorder(GuiUtils.getEmptyBorder(5, 5, 5, 5));
    htmlPanel.setContentType("text/html");
    htmlPanel.setEditable(false);
    htmlPanel.addHyperlinkListener(
        e -> {
          JTextPane pane = (JTextPane) e.getSource();
          if (e.getEventType() == HyperlinkEvent.EventType.ENTERED) {
            pane.setToolTipText(getLinkTooltip(e.getDescription()));
          } else if (e.getEventType() == HyperlinkEvent.EventType.EXITED) {
            pane.setToolTipText(null);
          } else if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            activateLink(e.getDescription());
          }
        });
    setPreferredSize(GuiUtils.getDimension(1024, 1024));
    setSeries(series);
    IdentityMask.addChangeListener(maskListener);
    htmlPanel.setTransferHandler(new SeriesHandler());
  }

  public JTextPane getHtmlPanel() {
    return htmlPanel;
  }

  public synchronized Series<?> getSeries() {
    return series;
  }

  public synchronized void setSeries(Series<?> newSeries) {
    MediaSeries<?> oldSequence = this.series;
    this.series = newSeries;

    if (oldSequence == null && newSeries == null) {
      return;
    }
    if (oldSequence != null && oldSequence.equals(newSeries)) {
      return;
    }

    closingSeries(oldSequence);

    if (series != null) {
      // Should have only one object by series (if more, they are split in several subseries in
      // dicomModel)
      DicomSpecialElement s = DicomModel.getFirstSpecialElement(series, DicomSpecialElement.class);
      displayLimitedDicomInfo(s);
      series.setOpen(true);
      series.setFocused(true);
      series.setSelected(true, null);
    }
  }

  private void closingSeries(MediaSeries<?> mediaSeries) {
    if (mediaSeries == null) {
      return;
    }
    boolean open = false;
    List<ViewerPlugin<?>> viewerPlugins = GuiUtils.getUICore().getViewerPlugins();
    synchronized (viewerPlugins) {
      pluginList:
      for (final ViewerPlugin<?> plugin : viewerPlugins) {
        List<? extends MediaSeries<?>> openSeries = plugin.getOpenSeries();
        if (openSeries != null) {
          for (MediaSeries<?> s : openSeries) {
            if (mediaSeries == s) {
              // The sequence is still open in another view or plugin
              open = true;
              break pluginList;
            }
          }
        }
      }
    }
    mediaSeries.setOpen(open);
    // TODO setSelected and setFocused must be global to all view as open
    mediaSeries.setSelected(false, null);
    mediaSeries.setFocused(false);
  }

  public void dispose() {
    IdentityMask.removeChangeListener(maskListener);
    if (series != null) {
      closingSeries(series);
      series = null;
    }
  }

  @Override
  public void changingViewContentEvent(SeriesViewerEvent event) {
    EVENT type = event.getEventType();
    if (EVENT.LAYOUT.equals(type) && event.getSeries() instanceof Series) {
      setSeries((Series<?>) event.getSeries());
    }
  }

  private void displayLimitedDicomInfo(DicomSpecialElement media) {
    StringBuilder html = new StringBuilder();
    map.clear();
    if (media != null) {
      SRReader reader = new SRReader(series, media);
      reader.readDocumentGeneralModule(html, map);
    }
    htmlPanel.setText(html.toString());
    this.setViewportView(htmlPanel);
    htmlPanel.moveCaretPosition(0);
  }

  // ================================================================================
  // Hyperlinks
  // ================================================================================

  private static String getLinkTooltip(String href) {
    if (href == null) {
      return null;
    }
    if (href.startsWith("#")) {
      return Messages.getString("SRReader.node") + " " + href.substring(1);
    }
    if (href.startsWith(SRReader.LINK_PREFIX)) {
      return href.substring(SRReader.LINK_PREFIX.length());
    }
    return href;
  }

  private void activateLink(String href) {
    if (href == null) {
      return;
    }
    if (href.startsWith("#")) {
      htmlPanel.scrollToReference(href.substring(1));
    } else if (href.startsWith(SRReader.LINK_PREFIX)) {
      openReference(href.substring(SRReader.LINK_PREFIX.length()));
    } else {
      LOGGER.debug("Ignore unknown SR hyperlink: {}", href);
    }
  }

  private void openReference(String key) {
    SRImageReference imgRef = map.get(key);
    if (imgRef == null) {
      LOGGER.warn("No SR reference registered for link {}", key);
      return;
    }
    DataExplorerView dicomView = GuiUtils.getUICore().getExplorerPlugin(DicomExplorer.NAME);
    if (!(dicomView instanceof DicomExplorer)
        || !(dicomView.getDataExplorerModel() instanceof DicomModel model)) {
      return;
    }
    SOPInstanceReference ref = imgRef.getSopInstanceReference();
    if (ref != null && ref.getReferencedSOPInstanceUID() != null && imgRef.isSegmentReference()) {
      openSegment(model, key, imgRef, ref);
    } else if (ref != null && ref.getReferencedSOPInstanceUID() != null) {
      openSopInstance(model, key, imgRef, ref);
    } else if (imgRef.getFrameOfReferenceUID() != null) {
      openFrameOfReference(model, key, imgRef);
    } else {
      LOGGER.warn("SR content item {} has no referenced SOP instance", key);
    }
  }

  private void openSopInstance(
      DicomModel model, String key, SRImageReference imgRef, SOPInstanceReference ref) {
    MediaSeriesGroup study = model.getParent(series, DicomModel.study);
    MediaSeriesGroup patient = model.getParent(series, DicomModel.patient);
    Series<?> s =
        findSOPInstanceReference(model, patient, study, ref.getReferencedSOPInstanceUID());
    if (s == null) {
      LOGGER.info(
          "SR reference {} not loaded: SOP Instance {}", key, ref.getReferencedSOPInstanceUID());
      // TODO try to download if IHE IID has been configured
      showNotFound(imgRef.hasGraphics() ? "SRView.msg" : "SRView.msg_obj");
      return;
    }

    SeriesViewerFactory plugin = GuiUtils.getUICore().getViewerFactory(s.getMimeType());
    if (plugin == null || plugin instanceof MimeSystemAppFactory) {
      LOGGER.warn("No viewer for the SR reference {} ({})", key, s.getMimeType());
      return;
    }
    if (s instanceof DicomSeries dicomSeries
        && plugin.canReadMimeType(DicomMediaIO.SERIES_MIMETYPE)) {
      openImage(model, plugin, dicomSeries, ref, imgRef);
    } else {
      // Waveform, other SR, presentation state...: let the matching plugin display the series
      ViewerPluginBuilder.openInDefaultViewer(s, model, ViewerOpenOptions.defaults());
    }
  }

  /** Opens the slice closest to an SCOORD3D item that references a Frame of Reference only. */
  private void openFrameOfReference(DicomModel model, String key, SRImageReference imgRef) {
    MediaSeriesGroup study = model.getParent(series, DicomModel.study);
    MediaSeriesGroup patient = model.getParent(series, DicomModel.patient);
    String forUID = imgRef.getFrameOfReferenceUID();
    DicomSeries dicomSeries = findSeriesByFrameOfReference(model, patient, study, forUID);
    DicomImageElement slice =
        dicomSeries == null ? null : findClosestSlice(dicomSeries, imgRef.getGraphics());
    if (slice == null) {
      LOGGER.info("SR reference {} not loaded: Frame of Reference {}", key, forUID);
      showNotFound("SRView.msg");
      return;
    }
    SeriesViewerFactory plugin = GuiUtils.getUICore().getViewerFactory(dicomSeries.getMimeType());
    if (plugin == null || plugin instanceof MimeSystemAppFactory) {
      return;
    }
    SOPInstanceReference ref = new SOPInstanceReference();
    ref.setReferencedSOPInstanceUID(TagD.getTagValue(slice, Tag.SOPInstanceUID, String.class));
    ref.setReferencedSOPClassUID(TagD.getTagValue(slice, Tag.SOPClassUID, String.class));
    if (slice.getMediaReader().getMediaElementNumber() > 1) {
      Integer frame = TagD.getTagValue(slice, Tag.InstanceNumber, Integer.class);
      if (frame != null) {
        ref.setReferencedFrameNumber(frame);
      }
    }
    openImage(model, plugin, dicomSeries, ref, imgRef);
  }

  /**
   * Shows the segment of a segmentation object: the segmentation is a hidden element attached to
   * the series it segments, so that series is opened and the viewer is asked to locate the segment.
   */
  private void openSegment(
      DicomModel model, String key, SRImageReference imgRef, SOPInstanceReference ref) {
    MediaSeriesGroup patient = model.getParent(series, DicomModel.patient);
    String sopUID = ref.getReferencedSOPInstanceUID();
    SegSpecialElement seg =
        HiddenSeriesManager.getHiddenElementsFromPatient(SegSpecialElement.class, patient).stream()
            .filter(e -> sopUID.equals(TagD.getTagValue(e, Tag.SOPInstanceUID, String.class)))
            .findFirst()
            .orElse(null);
    SegRegion<DicomImageElement> region =
        seg == null ? null : seg.getSegAttributes().get(imgRef.getSegmentNumbers()[0]);
    if (region == null) {
      LOGGER.info(
          "SR reference {} not loaded: segment {} of {}",
          key,
          imgRef.getSegmentNumbers()[0],
          sopUID);
      showNotFound("SRView.msg_seg");
      return;
    }
    DicomSeries target = null;
    for (String seriesUID : seg.getRefMap().keySet()) {
      if (model.getSeriesNode(seriesUID) instanceof DicomSeries dicomSeries) {
        target = dicomSeries;
        break;
      }
    }
    if (target == null) {
      showNotFound("SRView.msg");
      return;
    }
    SeriesViewerFactory plugin = GuiUtils.getUICore().getViewerFactory(target.getMimeType());
    if (plugin == null || plugin instanceof MimeSystemAppFactory) {
      return;
    }
    seg.setVisible(true);
    region.setSelected(true);
    ViewerOpenOptions opts =
        ViewerOpenOptions.builder()
            .placement(ViewerPlacement.newTab())
            .uid(UUID.randomUUID().toString())
            .build();
    new ViewerPluginBuilder(plugin, List.of(target), model, opts).open();
    model.firePropertyChange(
        new ObservableEvent(ObservableEvent.BasicAction.SELECT, opts.uid(), null, region));
  }

  private void showNotFound(String messageKey) {
    JOptionPane.showMessageDialog(
        WinUtil.getValidComponent(this),
        Messages.getString(messageKey),
        Messages.getString("SRView.open"),
        JOptionPane.WARNING_MESSAGE);
  }

  private void openImage(
      DicomModel model,
      SeriesViewerFactory plugin,
      DicomSeries dicomSeries,
      SOPInstanceReference ref,
      SRImageReference clicked) {
    if (keyReferences == null) {
      keyReferences = buildKO(model, dicomSeries);
    }
    if (keyReferences == null) {
      return;
    }
    Reference koRef =
        new Reference(
            TagD.getTagValue(dicomSeries, Tag.StudyInstanceUID, String.class),
            TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class),
            ref.getReferencedSOPInstanceUID(),
            ref.getReferencedSOPClassUID(),
            TagD.getTagValue(dicomSeries, Tag.InstanceNumber, Integer.class),
            ref.getReferencedFrameNumber());
    keyReferences.addKeyObject(koRef);

    attachGraphics(model, dicomSeries, clicked);

    ViewerOpenOptions opts = model.createViewerKeyImageOpenOptions();
    new ViewerPluginBuilder(plugin, List.of(dicomSeries), model, opts).open();
    model.firePropertyChange(
        new ObservableEvent(ObservableEvent.BasicAction.SELECT, opts.uid(), null, keyReferences));
    applyPresentationState(model, opts.uid(), clicked);
  }

  /** Asks the opened viewer to apply the presentation state the IMAGE item names, if loaded. */
  private void applyPresentationState(DicomModel model, String viewerUID, SRImageReference ref) {
    String prUID = ref.getPresentationStateUID();
    if (prUID == null) {
      return;
    }
    MediaSeriesGroup patient = model.getParent(series, DicomModel.patient);
    PRSpecialElement pr =
        HiddenSeriesManager.getHiddenElementsFromPatient(PRSpecialElement.class, patient).stream()
            .filter(e -> prUID.equals(TagD.getTagValue(e, Tag.SOPInstanceUID, String.class)))
            .findFirst()
            .orElse(null);
    if (pr == null) {
      LOGGER.info("Presentation state {} referenced by the SR is not loaded", prUID);
      return;
    }
    model.firePropertyChange(
        new ObservableEvent(ObservableEvent.BasicAction.SELECT, viewerUID, null, pr));
  }

  // ================================================================================
  // Graphics
  // ================================================================================

  /**
   * Emphasizes the clicked items and refreshes the SR overlay of every image of the series.
   *
   * <p>The overlay itself is rebuilt by the image view on each image change (see {@link
   * SpecialElementOverlay}); this pass covers the images already displayed, whose view will not
   * change image, and clears the emphasis set by a previous click, in this or another report.
   */
  private void attachGraphics(DicomModel model, DicomSeries dicomSeries, SRImageReference clicked) {
    MediaSeriesGroup patient = model.getParent(series, DicomModel.patient);
    List<SpecialElementOverlay> overlays =
        model.getSpecialElementsFromPatient(SpecialElementOverlay.class, patient);
    SRSpecialElement current = DicomModel.getFirstSpecialElement(series, SRSpecialElement.class);
    Set<String> highlighted =
        clicked.getGraphics().stream().map(SRGraphic::nodeId).collect(Collectors.toSet());
    for (SpecialElementOverlay overlay : overlays) {
      if (overlay instanceof SRSpecialElement sr) {
        sr.setHighlightedNodes(sr == current ? highlighted : Set.of());
      }
    }
    if (current != null && !overlays.contains(current)) {
      // The report is not attached to the explorer model (dropped file): draw it anyway
      current.setHighlightedNodes(highlighted);
      overlays = new ArrayList<>(overlays);
      overlays.add(current);
    }

    for (DicomImageElement img : dicomSeries.getMedias(null, null)) {
      GraphicModel modelList = (GraphicModel) img.getTagValue(TagW.PresentationModel);
      if (modelList == null) {
        boolean applies = false;
        for (SpecialElementOverlay overlay : overlays) {
          if (overlay.appliesTo(img)) {
            applies = true;
            break;
          }
        }
        if (!applies) {
          continue; // nothing to draw and nothing to clear
        }
        modelList = new XmlGraphicModel(img);
        img.setTag(TagW.PresentationModel, modelList);
      }
      SpecialElementOverlay.apply(modelList, img, LayerType.DICOM_SR, overlays);
    }
  }

  // ================================================================================
  // Model lookup
  // ================================================================================

  private static Series<?> findSOPInstanceReference(
      DicomModel model, MediaSeriesGroup patient, MediaSeriesGroup study, String sopUID) {
    if (model != null && patient != null && sopUID != null) {
      Series<?> s = null;
      if (study != null) {
        s = findSOPInstanceReference(model, study, sopUID);
        if (s != null) {
          return s;
        }
      }

      synchronized (model) { // NOSONAR lock object is the list for iterating its elements safely
        for (MediaSeriesGroup st : model.getChildren(patient)) {
          if (st != study) {
            s = findSOPInstanceReference(model, st, sopUID);
          }
          if (s != null) {
            return s;
          }
        }
      }
    }
    return null;
  }

  private static Series<?> findSOPInstanceReference(
      DicomModel model, MediaSeriesGroup study, String sopUID) {
    if (model != null && study != null) {
      TagW sopTag = TagD.getUID(Level.INSTANCE);
      synchronized (model) { // NOSONAR lock object is the list for iterating its elements safely
        for (MediaSeriesGroup seq : model.getChildren(study)) {
          if (seq instanceof Series<?> s
              && (s.hasMediaContains(sopTag, sopUID) || hasSpecialElement(s, sopUID))) {
            return s;
          }
        }
      }
    }
    return null;
  }

  /** Special elements (SR, PR, KO, waveform...) are not media of their series: look them up. */
  private static boolean hasSpecialElement(Series<?> series, String sopUID) {
    for (DicomSpecialElement el :
        DicomModel.getSpecialElements(series, DicomSpecialElement.class)) {
      String uid = TagD.getTagValue(el, Tag.SOPInstanceUID, String.class);
      if (uid == null && el.getMediaReader() != null) {
        Attributes dcm = el.getMediaReader().getDicomObject();
        uid = dcm == null ? null : dcm.getString(Tag.SOPInstanceUID);
      }
      if (sopUID.equals(uid)) {
        return true;
      }
    }
    return false;
  }

  private static DicomSeries findSeriesByFrameOfReference(
      DicomModel model, MediaSeriesGroup patient, MediaSeriesGroup study, String forUID) {
    if (model == null || patient == null || forUID == null) {
      return null;
    }
    DicomSeries s = study == null ? null : findSeriesByFrameOfReference(model, study, forUID);
    if (s != null) {
      return s;
    }
    synchronized (model) { // NOSONAR lock object is the list for iterating its elements safely
      for (MediaSeriesGroup st : model.getChildren(patient)) {
        if (st != study) {
          s = findSeriesByFrameOfReference(model, st, forUID);
          if (s != null) {
            return s;
          }
        }
      }
    }
    return null;
  }

  private static DicomSeries findSeriesByFrameOfReference(
      DicomModel model, MediaSeriesGroup study, String forUID) {
    synchronized (model) { // NOSONAR lock object is the list for iterating its elements safely
      for (MediaSeriesGroup seq : model.getChildren(study)) {
        if (seq instanceof DicomSeries s) {
          DicomImageElement first = s.getMedia(MediaSeries.MEDIA_POSITION.FIRST, null, null);
          if (first != null
              && forUID.equals(first.getFrameOfReferenceUID())
              && first.getSliceGeometry() != null) {
            return s;
          }
        }
      }
    }
    return null;
  }

  /** The slice whose plane is closest to the centroid of the 3D points of the clicked items. */
  static DicomImageElement findClosestSlice(DicomSeries series, List<SRGraphic> items) {
    Vector3d centroid = new Vector3d();
    int count = 0;
    for (SRGraphic item : items) {
      if (item.threeD()) {
        float[] data = item.item().getFloats(Tag.GraphicData);
        if (data != null) {
          for (int i = 0; i + 2 < data.length; i += 3) {
            centroid.add(data[i], data[i + 1], data[i + 2]);
            count++;
          }
        }
      }
    }
    if (count == 0) {
      return series.getMedia(MediaSeries.MEDIA_POSITION.MIDDLE, null, null);
    }
    centroid.div(count);

    DicomImageElement best = null;
    double bestDistance = Double.MAX_VALUE;
    for (DicomImageElement img : series.getMedias(null, null)) {
      GeometryOfSlice geometry = img.getSliceGeometry();
      if (geometry != null) {
        double d =
            Math.abs(new Vector3d(centroid).sub(geometry.getTLHC()).dot(geometry.getNormal()));
        if (d < bestDistance) {
          bestDistance = d;
          best = img;
        }
      }
    }
    return best;
  }

  private KOSpecialElement buildKO(DicomModel model, DicomSeries s) {
    SRSpecialElement dcmElement = DicomModel.getFirstSpecialElement(series, SRSpecialElement.class);
    if (dcmElement != null) {
      DicomImageElement dcm = s.getMedia(MediaSeries.MEDIA_POSITION.FIRST, null, null);
      if (dcm != null && dcm.getMediaReader() != null) {
        Attributes dicomSourceAttribute = dcm.getMediaReader().getDicomObject();
        Attributes attributes =
            DicomMediaUtils.createDicomKeyObject(
                dicomSourceAttribute, dcmElement.getShortLabel(), null);

        LoadDicomObjects loadDicomObjects =
            new LoadDicomObjects(model, OpeningViewer.NONE, attributes);
        GuiExecutor.invokeAndWait(loadDicomObjects);

        for (KOSpecialElement koElement : DicomModel.getKoSpecialElements(s)) {
          if (koElement.getMediaReader().getDicomObject().equals(attributes)) {
            return koElement;
          }
        }
      }
    }
    return null;
  }

  private static class SeriesHandler extends SequenceHandler {
    public SeriesHandler() {
      super(false, true);
    }

    @Override
    protected boolean dropFiles(List<Path> files) {
      return DicomSeriesHandler.dropDicomFiles(files);
    }
  }
}
