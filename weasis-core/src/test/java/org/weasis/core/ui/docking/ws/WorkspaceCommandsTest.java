/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.docking.ws;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.command.CommandContext;
import org.weasis.core.api.command.CommandModel;
import org.weasis.core.api.command.CommandOrigin;
import org.weasis.core.api.command.CommandResult;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.TagW;

class WorkspaceCommandsTest {

  private static final class StubView extends JPanel implements WorkspaceView {
    private final String id = UUID.randomUUID().toString();
    private final String title;

    StubView(String title) {
      this.title = title;
    }

    @Override
    public String viewId() {
      return id;
    }

    @Override
    public JComponent component() {
      return this;
    }

    @Override
    public String tabTitle() {
      return title;
    }

    @Override
    public JsonObject saveState() {
      return Json.createObjectBuilder().add("name", title).build();
    }
  }

  private WorkspaceManager manager;
  private WorkspaceCommands commands;
  private StubView v1;
  private StubView v2;

  @BeforeEach
  void setUp() {
    manager = new WorkspaceManager(null);
    commands = new WorkspaceCommands().withManager(() -> manager);
    v1 = new StubView("one");
    v2 = new StubView("two");
    manager.open(v1);
    manager.open(v2);
    manager.select(v2);
  }

  private CommandResult run(CommandOrigin origin, String verb, String... args) {
    return CommandContext.runWith(
        new CommandContext(origin), () -> CommandModel.execute(commands, verb, args));
  }

  private static JsonObject json(String text) {
    return Json.createReader(new StringReader(text)).readObject();
  }

  @Test
  void listReturnsWindowsGroupsAndViews() {
    CommandResult result = run(CommandOrigin.LAUNCH_ARGS, "list");
    JsonObject state = json(result.body());
    var views =
        state
            .getJsonArray("windows")
            .getJsonObject(0)
            .getJsonArray("groups")
            .getJsonObject(0)
            .getJsonArray("views");
    assertAll(
        () -> assertEquals(CommandResult.OK, result.status()),
        () -> assertEquals(2, views.size()),
        () -> assertEquals(v1.viewId(), views.getJsonObject(0).getString("id")),
        () -> assertEquals("one", views.getJsonObject(0).getString("name")),
        () -> assertTrue(views.getJsonObject(1).getBoolean("selected")));
  }

  @Test
  void listNumbersTheWindowsAndGroupsAndNamesTheMaximizedView() {
    run(CommandOrigin.LAUNCH_ARGS, "maximize", "--view", v2.viewId());
    JsonObject window =
        json(run(CommandOrigin.LAUNCH_ARGS, "list").body())
            .getJsonArray("windows")
            .getJsonObject(0);
    assertAll(
        () -> assertEquals(1, window.getInt("window")),
        () -> assertTrue(window.getBoolean("main")),
        () -> assertEquals(v2.viewId(), window.getString("maximized")),
        () -> assertEquals(1, window.getJsonArray("groups").getJsonObject(0).getInt("group")));
  }

  @Test
  void aWorkspaceWithoutAnyViewStillLists() {
    manager.close(v1);
    manager.close(v2);
    CommandResult result = run(CommandOrigin.LAUNCH_ARGS, "list");
    // the main window of the application keeps an empty tree, a floating one is closed with it
    assertAll(
        () -> assertEquals(CommandResult.OK, result.status(), result.error()),
        () ->
            json(result.body()).getJsonArray("windows").stream()
                .map(jakarta.json.JsonValue::asJsonObject)
                .forEach(w -> assertEquals(0, w.getJsonArray("groups").size())));
  }

  @Test
  void selectEchoesStateOnlyForJsonOutput() {
    CommandResult quiet = run(CommandOrigin.LAUNCH_ARGS, "select", "--view", v1.viewId());
    CommandResult remote = run(CommandOrigin.REMOTE, "select", "--view", v2.viewId());
    assertAll(
        () -> assertEquals(CommandResult.OK, quiet.status()),
        () -> assertEquals("", quiet.body()),
        () -> assertEquals(CommandResult.OK, remote.status()),
        () -> assertTrue(remote.body().contains(v2.viewId())));
  }

  @Test
  void unknownViewAndBadOptionsAreReported() {
    CommandResult unknown = run(CommandOrigin.REMOTE, "select", "--view", "nope");
    CommandResult missing = run(CommandOrigin.REMOTE, "select");
    CommandResult badSide =
        run(
            CommandOrigin.REMOTE,
            "split",
            "--view",
            v2.viewId(),
            "--anchor",
            v1.viewId(),
            "--side",
            "diagonal");
    CommandResult badRatio =
        run(
            CommandOrigin.REMOTE,
            "split",
            "--view",
            v2.viewId(),
            "--anchor",
            v1.viewId(),
            "--side",
            "right",
            "--ratio",
            "2");
    CommandResult badBounds =
        run(CommandOrigin.REMOTE, "detach", "--view", v2.viewId(), "--bounds", "1,2,3");
    CommandResult help = run(CommandOrigin.REMOTE, "split", "--help");
    assertAll(
        () -> assertEquals(CommandResult.ERROR, unknown.status()),
        () -> assertTrue(unknown.error().contains("Unknown view id: nope"), unknown.error()),
        () -> assertEquals(CommandResult.USAGE, missing.status()),
        () -> assertTrue(missing.error().contains("--view"), missing.error()),
        () -> assertEquals(CommandResult.USAGE, badSide.status()),
        () -> assertEquals(CommandResult.USAGE, badRatio.status()),
        () -> assertTrue(badRatio.error().contains("--ratio"), badRatio.error()),
        () -> assertEquals(CommandResult.USAGE, badBounds.status()),
        () -> assertTrue(badBounds.error().contains("x,y,width,height"), badBounds.error()),
        () -> assertEquals(CommandResult.OK, help.status()),
        () -> assertTrue(help.body().contains("TOP, BOTTOM, LEFT, RIGHT"), help.body()));
  }

  @Test
  void splitAndStackChangeTheLayout() {
    CommandResult split =
        run(
            CommandOrigin.LAUNCH_ARGS,
            "split",
            "--view",
            v2.viewId(),
            "--anchor",
            v1.viewId(),
            "--side",
            "Right");
    assertEquals(CommandResult.OK, split.status(), split.error());
    assertEquals(2, manager.getTabGroups(null).size());
    assertNotSame(manager.groupOf(v1).orElseThrow(), manager.groupOf(v2).orElseThrow());

    CommandResult stack =
        run(CommandOrigin.LAUNCH_ARGS, "stack", "--view", v2.viewId(), "--anchor", v1.viewId());
    assertEquals(CommandResult.OK, stack.status(), stack.error());
    assertSame(manager.groupOf(v1).orElseThrow(), manager.groupOf(v2).orElseThrow());
  }

  @Test
  void snapshotWritesAFileAndLayoutRestoresIt(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("layout.json");
    CommandContext context = new CommandContext(CommandOrigin.REMOTE);
    CommandResult snapshot =
        CommandContext.runWith(
            context,
            () -> CommandModel.execute(commands, "save", new String[] {"--out", file.toString()}));
    assertAll(
        () -> assertEquals(CommandResult.OK, snapshot.status(), snapshot.error()),
        () -> assertTrue(Files.exists(file)),
        () -> assertEquals(List.of(file.toAbsolutePath()), context.files()),
        () -> assertTrue(snapshot.body().contains("layout.json")));

    CommandResult inline = run(CommandOrigin.REMOTE, "save");
    assertTrue(inline.body().contains(v1.viewId()));

    run(
        CommandOrigin.LAUNCH_ARGS,
        "split",
        "--view",
        v2.viewId(),
        "--anchor",
        v1.viewId(),
        "--side",
        "bottom");
    assertEquals(2, manager.getTabGroups(null).size());
    CommandResult layout = run(CommandOrigin.LAUNCH_ARGS, "load", "--file", file.toString());
    assertAll(
        () -> assertEquals(CommandResult.OK, layout.status(), layout.error()),
        () -> assertEquals(1, manager.getTabGroups(null).size()),
        () -> assertFalse(layout.body().contains("windows"), "no echo without --json"));
  }

  /** A view whose title comes from the patient, resolved under the mask in force. */
  private static final class PatientView extends JPanel implements WorkspaceView {
    private static final String PATIENT = "DOE^JOHN"; // NON-NLS

    @Override
    public String viewId() {
      return "patient-view"; // NON-NLS
    }

    @Override
    public JComponent component() {
      return this;
    }

    @Override
    public String tabTitle() {
      return PATIENT;
    }

    @Override
    public JsonObject saveState() {
      return Json.createObjectBuilder()
          .add("name", IdentityMask.maskText(TagW.PatientPseudoUID, PATIENT))
          .build();
    }
  }

  @Test
  void remoteCallerNeverReadsAnIdentifyingViewTitle(@TempDir Path dir) throws Exception {
    manager.open(new PatientView());
    Path file = dir.resolve("remote.json");

    CommandResult local = run(CommandOrigin.LAUNCH_ARGS, "list");
    CommandResult list = run(CommandOrigin.REMOTE, "list");
    CommandResult echo = run(CommandOrigin.REMOTE, "select", "--view", v1.viewId());
    CommandResult inline = run(CommandOrigin.REMOTE, "save");
    run(CommandOrigin.REMOTE, "save", "--out", file.toString());

    assertAll(
        () -> assertTrue(local.body().contains(PatientView.PATIENT), "session behaviour locally"),
        () -> assertTrue(list.body().contains("patient-view")),
        () -> assertFalse(list.body().contains("DOE"), list.body()),
        () -> assertFalse(echo.body().contains("DOE"), echo.body()),
        () -> assertFalse(inline.body().contains("DOE"), inline.body()),
        () -> assertFalse(Files.readString(file).contains("DOE")));
  }

  /** The namespace is new in 5.0, but its first spellings were renamed before it shipped. */
  @Test
  void theEarlierVerbNamesAreGone() {
    assertAll(
        () -> assertEquals(CommandResult.USAGE, run(CommandOrigin.REMOTE, "snapshot").status()),
        () -> assertEquals(CommandResult.USAGE, run(CommandOrigin.REMOTE, "restore").status()),
        () ->
            assertEquals(
                CommandResult.USAGE,
                run(CommandOrigin.REMOTE, "layout", "--file", "x.json").status()));
  }

  @Test
  void headlessWorkspaceIsAnExecutionError() {
    WorkspaceCommands none = new WorkspaceCommands().withManager(() -> null);
    CommandResult result = CommandModel.execute(none, "list", new String[0]);
    assertAll(
        () -> assertEquals(CommandResult.ERROR, result.status()),
        () -> assertTrue(result.error().contains("No workspace available"), result.error()));
  }
}
