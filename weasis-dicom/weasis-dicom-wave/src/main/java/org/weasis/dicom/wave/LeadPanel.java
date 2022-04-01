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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.dicom.wave.SignalMarker.Measure;

public class LeadPanel extends JPanel {

  private final WaveView view;
  private final ChannelDefinition channels;
  private final WaveDataReadable data;
  private final MarkerAnnotation markerAnnotation;

  private double ratioX;
  private final int mvCellCount;
  private double secondCellCount;
  private int sampleNumber;

  private int selectedPosition;
  private final List<SignalMarker> markers;
  private final Measure measureType;
  private final Font fontTitle = new Font("SanSerif", Font.BOLD, 11);

  public LeadPanel(WaveView view, WaveDataReadable data, ChannelDefinition channels) {
    this.view = view;
    this.data = data;
    this.channels = channels;
    this.mvCellCount = view.getMvCells();
    this.secondCellCount = view.getSeconds() * 10;
    this.sampleNumber = data.getNbSamplesPerChannel();
    this.selectedPosition = -1;
    this.markers = new ArrayList<>();
    this.markerAnnotation = new MarkerAnnotation(channels.getLead());
    this.measureType = Measure.VERTICAL;

    addListeners();
    setOpaque(false);
    setBackground(new Color(210, 210, 210));
  }

  public ChannelDefinition getChannels() {
    return channels;
  }

  public MarkerAnnotation getMarkerAnnotation() {
    return markerAnnotation;
  }

  public void setTime(double start, double length) {
    if (start + length > view.getSeconds()) {
      length = view.getSeconds() - start;
    }

    this.secondCellCount = (int) (length * 10);
    this.sampleNumber = (int) (length * view.getSamplesPerSecond());
  }

  private void setSelectedPosition(int position) {
    if (position < 0 || position >= data.getNbSamplesPerChannel()) {
      selectedPosition = -1;
      view.getInfoPanel().setCurrentValues(-1, -1);
    } else {
      selectedPosition = position;
      double sec = selectedPosition / (double) view.getSamplesPerSecond();
      double uV = data.getSample(selectedPosition, channels);
      view.getInfoPanel().setCurrentValues(sec, uV / 1000);
    }
  }

  private void addListeners() {
    MouseAdapter markerAdapter =
        new MouseAdapter() {
          @Override
          public void mouseDragged(MouseEvent e) {
            mouseMoved(e);
          }

          @Override
          public void mouseMoved(MouseEvent e) {
            if (selectedPosition >= 0) {
              if ((e.getModifiersEx() & InputEvent.BUTTON1_DOWN_MASK)
                  == InputEvent.BUTTON1_DOWN_MASK) {
                setSignalMarker(selectedPosition, SignalMarker.Type.START);
              }
              if ((e.getModifiersEx() & InputEvent.BUTTON3_DOWN_MASK)
                  == InputEvent.BUTTON3_DOWN_MASK) {
                setSignalMarker(selectedPosition, SignalMarker.Type.STOP);
              }
              repaint();
            }
          }

          @Override
          public void mouseClicked(MouseEvent e) {
            if (selectedPosition < 0) {
              return;
            }

            if (e.getButton() == MouseEvent.BUTTON1) {
              setSignalMarker(selectedPosition, SignalMarker.Type.START);
            } else if (e.getButton() == MouseEvent.BUTTON3) {
              setSignalMarker(selectedPosition, SignalMarker.Type.STOP);
            } else if (e.getButton() == MouseEvent.BUTTON2) {
              removeAllMarkers();
            }
            repaint();
          }
        };

    MouseAdapter basicMouseListener =
        new MouseAdapter() {
          @Override
          public void mouseEntered(MouseEvent e) {
            setCursor(DefaultView2d.CROSS_CURSOR);
            view.getInfoPanel().setLead(channels.getTitle());
            view.getInfoPanel()
                .setMinMax(
                    (channels.getMinValue() + channels.getBaseline()) / 1000,
                    (channels.getMaxValue() + channels.getBaseline()) / 1000);
          }

          @Override
          public void mouseExited(MouseEvent e) {
            setCursor(DefaultView2d.DEFAULT_CURSOR);
            setSelectedPosition(-1);
            repaint();
          }

          @Override
          public void mouseDragged(MouseEvent e) {
            mouseMoved(e);
          }

          @Override
          public void mouseMoved(MouseEvent e) {
            double sampleWidth = getPreferredSize().getWidth() / sampleNumber;
            double sample = e.getPoint().getX() / sampleWidth;
            setSelectedPosition((int) Math.round(sample));
            repaint();
          }
        };

    this.addMouseListener(markerAdapter);
    this.addMouseMotionListener(markerAdapter);

    this.addMouseListener(basicMouseListener);
    this.addMouseMotionListener(basicMouseListener);
  }

  public void removeAllMarkers() {
    markers.clear();
    markerAnnotation.setStartValues(null, null);
    markerAnnotation.setStopValues(null, null);
    markerAnnotation.setSelectionValues(null, null, null);
    repaint();
  }

  private boolean isMarkerAdapted(SignalMarker marker, Measure tool, SignalMarker.Type type) {
    return tool == marker.getTool() && (type == null || type == marker.getType());
  }

  private void removeMarkers(Measure tool, SignalMarker.Type type) {
    for (int i = markers.size() - 1; i >= 0; i--) {
      SignalMarker marker = markers.get(i);
      if (isMarkerAdapted(marker, tool, type)) {
        markers.remove(i);
      }
    }
  }

  private SignalMarker getSignalMarker(Measure tool, SignalMarker.Type type) {
    for (SignalMarker marker : markers) {
      if (isMarkerAdapted(marker, tool, type)) {
        return marker;
      }
    }
    return null;
  }

  public void shiftSignalMarker(Measure tool, SignalMarker.Type type, int shift) {
    for (SignalMarker marker : markers) {
      if (isMarkerAdapted(marker, tool, type)) {
        marker.setPosition(marker.getPosition() + shift);
      }
    }
  }

  public void setSignalMarker(int position, SignalMarker.Type type) {
    removeMarkers(measureType, type);

    markerAnnotation.setSelectionValues(null, null, null);

    boolean start = type == SignalMarker.Type.START;
    if (position < 0 || position >= data.getNbSamplesPerChannel()) {
      if (start) {
        markerAnnotation.setStartValues(null, null);
      } else {
        markerAnnotation.setStopValues(null, null);
      }
    } else {
      double sec = position / (double) view.getSamplesPerSecond();
      double uV = data.getSample(position, channels);
      markers.add(new SignalMarker(measureType, type, position));
      if (start) {
        markerAnnotation.setStartValues(sec, uV / 1000);
      } else {
        markerAnnotation.setStopValues(sec, uV / 1000);
      }

      updateSelection();
    }
    view.updateMarkersTable();
  }

  private void updateSelection() {
    SignalMarker start = getSignalMarker(measureType, SignalMarker.Type.START);
    SignalMarker stop = getSignalMarker(measureType, SignalMarker.Type.STOP);
    if (start == null || stop == null) {
      return;
    }

    int startPos = start.getPosition();
    int stopPos = stop.getPosition();

    double time = (stopPos - startPos) / (double) view.getSamplesPerSecond();
    double diffuV = data.getSample(stopPos, channels) - data.getSample(startPos, channels);

    int min = Integer.MAX_VALUE;
    int max = Integer.MIN_VALUE;
    for (int i = startPos; i <= stopPos; i++) {
      int val = data.getRawSample(startPos, channels);
      if (val < min) {
        min = val;
      }
      if (val > max) {
        max = val;
      }
    }

    double amplitudeuV =
        (max - min) * channels.getAmplitudeUnitScalingFactor() + channels.getBaseline();

    if (measureType == Measure.VERTICAL) {
      markerAnnotation.setSelectionValues(time, diffuV / 1000, amplitudeuV / 1000);
    } else if (measureType == Measure.HORIZONTAL) {
      markerAnnotation.setSelectionValues(0.0, diffuV / 1000, 0.0);
    }
  }

  @Override
  public void paintComponent(Graphics g) {

    final Graphics2D g2d = (Graphics2D) g;
    Paint oldColor = g2d.getPaint();
    Stroke oldStroke = g2d.getStroke();

    // Rectangle originalBounds = g2.getClipBounds();
    Object[] oldRenderingHints = GuiUtils.setRenderingHints(g, true, true, true);
    // g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

    g2d.setBackground(this.getBackground());
    g2d.clearRect(0, 0, getWidth(), getHeight());

    Dimension dim = getPreferredSize();
    this.ratioX = dim.getWidth() / this.sampleNumber;
    // g2.setClip(this.getVisibleRect());

    drawSelectedBackground(g2d);
    drawGrid(g2d);
    drawWaveData(g2d, dim);
    drawLeadTitle(g2d);
    drawSignalMarkers(g2d, dim);

    GuiUtils.resetRenderingHints(g, oldRenderingHints);
    // g2.setClip(originalBounds);
    g2d.setPaint(oldColor);
    g2d.setStroke(oldStroke);
  }

  private void drawGrid(Graphics2D g2) {
    BasicStroke thinStroke = new BasicStroke(0.25f);
    BasicStroke thickStroke = new BasicStroke(0.5f);
    g2.setColor(new Color(227, 69, 56, 175));

    double pixelPerMm =
        Toolkit.getDefaultToolkit().getScreenResolution() / 25.4 * view.getZoomRatio();

    Dimension dim = getPreferredSize();
    for (int i = 0; i < dim.height / pixelPerMm; i++) {
      g2.setStroke(i % 5 == 0 ? thickStroke : thinStroke);
      g2.draw(new Line2D.Double(0, i * pixelPerMm, dim.getWidth(), i * pixelPerMm));
    }

    for (int i = 0; i < dim.width / pixelPerMm; i++) {
      g2.setStroke(i % 5 == 0 ? thickStroke : thinStroke);
      g2.draw(new Line2D.Double(i * pixelPerMm, 0, i * pixelPerMm, dim.getHeight()));
    }

    g2.setStroke(new BasicStroke(1.4f));
    g2.draw(new Rectangle2D.Double(0.7, 0.0, dim.width - 1.7, dim.height - 1.0));
  }

  private void drawWaveData(Graphics2D g2, Dimension dim) {
    double cellHeight = dim.getHeight() / this.mvCellCount;
    double halfHeight = dim.height / 2.0; // baseline

    g2.setColor(Color.BLACK);
    Stroke stroke = new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    g2.setStroke(stroke);

    Path2D path = new Path2D.Double(Path2D.WIND_NON_ZERO, sampleNumber);
    double x = 0.0;
    double y = halfHeight - (data.getSample(0, channels) / 1000 * cellHeight);
    path.moveTo(x, y);
    for (int i = 1; i < this.sampleNumber; i++) {
      x = ratioX * i;
      y = halfHeight - (data.getSample(i, channels) / 1000 * cellHeight);
      path.lineTo(x, y);
    }
    g2.draw(path);
  }

  private void drawSelectedBackground(Graphics2D g2) {
    SignalMarker start = getSignalMarker(Measure.VERTICAL, SignalMarker.Type.START);
    SignalMarker stop = getSignalMarker(Measure.VERTICAL, SignalMarker.Type.STOP);
    if (start == null || stop == null) {
      return;
    }

    Color background = new Color(230, 230, 230, 100);
    g2.setColor(background);

    double startX = this.ratioX * start.getPosition();
    double stopX = this.ratioX * stop.getPosition();
    if (startX > stopX) {
      double tmp = stopX;
      stopX = startX;
      startX = tmp;
    }

    Rectangle2D rect = new Rectangle2D.Double(startX, 0, stopX - startX, getPreferredSize().height);
    g2.fill(rect);
  }

  private void drawSignalMarkers(Graphics2D g2, Dimension dim) {
    drawMarker(g2, Color.BLUE, selectedPosition, dim);
    for (SignalMarker marker : markers) {
      Color color;
      if (marker.getType() == SignalMarker.Type.START) {
        color = Color.GREEN;
      } else {
        color = Color.CYAN;
      }
      drawMarker(g2, color, marker.getPosition(), dim);
    }
  }

  private void drawMarker(Graphics2D g2, Color color, int position, Dimension dim) {
    if (position < 0) {
      return;
    }

    double x = this.ratioX * position;
    Line2D line = new Line2D.Double(x, 0, x, dim.height);

    g2.setColor(color);
    g2.setStroke(new BasicStroke(0.9f));
    g2.draw(line);
  }

  private void drawLeadTitle(Graphics2D g2) {
    g2.setColor(Color.black);
    g2.setFont(fontTitle);
    g2.drawString(channels.getTitle(), 5, 15);
  }
}
