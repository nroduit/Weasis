/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wave;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.AffineTransform;
import java.awt.print.PageFormat;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.img.util.DicomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagView;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.ExportImage;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.WaveformAnnotation;
import org.weasis.dicom.codec.WaveformAnnotationSource;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.main.DicomExplorer;
import org.weasis.dicom.wave.dockable.MeasureAnnotationTool;
import org.weasis.dicom.waveform.WaveformReaders;
import org.weasis.opencv.waveform.WaveformReader;

public class WaveView extends JPanel implements SeriesViewerListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(WaveView.class);
  private static final String PREFERENCE_NODE = "weasis.dicom.wave"; // NON-NLS
  private static final String P_LAST_FORMAT = "last.format"; // NON-NLS
  private static final String P_CABRERA = "cabrera"; // NON-NLS
  private static final String P_CALIBRATION = "calibration.pulse"; // NON-NLS
  private static final String P_FILTER_BASELINE = "filter.baseline"; // NON-NLS
  private static final String P_FILTER_LOWPASS = "filter.lowpass"; // NON-NLS
  private static final String P_FILTER_NOTCH = "filter.notch"; // NON-NLS

  /** Signal scanned for the amplitude range when the object has no Channel Minimum / Maximum. */
  private static final int MAX_SCAN_SECONDS = 600;

  /** Signal used for the heart rate estimate. */
  private static final int HEART_RATE_SECONDS = 60;

  /** Width in millimetres of the gutter holding the calibration pulse. */
  private static final double GUTTER_MM = 6;

  /** Height in pixels of the patient header of an exported or printed page. */
  static final int HEADER_HEIGHT = 50;

  private Series<?> series;

  private InfoPanel infoPanel;
  private JPanel pane;
  private JScrollPane scrollPane;
  private OverviewPanel overviewPanel;

  private int mvCells;
  private double seconds;
  private int channelNumber;
  private int sampleNumber;
  private Format currentFormat;
  private boolean cabrera;
  private boolean calibrationPulse;
  private SignalFilter filter;
  private int samplesPerSecond;
  private WaveDataReadable waveData;
  private final List<ChannelDefinition> channels;
  private double zoomRatio = 1.0;
  private Double heartRate;

  private WaveLayoutManager waveLayoutManager;

  private MeasureAnnotationTool annotationTool;

  /** The displayed waveform object, kept to refresh the external annotations. */
  private Attributes currentAttributes;

  /** The displayed element, kept to switch between its multiplex groups. */
  private DicomSpecialElement currentElement;

  /** Index of the displayed item of the Waveform Sequence. */
  private int multiplexGroup;

  private final List<String> multiplexGroupLabels = new ArrayList<>();

  /** The temporal annotations the object itself carries (Waveform Annotation Sequence). */
  private final List<WaveformAnnotation> objectAnnotations = new ArrayList<>();

  public WaveView() {
    this(null);
  }

  public WaveView(Series<?> series) {
    setLayout(new BorderLayout());
    setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    setPreferredSize(GuiUtils.getDimension(1024, 1024));

    this.channels = new ArrayList<>();
    this.filter = SignalFilter.NONE;
    this.calibrationPulse = true;

    String plugInId = GuiUtils.getUICore().getPluginManager().getPluginId(this.getClass());
    String formatName = null;
    Preferences prefs = BundlePreferences.getDefaultPreferences(plugInId);
    if (prefs != null) {
      Preferences p = prefs.node(PREFERENCE_NODE);
      formatName = p.get(P_LAST_FORMAT, null);
      this.cabrera = p.getBoolean(P_CABRERA, false);
      this.calibrationPulse = p.getBoolean(P_CALIBRATION, true);
      this.filter =
          new SignalFilter(
              p.getBoolean(P_FILTER_BASELINE, false),
              p.getDouble(P_FILTER_LOWPASS, 0),
              p.getDouble(P_FILTER_NOTCH, 0));
    }
    this.currentFormat = Format.getValue(formatName);
    this.addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            if (pane != null) {
              pane.revalidate();
            }
          }
        });
    setSeries(series);
  }

  /** Lists the interval summary and every caliper in the annotation tool. */
  public void updateMarkersTable() {
    if (annotationTool == null) {
      return;
    }
    List<Object[]> intervalRows = new ArrayList<>();
    List<Object[]> caliperRows = new ArrayList<>();
    if (waveLayoutManager != null) {
      List<Caliper> all = new ArrayList<>();
      for (LeadPanel lead : waveLayoutManager.getSortedComponents()) {
        all.addAll(lead.getCalipers());
      }
      IntervalSummary summary = IntervalSummary.of(all, heartRate);
      if (summary.rr() != null) {
        String source =
            switch (summary.rrSource()) {
              case IntervalSummary.SOURCE_BEATS -> " (" + summary.beats() + " QRS)"; // NON-NLS
              case IntervalSummary.SOURCE_ESTIMATE ->
                  " (" + Messages.getString("InfoPanel.estimated") + ")";
              default -> "";
            };
        intervalRows.add(
            new Object[] {
              Messages.getString("WaveView.heart_rate"),
              WaveFormats.bpm(summary.heartRate()) + source
            });
        intervalRows.add(new Object[] {"R-R", WaveFormats.millis(summary.rr())}); // NON-NLS
      }
      if (summary.pr() != null) {
        intervalRows.add(new Object[] {"PR", WaveFormats.millis(summary.pr())}); // NON-NLS
      }
      if (summary.qrs() != null) {
        intervalRows.add(new Object[] {"QRS", WaveFormats.millis(summary.qrs())}); // NON-NLS
      }
      if (summary.qt() != null) {
        intervalRows.add(new Object[] {"QT", WaveFormats.millis(summary.qt())}); // NON-NLS
        if (summary.qtcBazett() != null) {
          intervalRows.add(
              new Object[] {"QTc (Bazett)", WaveFormats.millis(summary.qtcBazett())}); // NON-NLS
          intervalRows.add(
              new Object[] {
                "QTc (Fridericia)", WaveFormats.millis(summary.qtcFridericia()) // NON-NLS
              });
        }
      }

      for (LeadPanel lead : waveLayoutManager.getSortedComponents()) {
        String unit = lead.getUnit();
        int n = 0;
        for (Caliper c : lead.getCalipers()) {
          if (c.isEmpty()) {
            continue;
          }
          n++;
          String name =
              lead.getDisplayTitle()
                  + " \u00B7 "
                  + (c.getLabel() != null
                      ? c.getLabel()
                      : Messages.getString("WaveView.caliper") + " " + n)
                  + " ";
          if (c.isTime()) {
            double fs = samplesPerSecond;
            caliperRows.add(
                new Object[] {
                  name + Messages.getString("WaveView.start_time"),
                  WaveFormats.SECONDS.format(c.getStart() / fs)
                });
            caliperRows.add(
                new Object[] {
                  name + Messages.getString("WaveView.duration"),
                  WaveFormats.millis(c.getDuration())
                });
            if (c.getBeats() >= 2) {
              caliperRows.add(
                  new Object[] {
                    name + Messages.getString("WaveView.beats"),
                    c.getBeats()
                        + ", R-R " // NON-NLS
                        + WaveFormats.millis(c.getMeanRR())
                        + ", "
                        + WaveFormats.bpm(c.getMeanRate())
                  });
            } else {
              caliperRows.add(
                  new Object[] {
                    name + Messages.getString("WaveView.rate"), WaveFormats.bpm(c.getRate())
                  });
            }
            caliperRows.add(
                new Object[] {
                  name + Messages.getString("WaveView.diff"), WaveFormats.value(c.getDelta(), unit)
                });
            caliperRows.add(
                new Object[] {
                  name + Messages.getString("WaveView.amplitude"),
                  WaveFormats.value(c.getPeakToPeak(), unit)
                });
          } else {
            caliperRows.add(
                new Object[] {
                  name + Messages.getString("WaveView.diff"), WaveFormats.value(c.getDelta(), unit)
                });
          }
        }
      }
    }
    annotationTool.updateCalipers(intervalRows, caliperRows);
  }

  public void clearMeasurements() {
    if (waveLayoutManager != null) {
      for (LeadPanel lead : waveLayoutManager.getSortedComponents()) {
        lead.removeAllCalipers();
      }
    }
    updateMarkersTable();
  }

  public double getZoomRatio() {
    return zoomRatio;
  }

  public void setZoomRatio(double zoomRatio) {
    this.zoomRatio = zoomRatio;
  }

  public synchronized Series getSeries() {
    return series;
  }

  public synchronized void setSeries(Series newSeries) {
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
      try {
        // Should have only one object by series (if more, they are split in several subseries in
        // dicomModel)
        DicomSpecialElement s =
            DicomModel.getFirstSpecialElement(series, DicomSpecialElement.class);
        series.setOpen(true);
        series.setFocused(true);
        series.setSelected(true, null);
        displayECG(s);
      } catch (Exception e) {
        LOGGER.error("Cannot display Waveform", e);
      }
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
    String plugInId = GuiUtils.getUICore().getPluginManager().getPluginId(this.getClass());
    Preferences prefs = BundlePreferences.getDefaultPreferences(plugInId);
    if (prefs != null && currentFormat != null) {
      Preferences p = prefs.node(PREFERENCE_NODE);
      p.put(P_LAST_FORMAT, currentFormat.name());
      p.putBoolean(P_CABRERA, cabrera);
      p.putBoolean(P_CALIBRATION, calibrationPulse);
      p.putBoolean(P_FILTER_BASELINE, filter.baseline());
      p.putDouble(P_FILTER_LOWPASS, filter.lowPassHz());
      p.putDouble(P_FILTER_NOTCH, filter.notchHz());
    }
    if (series != null) {
      closingSeries(series);
      series = null;
    }
    closeWaveData();
    setAnnotationTool(null);
  }

  void setAnnotationTool(MeasureAnnotationTool tool) {
    this.annotationTool = tool;
  }

  @Override
  public void changingViewContentEvent(SeriesViewerEvent event) {
    EVENT type = event.getEventType();
    if (EVENT.LAYOUT.equals(type) && event.getSeries() instanceof Series) {
      setSeries((Series<?>) event.getSeries());
    }
  }

  private void displayECG(DicomSpecialElement media) throws Exception {
    removeAll();
    this.currentElement = media;
    if (media == null) {
      return;
    }
    DicomMediaIO dicomImageLoader = media.getMediaReader();
    Attributes attributes = dicomImageLoader.getDicomObject();
    if (attributes != null) {
      Sequence waveformSeq = attributes.getSequence(Tag.WaveformSequence);
      if (waveformSeq == null || waveformSeq.isEmpty()) {
        throw new IllegalStateException("No Waveform Sequence");
      }
      readMultiplexGroupLabels(waveformSeq);
      if (multiplexGroup < 0 || multiplexGroup >= waveformSeq.size()) {
        multiplexGroup = 0;
      }
      Attributes dcm = waveformSeq.get(multiplexGroup);

      this.channelNumber =
          DicomUtils.getIntegerFromDicomElement(dcm, Tag.NumberOfWaveformChannels, 0);
      Sequence chDefSeq = Optional.of(dcm.getSequence(Tag.ChannelDefinitionSequence)).get();
      this.channels.clear();
      for (int i = 0; i < chDefSeq.size(); i++) {
        channels.add(new ChannelDefinition(chDefSeq.get(i), i));
      }

      readWaveformData(dicomImageLoader, dcm);
      // Leads index the multiplexed samples by position: keep only the channels the data holds
      int dataChannels = waveData.getNbOfChannels();
      if (channelNumber != dataChannels || channels.size() != dataChannels) {
        LOGGER.warn(
            "Waveform channels mismatch: {} declared, {} definitions, {} in the data",
            channelNumber,
            channels.size(),
            dataChannels);
        if (channels.size() > dataChannels) {
          channels.subList(dataChannels, channels.size()).clear();
        }
      }
      this.channelNumber = channels.size();
      double frequency = DicomUtils.getDoubleFromDicomElement(dcm, Tag.SamplingFrequency, 1.0);
      this.seconds = sampleNumber / frequency;
      this.samplesPerSecond = (int) Math.round(frequency);
      getMinMax(channels);
      this.heartRate = estimateHeartRate();

      this.scrollPane = new JScrollPane();
      scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
      scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

      double speed =
          waveLayoutManager == null ? WaveLayoutManager.AUTO_SPEED : waveLayoutManager.getSpeed();
      int amplitude =
          waveLayoutManager == null
              ? WaveLayoutManager.AUTO_AMPLITUDE
              : waveLayoutManager.getAmplitude();

      this.waveLayoutManager = new WaveLayoutManager(this, speed, amplitude);
      this.pane = new JPanel(waveLayoutManager);
      JPanel channelWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
      channelWrap.add(pane);
      scrollPane.setViewportView(channelWrap);

      addChannelPanels();
      if (getChannelNumber() < 12 && currentFormat != Format.SUPERIMPOSED) {
        this.currentFormat = Format.DEFAULT;
      }
      setFormat(currentFormat);

      // Panel which includes the Buttons for zooming
      ToolPanel tools = new ToolPanel(this);
      // Panel with information about the channel the mouse cursor is over
      this.infoPanel = new InfoPanel(zoomRatio);
      infoPanel.setHeartRate(heartRate);

      JPanel wrap = new JPanel(new BorderLayout());
      wrap.add(tools, BorderLayout.NORTH);
      wrap.add(infoPanel, BorderLayout.SOUTH);

      JPanel center = new JPanel(new BorderLayout());
      this.overviewPanel = new OverviewPanel(this, scrollPane.getViewport(), pane);
      overviewPanel.setVisible(seconds > OverviewPanel.MIN_SECONDS);
      center.add(overviewPanel, BorderLayout.NORTH);
      center.add(scrollPane, BorderLayout.CENTER);

      this.add(wrap, BorderLayout.NORTH);
      this.add(center, BorderLayout.CENTER);
    }
    updateMarkersTable();
    this.currentAttributes = attributes;
    readObjectAnnotations(attributes);
    if (annotationTool != null) {
      annotationTool.readAnnotations(attributes);
    }
    applyAnnotations();
  }

  /**
   * The heart rate estimated from lead II (or the first channel) over the first minute, baseline
   * removed and low-passed at 40 Hz; null when no regular beats are found.
   */
  private Double estimateHeartRate() {
    if (waveData == null || channels.isEmpty() || samplesPerSecond < 50) {
      return null;
    }
    ChannelDefinition reference = channels.get(0);
    for (ChannelDefinition c : channels) {
      if (Lead.II.equals(c.getLead())) {
        reference = c;
        break;
      }
    }
    if (!reference.isVoltage()) {
      return null;
    }
    int count = (int) Math.min(sampleNumber, (long) HEART_RATE_SECONDS * samplesPerSecond);
    int[] raw = new int[count];
    waveData.readRaw(0, count, reference, raw);
    double[] signal = new double[count];
    for (int i = 0; i < count; i++) {
      signal[i] = reference.toPhysical(raw[i]);
    }
    new SignalFilter(true, 40, 0).apply(signal, count, samplesPerSecond);
    try {
      return HeartRate.estimate(signal, samplesPerSecond);
    } catch (RuntimeException e) {
      LOGGER.warn("Heart rate estimate", e);
      return null;
    }
  }

  /** One label per item of the Waveform Sequence: its Multiplex Group Label and duration. */
  private void readMultiplexGroupLabels(Sequence waveformSeq) {
    multiplexGroupLabels.clear();
    for (int i = 0; i < waveformSeq.size(); i++) {
      Attributes item = waveformSeq.get(i);
      String label = item.getString(Tag.MultiplexGroupLabel);
      if (!StringUtil.hasText(label)) {
        label = Messages.getString("WaveView.group") + " " + (i + 1);
      }
      double frequency = DicomUtils.getDoubleFromDicomElement(item, Tag.SamplingFrequency, 0.0);
      int samples = DicomUtils.getIntegerFromDicomElement(item, Tag.NumberOfWaveformSamples, 0);
      if (frequency > 0 && samples > 0) {
        label += " (" + WaveFormats.SECONDS.format(samples / frequency) + ")";
      }
      multiplexGroupLabels.add(label);
    }
  }

  /** The labels of the multiplex groups of the displayed object, in sequence order. */
  public List<String> getMultiplexGroupLabels() {
    return multiplexGroupLabels;
  }

  public int getMultiplexGroup() {
    return multiplexGroup;
  }

  /**
   * Displays another item of the Waveform Sequence (e.g. the median beat instead of the rhythm).
   */
  public void setMultiplexGroup(int index) {
    if (index == multiplexGroup || index < 0 || index >= multiplexGroupLabels.size()) {
      return;
    }
    this.multiplexGroup = index;
    try {
      displayECG(currentElement);
    } catch (Exception e) {
      LOGGER.error("Cannot display the multiplex group {}", index, e);
    }
    revalidate();
    repaint();
  }

  /** Rebuilds the markers coming from other objects (SR TCOORD items) after the model changed. */
  public void refreshAnnotations() {
    applyAnnotations();
  }

  /**
   * The temporal items of the object's Waveform Annotation Sequence: those with a Temporal Range
   * Type and sample positions, time offsets or date-times (numeric results and free text are listed
   * by the annotation tool instead).
   */
  private void readObjectAnnotations(Attributes attributes) {
    objectAnnotations.clear();
    Sequence seq =
        attributes == null ? null : attributes.getSequence(Tag.WaveformAnnotationSequence);
    if (seq == null) {
      return;
    }
    for (Attributes item : seq) {
      String type = item.getString(Tag.TemporalRangeType);
      if (!StringUtil.hasText(type)) {
        continue;
      }
      int[] positions = item.getInts(Tag.ReferencedSamplePositions);
      double[] offsets = item.getDoubles(Tag.ReferencedTimeOffsets);
      String[] dateTimes = item.getStrings(Tag.ReferencedDateTime);
      if ((positions == null || positions.length == 0)
          && (offsets == null || offsets.length == 0)
          && (dateTimes == null || dateTimes.length == 0)) {
        continue;
      }
      String label = null;
      Attributes name = item.getNestedDataset(Tag.ConceptNameCodeSequence);
      if (name != null) {
        label = name.getString(Tag.CodeMeaning);
      }
      if (!StringUtil.hasText(label)) {
        label = item.getString(Tag.UnformattedTextValue);
      }
      int[] channelRefs = item.getInts(Tag.ReferencedWaveformChannels);
      objectAnnotations.add(
          new WaveformAnnotation(
              label,
              type,
              positions == null || positions.length == 0 ? null : positions,
              offsets == null || offsets.length == 0 ? null : offsets,
              dateTimes == null || dateTimes.length == 0 ? null : dateTimes,
              channelRefs));
    }
  }

  /** Whether an annotation applies to a 1-based channel of the displayed multiplex group. */
  private boolean appliesTo(WaveformAnnotation annotation, int channelNumber) {
    int[] refs = annotation.channels();
    if (refs == null || refs.length < 2) {
      return true;
    }
    for (int i = 0; i + 1 < refs.length; i += 2) {
      boolean group = refs[i] == 0 || refs[i] == multiplexGroup + 1;
      boolean channel = refs[i + 1] == 0 || refs[i + 1] == channelNumber;
      if (group && channel) {
        return true;
      }
    }
    return false;
  }

  /**
   * Draws the temporal annotations of the object and of the patient's {@link
   * WaveformAnnotationSource} elements (SR TCOORD items) on the matching leads, and lists the
   * external ones in the annotation tool.
   */
  private void applyAnnotations() {
    if (currentAttributes == null || pane == null) {
      return;
    }
    List<WaveformAnnotation> external = new ArrayList<>();
    if (series != null && series.getTagValue(TagW.ExplorerModel) instanceof DicomModel model) {
      MediaSeriesGroup patient = model.getParent(series, DicomModel.patient);
      String sopUID = currentAttributes.getString(Tag.SOPInstanceUID);
      for (WaveformAnnotationSource source :
          model.getSpecialElementsFromPatient(WaveformAnnotationSource.class, patient)) {
        external.addAll(source.getWaveformAnnotations(sopUID));
      }
    }
    Instant start = acquisitionStart(currentAttributes);
    for (Component c : pane.getComponents()) {
      if (c instanceof LeadPanel lead) {
        int channelNumber = lead.getChannels().getPosition() + 1;
        List<LeadAnnotation> items = new ArrayList<>();
        for (WaveformAnnotation annotation : objectAnnotations) {
          if (appliesTo(annotation, channelNumber)) {
            items.addAll(toLeadAnnotations(annotation, start, false));
          }
        }
        for (WaveformAnnotation annotation : external) {
          if (annotation.appliesToChannel(channelNumber)) {
            items.addAll(toLeadAnnotations(annotation, start, true));
          }
        }
        lead.setAnnotations(items);
      }
    }
    if (annotationTool != null) {
      List<Object[]> rows = new ArrayList<>();
      for (WaveformAnnotation annotation : external) {
        rows.add(new Object[] {annotation.label(), describe(annotation)});
      }
      annotationTool.setExternalAnnotations(rows);
    }
  }

  /** The lead annotations of one item: points, or segments when the range type pairs positions. */
  private List<LeadAnnotation> toLeadAnnotations(
      WaveformAnnotation annotation, Instant start, boolean external) {
    List<Integer> indexes = toSampleIndexes(annotation, start);
    List<LeadAnnotation> items = new ArrayList<>();
    String type = annotation.temporalRangeType();
    boolean segments = "SEGMENT".equals(type) || "MULTISEGMENT".equals(type); // NON-NLS
    if (segments && indexes.size() >= 2) {
      for (int i = 0; i + 1 < indexes.size(); i += 2) {
        int a = indexes.get(i);
        int b = indexes.get(i + 1);
        items.add(new LeadAnnotation(Math.min(a, b), Math.max(a, b), annotation.label(), external));
      }
    } else {
      for (int index : indexes) {
        items.add(new LeadAnnotation(index, index, annotation.label(), external));
      }
    }
    return items;
  }

  /** The 0-based sample indexes of an annotation, whatever its temporal encoding. */
  List<Integer> toSampleIndexes(WaveformAnnotation annotation, Instant start) {
    List<Integer> indexes = new ArrayList<>();
    if (annotation.samplePositions() != null) {
      for (int position : annotation.samplePositions()) {
        indexes.add(position - 1); // sample numbers start at 1
      }
    } else if (annotation.timeOffsets() != null) {
      for (double seconds : annotation.timeOffsets()) {
        indexes.add((int) Math.round(seconds * samplesPerSecond));
      }
    } else if (annotation.dateTimes() != null && start != null) {
      for (String dt : annotation.dateTimes()) {
        Instant instant = toInstant(TagD.getDicomDateTime(dt));
        if (instant != null) {
          double seconds = Duration.between(start, instant).toNanos() / 1e9;
          indexes.add((int) Math.round(seconds * samplesPerSecond));
        }
      }
    }
    return indexes;
  }

  /** Start of the displayed multiplex group: Acquisition DateTime plus its time offset. */
  private Instant acquisitionStart(Attributes attributes) {
    Instant start = toInstant(TagD.getDicomDateTime(attributes.getString(Tag.AcquisitionDateTime)));
    if (start == null) {
      return null;
    }
    Sequence seq = attributes.getSequence(Tag.WaveformSequence);
    Attributes group = seq == null || multiplexGroup >= seq.size() ? null : seq.get(multiplexGroup);
    double offsetMs = group == null ? 0 : group.getDouble(Tag.MultiplexGroupTimeOffset, 0);
    return start.plusNanos(Math.round(offsetMs * 1_000_000));
  }

  private static Instant toInstant(Temporal temporal) {
    if (temporal instanceof ZonedDateTime zoned) {
      return zoned.toInstant();
    }
    if (temporal instanceof OffsetDateTime offset) {
      return offset.toInstant();
    }
    if (temporal instanceof LocalDateTime local) {
      return local.atZone(ZoneId.systemDefault()).toInstant();
    }
    return null;
  }

  private static String describe(WaveformAnnotation annotation) {
    StringBuilder text = new StringBuilder(annotation.temporalRangeType());
    if (annotation.samplePositions() != null) {
      text.append(": ").append(Arrays.toString(annotation.samplePositions()));
    } else if (annotation.timeOffsets() != null) {
      text.append(": ").append(Arrays.toString(annotation.timeOffsets())).append(" s"); // NON-NLS
    } else if (annotation.dateTimes() != null) {
      text.append(": ").append(String.join(", ", annotation.dateTimes()));
    }
    return text.toString();
  }

  /**
   * Opens the Waveform Data through a {@link WaveformReader}: native data is memory-mapped per
   * window and T.261 streams (Supplement 253) are decoded per frame sequence, so only the windows
   * being displayed are in memory whatever the length of the recording.
   */
  private void readWaveformData(DicomMediaIO reader, Attributes dcm) throws IOException {
    closeWaveData();
    String tsuid = TagD.getTagValue(reader, Tag.TransferSyntaxUID, String.class);
    if (tsuid == null) {
      tsuid = UID.ExplicitVRLittleEndian;
    }
    WaveformReader waveformReader = WaveformReaders.open(dcm, tsuid);
    WaveformWindowData windowData = new WaveformWindowData(waveformReader);
    // Once more of the recording is summarised, zoomed-out leads can be painted from the summary
    windowData.setSummaryListener(
        () ->
            GuiExecutor.execute(
                () -> {
                  if (pane != null && waveData == windowData) {
                    pane.repaint();
                    if (overviewPanel != null) {
                      overviewPanel.repaint();
                    }
                  }
                }));
    this.waveData = windowData;
    this.sampleNumber = waveData.getNbSamplesPerChannel();
  }

  private void closeWaveData() {
    if (waveData != null) {
      waveData.close();
      waveData = null;
    }
  }

  private void addChannelPanels() {
    for (ChannelDefinition channel : channels) {
      LeadPanel panel = new LeadPanel(this, waveData, channel);
      pane.add(channel.getTitle(), panel);

      Lead rhythm = Lead.rhythmOf(channel.getLead());
      if (rhythm != null) {
        LeadPanel strip =
            new LeadPanel(this, waveData, new ChannelDefinition(channel, rhythm.toString()));
        pane.add(rhythm.toString(), strip);
      }
    }
  }

  /** The smallest of 1, 2, 5 times a power of ten that is at least {@code value}. */
  static double niceCeil(double value) {
    if (value <= 0 || Double.isNaN(value) || Double.isInfinite(value)) {
      return 1;
    }
    double power = Math.pow(10, Math.floor(Math.log10(value)));
    for (double step : new double[] {1, 2, 5, 10}) {
      if (step * power >= value - 1e-9) {
        return step * power;
      }
    }
    return 10 * power;
  }

  /**
   * The amplitude range of the leads (sensitivity and baseline applied, in the display unit), from
   * the Channel Minimum / Maximum Value attributes when the object carries them, otherwise scanned
   * over at most {@link #MAX_SCAN_SECONDS} of signal so that opening a long recording never reads
   * it as a whole. A channel whose mean level exceeds its peak-to-peak span (a DC-coupled channel
   * with an electrode offset) is displayed around that level, which becomes its display offset.
   * Voltage channels share the millivolt grid; any other channel gets a round cell value so that
   * its range fills the same number of cells.
   */
  private void getMinMax(List<ChannelDefinition> channels) {
    WaveDataReadable db = waveData;
    if (db == null) {
      return;
    }
    double maxVoltage = 0;
    boolean anyVoltage = false;
    int scanned = (int) Math.min(sampleNumber, (long) MAX_SCAN_SECONDS * samplesPerSecond);
    int[] range = new int[2];
    for (int i = 0; i < channelNumber; i++) {
      ChannelDefinition c = channels.get(i);
      double scale = c.getAmplitudeUnitScalingFactor();
      double min;
      double max;
      if (c.getStoredMinimum() != null && c.getStoredMaximum() != null) {
        min = c.getStoredMinimum() * scale + c.getBaseline();
        max = c.getStoredMaximum() * scale + c.getBaseline();
      } else {
        db.rawMinMax(0, scanned, c, range);
        min = range[0] * scale + c.getBaseline();
        max = range[1] * scale + c.getBaseline();
      }
      if (min > max) {
        double tmp = min;
        min = max;
        max = tmp;
      }
      double offset = 0;
      double level = db.rawMean(0, scanned, c) * scale + c.getBaseline();
      if (Math.abs(level) > max - min) {
        offset = level;
        min -= offset;
        max -= offset;
      }
      c.setDisplayOffset(offset);
      c.setMinValue(c.toDisplay(min));
      c.setMaxValue(c.toDisplay(max));
      if (c.isVoltage()) {
        anyVoltage = true;
        maxVoltage =
            Math.max(maxVoltage, Math.max(Math.abs(c.toDisplay(min)), Math.abs(c.toDisplay(max))));
      }
    }

    this.mvCells = anyVoltage ? Math.max(2, (int) Math.ceil(maxVoltage) * 2) : 4;
    for (int i = 0; i < channelNumber; i++) {
      ChannelDefinition c = channels.get(i);
      if (c.isVoltage()) {
        c.setCellValue(1.0);
      } else {
        double extent = Math.max(Math.abs(c.getMinValue()), Math.abs(c.getMaxValue()));
        c.setCellValue(niceCeil(extent / (mvCells / 2.0)));
      }
    }
  }

  public void setSpeed(double mmPerSecond) {
    this.waveLayoutManager.setSpeed(mmPerSecond);
    this.pane.revalidate();
  }

  public void setAmplitude(int mmPerMilliVolt) {
    this.waveLayoutManager.setAmplitude(mmPerMilliVolt);
    this.pane.revalidate();
  }

  public boolean isCabrera() {
    return cabrera;
  }

  /** Switches between the standard and the Cabrera lead sequences (see {@link Format}). */
  public void setCabrera(boolean cabrera) {
    if (this.cabrera != cabrera) {
      this.cabrera = cabrera;
      if (pane != null) {
        setFormat(currentFormat);
      }
    }
  }

  public boolean isCalibrationPulse() {
    return calibrationPulse;
  }

  /** Shows or hides the 1 mV calibration pulse in a gutter left of every lead. */
  public void setCalibrationPulse(boolean calibrationPulse) {
    if (this.calibrationPulse != calibrationPulse) {
      this.calibrationPulse = calibrationPulse;
      if (pane != null) {
        pane.revalidate();
        pane.repaint();
      }
    }
  }

  public SignalFilter getFilter() {
    return filter;
  }

  /** Sets the display filters of every lead and measures the calipers again. */
  public void setFilter(SignalFilter filter) {
    this.filter = filter == null ? SignalFilter.NONE : filter;
    if (pane != null) {
      for (Component c : pane.getComponents()) {
        if (c instanceof LeadPanel lead) {
          lead.remeasure();
        }
      }
      updateMarkersTable();
      pane.repaint();
    }
  }

  private Caliper.Kind caliperKind = Caliper.Kind.TIME;
  private String caliperLabel;
  private boolean calipersOnAllLeads = true;

  /** The kind of the calipers the next press creates (Alt forces an amplitude caliper). */
  public Caliper.Kind getCaliperKind() {
    return caliperKind;
  }

  public void setCaliperKind(Caliper.Kind kind) {
    this.caliperKind = kind == null ? Caliper.Kind.TIME : kind;
  }

  /** The label given to the next calipers (RR, PR, QRS, QT...), or null. */
  public String getCaliperLabel() {
    return caliperLabel;
  }

  public void setCaliperLabel(String label) {
    this.caliperLabel = label == null || label.isBlank() ? null : label;
  }

  /** Whether the time calipers of a lead are drawn as guides on every other lead. */
  public boolean isCalipersOnAllLeads() {
    return calipersOnAllLeads;
  }

  public void setCalipersOnAllLeads(boolean on) {
    this.calipersOnAllLeads = on;
    if (pane != null) {
      pane.repaint();
    }
  }

  /** The complete time calipers of the other leads, drawn as guides. */
  public List<Caliper> getTimeCalipersOfOtherLeads(LeadPanel lead) {
    List<Caliper> result = new ArrayList<>();
    if (pane == null) {
      return result;
    }
    for (Component c : pane.getComponents()) {
      if (c instanceof LeadPanel other && other != lead && other.isVisible()) {
        for (Caliper caliper : other.getCalipers()) {
          if (caliper.isTime() && !caliper.isEmpty()) {
            result.add(caliper);
          }
        }
      }
    }
    return result;
  }

  /** Width in pixels of the calibration gutter of every lead (0 when the pulse is hidden). */
  public int getGutterWidth() {
    return calibrationPulse ? (int) Math.round(GUTTER_MM * getPixelPerMm()) : 0;
  }

  /** Screen pixels per millimetre of paper at the current zoom. */
  public double getPixelPerMm() {
    return WaveLayoutManager.pixelPerMm(zoomRatio);
  }

  /** Pixels of one second of signal at the current speed and zoom. */
  public double getPixelsPerSecond() {
    return waveLayoutManager == null ? 0 : waveLayoutManager.getPixelsPerSecond();
  }

  /** The title of the superimposed lead at a position, for the shared legend. */
  public String getOverlayTitle(int index) {
    if (waveLayoutManager != null) {
      List<LeadPanel> ordered = waveLayoutManager.getSortedComponents();
      if (index >= 0 && index < ordered.size()) {
        return ordered.get(index).getDisplayTitle();
      }
    }
    return "";
  }

  /** The heart rate estimated from the signal, or null. */
  public Double getHeartRate() {
    return heartRate;
  }

  public void setFormat(Format format) {
    currentFormat = format == null ? Format.DEFAULT : format;
    for (Component c : pane.getComponents()) {
      if (c instanceof LeadPanel lead) {
        lead.setInverted(cabrera && Lead.AVR.equals(lead.getChannels().getLead()));
      }
    }
    double column = currentFormat.getColumnSeconds();
    List<LeadPanel> ordered = waveLayoutManager.getSortedComponents();
    int i = 0;
    for (LeadPanel lead : ordered) {
      if (lead.getChannels().getLead().isRhythm()) {
        lead.setTime(0, column < 0 ? Integer.MAX_VALUE : 10);
      } else if (column < 0) {
        lead.setTime(0, Integer.MAX_VALUE);
      } else {
        int columns = currentFormat.getXlayoutSize();
        lead.setTime((i % columns) * column, column);
        i++;
      }
    }
    this.pane.revalidate();
    this.pane.repaint();
    if (overviewPanel != null) {
      overviewPanel.repaint();
    }
  }

  public int getChannelNumber() {
    return channelNumber;
  }

  public int getMvCells() {
    return mvCells;
  }

  public double getSeconds() {
    return seconds;
  }

  public int getSamplesPerSecond() {
    return samplesPerSecond;
  }

  public Format getCurrentFormat() {
    return currentFormat;
  }

  public double getSpeed() {
    if (waveLayoutManager != null) {
      return waveLayoutManager.getSpeed();
    }
    return WaveLayoutManager.AUTO_SPEED;
  }

  public int getAmplitude() {
    if (waveLayoutManager != null) {
      return waveLayoutManager.getAmplitude();
    }
    return WaveLayoutManager.AUTO_AMPLITUDE;
  }

  public InfoPanel getInfoPanel() {
    return infoPanel;
  }

  /** The channels of the displayed multiplex group. */
  public List<ChannelDefinition> getChannels() {
    return channels;
  }

  /** The samples of the displayed multiplex group, or null before a series is displayed. */
  public WaveDataReadable getWaveData() {
    return waveData;
  }

  /** The panel holding the leads, or null before a series is displayed. */
  JPanel getLeadPane() {
    return pane;
  }

  /** Scrolls the leads so that the given time is at the left of the viewport. */
  public void scrollToSeconds(double time) {
    if (scrollPane == null || pane == null || seconds <= 0) {
      return;
    }
    int gutter = getGutterWidth();
    int x = gutter + (int) Math.round(time * getPixelsPerSecond());
    int max = Math.max(0, pane.getWidth() - scrollPane.getViewport().getWidth());
    scrollPane
        .getViewport()
        .setViewPosition(
            new java.awt.Point(
                Math.max(0, Math.min(x, max)), scrollPane.getViewport().getViewPosition().y));
  }

  /** Scrolls by a share of the viewport width (1 = one page forward, -1 = one page back). */
  public void scrollPages(double pages) {
    if (scrollPane == null || pane == null) {
      return;
    }
    java.awt.Point position = scrollPane.getViewport().getViewPosition();
    int width = scrollPane.getViewport().getWidth();
    int max = Math.max(0, pane.getWidth() - width);
    int x = (int) Math.round(position.x + pages * width);
    scrollPane
        .getViewport()
        .setViewPosition(new java.awt.Point(Math.max(0, Math.min(x, max)), position.y));
  }

  /** Width in pixels of an exported page: the leads as laid out. */
  public int getPageWidth() {
    return pane == null ? 0 : pane.getWidth();
  }

  /** Height in pixels of an exported page: the patient header above the leads. */
  public int getPageHeight() {
    return pane == null ? 0 : pane.getHeight() + HEADER_HEIGHT;
  }

  /** Paints the patient header and the leads at their current size, pixel units. */
  public void paintPage(Graphics2D g2d) {
    if (pane == null) {
      return;
    }
    AffineTransform transform = g2d.getTransform();
    g2d.setColor(Color.WHITE);
    g2d.fillRect(0, 0, pane.getWidth(), HEADER_HEIGHT);
    printHeader(g2d, pane.getWidth() / 2);
    g2d.translate(0, HEADER_HEIGHT);
    boolean wasBuffered = ExportImage.disableDoubleBuffering(pane);
    pane.paint(g2d);
    ExportImage.restoreDoubleBuffering(pane, wasBuffered);
    g2d.setTransform(transform);
  }

  /**
   * Prints the page at the paper scale when it fits (one screen millimetre is one printed
   * millimetre, so 25 mm/s and 10 mm/mV stay true), scaled down to the page otherwise.
   */
  public void printWave(Graphics2D g2d, PageFormat f) {
    if ((f == null) || (g2d == null) || pane == null) {
      return;
    }
    double pointsPerPixel = 72.0 / 25.4 / getPixelPerMm();
    double headerPoints = HEADER_HEIGHT * pointsPerPixel;
    double canvasWidth = pane.getWidth();
    double canvasHeight = pane.getHeight();
    double fit =
        Math.min(
            f.getImageableWidth() / canvasWidth,
            (f.getImageableHeight() - headerPoints) / canvasHeight);
    double scale = Math.min(pointsPerPixel, fit);

    AffineTransform originalTransform = g2d.getTransform();
    g2d.translate(f.getImageableX(), f.getImageableY());
    g2d.scale(scale, scale);
    paintPage(g2d);
    g2d.setTransform(originalTransform);
  }

  private void printHeader(Graphics2D g2, int midWidth) {
    DataExplorerView dicomView = GuiUtils.getUICore().getExplorerPlugin(DicomExplorer.NAME);
    DicomModel model = null;
    if (dicomView != null) {
      model = (DicomModel) dicomView.getDataExplorerModel();
    }
    if (model != null) {
      MediaSeriesGroup study = model.getParent(series, DicomModel.study);
      MediaSeriesGroup patient = model.getParent(series, DicomModel.patient);

      DicomSpecialElement dcm =
          DicomModel.getFirstSpecialElement(series, DicomSpecialElement.class);
      if (dcm != null && patient != null && study != null) {
        g2.setColor(Color.black);
        g2.setFont(new Font("SanSerif", Font.PLAIN, 11));
        FontMetrics fontMetrics = g2.getFontMetrics();
        final int fontHeight = fontMetrics.getHeight();
        float drawY = fontHeight;
        TagW patNameTag = TagD.get(Tag.PatientName);
        g2.drawString(
            patNameTag.getFormattedTagValue(
                IdentityMask.maskValue(patNameTag, patient.getTagValue(patNameTag)), null),
            0,
            drawY);
        StringBuilder studyDate =
            new StringBuilder(
                new TagView(
                        TagD.getTagFromIDs(
                            Tag.AcquisitionDate,
                            Tag.ContentDate,
                            Tag.DateOfSecondaryCapture,
                            Tag.SeriesDate,
                            Tag.StudyDate))
                    .getFormattedText(dcm));
        studyDate.append(" - ");
        studyDate.append(
            new TagView(
                    TagD.getTagFromIDs(
                        Tag.AcquisitionTime,
                        Tag.ContentTime,
                        Tag.TimeOfSecondaryCapture,
                        Tag.SeriesTime,
                        Tag.StudyTime))
                .getFormattedText(dcm));
        g2.drawString(studyDate.toString(), midWidth, drawY);
        drawY += fontHeight;

        TagW patBirthTag = TagD.get(Tag.PatientBirthDate);
        StringBuilder birthDate =
            new StringBuilder(
                patBirthTag.getFormattedTagValue(
                    IdentityMask.maskValue(patBirthTag, patient.getTagValue(patBirthTag)), null));
        TagW patSexTag = TagD.get(Tag.PatientSex);
        birthDate.append(" - ");
        birthDate.append(
            patSexTag.getFormattedTagValue(
                IdentityMask.maskValue(patSexTag, patient.getTagValue(patSexTag)), null));
        g2.drawString(birthDate.toString(), 0, drawY);
        TagW studyDesTag = TagD.get(Tag.StudyDescription);
        g2.drawString(
            studyDesTag.getFormattedTagValue(
                IdentityMask.maskValue(studyDesTag, study.getTagValue(studyDesTag)),
                "$V:l$40$"), // NON-NLS
            midWidth,
            drawY);
        drawY += fontHeight;

        TagW patIDTag = TagD.get(Tag.PatientID);
        g2.drawString(
            patIDTag.getFormattedTagValue(
                IdentityMask.maskValue(patIDTag, patient.getTagValue(patIDTag)),
                "ID: $V"), // NON-NLS
            0,
            drawY);
        StringBuilder settings = new StringBuilder();
        settings.append(
            getSpeed() == WaveLayoutManager.AUTO_SPEED
                ? String.format("%.1f mm/s", getPixelsPerSecond() / getPixelPerMm()) // NON-NLS
                : String.format("%.0f mm/s", getSpeed())); // NON-NLS
        settings.append("  ");
        settings.append(
            getAmplitude() == WaveLayoutManager.AUTO_AMPLITUDE
                ? "auto mm/mV" // NON-NLS
                : getAmplitude() + " mm/mV"); // NON-NLS
        if (!filter.isIdentity()) {
          settings.append("  "); // NON-NLS
          if (filter.baseline()) {
            settings.append(Messages.getString("ToolPanel.baseline")).append(' ');
          }
          if (filter.lowPassHz() > 0) {
            settings.append(String.format("LP %.0f Hz ", filter.lowPassHz())); // NON-NLS
          }
          if (filter.notchHz() > 0) {
            settings.append(String.format("notch %.0f Hz", filter.notchHz())); // NON-NLS
          }
        }
        g2.drawString(settings.toString(), midWidth, drawY);
      }
    }
  }
}
