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
import java.awt.geom.Point2D;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferShort;
import java.awt.print.PageFormat;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.util.StreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagView;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.util.ImagePrint;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.wave.dockable.MeasureAnnotationTool;

public class WaveView extends JPanel implements SeriesViewerListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(WaveView.class);

  private Series<?> series;

  private InfoPanel infoPanel;
  private JPanel pane;

  private int mvCells;
  private double seconds;
  private int channelNumber;
  private int sampleNumber;
  private Format currentFormat;
  private int samplesPerSecond;
  private WaveDataReadable waveData;
  private final List<ChannelDefinition> channels;
  private double zoomRatio = 1.0;

  private WaveLayoutManager waveLayoutManager;

  private MeasureAnnotationTool annotationTool;

  public WaveView() {
    this(null);
  }

  public WaveView(Series<?> series) {
    setLayout(new BorderLayout());
    setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    setPreferredSize(GuiUtils.getDimension(1024, 1024));

    this.channels = new ArrayList<>();
    this.currentFormat = Format.DEFAULT;
    setSeries(series);
  }

  public void updateMarkersTable() {
    List<Object[]> list = new ArrayList<>();
    if (waveLayoutManager != null) {
      for (LeadPanel lead : waveLayoutManager.getSortedComponents()) {
        MarkerAnnotation m = lead.getMarkerAnnotation();
        if (m.getStartSeconds() != null) {
          String leadName = m.getLead().toString();
          list.add(
              new Object[] {
                leadName,
                Messages.getString("WaveView.start_time"),
                MarkerAnnotation.secondFormatter.format(m.getStartSeconds())
              });
          list.add(
              new Object[] {
                leadName,
                Messages.getString("WaveView.start_val"),
                MarkerAnnotation.mVFormatter.format(m.getStartMilliVolt())
              });

          if (m.getStopSeconds() != null) {
            list.add(
                new Object[] {
                  leadName,
                  Messages.getString("WaveView.stop_time"),
                  MarkerAnnotation.secondFormatter.format(m.getStopSeconds())
                });
            list.add(
                new Object[] {
                  leadName,
                  Messages.getString("WaveView.stop_val"),
                  MarkerAnnotation.mVFormatter.format(m.getStopMilliVolt())
                });
          }
          if (m.getDuration() != null) {
            list.add(
                new Object[] {
                  leadName,
                  Messages.getString("WaveView.duration"),
                  MarkerAnnotation.secondFormatter.format(m.getDuration())
                });
            list.add(
                new Object[] {
                  leadName,
                  Messages.getString("WaveView.diff"),
                  MarkerAnnotation.mVFormatter.format(m.getDiffmV())
                });
            list.add(
                new Object[] {
                  leadName,
                  Messages.getString("WaveView.amplitude"),
                  MarkerAnnotation.mVFormatter.format(m.getAmplitude())
                });
          }
        }
      }
    }

    annotationTool.updateMeasuredItems(list);
  }

  public void clearMeasurements() {
    if (waveLayoutManager != null) {
      for (LeadPanel lead : waveLayoutManager.getSortedComponents()) {
        lead.removeAllMarkers();
      }
    }
    annotationTool.updateMeasuredItems(null);
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
    DicomMediaIO dicomImageLoader = media.getMediaReader();
    Attributes attributes = dicomImageLoader.getDicomObject();
    if (attributes != null) {
      // TODO handle several Waveforms: display a combo
      Attributes dcm = Optional.of(attributes.getNestedDataset(Tag.WaveformSequence)).get();

      this.channelNumber =
          DicomMediaUtils.getIntegerFromDicomElement(dcm, Tag.NumberOfWaveformChannels, 0);
      Sequence chDefSeq = Optional.of(dcm.getSequence(Tag.ChannelDefinitionSequence)).get();
      this.channels.clear();
      for (int i = 0; i < chDefSeq.size(); i++) {
        channels.add(new ChannelDefinition(chDefSeq.get(i), i));
      }

      // TODO show when derived
      String originality = dcm.getString(Tag.WaveformOriginality);

      this.sampleNumber =
          DicomMediaUtils.getIntegerFromDicomElement(dcm, Tag.NumberOfWaveformSamples, 0);
      double frequency = DicomMediaUtils.getDoubleFromDicomElement(dcm, Tag.SamplingFrequency, 1.0);
      this.seconds = sampleNumber / frequency;
      this.samplesPerSecond = (int) (sampleNumber / seconds);

      readWaveformData(dcm);
      getMinMax(channels);

      JScrollPane scrollPane = new JScrollPane();
      scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
      scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

      double speed =
          waveLayoutManager == null ? WaveLayoutManager.AUTO_SPEED : waveLayoutManager.getSpeed();
      int amplitude =
          waveLayoutManager == null
              ? WaveLayoutManager.AUTO_AMPLITUDE
              : waveLayoutManager.getAmplitude();

      this.waveLayoutManager = new WaveLayoutManager(this, currentFormat, speed, amplitude);
      this.pane = new JPanel(waveLayoutManager);
      JPanel channelWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
      channelWrap.add(pane);
      scrollPane.setViewportView(channelWrap);

      addChannelPanels();
      if (getChannelNumber() < 12) {
        this.currentFormat = Format.DEFAULT;
      }
      setFormat(currentFormat);

      // Panel which includes the Buttons for zooming
      ToolPanel tools = new ToolPanel(this);
      tools.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
      // Panel with information about the channel the mouse cursor is over
      this.infoPanel = new InfoPanel(zoomRatio);

      JPanel wrap = new JPanel(new BorderLayout());
      wrap.add(tools, BorderLayout.NORTH);
      wrap.add(infoPanel, BorderLayout.SOUTH);

      this.add(wrap, BorderLayout.NORTH);
      this.add(scrollPane, BorderLayout.CENTER);
      this.addComponentListener(
          new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
              pane.revalidate();
            }
          });
    }
    updateMarkersTable();
    annotationTool.readAnnotations(attributes);
  }

  private void readWaveformData(Attributes dcm) throws IOException {
    Object wdata = dcm.getValue(Tag.WaveformData);
    ByteArrayOutputStream array;
    if (wdata instanceof BulkData bulkData) {
      try (BufferedInputStream input = new BufferedInputStream(bulkData.openStream())) {
        array = new ByteArrayOutputStream(bulkData.length());
        StreamUtils.copy(input, array);
      } catch (Exception e) {
        LOGGER.error("Reading Waveform data");
        return;
      }
    } else {
      throw new IOException("Cannot read Waveform data");
    }

    int bitsAllocated =
        DicomMediaUtils.getIntegerFromDicomElement(dcm, Tag.WaveformBitsAllocated, 0);

    ByteBuffer byteBuffer = ByteBuffer.wrap(array.toByteArray());
    byteBuffer.order(dcm.bigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

    if (bitsAllocated == 16) {
      short[] shortData;

      shortData = new short[byteBuffer.limit() / 2];
      for (int i = 0; i < shortData.length; i++) {
        shortData[i] = byteBuffer.getShort();
      }
      DataBufferShort dataBuffer = new DataBufferShort(shortData, shortData.length, 0);
      waveData = new WaveShortData(dataBuffer, channelNumber, sampleNumber);

    } else if (bitsAllocated == 8) {
      byte[] byteData = byteBuffer.array();
      DataBufferByte dataBuffer = new DataBufferByte(byteData, byteData.length, 0);
      waveData = new WaveByteData(dataBuffer, channelNumber, sampleNumber);
    } else {
      throw new IOException("Unexpected bitsAllocated value: " + bitsAllocated);
    }
  }

  private void addChannelPanels() {
    for (ChannelDefinition channel : channels) {
      LeadPanel panel = new LeadPanel(this, waveData, channel);
      pane.add(channel.getTitle(), panel);

      if (Lead.II == channel.getLead()) {
        LeadPanel rhythm =
            new LeadPanel(this, waveData, new ChannelDefinition(channel, Lead.RHYTHM.toString()));
        pane.add("rhythm", rhythm); // NON-NLS
      }
    }
  }

  private void getMinMax(List<ChannelDefinition> channels) {
    WaveDataReadable db = waveData;
    if (db != null) {
      double minAll = Double.MAX_VALUE;
      double maxAll = Double.MIN_VALUE;

      for (int i = 0; i < channelNumber; i++) {
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        ChannelDefinition c = channels.get(i);
        for (int j = 0; j < sampleNumber; j++) {
          double val = db.getSample(j, c);
          if (val < min) {
            min = val;
          }
          if (val > max) {
            max = val;
          }
        }
        c.setMinValue(min);
        c.setMaxValue(max);
        if (min < minAll) {
          minAll = min;
        }
        if (max > maxAll) {
          maxAll = max;
        }
      }

      double minmaxuV = Math.max(Math.abs(maxAll), Math.abs(minAll));
      double minmaxmV = minmaxuV / 1000;
      this.mvCells = (int) Math.ceil(minmaxmV) * 2;
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

  public void setFormat(Format format) {
    currentFormat = format;
    this.waveLayoutManager.setWaveFormat(currentFormat);
    if (currentFormat == Format.TWO) {
      List<LeadPanel> ordered = waveLayoutManager.getSortedComponents();
      for (int i = 0; i < channelNumber; i++) {
        double startTime = (i % 2) != 0 ? 5.0 : 0;
        ordered.get(i).setTime(startTime, 5);
      }
    } else if (currentFormat == Format.FOUR || currentFormat == Format.FOUR_RHYTHM) {
      List<LeadPanel> ordered = waveLayoutManager.getSortedComponents();
      for (int i = 0; i < channelNumber; i++) {
        int index = i % 4;
        double startTime;
        if (index == 1) {
          startTime = 2.5;
        } else if (index == 2) {
          startTime = 5.0;
        } else if (index == 3) {
          startTime = 7.5;
        } else {
          startTime = 0.0;
        }
        ordered.get(i).setTime(startTime, 2.5);
      }
    } else {
      for (Component c : pane.getComponents()) {
        ((LeadPanel) c).setTime(0, Integer.MAX_VALUE);
      }
    }
    this.pane.revalidate();
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

  public void printWave(Graphics2D g2d, PageFormat f) {
    if ((f == null) || (g2d == null)) {
      return;
    }
    Point2D.Double placeholder = new Point2D.Double(f.getImageableWidth(), f.getImageableHeight());

    double textOffset = 50.0;
    double canvasWidth = pane.getWidth();
    double canvasHeight = pane.getHeight() - textOffset;
    double scaleCanvas = Math.min(placeholder.x / canvasWidth, placeholder.y / canvasHeight);

    AffineTransform originalTransform = g2d.getTransform();
    boolean wasBuffered = ImagePrint.disableDoubleBuffering(pane);
    g2d.translate(f.getImageableX(), f.getImageableY());
    printHeader(g2d, (int) (f.getImageableWidth() / 2));
    g2d.translate(0, textOffset);
    g2d.scale(scaleCanvas, scaleCanvas);
    pane.paint(g2d);
    ImagePrint.restoreDoubleBuffering(pane, wasBuffered);
    g2d.setTransform(originalTransform);
  }

  private void printHeader(Graphics2D g2, int midWidth) {
    DataExplorerView dicomView =
        org.weasis.core.ui.docking.UIManager.getExplorerplugin(DicomExplorer.NAME);
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
        g2.setFont(new Font("SanSerif", Font.PLAIN, 9));
        FontMetrics fontMetrics = g2.getFontMetrics();
        final int fontHeight = fontMetrics.getHeight();
        float drawY = fontHeight;
        TagW patNameTag = TagD.get(Tag.PatientName);
        g2.drawString(
            patNameTag.getFormattedTagValue(patient.getTagValue(patNameTag), null), 0, drawY);
        StringBuilder studyDate =
            new StringBuilder(
                new TagView(
                        TagD.getTagFromIDs(
                            Tag.AcquisitionDate,
                            Tag.ContentDate,
                            Tag.DateOfSecondaryCapture,
                            Tag.SeriesDate,
                            Tag.StudyDate))
                    .getFormattedText(false, dcm));
        studyDate.append(" - ");
        studyDate.append(
            new TagView(
                    TagD.getTagFromIDs(
                        Tag.AcquisitionTime,
                        Tag.ContentTime,
                        Tag.TimeOfSecondaryCapture,
                        Tag.SeriesTime,
                        Tag.StudyTime))
                .getFormattedText(false, dcm));
        g2.drawString(studyDate.toString(), midWidth, drawY);
        drawY += fontHeight;

        TagW patBirthTag = TagD.get(Tag.PatientBirthDate);
        StringBuilder birthDate =
            new StringBuilder(
                patBirthTag.getFormattedTagValue(patient.getTagValue(patBirthTag), null));
        TagW patSexTag = TagD.get(Tag.PatientSex);
        birthDate.append(" - ");
        birthDate.append(patSexTag.getFormattedTagValue(patient.getTagValue(patSexTag), null));
        g2.drawString(birthDate.toString(), 0, drawY);
        TagW studyDesTag = TagD.get(Tag.StudyDescription);
        g2.drawString(
            studyDesTag.getFormattedTagValue(study.getTagValue(studyDesTag), "$V:l$40$"), // NON-NLS
            midWidth,
            drawY);
        drawY += fontHeight;

        TagW patIDTag = TagD.get(Tag.PatientID);
        g2.drawString(
            patIDTag.getFormattedTagValue(patient.getTagValue(patIDTag), "ID: $V"), // NON-NLS
            0,
            drawY);
        TagW studyAcNbTag = TagD.get(Tag.AccessionNumber);
        g2.drawString(
            studyAcNbTag.getFormattedTagValue(study.getTagValue(studyAcNbTag), null),
            midWidth,
            drawY);
      }
    }
  }
}
