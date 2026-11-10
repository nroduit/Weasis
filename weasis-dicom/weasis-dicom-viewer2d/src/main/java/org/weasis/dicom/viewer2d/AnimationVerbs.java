/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import org.weasis.core.api.command.CommandContext;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.MaskingProfile;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.editor.image.export.AnimationExportTask;
import org.weasis.core.ui.editor.image.export.FrameGrabber;
import org.weasis.core.ui.editor.image.export.FrameSink;
import org.weasis.core.ui.editor.image.export.FrameSinkFactory;
import org.weasis.core.ui.editor.image.export.FrameSource;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.export.DicomScSinkFactory;

/**
 * What the animation verbs share: the masking a command binds to the frames, the sink the file
 * extension names, and the synchronous run of a pull export. The dialog of the export feature does
 * the same with a form and a progress bar; a verb does it with options and a JSON answer.
 */
public final class AnimationVerbs {

  private AnimationVerbs() {}

  /**
   * The profile the frames are masked with: the AI request profile for a remote caller whatever the
   * session does, the session profile when masking is on, none otherwise, as {@code capture}.
   */
  public static MaskingProfile maskingForCommand() {
    if (CommandContext.current().isRemote()) {
      return MaskingProfile.aiRequest();
    }
    return IdentityMask.isSessionMasking() ? IdentityMask.sessionProfile() : null;
  }

  /** The extension of the file, lower case, without the dot. */
  public static String extensionOf(Path file) {
    String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
    int dot = name.lastIndexOf('.');
    return dot < 0 ? "" : name.substring(dot + 1);
  }

  /**
   * The sink the extension of {@code out} names among the formats the export offers for the series:
   * {@code .apng} or {@code .png} (APNG), {@code .gif}, {@code .dcm} (a Secondary Capture in the
   * study of the series, when a series is given).
   */
  public static FrameSinkFactory sinkFor(
      Path out, MediaSeries<DicomImageElement> series, String title) {
    String extension = extensionOf(out);
    if ("png".equals(extension)) { // NON-NLS
      extension = "apng"; // NON-NLS
    }
    for (FrameSinkFactory factory : DicomScSinkFactory.defaultSinks(series, title)) {
      if (factory.extension().equalsIgnoreCase(extension)) {
        return factory;
      }
    }
    throw new IllegalArgumentException("--out must end with .apng, .png, .gif or .dcm");
  }

  /** Refuses a file that exists and was not written by this session, unless forced. */
  public static void requireWritable(Path file, boolean force) throws IOException {
    if (Files.exists(file) && !force && !CommandContext.wasWritten(file)) {
      throw new IllegalArgumentException("The file exists: " + file + " (--force replaces it)");
    }
    if (file.getParent() != null) {
      Files.createDirectories(file.getParent());
    }
  }

  /** Milliseconds per frame at a rate in frames per second. */
  public static int frameDurationMs(double rate) {
    if (!(rate > 0) || rate > 120) {
      throw new IllegalArgumentException("--rate is between 0 and 120 frames per second");
    }
    return Math.max(1, (int) Math.round(1000.0 / rate));
  }

  /**
   * Runs a pull export to the end on the calling thread (never the event thread: the task shows
   * every frame there), releases the grabber and returns the frames written.
   */
  public static int export(FrameSource source, FrameSink sink, FrameGrabber grabber)
      throws IOException {
    AnimationExportTask task = new AnimationExportTask(source, sink, grabber.getFrameSize());
    try {
      task.execute();
      task.get();
      return source.frameCount();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      task.stop();
      throw new IllegalStateException("Interrupted while exporting the animation", e);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause() == null ? e : e.getCause();
      if (cause instanceof IOException io) {
        throw io;
      }
      throw new IllegalStateException(
          "The export failed: " + (cause.getMessage() == null ? cause : cause.getMessage()), cause);
    } finally {
      grabber.release();
    }
  }

  /** The keys every animation answer carries. */
  public static JsonObjectBuilder describe(
      Path file,
      String format,
      int frames,
      int frameDurationMs,
      FrameGrabber grabber,
      String scope) {
    return Json.createObjectBuilder()
        .add("file", file.toAbsolutePath().toString()) // NON-NLS
        .add("format", format) // NON-NLS
        .add("frames", frames) // NON-NLS
        .add("durationMs", (long) frames * frameDurationMs) // NON-NLS
        .add("frameDurationMs", frameDurationMs) // NON-NLS
        .add("width", grabber.getFrameSize().width) // NON-NLS
        .add("height", grabber.getFrameSize().height) // NON-NLS
        .add("scope", scope); // NON-NLS
  }
}
