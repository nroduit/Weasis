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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.MediaSeries;
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
                ContextFacts.VOLUME,
                true,
                ContextFacts.TEMPORAL,
                "STATIC",
                ContextFacts.CALIBRATED,
                true,
                ContextFacts.objects("SEG"),
                2));
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
                ContextFacts.TEMPORAL,
                "CINE",
                ContextFacts.SUV,
                true,
                ContextFacts.FUSION,
                true,
                ContextFacts.HANGING_PROTOCOL,
                "weasis.ct"));
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
    assertTrue(
        Relevance.isActionRelevant("test.cine", context(Map.of(ContextFacts.TEMPORAL, "CINE"))));
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
    assertEquals(
        List.of(viewer), container.shownBars(), "no context: the capabilities are unknown");
    assertEquals(2, container.getComponentCount(), "the viewer bar and the overflow");
    assertEquals(List.of(volume, cine), container.hiddenBars());

    container.applyContext(
        context(Map.of(ContextFacts.VOLUME, true, ContextFacts.TEMPORAL, "CINE")));
    assertEquals(List.of(viewer, volume, cine), container.shownBars(), "sorted by position");
    assertEquals(3, container.getComponentCount(), "nothing hidden: no overflow");

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

  @Test
  void aHiddenToolbarRevealedFromTheOverflowStaysForTheSameViewOnly() {
    ToolBarContainer container = new ToolBarContainer();
    PredicateBar viewer = new PredicateBar("viewer", 10, Relevance.ALWAYS);
    PredicateBar cine = new PredicateBar("cine", 80, Relevance.TEMPORAL);
    container.registerToolBar(List.of(viewer, cine));
    MediaSeries<?> series = mock(MediaSeries.class);
    ViewContext still =
        new ViewContext(null, "2d", series, Map.of(ContextFacts.TEMPORAL, "STATIC"));
    container.applyContext(still);
    assertEquals(List.of(cine), container.hiddenBars());

    assertFalse(container.reveal("nope"));
    assertTrue(container.reveal(cine.getToolbarId()));
    assertAll(
        () -> assertEquals(List.of(viewer, cine), container.shownBars()),
        () -> assertTrue(container.hiddenBars().isEmpty()),
        () -> assertEquals(2, container.getComponentCount(), "no overflow left"));

    container.applyContext(
        new ViewContext(null, "2d", series, Map.of(ContextFacts.TEMPORAL, "STATIC", "x", 1)));
    assertEquals(List.of(viewer, cine), container.shownBars(), "same view, new facts: kept");

    container.applyContext(context(Map.of(ContextFacts.TEMPORAL, "STATIC")));
    assertEquals(List.of(viewer), container.shownBars(), "another view: hidden again");
  }

  @Test
  void aHiddenToolRevealedFromTheOverflowStaysUntilAnotherView() {
    PredicateTool why = new PredicateTool("hp-why", Relevance.PROTOCOL);
    PredicateTool hiddenByUser = new PredicateTool("measure", Relevance.PROTOCOL);
    assertTrue(ToolVisibility.setVisible(hiddenByUser, false));
    SeriesViewer<?> viewer = mock(SeriesViewer.class);
    when(viewer.getSeriesViewerUI())
        .thenReturn(new SeriesViewerUI(Object.class, null, List.of(why, hiddenByUser), null));
    MediaSeries<?> series = mock(MediaSeries.class);
    ViewContext plain = new ViewContext(viewer, "2d", series, Map.of());

    Relevance.apply(plain);
    assertEquals(List.of(why), Relevance.hiddenTools(plain), "what the user hid is not offered");
    assertEquals(List.of("close"), why.events);

    Relevance.reveal(why);
    assertAll(
        () -> assertTrue(Relevance.isRevealed(why)),
        () -> assertTrue(Relevance.hiddenTools(plain).isEmpty()),
        () -> assertEquals(List.of("close", "show"), why.events));
    Relevance.apply(new ViewContext(viewer, "2d", series, Map.of("x", 1)));
    assertEquals(List.of("close", "show", "show"), why.events, "same view: still shown");

    Relevance.apply(new ViewContext(viewer, "2d", mock(MediaSeries.class), Map.of()));
    assertAll(
        () -> assertFalse(Relevance.isRevealed(why)),
        () -> assertEquals(List.of("close", "show", "show", "close"), why.events));
    assertTrue(Relevance.hiddenTools(ViewContext.empty()).isEmpty());
  }

  @Test
  void anItemRelevantButNotApplicableIsDisabledWithTheReason() {
    JMenuItem item = new JMenuItem("Calibrate");
    Relevance.notApplicable(item, "Needs a line");
    assertFalse(item.isEnabled());
    assertEquals("Needs a line", item.getToolTipText());
  }

  @Test
  void aBurstOfContextsIsAppliedOnceWithTheLastOne() throws InterruptedException {
    AtomicInteger applications = new AtomicInteger();
    AtomicReference<ViewContext> last = new AtomicReference<>();
    CountDownLatch applied = new CountDownLatch(1);
    ToolBarContainer counting =
        new ToolBarContainer() {
          @Override
          public void applyContext(ViewContext context, UiProfile profile) {
            applications.incrementAndGet();
            last.set(context);
            applied.countDown();
          }
        };
    Relevance.setToolbarContainer(() -> counting);
    try {
      ViewContext first = context(Map.of("n", 1));
      ViewContext third = context(Map.of("n", 3));
      Relevance.schedule(first);
      Relevance.schedule(context(Map.of("n", 2)));
      Relevance.schedule(third);
      assertTrue(applied.await(5, TimeUnit.SECONDS), "applied on the event thread");
      Thread.sleep(Relevance.DEBOUNCE_MS * 2L);
      assertEquals(1, applications.get(), "coalesced");
      assertEquals(third, last.get());
    } finally {
      Relevance.setToolbarContainer(() -> null);
    }
  }
}
