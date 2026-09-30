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
import java.util.Locale;
import org.pf4j.Extension;
import org.weasis.core.api.command.AbstractVerb;
import org.weasis.core.api.command.CommandExtension;
import org.weasis.core.api.command.Since;
import org.weasis.dicom.viewer3d.gl.GlBackend;
import org.weasis.dicom.viewer3d.gl.GlRuntime;
import org.weasis.dicom.viewer3d.gl.GlThread;
import org.weasis.dicom.viewer3d.vr.RenderingLayer;
import picocli.CommandLine.Command;

/**
 * Command namespace {@code view3d}: what the volume renderer can do on this machine. It answers the
 * question every other 3D workflow depends on — whether the viewer can render at all here, and
 * through which driver.
 */
@Extension
@Command(
    name = "view3d",
    description = "Volume rendering: the capabilities of the renderer on this machine.",
    subcommands = {Viewer3dCommands.Info.class})
@Since("5.0")
public class Viewer3dCommands implements CommandExtension {

  @Override
  public String getNamespace() {
    return "view3d"; // NON-NLS
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
      JsonObjectBuilder object =
          Json.createObjectBuilder()
              .add("available", available)
              .add("backend", GlBackend.selected().name().toLowerCase(Locale.ROOT));
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
      out.append("backend: ") // NON-NLS
          .append(GlBackend.selected().name().toLowerCase(Locale.ROOT))
          .append('\n');
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
