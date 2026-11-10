/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.ui.gui;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Window;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.pf4j.Extension;
import org.weasis.core.api.command.AbstractVerb;
import org.weasis.core.api.command.Capability;
import org.weasis.core.api.command.CommandContext;
import org.weasis.core.api.command.CommandExtension;
import org.weasis.core.api.command.CommandManager;
import org.weasis.core.api.command.CommandPolicy;
import org.weasis.core.api.command.Needs;
import org.weasis.core.api.command.OutputPath;
import org.weasis.core.api.command.Renamed;
import org.weasis.core.api.command.ResultSchema;
import org.weasis.core.api.command.Returns;
import org.weasis.core.api.command.Since;
import org.weasis.core.api.command.Term;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.MaskingProfile;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.export.AnimatedImageSink;
import org.weasis.core.ui.editor.image.export.AnimationFormat;
import org.weasis.core.ui.editor.image.export.AnimationRecorder;
import org.weasis.core.ui.editor.image.export.CaptureScope;
import org.weasis.core.ui.editor.image.export.FrameGrabber;
import org.weasis.core.ui.editor.image.export.FrameSink;
import org.weasis.core.ui.editor.image.export.RecordingState;
import org.weasis.launcher.CommandProtocol;
import org.weasis.launcher.Prerelease;
import org.weasis.launcher.integrity.Integrity;
import org.weasis.plugin.manager.LoadedPlugins;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** Command namespace {@code weasis}: information about the application and its main window. */
@Extension
@Command(
    name = "weasis",
    description = "The application itself: version, installation and the main window.",
    subcommands = {
      WeasisCommands.Info.class,
      WeasisCommands.Show.class,
      WeasisCommands.Minimize.class,
      WeasisCommands.Quit.class,
      WeasisCommands.Feedback.class,
      WeasisCommands.Record.class
    })
public class WeasisCommands implements CommandExtension {

  @Override
  public String getNamespace() {
    return "weasis"; // NON-NLS
  }

  @Override
  public List<Term> getTerms() {
    return List.of(
        Term.of(
            "recording",
            "the display sampled into an animation file between 'weasis:record start' and 'stop';"
                + " its handle is the file it writes")); // NON-NLS
  }

  @Override
  public Object getCommandModel() {
    return this;
  }

  static Map<String, String> specifications() {
    Map<String, String> specs = new LinkedHashMap<>();
    specs.put("name", String.valueOf(AppProperties.WEASIS_NAME)); // NON-NLS
    specs.put("version", String.valueOf(AppProperties.WEASIS_VERSION)); // NON-NLS
    Prerelease build = Prerelease.of(AppProperties.WEASIS_VERSION);
    specs.put("preview", String.valueOf(build.isPreview())); // NON-NLS
    if (build.isPreview()) {
      specs.put("prerelease", build.suffix()); // NON-NLS
      specs.put("packageVersion", build.baseVersion()); // NON-NLS
    }
    specs.put("installationPath", String.valueOf(AppProperties.WEASIS_PATH)); // NON-NLS
    specs.put("tempPath", String.valueOf(AppProperties.APP_TEMP_DIR)); // NON-NLS
    specs.put("profile", String.valueOf(AppProperties.WEASIS_PROFILE)); // NON-NLS
    specs.put("user", String.valueOf(AppProperties.WEASIS_USER)); // NON-NLS
    specs.put("nativeSpecs", System.getProperty("native.library.spec", "")); // NON-NLS
    specs.put(
        "operatingSystem", // NON-NLS
        System.getProperty("os.name") // NON-NLS
            + " "
            + System.getProperty("os.version") // NON-NLS
            + " "
            + System.getProperty("os.arch")); // NON-NLS
    specs.put("javaVendor", System.getProperty("java.vendor", "")); // NON-NLS
    specs.put("javaVersion", System.getProperty("java.version", "")); // NON-NLS
    specs.put("javaHome", System.getProperty("java.home", "")); // NON-NLS
    specs.put("commandApiVersion", CommandManager.API_VERSION); // NON-NLS
    specs.put("protocolVersion", String.valueOf(CommandProtocol.VERSION)); // NON-NLS
    return specs;
  }

  @Command(
      name = "info",
      description =
          "Version and installation details, the version being one of the keys. The 4.x option -v "
              + "still answers with the version alone.")
  static class Info extends AbstractVerb {
    @Option(
        names = {"-v", "--version"},
        hidden = true)
    @Renamed("the version key of the result")
    boolean version;

    // The 4.x line "weasis:info -a" must still parse; every specification is the default.
    @Option(
        names = {"-a", "--all"},
        hidden = true)
    boolean all;

    @Option(
        names = "--plugins",
        description = "The loaded-plugins manifest: policy, lock, fingerprint and every plugin.")
    @Since("5.0")
    boolean plugins;

    @Option(
        names = "--integrity",
        description =
            "The integrity of the configuration and the plugins: the level reached (signed, "
                + "digest, none), the signing key, the entries verified and the failures.")
    @Since("5.0")
    boolean integrity;

    @Override
    protected void run() {
      if (version) {
        print(AppProperties.WEASIS_VERSION);
        return;
      }
      Map<String, String> specs = specifications();
      Integrity.Report report = integrity ? Integrity.report() : null;
      if (isJson()) {
        JsonObjectBuilder json = Json.createObjectBuilder();
        specs.forEach(json::add);
        if (report != null) {
          json.add("integrity", integrityJson(report)); // NON-NLS
        }
        if (plugins) {
          JsonArrayBuilder list = Json.createArrayBuilder();
          LoadedPlugins.current().plugins().forEach(p -> list.add(PluginCommands.json(p)));
          json.add("plugins", PluginCommands.policyJson().add("plugins", list)); // NON-NLS
        }
        CommandPolicy policy = CommandContext.current().policy();
        org.weasis.core.api.command.JsonWriter out = new org.weasis.core.api.command.JsonWriter();
        policy.write(out);
        try (JsonReader reader = Json.createReader(new StringReader(out.toString()))) {
          json.add("policy", reader.readObject()); // NON-NLS
        }
        json.add("policyLocked", policy.locked()); // NON-NLS
        CommandManager manager = CommandManager.current();
        if (manager != null) {
          json.add("policySource", manager.policySource()); // NON-NLS
        }
        print(json.build().toString());
      } else {
        specs.forEach((k, v) -> print("  " + k + ": " + v));
        if (report != null) {
          print(
              "  integrity: "
                  + report.level().key() // NON-NLS
                  + (report.keyId() == null ? "" : " (key " + report.keyId() + ")")
                  + ", "
                  + report.verified()
                  + "/"
                  + report.entries()
                  + " verified");
          report.failures().forEach(f -> print("    failed: " + f)); // NON-NLS
        }
        CommandPolicy policy = CommandContext.current().policy();
        CommandManager manager = CommandManager.current();
        print(
            "  policy: remote " // NON-NLS
                + (policy.remote().enabled()
                    ? String.join(",", policy.remote().namespaces())
                    : "closed") // NON-NLS
                + ", network read "
                + (policy.network().read() ? "allow" : "refuse") // NON-NLS
                + ", write "
                + (policy.network().write() ? "allow" : "refuse") // NON-NLS
                + ", write roots "
                + policy.files().writeRoots()
                + (policy.locked() ? " (locked)" : "") // NON-NLS
                + (manager == null ? "" : ", from " + manager.policySource())); // NON-NLS
        if (plugins) {
          LoadedPlugins manifest = LoadedPlugins.current();
          print(
              "  plugins: policy "
                  + (manifest.policy() == null ? "" : manifest.policy().key()) // NON-NLS
                  + (manifest.locked() ? " (locked)" : "")
                  + ", fingerprint "
                  + manifest.fingerprint());
          manifest
              .plugins()
              .forEach(
                  p ->
                      print(
                          "    "
                              + p.id()
                              + "@"
                              + p.version()
                              + " "
                              + p.source()
                              + " "
                              + p.tier().key()
                              + " "
                              + p.state()));
        }
      }
    }

    static JsonObjectBuilder integrityJson(Integrity.Report report) {
      JsonObjectBuilder json =
          Json.createObjectBuilder()
              .add("level", report.level().key()) // NON-NLS
              .add("entries", report.entries()) // NON-NLS
              .add("verified", report.verified()) // NON-NLS
              .add("lowered", report.lowered()); // NON-NLS
      if (report.keyId() != null) {
        json.add("keyId", report.keyId()); // NON-NLS
      }
      if (report.source() != null) {
        json.add("source", report.source()); // NON-NLS
      }
      JsonArrayBuilder failures = Json.createArrayBuilder();
      report.failures().forEach(failures::add);
      return json.add("failures", failures); // NON-NLS
    }
  }

  /** Exclusive window actions. */
  static class WindowAction {
    @Option(
        names = {"-q", "--quit"},
        description = "Shut Weasis down.")
    boolean quit;

    @Option(
        names = {"-v", "--visible"},
        description = "Bring the main window to the front.")
    boolean visible;

    @Option(
        names = {"-m", "--minimized"},
        description = "Minimize the main window.")
    boolean minimized;
  }

  /** Shared body of the window verbs: apply, then answer with the state of the main window. */
  abstract static class WindowVerb extends AbstractVerb {
    private static final Duration QUIT_GRACE = Duration.ofMillis(300);

    @Override
    protected void run() {
      print(GuiExecutor.invokeAndRethrow(() -> applyAndDescribe(action())));
    }

    /** What this verb does to the window; null only reports its state. */
    abstract WindowAction action();

    /** Ends the application once the reply is on its way. */
    void quit() {
      // The reply is written by the socket handler after this verb returns: give it a moment
      // before the JVM goes down, so the caller sees the status instead of a closed connection.
      Thread.ofVirtual()
          .start(
              () -> {
                try {
                  Thread.sleep(QUIT_GRACE.toMillis());
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                System.exit(0);
              });
      print(
          isJson()
              ? Json.createObjectBuilder().add("quitting", true).build().toString() // NON-NLS
              : "quitting"); // NON-NLS
    }

    String applyAndDescribe(WindowAction action) {
      Window window = GuiUtils.getUICore().getApplicationWindow();
      if (!(window instanceof Frame frame)) {
        throw new IllegalStateException("No main window");
      }
      if (action != null) {
        if (action.minimized) {
          frame.setState(Frame.ICONIFIED);
        } else {
          WeasisWin.bringToFront(frame);
        }
      }
      String state = windowState(frame);
      if (isJson()) {
        Rectangle bounds = frame.getBounds();
        return Json.createObjectBuilder()
            .add("state", state) // NON-NLS
            .add("visible", frame.isVisible()) // NON-NLS
            .add("focused", frame.isFocused()) // NON-NLS
            .add(
                "bounds", // NON-NLS
                Json.createObjectBuilder()
                    .add("x", bounds.x)
                    .add("y", bounds.y)
                    .add("width", bounds.width) // NON-NLS
                    .add("height", bounds.height)) // NON-NLS
            .build()
            .toString();
      }
      return "state: " + state; // NON-NLS
    }

    private static String windowState(Frame frame) {
      if (!frame.isVisible()) {
        return "hidden"; // NON-NLS
      }
      if ((frame.getExtendedState() & Frame.ICONIFIED) != 0) {
        return "minimized"; // NON-NLS
      }
      return (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH
          ? "maximized" // NON-NLS
          : "normal"; // NON-NLS
    }
  }

  @Command(
      name = "show",
      description = "Brings the main window to the front and returns its state.")
  @Since("5.0")
  static class Show extends WindowVerb {
    @Override
    WindowAction action() {
      WindowAction action = new WindowAction();
      action.visible = true;
      return action;
    }
  }

  @Command(
      name = "feedback",
      description =
          "The feedback channel of a preview: the link to the issue form of the 5.0 preview with "
              + "the version and the platform of this instance filled in, and the discussions "
              + "category for questions; --open opens the form in the browser.")
  @Since("5.0")
  static class Feedback extends AbstractVerb implements Returns {
    @Option(names = "--open", description = "Open the form in the default browser.")
    boolean open;

    @Option(
        names = "--feature",
        paramLabel = "<text>",
        description = "The feature of the finding, preselected in the form.")
    String feature;

    @Override
    protected void run() {
      String platform = FeedbackLink.platform();
      String url = FeedbackLink.url(AppProperties.WEASIS_VERSION, platform, feature);
      Prerelease build = Prerelease.of(AppProperties.WEASIS_VERSION);
      if (open) {
        GuiExecutor.execute(() -> GuiUtils.openInDefaultBrowser(null, java.net.URI.create(url)));
      }
      if (isJson()) {
        JsonObjectBuilder json =
            Json.createObjectBuilder()
                .add("url", url) // NON-NLS
                .add("discussions", FeedbackLink.DISCUSSIONS) // NON-NLS
                .add("version", AppProperties.WEASIS_VERSION) // NON-NLS
                .add("preview", build.isPreview()) // NON-NLS
                .add("platform", platform) // NON-NLS
                .add("opened", open); // NON-NLS
        print(json.build().toString());
      } else {
        print(url);
        print(FeedbackLink.DISCUSSIONS);
      }
    }

    @Override
    public ResultSchema result() {
      return ResultSchema.of()
          .field("url", String.class, "The issue form with the version and the platform filled in")
          .field("discussions", String.class, "The discussions category of the preview")
          .field("version", String.class, "The version of this instance")
          .field("preview", Boolean.class, "Whether this instance is a preview build")
          .field("platform", String.class, "Operating system, architecture and monitors")
          .field("opened", Boolean.class, "Whether the form was opened in the browser")
          .build();
    }
  }

  /** The recording a command started, one at a time, until it is stopped. */
  record Recording(
      AnimationRecorder recorder, Path file, String format, String scope, long started) {}

  private static final java.util.concurrent.atomic.AtomicReference<Recording> RECORDING =
      new java.util.concurrent.atomic.AtomicReference<>();

  @Command(
      name = "record",
      description =
          "Records the display as an animation, as the Record action does: 'start' samples the"
              + " layout of the selected viewer (or the window with --scope window) into the file"
              + " of --out at --rate frames per second until 'stop' or --max-seconds, and answers"
              + " a recording handle; 'stop' closes the file and answers the frames written and the"
              + " duration. Frames are masked as a capture from the same caller would be.")
  @Since("5.0")
  static class Record extends AbstractVerb implements Returns {
    enum Step {
      START,
      STOP
    }

    @Parameters(index = "0", paramLabel = "<start|stop>", description = "start, stop.")
    Step step;

    @Option(
        names = "--out",
        paramLabel = "<output-path>",
        description = "File to write (start); the extension selects APNG (.apng, .png) or GIF.")
    OutputPath out;

    @Option(
        names = "--force",
        description = "Replace the file when it exists and was not written by this session.")
    boolean force;

    @Option(
        names = "--scope",
        paramLabel = "<layout|window>",
        description =
            "What is recorded: the layout of the selected viewer (default) or the window.")
    String scope = "layout"; // NON-NLS

    @Option(
        names = "--rate",
        paramLabel = "<fps>",
        description = "Samples per second (default: 12).")
    double rate = 12;

    @Option(
        names = "--max-seconds",
        paramLabel = "<s>",
        description = "The recording stops by itself after this long, 1 to 600 (default: 60).")
    int maxSeconds = 60;

    @Option(
        names = "--scale",
        paramLabel = "<factor>",
        description = "Size of the frames against the display, 0.1 to 1 (default: 0.5).")
    double scale = 0.5;

    @Option(names = "--pointer", description = "Draw the mouse pointer on the frames.")
    boolean pointer;

    @Override
    public ResultSchema result() {
      return ResultSchema.of()
          .field(
              "recording",
              String.class,
              "the handle of the recording: the file it writes") // NON-NLS
          .field("file", String.class, "the file written") // NON-NLS
          .field("format", String.class, "apng or gif") // NON-NLS
          .field("scope", String.class, "layout or window") // NON-NLS
          .field("frames", int.class, "frames written (stop)") // NON-NLS
          .field("durationMs", long.class, "length of the recording (stop)") // NON-NLS
          .field("width", int.class, "width of a frame") // NON-NLS
          .field("height", int.class, "height of a frame") // NON-NLS
          .field("recording", "boolean", "whether a recording is under way") // NON-NLS
          .build();
    }

    @Override
    protected void run() throws IOException {
      if (step == Step.STOP) {
        stop();
        return;
      }
      if (out == null) {
        throw invalid("--out is required to start a recording");
      }
      if (!(rate > 0) || rate > 60) {
        throw invalid("--rate is between 0 and 60 samples per second");
      }
      if (maxSeconds < 1 || maxSeconds > 600) {
        throw invalid("--max-seconds is between 1 and 600");
      }
      if (scale <= 0 || scale > 1) {
        throw invalid("--scale is between 0.1 and 1");
      }
      CaptureScope captureScope =
          switch (scope.toLowerCase(java.util.Locale.ROOT)) {
            case "layout" -> CaptureScope.CONTAINER; // NON-NLS
            case "window" -> CaptureScope.APPLICATION_WINDOW; // NON-NLS
            default -> throw invalid("--scope is layout or window, not '" + scope + "'");
          };
      Path file = out.value();
      String extension = extensionOf(file);
      AnimationFormat format =
          switch (extension) {
            case "apng", "png" -> AnimationFormat.APNG; // NON-NLS
            case "gif" -> AnimationFormat.GIF; // NON-NLS
            default -> throw invalid("--out must end with .apng, .png or .gif");
          };
      if (Files.exists(file) && !force && !CommandContext.wasWritten(file)) {
        throw invalid("The file exists: " + file + " (--force replaces it)");
      }
      if (RECORDING.get() != null || RecordingState.isRecording()) {
        throw new IllegalStateException("A recording is already under way: stop it first");
      }
      if (file.getParent() != null) {
        Files.createDirectories(file.getParent());
      }
      MaskingProfile profile =
          CommandContext.current().isRemote()
              ? MaskingProfile.aiRequest()
              : IdentityMask.isSessionMasking() ? IdentityMask.sessionProfile() : null;
      int intervalMs = Math.max(1, (int) Math.round(1000.0 / rate));
      Recording recording =
          GuiExecutor.invokeAndRethrow(
              () -> {
                ImageViewerPlugin<?> container =
                    GuiUtils.getUICore().getSelectedPlugin().orElse(null)
                            instanceof ImageViewerPlugin<?> c
                        ? c
                        : null;
                if (container == null && captureScope == CaptureScope.CONTAINER) {
                  throw new IllegalStateException("No image viewer is selected: nothing to record");
                }
                ViewCanvas<?> view = container == null ? null : container.getSelectedViewCanvas();
                FrameGrabber grabber =
                    new FrameGrabber(captureScope, view, container, profile, scale);
                grabber.setPointerVisible(pointer);
                grabber.setFollowSelection(true);
                grabber.prepare();
                FrameSink sink;
                try {
                  sink = AnimatedImageSink.factory(format).create(file, profile);
                } catch (IOException e) {
                  grabber.release();
                  throw new IllegalStateException(
                      "Cannot create " + file + ": " + e.getMessage(), e);
                }
                AnimationRecorder recorder =
                    new AnimationRecorder(
                        grabber,
                        sink,
                        intervalMs,
                        maxSeconds * 1000L,
                        (int) Math.ceil(maxSeconds * rate));
                if (!recorder.start()) {
                  sink.abort();
                  throw new IllegalStateException(
                      "A recording is already under way: stop it first");
                }
                Recording started =
                    new Recording(
                        recorder,
                        file,
                        format.getExtension(),
                        captureScope == CaptureScope.CONTAINER ? "layout" : "window", // NON-NLS
                        System.currentTimeMillis());
                RECORDING.set(started);
                return started;
              });
      CommandContext.current().addFile(file);
      print(
          Json.createObjectBuilder()
              .add("recording", recording.file().toAbsolutePath().toString()) // NON-NLS
              .add("file", recording.file().toAbsolutePath().toString()) // NON-NLS
              .add("format", recording.format()) // NON-NLS
              .add("scope", recording.scope()) // NON-NLS
              .add("maxSeconds", maxSeconds) // NON-NLS
              .build()
              .toString());
    }

    private void stop() {
      Recording recording = RECORDING.getAndSet(null);
      if (recording == null) {
        throw new IllegalStateException("No recording started by a command is under way");
      }
      GuiExecutor.invokeAndRethrow(
          () -> {
            recording.recorder().stop();
            return null;
          });
      int frames;
      try {
        frames = recording.recorder().completion().get(2, java.util.concurrent.TimeUnit.MINUTES);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while the recording was closing", e);
      } catch (java.util.concurrent.ExecutionException e) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        throw new IllegalStateException("The recording failed: " + cause.getMessage(), cause);
      } catch (java.util.concurrent.TimeoutException e) {
        throw new IllegalStateException("The recording did not close in two minutes", e);
      }
      print(
          Json.createObjectBuilder()
              .add("recording", recording.file().toAbsolutePath().toString()) // NON-NLS
              .add("file", recording.file().toAbsolutePath().toString()) // NON-NLS
              .add("format", recording.format()) // NON-NLS
              .add("scope", recording.scope()) // NON-NLS
              .add("frames", frames) // NON-NLS
              .add("durationMs", System.currentTimeMillis() - recording.started()) // NON-NLS
              .build()
              .toString());
    }

    private static String extensionOf(Path file) {
      String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
      int dot = name.lastIndexOf('.');
      return dot < 0 ? "" : name.substring(dot + 1);
    }
  }

  @Command(name = "minimize", description = "Minimizes the main window and returns its state.")
  @Since("5.0")
  static class Minimize extends WindowVerb {
    @Override
    WindowAction action() {
      WindowAction action = new WindowAction();
      action.minimized = true;
      return action;
    }
  }

  @Command(
      name = "quit",
      description =
          "Shuts Weasis down and returns once the reply is written; a remote caller confirms with "
              + "-y, and the instance may forbid it.")
  @Since("5.0")
  @Needs(Capability.SHUTDOWN)
  static class Quit extends WindowVerb {
    @Override
    protected void run() {
      quit();
    }

    @Override
    WindowAction action() {
      return null;
    }
  }
}
