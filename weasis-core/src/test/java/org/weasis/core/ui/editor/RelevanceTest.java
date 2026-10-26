/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.swing.JPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.setting.SettingEnvironment;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.ToolVisibility;
import org.weasis.core.ui.util.ToolBarContainer;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.core.ui.util.WtoolBar;

class RelevanceTest {

  /** A tool with a predicate that records what the applier does to it. */
  private static final class PredicateTool implements DockableTool {
    final String id;
    final Predicate<ViewContext> predicate;
    final List<String> events = new ArrayList<>();
    final JPanel component = new JPanel();
    boolean enabled = true;

    PredicateTool(String id, Predicate<ViewContext> predicate) {
      this.id = id;
      this.predicate = predicate;
    }

    @Override
    public boolean isRelevant(ViewContext context) {
      return predicate.test(context);
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
      return enabled;
    }

    @Override
    public void setComponentEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    @Override
    public Type getPanelType() {
      return Type.TOOL;
    }
  }

  /** A toolbar with a predicate. */
  private static final class PredicateBar extends WtoolBar {
    final Predicate<ViewContext> predicate;

    PredicateBar(String name, int position, Predicate<ViewContext> predicate) {
      super(name, position);
      this.predicate = predicate;
    }

    @Override
    public boolean isRelevant(ViewContext context) {
      return predicate.test(context);
    }
  }

  private static ViewContext context(Map<String, Object> facts) {
    return new ViewContext(null, "2d", null, facts);
  }

  @BeforeEach
  void installEmptyEnvironment() {
    Settings.install(SettingEnvironment.inMemory(new HashMap<>()));
    ToolVisibility.setSelectedViewer(java.util.Optional::empty);
  }

  @AfterEach
  void showAllAgain() {
    Settings.install(SettingEnvironment.inMemory(new HashMap<>()));
  }

  @Test
  void theContentPredicatesReadTheFacts() {
    ViewContext ct =
        context(
            Map.of(
                ContextFacts.VOLUME, true,
                ContextFacts.TEMPORAL, "STATIC",
                ContextFacts.CALIBRATED, true,
                ContextFacts.objects("SEG"), 2));
    assertTrue(Relevance.VOLUME.test(ct));
    assertFalse(Relevance.TEMPORAL.test(ct));
    assertFalse(Relevance.SUV.test(ct));
    assertFalse(Relevance.CALIBRATION.test(ct), "a calibrated image needs no calibration");
    assertFalse(Relevance.FUSION.test(ct));
    assertFalse(Relevance.PROTOCOL.test(ct));
    assertTrue(Relevance.objects("seg").test(ct), "modality names are matched upper case");
    assertFalse(Relevance.objects("KO").test(ct));

    ViewContext cine =
        context(
            Map.of(
                ContextFacts.TEMPORAL, "CINE",
                ContextFacts.SUV, true,
                ContextFacts.FUSION, true,
                ContextFacts.HANGING_PROTOCOL, "weasis.ct"));
    assertTrue(Relevance.TEMPORAL.test(cine));
    assertTrue(Relevance.SUV.test(cine));
    assertTrue(Relevance.FUSION.test(cine));
    assertTrue(Relevance.PROTOCOL.test(cine));
    assertTrue(Relevance.TEMPORAL.test(context(Map.of(ContextFacts.TEMPORAL, "DYNAMIC"))));

    ViewContext empty = ViewContext.empty();
    assertFalse(Relevance.VOLUME.test(empty));
    assertTrue(Relevance.CALIBRATION.test(empty), "no spacing known: calibration stays offered");
    assertTrue(Relevance.ALWAYS.test(empty));
  }

  @Test
  void anItemWithoutPredicateIsAlwaysRelevantAndTheSettingTurnsTheFilteringOff() {
    PredicateTool volume = new PredicateTool("mpr", Relevance.VOLUME);
    ViewContext radiograph = context(Map.of(ContextFacts.VOLUME, false));
    assertFalse(Relevance.isRelevant(volume, radiograph));
    assertTrue(Relevance.isRelevant(new Object(), radiograph), "not a Relevant item");
    assertTrue(Relevance.isRelevant(volume, null) == Relevance.VOLUME.test(ViewContext.empty()));
    assertTrue(
        Relevance.isRelevant(
            new PredicateTool(
                "failing",
                c -> {
                  throw new IllegalStateException("boom");
                }),
            radiograph),
        "a failing predicate hides nothing");

    assertTrue(Relevance.contextual());
    Settings.set(Relevance.CONTEXTUAL, false);
    assertFalse(Relevance.contextual());
    assertTrue(Relevance.isRelevant(volume, radiograph), "show all tools");
    assertTrue(Relevance.applies(Relevance.VOLUME, radiograph));
  }

  @Test
  void actionsAreRelevantUnlessDeclaredOtherwise() {
    Relevance.registerAction("test.cine", Relevance.TEMPORAL);
    ViewContext still = context(Map.of(ContextFacts.TEMPORAL, "STATIC"));
    assertFalse(Relevance.isActionRelevant("test.cine", still));
    assertTrue(Relevance.isActionRelevant("test.cine", context(Map.of(ContextFacts.TEMPORAL, "CINE"))));
    assertTrue(Relevance.isActionRelevant("test.unknown", still));
    assertTrue(Relevance.isActionRelevant(null, still));
    assertTrue(Relevance.actions().containsKey("test.cine"));
  }

  @Test
  void theToolsOfAViewerFollowTheirVisibilityAndTheirRelevance() {
    PredicateTool display = new PredicateTool("display", Relevance.ALWAYS);
    PredicateTool why = new PredicateTool("hp-why", Relevance.PROTOCOL);
    PredicateTool hidden = new PredicateTool("measure", Relevance.ALWAYS);
    assertTrue(ToolVisibility.setVisible(hidden, false), "the user hid it");
    hidden.events.clear();
    List<DockableTool> tools = List.of(display, why, hidden);

    assertEquals(List.of("display"), Relevance.applyTools(tools, context(Map.of())));
    assertEquals(List.of("show"), display.events);
    assertEquals(List.of("close"), why.events);
    assertEquals(List.of("close"), hidden.events, "the user's choice wins over relevance");

    ViewContext hung = context(Map.of(ContextFacts.HANGING_PROTOCOL, "weasis.ct"));
    assertEquals(List.of("display", "hp-why"), Relevance.applyTools(tools, hung));
    assertEquals(List.of("close", "show"), why.events);

    Settings.set(Relevance.CONTEXTUAL, false);
    assertEquals(
        List.of("display", "hp-why"),
        Relevance.applyTools(tools, context(Map.of())),
        "show all tools, except what the user hid");
  }

  @Test
  void theToolbarRowShowsTheEnabledAndRelevantBarsInOrder() {
    ToolBarContainer container = new ToolBarContainer();
    PredicateBar viewer = new PredicateBar("viewer", 10, Relevance.ALWAYS);
    PredicateBar volume = new PredicateBar("3d", 50, Relevance.VOLUME);
    PredicateBar cine = new PredicateBar("cine", 80, Relevance.TEMPORAL);
    container.registerToolBar(List.of(cine, volume, viewer));
    assertEquals(List.of(viewer), container.shownBars(), "no context: the capabilities are unknown");
    assertEquals(1, container.getComponentCount());

    container.applyContext(context(Map.of(ContextFacts.VOLUME, true, ContextFacts.TEMPORAL, "CINE")));
    assertEquals(List.of(viewer, volume, cine), container.shownBars(), "sorted by position");
    assertEquals(3, container.getComponentCount());

    container.applyContext(context(Map.of(ContextFacts.VOLUME, true)));
    assertEquals(List.of(viewer, volume), container.shownBars());
    assertEquals(List.of(cine, volume, viewer).size(), container.getRegisteredToolBars().size());

    container.displayToolbar(volume, false);
    assertFalse(volume.isComponentEnabled());
    assertEquals(List.of(viewer), container.shownBars(), "the user disabled it");
    container.displayToolbar(volume, true);
    assertEquals(List.of(viewer, volume), container.shownBars());

    Settings.set(Relevance.CONTEXTUAL, false);
    container.applyContext(context(Map.of()));
    assertEquals(List.of(viewer, volume, cine), container.shownBars(), "show all tools");

    container.registerToolBar(List.of());
    assertEquals(List.of(ToolBarContainer.EMPTY), container.shownBars());
    List<Toolbar> registered = container.getRegisteredToolBars();
    assertEquals(1, registered.size());
  }
}
