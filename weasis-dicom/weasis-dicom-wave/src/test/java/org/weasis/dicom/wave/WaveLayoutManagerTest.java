/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Lays 12 leads and their rhythm strips out in every format on a 1600 x 900 viewport. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class WaveLayoutManagerTest {

  private static final int VIEWPORT_WIDTH = 1600;
  private static final int VIEWPORT_HEIGHT = 900;
  private static final int GUTTER = 20;

  private WaveView view;
  private WaveLayoutManager manager;
  private JPanel pane;
  private final List<LeadPanel> leads = new ArrayList<>();

  private void build(Format format) {
    view = Mockito.mock(WaveView.class);
    Mockito.when(view.getCurrentFormat()).thenReturn(format);
    Mockito.when(view.getSeconds()).thenReturn(10.0);
    Mockito.when(view.getSamplesPerSecond()).thenReturn(500);
    Mockito.when(view.getMvCells()).thenReturn(2);
    Mockito.when(view.getZoomRatio()).thenReturn(1.0);
    Mockito.when(view.getChannelNumber()).thenReturn(12);
    Mockito.when(view.getGutterWidth()).thenReturn(GUTTER);
    Mockito.when(view.getFilter()).thenReturn(SignalFilter.NONE);
    manager =
        new WaveLayoutManager(view, WaveLayoutManager.AUTO_SPEED, WaveLayoutManager.AUTO_AMPLITUDE);
    pane = new JPanel(manager);
    JPanel wrap = new JPanel();
    JPanel viewport = new JPanel();
    viewport.setSize(VIEWPORT_WIDTH, VIEWPORT_HEIGHT);
    viewport.add(wrap);
    wrap.add(pane);
    WaveDataReadable data = Mockito.mock(WaveDataReadable.class);
    Mockito.when(data.getNbSamplesPerChannel()).thenReturn(5000);
    leads.clear();
    for (Lead lead : Lead.DEFAULT_12LEAD) {
      ChannelDefinition channel = Mockito.mock(ChannelDefinition.class);
      Mockito.when(channel.getLead()).thenReturn(lead);
      Mockito.when(channel.getTitle()).thenReturn(lead.toString());
      Mockito.when(channel.getDisplayUnit()).thenReturn("mV");
      LeadPanel panel = new LeadPanel(view, data, channel);
      pane.add(lead.toString(), panel);
      leads.add(panel);
      Lead rhythm = Lead.rhythmOf(lead);
      if (rhythm != null) {
        ChannelDefinition strip = Mockito.mock(ChannelDefinition.class);
        Mockito.when(strip.getLead()).thenReturn(rhythm);
        Mockito.when(strip.getTitle()).thenReturn(rhythm.toString());
        Mockito.when(strip.getDisplayUnit()).thenReturn("mV");
        LeadPanel panel2 = new LeadPanel(view, data, strip);
        pane.add(rhythm.toString(), panel2);
        leads.add(panel2);
      }
    }
    Dimension preferred = manager.preferredLayoutSize(pane);
    pane.setSize(preferred);
    manager.layoutContainer(pane);
  }

  private List<LeadPanel> visible(boolean rhythm) {
    List<LeadPanel> list = new ArrayList<>();
    for (LeadPanel p : leads) {
      if (p.isVisible() && p.getChannels().getLead().isRhythm() == rhythm) {
        list.add(p);
      }
    }
    return list;
  }

  @Test
  void three_by_four_with_three_rhythm_strips_takes_six_rows() {
    build(Format.FOUR_RHYTHM3);
    List<LeadPanel> columns = visible(false);
    List<LeadPanel> strips = visible(true);
    assertEquals(12, columns.size());
    assertEquals(3, strips.size());
    int rowHeight = columns.get(0).getHeight();
    assertEquals(6 * rowHeight, pane.getHeight());
    // four columns of 2.5 s, each with its gutter, over the pane width
    int columnWidth = columns.get(0).getWidth();
    assertEquals(4 * columnWidth, pane.getWidth(), 4); // integer division of the width
    double pps = manager.getPixelsPerSecond();
    assertEquals(GUTTER + 2.5 * pps, columnWidth, 1.5);
    // the strips sit under the three rows, span the row and start at the left
    for (int i = 0; i < 3; i++) {
      Rectangle r = strips.get(i).getBounds();
      assertEquals(0, r.x);
      assertEquals((3 + i) * rowHeight, r.y);
      assertEquals(GUTTER + Math.round(10 * pps), r.width, 1.5);
    }
    // the time scale is the same in a column and in a strip: 10 s of strip = 4 columns of 2.5 s
    assertEquals(4 * (columnWidth - GUTTER), strips.get(0).getWidth() - GUTTER, 4);
    // the leads sit in reading order: aVR heads the second column (standard sequence)
    LeadPanel avr =
        columns.stream().filter(p -> Lead.AVR.equals(p.getChannels().getLead())).findFirst().get();
    assertEquals(columnWidth, avr.getX());
    assertEquals(0, avr.getY());
  }

  @Test
  void six_by_two_with_rhythm_takes_seven_rows() {
    build(Format.TWO_RHYTHM);
    List<LeadPanel> columns = visible(false);
    List<LeadPanel> strips = visible(true);
    assertEquals(12, columns.size());
    assertEquals(1, strips.size());
    int rowHeight = columns.get(0).getHeight();
    assertEquals(7 * rowHeight, pane.getHeight());
    assertEquals(2 * columns.get(0).getWidth(), pane.getWidth(), 2);
    assertEquals(6 * rowHeight, strips.get(0).getY());
    assertEquals(Lead.RHYTHM, strips.get(0).getChannels().getLead());
  }

  @Test
  void default_format_stacks_every_lead_and_hides_the_strips() {
    build(Format.DEFAULT);
    assertEquals(12, visible(false).size());
    assertTrue(visible(true).isEmpty());
    assertEquals(12 * visible(false).get(0).getHeight(), pane.getHeight());
    assertEquals(pane.getWidth(), visible(false).get(0).getWidth());
  }

  @Test
  void superimposed_leads_share_the_whole_area() {
    build(Format.SUPERIMPOSED);
    List<LeadPanel> columns = visible(false);
    assertEquals(12, columns.size());
    assertTrue(visible(true).isEmpty());
    Rectangle bounds = columns.get(0).getBounds();
    for (LeadPanel p : columns) {
      assertEquals(bounds, p.getBounds());
    }
    assertEquals(pane.getWidth(), bounds.width);
    assertEquals(pane.getHeight(), bounds.height);
    // exactly one of them paints the background, the others are transparent overlays
    int opaque = 0;
    for (LeadPanel p : columns) {
      if (p.isOpaque()) {
        opaque++;
      }
    }
    assertEquals(1, opaque);
    assertFalse(pane.getWidth() > VIEWPORT_WIDTH, "fits the viewport");
  }
}
