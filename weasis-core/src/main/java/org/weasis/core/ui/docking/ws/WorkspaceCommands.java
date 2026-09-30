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

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import java.awt.Rectangle;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.command.CommandExtension;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshotIO;
import org.weasis.core.ui.editor.image.ViewerPlugin;

/**
 * Command namespace {@code workspace}: scripting and AI/MCP access to the workspace engine. Inputs
 * and outputs are JSON-friendly so the verbs map one-to-one to future MCP tools; view ids are the
 * stable {@link WorkspaceView#viewId()} exposed by {@code workspace:list}.
 */
@Extension
public class WorkspaceCommands implements CommandExtension {
  private static final Logger LOGGER = LoggerFactory.getLogger(WorkspaceCommands.class);

  @Override
  public String getNamespace() {
    return "workspace"; // NON-NLS
  }

  @Override
  public String getHelp() {
    return """
        workspace commands:
          workspace:list                                    print windows/groups/views as JSON
          workspace:select|close|maximize|restore --view ID act on one view
          workspace:stack --view ID --anchor ID             stack a view onto another
          workspace:split --view ID --anchor ID --side right|left|top|bottom [--ratio 0.5]
          workspace:detach --view ID [--bounds x,y,w,h]     float a view in its own window
          workspace:snapshot [--file PATH]                  capture the layout to JSON
          workspace:layout --file PATH                      restore a captured layout
        """; // NON-NLS
  }

  @Override
  public void execute(String commandName, String[] args) throws Exception {
    WorkspaceManager manager = GuiUtils.getUICore().getWorkspaceManager();
    if (manager == null) {
      LOGGER.warn("No workspace available (headless)");
      return;
    }
    switch (commandName) {
      case "list" -> print(list(manager)); // NON-NLS
      case "snapshot" -> snapshot(manager, option(args, "file")); // NON-NLS
      case "layout" -> layout(manager, option(args, "file")); // NON-NLS
      case "select" -> withView(manager, args, v -> GuiExecutor.execute(() -> manager.select(v)));
      case "close" -> // NON-NLS
          withView(
              manager,
              args,
              v ->
                  GuiExecutor.execute(
                      () -> {
                        if (v.isClosable()) {
                          v.requestClose();
                        }
                      }));
      case "maximize" -> // NON-NLS
          withView(manager, args, v -> GuiExecutor.execute(() -> manager.maximize(v)));
      case "restore" -> // NON-NLS
          withView(manager, args, v -> GuiExecutor.execute(() -> manager.restore(v)));
      case "detach" -> // NON-NLS
          withView(
              manager,
              args,
              v -> GuiExecutor.execute(() -> manager.detach(v, bounds(option(args, "bounds")))));
      case "stack" -> { // NON-NLS
        WorkspaceView view = requireView(manager, option(args, "view"));
        WorkspaceView anchor = requireView(manager, option(args, "anchor"));
        GuiExecutor.execute(() -> manager.stack(view, anchor));
      }
      case "split" -> { // NON-NLS
        WorkspaceView view = requireView(manager, option(args, "view"));
        WorkspaceView anchor = requireView(manager, option(args, "anchor"));
        DockSide side = DockSide.valueOf(option(args, "side").toUpperCase(Locale.ROOT)); // NON-NLS
        String ratio = option(args, "ratio"); // NON-NLS
        double r = ratio == null ? 0.5 : Double.parseDouble(ratio);
        GuiExecutor.execute(() -> manager.split(view, anchor, side, r));
      }
      default -> print(getHelp());
    }
  }

  private static String list(WorkspaceManager manager) {
    StringBuilder out = new StringBuilder();
    GuiExecutor.invokeAndWait(
        () -> {
          JsonArrayBuilder windows = Json.createArrayBuilder();
          for (WorkspaceSnapshot.WindowSnapshot win : manager.capture().windows()) {
            JsonObjectBuilder w = Json.createObjectBuilder().add("main", win.main());
            JsonArrayBuilder groups = Json.createArrayBuilder();
            appendGroups(win.root(), groups);
            w.add("groups", groups);
            windows.add(w);
          }
          out.append(Json.createObjectBuilder().add("windows", windows).build());
        });
    return out.toString();
  }

  private static void appendGroups(WorkspaceSnapshot.Node node, JsonArrayBuilder groups) {
    switch (node) {
      case WorkspaceSnapshot.SplitNode(var v, var r, var first, var second) -> {
        appendGroups(first, groups);
        appendGroups(second, groups);
      }
      case WorkspaceSnapshot.GroupNode(List<WorkspaceSnapshot.ViewDescriptor> views, int sel) -> {
        JsonArrayBuilder array = Json.createArrayBuilder();
        for (int i = 0; i < views.size(); i++) {
          WorkspaceSnapshot.ViewDescriptor d = views.get(i);
          array.add(
              Json.createObjectBuilder()
                  .add("id", d.viewId())
                  .add("name", d.state().getString("name", "")) // NON-NLS
                  .add("selected", i == sel));
        }
        groups.add(Json.createObjectBuilder().add("views", array));
      }
      case null -> {
        // empty window
      }
    }
  }

  private void snapshot(WorkspaceManager manager, String file) throws Exception {
    WorkspaceSnapshot[] holder = new WorkspaceSnapshot[1];
    GuiExecutor.invokeAndWait(() -> holder[0] = manager.capture());
    if (file != null) {
      WorkspaceSnapshotIO.write(holder[0], Path.of(file));
      LOGGER.info("Workspace snapshot written to {}", file);
    } else {
      print(WorkspaceSnapshotIO.toJson(holder[0]).toString());
    }
  }

  private void layout(WorkspaceManager manager, String file) throws Exception {
    if (file == null) {
      print(getHelp());
      return;
    }
    WorkspaceSnapshot snapshot = WorkspaceSnapshotIO.read(Path.of(file));
    GuiExecutor.execute(
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
  }

  private void withView(WorkspaceManager manager, String[] args, ViewAction action) {
    action.accept(requireView(manager, option(args, "view"))); // NON-NLS
  }

  private static WorkspaceView requireView(WorkspaceManager manager, String id) {
    return find(manager, id)
        .orElseThrow(() -> new IllegalArgumentException("Unknown view id: " + id));
  }

  private static Optional<WorkspaceView> find(WorkspaceManager manager, String id) {
    return manager.getViews().stream().filter(v -> v.viewId().equals(id)).findFirst();
  }

  /** Returns the value following {@code --name}, or null. */
  private static String option(String[] args, String name) {
    String key = "--" + name;
    for (int i = 0; i < args.length - 1; i++) {
      if (key.equals(args[i])) {
        return args[i + 1];
      }
    }
    return null;
  }

  private static Rectangle bounds(String value) {
    if (value == null) {
      return null;
    }
    String[] parts = value.split(",");
    return parts.length == 4
        ? new Rectangle(
            Integer.parseInt(parts[0].trim()),
            Integer.parseInt(parts[1].trim()),
            Integer.parseInt(parts[2].trim()),
            Integer.parseInt(parts[3].trim()))
        : null;
  }

  private static void print(String text) {
    System.out.println(text); // NOSONAR command console output
  }

  @FunctionalInterface
  private interface ViewAction {
    void accept(WorkspaceView view);
  }
}
