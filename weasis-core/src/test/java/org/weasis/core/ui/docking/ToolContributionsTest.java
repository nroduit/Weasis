/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.docking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.editor.ViewContext;
import org.weasis.core.ui.editor.ViewContexts;

class ToolContributionsTest {

  /** A tool that records what the contributions do to it. */
  private static final class RecordingTool implements DockableTool {
    final String id;
    final List<String> events = new ArrayList<>();
    final JPanel component = new JPanel();

    RecordingTool(String id) {
      this.id = id;
    }

    @Override
    public String getToolId() {
      return id;
    }

    @Override
    public void showDockable() {
      events.add("show");
    }

    @Override
    public void closeDockable() {
      events.add("close");
    }

    @Override
    public Component getToolComponent() {
      return component;
    }

    @Override
    public int getDockableWidth() {
      return 200;
    }

    @Override
    public String getComponentName() {
      return id;
    }

    @Override
    public int getComponentPosition() {
      return 0;
    }

    @Override
    public void setComponentPosition(int position) {}

    @Override
    public boolean isComponentEnabled() {
      return true;
    }

    @Override
    public void setComponentEnabled(boolean enabled) {}

    @Override
    public Type getPanelType() {
      return Type.TOOL_EXT;
    }
  }

  /** Relevant when the context carries the fact it watches. */
  private static final class FactContribution implements ToolContribution {
    final RecordingTool tool;
    final String fact;
    int created;

    FactContribution(String id, String fact) {
      this.tool = new RecordingTool(id);
      this.fact = fact;
    }

    @Override
    public String toolId() {
      return tool.id;
    }

    @Override
    public boolean appliesTo(ViewContext context) {
      return true;
    }

    @Override
    public boolean isRelevant(ViewContext context) {
      return context.fact(fact).isPresent();
    }

    @Override
    public DockableTool tool(ViewContext context) {
      created++;
      return tool;
    }
  }

  private static ViewContext context(Map<String, Object> facts) {
    return new ViewContext(null, "2d", null, facts);
  }

  @Test
  void aRelevantContributionShowsItsToolAndAnIrrelevantOneHidesItOnlyWhenItWasShown() {
    FactContribution seg = new FactContribution("segmentation", "seg.objects");
    FactContribution rt = new FactContribution("rt", "rt.objects");
    List<ToolContribution> all = List.of(seg, rt);

    assertEquals(
        List.of("segmentation"), ToolContributions.apply(all, context(Map.of("seg.objects", 2))));
    assertEquals(List.of("show"), seg.tool.events);
    assertEquals(List.of(), rt.tool.events, "never shown, so not closed");

    assertEquals(List.of("rt"), ToolContributions.apply(all, context(Map.of("rt.objects", 1))));
    assertEquals(List.of("show", "close"), seg.tool.events);
    assertEquals(List.of("show"), rt.tool.events);

    assertEquals(List.of(), ToolContributions.apply(all, ViewContext.empty()));
    assertEquals(List.of("show", "close"), rt.tool.events);
  }

  @Test
  void theContextCarriesTheFactsAndThe4xKeysAndTheContributionsFollowTheCurrentOne() {
    ViewContexts.setContributors(() -> List.of((viewer, series, facts) -> facts.put("x", 1)));
    ViewContext empty = ViewContexts.of(null, null);
    assertTrue(empty.isEmpty());
    assertEquals(Optional.empty(), empty.fact("x"));
    ViewContext withFacts = context(Map.of("hp.step", 2, "hp.protocol", "weasis.ct"));
    assertEquals(Optional.of(2), withFacts.fact("hp.step", Integer.class));
    assertEquals(Optional.empty(), withFacts.fact("hp.step", String.class));
    assertFalse(withFacts.properties().containsKey(ViewContext.SERIES_KEY), "no series");
    assertEquals("weasis.ct", withFacts.toJson().getJsonObject("facts").getString("hp.protocol"));

    FactContribution chart = new FactContribution("chart", "hp.protocol");
    ToolContributions.setExtensions(() -> List.of(chart));
    ToolContributions.follow();
    ViewContexts.publish(withFacts);
    assertEquals(List.of("show"), chart.tool.events);
    assertEquals(withFacts, ViewContexts.current());
    ViewContexts.publish(ViewContext.empty());
    assertEquals(List.of("show", "close"), chart.tool.events);
    assertTrue(ViewContexts.current().isEmpty());
  }
}
