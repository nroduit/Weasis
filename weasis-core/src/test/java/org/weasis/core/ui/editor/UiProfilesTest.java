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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.setting.SettingEnvironment;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.api.util.LayeredEntries.Origin;
import org.weasis.core.api.util.ListDocument;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.MiniStrip;
import org.weasis.core.ui.docking.Shell;
import org.weasis.core.ui.docking.ShellState;
import org.weasis.core.ui.docking.ShellState.ShellSide;
import org.weasis.core.ui.docking.SidePanel;
import org.weasis.core.ui.docking.SidePanel.Role;
import org.weasis.core.ui.docking.SidePanel.Side;
import org.weasis.core.ui.docking.Slot;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MouseActionType;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.MouseButton;
import org.weasis.core.ui.model.graphic.profile.MeasurementProfile;
import org.weasis.core.ui.model.graphic.profile.MeasurementProfileRegistry;
import org.weasis.core.ui.util.ToolBarContainer;
import org.weasis.core.ui.util.WtoolBar;

class UiProfilesTest {

  /** A tool of the shell tests: an id, a position and an empty panel. */
  private static final class ProfileTool implements DockableTool {
    private final String id;
    private final int position;
    private final Slot preferred;
    private final JPanel component = new JPanel();
    private boolean enabled = true;

    ProfileTool(String id, int position, Slot preferred) {
      this.id = id;
      this.position = position;
      this.preferred = preferred;
    }

    @Override
    public String getToolId() {
      return id;
    }

    @Override
    public Slot getPreferredSlot() {
      return preferred;
    }

    @Override
    public void showDockable() {}

    @Override
    public void closeDockable() {}

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
    public Type getPanelType() {
      return Type.TOOL;
    }

    @Override
    public boolean isComponentEnabled() {
      return enabled;
    }

    @Override
    public void setComponentEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    @Override
    public int getComponentPosition() {
      return position;
    }

    @Override
    public void setComponentPosition(int position) {}
  }

  /** A toolbar whose id is given, not derived from the class name. */
  private static final class NamedBar extends WtoolBar {
    private final String id;

    NamedBar(String id, int position) {
      super(id, position);
      this.id = id;
    }

    @Override
    public String getToolbarId() {
      return id;
    }
  }

  private static UiProfile ct() {
    return new UiProfile(
        "test.ct", // NON-NLS
        "CT", // NON-NLS
        new ShellState(null, new ShellSide(Arrays.asList("measure", "hp-why"), 0, 0, false), null),
        List.of("measure", "viewer"), // NON-NLS
        null,
        Map.of(MouseButton.LEFT, MouseActionType.ZOOM, MouseButton.WHEEL, MouseActionType.SEQUENCE),
        "test.cross"); // NON-NLS
  }

  private static ViewContext context(SeriesViewer<?> viewer, String profileId) {
    Map<String, Object> facts = new LinkedHashMap<>();
    if (profileId != null) {
      facts.put(ContextFacts.UI_PROFILE, profileId);
    }
    return new ViewContext(viewer, "2d", null, facts); // NON-NLS
  }

  private static MeasurementProfile measurement(String id, String... modalities) {
    return new MeasurementProfile(id, id, List.of(modalities), null, null, null, null, false);
  }

  private Path userFile;
  private MeasurementProfileRegistry measurement;

  @BeforeEach
  void installLayers(@TempDir Path dir) {
    Settings.install(SettingEnvironment.inMemory(new HashMap<>()));
    userFile = dir.resolve(UiProfiles.DOCUMENT);
    UiProfiles.document().setBuiltIn(List.of(ct().toJson()));
    UiProfiles.document().configure(Optional.empty(), userFile);
    measurement =
        new MeasurementProfileRegistry(
            List.of(measurement("default"), measurement("test.cross", "CT")), // NON-NLS
            dir.resolve("site.json"), // NON-NLS
            dir.resolve("measurementProfiles.json")); // NON-NLS
    UiProfiles.setMeasurementRegistry(() -> measurement);
    UiProfiles.setShell(() -> null);
    UiProfiles.release();
  }

  @AfterEach
  void releaseEverything() {
    UiProfiles.release();
    UiProfiles.document().setBuiltIn(List.of());
    UiProfiles.setShell(Shell::current);
    UiProfiles.setMeasurementRegistry(MeasurementProfileRegistry::getInstance);
  }

  @Test
  void theUserLayerOverridesTheBuiltInOneAndComesOffAgain() throws Exception {
    assertEquals(Origin.BUILT_IN, UiProfiles.document().find("test.ct").orElseThrow().origin());
    UiProfiles.document().put(ct().withToolbars(List.of("viewer")).toJson());
    assertAll(
        () ->
            assertEquals(Origin.USER, UiProfiles.document().find("test.ct").orElseThrow().origin()),
        () -> assertEquals(List.of("viewer"), UiProfiles.find("test.ct").orElseThrow().toolbars()),
        () -> assertTrue(Files.isRegularFile(userFile)),
        () -> assertEquals(1, UiProfiles.profiles().size()));
    assertTrue(UiProfiles.document().remove("test.ct"));
    assertEquals(List.of("measure", "viewer"), UiProfiles.find("test.ct").orElseThrow().toolbars());
  }

  @Test
  void theContextActivatesAndReleasesTheProfileWithItsMeasurementProfile() {
    assertNull(UiProfiles.apply(context(null, null)));
    assertTrue(UiProfiles.active().isEmpty());

    UiProfile applied = UiProfiles.apply(context(null, "test.ct"));
    assertAll(
        () -> assertEquals(ct(), applied),
        () -> assertEquals("test.ct", UiProfiles.activeId()),
        () -> assertEquals("test.cross", measurement.overrideId()),
        () -> assertEquals("test.cross", measurement.active().id()));

    assertNull(UiProfiles.apply(context(null, "test.unknown")), "an unknown id releases");
    assertAll(
        () -> assertTrue(UiProfiles.active().isEmpty()),
        () -> assertNull(measurement.overrideId()),
        () -> assertEquals("default", measurement.active().id()));

    Settings.set(Relevance.CONTEXTUAL, false);
    assertNull(UiProfiles.apply(context(null, "test.ct")), "show all tools: no profile");
  }

  @Test
  void theShellTemplateFillsTheSlotsAndAUserChangeGoesToTheUserLayer() throws Exception {
    Shell shell =
        new Shell(
            new SidePanel(Role.EXPLORER, Side.LEFT),
            new SidePanel(Role.TOOLS, Side.RIGHT),
            new MiniStrip());
    UiProfiles.setShell(() -> shell);
    SwingUtilities.invokeAndWait(
        () -> {
          shell.tools().show(new ProfileTool("display", 10, Slot.TOP));
          shell.tools().show(new ProfileTool("measure", 20, Slot.TOP));
          shell.tools().show(new ProfileTool("hp-why", 40, Slot.BOTTOM));
          UiProfiles.apply(context(null, "test.ct"));
        });
    assertAll(
        () -> assertEquals(Optional.of("measure"), shell.tools().toolIn(Slot.TOP)),
        () -> assertEquals(Optional.of("hp-why"), shell.tools().toolIn(Slot.BOTTOM)));

    SwingUtilities.invokeAndWait(
        () -> {
          shell.tools().open("display", Slot.TOP);
          UiProfiles.userChangedSlots(shell.tools());
        });
    UiProfile user = UiProfiles.find("test.ct").orElseThrow();
    assertAll(
        () ->
            assertEquals(Origin.USER, UiProfiles.document().find("test.ct").orElseThrow().origin()),
        () -> assertEquals("display", user.shell().tools().slot(Slot.TOP)),
        () -> assertEquals("hp-why", user.shell().tools().slot(Slot.BOTTOM)),
        () -> assertEquals(List.of("measure", "viewer"), user.toolbars(), "the rest is kept"),
        () -> assertEquals(user, UiProfiles.active().orElseThrow()));
  }

  @Test
  void theToolbarRowFollowsTheProfileAndAToggleEditsItsUserLayer() {
    ToolBarContainer container = new ToolBarContainer();
    NamedBar viewer = new NamedBar("viewer", 10);
    NamedBar measure = new NamedBar("measure", 20);
    NamedBar cine = new NamedBar("cine", 80);
    container.registerToolBar(List.of(viewer, measure, cine));
    assertEquals(List.of(viewer, measure, cine), container.shownBars());

    UiProfile profile = UiProfiles.apply(context(null, "test.ct"));
    container.applyContext(context(null, "test.ct"), profile);
    assertEquals(List.of(measure, viewer), container.shownBars(), "listed bars, profile order");
    assertEquals(2, container.getComponentCount());

    container.displayToolbar(cine, true);
    assertAll(
        () -> assertEquals(List.of(measure, viewer, cine), container.shownBars()),
        () ->
            assertEquals(
                List.of("measure", "viewer", "cine"),
                UiProfiles.find("test.ct").orElseThrow().toolbars()),
        () ->
            assertEquals(
                Origin.USER, UiProfiles.document().find("test.ct").orElseThrow().origin()));

    container.displayToolbar(viewer, false);
    assertAll(
        () -> assertEquals(List.of(measure, cine), container.shownBars()),
        () -> assertTrue(viewer.isComponentEnabled(), "the global flag is untouched"),
        () ->
            assertEquals(
                List.of("measure", "cine"), UiProfiles.find("test.ct").orElseThrow().toolbars()));

    UiProfiles.release();
    container.applyContext(context(null, null), null);
    assertEquals(List.of(viewer, measure, cine), container.shownBars(), "the user's row again");
    assertTrue(
        Files.exists(userFile) && UiProfiles.document().userEntries().size() == 1,
        "one user entry, " + ListDocument.documentId(userFile));
  }

  @Test
  @SuppressWarnings("unchecked")
  void theMouseBindingsAreLaidOverTheUsersAndComeBackOnRelease() {
    Map<String, String> bindings = new HashMap<>();
    bindings.put(MouseActions.T_LEFT, "winLevel"); // NON-NLS
    bindings.put(MouseActions.T_WHEEL, "sequence"); // NON-NLS
    MouseActions actions = mock(MouseActions.class);
    when(actions.getAction(anyString()))
        .thenAnswer(i -> bindings.get(i.getArgument(0, String.class)));
    doAnswer(
            i -> {
              bindings.put(i.getArgument(0, String.class), i.getArgument(1, String.class));
              return null;
            })
        .when(actions)
        .setAction(anyString(), anyString());
    ImageViewerEventManager<?> manager = mock(ImageViewerEventManager.class);
    when(manager.getMouseActions()).thenReturn(actions);
    ImageViewerPlugin<?> plugin = mock(ImageViewerPlugin.class);
    doAnswer(i -> manager).when(plugin).getEventManager();
    doAnswer(i -> plugin).when(manager).getSelectedView2dContainer();

    UiProfiles.apply(context(plugin, "test.ct"));
    assertAll(
        () -> assertEquals("zoom", bindings.get(MouseActions.T_LEFT)),
        () -> assertEquals("sequence", bindings.get(MouseActions.T_WHEEL), "already so"));
    verify(plugin).setMouseActions(any());

    UiProfiles.apply(context(plugin, null));
    assertAll(
        () -> assertEquals("winLevel", bindings.get(MouseActions.T_LEFT), "the user's again"),
        () -> assertFalse(UiProfiles.active().isPresent()));
  }

  @Test
  void theMenuEntriesFollowTheActiveProfile() {
    assertTrue(UiProfiles.showsMenu("weasis.contextmenu.presets"), "no profile: every entry");
    UiProfiles.document().setBuiltIn(List.of(ct().toJson(), menus().toJson()));
    UiProfiles.apply(context(null, "test.menus"));
    assertAll(
        () -> assertTrue(UiProfiles.showsMenu("weasis.contextmenu.presets")),
        () -> assertTrue(UiProfiles.showsMenu("zoom")),
        () -> assertFalse(UiProfiles.showsMenu("weasis.contextmenu.filter")),
        () -> assertTrue(UiProfiles.showsMenu(null), "an entry without switch"));
  }

  private static UiProfile menus() {
    return new UiProfile("test.menus", null, null, null, List.of("presets", "zoom"), null, null);
  }
}
