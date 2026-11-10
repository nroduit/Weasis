/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import org.pf4j.Extension;
import org.weasis.core.api.command.AbstractVerb;
import org.weasis.core.api.command.CommandExtension;
import org.weasis.core.api.command.Since;
import org.weasis.dicom.viewer3d.gl.GlRuntime;
import org.weasis.dicom.viewer3d.gl.GlThread;
import org.weasis.dicom.viewer3d.vr.RenderingLayer;
import picocli.CommandLine.Command;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.weasis.core.api.command.CommandContext;
import org.weasis.core.api.command.CommandModel;
import org.weasis.core.api.command.OutputPath;
import org.weasis.core.api.command.PaneIndex;
import org.weasis.core.api.command.ResultSchema;
import org.weasis.core.api.command.Returns;
import org.weasis.core.api.command.ViewId;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.ui.editor.image.PaneOptions;
import org.weasis.core.ui.editor.image.ViewDisplayState;
import org.weasis.core.ui.editor.image.export.CaptureScope;
import org.weasis.core.ui.editor.image.export.ViewCapture;
import org.weasis.dicom.viewer2d.CaptureGuard;
import org.weasis.dicom.viewer3d.vr.View3d;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 * Command namespace {@code view3d}: what the volume renderer can do on this machine. It answers the
 * question every other 3D workflow depends on — whether the viewer can render at all here, and
 * through which driver.
 */
@Extension
@Command(
    name = "view3d",
    description = "Volume rendering: the capabilities of the renderer on this machine.",
    subcommands = {Viewer3dCommands.Info.class, Viewer3dCommands.Capture.class})
@Since("5.0")
public class Viewer3dCommands implements CommandExtension {

  @Override
  public String getNamespace() {
    return "view3d"; // NON-NLS
  }

  @Override
  public boolean isRelevantFor(String viewerTypeId) {
    return "3d".equals(viewerTypeId); // NON-NLS
  }

  @Override
  public Object getCommandModel() {
    return this;
  }

  /**
   * Probes the renderer exactly as the preference page does, so both report the same thing. The
   * probe is what creates the context on first use, and the crash guard of {@link View3DFactory}
   * covers it.
   */
  @Command(
      name = "capture",
      description =
          "Renders a 3D view as displayed into a PNG or JPEG file: the volume with its annotations,"
              + " or the whole layout or the application window with --scope.")
  @Since("5.0")
  static class Capture extends AbstractVerb implements Returns {
    private static final int DEFAULT_MAX_PIXELS = 2_000_000;

    @Mixin PaneOptions target;

    @Option(
        names = "--out",
        required = true,
        paramLabel = "<output-path>",
        description = "File to write; the extension selects PNG or JPEG (.jpg).")
    OutputPath out;

    @Option(names = CommandModel.FORCE, description = "Replace the file when it exists and was not written by this session.")
    boolean force;

    @Option(
        names = "--max-pixels",
        paramLabel = "<n>",
        description = "Downscale so the file has at most this many pixels (default: " + DEFAULT_MAX_PIXELS + ").")
    Integer maxPixels;

    @Option(
        names = "--scope",
        paramLabel = "<view|layout|window>",
        description = "The pane (default), the whole layout of the view, or the application window as displayed.")
    ViewCapture.Scope scope = ViewCapture.Scope.VIEW;

    @Override
    public ResultSchema result() {
      return ResultSchema.of()
          .field("target", "object", "the view and pane captured") // NON-NLS
          .field("target.view", ViewId.class, "the view") // NON-NLS
          .field("target.pane", PaneIndex.class, "the pane in its grid") // NON-NLS
          .field("target.viewerType", String.class, "3d") // NON-NLS
          .field("target.series", "series-uid", "the series rendered") // NON-NLS
          .field("file", String.class, "the file written") // NON-NLS
          .field("format", String.class, "png or jpg") // NON-NLS
          .field("width", int.class, "width of the file in pixels") // NON-NLS
          .field("height", int.class, "height of the file in pixels") // NON-NLS
          .field("scale", double.class, "the downscale applied, 1 for none") // NON-NLS
          .field("scope", String.class, "view, layout or window") // NON-NLS
          .build();
    }

    @Override
    protected void run() throws IOException {
      if (maxPixels != null && maxPixels <= 0) {
        throw invalid("--max-pixels must be positive");
      }
      int limit = maxPixels == null ? DEFAULT_MAX_PIXELS : maxPixels;
      String format;
      try {
        format = ViewCapture.formatOf(out.value());
      } catch (IllegalArgumentException e) {
        throw invalid(e.getMessage());
      }
      Path file = out.value();
      if (Files.exists(file) && !force && !CommandContext.wasWritten(file)) {
        throw invalid("The file exists: " + file + " (--force replaces it)");
      }
      record Rendered(BufferedImage image, JsonObjectBuilder target) {}
      CaptureScope captureScope = scope.toCaptureScope();
      Rendered rendered =
          GuiExecutor.invokeAndRethrow(
              () -> {
                PaneOptions.Pane pane =
                    target.resolvePane().orElseThrow(() -> invalid("No view is selected: give --view"));
                if (!(pane.view() instanceof View3d view)) {
                  throw new IllegalStateException(
                      "The pane is not a 3D view: view2d:capture renders the 2D views");
                }
                CaptureGuard.requireCapturable(
                    CaptureGuard.panesOf(captureScope, view, pane.container()));
                BufferedImage image =
                    IdentityMask.callForCommand(
                        () -> ViewCapture.render(captureScope, view, pane.container(), v -> null));
                if (image == null) {
                  throw new IllegalStateException("Nothing to capture: the " + scope + " has no size");
                }
                return new Rendered(image, ViewDisplayState.target(pane.container(), view));
              });
      double scale = ViewCapture.scaleFor(rendered.image().getWidth(), rendered.image().getHeight(), limit);
      BufferedImage result = ViewCapture.scale(rendered.image(), scale);
      if (file.getParent() != null) {
        Files.createDirectories(file.getParent());
      }
      ViewCapture.write(result, format, file);
      CommandContext.current().addFile(file);
      print(
          Json.createObjectBuilder()
              .add("target", rendered.target()) // NON-NLS
              .add("file", file.toAbsolutePath().toString()) // NON-NLS
              .add("format", format) // NON-NLS
              .add("width", result.getWidth()) // NON-NLS
              .add("height", result.getHeight()) // NON-NLS
              .add("scale", scale) // NON-NLS
              .add("scope", scope.toString()) // NON-NLS
              .build()
              .toString());
    }
  }

  @Command(
      name = "info",
      description =
          "Reports whether volume rendering is available here, the binding and the context it "
              + "runs on, the graphics adapter and the limits that decide the rendering path.")
  @Since("5.0")
  static class Info extends AbstractVerb {

    @Override
    protected void run() {
      OpenGLInfo info = View3DFactory.isOpenglEnable() ? View3DFactory.getOpenGLInfo() : null;
      // Reading it after the probe: a driver that fails it turns OpenGL off on the way out.
      boolean available = info != null && View3DFactory.isOpenglEnable();
      GlThread thread = GlRuntime.threadIfStarted();
      String context = thread == null ? null : thread.context().name();
      String description = thread == null ? null : thread.context().description();
      boolean fboForced = available && View3DFactory.isFboForced();

      print(
          isJson()
              ? json(available, context, description, info, fboForced).build().toString()
              : text(available, context, info, fboForced));
    }

    /** The report as the agents read it; pure, so its shape is pinned by a test. */
    static JsonObjectBuilder json(
        boolean available, String context, String description, OpenGLInfo info, boolean fboForced) {
      JsonObjectBuilder object = Json.createObjectBuilder().add("available", available);
      if (context != null) {
        object.add("context", context);
      }
      if (description != null) {
        object.add("description", description);
      }
      if (info == null) {
        return object;
      }
      return object
          .add("vendor", nullToEmpty(info.vendor()))
          .add("renderer", nullToEmpty(info.renderer()))
          .add("version", nullToEmpty(info.shortVersion()))
          .add("maxTextureSize3D", info.max3dTextureSize())
          .add("computeShaders", info.isComputeShaderCapable())
          .add("path", renderingPath(info, fboForced))
          .add("softwareRenderer", info.looksSoftware())
          .add("sufficient", isSufficient(info));
    }

    static String text(boolean available, String context, OpenGLInfo info, boolean fboForced) {
      StringBuilder out = new StringBuilder();
      out.append("available: ").append(available).append('\n'); // NON-NLS
      if (context != null) {
        out.append("context: ").append(context).append('\n'); // NON-NLS
      }
      if (info == null) {
        return out.append("No graphics adapter with the required OpenGL capabilities") // NON-NLS
            .toString();
      }
      out.append("renderer: ") // NON-NLS
          .append(nullToEmpty(info.vendor()))
          .append(", ")
          .append(nullToEmpty(info.renderer()))
          .append('\n');
      out.append("version: ").append(nullToEmpty(info.shortVersion())).append('\n'); // NON-NLS
      out.append("path: ").append(renderingPath(info, fboForced)).append('\n'); // NON-NLS
      out.append("maxTextureSize3D: ").append(info.max3dTextureSize()); // NON-NLS
      if (!isSufficient(info)) {
        out.append("\ncapabilities are not sufficient for volume rendering"); // NON-NLS
      }
      return out.toString();
    }

    /** The ray-caster the viewer selects: the compute shader, or the fragment-shader fallback. */
    private static String renderingPath(OpenGLInfo info, boolean fboForced) {
      return !fboForced && info.isComputeShaderCapable() ? "compute" : "fbo"; // NON-NLS
    }

    private static boolean isSufficient(OpenGLInfo info) {
      return info.isVersionCompliant()
          && !info.looksSoftware()
          && info.max3dTextureSize() >= RenderingLayer.MIN_TEXTURE_SIZE;
    }

    private static String nullToEmpty(String value) {
      return value == null ? "" : value;
    }
  }
}
