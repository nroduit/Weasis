/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.pf4j.Extension;
import org.weasis.core.api.command.AbstractVerb;
import org.weasis.core.api.command.Capability;
import org.weasis.core.api.command.CommandExtension;
import org.weasis.core.api.command.CommandFailure;
import org.weasis.core.api.command.InputPath;
import org.weasis.core.api.command.Needs;
import org.weasis.core.api.command.PaneIndex;
import org.weasis.core.api.command.Since;
import org.weasis.core.api.command.ViewId;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.ColorProfileOp;
import org.weasis.core.api.image.color.ColorManagement;
import org.weasis.core.api.image.color.IccProfile;
import org.weasis.core.api.setting.CoreSettings;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.ui.docking.ws.WorkspaceManager;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 * Command namespace {@code color}: color management of the display, the display profile and the
 * conversion a view applies.
 */
@Extension
@Command(
    name = "color",
    description = "Color management of the display: switch, display profile, view conversion.",
    subcommands = {ColorCommands.Status.class, ColorCommands.Set.class})
@Since("5.0")
public class ColorCommands implements CommandExtension {

  @Override
  public String getNamespace() {
    return "color"; // NON-NLS
  }

  @Override
  public Object getCommandModel() {
    return this;
  }

  static class PaneOptions {
    @Option(
        names = "--view",
        paramLabel = "<view-id>",
        description = "Viewer id from 'workspace:list' (default: the selected viewer).")
    ViewId viewId;

    @Option(
        names = "--pane",
        paramLabel = "<pane-index>",
        description = "Pane index in the viewer layout, from 1 (default: the selected pane).")
    PaneIndex pane;

    /** The pane, or empty when nothing is selected and no viewer was named. */
    Optional<ViewCanvas<?>> resolve() {
      ImageViewerPlugin<?> viewer;
      if (viewId == null) {
        Optional<ViewerPlugin<?>> selected = GuiUtils.getUICore().getSelectedPlugin();
        if (selected.isEmpty() || !(selected.get() instanceof ImageViewerPlugin<?> v)) {
          return Optional.empty();
        }
        viewer = v;
      } else {
        WorkspaceManager manager = GuiUtils.getUICore().getWorkspaceManager();
        viewer =
            Optional.ofNullable(manager)
                .flatMap(m -> m.findView(viewId.value()))
                .filter(ImageViewerPlugin.class::isInstance)
                .map(ImageViewerPlugin.class::cast)
                .orElseThrow(
                    () ->
                        CommandFailure.unknownHandle(
                            "Unknown image viewer id: " + viewId, "workspace:list")); // NON-NLS
      }
      if (pane == null) {
        return Optional.ofNullable(viewer.getSelectedViewCanvas());
      }
      List<? extends ViewCanvas<?>> panels = viewer.getImagePanels();
      if (pane.value() > panels.size()) {
        throw new IllegalArgumentException(
            "Pane index out of range: " + pane + " (" + panels.size() + " panes)");
      }
      return Optional.of(panels.get(pane.toOffset()));
    }
  }

  @Command(
      name = "status",
      description =
          "Whether color management is on, the display profile, and the profile and conversion of"
              + " a view.")
  static class Status extends AbstractVerb {
    @Mixin PaneOptions target;

    @Override
    protected void run() {
      print(GuiExecutor.invokeAndRethrow(() -> status(target)).toString());
    }
  }

  /** Exactly one way to choose the display profile. */
  static class DisplayChoice {
    @Option(
        names = "--display-profile",
        paramLabel = "<icc-file>",
        description = "ICC profile file (.icc, .icm) of the display of this workstation.")
    InputPath file;

    @Option(names = "--srgb", description = "No display profile: the display is sRGB.")
    boolean srgb;
  }

  @Command(
      name = "set",
      description =
          "Switches color management or sets the display profile, then prints the status.")
  @Needs(Capability.FILE_READ)
  static class Set extends AbstractVerb {
    @Option(
        names = "--enabled",
        paramLabel = "<true|false>",
        arity = "1",
        description = "Convert color images to the display profile.")
    Boolean enabled;

    @ArgGroup(exclusive = true)
    DisplayChoice display;

    @Override
    protected void run() throws IOException {
      if (enabled == null && display == null) {
        throw invalid("Give --enabled, --display-profile or --srgb");
      }
      if (enabled != null && !DisplayColor.setEnabled(enabled)) {
        throw invalid("Color management is locked by the site configuration");
      }
      if (display != null) {
        if (display.file != null) {
          DisplayColor.setDisplayProfile(display.file.value());
        } else if (!DisplayColor.resetDisplayProfile()) {
          throw invalid("The display profile is locked by the site configuration");
        }
      }
      print(GuiExecutor.invokeAndRethrow(() -> status(new PaneOptions())).toString());
    }
  }

  static JsonObject status(PaneOptions target) {
    JsonObjectBuilder body =
        Json.createObjectBuilder()
            .add("enabled", ColorManagement.isEnabled()) // NON-NLS
            .add("locked", Settings.isLocked(CoreSettings.DISPLAY_PROFILE)); // NON-NLS
    JsonObjectBuilder display = describe(ColorManagement.displayProfile());
    ColorManagement.displayProfileFile().ifPresent(f -> display.add("file", f.toString()));
    body.add("display", display); // NON-NLS
    target
        .resolve()
        .ifPresent(
            view -> {
              Object value =
                  view.getDisplayOpManager()
                      .getParamValue(ColorProfileOp.OP_NAME, ColorProfileOp.P_SOURCE_PROFILE)
                      .orElse(null);
              IccProfile source = value instanceof IccProfile p ? p : null;
              JsonObjectBuilder v =
                  Json.createObjectBuilder()
                      .add(
                          "managed",
                          view.getDisplayOpManager()
                              .getNode(ColorProfileOp.OP_NAME)
                              .isPresent()) // NON-NLS
                      .add("source", describe(source == null ? IccProfile.SRGB : source)) // NON-NLS
                      .add("declared", source != null) // NON-NLS
                      .add(
                          "conversion", // NON-NLS
                          ColorManagement.toDisplay(source).kind().name().toLowerCase(Locale.ROOT));
              body.add("view", v); // NON-NLS
            });
    return body.build();
  }

  private static JsonObjectBuilder describe(IccProfile profile) {
    return Json.createObjectBuilder()
        .add("description", profile.description().orElse("")) // NON-NLS
        .add("srgb", ColorManagement.to(profile, IccProfile.SRGB).isIdentity()) // NON-NLS
        .add("class", profile.deviceClass()) // NON-NLS
        .add("colorSpace", profile.colorSpace().strip()) // NON-NLS
        .add("size", profile.size()) // NON-NLS
        .add("id", profile.id()); // NON-NLS
  }
}
