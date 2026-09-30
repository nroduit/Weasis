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
import jakarta.json.JsonObjectBuilder;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Window;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.pf4j.Extension;
import org.weasis.core.api.command.AbstractVerb;
import org.weasis.core.api.command.Capability;
import org.weasis.core.api.command.CommandExtension;
import org.weasis.core.api.command.CommandManager;
import org.weasis.core.api.command.Needs;
import org.weasis.core.api.command.Renamed;
import org.weasis.core.api.command.Since;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.launcher.CommandProtocol;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** Command namespace {@code weasis}: information about the application and its main window. */
@Extension
@Command(
    name = "weasis",
    description = "The application itself: version, installation and the main window.",
    subcommands = {
      WeasisCommands.Info.class,
      WeasisCommands.Show.class,
      WeasisCommands.Minimize.class,
      WeasisCommands.Quit.class
    })
public class WeasisCommands implements CommandExtension {

  @Override
  public String getNamespace() {
    return "weasis"; // NON-NLS
  }

  @Override
  public Object getCommandModel() {
    return this;
  }

  static Map<String, String> specifications() {
    Map<String, String> specs = new LinkedHashMap<>();
    specs.put("name", String.valueOf(AppProperties.WEASIS_NAME)); // NON-NLS
    specs.put("version", String.valueOf(AppProperties.WEASIS_VERSION)); // NON-NLS
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

    @Override
    protected void run() {
      if (version) {
        print(AppProperties.WEASIS_VERSION);
        return;
      }
      Map<String, String> specs = specifications();
      if (isJson()) {
        JsonObjectBuilder json = Json.createObjectBuilder();
        specs.forEach(json::add);
        print(json.build().toString());
      } else {
        specs.forEach((k, v) -> print("  " + k + ": " + v));
      }
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
