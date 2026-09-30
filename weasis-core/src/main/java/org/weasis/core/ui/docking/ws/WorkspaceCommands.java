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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.pf4j.Extension;
import org.weasis.core.api.command.AbstractVerb;
import org.weasis.core.api.command.CommandContext;
import org.weasis.core.api.command.CommandExtension;
import org.weasis.core.api.command.CommandFailure;
import org.weasis.core.api.command.InputPath;
import org.weasis.core.api.command.ListOptions;
import org.weasis.core.api.command.OutputPath;
import org.weasis.core.api.command.ScreenRect;
import org.weasis.core.api.command.Since;
import org.weasis.core.api.command.SplitRatio;
import org.weasis.core.api.command.ViewId;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshotIO;
import org.weasis.core.ui.editor.SeriesViewer;
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
      WorkspaceCommands.Stack.class,
      WorkspaceCommands.Split.class,
      WorkspaceCommands.Detach.class,
      WorkspaceCommands.Save.class,
      WorkspaceCommands.Load.class
    })
@Since("5.0")
public class WorkspaceCommands implements CommandExtension {

  private Supplier<WorkspaceManager> managerSupplier =
      () -> GuiUtils.getUICore().getWorkspaceManager();

  @Override
  public String getNamespace() {
    return "workspace"; // NON-NLS
  }

  @Override
  public Object getCommandModel() {
    return this;
  }

  /** Replaces the workspace lookup, for headless tests. */
  WorkspaceCommands withManager(Supplier<WorkspaceManager> supplier) {
    this.managerSupplier = supplier;
    return this;
  }

  WorkspaceManager manager() {
    WorkspaceManager manager = managerSupplier.get();
    if (manager == null) {
      throw new IllegalStateException("No workspace available (headless)");
    }
    return manager;
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
    return JsonUtil.window(Json.createObjectBuilder(), "windows", windows, bounds) // NON-NLS
        .build()
        .toString();
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

  @Command(name = "detach", description = "Floats a view in its own window.")
  static class Detach extends ViewVerb {
    @Option(
        names = "--bounds",
        paramLabel = "<screen-rect>",
        description = "Screen bounds of the new window, as x,y,width,height.")
    ScreenRect bounds;

    @Override
    void apply(WorkspaceManager manager, WorkspaceView view) {
      manager.detach(view, bounds == null ? null : bounds.toRectangle());
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

  @Command(name = "load", description = "Restores a layout captured by 'workspace:save'.")
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
      GuiExecutor.invokeAndRethrow(
          () -> {
            // reuse the currently open views by id; unresolved descriptors are skipped
            List<WorkspaceView> pool = manager.getViews();
            manager.restore(
                snapshot,
                descriptor ->
                    pool.stream().filter(v -> v.viewId().equals(descriptor.viewId())).findFirst());
            for (WorkspaceView leftover : pool) {
              if (!manager.isShowing(leftover) && leftover instanceof ViewerPlugin<?> plugin) {
                plugin.close();
                plugin.handleFocusAfterClosing();
              }
            }
          });
      printState();
    }
  }
}
