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
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.util.DropDownButton;
import org.weasis.core.api.gui.util.DynamicMenu;
import org.weasis.core.api.setting.SettingEnvironment;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.util.ToolBarContainer;
import org.weasis.core.ui.util.WtoolBar;

class ActionCatalogTest {

  private final List<String> ran = new ArrayList<>();
  private final List<Supplier<List<ActionEntry>>> sources = new ArrayList<>();

  @BeforeEach
  void installEnvironment() {
    Settings.install(SettingEnvironment.inMemory(new HashMap<>()));
  }

  @AfterEach
  void removeSources() {
    sources.forEach(ActionCatalog::removeSource);
  }

  private void source(Supplier<List<ActionEntry>> source) {
    sources.add(source);
    ActionCatalog.addSource(source);
  }

  private JMenuItem item(String text) {
    JMenuItem item = new JMenuItem(text);
    item.addActionListener(e -> ran.add(text));
    return item;
  }

  private JMenuBar menuBar() {
    JMenuBar bar = new JMenuBar();
    JMenu file = new JMenu("File");
    JMenuItem open = item("Open DICOM…");
    open.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
    file.add(open);
    JMenu export = new JMenu("Export");
    export.add(item("Image"));
    JMenuItem disabled = item("Not now");
    disabled.setEnabled(false);
    export.add(disabled);
    file.add(export);
    bar.add(file);
    DynamicMenu view =
        new DynamicMenu("View") {
          @Override
          public void popupMenuWillBecomeVisible() {
            removeAll();
            add(item("Toolbars"));
          }
        };
    bar.add(view);
    return bar;
  }

  @Test
  void theMenuBarGivesOneEntryPerLeafWithItsPathShortcutAndGroup() {
    List<ActionEntry> entries = ActionCatalog.ofMenuBar(menuBar());
    Map<String, ActionEntry> byId = new HashMap<>();
    entries.forEach(e -> byId.put(e.id(), e));
    assertAll(
        () -> assertEquals(3, entries.size(), entries.toString()),
        () -> assertEquals("Open DICOM…", byId.get("menu/file/open-dicom").label()),
        () -> assertEquals("File", byId.get("menu/file/open-dicom").group()),
        () -> assertEquals("Ctrl+O", byId.get("menu/file/open-dicom").shortcut()),
        () -> assertEquals("Export › Image", byId.get("menu/file/export/image").label()),
        () -> assertTrue(byId.containsKey("menu/view/toolbars"), "the dynamic menu is built"),
        () -> assertFalse(byId.containsKey("menu/file/export/not-now"), "disabled: left out"));
    byId.get("menu/file/export/image").run().run();
    assertEquals(List.of("Image"), ran);
  }

  /** A toolbar with a predicate. */
  private static final class VolumeBar extends WtoolBar {
    VolumeBar() {
      super("3D", 50);
    }

    @Override
    public boolean isRelevant(ViewContext context) {
      return Relevance.VOLUME.test(context);
    }
  }

  @Test
  void theToolbarButtonsAreListedShownOrNotWithTheirCommand() {
    ToolBarContainer container = new ToolBarContainer();
    WtoolBar viewer = new WtoolBar("Viewer", 10);
    JButton zoom = new JButton("Z");
    zoom.setToolTipText("<html>Zoom <b>in</b></html>");
    zoom.setActionCommand("zoomIn");
    zoom.addActionListener(e -> ran.add("zoom"));
    viewer.add(zoom);
    JToggleButton lens = new JToggleButton("Lens");
    viewer.add(lens);
    viewer.add(
        new DropDownButton("menu", (Icon) null) {
          @Override
          protected JPopupMenu getPopupMenu() {
            return new JPopupMenu();
          }
        });
    VolumeBar volume = new VolumeBar();
    JButton mpr = new JButton("MPR");
    volume.add(mpr);
    container.registerToolBar(List.of(viewer, volume));
    container.applyContext(new ViewContext(null, "2d", null, Map.of(ContextFacts.VOLUME, false)));

    List<ActionEntry> entries = ActionCatalog.ofToolbars(container);
    Map<String, ActionEntry> byId = new HashMap<>();
    entries.forEach(e -> byId.put(e.id(), e));
    assertAll(
        () -> assertEquals(3, entries.size(), entries.toString()),
        () -> assertEquals("Zoom in", byId.get("toolbar/w/zoomin").label()),
        () -> assertEquals("zoomIn", byId.get("toolbar/w/zoomin").command()),
        () -> assertEquals("Viewer", byId.get("toolbar/w/zoomin").group()),
        () -> assertTrue(byId.get("toolbar/w/zoomin").relevant()),
        () -> assertEquals("", byId.get("toolbar/w/lens").command(), "text, not a command"),
        () -> assertFalse(byId.get("toolbar/volume/mpr").relevant(), "its bar is out of the row"),
        () -> assertFalse(byId.containsKey("toolbar/w/menu"), "a drop-down only opens"));
    byId.get("toolbar/w/zoomin").run().run();
    assertEquals(List.of("zoom"), ran);
  }

  private static DockableTool tool(String id, boolean relevant) {
    DockableTool tool = mock(DockableTool.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
    when(tool.getToolId()).thenReturn(id);
    when(tool.getComponentName()).thenReturn(id.toUpperCase());
    when(tool.getPanelType()).thenReturn(Insertable.Type.TOOL);
    when(tool.isComponentEnabled()).thenReturn(true);
    when(tool.isRelevant(org.mockito.ArgumentMatchers.any())).thenReturn(relevant);
    return tool;
  }

  @Test
  void theToolsOfTheViewerAreListedAndRunningOneRevealsIt() {
    DockableTool measure = tool("measure", true);
    DockableTool why = tool("hp-why", false);
    SeriesViewer<?> viewer = mock(SeriesViewer.class);
    when(viewer.getSeriesViewerUI())
        .thenReturn(new SeriesViewerUI(Object.class, null, List.of(measure, why), null));
    ViewContext context = new ViewContext(viewer, "2d", null, Map.of());

    List<ActionEntry> entries = ActionCatalog.ofTools(context);
    assertAll(
        () -> assertEquals(2, entries.size()),
        () -> assertEquals("tool/measure", entries.get(0).id()),
        () -> assertTrue(entries.get(0).relevant()),
        () -> assertFalse(entries.get(1).relevant()),
        () -> assertTrue(ActionCatalog.ofTools(ViewContext.empty()).isEmpty()));
    entries.get(1).run().run();
    assertTrue(Relevance.isRevealed(why));
    Relevance.apply(ViewContext.empty());
  }

  @Test
  void theCatalogMergesItsSourcesAndSearchesByWord() {
    source(() -> ActionCatalog.ofMenuBar(menuBar()));
    source(
        () ->
            List.of(
                new ActionEntry("x/zoom-in", "Zoom in", "Viewer", "", "zoomIn", true, () -> {}),
                new ActionEntry("x/zoom-out", "Zoom out", "Viewer", "", "", false, () -> {}),
                new ActionEntry("menu/file/open-dicom", "Dup", "Z", "", "", true, () -> {})));
    List<ActionEntry> all = ActionCatalog.entries();
    assertAll(
        () -> assertEquals(5, all.size(), "one entry per id, the first source wins"),
        () ->
            assertEquals(
                "Open DICOM…", ActionCatalog.find("menu/file/open-dicom").orElseThrow().label()),
        () -> assertEquals("File", all.get(0).group(), "sorted by group then label"));
    List<String> zoom = ActionCatalog.search("zoom").stream().map(ActionEntry::id).toList();
    List<String> out = ActionCatalog.search("out zo").stream().map(ActionEntry::id).toList();
    List<String> group = ActionCatalog.search("viewer").stream().map(ActionEntry::id).toList();
    assertAll(
        () -> assertEquals(List.of("x/zoom-in", "x/zoom-out"), zoom),
        () -> assertEquals(List.of("x/zoom-out"), out, "every word must match"),
        () -> assertEquals(2, group.size(), "the group matches too"),
        () -> assertTrue(ActionCatalog.search("nothing-like-it").isEmpty()),
        () ->
            assertEquals(
                all.stream().map(ActionEntry::id).toList(),
                ActionCatalog.search(" ").stream().map(ActionEntry::id).toList(),
                "a blank query lists everything"));
    assertEquals(
        "{\"id\":\"x/zoom-in\",\"label\":\"Zoom in\",\"group\":\"Viewer\",\"relevant\":true,\"command\":\"zoomIn\"}",
        all.stream()
            .filter(e -> e.id().equals("x/zoom-in"))
            .findFirst()
            .orElseThrow()
            .toJson()
            .toString());
    assertEquals("open-dicom", ActionEntry.slug(" Open DICOM… "));
  }
}
