/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.docking.ws;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.awt.GraphicsDevice;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.pf4j.Extension;
import org.weasis.core.api.command.AbstractVerb;
import org.weasis.core.api.command.CommandContext;
import org.weasis.core.api.command.CommandExtension;
import org.weasis.core.api.command.CommandFailure;
import org.weasis.core.api.command.CommandManager;
import org.weasis.core.api.command.InputPath;
import org.weasis.core.api.command.ListOptions;
import org.weasis.core.api.command.OutputPath;
import org.weasis.core.api.command.ScreenRect;
import org.weasis.core.api.command.ScreenRole;
import org.weasis.core.api.command.Since;
import org.weasis.core.api.command.SplitRatio;
import org.weasis.core.api.command.ToolId;
import org.weasis.core.api.command.ViewId;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.MiniStrip;
import org.weasis.core.ui.docking.Shell;
import org.weasis.core.ui.docking.ShellState;
import org.weasis.core.ui.docking.SidePanel;
import org.weasis.core.ui.docking.Slot;
import org.weasis.core.ui.docking.ToolView;
import org.weasis.core.ui.docking.ToolVisibility;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshotIO;
import org.weasis.core.ui.editor.ExternalDisplay;
import org.weasis.core.ui.editor.ScreenRoles;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.ViewContext;
import org.weasis.core.ui.editor.ViewContexts;
import org.weasis.core.ui.editor.ViewerRestorer;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewDisplayState;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * Command namespace {@code workspace}: scripting and agent access to the workspace engine. View ids
 * are the stable {@link WorkspaceView#viewId()} returned by {@code workspace:list}; verbs that
 * change the layout return the new state as JSON when JSON output is on.
 */
@Extension
@Command(
    name = "workspace",
    description =
        "Layout of the viewer workspace: windows, tab groups and views. View ids come from "
            + "'workspace:list'; verbs that change the layout return the new state with --json.",
    subcommands = {
      WorkspaceCommands.ListViews.class,
      WorkspaceCommands.Select.class,
      WorkspaceCommands.Close.class,
      WorkspaceCommands.Maximize.class,
      WorkspaceCommands.Unmaximize.class,
      WorkspaceCommands.Fullscreen.class,
      WorkspaceCommands.Stack.class,
      WorkspaceCommands.Split.class,
      WorkspaceCommands.Detach.class,
      WorkspaceCommands.Screens.class,
      WorkspaceCommands.Move.class,
      WorkspaceCommands.Save.class,
      WorkspaceCommands.Load.class,
      WorkspaceCommands.Panel.class,
      WorkspaceCommands.Context.class
    })
@Since("5.0")
public class WorkspaceCommands implements CommandExtension {

  private Supplier<WorkspaceManager> managerSupplier =
      () -> GuiUtils.getUICore().getWorkspaceManager();
  private Supplier<Shell> shellSupplier = Shell::current;

  @Override
  public String getNamespace() {
    return "workspace"; // NON-NLS
  }

  @Override
  public Object getCommandModel() {
    return this;
  }

  @Override
  public List<org.weasis.core.api.event.EventType> getEventTypes() {
    return WorkspaceManager.eventTypes();
  }

  /** Replaces the workspace lookup, for headless tests. */
  WorkspaceCommands withManager(Supplier<WorkspaceManager> supplier) {
    this.managerSupplier = supplier;
    return this;
  }

  /** Replaces the shell lookup, for headless tests. */
  WorkspaceCommands withShell(Supplier<Shell> supplier) {
    this.shellSupplier = supplier;
    return this;
  }

  WorkspaceManager manager() {
    WorkspaceManager manager = managerSupplier.get();
    if (manager == null) {
      throw new IllegalStateException("No workspace available (headless)");
    }
    return manager;
  }

  /** The shell, or null when the application runs without one. */
  Shell shell() {
    return shellSupplier.get();
  }

  Shell requireShell() {
    Shell shell = shell();
    if (shell == null) {
      throw new IllegalStateException("No shell available (headless)");
    }
    return shell;
  }

  WorkspaceView requireView(ViewId id) {
    return manager().getViews().stream()
        .filter(v -> v.viewId().equals(id.value()))
        .findFirst()
        .orElseThrow(
            () ->
                CommandFailure.unknownHandle(
                    "Unknown view id: " + id, "workspace:list")); // NON-NLS
  }

  /** Captures on the EDT under the mask of the caller: view titles carry the patient name. */
  static WorkspaceSnapshot capture(WorkspaceManager manager) {
    return GuiExecutor.invokeAndRethrow(() -> IdentityMask.callForCommand(manager::capture));
  }

  /**
   * The workspace as commands report it: windows and their tab groups, each view with its viewer
   * type, and, for an image viewer, its grid, its synchronization and what each pane shows. Window
   * and group numbers are positions in this listing, not durable handles; view ids are.
   */
  static String state(WorkspaceManager manager) {
    return state(manager, new ListOptions());
  }

  static String state(WorkspaceManager manager, ListOptions bounds) {
    return GuiExecutor.invokeAndRethrow(
        () -> IdentityMask.callForCommand(() -> build(manager, bounds)));
  }

  private static String build(WorkspaceManager manager, ListOptions bounds) {
    List<JsonObject> windows = new ArrayList<>();
    int index = 0;
    for (WorkspaceSnapshot.WindowSnapshot win : manager.capture().windows()) {
      JsonObjectBuilder w =
          Json.createObjectBuilder().add("window", ++index).add("main", win.main()); // NON-NLS
      if (win.maximizedViewId() != null) {
        w.add("maximized", win.maximizedViewId()); // NON-NLS
      }
      JsonArrayBuilder groups = Json.createArrayBuilder();
      appendGroups(win.root(), groups, new int[1], manager);
      w.add("groups", groups);
      windows.add(w.build());
    }
    JsonObjectBuilder json = Json.createObjectBuilder();
    // the application's selection: the view whose window is the active one
    manager.selectedView().ifPresent(view -> json.add("selected", view.viewId())); // NON-NLS
    return JsonUtil.window(json, "windows", windows, bounds).build().toString(); // NON-NLS
  }

  private static void appendGroups(
      WorkspaceSnapshot.Node node, JsonArrayBuilder groups, int[] counter, WorkspaceManager m) {
    switch (node) {
      case WorkspaceSnapshot.SplitNode(var v, var r, var first, var second) -> {
        appendGroups(first, groups, counter, m);
        appendGroups(second, groups, counter, m);
      }
      case WorkspaceSnapshot.GroupNode(List<WorkspaceSnapshot.ViewDescriptor> views, int sel) -> {
        JsonArrayBuilder array = Json.createArrayBuilder();
        for (int i = 0; i < views.size(); i++) {
          WorkspaceSnapshot.ViewDescriptor d = views.get(i);
          JsonObjectBuilder view =
              Json.createObjectBuilder()
                  .add("id", d.viewId())
                  .add("name", d.state().getString("name", "")) // NON-NLS
                  .add("selected", i == sel);
          m.findView(d.viewId()).ifPresent(live -> describe(live, view));
          array.add(view);
        }
        groups.add(Json.createObjectBuilder().add("group", ++counter[0]).add("views", array));
      }
      case null -> {
        // empty window
      }
    }
  }

  /** Adds the viewer type and, for an image viewer, its grid, synchronization and panes. */
  private static void describe(WorkspaceView live, JsonObjectBuilder view) {
    if (live instanceof ToolView toolView) {
      view.add("tool", toolView.tool().getToolId()); // NON-NLS
      return;
    }
    if (!(live instanceof SeriesViewer<?> viewer)) {
      return;
    }
    String type = ViewDisplayState.viewerTypeId(viewer);
    if (type != null) {
      view.add("viewerType", type); // NON-NLS
    }
    if (live instanceof ImageViewerPlugin<?> container) {
      JsonObject state = ViewDisplayState.ofViewer(container);
      view.add("grid", state.getString("grid")) // NON-NLS
          .add("sync", state.getString("sync")) // NON-NLS
          .add("panes", state.getJsonArray("panes")); // NON-NLS
    }
  }

  /** Shared plumbing of every verb: the parent extension and the state echo. */
  abstract static class Verb extends AbstractVerb {
    @ParentCommand WorkspaceCommands parent;

    /** Echoes the layout after a change when JSON output is on. */
    void printState() {
      if (isJson()) {
        print(state(parent.manager()));
      }
    }
  }

  abstract static class ViewVerb extends Verb {
    @Option(
        names = "--view",
        required = true,
        paramLabel = "<view-id>",
        description = "View id from 'workspace:list'.")
    ViewId viewId;

    @Override
    protected void run() {
      WorkspaceManager manager = parent.manager();
      WorkspaceView view = parent.requireView(viewId);
      GuiExecutor.invokeAndRethrow(() -> apply(manager, view));
      printState();
    }

    abstract void apply(WorkspaceManager manager, WorkspaceView view);
  }

  @Command(name = "list", description = "Windows, tab groups and views as JSON.")
  static class ListViews extends Verb {
    @Mixin ListOptions bounds;

    @Override
    protected void run() {
      print(state(parent.manager(), bounds));
    }
  }

  @Command(name = "select", description = "Brings a view to the front of its tab group.")
  static class Select extends ViewVerb {
    @Override
    void apply(WorkspaceManager manager, WorkspaceView view) {
      manager.select(view);
    }
  }

  @Command(name = "close", description = "Closes a view (fails when the view is not closable).")
  static class Close extends ViewVerb {
    @Override
    void apply(WorkspaceManager manager, WorkspaceView view) {
      if (!view.isClosable()) {
        throw new IllegalStateException("View is not closable: " + view.viewId());
      }
      view.requestClose();
    }
  }

  @Command(name = "maximize", description = "Shows a view alone in its window.")
  static class Maximize extends ViewVerb {
    @Override
    void apply(WorkspaceManager manager, WorkspaceView view) {
      manager.maximize(view);
    }
  }

  @Command(name = "unmaximize", description = "Ends the maximized state of a view.")
  static class Unmaximize extends ViewVerb {
    @Override
    void apply(WorkspaceManager manager, WorkspaceView view) {
      manager.restore(view);
    }
  }

  @Command(
      name = "fullscreen",
      description =
          "Puts a window on the full bounds of its monitor without decorations: the window of "
              + "--view, else the active window. --off ends it. A window state, kept by "
              + "'workspace:save' and restored by 'workspace:load'.")
  @Since("5.0")
  static class Fullscreen extends Verb {
    @Option(
        names = "--view",
        paramLabel = "<view-id>",
        description =
            "View id from 'workspace:list' whose window is meant (default: the active one).")
    ViewId viewId;

    @Option(names = "--off", description = "Ends the fullscreen state of that window.")
    boolean off;

    @Option(
        names = "--canvas",
        description =
            "The selected pane of the view alone fills the viewer, the window and the monitor "
                + "(what F11 and a double click do); --off ends it.")
    boolean canvas;

    @Override
    protected void run() {
      WorkspaceManager manager = parent.manager();
      WorkspaceView view =
          viewId != null ? parent.requireView(viewId) : manager.selectedView().orElse(null);
      GuiExecutor.invokeAndRethrow(
          () -> {
            if (canvas) {
              if (!(view instanceof ImageViewerPlugin<?> viewer)) {
                throw new IllegalArgumentException("--canvas needs an image viewer as --view");
              }
              if (viewer.isCanvasFullscreen() == !off) {
                return;
              }
              viewer.toggleCanvasFullscreen();
              return;
            }
            Window window =
                view != null
                    ? manager.groupOf(view).map(g -> g.getWorkspace().getWindow()).orElse(null)
                    : manager.activeWorkspace().getWindow();
            if (window == null) {
              throw new IllegalStateException("No window to put fullscreen (headless)");
            }
            manager.setFullscreen(window, !off);
          });
      printState();
    }
  }

  @Command(name = "stack", description = "Moves a view into the tab group of another view.")
  static class Stack extends ViewVerb {
    @Option(
        names = "--anchor",
        required = true,
        paramLabel = "<view-id>",
        description = "View whose tab group receives the view.")
    ViewId anchorId;

    @Override
    void apply(WorkspaceManager manager, WorkspaceView view) {
      manager.stack(view, parent.requireView(anchorId));
    }
  }

  @Command(name = "split", description = "Places a view next to another view in a new split.")
  static class Split extends ViewVerb {
    @Option(
        names = "--anchor",
        required = true,
        paramLabel = "<view-id>",
        description = "View whose group is split.")
    ViewId anchorId;

    @Option(
        names = "--side",
        required = true,
        paramLabel = "<side>",
        description = "Side of the anchor for the new group: ${COMPLETION-CANDIDATES}.")
    DockSide side;

    @Option(
        names = "--ratio",
        defaultValue = "0.5",
        paramLabel = "<split-ratio>",
        description = "Share of the space given to the anchor (default: ${DEFAULT-VALUE}).")
    SplitRatio ratio;

    @Override
    void apply(WorkspaceManager manager, WorkspaceView view) {
      manager.split(view, parent.requireView(anchorId), side, ratio.value());
    }
  }

  @Command(
      name = "detach",
      description =
          "Floats a view in its own window: a viewer window, which shows the toolbar row while "
              + "it is the active one. Without option the window opens near the main window; "
              + "--screen fills the usable area of the screen of that role (see 'screens'), "
              + "--bounds gives explicit pixels.")
  static class Detach extends ViewVerb {
    @Option(
        names = "--bounds",
        paramLabel = "<screen-rect>",
        description = "Screen bounds of the new window, as x,y,width,height.")
    ScreenRect bounds;

    @Option(
        names = "--screen",
        paramLabel = "<screen-role>",
        description = "Screen by role: ${COMPLETION-CANDIDATES}.")
    ScreenRole screen;

    @Override
    void apply(WorkspaceManager manager, WorkspaceView view) {
      if (bounds != null && screen != null) {
        throw new IllegalArgumentException("--bounds and --screen are exclusive");
      }
      Rectangle target = bounds == null ? null : bounds.toRectangle();
      if (screen != null) {
        target = ExternalDisplay.onRole(screen, manager.mainWindow()).screenBounds();
      }
      manager.detach(view, target);
    }
  }

  @Command(
      name = "move",
      description =
          "Moves the window of a view to the screen of a role (its usable area, see 'screens'); "
              + "the main window moves too. Ends the fullscreen state of that window.")
  @Since("5.0")
  static class Move extends ViewVerb {
    @Option(
        names = "--screen",
        required = true,
        paramLabel = "<screen-role>",
        description = "Screen by role: ${COMPLETION-CANDIDATES}.")
    ScreenRole screen;

    @Override
    void apply(WorkspaceManager manager, WorkspaceView view) {
      Window window =
          manager
              .windowOf(view)
              .orElseThrow(() -> new IllegalStateException("The view has no window (headless)"));
      manager.moveToScreen(window, screen);
    }
  }

  @Command(
      name = "screens",
      description =
          "The screens of the workstation by role (control, diagnostic-1, diagnostic-2, color, "
              + "portrait) with the monitor each one resolves to: the main window's screen is "
              + "the control screen, the others are derived left to right, and a role the user "
              + "gave a monitor on the Screens preference page wins. A role without a monitor of "
              + "its own resolves to the nearest one and is reported as not fulfilled. --id with "
              + "--role gives a monitor a role, --id with --auto takes it back.")
  @Since("5.0")
  static class Screens extends Verb {
    @Option(
        names = "--id",
        paramLabel = "<screen-id>",
        description = "A screen id from the listing, to give it a role or take it back.")
    String id;

    @Option(
        names = "--role",
        paramLabel = "<screen-role>",
        description = "The role of --id: ${COMPLETION-CANDIDATES}.")
    ScreenRole role;

    @Option(names = "--auto", description = "Clears the role of --id; it is derived again.")
    boolean auto;

    @Override
    protected void run() {
      WorkspaceManager manager = parent.manager();
      if (id == null && (role != null || auto)) {
        throw new IllegalArgumentException("--role and --auto need --id");
      }
      if (id != null && (role == null) == !auto) {
        throw new IllegalArgumentException("--id needs --role or --auto, not both");
      }
      Map<String, GraphicsDevice> devices = ScreenRoles.devices();
      if (id != null) {
        if (!devices.containsKey(id)) {
          throw new IllegalArgumentException(
              "Unknown screen id: " + id + "; the listing gives them"); // NON-NLS
        }
        ScreenRoles.setExplicitRole(id, auto ? null : role);
      }
      ScreenRoles.Mapping mapping =
          GuiExecutor.invokeAndRethrow(() -> ScreenRoles.mapping(manager.mainWindow()));
      ScreenRoles.Screen main = mapping.screen(ScreenRole.CONTROL);
      if (isJson()) {
        JsonArrayBuilder array = Json.createArrayBuilder();
        for (ScreenRole r : ScreenRole.values()) {
          ScreenRoles.Screen screen = mapping.screen(r);
          if (screen == null) {
            continue;
          }
          Rectangle b = screen.bounds();
          array.add(
              Json.createObjectBuilder()
                  .add("role", r.key())
                  .add("screen", screen.id())
                  .add(
                      "bounds",
                      Json.createObjectBuilder()
                          .add("x", b.x)
                          .add("y", b.y)
                          .add("width", b.width)
                          .add("height", b.height))
                  .add("main", screen.equals(main))
                  .add("fulfilled", mapping.isFulfilled(r))
                  .add("source", mapping.isExplicit(r) ? "explicit" : "derived")); // NON-NLS
        }
        print(Json.createObjectBuilder().add("screens", array).build().toString());
      } else {
        StringBuilder text = new StringBuilder();
        for (ScreenRole r : ScreenRole.values()) {
          ScreenRoles.Screen screen = mapping.screen(r);
          if (screen == null) {
            continue;
          }
          Rectangle b = screen.bounds();
          text.append(r.key())
              .append('\t')
              .append(screen.id())
              .append('\t')
              .append(b.x)
              .append(',')
              .append(b.y)
              .append(',')
              .append(b.width)
              .append(',')
              .append(b.height)
              .append('\t')
              .append(mapping.isExplicit(r) ? "explicit" : "derived")
              .append(mapping.isFulfilled(r) ? "" : ", shared")
              .append(screen.equals(main) ? "\t(main)" : "")
              .append(System.lineSeparator());
        }
        print(text.toString().stripTrailing());
      }
    }
  }

  @Command(name = "save", description = "Captures the layout as JSON, inline or to a file.")
  static class Save extends Verb {
    @Option(
        names = "--out",
        paramLabel = "<output-path>",
        description = "Write the snapshot to this file and report its path.")
    OutputPath file;

    @Option(
        names = "--force",
        description = "Replace the file when it exists and was not written by this session.")
    @Since("5.0")
    boolean force;

    @Override
    protected void run() throws Exception {
      WorkspaceSnapshot snapshot = capture(parent.manager());
      Shell shell = parent.shell();
      if (shell != null) {
        snapshot =
            snapshot.withShell(GuiExecutor.invokeAndRethrow(() -> ShellState.capture(shell)));
      }
      if (file != null) {
        WorkspaceSnapshotIO.write(snapshot, file.value());
        CommandContext.current().addFile(file.value());
        if (isJson()) {
          print(
              Json.createObjectBuilder()
                  .add("file", file.toAbsolutePath().toString())
                  .build()
                  .toString());
        }
      } else {
        print(WorkspaceSnapshotIO.toJson(snapshot).toString());
      }
    }
  }

  @Command(
      name = "load",
      description =
          "Restores a layout captured by 'workspace:save': open views are reused, closed viewers "
              + "are created again with their series and presentation when the series are still "
              + "loaded; the shell (explorer and tool slots) follows when the snapshot has one.")
  static class Load extends Verb {
    @Option(
        names = "--file",
        required = true,
        paramLabel = "<input-path>",
        description = "Snapshot file to restore.")
    InputPath file;

    @Override
    protected void run() throws Exception {
      WorkspaceSnapshot snapshot = WorkspaceSnapshotIO.read(file.value());
      WorkspaceManager manager = parent.manager();
      // The previous display is one rollback away
      CommandManager.stateBoundary()
          .checkpoint(
              "before workspace:load " + file.value().getFileName(), // NON-NLS
              CommandContext.current().origin());
      List<String> folded = new ArrayList<>();
      List<String> skippedTools = new ArrayList<>();
      GuiExecutor.invokeAndRethrow(
          () -> {
            // open views are reused by id, closed viewers recreated; the rest is skipped
            List<WorkspaceView> pool = manager.getViews();
            folded.addAll(manager.restore(snapshot, new ViewerRestorer(pool)).folded());
            for (WorkspaceView leftover : pool) {
              if (!manager.isShowing(leftover) && leftover instanceof ViewerPlugin<?> plugin) {
                plugin.close();
                plugin.handleFocusAfterClosing();
              }
            }
            Shell shell = parent.shell();
            if (shell != null && snapshot.shell() != null) {
              skippedTools.addAll(ShellState.apply(snapshot.shell(), shell));
            }
          });
      if (isJson()) {
        JsonObject state = JsonUtil.readObject(state(manager));
        JsonObjectBuilder json = Json.createObjectBuilder(state);
        json.add("folded", Json.createArrayBuilder(folded)); // NON-NLS
        json.add("skippedTools", Json.createArrayBuilder(skippedTools)); // NON-NLS
        print(json.build().toString());
      } else if (!folded.isEmpty() || !skippedTools.isEmpty()) {
        print(
            "folded windows: "
                + String.join(", ", folded)
                + "; skipped tools: "
                + String.join(", ", skippedTools)); // NON-NLS
      }
    }
  }

  @Command(
      name = "context",
      description =
          "What is displayed and why, as the tools of the plugins see it: the type of the "
              + "selected viewer, its selected series and the facts the plugins contribute about "
              + "them (the hanging protocol and its step, the series features). An agent reads "
              + "it to know which tools and verbs apply.")
  @Since("5.0")
  static class Context extends Verb {
    @Override
    protected void run() {
      ViewContext context = GuiExecutor.invokeAndRethrow(ViewContexts::current);
      JsonObject json = IdentityMask.callForCommand(context::toJson);
      print(isJson() ? json.toString() : json.toString());
    }
  }

  @Command(
      name = "panel",
      description =
          "Shell slots around the workspace: the explorer side (leading) and the tools side "
              + "(trailing), two stacked slots each, and the central strip. Without option it "
              + "returns the shell state; --side selects a side, then --slot with --tool or --clear "
              + "fills or empties a slot, --collapse / --expand hide or show the side, --width and "
              + "--ratio size it. Tool ids are stable across viewer types; an id the side does not "
              + "offer now fails and lists the ones it does.")
  @Since("5.0")
  static class Panel extends Verb {
    @Option(
        names = "--side",
        paramLabel = "<side>",
        description = "Side by role: ${COMPLETION-CANDIDATES}.")
    SidePanel.Role side;

    @Option(
        names = "--slot",
        paramLabel = "<slot>",
        description = "Slot of the side: ${COMPLETION-CANDIDATES}.")
    Slot slot;

    @Option(
        names = "--tool",
        paramLabel = "<tool-id>",
        description = "Opens this tool in --slot of --side, replacing what was there.")
    ToolId tool;

    @Option(names = "--clear", description = "Empties --slot of --side.")
    boolean clear;

    @Option(
        names = "--hide",
        description =
            "Hides --tool of --side for good: the user's choice, kept in the setting "
                + "weasis.tool.<id>.visible (see config:setting), until --show.")
    boolean hide;

    @Option(names = "--show", description = "Shows --tool of --side again after --hide.")
    boolean show;

    @Option(
        names = "--as-view",
        description =
            "Shows --tool of --side as a tab of the active window instead of a slot, where it "
                + "gets width and floats like any view; --tool with --slot brings it back.")
    boolean asView;

    @Option(names = "--collapse", description = "Hides both slots of --side (tools remembered).")
    boolean collapse;

    @Option(names = "--expand", description = "Shows the remembered tools of --side again.")
    boolean expand;

    @Option(
        names = "--width",
        paramLabel = "<pixels>",
        description = "Content width of --side, in pixels.")
    Integer width;

    @Option(
        names = "--ratio",
        paramLabel = "<split-ratio>",
        description = "Share of the top slot of --side when both slots are shown.")
    SplitRatio ratio;

    @Option(
        names = "--strip",
        paramLabel = "<tool-id>",
        description = "Keeps the central strip on this tool (fails when another tool is shown).")
    ToolId strip;

    @Option(names = "--no-strip", description = "Empties the central strip.")
    boolean noStrip;

    @Override
    protected void run() {
      boolean change = validate();
      Shell shell = parent.requireShell();
      if (change) {
        GuiExecutor.invokeAndRethrow(() -> apply(shell));
      }
      ShellState state = GuiExecutor.invokeAndRethrow(() -> ShellState.capture(shell));
      if (isJson() || !change) {
        print(isJson() ? state.toJson().toString() : text(state));
      }
    }

    /** Checks the option combinations; returns whether the verb changes something. */
    private boolean validate() {
      boolean slotChange = tool != null || clear;
      boolean sideChange = slotChange || collapse || expand || width != null || ratio != null;
      if (sideChange && side == null) {
        throw invalid(
            "--side is required with "
                + "--tool, --clear, --collapse, --expand, --width and --ratio");
      }
      if (asView && (tool == null || clear || slot != null)) {
        throw invalid("--as-view goes with --tool alone, without --slot or --clear");
      }
      if (hide && show) {
        throw invalid("--hide and --show are exclusive");
      }
      if ((hide || show) && (tool == null || clear || slot != null || asView)) {
        throw invalid("--hide and --show go with --tool alone");
      }
      if (slotChange && slot == null && !asView && !hide && !show) {
        throw invalid("--slot is required with --tool and --clear");
      }
      if (tool != null && clear) {
        throw invalid("--tool and --clear are exclusive");
      }
      if (collapse && expand) {
        throw invalid("--collapse and --expand are exclusive");
      }
      if (strip != null && noStrip) {
        throw invalid("--strip and --no-strip are exclusive");
      }
      if (width != null && width <= 0) {
        throw invalid("--width must be positive");
      }
      return sideChange || strip != null || noStrip;
    }

    private void apply(Shell shell) {
      if (side != null) {
        SidePanel panel = shell.side(side);
        if (hide || show) {
          DockableTool target =
              GuiUtils.getUICore()
                  .getSelectedPlugin()
                  .map(viewer -> viewer.getSeriesViewerUI().tools)
                  .flatMap(
                      tools ->
                          tools.stream()
                              .filter(t -> t.getToolId().equals(tool.value()))
                              .findFirst())
                  .orElseThrow(() -> unknownTool(tool.value(), side.key(), panel.toolIds()));
          if (!ToolVisibility.setVisible(target, show)) {
            throw new IllegalStateException(
                "The site locks " + ToolVisibility.key(tool.value())); // NON-NLS
          }
        } else if (asView) {
          if (!panel.openAsView(tool.value())) {
            throw unknownTool(tool.value(), side.key(), panel.toolIds());
          }
        } else if (tool != null && !panel.open(tool.value(), slot)) {
          throw unknownTool(tool.value(), side.key(), panel.toolIds());
        }
        if (clear) {
          panel.clear(slot);
        }
        if (width != null) {
          panel.setContentWidth(width);
        }
        if (ratio != null) {
          panel.setRatio(ratio.value());
        }
        if (collapse) {
          panel.collapse();
        } else if (expand) {
          panel.expand();
        }
      }
      MiniStrip miniStrip = shell.strip();
      if (noStrip) {
        miniStrip.hideCurrent();
      } else if (strip != null && !strip.value().equals(miniStrip.toolId())) {
        throw unknownTool(
            strip.value(),
            "strip", // NON-NLS
            miniStrip.toolId() == null ? List.of() : List.of(miniStrip.toolId()));
      }
    }

    private static CommandFailure unknownTool(String id, String where, List<String> available) {
      return CommandFailure.unknownHandle(
          "Unknown tool id on " + where + ": " + id + " (available: " + available + ")",
          "workspace:panel"); // NON-NLS
    }

    private static String text(ShellState state) {
      StringBuilder sb = new StringBuilder();
      for (SidePanel.Role role : SidePanel.Role.values()) {
        ShellState.ShellSide s = state.side(role);
        sb.append(role.key()).append(": "); // NON-NLS
        for (Slot slot : Slot.values()) {
          String id = s.slot(slot);
          sb.append(slot.name().toLowerCase(java.util.Locale.ROOT))
              .append('=')
              .append(id == null ? "-" : id)
              .append(' ');
        }
        sb.append("width=")
            .append(s.width()) // NON-NLS
            .append(" ratio=") // NON-NLS
            .append(String.format(java.util.Locale.ROOT, "%.2f", s.ratio()))
            .append(s.collapsed() ? " collapsed" : "") // NON-NLS
            .append(s.views().isEmpty() ? "" : " views=" + String.join(",", s.views())) // NON-NLS
            .append(System.lineSeparator());
      }
      sb.append("strip: ").append(state.strip() == null ? "-" : state.strip()); // NON-NLS
      return sb.toString();
    }
  }
}
