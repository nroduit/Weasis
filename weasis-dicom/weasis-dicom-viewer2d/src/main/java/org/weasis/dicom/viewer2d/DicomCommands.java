/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.command.CommandExtension;
import org.weasis.core.api.command.Option;
import org.weasis.core.api.command.Options;
import org.weasis.core.api.gui.layout.MigLayoutModel;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.SynchEvent;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.model.utils.bean.PanPoint;
import org.weasis.dicom.codec.DicomImageElement;

@Extension
public class DicomCommands implements CommandExtension {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomCommands.class);

  private static final String ZOOM_USAGE =
      """
      Change the zoom value of the selected image
      Usage: dcmview2d:zoom (set VALUE | increase NUMBER | decrease NUMBER)
        -s --set=VALUE        [decimal value]  set a new value from 0.0 to 12.0 (zoom magnitude, 0.0 => default, -200.0 => best fit, -100.0 => real size)
        -i --increase=NUMBER  increase of some amount
        -d --decrease=NUMBER  decrease of some amount
        -? --help             show help
      """;

  private static final String WL_USAGE =
      """
      Change the window/level values of the selected image (increase or decrease into a normalized range of 4096)
      Usage: dcmview2d:wl -- WIN LEVEL
      WIN and LEVEL are Integer. It is mandatory to have '--' (end of options) for negative values
        -? --help       show help
      """;

  private static final String MOVE_USAGE =
      """
      Pan the selected image
      Usage: dcmview2d:move -- X Y
      X and Y are Integer. It is mandatory to have '--' (end of options) for negative values
        -? --help       show help
      """;

  private static final String SCROLL_USAGE =
      """
      Scroll into the images of the selected series
      Usage: dcmview2d:scroll ( -s NUMBER | -i NUMBER | -d NUMBER)
        -s --set=NUMBER       set a new value from 1 to series size
        -i --increase=NUMBER  increase of some amount
        -d --decrease=NUMBER  decrease of some amount
        -? --help             show help
      """;

  private static final String LAYOUT_USAGE =
      """
      Select a split-screen layout
      Usage: dcmview2d:layout ( -n NUMBER | -i ID )
        -n --number=NUMBER  select the best matching number of views
        -i --id=ID          select the layout from its identifier
        -? --help           show help
      """;

  private static final String MOUSE_LEFT_ACTION_USAGE =
      """
      Change the mouse left action
      Usage: dcmview2d:mouseLeftAction COMMAND
      COMMAND is (sequence|winLevel|zoom|pan|rotation|crosshair|measure|draw|contextMenu|none)
        -? --help       show help
      """;

  private static final String RESET_USAGE =
      """
      Reset image display
      Usage: dcmview2d:reset (-a | COMMAND...)
      COMMAND is (winLevel|zoom|pan|rotation)
        -a --all        reset to original display
        -? --help       show help
      """;

  @Override
  public String getNamespace() {
    return "dcmview2d";
  }

  @Override
  public void execute(String commandName, String[] args) throws Exception {
    switch (commandName) {
      case "zoom":
        zoom(args);
        break;
      case "wl":
        wl(args);
        break;
      case "move":
        move(args);
        break;
      case "scroll":
        scroll(args);
        break;
      case "layout":
        layout(args);
        break;
      case "mouseLeftAction":
        mouseLeftAction(args);
        break;
      case "reset":
        reset(args);
        break;
      default:
        throw new IllegalArgumentException("Unknown command: " + commandName);
    }
  }

  @Override
  public String getHelp() {
    return String.join("\n", ZOOM_USAGE)
        + "\n\n"
        + String.join("\n", WL_USAGE)
        + "\n\n"
        + String.join("\n", MOVE_USAGE)
        + "\n\n"
        + String.join("\n", SCROLL_USAGE)
        + "\n\n"
        + String.join("\n", LAYOUT_USAGE)
        + "\n\n"
        + String.join("\n", MOUSE_LEFT_ACTION_USAGE)
        + "\n\n"
        + String.join("\n", RESET_USAGE);
  }

  /******* Command Line Functions ********/

  public void zoom(String[] argv) throws IOException {
    final Option opt = Options.compile(ZOOM_USAGE).parse(argv);
    final List<String> args = opt.args();

    if (opt.isSet("help") // NON-NLS
        || !opt.isOnlyOneOptionActivate("set", "increase", "decrease")) { // NON-NLS
      opt.usage();
      return;
    }

    GuiExecutor.execute(() -> zoomCommand(opt, args));
  }

  private void zoomCommand(Option opt, List<String> args) {
    try {
      EventManager e = EventManager.getInstance();
      Optional<SliderChangeListener> zoomAction = e.getAction(ActionW.ZOOM);
      if (zoomAction.isPresent()) {
        if (opt.isSet("increase")) { // NON-NLS
          zoomAction
              .get()
              .setSliderValue(
                  zoomAction.get().getSliderValue() + opt.getNumber("increase")); // NON-NLS
        } else if (opt.isSet("decrease")) { // NON-NLS
          zoomAction
              .get()
              .setSliderValue(
                  zoomAction.get().getSliderValue() - opt.getNumber("decrease")); // NON-NLS
        } else if (opt.isSet("set")) { // NON-NLS
          double val3 = Double.parseDouble(opt.get("set")); // NON-NLS
          if (val3 <= 0.0) {
            e.firePropertyChange(
                ActionW.SYNCH.cmd(),
                null,
                new SynchEvent(e.getSelectedViewPane(), ActionW.ZOOM.cmd(), val3));
          } else {
            zoomAction.get().setRealValue(val3);
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error("Zoom command: {}", args.isEmpty() ? "" : args.get(0), e);
    }
  }

  public void wl(String[] argv) throws IOException {
    final Option opt = Options.compile(WL_USAGE).parse(argv);
    final List<String> args = opt.args();

    if (opt.isSet("help") || args.size() != 2) { // NON-NLS
      opt.usage();
      return;
    }

    GuiExecutor.execute(
        () -> {
          try {
            EventManager e = EventManager.getInstance();
            Optional<SliderChangeListener> windowAction = e.getAction(ActionW.WINDOW);
            Optional<SliderChangeListener> levelAction = e.getAction(ActionW.LEVEL);
            if (windowAction.isPresent() && levelAction.isPresent()) {
              int win = windowAction.get().getSliderValue() + Integer.parseInt(args.get(0));
              int level = levelAction.get().getSliderValue() + Integer.parseInt(args.get(1));
              windowAction.get().setSliderValue(win);
              levelAction.get().setSliderValue(level);
            }
          } catch (Exception e) {
            LOGGER.error("Window/level command: {} {}", args.get(0), args.get(1), e);
          }
        });
  }

  public void move(String[] argv) throws IOException {
    final Option opt = Options.compile(MOVE_USAGE).parse(argv);
    final List<String> args = opt.args();

    if (opt.isSet("help") || args.size() != 2) { // NON-NLS
      opt.usage();
      return;
    }

    GuiExecutor.execute(
        () -> {
          try {
            EventManager e = EventManager.getInstance();
            int valx = Integer.parseInt(args.get(0));
            int valy = Integer.parseInt(args.get(1));
            e.getAction(ActionW.PAN)
                .ifPresent(a -> a.setPoint(new PanPoint(PanPoint.State.MOVE, valx, valy)));

          } catch (Exception e) {
            LOGGER.error("Move (x,y) command: {} {}", args.get(0), args.get(1), e);
          }
        });
  }

  public void scroll(String[] argv) throws IOException {
    final Option opt = Options.compile(SCROLL_USAGE).parse(argv);

    if (opt.isSet("help") // NON-NLS
        || !opt.isOnlyOneOptionActivate("set", "increase", "decrease")) { // NON-NLS
      opt.usage();
      return;
    }

    GuiExecutor.execute(
        () -> {
          try {
            EventManager e = EventManager.getInstance();
            Optional<SliderCineListener> cineAction = e.getAction(ActionW.SCROLL_SERIES);
            if (cineAction.isPresent() && cineAction.get().isActionEnabled()) {
              SliderCineListener moveTroughSliceAction = cineAction.get();
              if (opt.isSet("increase")) { // NON-NLS
                moveTroughSliceAction.setSliderValue(
                    moveTroughSliceAction.getSliderValue() + opt.getNumber("increase")); // NON-NLS
              } else if (opt.isSet("decrease")) { // NON-NLS
                moveTroughSliceAction.setSliderValue(
                    moveTroughSliceAction.getSliderValue() - opt.getNumber("decrease")); // NON-NLS
              } else if (opt.isSet("set")) { // NON-NLS
                moveTroughSliceAction.setSliderValue(opt.getNumber("set")); // NON-NLS
              }
            }
          } catch (Exception e) {
            LOGGER.error("Scroll command error:", e);
          }
        });
  }

  public void layout(String[] argv) throws IOException {
    final Option opt = Options.compile(LAYOUT_USAGE).parse(argv);

    if (opt.isSet("help") || !opt.isOnlyOneOptionActivate("number", "id")) { // NON-NLS
      opt.usage();
      return;
    }
    GuiExecutor.execute(
        () -> {
          try {
            EventManager e = EventManager.getInstance();
            ImageViewerPlugin<DicomImageElement> selectedView2dContainer =
                e.getSelectedView2dContainer();
            if (opt.isSet("number")) { // NON-NLS
              if (selectedView2dContainer != null) {
                MigLayoutModel val1 =
                    selectedView2dContainer.getBestDefaultViewLayout(
                        opt.getNumber("number")); // NON-NLS
                e.getAction(ActionW.LAYOUT).ifPresent(a -> a.setSelectedItem(val1));
              }
            } else if (opt.isSet("id")) {
              if (selectedView2dContainer != null) {
                MigLayoutModel val2 = selectedView2dContainer.getViewLayout(opt.get("id"));
                if (val2 != null) {
                  e.getAction(ActionW.LAYOUT).ifPresent(a -> a.setSelectedItem(val2));
                }
              }
            }
          } catch (Exception e) {
            LOGGER.error("Layout command error", e);
          }
        });
  }

  public void mouseLeftAction(String[] argv) throws IOException {
    final Option opt = Options.compile(MOUSE_LEFT_ACTION_USAGE).parse(argv);
    final List<String> args = opt.args();

    if (opt.isSet("help") || args.size() != 1) { // NON-NLS
      opt.usage();
      return;
    }

    GuiExecutor.execute(
        () -> {
          String command = args.get(0);
          if (command != null) {
            try {
              if (command.startsWith("session")) { // NON-NLS
                AuditLog.LOGGER.info("source:command {}", command);
              } else {
                AuditLog.LOGGER.info(
                    "source:command mouse:{} action:{}", MouseActions.T_LEFT, command);
                excecuteMouseAction(command);
              }
            } catch (Exception e) {
              LOGGER.error("Mouse command: {}", command, e);
            }
          }
        });
  }

  private void excecuteMouseAction(String command) {
    EventManager e = EventManager.getInstance();
    if (!command.equals(e.getMouseActions().getAction(MouseActions.T_LEFT))) {
      e.getMouseActions().setAction(MouseActions.T_LEFT, command);
      ImageViewerPlugin<DicomImageElement> view = e.getSelectedView2dContainer();
      if (view != null) {
        view.setMouseActions(e.getMouseActions());
        final ViewerToolBar toolBar = view.getViewerToolBar();
        if (toolBar != null) {
          // Test if mouse action exist and if not NO_ACTION is set
          Feature<?> action = toolBar.getToolBarAction(command);
          if (action == null) {
            command = ActionW.NO_ACTION.cmd();
          }
          toolBar.changeButtonState(MouseActions.T_LEFT, command);
        }
      }
    }
  }

  public void reset(String[] argv) throws IOException {
    final Option opt = Options.compile(RESET_USAGE).parse(argv);
    final List<String> args = opt.args();

    if (opt.isSet("help") || args.isEmpty() && !opt.isSet("all")) { // NON-NLS
      opt.usage();
      return;
    }

    GuiExecutor.execute(
        () -> {
          EventManager e = EventManager.getInstance();
          if (opt.isSet("all")) { // NON-NLS
            e.reset(ResetTools.ALL);
          } else {
            for (String command : args) {
              try {
                if (ActionW.WINLEVEL.cmd().equals(command)) {
                  e.reset(ResetTools.WL);
                } else if (ActionW.ZOOM.cmd().equals(command)) {
                  e.reset(ResetTools.ZOOM);
                } else if (ActionW.PAN.cmd().equals(command)) {
                  e.reset(ResetTools.PAN);
                } else {
                  LOGGER.warn("Reset command not found: {}", command);
                }
              } catch (Exception ex) {
                LOGGER.error("Reset command: {}", command, ex);
              }
            }
          }
        });
  }
}
