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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.docking.ShellState.ShellSide;
import org.weasis.core.ui.docking.SidePanel.Role;
import org.weasis.core.ui.docking.SidePanel.Side;
import org.weasis.core.ui.docking.ws.WorkspaceManager;

class SidePanelTest {

  /** The shell is driven from the EDT, as the viewers do through the GUI executor. */
  private static void edt(Runnable action) throws Exception {
    SwingUtilities.invokeAndWait(action);
  }

  private static void flush() throws Exception {
    edt(() -> {});
  }

  private static List<String> buttonIds(SidePanel panel) {
    return Arrays.stream(panel.getStripe().getComponents())
        .filter(JToggleButton.class::isInstance)
        .map(c -> ((JToggleButton) c).getName())
        .toList();
  }

  private static Optional<JToggleButton> button(SidePanel panel, String id) {
    return Arrays.stream(panel.getStripe().getComponents())
        .filter(JToggleButton.class::isInstance)
        .map(JToggleButton.class::cast)
        .filter(b -> id.equals(b.getName()))
        .findFirst();
  }

  @Test
  void theExplorerSideOpensItsFirstToolAndTheToolsSideWaitsForAClick() throws Exception {
    SidePanel explorer = new SidePanel(Role.EXPLORER, Side.LEFT);
    SidePanel tools = new SidePanel(Role.TOOLS, Side.RIGHT);
    edt(() -> explorer.show(new FakeTool("file-explorer", 10)));
    edt(() -> explorer.show(new FakeTool("dicom-explorer", 20)));
    edt(() -> tools.show(new FakeTool("display", 10)));
    flush();
    assertAll(
        () -> assertEquals(Optional.of("file-explorer"), explorer.toolIn(Slot.TOP)),
        () -> assertEquals(Optional.empty(), explorer.toolIn(Slot.BOTTOM)),
        () -> assertEquals(Optional.empty(), tools.toolIn(Slot.TOP)),
        () -> assertEquals(List.of("file-explorer", "dicom-explorer"), buttonIds(explorer)),
        () -> assertTrue(button(explorer, "file-explorer").orElseThrow().isSelected()));
  }

  @Test
  void aToolWithTheSameIdIsSwappedInPlaceAndTheStripeKeepsItsButton() throws Exception {
    SidePanel tools = new SidePanel(Role.TOOLS, Side.RIGHT);
    FakeTool display2d = new FakeTool("display", 10);
    FakeTool image = new FakeTool("image", 20);
    edt(() -> tools.show(display2d));
    edt(() -> tools.show(image));
    edt(() -> tools.open("display", Slot.TOP));
    flush();
    JToggleButton before = button(tools, "display").orElseThrow();
    Component shownBefore = tools.getComponent(0);

    // a viewer switch: the 2D tools close and the 3D tools show in one event, same ids
    FakeTool display3d = new FakeTool("display", 10);
    FakeTool volume = new FakeTool("volume", 20);
    edt(
        () -> {
          tools.hide(display2d);
          tools.hide(image);
          tools.show(display3d);
          tools.show(volume);
        });
    flush();

    assertAll(
        () -> assertEquals(Optional.of("display"), tools.toolIn(Slot.TOP)),
        () -> assertSame(before, button(tools, "display").orElseThrow(), "button kept"),
        () -> assertSame(shownBefore, tools.getComponent(0), "slot host kept"),
        () ->
            assertTrue(
                SwingUtilities.isDescendingFrom(display3d.getToolComponent(), shownBefore),
                "content swapped in place"),
        () -> assertEquals(List.of("display", "volume"), buttonIds(tools)));
  }

  @Test
  void aDetailToolOpensAtTheBottomAndTheSideStateReportsBothSlots() throws Exception {
    SidePanel tools = new SidePanel(Role.TOOLS, Side.RIGHT);
    edt(() -> tools.show(new FakeTool("display", 10)));
    edt(() -> tools.show(new FakeTool("segmentation", 30, Slot.BOTTOM)));
    edt(() -> tools.open("display", Slot.TOP));
    flush();
    edt(() -> button(tools, "segmentation").orElseThrow().doClick());
    flush();
    ShellSide state = tools.captureState();
    assertAll(
        () -> assertEquals(Optional.of("segmentation"), tools.toolIn(Slot.BOTTOM)),
        () -> assertEquals(Arrays.asList("display", "segmentation"), state.slots()),
        () -> assertFalse(state.collapsed()),
        () -> assertTrue(state.width() > 0),
        () ->
            assertTrue(
                button(tools, "segmentation").orElseThrow().getToolTipText().contains("bottom")));
  }

  @Test
  void clickingAShownToolCollapsesItsSlotAndTheMemoryBringsItBack() throws Exception {
    SidePanel explorer = new SidePanel(Role.EXPLORER, Side.LEFT);
    FakeTool dicom = new FakeTool("dicom-explorer", 10);
    edt(() -> explorer.show(dicom));
    flush();
    edt(() -> button(explorer, "dicom-explorer").orElseThrow().doClick());
    flush();
    boolean collapsed = explorer.isCollapsed();
    edt(() -> explorer.expand());
    flush();
    assertAll(
        () -> assertTrue(collapsed),
        () -> assertEquals(Arrays.asList("dicom-explorer", null), explorer.captureState().slots()),
        () -> assertFalse(explorer.isCollapsed()),
        () -> assertEquals(Optional.of("dicom-explorer"), explorer.toolIn(Slot.TOP)));
  }

  @Test
  void applyingAStateFillsTheSlotsAndSkipsTheIdsTheSideDoesNotOffer() throws Exception {
    SidePanel tools = new SidePanel(Role.TOOLS, Side.RIGHT);
    edt(() -> tools.show(new FakeTool("display", 10)));
    edt(() -> tools.show(new FakeTool("measure", 30)));
    flush();
    List<String> skipped =
        tools.applyState(new ShellSide(Arrays.asList("measure", "nope"), 333, 0.4, false));
    flush();
    List<String> cleared =
        tools.applyState(new ShellSide(Arrays.asList("display", null), 0, 0, true));
    flush();
    assertAll(
        () -> assertEquals(List.of("nope"), skipped),
        () -> assertEquals(List.of(), cleared),
        () -> assertEquals(333, tools.getContentWidth()),
        () -> assertEquals(0.4, tools.getRatio(), 1e-9),
        () -> assertEquals(Optional.of("display"), tools.toolIn(Slot.TOP)),
        () -> assertEquals(Optional.empty(), tools.toolIn(Slot.BOTTOM)),
        () -> assertTrue(tools.isCollapsed()));
  }

  @Test
  void aBottomToolWithAShareOfItsOwnOpensAtItAndKeepsTheRatioSetForIt() throws Exception {
    SidePanel explorer = new SidePanel(Role.EXPLORER, Side.LEFT);
    edt(() -> explorer.show(new FakeTool("dicom-explorer", 10)));
    edt(() -> explorer.show(new FakeTool("share-import", 30, Slot.BOTTOM, 0.25)));
    edt(() -> explorer.show(new FakeTool("share-retrieve", 31, Slot.BOTTOM, 0.65)));
    edt(() -> explorer.open("share-import", Slot.BOTTOM));
    flush();
    double importRatio = explorer.getRatio();
    edt(() -> explorer.setRatio(0.5));
    flush();
    edt(() -> explorer.open("share-retrieve", Slot.BOTTOM));
    flush();
    double retrieveRatio = explorer.getRatio();
    edt(() -> explorer.open("share-import", Slot.BOTTOM));
    flush();
    double importAgain = explorer.getRatio();
    edt(() -> explorer.clear(Slot.BOTTOM));
    flush();
    double sideRatio = explorer.getRatio();
    assertAll(
        () -> assertEquals(0.75, importRatio, 1e-9, "opens at its share"),
        () -> assertEquals(0.35, retrieveRatio, 1e-9, "another tool, another share"),
        () -> assertEquals(0.5, importAgain, 1e-9, "the ratio set for it is kept"),
        () -> assertNotEquals(0.5, sideRatio, 1e-9, "the side ratio is not touched"));
  }

  @Test
  void aToolOpenedByTheUserWidensTheSideWhileShownWithoutChangingTheWidthOfTheUser()
      throws Exception {
    SidePanel explorer = new SidePanel(Role.EXPLORER, Side.LEFT);
    edt(() -> explorer.show(new FakeTool("dicom-explorer", 10)));
    edt(() -> explorer.show(new FakeTool("fit-import", 30, Slot.BOTTOM).widthToFit(420)));
    edt(() -> explorer.show(new FakeTool("fit-export", 31, Slot.BOTTOM).widthToFit(380)));
    edt(() -> explorer.show(new FakeTool("fit-narrow", 32, Slot.BOTTOM).widthToFit(150)));
    edt(() -> explorer.setContentWidth(260));
    flush();
    edt(() -> button(explorer, "fit-import").orElseThrow().doClick());
    flush();
    int widened = explorer.getContentWidth();
    int recorded = explorer.captureState().width();
    edt(() -> button(explorer, "fit-export").orElseThrow().doClick());
    flush();
    int switched = explorer.getContentWidth();
    edt(() -> button(explorer, "fit-narrow").orElseThrow().doClick());
    flush();
    int narrow = explorer.getContentWidth();
    edt(() -> button(explorer, "fit-import").orElseThrow().doClick());
    flush();
    edt(() -> button(explorer, "fit-import").orElseThrow().doClick());
    flush();
    int closed = explorer.getContentWidth();
    edt(() -> explorer.open("fit-import", Slot.BOTTOM));
    flush();
    int applied = explorer.getContentWidth();
    assertAll(
        () -> assertEquals(420, widened, "widened for the tool"),
        () -> assertEquals(260, recorded, "a session keeps the width of the user"),
        () -> assertEquals(380, switched, "another tool, its own width"),
        () -> assertEquals(260, narrow, "a narrower tool gets the width of the user back"),
        () -> assertEquals(260, closed, "closing the tool restores the width of the user"),
        () -> assertEquals(260, applied, "an open that is not the user's does not widen"));
  }

  @Test
  void aWidthSetWhileWidenedBecomesTheWidthOfTheUser() throws Exception {
    SidePanel explorer = new SidePanel(Role.EXPLORER, Side.LEFT);
    edt(() -> explorer.show(new FakeTool("dicom-explorer", 10)));
    edt(() -> explorer.show(new FakeTool("fit-send", 30, Slot.BOTTOM).widthToFit(400)));
    edt(() -> explorer.setContentWidth(240));
    edt(() -> explorer.openByUser("fit-send", Slot.BOTTOM));
    flush();
    int widened = explorer.getContentWidth();
    edt(() -> explorer.setContentWidth(500));
    edt(() -> explorer.clear(Slot.BOTTOM));
    flush();
    assertAll(
        () -> assertEquals(400, widened),
        () -> assertEquals(500, explorer.getContentWidth()),
        () -> assertEquals(500, explorer.captureState().width()));
  }

  @Test
  void anActionButtonSitsAmongTheToolsByPositionAndRunsWithoutTouchingTheSlots() throws Exception {
    SidePanel explorer = new SidePanel(Role.EXPLORER, Side.LEFT);
    int[] runs = {0};
    edt(() -> explorer.show(new FakeTool("dicom-explorer", 10)));
    edt(() -> explorer.show(new FakeTool("act-import", 30, Slot.BOTTOM)));
    edt(() -> explorer.show(new FakeTool("act-retrieve", 31, Slot.BOTTOM)));
    edt(() -> explorer.addAction(new StripeAction("act-disc", 30, null, "Disc", () -> runs[0]++)));
    flush();
    List<String> order = stripeIds(explorer);
    ShellSide before = explorer.captureState();
    edt(
        () ->
            Arrays.stream(explorer.getStripe().getComponents())
                .filter(c -> "act-disc".equals(c.getName()))
                .map(javax.swing.AbstractButton.class::cast)
                .findFirst()
                .orElseThrow()
                .doClick());
    flush();
    ShellSide after = explorer.captureState();
    edt(() -> explorer.removeAction("act-disc"));
    flush();
    assertAll(
        () ->
            assertEquals(
                List.of("dicom-explorer", "act-import", "act-disc", "act-retrieve"), order),
        () -> assertEquals(1, runs[0]),
        () -> assertEquals(before.slots(), after.slots(), "an action opens no slot"),
        () ->
            assertEquals(
                List.of("dicom-explorer", "act-import", "act-retrieve"), stripeIds(explorer)),
        () -> assertEquals(List.of(), explorer.actionIds()));
  }

  private static List<String> stripeIds(SidePanel panel) {
    return Arrays.stream(panel.getStripe().getComponents())
        .filter(javax.swing.AbstractButton.class::isInstance)
        .map(Component::getName)
        .toList();
  }

  @Test
  void aToolMovedToTheOtherSlotLeavesTheFirstOne() throws Exception {
    SidePanel tools = new SidePanel(Role.TOOLS, Side.RIGHT);
    edt(() -> tools.show(new FakeTool("display", 10)));
    edt(() -> tools.open("display", Slot.TOP));
    flush();
    edt(() -> tools.open("display", Slot.BOTTOM));
    flush();
    assertAll(
        () -> assertEquals(Optional.empty(), tools.toolIn(Slot.TOP)),
        () -> assertEquals(Optional.of("display"), tools.toolIn(Slot.BOTTOM)),
        () -> assertFalse(tools.open("nope", Slot.TOP)));
  }

  @Test
  void aToolOpensAsACentralViewAndComesBackToItsSlot() throws Exception {
    SidePanel tools = new SidePanel(Role.TOOLS, Side.RIGHT);
    WorkspaceManager manager = new WorkspaceManager(null);
    tools.setWorkspace(() -> manager);
    FakeTool display = new FakeTool("display", 10);
    edt(() -> tools.show(display));
    edt(() -> tools.open("display", Slot.TOP));
    flush();
    assertEquals(Optional.of("display"), tools.toolIn(Slot.TOP));

    edt(() -> assertTrue(tools.openAsView("display")));
    flush();
    assertAll(
        () -> assertEquals(Optional.empty(), tools.toolIn(Slot.TOP), "the slot emptied"),
        () -> assertTrue(tools.isView("display")),
        () -> assertEquals(List.of("display"), tools.captureState().views()),
        () ->
            assertEquals(
                List.of("tool:display"), manager.getViews().stream().map(v -> v.viewId()).toList()),
        () -> assertEquals("tool", manager.getViews().getFirst().typeId()),
        () ->
            assertTrue(button(tools, "display").orElseThrow().isSelected(), "the stripe shows it"),
        () -> assertFalse(tools.openAsView("nope")));

    edt(() -> assertTrue(tools.returnFromView("display")));
    flush();
    assertAll(
        () -> assertEquals(Optional.of("display"), tools.toolIn(Slot.TOP), "back in its slot"),
        () -> assertFalse(tools.isView("display")),
        () -> assertTrue(manager.getViews().isEmpty()),
        () -> assertEquals(List.of(), tools.captureState().views()));

    // a state that names the view opens it again, and a slot entry brings it back
    edt(
        () ->
            tools.applyState(
                new ShellSide(Arrays.asList(null, null), 0, 0, false, List.of("display"))));
    flush();
    assertTrue(tools.isView("display"));
    edt(() -> tools.applyState(new ShellSide(Arrays.asList("display", null), 0, 0, false)));
    flush();
    assertAll(
        () -> assertFalse(tools.isView("display")),
        () -> assertEquals(Optional.of("display"), tools.toolIn(Slot.TOP)),
        () -> assertTrue(manager.getViews().isEmpty()));
  }
}
