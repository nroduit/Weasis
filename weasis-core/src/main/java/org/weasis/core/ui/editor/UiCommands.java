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

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.pf4j.Extension;
import org.weasis.core.api.command.AbstractVerb;
import org.weasis.core.api.command.CommandExtension;
import org.weasis.core.api.command.CommandFailure;
import org.weasis.core.api.command.Since;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.LayeredDocument;
import org.weasis.core.api.util.LayeredDocument.Entry;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.Shell;
import org.weasis.core.ui.docking.ShellState;
import org.weasis.core.ui.util.ToolBarContainer;
import org.weasis.core.ui.util.Toolbar;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/** The {@code ui:} namespace: the UI profiles laid over the workspace. */
@Extension
@Command(
    name = "ui",
    description =
        "The workspace around the viewers: the UI profiles (shell slots, toolbars, menus, mouse "
            + "bindings, measurement profile) that a hanging protocol or a reading step lays over "
            + "it, the overflow of what the selected view hides, and the catalog of every action "
            + "of the workspace (menu entries, toolbar buttons, tool panels). Profile ids come "
            + "from 'ui:profiles', action ids from 'ui:actions'.",
    subcommands = {
      UiCommands.Profiles.class,
      UiCommands.Profile.class,
      UiCommands.Reveal.class,
      UiCommands.Actions.class,
      UiCommands.Action.class,
      UiCommands.Context.class
    })
@Since("5.0")
public class UiCommands implements CommandExtension {

  static final String HIDDEN = "hidden"; // NON-NLS
  static final String TOOLS = "tools"; // NON-NLS
  static final String ACTIONS = "actions"; // NON-NLS

  private Supplier<Optional<? extends SeriesViewer<?>>> selectedViewer =
      () -> GuiUtils.getUICore().getSelectedPlugin();
  private Supplier<Shell> shell = Shell::current;
  private Supplier<ToolBarContainer> toolbars = () -> GuiUtils.getUICore().getToolbarContainer();

  @Override
  public String getNamespace() {
    return "ui"; // NON-NLS
  }

  @Override
  public Object getCommandModel() {
    return this;
  }

  UiCommands withSelectedViewer(Supplier<Optional<? extends SeriesViewer<?>>> supplier) {
    this.selectedViewer = Objects.requireNonNull(supplier);
    return this;
  }

  UiCommands withShell(Supplier<Shell> supplier) {
    this.shell = Objects.requireNonNull(supplier);
    return this;
  }

  UiCommands withToolbars(Supplier<ToolBarContainer> supplier) {
    this.toolbars = Objects.requireNonNull(supplier);
    return this;
  }

  /** The active profile, what the shell shows now and what the selected view hides. */
  JsonObject state() {
    JsonObjectBuilder json = Json.createObjectBuilder();
    String active = UiProfiles.activeId();
    if (active != null) {
      json.add("active", active); // NON-NLS
    }
    Shell current = shell.get();
    if (current != null) {
      json.add(ShellState.SHELL, ShellState.capture(current).toJson());
    }
    JsonObjectBuilder hidden = Json.createObjectBuilder();
    ToolBarContainer container = toolbars.get();
    if (container != null) {
      JsonArrayBuilder row = Json.createArrayBuilder();
      container.shownBars().stream().map(Toolbar::getToolbarId).forEach(row::add);
      json.add(UiProfile.TOOLBARS, row);
      JsonArrayBuilder out = Json.createArrayBuilder();
      container.hiddenBars().stream().map(Toolbar::getToolbarId).forEach(out::add);
      hidden.add(UiProfile.TOOLBARS, out);
    }
    JsonArrayBuilder tools = Json.createArrayBuilder();
    Relevance.hiddenTools(ViewContexts.current()).stream()
        .map(DockableTool::getToolId)
        .forEach(tools::add);
    json.add(HIDDEN, hidden.add(TOOLS, tools));
    return json.build();
  }

  static JsonObject describe(Entry entry, UiProfile profile) {
    JsonObjectBuilder json =
        Json.createObjectBuilder()
            .add("id", entry.id()) // NON-NLS
            .add("origin", entry.origin().name().toLowerCase(Locale.ROOT)) // NON-NLS
            .add("locked", entry.locked()) // NON-NLS
            .add("active", entry.id().equals(UiProfiles.activeId())); // NON-NLS
    if (profile.name() != null) {
      json.add(UiProfile.NAME, profile.name());
    }
    return json.build();
  }

  abstract static class Verb extends AbstractVerb {
    @ParentCommand UiCommands parent;
  }

  @Command(
      name = "profiles",
      description =
          "The UI profiles of every layer (built-in, site, user) with the one in force, and the "
              + "shell and toolbar row as they are now.")
  @Since("5.0")
  static class Profiles extends Verb {
    @Override
    protected void run() {
      JsonArrayBuilder list = Json.createArrayBuilder();
      LayeredDocument document = UiProfiles.document();
      for (Entry entry : document.entries()) {
        UiProfiles.find(entry.id()).ifPresent(p -> list.add(describe(entry, p)));
      }
      JsonObject state = GuiExecutor.invokeAndRethrow(parent::state);
      JsonObject body =
          Json.createObjectBuilder(state).add(UiProfiles.ENTRIES, list).build(); // NON-NLS
      if (isJson()) {
        print(body.toString());
        return;
      }
      StringBuilder out = new StringBuilder();
      for (JsonObject p : body.getJsonArray(UiProfiles.ENTRIES).getValuesAs(JsonObject.class)) {
        out.append(p.getBoolean("active") ? "* " : "  ") // NON-NLS
            .append(p.getString("id")) // NON-NLS
            .append(" (") // NON-NLS
            .append(p.getString("origin")) // NON-NLS
            .append(p.getBoolean("locked") ? ", locked)" : ")") // NON-NLS
            .append(p.containsKey(UiProfile.NAME) ? " " + p.getString(UiProfile.NAME) : "")
            .append('\n');
      }
      print(out.toString().stripTrailing());
    }
  }

  @Command(
      name = "profile",
      description =
          "One UI profile as its JSON entry; --apply lays it over the workspace of the selected "
              + "viewer until the next selection asks for another, --clear puts the user's own "
              + "settings back, --delete removes the entry of the user layer. After a change the "
              + "active profile, the shell and the toolbar row are returned.")
  @Since("5.0")
  static class Profile extends Verb {
    @Option(
        names = "--id",
        paramLabel = "<ui-profile-id>",
        description = "Profile id from 'profiles'.")
    String id;

    @Option(names = "--apply", description = "Lays --id over the workspace now.")
    boolean apply;

    @Option(names = "--clear", description = "Releases the active profile.")
    boolean clear;

    @Option(names = "--delete", description = "Removes --id from the user layer.")
    boolean delete;

    @Override
    protected void run() throws IOException {
      int actions = (apply ? 1 : 0) + (clear ? 1 : 0) + (delete ? 1 : 0);
      if (actions > 1) {
        throw invalid("--apply, --clear and --delete are exclusive");
      }
      if (id == null && !clear) {
        throw invalid("--id is required, except with --clear");
      }
      if (clear) {
        GuiExecutor.invokeAndRethrow(UiProfiles::release);
        print(parent.state().toString());
        return;
      }
      Entry entry =
          UiProfiles.document()
              .find(id)
              .orElseThrow(
                  () ->
                      CommandFailure.unknownHandle("Unknown UI profile id: " + id, "ui:profiles"));
      UiProfile profile =
          UiProfiles.find(id)
              .orElseThrow(
                  () -> new IllegalArgumentException("The UI profile " + id + " is invalid"));
      if (delete) {
        if (!UiProfiles.document().remove(id)) {
          throw new IllegalArgumentException("The UI profile " + id + " is not in the user layer");
        }
        print(parent.state().toString());
        return;
      }
      if (apply) {
        GuiExecutor.invokeAndRethrow(
            () -> UiProfiles.activate(profile, parent.selectedViewer.get().orElse(null)));
        print(parent.state().toString());
        return;
      }
      JsonObject json =
          Json.createObjectBuilder(describe(entry, profile))
              .add("profile", profile.toJson()) // NON-NLS
              .build();
      print(json.toString());
    }
  }

  @Command(
      name = "reveal",
      description =
          "Shows again, for the selected view only, a toolbar or a tool panel that the view's "
              + "context or the UI profile hides (the overflow 'More tools...' of the toolbar row); "
              + "the next view hides it again. The hidden ids are under 'hidden' in the state the "
              + "verbs of this namespace return.")
  @Since("5.0")
  static class Reveal extends Verb {
    @Option(
        names = "--toolbar",
        paramLabel = "<toolbar-id>",
        description = "Toolbar id from 'hidden.toolbars'.")
    String toolbar;

    @Option(
        names = "--tool",
        paramLabel = "<tool-id>",
        description = "Tool id from 'hidden.tools'.")
    String tool;

    @Override
    protected void run() {
      if ((toolbar == null) == (tool == null)) {
        throw invalid("one of --toolbar or --tool is required");
      }
      GuiExecutor.invokeAndRethrow(
          () -> {
            if (toolbar != null) {
              ToolBarContainer container = parent.toolbars.get();
              if (container == null || !container.reveal(toolbar)) {
                throw CommandFailure.unknownHandle("Unknown toolbar id: " + toolbar, "ui:profiles");
              }
            } else {
              Relevance.hiddenTools(ViewContexts.current()).stream()
                  .filter(t -> t.getToolId().equals(tool))
                  .findFirst()
                  .ifPresentOrElse(
                      Relevance::reveal,
                      () -> {
                        throw CommandFailure.unknownHandle(
                            "No hidden tool with id: " + tool, "ui:profiles");
                      });
            }
          });
      print(parent.state().toString());
    }
  }

  static JsonArrayBuilder actionsJson(List<ActionEntry> entries) {
    JsonArrayBuilder list = Json.createArrayBuilder();
    entries.forEach(entry -> list.add(entry.toJson()));
    return list;
  }

  static String actionsText(List<ActionEntry> entries) {
    StringBuilder out = new StringBuilder();
    for (ActionEntry entry : entries) {
      out.append(entry.relevant() ? "  " : "- ") // NON-NLS
          .append(entry.id())
          .append("  ")
          .append(entry.label())
          .append(" (")
          .append(entry.group())
          .append(entry.shortcut().isEmpty() ? ")" : ", " + entry.shortcut() + ")")
          .append('\n');
    }
    return out.toString().stripTrailing();
  }

  @Command(
      name = "actions",
      description =
          "The actions of the workspace as the command palette lists them: the menu entries, the "
              + "toolbar buttons and the tool panels of the selected viewer, with a stable id, the "
              + "group, the shortcut and whether the selected view shows them ('relevant'; a "
              + "hidden one still runs). In text a hidden action is marked with '-'.")
  @Since("5.0")
  static class Actions extends Verb {
    @Option(
        names = "--filter",
        paramLabel = "<text>",
        description = "Keeps the actions whose label, group or id contain every word, best first.")
    String filter;

    @Option(names = "--relevant", description = "Keeps the actions the selected view shows.")
    boolean relevant;

    @Override
    protected void run() {
      List<ActionEntry> entries =
          GuiExecutor.invokeAndRethrow(
              () -> filter == null ? ActionCatalog.entries() : ActionCatalog.search(filter));
      if (relevant) {
        entries = entries.stream().filter(ActionEntry::relevant).toList();
      }
      if (isJson()) {
        print(Json.createObjectBuilder().add(ACTIONS, actionsJson(entries)).build().toString());
      } else {
        print(actionsText(entries));
      }
    }
  }

  @Command(
      name = "action",
      description =
          "Runs one action of 'ui:actions' by id, as its menu entry, button or stripe click would, "
              + "and returns the active profile, the shell and the toolbar row after it.")
  @Since("5.0")
  static class Action extends Verb {
    @Option(
        names = "--id",
        required = true,
        paramLabel = "<action-id>",
        description = "From 'actions'.")
    String id;

    @Override
    protected void run() {
      GuiExecutor.invokeAndRethrow(
          () ->
              ActionCatalog.find(id)
                  .orElseThrow(
                      () -> CommandFailure.unknownHandle("Unknown action id: " + id, "ui:actions"))
                  .run()
                  .run());
      print(parent.state().toString());
    }
  }

  @Command(
      name = "context",
      description =
          "The context of the selected view (its facts), the UI profile in force with its entry, "
              + "the shell and the toolbar row, what the view hides, and the actions the view "
              + "shows with their action command when they have one; what an agent needs to pick "
              + "the next verb.")
  @Since("5.0")
  static class Context extends Verb {
    @Override
    protected void run() {
      JsonObject body =
          GuiExecutor.invokeAndRethrow(
              () -> {
                JsonObjectBuilder json = Json.createObjectBuilder();
                json.add("context", ViewContexts.current().toJson()); // NON-NLS
                UiProfiles.active()
                    .ifPresent(
                        profile ->
                            UiProfiles.document()
                                .find(profile.id())
                                .ifPresent(
                                    entry ->
                                        json.add(
                                            "profile", // NON-NLS
                                            Json.createObjectBuilder(describe(entry, profile))
                                                .add("profile", profile.toJson()) // NON-NLS
                                                .build())));
                parent.state().forEach(json::add);
                List<ActionEntry> relevant =
                    ActionCatalog.entries().stream().filter(ActionEntry::relevant).toList();
                json.add(ACTIONS, actionsJson(relevant));
                return json.build();
              });
      print(body.toString());
    }
  }
}
