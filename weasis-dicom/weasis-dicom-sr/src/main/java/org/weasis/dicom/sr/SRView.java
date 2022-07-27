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
import java.beans.PropertyChangeListener;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.event.HyperlinkEvent;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.imp.XmlGraphicModel;
import org.weasis.core.ui.model.layer.GraphicLayer;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.layer.imp.DefaultLayer;
import org.weasis.dicom.codec.AbstractKOSpecialElement.Reference;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.KOSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.macro.SOPInstanceReference;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.HangingProtocols.OpeningViewer;
import org.weasis.dicom.explorer.LoadDicomObjects;
import org.weasis.dicom.explorer.MimeSystemAppFactory;

public class SRView extends JScrollPane implements SeriesViewerListener {

  private final JTextPane htmlPanel = new JTextPane();
  private final Map<String, SRImageReference> map = new HashMap<>();
  private Series<?> series;
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
            pane.setToolTipText(e.getDescription());
          } else if (e.getEventType() == HyperlinkEvent.EventType.EXITED) {
            pane.setToolTipText(null);
          } else if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            String desc = e.getDescription();
            URL url = e.getURL();
            if (url == null && desc != null && desc.startsWith("#")) {
              htmlPanel.scrollToReference(desc.substring(1));
            } else {
              openRelatedSeries(e.getURL().getHost());
            }
          }
        });
    setPreferredSize(GuiUtils.getDimension(1024, 1024));
    setSeries(series);
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
    synchronized (UIManager.VIEWER_PLUGINS) {
      List<ViewerPlugin<?>> plugins = UIManager.VIEWER_PLUGINS;
      pluginList:
      for (final ViewerPlugin<?> plugin : plugins) {
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
    if (media != null) {
      SRReader reader = new SRReader(series, media);
      map.clear();
      reader.readDocumentGeneralModule(html, map);
    }
    htmlPanel.setText(html.toString());
    this.setViewportView(htmlPanel);
    htmlPanel.moveCaretPosition(0);
  }

  private void openRelatedSeries(String reference) {
    SRImageReference imgRef = map.get(reference);
    if (imgRef != null) {
      SOPInstanceReference ref = imgRef.getSopInstanceReference();
      if (ref != null) {
        DataExplorerView dicomView =
            org.weasis.core.ui.docking.UIManager.getExplorerplugin(DicomExplorer.NAME);
        DicomModel model = null;
        if (dicomView != null) {
          model = (DicomModel) dicomView.getDataExplorerModel();
        }
        if (model != null) {
          MediaSeriesGroup study = model.getParent(series, DicomModel.study);
          MediaSeriesGroup patient = model.getParent(series, DicomModel.patient);

          Series<?> s =
              findSOPInstanceReference(model, patient, study, ref.getReferencedSOPInstanceUID());
          if (s instanceof DicomSeries dicomSeries) {
            if (keyReferences == null) {
              keyReferences = buildKO(model, dicomSeries);
            }

            if (keyReferences != null) {
              Reference koRef =
                  new Reference(
                      TagD.getTagValue(s, Tag.StudyInstanceUID, String.class),
                      TagD.getTagValue(s, Tag.SeriesInstanceUID, String.class),
                      ref.getReferencedSOPInstanceUID(),
                      ref.getReferencedSOPClassUID(),
                      ref.getReferencedFrameNumber());
              keyReferences.addKeyObject(koRef);
              SeriesViewerFactory plugin = UIManager.getViewerFactory(DicomMediaIO.SERIES_MIMETYPE);
              if (plugin != null && !(plugin instanceof MimeSystemAppFactory)) {
                MediaElement mediaElement =
                    dicomSeries.getMedia(0, keyReferences.getSOPInstanceUIDFilter(), null);
                addGraphicsToView(mediaElement, imgRef);
                String uid = UUID.randomUUID().toString();
                Map<String, Object> props = Collections.synchronizedMap(new HashMap<>());
                props.put(ViewerPluginBuilder.CMP_ENTRY_BUILD_NEW_VIEWER, false);
                props.put(ViewerPluginBuilder.BEST_DEF_LAYOUT, false);
                props.put(ViewerPluginBuilder.ICON, ResourceUtil.getIcon(OtherIcon.KEY_IMAGE));
                props.put(ViewerPluginBuilder.UID, uid);
                List<DicomSeries> seriesList = new ArrayList<>();
                seriesList.add((DicomSeries) s);
                ViewerPluginBuilder builder =
                    new ViewerPluginBuilder(plugin, seriesList, model, props);
                ViewerPluginBuilder.openSequenceInPlugin(builder);
                model.firePropertyChange(
                    new ObservableEvent(
                        ObservableEvent.BasicAction.SELECT, uid, null, keyReferences));
              }
            }
          } else {
            // TODO try to download if IHE IID has been configured
            JOptionPane.showMessageDialog(
                this,
                Messages.getString("SRView.msg"),
                Messages.getString("SRView.open"),
                JOptionPane.WARNING_MESSAGE);
          }
        }
      }
    }
  }

  private void addGraphicsToView(MediaElement mediaElement, SRImageReference imgRef) {
    if (mediaElement instanceof ImageElement imageElement
        && imgRef.getGraphics() != null
        && !imgRef.getGraphics().isEmpty()) {

      GraphicModel modelList = (GraphicModel) mediaElement.getTagValue(TagW.PresentationModel);
      // After getting a new image iterator, update the measurements
      if (modelList == null) {
        modelList = new XmlGraphicModel(imageElement);
        mediaElement.setTag(TagW.PresentationModel, modelList);
      }

      String layerName = "SCOORD [DICOM]"; // NON-NLS
      GraphicLayer layer = null;
      for (GraphicLayer l : modelList.getLayers()) {
        if (layerName.equals(l.getName())) {
          layer = l;
          break;
        }
      }

      boolean addLayer = layer == null;

      if (addLayer) {
        layer = new DefaultLayer(LayerType.DICOM_SR);
        layer.setName(layerName);
        layer.setSerializable(false);
        layer.setLocked(true);
        layer.setSelectable(false);
        layer.setLevel(305);
      }

      if (!addLayer) {
        List<Graphic> models = modelList.getModels();
        int size = 0;
        synchronized (models) {
          for (Graphic g : models) {
            boolean sr = layer.equals(g.getLayer());
            if (sr) {
              size++;
            }
          }
        }

        if (imgRef.getGraphics().size() == size) {
          return;
        }

        if (size > 0) {
          modelList.deleteByLayer(layer);
        }
      }

      for (Graphic graphic : imgRef.getGraphics()) {
        graphic.setLayer(layer);
        for (PropertyChangeListener listener : modelList.getGraphicsListeners()) {
          graphic.addPropertyChangeListener(listener);
        }
        modelList.addGraphic(graphic);
      }
    }
  }

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
        GuiExecutor.instance().invokeAndWait(loadDicomObjects);

        for (KOSpecialElement koElement : DicomModel.getKoSpecialElements(s)) {
          if (koElement.getMediaReader().getDicomObject().equals(attributes)) {
            return koElement;
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
          if (seq instanceof Series<?> s) {
            if (s.hasMediaContains(sopTag, sopUID)) {
              return s;
            }
          }
        }
      }
    }
    return null;
  }
}
