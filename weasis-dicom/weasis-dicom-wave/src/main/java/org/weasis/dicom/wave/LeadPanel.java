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

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.ui.editor.image.DefaultView2d;

/**
 * One lead of the waveform: the millimetre grid, the calibration pulse, the signal (filtered when
 * the view asks for it), the annotations, the cursor and the calipers. Only the clip is painted, so
 * a cursor move repaints two narrow strips and scrolling repaints the exposed band; the signal is
 * drawn as its min / max envelope when several samples fall on a pixel.
 *
 * <p>In the superimposed format every lead is an overlay of the same area: the overlays paint their
 * trace only, in their colour, above the base lead that paints the grid.
 */
public class LeadPanel extends JPanel {

  private static final Color GRID_COLOR = new Color(227, 69, 56, 175);
  private static final Color ANNOTATION_COLOR = new Color(20, 90, 200);
  private static final Color EXTERNAL_ANNOTATION_COLOR = Color.MAGENTA;
  private static final Stroke THIN_STROKE = new BasicStroke(0.25f);
  private static final Stroke THICK_STROKE = new BasicStroke(0.5f);
  private static final Stroke BORDER_STROKE = new BasicStroke(1.4f);
  private static final Stroke SIGNAL_STROKE =
      new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);
  private static final Stroke MARKER_STROKE = new BasicStroke(0.9f);
  private static final Stroke ANNOTATION_STROKE =
      new BasicStroke(
          1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] {4f, 3f}, 0f);

  /** Trace colours of the superimposed leads, by position in the layout. */
  static final Color[] OVERLAY_COLORS = {
    Color.BLACK,
    new Color(0, 90, 200),
    new Color(200, 30, 30),
    new Color(0, 140, 60),
    new Color(230, 120, 0),
    new Color(130, 0, 160),
    new Color(0, 150, 160),
    new Color(120, 70, 20),
    new Color(200, 0, 140),
    new Color(90, 110, 0),
    new Color(20, 40, 120),
    new Color(110, 110, 110)
  };

  /** Half width of the strip repainted around a cursor or marker line. */
  private static final int LINE_MARGIN = 3;

  private static final java.text.DecimalFormat DC_FORMAT =
      new java.text.DecimalFormat("#0.#"); // NON-NLS

  /** Visible samples read in one call when no summary covers them (16 MB of buffer). */
  private static final int MAX_BUFFERED_SAMPLES = 1 << 22;

  /** Above this many visible samples the display filters are not applied. */
  private static final int MAX_FILTERED_SAMPLES = 1 << 20;

  private final WaveView view;
  private final ChannelDefinition channels;
  private final WaveDataReadable data;

  private double ratioX;
  private int gutter;
  private final int mvCellCount;
  private double secondCellCount;
  private int sampleNumber;
  private int sampleOffset;
  private boolean inverted;
  private int overlayIndex = -1;

  private int selectedPosition;
  private final List<LeadAnnotation> annotations = new ArrayList<>();

  /** Pixels around a caliper line within which it can be grabbed. */
  private static final int HANDLE_RADIUS = 5;

  private static final Color CALIPER_GHOST = new Color(90, 90, 90, 140);
  private static final Color AMPLITUDE_COLOR = new Color(0, 95, 210);
  private static final Color AMPLITUDE_FILL = new Color(0, 95, 210, 40);

  /** Half width in pixels of the shaded range of an amplitude caliper around its anchor. */
  private static final int AMPLITUDE_HALF_WIDTH = 60;

  private static final Stroke GHOST_STROKE =
      new BasicStroke(
          0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] {2f, 3f}, 0f);

  private final List<Caliper> calipers = new ArrayList<>();

  /** The caliper still waiting for its second point. */
  private Caliper pending;

  /** The caliper whose handle is being dragged, and which handle. */
  private Caliper dragged;

  private int draggedHandle;

  /** The caliper and handle the arrow keys move. */
  private Caliper lastEdited;

  private int lastHandle = 1;

  private final Font fontTitle = new Font("SanSerif", Font.BOLD, 11);
  private final Font fontLabel = new Font("SanSerif", Font.PLAIN, 10);

  private int[] rawBuffer = new int[0];
  private final int[] range = new int[2];

  public LeadPanel(WaveView view, WaveDataReadable data, ChannelDefinition channels) {
    this.view = view;
    this.data = data;
    this.channels = channels;
    this.mvCellCount = view.getMvCells();
    this.secondCellCount = view.getSeconds() * 10;
    this.sampleNumber = data.getNbSamplesPerChannel();
    this.sampleOffset = 0;
    this.selectedPosition = -1;

    addListeners();
    addKeyBindings();
    setFocusable(true);
    setOpaque(true);
    setBackground(new Color(210, 210, 210));
  }

  public ChannelDefinition getChannels() {
    return channels;
  }

  /** The calipers of this lead, in creation order. */
  public List<Caliper> getCalipers() {
    return calipers;
  }

  /** The unit of the values of this lead. */
  public String getUnit() {
    return channels.getDisplayUnit();
  }

  /** Whether the lead is displayed with its sign changed (-aVR of the Cabrera sequence). */
  public boolean isInverted() {
    return inverted;
  }

  public void setInverted(boolean inverted) {
    if (this.inverted != inverted) {
      this.inverted = inverted;
      repaint();
    }
  }

  /**
   * -1 for a lead of its own; otherwise the position of the lead among the superimposed leads,
   * which gives its colour. The base of the superimposition is set with {@link #setOverlayBase}.
   */
  public void setOverlayIndex(int index) {
    if (this.overlayIndex != index) {
      this.overlayIndex = index;
      setOpaque(index < 0 || overlayBase);
      repaint();
    }
  }

  private boolean overlayBase;

  /** The superimposed lead that paints the background and the grid under the others. */
  public void setOverlayBase(boolean base) {
    this.overlayBase = base;
    setOpaque(overlayIndex < 0 || base);
  }

  private boolean isOverlay() {
    return overlayIndex >= 0 && !overlayBase;
  }

  private Color traceColor() {
    return overlayIndex < 0
        ? Color.BLACK
        : OVERLAY_COLORS[Math.floorMod(overlayIndex, OVERLAY_COLORS.length)];
  }

  /** The lead name as displayed, "-" prefixed when inverted. */
  public String getDisplayTitle() {
    String title = channels.getTitle();
    return inverted ? "-" + title : title;
  }

  /** The title with the DC level removed from the display and the cell value when not 1 mV. */
  private String getPaintedTitle() {
    StringBuilder title = new StringBuilder(getDisplayTitle());
    double offset = channels.getDisplayOffset();
    if (offset != 0) {
      double shown = channels.toDisplay(inverted ? -offset : offset);
      title
          .append(" [DC ") // NON-NLS
          .append(DC_FORMAT.format(shown))
          .append(' ')
          .append(channels.getDisplayUnit())
          .append(']');
    }
    if (!channels.isVoltage() || channels.getCellValue() != 1.0) {
      title
          .append(" [")
          .append(WaveFormats.value(channels.getCellValue(), channels.getDisplayUnit()))
          .append("/div]"); // NON-NLS
    }
    return title.toString();
  }

  /** Replaces the temporal annotations of this lead and repaints. */
  public void setAnnotations(List<LeadAnnotation> items) {
    annotations.clear();
    if (items != null) {
      annotations.addAll(items);
    }
    repaint();
  }

  public void setTime(double start, double length) {
    if (start + length > view.getSeconds()) {
      length = view.getSeconds() - start;
    }

    this.secondCellCount = (int) (length * 10);
    this.sampleNumber = (int) (length * view.getSamplesPerSecond());
    this.sampleOffset = (int) (start * view.getSamplesPerSecond());
  }

  /** First sample shown by this panel (absolute index). */
  public int getSampleOffset() {
    return sampleOffset;
  }

  /** Number of samples shown by this panel. */
  public int getSampleNumber() {
    return sampleNumber;
  }

  // ---- values -------------------------------------------------------------------------------

  /** The displayed value (display unit, DC level removed, sign applied) of a stored value. */
  private double display(int raw) {
    double physical = channels.toPhysical(raw);
    return channels.toDisplay(inverted ? -physical : physical);
  }

  /** The displayed value of the sample at an absolute index, filtered like the trace. */
  private double sampleValue(int index) {
    if (view.getFilter().isIdentity()) {
      return display(data.getRawSample(index, channels));
    }
    return signal(index - sampleOffset, 1)[0];
  }

  /**
   * The displayed values of {@code count} samples from the relative index {@code first}, with the
   * display filters applied (unless the range is too long for them). Positions outside the
   * recording repeat its first or last sample so that the filters see no step there.
   */
  private double[] signal(int first, int count) {
    SignalFilter filter = view.getFilter();
    int fs = view.getSamplesPerSecond();
    boolean filtered = !filter.isIdentity() && count <= MAX_FILTERED_SAMPLES;
    int margin = filtered ? filter.margin(fs) : 0;
    int total = count + 2 * margin;
    int start = sampleOffset + first - margin;
    int[] raw = rawBuffer(total);
    data.readRaw(start, total, channels, raw);
    int last = data.getNbSamplesPerChannel() - 1;
    for (int i = 0; i < total; i++) {
      int index = start + i;
      if (index < 0) {
        raw[i] = data.getRawSample(0, channels);
      } else if (index > last) {
        raw[i] = data.getRawSample(last, channels);
      }
    }
    double[] values = new double[total];
    for (int i = 0; i < total; i++) {
      values[i] = display(raw[i]);
    }
    if (filtered) {
      filter.apply(values, total, fs);
    }
    if (margin == 0) {
      return values;
    }
    double[] out = new double[count];
    System.arraycopy(values, margin, out, 0, count);
    return out;
  }

  private int[] rawBuffer(int size) {
    if (rawBuffer.length < size) {
      rawBuffer = new int[Math.max(size, Math.min(2 * rawBuffer.length, MAX_BUFFERED_SAMPLES))];
    }
    return rawBuffer;
  }

  // ---- geometry -----------------------------------------------------------------------------

  private void updateScale() {
    this.ratioX = view.getPixelsPerSecond() / Math.max(1, view.getSamplesPerSecond());
    this.gutter = view.getGutterWidth();
  }

  /** The x of a relative sample index. */
  private double xOf(double relativeSample) {
    return gutter + ratioX * relativeSample;
  }

  /** The relative sample index at an x. */
  private double sampleAt(double x) {
    return ratioX <= 0 ? -1 : (x - gutter) / ratioX;
  }

  /** Pixels per display unit at the current height. */
  private double pixelsPerUnit() {
    double cellHeight = getHeight() / (double) mvCellCount;
    return cellHeight / channels.getCellValue();
  }

  /** The y of a value in display units. */
  private double yOf(double value) {
    return getHeight() / 2.0 - value * pixelsPerUnit();
  }

  /** The value in display units at a y. */
  private double valueAt(double y) {
    double ppu = pixelsPerUnit();
    return ppu <= 0 ? 0 : (getHeight() / 2.0 - y) / ppu;
  }

  private void setSelectedPosition(int position) {
    int previous = selectedPosition;
    this.selectedPosition = sampleOffset + position;
    if (position < 0
        || position >= sampleNumber
        || selectedPosition >= data.getNbSamplesPerChannel()) {
      this.selectedPosition = -1;
      view.getInfoPanel().setCurrentValues(-1, -1, null);
    } else {
      double sec = selectedPosition / (double) view.getSamplesPerSecond();
      view.getInfoPanel()
          .setCurrentValues(sec, sampleValue(selectedPosition), channels.getDisplayUnit());
    }
    if (previous != selectedPosition) {
      repaintLine(previous);
      repaintLine(selectedPosition);
    }
  }

  /** Repaints the strip around the vertical line of an absolute sample position. */
  private void repaintLine(int position) {
    if (position < 0) {
      return;
    }
    updateScale();
    int x = (int) Math.round(xOf(position - sampleOffset));
    repaint(x - LINE_MARGIN, 0, 2 * LINE_MARGIN + 1, getHeight());
  }

  private void addListeners() {
    MouseAdapter mouse =
        new MouseAdapter() {
          @Override
          public void mouseEntered(MouseEvent e) {
            view.getInfoPanel().setLead(getDisplayTitle());
            double min = channels.getMinValue();
            double max = channels.getMaxValue();
            if (inverted) {
              view.getInfoPanel().setMinMax(-max, -min, channels.getDisplayUnit());
            } else {
              view.getInfoPanel().setMinMax(min, max, channels.getDisplayUnit());
            }
            updateCursor(e.getPoint());
          }

          @Override
          public void mouseExited(MouseEvent e) {
            setCursor(DefaultView2d.DEFAULT_CURSOR);
            setSelectedPosition(Integer.MIN_VALUE);
          }

          @Override
          public void mouseMoved(MouseEvent e) {
            updateScale();
            double sample = sampleAt(e.getPoint().getX());
            setSelectedPosition(sample < 0 ? Integer.MIN_VALUE : (int) Math.round(sample));
            updateCursor(e.getPoint());
          }

          @Override
          public void mousePressed(MouseEvent e) {
            requestFocusInWindow();
            updateScale();
            if (e.isPopupTrigger()) {
              return;
            }
            if (e.getButton() == MouseEvent.BUTTON2) {
              Caliper c = caliperAt(e.getPoint(), new int[1], true);
              if (c != null) {
                removeCaliper(c);
              }
              return;
            }
            if (e.getButton() != MouseEvent.BUTTON1) {
              return;
            }
            if (e.isShiftDown()) {
              removeAllCalipers();
              return;
            }
            int[] handle = new int[1];
            Caliper c = caliperAt(e.getPoint(), handle, false);
            if (c != null) {
              dragged = c;
              draggedHandle = handle[0];
              lastEdited = c;
              lastHandle = handle[0];
              return;
            }
            if (pending != null && !e.isControlDown()) {
              setHandle(pending, 1, e.getPoint());
              finish(pending);
              return;
            }
            Caliper.Kind kind = e.isControlDown() ? Caliper.Kind.AMPLITUDE : view.getCaliperKind();
            startCaliper(kind, e.getPoint());
          }

          @Override
          public void mouseDragged(MouseEvent e) {
            updateScale();
            double sample = sampleAt(e.getPoint().getX());
            setSelectedPosition(sample < 0 ? Integer.MIN_VALUE : (int) Math.round(sample));
            if (dragged != null) {
              setHandle(dragged, draggedHandle, e.getPoint());
              measure(dragged);
              view.updateMarkersTable();
              repaintCalipers();
            }
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            if (dragged == null) {
              return;
            }
            Caliper c = dragged;
            dragged = null;
            if (c == pending && c.isEmpty()) {
              // A click without a drag: the second point comes with the next click
              repaintCalipers();
              return;
            }
            finish(c);
          }

          @Override
          public void mouseClicked(MouseEvent e) {
            if (e.getButton() == MouseEvent.BUTTON3) {
              updateScale();
              if (pending != null) {
                setHandle(pending, 1, e.getPoint());
                finish(pending);
                return;
              }
              Caliper c = caliperAt(e.getPoint(), new int[1], true);
              if (c != null) {
                showCaliperMenu(c, e.getPoint());
              }
            }
          }
        };
    this.addMouseListener(mouse);
    this.addMouseMotionListener(mouse);
  }

  /** The arrow keys move the last handle touched by one sample or one hundredth of a unit. */
  private void addKeyBindings() {
    bind("LEFT", -1, 0, false); // NON-NLS
    bind("RIGHT", 1, 0, false); // NON-NLS
    bind("shift LEFT", -10, 0, false); // NON-NLS
    bind("shift RIGHT", 10, 0, false); // NON-NLS
    bind("UP", 0, 1, false); // NON-NLS
    bind("DOWN", 0, -1, false); // NON-NLS
    bind("shift UP", 0, 10, false); // NON-NLS
    bind("shift DOWN", 0, -10, false); // NON-NLS
    bind("DELETE", 0, 0, true); // NON-NLS
    bind("BACK_SPACE", 0, 0, true); // NON-NLS
  }

  private void bind(String keys, int dx, int dy, boolean delete) {
    getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(keys), keys);
    getActionMap()
        .put(
            keys,
            new AbstractAction() {
              @Override
              public void actionPerformed(ActionEvent e) {
                nudge(dx, dy, delete);
              }
            });
  }

  private void nudge(int dx, int dy, boolean delete) {
    if (lastEdited == null || !calipers.contains(lastEdited)) {
      return;
    }
    if (delete) {
      removeCaliper(lastEdited);
      return;
    }
    if (lastEdited.isTime() && dx != 0) {
      int sample = lastEdited.getPoint(lastHandle) + dx;
      int max = data.getNbSamplesPerChannel() - 1;
      lastEdited.setPoint(lastHandle, Math.max(0, Math.min(max, sample)));
    } else if (!lastEdited.isTime() && dy != 0) {
      lastEdited.setLevel(lastHandle, lastEdited.getLevel(lastHandle) + dy * 0.01);
    } else {
      return;
    }
    measure(lastEdited);
    view.updateMarkersTable();
    repaintCalipers();
  }

  /** A resize cursor near a caliper handle, the cross elsewhere. */
  private void updateCursor(Point p) {
    int[] handle = new int[1];
    Caliper c = caliperAt(p, handle, false);
    if (c == null) {
      setCursor(DefaultView2d.CROSS_CURSOR);
    } else {
      setCursor(
          Cursor.getPredefinedCursor(c.isTime() ? Cursor.E_RESIZE_CURSOR : Cursor.N_RESIZE_CURSOR));
    }
  }

  // ---- calipers --------------------------------------------------------------------------

  private void startCaliper(Caliper.Kind kind, Point p) {
    int sample = clampSample((int) Math.round(sampleAt(p.getX())));
    Caliper c;
    if (kind == Caliper.Kind.AMPLITUDE) {
      double level = valueAt(p.getY());
      c = Caliper.amplitude(sampleOffset + sample, level, level, view.getCaliperLabel());
    } else {
      c = Caliper.time(sampleOffset + sample, sampleOffset + sample, view.getCaliperLabel());
    }
    calipers.add(c);
    pending = c;
    dragged = c;
    draggedHandle = 1;
    lastEdited = c;
    lastHandle = 1;
    repaintCalipers();
  }

  private int clampSample(int relative) {
    int max = Math.min(sampleNumber, data.getNbSamplesPerChannel() - sampleOffset) - 1;
    return Math.max(0, Math.min(max, relative));
  }

  private void setHandle(Caliper c, int handle, Point p) {
    if (c.isTime()) {
      c.setPoint(handle, sampleOffset + clampSample((int) Math.round(sampleAt(p.getX()))));
    } else {
      c.setLevel(handle, valueAt(p.getY()));
    }
  }

  /** Completes a caliper: measures it and publishes it. */
  private void finish(Caliper c) {
    if (c == pending) {
      pending = null;
    }
    if (c.isEmpty()) {
      calipers.remove(c);
    } else {
      measure(c);
    }
    view.updateMarkersTable();
    repaint();
  }

  /** Adds a measured time caliper over absolute sample indexes. */
  public Caliper addTimeCaliper(int start, int stop, String label) {
    Caliper c = Caliper.time(start, stop, label);
    calipers.add(c);
    measure(c);
    view.updateMarkersTable();
    repaint();
    return c;
  }

  /** Adds an amplitude caliper between two levels of the display unit. */
  public Caliper addAmplitudeCaliper(int anchor, double level1, double level2, String label) {
    Caliper c = Caliper.amplitude(anchor, level1, level2, label);
    calipers.add(c);
    measure(c);
    view.updateMarkersTable();
    repaint();
    return c;
  }

  public void removeCaliper(Caliper c) {
    calipers.remove(c);
    if (pending == c) {
      pending = null;
    }
    if (lastEdited == c) {
      lastEdited = null;
    }
    view.updateMarkersTable();
    repaint();
  }

  public void removeAllCalipers() {
    calipers.clear();
    pending = null;
    dragged = null;
    lastEdited = null;
    view.updateMarkersTable();
    repaint();
  }

  /** Replaces the calipers of this lead by complete ones and measures them. */
  void setCalipers(List<Caliper> list) {
    calipers.clear();
    pending = null;
    dragged = null;
    lastEdited = null;
    for (Caliper c : list) {
      if (!c.isEmpty()) {
        calipers.add(c);
      }
    }
    remeasure();
    repaint();
  }

  /** Measures a caliper again (after a filter change for instance). */
  public void remeasure() {
    for (Caliper c : calipers) {
      measure(c);
    }
  }

  /**
   * Fills the measured values of a caliper: for a time caliper the duration, the difference and the
   * peak-to-peak amplitude of the signal between the instants, and the QRS complexes found between
   * them (with their mean R-R when there are at least two); for an amplitude caliper the difference
   * of the levels.
   */
  private void measure(Caliper c) {
    if (!c.isTime()) {
      c.setResults(null, c.getLevel(1) - c.getLevel(0), null, 0, null);
      return;
    }
    int fs = view.getSamplesPerSecond();
    int start = c.getStart();
    int stop = c.getStop();
    int count = stop - start + 1;
    double duration = (stop - start) / (double) fs;
    double delta;
    double peakToPeak;
    int beats = 0;
    Double meanRR = null;
    if (count <= MAX_FILTERED_SAMPLES) {
      double[] values = signal(start - sampleOffset, count);
      double min = Double.MAX_VALUE;
      double max = -Double.MAX_VALUE;
      for (double v : values) {
        min = Math.min(min, v);
        max = Math.max(max, v);
      }
      peakToPeak = max - min;
      delta = values[count - 1] - values[0];
      c.setExtent(min, max);
      if (count <= 60 * fs && count >= fs / 4 && channels.isVoltage()) {
        int margin = fs;
        double[] segment = signal(start - sampleOffset - margin, count + 2 * margin);
        new SignalFilter(true, 40, 0).apply(segment, segment.length, fs);
        List<Integer> peaks = HeartRate.peaks(segment, fs);
        int firstBeat = -1;
        int lastBeat = -1;
        for (int peak : peaks) {
          if (peak >= margin && peak < margin + count) {
            beats++;
            if (firstBeat < 0) {
              firstBeat = peak;
            }
            lastBeat = peak;
          }
        }
        if (beats >= 2) {
          meanRR = (lastBeat - firstBeat) / (double) (beats - 1) / fs;
        }
      }
    } else {
      int[] minMax = new int[2];
      data.rawMinMax(start, count, channels, minMax);
      peakToPeak =
          Math.abs(
              channels.toDisplay(
                  (minMax[1] - minMax[0]) * channels.getAmplitudeUnitScalingFactor()));
      delta = sampleValue(stop) - sampleValue(start);
      c.setExtent(null, null);
    }
    c.setResults(duration, delta, peakToPeak, beats, meanRR);
  }

  /**
   * The caliper under a point: by its handles (within {@link #HANDLE_RADIUS} pixels of a line) and,
   * when {@code inside} is set, by the band of a time caliper. {@code handle[0]} receives the
   * handle hit, or the nearer one for a band hit.
   */
  private Caliper caliperAt(Point p, int[] handle, boolean inside) {
    for (int i = calipers.size() - 1; i >= 0; i--) {
      Caliper c = calipers.get(i);
      if (c.isTime()) {
        double x0 = xOf(c.getPoint(0) - sampleOffset);
        double x1 = xOf(c.getPoint(1) - sampleOffset);
        if (Math.abs(p.getX() - x0) <= HANDLE_RADIUS) {
          handle[0] = 0;
          return c;
        }
        if (Math.abs(p.getX() - x1) <= HANDLE_RADIUS) {
          handle[0] = 1;
          return c;
        }
        if (inside && p.getX() > Math.min(x0, x1) && p.getX() < Math.max(x0, x1)) {
          handle[0] = Math.abs(p.getX() - x0) < Math.abs(p.getX() - x1) ? 0 : 1;
          return c;
        }
      } else {
        double y0 = yOf(c.getLevel(0));
        double y1 = yOf(c.getLevel(1));
        if (Math.abs(p.getY() - y0) <= HANDLE_RADIUS) {
          handle[0] = 0;
          return c;
        }
        if (Math.abs(p.getY() - y1) <= HANDLE_RADIUS) {
          handle[0] = 1;
          return c;
        }
      }
    }
    return null;
  }

  /** The label choices and the deletion of a caliper. */
  private void showCaliperMenu(Caliper c, Point p) {
    JPopupMenu menu = new JPopupMenu();
    ButtonGroup group = new ButtonGroup();
    JRadioButtonMenuItem none =
        new JRadioButtonMenuItem(Messages.common("none"), c.getLabel() == null);
    none.addActionListener(e -> relabel(c, null));
    group.add(none);
    menu.add(none);
    for (String label : Caliper.LABELS) {
      JRadioButtonMenuItem item = new JRadioButtonMenuItem(label, label.equals(c.getLabel()));
      item.addActionListener(e -> relabel(c, label));
      group.add(item);
      menu.add(item);
    }
    menu.addSeparator();
    JMenuItem delete = new JMenuItem(Messages.getString("LeadPanel.delete_caliper"));
    delete.addActionListener(e -> removeCaliper(c));
    menu.add(delete);
    menu.show(this, p.x, p.y);
  }

  private void relabel(Caliper c, String label) {
    c.setLabel(label);
    view.updateMarkersTable();
    repaint();
  }

  private void repaintCalipers() {
    repaint();
  }

  // ---- painting -----------------------------------------------------------------------------

  @Override
  public void paintComponent(Graphics g) {
    final Graphics2D g2d = (Graphics2D) g;
    Paint oldColor = g2d.getPaint();
    Stroke oldStroke = g2d.getStroke();
    Object[] oldRenderingHints = GuiUtils.setRenderingHints(g, true, true, true);

    Dimension dim = getSize();
    Rectangle bounds = new Rectangle(0, 0, dim.width, dim.height);
    Rectangle clipBounds = g2d.getClipBounds();
    Rectangle clip = clipBounds == null ? bounds : clipBounds.intersection(bounds);
    updateScale();

    boolean overlay = isOverlay();
    if (!overlay) {
      g2d.setBackground(this.getBackground());
      g2d.clearRect(clip.x, clip.y, clip.width, clip.height);
    }

    if (!clip.isEmpty()) {
      if (!overlay) {
        drawSelectedBackground(g2d, dim);
        drawGrid(g2d, dim, clip);
        drawCalibrationPulse(g2d, dim);
      }
      drawWaveData(g2d, dim, clip);
      drawLeadTitle(g2d);
      if (!overlay) {
        drawAnnotations(g2d, dim);
        drawSignalMarkers(g2d, dim);
      }
    }

    GuiUtils.resetRenderingHints(g, oldRenderingHints);
    g2d.setPaint(oldColor);
    g2d.setStroke(oldStroke);
  }

  /**
   * Draws the millimetre grid of the clip: every line is appended to one of two paths (thin and
   * thick) so that a repaint costs two draw calls whatever the width of the lead.
   */
  private void drawGrid(Graphics2D g2, Dimension dim, Rectangle clip) {
    double pixelPerMm = view.getPixelPerMm();
    if (pixelPerMm <= 0) {
      return;
    }
    g2.setColor(GRID_COLOR);

    Path2D thin = new Path2D.Float();
    Path2D thick = new Path2D.Float();
    double x0 = clip.x;
    double x1 = clip.x + clip.width;
    double y0 = clip.y;
    double y1 = clip.y + clip.height;

    int lastRow = (int) Math.min(dim.height / pixelPerMm, Math.ceil(y1 / pixelPerMm));
    for (int i = (int) Math.floor(y0 / pixelPerMm); i <= lastRow; i++) {
      double y = i * pixelPerMm;
      Path2D path = i % 5 == 0 ? thick : thin;
      path.moveTo(x0, y);
      path.lineTo(x1, y);
    }
    int lastColumn = (int) Math.min(dim.width / pixelPerMm, Math.ceil(x1 / pixelPerMm));
    for (int i = (int) Math.floor(x0 / pixelPerMm); i <= lastColumn; i++) {
      double x = i * pixelPerMm;
      Path2D path = i % 5 == 0 ? thick : thin;
      path.moveTo(x, y0);
      path.lineTo(x, y1);
    }
    g2.setStroke(THIN_STROKE);
    g2.draw(thin);
    g2.setStroke(THICK_STROKE);
    g2.draw(thick);

    g2.setStroke(BORDER_STROKE);
    g2.draw(new Rectangle2D.Double(0.7, 0.0, dim.width - 1.7, dim.height - 1.0));
  }

  /**
   * The 1 mV (one cell) by 200 ms square pulse in the gutter left of the trace: its width follows
   * the time scale of the lead (5 mm at 25 mm/s) and it ends where the trace starts.
   */
  private void drawCalibrationPulse(Graphics2D g2, Dimension dim) {
    if (gutter <= 0) {
      return;
    }
    double[] x =
        pulseEdges(
            gutter, view.getPixelsPerSecond(), WaveView.PULSE_MARGIN_MM * view.getPixelPerMm());
    double cellHeight = dim.getHeight() / mvCellCount;
    double base = dim.height / 2.0;
    Path2D pulse = new Path2D.Double();
    pulse.moveTo(x[0], base);
    pulse.lineTo(x[1], base);
    pulse.lineTo(x[1], base - cellHeight);
    pulse.lineTo(x[2], base - cellHeight);
    pulse.lineTo(x[2], base);
    pulse.lineTo(x[3], base);
    g2.setColor(traceColor());
    g2.setStroke(SIGNAL_STROKE);
    g2.draw(pulse);
  }

  /**
   * The x of the calibration pulse in a gutter: start of the baseline, rising edge, falling edge,
   * end of the baseline. The edges are 200 ms apart at the given time scale and the pulse is set
   * against the trace, a margin before the end of the gutter; in a gutter too narrow for it the
   * rising edge stops at the margin.
   */
  static double[] pulseEdges(double gutter, double pixelsPerSecond, double margin) {
    double fall = Math.max(margin, gutter - margin);
    double rise = Math.max(Math.min(margin, fall), fall - WaveView.PULSE_SECONDS * pixelsPerSecond);
    return new double[] {Math.max(0, rise - margin), rise, fall, gutter};
  }

  /**
   * Draws the samples of the clip. With more than two samples per pixel the signal is drawn as the
   * min / max envelope of the pixel columns (see {@link #drawEnvelope}), otherwise the samples are
   * joined. The neighbouring range is prefetched so that scrolling does not wait for the decoder.
   */
  private void drawWaveData(Graphics2D g2, Dimension dim, Rectangle clip) {
    if (sampleNumber <= 0 || ratioX <= 0) {
      return;
    }
    double cellHeight = dim.getHeight() / this.mvCellCount;
    double pixelsPerUnit = cellHeight / channels.getCellValue();
    double base = dim.height / 2.0;

    int clipX = Math.max(clip.x, gutter);
    int clipEnd = clip.x + clip.width;
    if (clipEnd <= clipX) {
      return;
    }
    int first = Math.max(0, (int) Math.floor(sampleAt(clipX - LINE_MARGIN)));
    int last = Math.min(sampleNumber - 1, (int) Math.ceil(sampleAt(clipEnd + LINE_MARGIN)));
    if (last < first) {
      return;
    }
    int visible = last - first + 1;
    data.prefetch(sampleOffset + first - visible, 3 * visible);

    g2.setColor(traceColor());
    if (ratioX < 0.5) {
      drawEnvelope(g2, dim, clipX, clipEnd, first, last, pixelsPerUnit, base);
    } else {
      double[] values = signal(first, visible);
      Path2D path = new Path2D.Float(Path2D.WIND_NON_ZERO, visible + 2);
      path.moveTo(xOf(first), base - values[0] * pixelsPerUnit);
      for (int i = 1; i < visible; i++) {
        path.lineTo(xOf(first + i), base - values[i] * pixelsPerUnit);
      }
      g2.setStroke(SIGNAL_STROKE);
      g2.draw(path);
    }
  }

  /**
   * Draws the signal as the band between the minimum and the maximum of each pixel column, filled
   * as one polygon: far cheaper to rasterize than a stroked zigzag of tall segments. Each column
   * also covers the last sample of the previous one so that the band never breaks on a steep slope.
   * The extrema come from the filtered samples when the display filters are on and the range is
   * short enough, from the min / max summary when it covers the range (and the columns are a few
   * blocks long), from the samples otherwise.
   */
  private void drawEnvelope(
      Graphics2D g2,
      Dimension dim,
      int clipX,
      int clipEnd,
      int first,
      int last,
      double pixelsPerUnit,
      double base) {
    int visible = last - first + 1;
    int perPixel = (int) (1 / ratioX);
    boolean filtered = !view.getFilter().isIdentity() && visible <= MAX_FILTERED_SAMPLES;
    double[] values = null;
    int[] raw = null;
    boolean summary = false;
    if (filtered) {
      values = signal(first, visible);
    } else {
      int block = data.summaryBlock(sampleOffset + first, visible);
      summary = block > 0 && perPixel >= 4 * block;
      if (!summary && visible <= MAX_BUFFERED_SAMPLES) {
        raw = rawBuffer(visible);
        data.readRaw(sampleOffset + first, visible, channels, raw);
      }
    }
    int startColumn = Math.max(gutter, clipX - 1);
    int endColumn = Math.min(clipEnd + 1, dim.width);
    int columns = Math.max(0, endColumn - startColumn);
    double[] top = new double[columns];
    double[] bottom = new double[columns];
    int n = 0;
    double half = ((BasicStroke) SIGNAL_STROKE).getLineWidth() / 2;
    for (int px = startColumn; px < endColumn; px++) {
      int s0 = Math.max(first, (int) Math.floor(sampleAt(px)));
      int s1 = Math.min(last, (int) Math.floor(sampleAt(px + 1)) - 1);
      if (s1 < s0) {
        continue;
      }
      int from = Math.max(first, s0 - 1); // continuity with the previous column
      double lo;
      double hi;
      if (values != null) {
        lo = Double.MAX_VALUE;
        hi = -Double.MAX_VALUE;
        for (int i = from - first; i <= s1 - first; i++) {
          double v = values[i];
          lo = Math.min(lo, v);
          hi = Math.max(hi, v);
        }
      } else {
        if (summary) {
          data.summaryMinMax(sampleOffset + from, s1 - from + 1, channels, range);
        } else if (raw != null) {
          minMax(raw, from - first, s1 - first, range);
        } else {
          data.rawMinMax(sampleOffset + from, s1 - from + 1, channels, range);
        }
        double a = display(range[0]);
        double b = display(range[1]);
        lo = Math.min(a, b);
        hi = Math.max(a, b);
      }
      top[n] = base - hi * pixelsPerUnit - half;
      bottom[n] = base - lo * pixelsPerUnit + half;
      n++;
    }
    if (n == 0) {
      return;
    }
    Path2D path = new Path2D.Float(Path2D.WIND_NON_ZERO, 2 * n + 2);
    double x = startColumn + 0.5;
    path.moveTo(x, top[0]);
    for (int i = 1; i < n; i++) {
      path.lineTo(x + i, top[i]);
    }
    for (int i = n - 1; i >= 0; i--) {
      path.lineTo(x + i, bottom[i]);
    }
    path.closePath();
    g2.fill(path);
  }

  private static void minMax(int[] values, int from, int to, int[] out) {
    int min = Integer.MAX_VALUE;
    int max = Integer.MIN_VALUE;
    for (int i = from; i <= to; i++) {
      int v = values[i];
      if (v < min) {
        min = v;
      }
      if (v > max) {
        max = v;
      }
    }
    out[0] = min;
    out[1] = max;
  }

  /** The bands of the time calipers, under the grid. */
  private void drawSelectedBackground(Graphics2D g2, Dimension dim) {
    Color background = IconColor.ACTIONS_YELLOW.getColor().brighter();
    for (Caliper c : calipers) {
      if (!c.isTime() || c.isEmpty()) {
        continue;
      }
      double startX = xOf(c.getStart() - sampleOffset);
      double stopX = xOf(c.getStop() - sampleOffset);
      g2.setColor(
          c == pending
              ? new Color(background.getRed(), background.getGreen(), background.getBlue(), 90)
              : background);
      g2.fill(new Rectangle2D.Double(startX, 0, stopX - startX, dim.height));
    }
  }

  /** The temporal annotations: dashed lines for points, shaded bands for segments, labelled. */
  private void drawAnnotations(Graphics2D g2, Dimension dim) {
    if (annotations.isEmpty()) {
      return;
    }
    g2.setFont(fontLabel);
    FontMetrics metrics = g2.getFontMetrics();
    int row = 0;
    java.util.Set<String> written = new java.util.HashSet<>();
    for (LeadAnnotation annotation : annotations) {
      int start = annotation.start() - sampleOffset;
      int end = annotation.end() - sampleOffset;
      if (end < 0 || start >= sampleNumber) {
        continue;
      }
      Color color = annotation.external() ? EXTERNAL_ANNOTATION_COLOR : ANNOTATION_COLOR;
      g2.setColor(color);
      g2.setStroke(ANNOTATION_STROKE);
      double x0 = xOf(Math.max(0, start));
      if (annotation.isPoint()) {
        g2.draw(new Line2D.Double(x0, 0, x0, dim.height));
      } else {
        double x1 = xOf(Math.min(sampleNumber - 1, end));
        Composite composite = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.12f));
        g2.fill(new Rectangle2D.Double(x0, 0, Math.max(1, x1 - x0), dim.height));
        g2.setComposite(composite);
        g2.draw(new Line2D.Double(x0, 0, x0, dim.height));
        g2.draw(new Line2D.Double(x1, 0, x1, dim.height));
      }
      // One label per distinct text: a series of points (every beat) is labelled once
      String label = annotation.label();
      if (label != null && !label.isEmpty() && written.add(label)) {
        int y = dim.height - 4 - (row % 3) * (metrics.getHeight() + 1);
        g2.drawString(label, (float) x0 + 3, y);
        row++;
      }
    }
  }

  /** The cursor, the ghosts of the other leads' time calipers, and this lead's calipers. */
  private void drawSignalMarkers(Graphics2D g2, Dimension dim) {
    Color color = IconColor.ACTIONS_YELLOW.getColor().darker();
    g2.setColor(color);
    g2.setStroke(MARKER_STROKE);
    drawMarker(g2, selectedPosition - sampleOffset, dim);

    if (view.isCalipersOnAllLeads()) {
      g2.setColor(CALIPER_GHOST);
      g2.setStroke(GHOST_STROKE);
      for (Caliper c : view.getTimeCalipersOfOtherLeads(this)) {
        drawMarker(g2, c.getStart() - sampleOffset, dim);
        drawMarker(g2, c.getStop() - sampleOffset, dim);
      }
    }

    g2.setFont(fontLabel);
    FontMetrics metrics = g2.getFontMetrics();
    List<Rectangle> placed = new ArrayList<>();
    for (Caliper c : calipers) {
      if (c.isTime()) {
        g2.setColor(color);
        g2.setStroke(c == pending ? ANNOTATION_STROKE : MARKER_STROKE);
        drawMarker(g2, c.getPoint(0) - sampleOffset, dim);
        drawMarker(g2, c.getPoint(1) - sampleOffset, dim);
        if (!c.isEmpty()) {
          double x0 = xOf(c.getStart() - sampleOffset);
          double x1 = xOf(c.getStop() - sampleOffset);
          // Free space: above the signal of the band when there is more room there, below else
          double signalTop = c.getMaxValue() == null ? dim.height / 2.0 : yOf(c.getMaxValue());
          double signalBottom = c.getMinValue() == null ? dim.height / 2.0 : yOf(c.getMinValue());
          Rectangle box =
              drawReadout(
                  g2,
                  metrics,
                  caliperLines(c),
                  color,
                  x0,
                  x1,
                  signalTop,
                  signalBottom,
                  dim,
                  placed);
          double bandX = Math.min(Math.max(x0, box.x), x1);
          drawLeader(g2, color, bandX, box.y < signalTop ? signalTop : signalBottom, box);
        }
      } else {
        drawAmplitudeCaliper(g2, metrics, c, dim, placed);
      }
    }
  }

  /**
   * An amplitude caliper: two blue levels across the lead, the range shaded around its anchor with
   * a bracket, and the readout beside the bracket, between the levels.
   */
  private void drawAmplitudeCaliper(
      Graphics2D g2, FontMetrics metrics, Caliper c, Dimension dim, List<Rectangle> placed) {
    double y0 = yOf(c.getLevel(0));
    double y1 = yOf(c.getLevel(1));
    double top = Math.min(y0, y1);
    double bottom = Math.max(y0, y1);
    double anchor = xOf(c.getAnchor() - sampleOffset);
    double left = Math.max(gutter, anchor - AMPLITUDE_HALF_WIDTH);
    double right = Math.min(dim.width, anchor + AMPLITUDE_HALF_WIDTH);
    if (!c.isEmpty()) {
      g2.setColor(AMPLITUDE_FILL);
      g2.fill(new Rectangle2D.Double(left, top, right - left, bottom - top));
    }
    g2.setColor(AMPLITUDE_COLOR);
    g2.setStroke(c == pending ? ANNOTATION_STROKE : MARKER_STROKE);
    g2.draw(new Line2D.Double(0, y0, dim.width, y0));
    g2.draw(new Line2D.Double(0, y1, dim.width, y1));
    if (!c.isEmpty()) {
      // the bracket: a vertical bar at the anchor with ticks at both levels
      g2.setStroke(SIGNAL_STROKE);
      g2.draw(new Line2D.Double(anchor, top, anchor, bottom));
      g2.draw(new Line2D.Double(anchor - 4, top, anchor + 4, top));
      g2.draw(new Line2D.Double(anchor - 4, bottom, anchor + 4, bottom));
      double middle = (top + bottom) / 2;
      Rectangle box =
          drawReadout(
              g2,
              metrics,
              caliperLines(c),
              AMPLITUDE_COLOR,
              right + 6,
              right + 6,
              top,
              bottom,
              dim,
              placed);
      drawLeader(g2, AMPLITUDE_COLOR, anchor, middle, box);
    }
  }

  /**
   * A dashed line from a point of a caliper to the nearest edge of its readout, drawn when the box
   * does not touch the point.
   */
  private void drawLeader(Graphics2D g2, Color color, double x, double y, Rectangle box) {
    double targetX = Math.max(box.x, Math.min(x, box.x + box.width));
    double targetY = Math.max(box.y, Math.min(y, box.y + box.height));
    if (Math.abs(targetX - x) < 4 && Math.abs(targetY - y) < 4) {
      return;
    }
    g2.setColor(color);
    g2.setStroke(GHOST_STROKE);
    g2.draw(new Line2D.Double(x, y, targetX, targetY));
    g2.fill(new java.awt.geom.Ellipse2D.Double(x - 2, y - 2, 4, 4));
  }

  /** The text of a caliper: its label, then its measures. */
  private List<String> caliperLines(Caliper c) {
    List<String> lines = new ArrayList<>();
    String prefix = c.getLabel() == null ? "" : c.getLabel() + ": ";
    String unit = channels.getDisplayUnit();
    if (c.isTime()) {
      StringBuilder first = new StringBuilder(prefix);
      first.append(WaveFormats.millis(c.getDuration()));
      if (c.getBeats() >= 2) {
        first
            .append("   ")
            .append(c.getBeats())
            .append(" QRS, R-R ") // NON-NLS
            .append(WaveFormats.millis(c.getMeanRR()))
            .append(' ')
            .append(WaveFormats.bpm(c.getMeanRate()));
      } else {
        first.append("   ").append(WaveFormats.bpm(c.getRate()));
      }
      lines.add(first.toString());
      lines.add(
          "\u0394 "
              + WaveFormats.value(c.getDelta(), unit)
              + "   pp " // NON-NLS
              + WaveFormats.value(c.getPeakToPeak(), unit));
    } else {
      lines.add(prefix + "\u0394 " + WaveFormats.value(Math.abs(c.getDelta()), unit));
      lines.add(
          WaveFormats.VALUE.format(Math.min(c.getLevel(0), c.getLevel(1)))
              + " \u2192 " // NON-NLS
              + WaveFormats.value(Math.max(c.getLevel(0), c.getLevel(1)), unit));
    }
    return lines;
  }

  /**
   * A small bordered box with text lines, next to a caliper. The box goes below {@code
   * signalBottom} when there is room, above {@code signalTop} otherwise, starting at {@code x0} (or
   * ending at {@code x1} when the lead ends), and is moved down past the boxes already placed so
   * that readouts never overlap. The box joins {@code placed} and is returned.
   */
  private Rectangle drawReadout(
      Graphics2D g2,
      FontMetrics metrics,
      List<String> lines,
      Color border,
      double x0,
      double x1,
      double signalTop,
      double signalBottom,
      Dimension dim,
      List<Rectangle> placed) {
    int width = 6;
    for (String line : lines) {
      width = Math.max(width, metrics.stringWidth(line) + 8);
    }
    int height = lines.size() * metrics.getHeight() + 4;
    int titleZone = 18;
    int left = (int) Math.round(x0 + 3);
    if (left + width > dim.width) {
      left = (int) Math.max(0, Math.min(x1, dim.width) - width - 3);
    }
    double roomBelow = dim.height - signalBottom;
    double roomAbove = signalTop - titleZone;
    int top;
    if (roomBelow >= height + 4 || roomBelow >= roomAbove) {
      top = (int) Math.min(signalBottom + 3, dim.height - height - 1);
    } else {
      top = (int) Math.max(titleZone, signalTop - height - 3);
    }
    Rectangle box = new Rectangle(left, top, width, height);
    for (int guard = 0; guard < placed.size() * 2 + 1; guard++) {
      Rectangle hit = null;
      for (Rectangle r : placed) {
        if (r.intersects(box)) {
          hit = r;
          break;
        }
      }
      if (hit == null) {
        break;
      }
      int below = hit.y + hit.height + 2;
      box.y = below + height <= dim.height ? below : Math.max(0, hit.y - height - 2);
    }
    placed.add(box);

    g2.setColor(new Color(255, 255, 255, 215));
    g2.fillRoundRect(box.x, box.y, box.width, box.height, 6, 6);
    g2.setColor(border);
    g2.setStroke(MARKER_STROKE);
    g2.drawRoundRect(box.x, box.y, box.width, box.height, 6, 6);
    g2.setColor(Color.DARK_GRAY);
    int baseline = box.y + metrics.getAscent() + 2;
    for (String line : lines) {
      g2.drawString(line, box.x + 4, baseline);
      baseline += metrics.getHeight();
    }
    return box;
  }

  private void drawMarker(Graphics2D g2, int position, Dimension dim) {
    if (position < 0 || position >= sampleNumber) {
      return;
    }
    double x = xOf(position);
    Line2D line = new Line2D.Double(x, 0, x, dim.height);
    g2.draw(line);
  }

  private void drawLeadTitle(Graphics2D g2) {
    g2.setFont(fontTitle);
    g2.setColor(traceColor());
    if (overlayIndex < 0) {
      g2.drawString(getPaintedTitle(), 5, 15);
    } else {
      // Superimposed: the names in their colours, side by side
      FontMetrics metrics = g2.getFontMetrics();
      int x = 5;
      for (int i = 0; i < overlayIndex; i++) {
        x += metrics.stringWidth(view.getOverlayTitle(i)) + 10;
      }
      g2.drawString(getDisplayTitle(), x, 15);
    }
  }
}
