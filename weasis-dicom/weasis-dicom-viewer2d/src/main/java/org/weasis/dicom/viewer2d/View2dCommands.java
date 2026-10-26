/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.command.AbstractVerb;
import org.weasis.core.api.command.CanvasRect;
import org.weasis.core.api.command.CommandContext;
import org.weasis.core.api.command.CommandExtension;
import org.weasis.core.api.command.CommandFailure;
import org.weasis.core.api.command.Degrees;
import org.weasis.core.api.command.GraphicId;
import org.weasis.core.api.command.GridId;
import org.weasis.core.api.command.ImageIndex;
import org.weasis.core.api.command.ImagePoint;
import org.weasis.core.api.command.ImagePolygon;
import org.weasis.core.api.command.ImageRect;
import org.weasis.core.api.command.InputPath;
import org.weasis.core.api.command.LayerName;
import org.weasis.core.api.command.ListOptions;
import org.weasis.core.api.command.Magnification;
import org.weasis.core.api.command.OutputPath;
import org.weasis.core.api.command.PaneIndex;
import org.weasis.core.api.command.SemanticType;
import org.weasis.core.api.command.Since;
import org.weasis.core.api.command.ViewId;
import org.weasis.core.api.command.WindowCenter;
import org.weasis.core.api.command.WindowWidth;
import org.weasis.core.api.gui.layout.MigLayoutModel;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.image.color.IccImageFiles;
import org.weasis.core.api.image.color.IccProfile;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.core.ui.docking.ws.WorkspaceManager;
import org.weasis.core.ui.editor.SeriesResolver;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.DisplayProfile;
import org.weasis.core.ui.editor.image.HangingLayout;
import org.weasis.core.ui.editor.image.ImageRegionStatistics;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MouseActionType;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.MouseButton;
import org.weasis.core.ui.editor.image.SynchEvent;
import org.weasis.core.ui.editor.image.SynchView;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewDisplayState;
import org.weasis.core.ui.editor.image.ViewTransferHandler;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.editor.image.ViewportPane;
import org.weasis.core.ui.model.AbstractGraphicModel;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicArea;
import org.weasis.core.ui.model.graphic.imp.AnnotationGraphic;
import org.weasis.core.ui.model.graphic.imp.angle.AngleToolGraphic;
import org.weasis.core.ui.model.graphic.imp.area.EllipseGraphic;
import org.weasis.core.ui.model.graphic.imp.area.PolygonGraphic;
import org.weasis.core.ui.model.graphic.imp.area.RectangleGraphic;
import org.weasis.core.ui.model.graphic.imp.area.ThreePointsCircleGraphic;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;
import org.weasis.core.ui.model.graphic.imp.line.PolylineGraphic;
import org.weasis.core.ui.model.layer.GraphicLayer;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.layer.imp.DefaultLayer;
import org.weasis.core.ui.model.utils.ImageStatistics;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.model.utils.bean.MeasurementVocabulary;
import org.weasis.core.ui.model.utils.bean.PanPoint;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.PixelAttestation;
import org.weasis.dicom.codec.PixelPrivacy;
import org.weasis.dicom.codec.Redaction;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.TagD;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.op.ImageConversion;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.TypeConversionException;

/** Command namespace {@code view2d}: display of a 2D DICOM view. */
@Extension
@Command(
    name = "view2d",
    description =
        "Display of a 2D DICOM view: zoom, window/level, pan, scrolling, grid, mouse action, "
            + "and the agent verbs capture, stats and measure. Every verb works on the pane given "
            + "by --view and --pane, the selected one by default, and returns its display state.",
    subcommands = {
      View2dCommands.Zoom.class,
      View2dCommands.WindowLevelVerb.class,
      View2dCommands.Pan.class,
      View2dCommands.Scroll.class,
      View2dCommands.Sort.class,
      View2dCommands.Grid.class,
      View2dCommands.Viewport.class,
      View2dCommands.Hang.class,
      View2dCommands.MouseAction.class,
      View2dCommands.Reset.class,
      View2dCommands.Sync.class,
      View2dCommands.Preset.class,
      View2dCommands.Rotate.class,
      View2dCommands.Flip.class,
      View2dCommands.Capture.class,
      View2dCommands.Stats.class,
      View2dCommands.Measure.class,
      View2dCommands.Annotate.class,
      View2dCommands.Graphics.class,
      View2dCommands.ClearAi.class
    })
public class View2dCommands implements CommandExtension {
  private static final Logger LOGGER = LoggerFactory.getLogger(View2dCommands.class);

  @Override
  public String getNamespace() {
    return "view2d"; // NON-NLS
  }

  @Override
  public boolean isRelevantFor(String viewerTypeId) {
    return viewerTypeId != null && !"3d".equals(viewerTypeId); // NON-NLS
  }

  @Override
  public Object getCommandModel() {
    return this;
  }

  static <T> T action(Feature<T> feature) {
    return EventManager.getInstance()
        .getAction(feature)
        .orElseThrow(() -> new IllegalStateException("No 2D view selected"));
  }

  static ImageViewerPlugin<DicomImageElement> selectedContainer() {
    ImageViewerPlugin<DicomImageElement> container =
        EventManager.getInstance().getSelectedView2dContainer();
    if (container == null) {
      throw new IllegalStateException("No 2D viewer selected");
    }
    return container;
  }

  @SuppressWarnings("unchecked")
  static ImageViewerPlugin<DicomImageElement> containerOf(String id) {
    WorkspaceManager manager = GuiUtils.getUICore().getWorkspaceManager();
    if (manager == null) {
      throw new IllegalStateException("No workspace available");
    }
    return manager
        .findView(id)
        .filter(ImageViewerPlugin.class::isInstance)
        .map(v -> (ImageViewerPlugin<DicomImageElement>) v)
        .orElseThrow(
            () ->
                CommandFailure.unknownHandle(
                    "Unknown 2D viewer id: " + id, "workspace:list")); // NON-NLS
  }

  /** A resolved pane: the viewer that owns it and the canvas itself. */
  record Target(
      ImageViewerPlugin<DicomImageElement> container, DefaultView2d<DicomImageElement> view) {}

  /**
   * Makes a pane the selected one before a change: the shared actions, the toolbars and the
   * window/level range then apply to it, exactly as after a click. Verbs that only read the state
   * leave the selection alone.
   */
  static void focus(Target target) {
    focus(target.container());
    if (target.container().getSelectedViewCanvas() != target.view()) {
      target.container().setSelectedImagePane(target.view());
    }
  }

  static void focus(ImageViewerPlugin<DicomImageElement> container) {
    EventManager manager = EventManager.getInstance();
    if (manager.getSelectedView2dContainer() != container) {
      container.setSelectedAndGetFocus();
      container.setSelected(true);
    }
  }

  /** Selects the target viewer: an id from 'workspace:list', else the selected viewer. */
  static class ViewerOptions {
    @Option(
        names = "--view",
        paramLabel = "<view-id>",
        description = "Viewer id from 'workspace:list' (default: the selected viewer).")
    ViewId viewId;

    ImageViewerPlugin<DicomImageElement> resolve() {
      return viewId == null ? selectedContainer() : containerOf(viewId.value());
    }
  }

  /** Selects the target 2D pane: a viewer from 'workspace:list' and a pane of its layout. */
  static class ViewOptions {
    @Mixin ViewerOptions viewer;

    @Option(
        names = "--pane",
        paramLabel = "<pane-index>",
        description = "Pane index in the viewer layout, from 1 (default: the selected pane).")
    PaneIndex pane;

    Target resolve() {
      ImageViewerPlugin<DicomImageElement> container = viewer.resolve();
      ViewCanvas<DicomImageElement> canvas;
      if (pane == null) {
        canvas = container.getSelectedViewCanvas();
      } else {
        List<ViewCanvas<DicomImageElement>> panels = container.getImagePanels();
        if (pane.value() > panels.size()) {
          throw new IllegalArgumentException(
              "Pane index out of range: " + pane + " (" + panels.size() + " panes)");
        }
        canvas = panels.get(pane.toOffset());
      }
      if (!(canvas instanceof DefaultView2d<DicomImageElement> view)) {
        throw new IllegalStateException("The pane is not a 2D image view");
      }
      return new Target(container, view);
    }

    /**
     * The pane and the image it shows; an empty pane is an error for the verbs that read pixels.
     */
    Target withImage() {
      Target target = resolve();
      if (target.view().getImage() == null) {
        throw new IllegalStateException("No image displayed in the pane");
      }
      return target;
    }

    DefaultView2d<DicomImageElement> view() {
      return withImage().view();
    }
  }

  /**
   * A verb that changes, or only reports, the display of one pane: it echoes the state of the pane
   * it resolved, and a call with no argument reports it without changing anything.
   */
  abstract static class DisplayVerb extends AbstractVerb {
    @Mixin ViewOptions target;

    @Override
    protected void run() {
      boolean change = hasChange();
      if (change) {
        validate();
      }
      JsonObject state =
          GuiExecutor.invokeAndRethrow(
              () -> {
                Target resolved = target.resolve();
                if (change) {
                  focus(resolved);
                  apply(resolved);
                }
                return ViewDisplayState.of(resolved.container(), resolved.view());
              });
      print(isJson() ? state.toString() : ViewDisplayState.text(state));
    }

    /** Whether the call carries a change; without one the verb only reports the state. */
    abstract boolean hasChange();

    /** Checks the combination of arguments before any viewer is touched. */
    void validate() {
      // nothing to check by default
    }

    abstract void apply(Target target);
  }

  /** A verb that changes, or only reports, a whole viewer: layout and synchronization. */
  abstract static class ViewerVerb extends AbstractVerb {
    @Mixin ViewerOptions target;

    @Override
    protected void run() {
      boolean change = hasChange();
      if (change) {
        validate();
      }
      JsonObject state =
          GuiExecutor.invokeAndRethrow(
              () -> {
                ImageViewerPlugin<DicomImageElement> container = target.resolve();
                if (change) {
                  focus(container);
                  apply(container);
                }
                return ViewDisplayState.ofViewer(container);
              });
      print(isJson() ? state.toString() : ViewDisplayState.textViewer(state));
    }

    abstract boolean hasChange();

    /** Checks the combination of arguments before any viewer is touched. */
    void validate() {
      // nothing to check by default
    }

    abstract void apply(ImageViewerPlugin<DicomImageElement> container);
  }

  /** How a zoom fits the image in the pane, when no magnification is given. */
  /** What a capture frames: the pane as displayed, or the image alone at its own size. */
  enum CaptureFrame {
    CANVAS,
    IMAGE;

    @Override
    public String toString() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  /** An on/off switch of an option that otherwise follows what is displayed. */
  enum Toggle {
    ON,
    OFF;

    @Override
    public String toString() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  enum ZoomFit {
    /** The zoom the preferences give to a newly opened image. */
    DEFAULT(0.0),
    /** One image pixel per screen pixel of the display. */
    REAL_SIZE(-100.0),
    /** The whole image in the pane. */
    BEST_FIT(-200.0);

    private final double command;

    ZoomFit(double command) {
      this.command = command;
    }

    /** The value the view understands, kept out of the surface (it was the 4.x magic number). */
    double command() {
      return command;
    }

    @Override
    public String toString() {
      return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
  }

  /** How much to zoom: a magnification, a fit, or a number of steps on the slider. */
  static class ZoomChoice {
    Magnification set;
    ZoomFit fit;
    Integer steps;

    @Option(
        names = "--set",
        paramLabel = "<magnification>",
        description = "Magnification, 1 being one image pixel per canvas pixel.")
    void setMagnification(Magnification value) {
      this.set = value;
    }

    @Option(
        names = "--fit",
        paramLabel = "<zoom-fit>",
        description = "default, real-size or best-fit.")
    void setFit(ZoomFit value) {
      this.fit = value;
    }

    @Option(
        names = "--by",
        paramLabel = "<steps>",
        description = "Moves the zoom slider by this many steps, negative to zoom out.")
    void setSteps(Integer value) {
      this.steps = value;
    }
  }

  @Command(
      name = "zoom",
      description =
          "Changes the zoom of a pane and returns its display state; with no argument the state is "
              + "returned unchanged.")
  static class Zoom extends DisplayVerb {
    @ArgGroup(exclusive = true)
    ZoomChoice choice;

    @Override
    boolean hasChange() {
      return choice != null;
    }

    @Override
    void apply(Target target) {
      SliderChangeListener zoom = action(ActionW.ZOOM);
      if (choice.set != null) {
        zoom.setRealValue(choice.set.value());
      } else if (choice.fit != null) {
        // A fit is not a slider value: the view computes it, so it goes straight to the pane.
        EventManager e = EventManager.getInstance();
        e.firePropertyChange(
            ActionW.SYNCH.cmd(),
            null,
            new SynchEvent(target.view(), ActionW.ZOOM.cmd(), choice.fit.command()));
      } else if (choice.steps != null) {
        zoom.setSliderValue(zoom.getSliderValue() + choice.steps);
      }
    }
  }

  /** Absolute window and level, both required together. */
  static class WindowLevel {
    @Option(
        names = "--window",
        required = true,
        paramLabel = "<window-width>",
        description = "Width of the window, in the unit of the pixels.")
    @Since("5.0")
    WindowWidth window;

    @Option(
        names = "--level",
        required = true,
        paramLabel = "<window-center>",
        description = "Centre of the window, in the unit of the pixels.")
    @Since("5.0")
    WindowCenter level;
  }

  @Command(
      name = "window-level",
      description =
          "Window and level of a pane: absolute with --window and --level, relative with "
              + "--window-by and --level-by in slider steps (range 4096). With no argument the "
              + "state is returned unchanged.")
  static class WindowLevelVerb extends DisplayVerb {
    @ArgGroup(exclusive = false)
    WindowLevel absolute;

    @Option(
        names = "--window-by",
        paramLabel = "<steps>",
        description = "Widens the window by this many slider steps, negative to narrow it.")
    @Since("5.0")
    Integer windowBy;

    @Option(
        names = "--level-by",
        paramLabel = "<steps>",
        description = "Raises the level by this many slider steps, negative to lower it.")
    @Since("5.0")
    Integer levelBy;

    @Override
    boolean hasChange() {
      return absolute != null || windowBy != null || levelBy != null;
    }

    @Override
    void validate() {
      boolean relative = windowBy != null || levelBy != null;
      if (absolute == null && !relative || absolute != null && relative) {
        throw invalid("Give --window and --level, or --window-by and --level-by");
      }
    }

    @Override
    void apply(Target target) {
      SliderChangeListener window = action(ActionW.WINDOW);
      SliderChangeListener level = action(ActionW.LEVEL);
      if (absolute != null) {
        window.setRealValue(absolute.window.value());
        level.setRealValue(absolute.level.value());
        return;
      }
      if (windowBy != null) {
        window.setSliderValue(window.getSliderValue() + windowBy);
      }
      if (levelBy != null) {
        level.setSliderValue(level.getSliderValue() + levelBy);
      }
    }
  }

  @Command(
      name = "pan",
      description =
          "Moves the image inside a pane by an offset in canvas pixels and returns its display "
              + "state; with no argument the state is returned unchanged.")
  static class Pan extends DisplayVerb {
    @Option(
        names = "--dx",
        paramLabel = "<canvas-pixels>",
        description = "Horizontal offset, positive to the right.")
    @Since("5.0")
    Integer dx;

    @Option(
        names = "--dy",
        paramLabel = "<canvas-pixels>",
        description = "Vertical offset, positive downwards.")
    @Since("5.0")
    Integer dy;

    @Override
    boolean hasChange() {
      return dx != null || dy != null;
    }

    @Override
    void apply(Target target) {
      PanPoint point = new PanPoint(PanPoint.State.MOVE, dx == null ? 0 : dx, dy == null ? 0 : dy);
      EventManager.getInstance().getAction(ActionW.PAN).ifPresent(a -> a.setPoint(point));
    }
  }

  /** Where to scroll: an image of the series, or a number of images from the current one. */
  static class ScrollChoice {
    ImageIndex image;
    Integer steps;

    @Option(names = "--image", paramLabel = "<image-index>", description = "Image to show, from 1.")
    void setImage(ImageIndex value) {
      this.image = value;
    }

    @Option(
        names = "--by",
        paramLabel = "<images>",
        description = "Moves by this many images, negative towards the first one.")
    void setSteps(Integer value) {
      this.steps = value;
    }
  }

  @Command(
      name = "scroll",
      description =
          "Moves through the images of the series a pane shows; with no argument the current "
              + "position is returned unchanged.")
  static class Scroll extends DisplayVerb {
    @ArgGroup(exclusive = true)
    ScrollChoice choice;

    @Override
    boolean hasChange() {
      return choice != null;
    }

    @Override
    void apply(Target target) {
      SliderCineListener cine = action(ActionW.SCROLL_SERIES);
      if (!cine.isActionEnabled()) {
        throw new IllegalStateException("No series to scroll in the pane");
      }
      if (choice.image != null) {
        cine.setSliderValue(choice.image.value());
      } else if (choice.steps != null) {
        cine.setSliderValue(cine.getSliderValue() + choice.steps);
      }
    }
  }

  /** Which grid to apply, or which one to ask about. */
  static class GridChoice {
    Integer panes;
    GridId grid;
    Integer bestFor;

    @Option(
        names = "--panes",
        paramLabel = "<n>",
        description = "Applies the best grid for this number of panes.")
    void setPanes(Integer value) {
      this.panes = value;
    }

    @Option(
        names = "--grid",
        paramLabel = "<grid-id>",
        description = "Applies this grid, by id from the bare verb.")
    void setGrid(GridId value) {
      this.grid = value;
    }

    @Option(
        names = "--best-for",
        paramLabel = "<n>",
        description = "Returns the best grid for this number of panes without applying it.")
    @Since("5.0")
    void setBestFor(Integer value) {
      this.bestFor = value;
    }
  }

  @Command(
      name = "grid",
      description =
          "Grid of panes of a viewer: with no argument it lists the grids it offers, --panes and "
              + "--grid apply one and return the state of the viewer, --best-for answers without "
              + "changing anything. An unknown grid id fails.")
  static class Grid extends AbstractVerb {
    @Mixin ViewerOptions target;

    @ArgGroup(exclusive = true)
    GridChoice choice;

    @Override
    protected void run() {
      print(GuiExecutor.invokeAndRethrow(this::applyOrDescribe));
    }

    private String applyOrDescribe() {
      ImageViewerPlugin<DicomImageElement> container = target.resolve();
      if (choice == null) {
        return list(container);
      }
      if (choice.bestFor != null) {
        return best(container, choice.bestFor);
      }
      focus(container);
      MigLayoutModel layout =
          choice.panes != null
              ? container.getBestDefaultViewLayout(choice.panes)
              : requireGrid(container, choice.grid);
      EventManager.getInstance()
          .getAction(ActionW.LAYOUT)
          .ifPresent(a -> a.setSelectedItem(layout));
      JsonObject state = ViewDisplayState.ofViewer(container);
      return isJson() ? state.toString() : ViewDisplayState.textViewer(state);
    }

    private String list(ImageViewerPlugin<DicomImageElement> container) {
      String current = container.getLayoutModel().getId();
      JsonArrayBuilder grids = Json.createArrayBuilder();
      for (MigLayoutModel layout : layouts(container)) {
        grids.add(
            Json.createObjectBuilder()
                .add("grid", layout.getId()) // NON-NLS
                .add("panes", ImageViewerPlugin.getViewTypeNumber(layout)) // NON-NLS
                .add("selected", layout.getId().equals(current))); // NON-NLS
      }
      JsonObject body = Json.createObjectBuilder().add("grids", grids).build(); // NON-NLS
      if (isJson()) {
        return body.toString();
      }
      return body.getJsonArray("grids").stream() // NON-NLS
          .map(JsonValue::asJsonObject)
          .map(g -> g.getString("grid") + " (" + g.getInt("panes") + " panes)") // NON-NLS
          .collect(Collectors.joining(", "));
    }

    private String best(ImageViewerPlugin<DicomImageElement> container, int panes) {
      MigLayoutModel layout = container.getBestDefaultViewLayout(panes);
      JsonObject body =
          Json.createObjectBuilder()
              .add("grid", layout.getId()) // NON-NLS
              .add("panes", ImageViewerPlugin.getViewTypeNumber(layout)) // NON-NLS
              .build();
      return isJson() ? body.toString() : body.getString("grid"); // NON-NLS
    }

    /** The grid of that id, or an error naming the ids the viewer offers (it used to fall back). */
    static MigLayoutModel requireGrid(ImageViewerPlugin<DicomImageElement> container, GridId id) {
      List<MigLayoutModel> layouts = layouts(container);
      return layouts.stream()
          .filter(layout -> id.value().equals(layout.getId()))
          .findFirst()
          .orElseThrow(
              () ->
                  new IllegalArgumentException(
                      "Unknown grid '"
                          + id
                          + "', available: "
                          + layouts.stream().map(MigLayoutModel::getId).toList()));
    }

    /**
     * The grids of that viewer: the LAYOUT action holds the ones of the selected viewer, which is
     * not the one addressed by {@code --view}.
     */
    static List<MigLayoutModel> layouts(ImageViewerPlugin<DicomImageElement> container) {
      return container.getLayoutList();
    }
  }

  @Command(
      name = "mouse-action",
      description =
          "Binds an action to a mouse button and returns the bindings; with no action the current "
              + "ones are returned unchanged.")
  static class MouseAction extends AbstractVerb {
    @Option(
        names = "--button",
        paramLabel = "<mouse-button>",
        defaultValue = "left",
        description = "left, middle, right or wheel (default: ${DEFAULT-VALUE}).")
    @Since("5.0")
    MouseButton button;

    @Option(
        names = "--action",
        paramLabel = "<mouse-action-type>",
        description =
            "sequence, window-level, zoom, pan, rotation, crosshair, measure, draw, context-menu "
                + "or none.")
    @Since("5.0")
    MouseActionType action;

    @Override
    protected void run() {
      if (action != null) {
        AuditLog.LOGGER.info("source:command mouse:{} action:{}", button, action);
        GuiExecutor.invokeAndRethrow(() -> bind(button, action));
      }
      print(bindings());
    }

    private String bindings() {
      MouseActions actions = EventManager.getInstance().getMouseActions();
      JsonObjectBuilder json = Json.createObjectBuilder();
      for (MouseButton value : MouseButton.values()) {
        json.add(
            value.toString(), MouseActionType.ofCommand(actions.getAction(value.key())).toString());
      }
      JsonObject body = json.build();
      return isJson()
          ? body.toString()
          : body.entrySet().stream()
              .map(e -> e.getKey() + "=" + ((JsonString) e.getValue()).getString())
              .collect(Collectors.joining(" "));
    }

    private static void bind(MouseButton button, MouseActionType action) {
      EventManager e = EventManager.getInstance();
      String command = action.command();
      if (command.equals(e.getMouseActions().getAction(button.key()))) {
        return;
      }
      e.getMouseActions().setAction(button.key(), command);
      ImageViewerPlugin<DicomImageElement> view = e.getSelectedView2dContainer();
      if (view == null) {
        return;
      }
      view.setMouseActions(e.getMouseActions());
      ViewerToolBar toolBar = view.getViewerToolBar();
      if (toolBar != null) {
        // Test if mouse action exist and if not NO_ACTION is set
        Feature<?> feature = toolBar.getToolBarAction(command);
        toolBar.changeButtonState(
            button.key(), feature == null ? ActionW.NO_ACTION.cmd() : command);
      }
    }
  }

  /** What a reset puts back to the state the image opened in. */
  enum ResetTool {
    WINDOW_LEVEL(ResetTools.WL),
    ZOOM(ResetTools.ZOOM),
    PAN(ResetTools.PAN),
    ROTATION(ResetTools.ROTATION);

    private final ResetTools tool;

    ResetTool(ResetTools tool) {
      this.tool = tool;
    }

    ResetTools tool() {
      return tool;
    }

    @Override
    public String toString() {
      return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** Reads the name of a tool as the surface spells it, {@code window-level} included. */
    static class Converter implements ITypeConverter<ResetTool> {
      @Override
      public ResetTool convert(String value) {
        String text = value == null ? "" : value.trim();
        for (ResetTool tool : values()) {
          if (tool.toString().equalsIgnoreCase(text)) {
            return tool;
          }
        }
        throw new TypeConversionException(
            "expected one of " + Stream.of(values()).map(Object::toString).toList());
      }
    }
  }

  @Command(
      name = "reset",
      description =
          "Puts the display of a pane back as the image opened, whole or tool by tool, and returns "
              + "its state; with no argument the state is returned unchanged.")
  static class Reset extends DisplayVerb {
    @Option(names = "--all", description = "Reset everything to the original display.")
    boolean all;

    @Option(
        names = "--tool",
        paramLabel = "<reset-tool>",
        converter = ResetTool.Converter.class,
        description = "window-level, zoom, pan or rotation; repeat it for several.")
    @Since("5.0")
    List<ResetTool> tools = new ArrayList<>();

    @Override
    boolean hasChange() {
      return all || !tools.isEmpty();
    }

    @Override
    void apply(Target target) {
      EventManager e = EventManager.getInstance();
      if (all) {
        e.reset(ResetTools.ALL);
      } else {
        tools.forEach(tool -> e.reset(tool.tool()));
      }
    }
  }

  // ------------------------------------------------------------------------------------------
  // Agent verbs: capture, stats, measure, clear-ai
  // ------------------------------------------------------------------------------------------

  /** Name of the graphic layers that hold what the agent draws. */
  public static final String AI_LAYER_NAME = "AI"; // NON-NLS

  private static final int DEFAULT_MAX_PIXELS = 2_000_000;
  private static final Set<String> IMAGE_FORMATS = Set.of("png", "jpg", "jpeg"); // NON-NLS

  static Point2D toImage(DefaultView2d<?> view, double x, double y, boolean canvasCoordinates) {
    return canvasCoordinates
        ? view.getImageCoordinatesFromMouse((int) Math.round(x), (int) Math.round(y))
        : new Point2D.Double(x, y);
  }

  static Point2D toImage(DefaultView2d<?> view, ImagePoint point, boolean canvasCoordinates) {
    return toImage(view, point.x(), point.y(), canvasCoordinates);
  }

  static List<Point2D> toImage(
      DefaultView2d<?> view, ImagePolygon polygon, boolean canvasCoordinates) {
    List<Point2D> points = new ArrayList<>(polygon.points().size());
    for (Point2D point : polygon.points()) {
      points.add(toImage(view, point.getX(), point.getY(), canvasCoordinates));
    }
    return points;
  }

  static Rectangle2D toImage(DefaultView2d<?> view, ImageRect rect, boolean canvasCoordinates) {
    Point2D a = toImage(view, rect.x(), rect.y(), canvasCoordinates);
    Point2D b = toImage(view, rect.x() + rect.width(), rect.y() + rect.height(), canvasCoordinates);
    return corners(a, b);
  }

  static Rectangle2D corners(Point2D a, Point2D b) {
    Rectangle2D r = new Rectangle2D.Double();
    r.setFrameFromDiagonal(a, b);
    return r;
  }

  static Unit spatialUnit() {
    return EventManager.getInstance()
        .getAction(ActionW.SPATIAL_UNIT)
        .map(c -> (Unit) c.getSelectedItem())
        .orElse(Unit.MILLIMETER);
  }

  static GraphicLayer aiLayer(DefaultView2d<?> view, LayerType type) {
    return view.getGraphicManager().getLayers().stream()
        .filter(l -> l.getType() == type && AI_LAYER_NAME.equals(l.getName()))
        .findFirst()
        .orElseGet(
            () -> {
              DefaultLayer layer = new DefaultLayer(type);
              layer.setName(AI_LAYER_NAME);
              return layer;
            });
  }

  /**
   * Measured values as JSON: the stable key of the measurement, its English name, the value and its
   * UCUM unit. A statistic computed on one channel of a colour image, or on the PET layer of a
   * fusion, carries the channel number or the label of that layer.
   */
  static JsonArrayBuilder measurementsJson(
      List<MeasureItem> items, Unit spatial, String pixelValueUnit) {
    JsonArrayBuilder array = Json.createArrayBuilder();
    for (MeasureItem item : items) {
      Measurement m = item.getMeasurement();
      JsonObjectBuilder o =
          Json.createObjectBuilder()
              .add("key", m.getKey()) // NON-NLS
              .add("name", MeasurementVocabulary.nameOf(m)); // NON-NLS
      Object value = item.getValue();
      if (value instanceof Number n && Double.isFinite(n.doubleValue())) {
        o.add("value", n.doubleValue()); // NON-NLS
      } else if (value != null) {
        o.add("value", String.valueOf(value)); // NON-NLS
      } else {
        o.addNull("value"); // NON-NLS
      }
      String unit = MeasurementVocabulary.unitOf(m, spatial, pixelValueUnit);
      if (unit != null) {
        o.add("unit", unit); // NON-NLS
      }
      qualify(o, item.getLabelExtension());
      array.add(o);
    }
    return array;
  }

  /** The channel or the fused series a statistic was computed on, when there is more than one. */
  private static void qualify(JsonObjectBuilder o, String labelExtension) {
    String label = labelExtension == null ? "" : labelExtension.trim();
    if (label.isEmpty()) {
      return;
    }
    try {
      o.add("channel", Integer.parseInt(label)); // NON-NLS
    } catch (NumberFormatException e) {
      o.add("fusion", label); // NON-NLS
    }
  }

  /** A graphic layer as JSON: its name and its kind. */
  static JsonObjectBuilder layerJson(GraphicLayer layer) {
    JsonObjectBuilder json = Json.createObjectBuilder();
    if (layer.getName() != null) {
      json.add("name", layer.getName()); // NON-NLS
    }
    json.add("type", SemanticType.valueOf(layer.getType())); // NON-NLS
    return json;
  }

  static List<MeasureItem> geometryOnly(List<MeasureItem> items) {
    Set<Measurement> stats = Set.of(ImageStatistics.ALL_MEASUREMENTS);
    return items.stream().filter(i -> !stats.contains(i.getMeasurement())).toList();
  }

  @Command(
      name = "capture",
      description =
          "Renders a view as displayed (window/level, annotations, identity masking) into a PNG "
              + "or JPEG file and returns how image pixels map to the capture; --frame image "
              + "renders the image alone at its own size, for exact comparisons.")
  @Since("5.0")
  static class Capture extends AbstractVerb {
    @Mixin ViewOptions target;

    @Option(
        names = "--out",
        required = true,
        paramLabel = "<output-path>",
        description = "File to write; the extension selects PNG or JPEG (.jpg).")
    OutputPath out;

    @Option(
        names = "--force",
        description = "Replace the file when it exists and was not written by this session.")
    @Since("5.0")
    boolean force;

    @Option(
        names = "--crop",
        paramLabel = "<canvas-rect>",
        description = "Crop to this region of the canvas before scaling.")
    @Since("5.0")
    CanvasRect crop;

    @Option(
        names = "--max-pixels",
        paramLabel = "<n>",
        description =
            "Downscale so the file has at most this many pixels (default: "
                + DEFAULT_MAX_PIXELS
                + " for the canvas, none for the image frame).")
    Integer maxPixels;

    @Option(
        names = "--frame",
        paramLabel = "<frame>",
        description =
            "canvas (default): the pane as displayed, its size and zoom; image: one pixel per"
                + " displayed image pixel, whatever the pane, the zoom and the pan.")
    @Since("5.0")
    CaptureFrame frame = CaptureFrame.CANVAS;

    @Option(
        names = "--annotations",
        paramLabel = "<on|off>",
        description =
            "Text, scale, orientation, drawings and cross-lines: on or off (default: as displayed"
                + " for the canvas, off for the image frame).")
    @Since("5.0")
    Toggle annotations;

    /** What the EDT rendered, plus the geometry needed to map pixels. */
    record Rendered(
        BufferedImage image,
        JsonObject target,
        Rectangle2D modelArea,
        Point[] corners,
        String modality,
        double[] pixelSpacing,
        String pixelSpacingUnit) {}

    @Override
    protected void run() throws IOException {
      if (maxPixels != null && maxPixels <= 0) {
        throw invalid("--max-pixels must be positive");
      }
      int limit =
          maxPixels != null
              ? maxPixels
              : frame == CaptureFrame.IMAGE ? Integer.MAX_VALUE : DEFAULT_MAX_PIXELS;
      String format = format(out.value());
      Rendered r =
          GuiExecutor.invokeAndRethrow(() -> render(target.withImage(), frame, annotations));

      BufferedImage source = r.image();
      Rectangle cropped =
          crop == null
              ? null
              : crop.toRectangle()
                  .intersection(new Rectangle(0, 0, source.getWidth(), source.getHeight()));
      if (cropped != null && cropped.isEmpty()) {
        throw invalid(
            "--crop is outside the canvas (" + source.getWidth() + "x" + source.getHeight() + ")");
      }
      BufferedImage image =
          cropped == null
              ? source
              : source.getSubimage(cropped.x, cropped.y, cropped.width, cropped.height);
      double scale = scaleFor(image, limit);
      BufferedImage result = grayWhenNeutral(scaled(image, scale));
      Path file = out.value();
      if (file.getParent() != null) {
        Files.createDirectories(file.getParent());
      }
      writeCapture(result, format, file);
      CommandContext.current().addFile(file);

      int dx = cropped == null ? 0 : cropped.x;
      int dy = cropped == null ? 0 : cropped.y;
      JsonArrayBuilder corners = Json.createArrayBuilder();
      for (Point p : r.corners()) {
        corners.add(Json.createArrayBuilder().add((p.x - dx) * scale).add((p.y - dy) * scale));
      }
      JsonObjectBuilder json =
          Json.createObjectBuilder()
              .add("target", r.target()) // NON-NLS
              .add("file", out.toAbsolutePath().toString()) // NON-NLS
              .add("format", format) // NON-NLS
              .add("width", result.getWidth()) // NON-NLS
              .add("height", result.getHeight()) // NON-NLS
              .add("scale", scale) // NON-NLS
              .add("frame", frame.toString()) // NON-NLS
              .add(
                  "canvas", // NON-NLS
                  Json.createObjectBuilder()
                      .add("width", source.getWidth()) // NON-NLS
                      .add("height", source.getHeight())) // NON-NLS
              .add(
                  "imageSize", // NON-NLS
                  Json.createObjectBuilder()
                      .add("width", r.modelArea().getWidth()) // NON-NLS
                      .add("height", r.modelArea().getHeight())) // NON-NLS
              .add("modality", r.modality() == null ? "" : r.modality()) // NON-NLS
              .add(
                  "pixelSpacing", // NON-NLS
                  Json.createArrayBuilder().add(r.pixelSpacing()[0]).add(r.pixelSpacing()[1]))
              .add("pixelSpacingUnit", r.pixelSpacingUnit()) // NON-NLS
              .add("imageCorners", corners); // NON-NLS
      if (cropped != null) {
        json.add(
            "crop", // NON-NLS
            Json.createObjectBuilder()
                .add("x", cropped.x)
                .add("y", cropped.y)
                .add("width", cropped.width) // NON-NLS
                .add("height", cropped.height)); // NON-NLS
      }
      print(json.build().toString());
    }

    /** A canvas capture holds the values sent to the display: it carries the display profile. */
    private static void writeCapture(BufferedImage image, String format, Path file)
        throws IOException {
      IccProfile profile = IccImageFiles.displayedProfile().orElse(null);
      if (profile != null
          && image.getRaster().getNumBands() == 3
          && IccImageFiles.canEmbed(file, profile)) {
        ImageCV mat = ImageConversion.toMat(image);
        try {
          if (!IccImageFiles.write(mat, file, null, profile)) {
            throw new IOException("Cannot write " + file);
          }
        } finally {
          mat.release();
        }
      } else if (!ImageIO.write(image, format, file.toFile())) {
        throw new IOException("No image writer for " + format);
      }
    }

    private String format(Path file) {
      String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
      int dot = name.lastIndexOf('.');
      String ext = dot < 0 ? "" : name.substring(dot + 1);
      if (!IMAGE_FORMATS.contains(ext)) {
        throw invalid("--out must end with .png, .jpg or .jpeg");
      }
      return "jpeg".equals(ext) ? "jpg" : ext; // NON-NLS
    }

    private static Rendered render(Target resolved, CaptureFrame frame, Toggle annotations) {
      DefaultView2d<DicomImageElement> view = resolved.view();
      DicomImageElement image = view.getImage();
      String modality = TagD.getTagValue(image, Tag.Modality, String.class);
      if (CommandContext.current().isRemote()
          && PixelPrivacy.requiresReview(image, modality)
          && !Redaction.isRedacted(image, view.getSeries())
          && !PixelAttestation.covers(image, view.getSeries())) {
        throw new IllegalStateException(
            "The image may carry burned-in identification; apply a redaction, or have a person"
                + " review it (redact:attest), before capturing it remotely");
      }
      // null: the canvas as displayed; otherwise what the view shows, annotations on or off
      DisplayProfile profile =
          annotations == null && frame == CaptureFrame.CANVAS
              ? null
              : annotations == Toggle.ON
                  ? DisplayProfile.of(view)
                  : DisplayProfile.of(view).imageOnly();
      BufferedImage buffered;
      Rectangle2D area;
      Point[] corners;
      if (frame == CaptureFrame.IMAGE) {
        ViewTransferHandler.ImageFrame rendered =
            IdentityMask.callForCommand(() -> ViewTransferHandler.createImageFrame(view, profile));
        buffered = rendered.image();
        area = rendered.modelArea();
        corners = rendered.corners();
      } else {
        RenderedImage rendered =
            IdentityMask.callForCommand(
                () -> ViewTransferHandler.createComponentImage(view, profile));
        if (!(rendered instanceof BufferedImage b)) {
          throw new IllegalStateException("The view could not be rendered");
        }
        buffered = b;
        area = view.getViewModel().getModelArea();
        corners =
            new Point[] {
              view.getMouseCoordinatesFromImage(area.getMinX(), area.getMinY()),
              view.getMouseCoordinatesFromImage(area.getMaxX(), area.getMinY()),
              view.getMouseCoordinatesFromImage(area.getMaxX(), area.getMaxY()),
              view.getMouseCoordinatesFromImage(area.getMinX(), area.getMaxY())
            };
      }
      return new Rendered(
          buffered,
          ViewDisplayState.target(resolved.container(), view).build(),
          area,
          corners,
          modality,
          // DICOM orders pixel spacing as row then column spacing
          new double[] {image.getPixelSizeY(), image.getPixelSizeX()},
          image.getPixelSpacingUnit().getUcum());
    }

    /**
     * The same pixels in one 8-bit gray channel when every pixel is neutral (red = green = blue),
     * as a monochrome image without colored annotations renders: a third of the file, same values.
     */
    static BufferedImage grayWhenNeutral(BufferedImage image) {
      int w = image.getWidth();
      int h = image.getHeight();
      int[] row = new int[w];
      for (int y = 0; y < h; y++) {
        image.getRGB(0, y, w, 1, row, 0, w);
        for (int rgb : row) {
          int r = (rgb >> 16) & 0xff;
          if (r != ((rgb >> 8) & 0xff) || r != (rgb & 0xff)) {
            return image;
          }
        }
      }
      BufferedImage gray = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
      java.awt.image.WritableRaster raster = gray.getRaster();
      int[] values = new int[w];
      for (int y = 0; y < h; y++) {
        image.getRGB(0, y, w, 1, row, 0, w);
        for (int x = 0; x < w; x++) {
          values[x] = row[x] & 0xff;
        }
        raster.setPixels(0, y, w, 1, values);
      }
      return gray;
    }

    private static double scaleFor(BufferedImage image, int maxPixels) {
      long pixels = (long) image.getWidth() * image.getHeight();
      return pixels <= maxPixels ? 1.0 : Math.sqrt((double) maxPixels / pixels);
    }

    private static BufferedImage scaled(BufferedImage source, double scale) {
      if (scale >= 1.0) {
        return source;
      }
      int w = Math.max(1, (int) Math.round(source.getWidth() * scale));
      int h = Math.max(1, (int) Math.round(source.getHeight() * scale));
      BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
      Graphics2D g = dst.createGraphics();
      try {
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, w, h, null);
      } finally {
        g.dispose();
      }
      return dst;
    }
  }

  /** Exclusive region of interest of the stats verb. */
  static class Region {
    @Option(
        names = "--rect",
        paramLabel = "<image-rect>",
        description = "Axis-aligned rectangle, in image pixels unless --canvas is given.")
    ImageRect rect;

    @Option(
        names = "--ellipse",
        paramLabel = "<image-rect>",
        description = "Ellipse inscribed in this rectangle.")
    ImageRect ellipse;

    @Option(
        names = "--polygon",
        paramLabel = "<image-polygon>",
        description = "Polygon vertices as x,y pairs (three at least).")
    ImagePolygon polygon;

    @Option(
        names = "--graphic",
        paramLabel = "<graphic-id>",
        description = "Id of an existing area graphic of the view, from 'graphics'.")
    GraphicId graphicId;

    GraphicArea toGraphic(DefaultView2d<DicomImageElement> view, boolean canvasCoordinates) {
      try {
        if (rect != null) {
          return new RectangleGraphic().buildGraphic(toImage(view, rect, canvasCoordinates));
        }
        if (ellipse != null) {
          return new EllipseGraphic().buildGraphic(toImage(view, ellipse, canvasCoordinates));
        }
        if (polygon != null) {
          return (GraphicArea)
              new PolygonGraphic().buildGraphic(toImage(view, polygon, canvasCoordinates));
        }
      } catch (InvalidShapeException e) {
        throw new IllegalArgumentException("Invalid region: " + e.getMessage(), e);
      }
      Graphic graphic =
          view.getGraphicManager().getAllGraphics().stream()
              .filter(g -> graphicId.value().equals(g.getUuid()))
              .findFirst()
              .orElseThrow(
                  () ->
                      CommandFailure.unknownHandle(
                          "Unknown graphic id: " + graphicId, "view2d:graphics")); // NON-NLS
      if (!(graphic instanceof GraphicArea area)) {
        throw new IllegalArgumentException("Graphic is not an area: " + graphicId);
      }
      return area;
    }
  }

  @Command(
      name = "stats",
      description =
          "Pixel statistics of a region (min, max, mean, median, standard deviation, ...) in the "
              + "image value unit, e.g. HU, plus the region geometry in the spatial unit.")
  @Since("5.0")
  static class Stats extends AbstractVerb {
    @Mixin ViewOptions target;

    @ArgGroup(exclusive = true, multiplicity = "1")
    Region region;

    @Option(
        names = "--canvas",
        description = "Coordinates are canvas pixels (as in a capture) instead of image pixels.")
    boolean canvasCoordinates;

    @Override
    protected void run() {
      if (region.polygon != null && region.polygon.points().size() < 3) {
        throw invalid("--polygon needs at least three x,y pairs");
      }
      String body =
          GuiExecutor.invokeAndRethrow(
              () -> {
                Target resolved = target.withImage();
                DefaultView2d<DicomImageElement> view = resolved.view();
                GraphicArea area = region.toGraphic(view, canvasCoordinates);
                var layer = view.getMeasurableLayer();
                Unit spatial = spatialUnit();
                // a caller of the commands gets every value, not the ones the user displays
                List<MeasureItem> stats =
                    Measurement.computingAll(
                        () -> ImageRegionStatistics.computeStatistics(area, layer));
                List<MeasureItem> geometry =
                    geometryOnly(
                        Measurement.computingAll(
                            () -> area.computeMeasurements(layer, true, spatial)));
                String values = layer.getPixelValueUnit();
                return Json.createObjectBuilder()
                    .add("target", ViewDisplayState.target(resolved.container(), view)) // NON-NLS
                    .add("statistics", measurementsJson(stats, spatial, values)) // NON-NLS
                    .add("geometry", measurementsJson(geometry, spatial, values)) // NON-NLS
                    .build()
                    .toString();
              });
      print(body);
    }
  }

  /** Measurement graphics the agent can draw, with the number of vertices each one takes. */
  enum MeasureType {
    LINE(2, 2),
    POLYLINE(2, Integer.MAX_VALUE),
    RECTANGLE(2, 2),
    ELLIPSE(2, 2),
    CIRCLE(3, 3),
    POLYGON(3, Integer.MAX_VALUE),
    ANGLE(3, 3);

    final int minPoints;
    final int maxPoints;

    MeasureType(int minPoints, int maxPoints) {
      this.minPoints = minPoints;
      this.maxPoints = maxPoints;
    }

    Graphic build(List<Point2D> points) throws InvalidShapeException {
      return switch (this) {
        case LINE -> new LineGraphic().buildGraphic(points);
        case POLYLINE -> new PolylineGraphic().buildGraphic(points);
        case RECTANGLE ->
            new RectangleGraphic().buildGraphic(corners(points.get(0), points.get(1)));
        case ELLIPSE -> new EllipseGraphic().buildGraphic(corners(points.get(0), points.get(1)));
        case CIRCLE -> new ThreePointsCircleGraphic().buildGraphic(points);
        case POLYGON -> new PolygonGraphic().buildGraphic(points);
        case ANGLE -> new AngleToolGraphic().buildGraphic(points);
      };
    }
  }

  @Command(
      name = "measure",
      description =
          "Draws a measurement on the AI layer of the view and returns its values, in the spatial "
              + "unit of the viewer (mm when the image is calibrated).")
  @Since("5.0")
  static class Measure extends AbstractVerb {
    @Mixin ViewOptions target;

    @Option(
        names = "--type",
        required = true,
        paramLabel = "<type>",
        description = "${COMPLETION-CANDIDATES}.")
    MeasureType type;

    @Option(
        names = "--points",
        required = true,
        paramLabel = "<image-polygon>",
        description = "Vertices as x,y pairs; rectangle and ellipse take two opposite corners.")
    ImagePolygon points;

    @Option(
        names = "--canvas",
        description = "Coordinates are canvas pixels (as in a capture) instead of image pixels.")
    boolean canvasCoordinates;

    @Override
    protected void run() {
      int count = points.points().size();
      if (count < type.minPoints || count > type.maxPoints) {
        String expected =
            type.maxPoints == Integer.MAX_VALUE
                ? "at least " + type.minPoints
                : String.valueOf(type.minPoints);
        throw invalid(type.name().toLowerCase(Locale.ROOT) + " needs " + expected + " x,y pairs");
      }
      String body =
          GuiExecutor.invokeAndRethrow(
              () -> {
                Target resolved = target.withImage();
                DefaultView2d<DicomImageElement> view = resolved.view();
                Graphic graphic;
                try {
                  graphic = type.build(toImage(view, points, canvasCoordinates));
                } catch (InvalidShapeException e) {
                  throw new IllegalArgumentException("Invalid shape: " + e.getMessage(), e);
                }
                GraphicLayer layer = aiLayer(view, LayerType.MEASURE);
                AbstractGraphicModel.addGraphicToModel(view, layer, graphic);
                view.getJComponent().repaint();
                var measurable = view.getMeasurableLayer();
                Unit spatial = spatialUnit();
                List<MeasureItem> items =
                    Measurement.computingAll(
                        () -> graphic.computeMeasurements(measurable, true, spatial));
                return Json.createObjectBuilder()
                    .add("target", ViewDisplayState.target(resolved.container(), view)) // NON-NLS
                    .add("id", graphic.getUuid()) // NON-NLS
                    .add("type", type.name().toLowerCase(Locale.ROOT)) // NON-NLS
                    .add("layer", layerJson(layer)) // NON-NLS
                    .add(
                        "measurements", // NON-NLS
                        measurementsJson(items, spatial, measurable.getPixelValueUnit()))
                    .build()
                    .toString();
              });
      print(body);
    }
  }

  @Command(name = "clear-ai", description = "Removes every graphic drawn by the agent in the view.")
  @Since("5.0")
  static class ClearAi extends AbstractVerb {
    @Mixin ViewOptions target;

    @Override
    protected void run() {
      String body =
          GuiExecutor.invokeAndRethrow(
              () -> {
                Target resolved = target.resolve();
                DefaultView2d<DicomImageElement> view = resolved.view();
                GraphicModel model = view.getGraphicManager();
                List<Graphic> ours =
                    model.getAllGraphics().stream()
                        .filter(
                            g ->
                                g.getLayer() != null
                                    && AI_LAYER_NAME.equals(g.getLayer().getName()))
                        .toList();
                ours.forEach(model::removeGraphic);
                view.getJComponent().repaint();
                return Json.createObjectBuilder()
                    .add("target", ViewDisplayState.target(resolved.container(), view)) // NON-NLS
                    .add("removed", ours.size()) // NON-NLS
                    .build()
                    .toString();
              });
      print(body);
    }
  }

  /** How the panes of a viewer follow each other. */
  enum SyncMode {
    NONE,
    STACK,
    TILE;

    @Override
    public String toString() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  @Command(
      name = "sync",
      description =
          "Sets how the panes of a viewer follow each other and returns its state: none turns the "
              + "synchronization off, another mode turns it on; with no argument the state is "
              + "returned unchanged.")
  static class Sync extends ViewerVerb {
    @Option(
        names = "--mode",
        paramLabel = "<sync-mode>",
        description = "none, stack or tile, among the modes the viewer offers.")
    @Since("5.0")
    SyncMode mode;

    @Override
    boolean hasChange() {
      return mode != null;
    }

    @Override
    void apply(ImageViewerPlugin<DicomImageElement> container) {
      ToggleButtonListener synchMode = action(ActionW.SYNCH_MODE);
      if (mode == SyncMode.NONE) {
        synchMode.setSelected(false);
        return;
      }
      ComboItemListener<SynchView> synch = action(ActionW.SYNCH);
      List<String> offered = new ArrayList<>();
      offered.add(SyncMode.NONE.toString());
      for (Object item : synch.getAllItem()) {
        if (item instanceof SynchView view) {
          if (mode.toString().equalsIgnoreCase(view.getCommand())) {
            synch.setSelectedItem(view);
            synchMode.setSelected(true);
            return;
          }
          offered.add(view.getCommand().toLowerCase(Locale.ROOT));
        }
      }
      throw new IllegalArgumentException(
          "This viewer does not offer the '" + mode + "' synchronization, only: " + offered);
    }
  }

  /** How a viewport shows its series: one image, or a grid of tiles. */
  static class ViewportMode {
    @Option(
        names = "--tiled",
        paramLabel = "<cols>x<rows>",
        description = "Shows the series of the pane on this grid of tiles, successive images.")
    @Since("5.0")
    String tiled;

    @Option(names = "--stack", description = "Returns the pane to a single image.")
    @Since("5.0")
    boolean stack;
  }

  @Command(
      name = "viewport",
      description =
          "Viewport of a pane: --tiled shows its series on a grid of tiles, --stack returns it to "
              + "one image; the state of the viewer, with its cells, is returned. With no "
              + "argument the state is returned unchanged.")
  @Since("5.0")
  static class Viewport extends AbstractVerb {
    @Mixin ViewOptions target;

    @ArgGroup(exclusive = true)
    ViewportMode mode;

    @Override
    protected void run() {
      HangingLayout.Tiling tiling =
          mode != null && mode.tiled != null ? HangingLayout.Tiling.parse(mode.tiled) : null;
      JsonObject state =
          GuiExecutor.invokeAndRethrow(
              () -> {
                Target resolved = target.resolve();
                ImageViewerPlugin<DicomImageElement> container = resolved.container();
                if (mode != null) {
                  ViewportPane<DicomImageElement> pane = container.getOwningPane(resolved.view());
                  if (pane == null) {
                    throw new IllegalStateException("The pane is not a viewport of the viewer");
                  }
                  focus(resolved);
                  if (tiling != null) {
                    pane.switchToTiled(
                        tiling.cols(), tiling.rows(), () -> container.createDefaultView(null));
                  } else {
                    pane.switchToStack();
                  }
                }
                return ViewDisplayState.ofViewer(container);
              });
      print(isJson() ? state.toString() : ViewDisplayState.textViewer(state));
    }
  }

  @Command(
      name = "hang",
      description =
          "Hangs series in the cells of a viewer in one call: --grid picks the grid, each --cell "
              + "puts a series in a cell (an empty value clears it), --tiled tiles a cell, --sync "
              + "and --select set the synchronization and the selected cell; --file applies a "
              + "layout saved by 'workspace:save'. The state of the viewer is returned. With no "
              + "argument the current layout is returned, in the form --file takes.")
  @Since("5.0")
  static class Hang extends AbstractVerb {
    @Mixin ViewerOptions target;

    @Option(
        names = "--grid",
        paramLabel = "<grid-id>",
        description = "Grid to apply, by id from 'grid'.")
    GridId grid;

    @Option(
        names = "--cell",
        paramLabel = "<cell>=<series-uid>",
        description = "Series shown in that cell, from 1; an empty value clears the cell.")
    Map<Integer, String> cells;

    @Option(
        names = "--tiled",
        paramLabel = "<cell>=<cols>x<rows>",
        description = "Tiles of that cell; 1x1 returns it to a single image.")
    Map<Integer, String> tiled;

    @Option(
        names = "--sync",
        paramLabel = "<sync-mode>",
        description = "none, stack or tile, among the modes the viewer offers.")
    SyncMode sync;

    @Option(names = "--select", paramLabel = "<cell>", description = "Cell to select, from 1.")
    Integer select;

    @Option(
        names = "--file",
        paramLabel = "<input-path>",
        description = "A layout written by 'workspace:save' or by this verb without argument.")
    InputPath file;

    boolean hasChange() {
      return grid != null
          || cells != null
          || tiled != null
          || sync != null
          || select != null
          || file != null;
    }

    @Override
    protected void run() {
      if (!hasChange()) {
        JsonObject layout =
            GuiExecutor.invokeAndRethrow(() -> target.resolve().captureLayout().toJson());
        print(layout.toString());
        return;
      }
      HangingLayout layout = file != null ? readFile() : build();
      if (file != null && (grid != null || cells != null || tiled != null || sync != null)) {
        throw new IllegalArgumentException("--file cannot be combined with the other options");
      }
      Map<String, MediaSeries<? extends MediaElement>> resolved = resolveSeries(layout);
      JsonObject state =
          GuiExecutor.invokeAndRethrow(
              () -> {
                ImageViewerPlugin<DicomImageElement> container = target.resolve();
                focus(container);
                if (layout.grid() != null) {
                  Grid.requireGrid(container, new GridId(layout.grid()));
                }
                if (!container.hang(layout, id -> Optional.ofNullable(resolved.get(id)))) {
                  throw new IllegalStateException("This viewer does not hang layouts");
                }
                return ViewDisplayState.ofViewer(container);
              });
      print(isJson() ? state.toString() : ViewDisplayState.textViewer(state));
    }

    private HangingLayout build() {
      HangingLayout.Builder builder = HangingLayout.builder();
      if (grid != null) {
        builder.grid(grid.value());
      }
      if (sync != null) {
        builder.synch(sync.toString());
      }
      if (select != null) {
        builder.select(select);
      }
      if (cells != null) {
        cells.forEach(
            (cell, uid) ->
                builder.series(position(cell), uid == null || uid.isBlank() ? null : uid));
      }
      if (tiled != null) {
        tiled.forEach(
            (cell, grid) -> {
              HangingLayout.Tiling tiling = HangingLayout.Tiling.parse(grid);
              builder.tiled(
                  position(cell), tiling.cols() == 1 && tiling.rows() == 1 ? null : tiling);
            });
      }
      return builder.build();
    }

    /** Cells are numbered from 1 on the command line and from 0 in the layout. */
    private static int position(int cell) {
      if (cell < 1) {
        throw new IllegalArgumentException("Cells are numbered from 1, got: " + cell);
      }
      return cell - 1;
    }

    private HangingLayout readFile() {
      try (JsonReader reader = Json.createReader(Files.newBufferedReader(file.value()))) {
        return HangingLayout.fromJson(reader.readObject());
      } catch (IOException | JsonException e) {
        throw new IllegalArgumentException(
            "Cannot read the layout " + file + ": " + e.getMessage());
      }
    }

    /** Every series the layout names must be loaded: an unknown id fails before any change. */
    private static Map<String, MediaSeries<? extends MediaElement>> resolveSeries(
        HangingLayout layout) {
      SeriesResolver resolver = SeriesResolver.ofApplication();
      Map<String, MediaSeries<? extends MediaElement>> resolved = new HashMap<>();
      for (HangingLayout.Cell cell : layout.cells()) {
        cell.seriesId()
            .ifPresent(
                id ->
                    resolved.put(
                        id,
                        resolver
                            .resolve(id)
                            .orElseThrow(
                                () ->
                                    CommandFailure.unknownHandle(
                                        "Unknown series id: " + id, "dicom:list")))); // NON-NLS
      }
      return resolved;
    }
  }

  // ------------------------------------------------------------------------------------------
  // Display verbs: preset, rotate, flip
  // ------------------------------------------------------------------------------------------

  /** Identifies a window/level preset: the id of a configured one, or its name. */
  public record PresetId(String value) implements SemanticType {

    private static final String EXPECTED = "a preset id or name from 'preset'"; // NON-NLS

    public PresetId {
      value = SemanticType.handle(value, EXPECTED);
    }

    public static PresetId parse(String text) {
      return new PresetId(text);
    }

    @Override
    public String toString() {
      return value;
    }
  }

  @Command(
      name = "preset",
      description =
          "Applies a window/level preset to a pane and returns its display state, or lists the "
              + "presets of its image when none is given. The presets belong to the image, so the "
              + "pane given by --view and --pane is selected first.")
  @Since("5.0")
  static class Preset extends AbstractVerb {
    @Mixin ViewOptions target;

    @Option(
        names = "--preset",
        paramLabel = "<preset-id>",
        description = "Preset id, or its name as the viewer shows it.")
    @Since("5.0")
    PresetId preset;

    @Override
    protected void run() {
      print(
          GuiExecutor.invokeAndRethrow(
              () -> {
                Target resolved = target.resolve();
                focus(resolved);
                ComboItemListener<Object> presets = action(ActionW.PRESET);
                if (preset == null) {
                  return presetsJson(presets);
                }
                presets.setSelectedItem(require(presets, preset));
                return state(resolved);
              }));
    }

    private String state(Target resolved) {
      JsonObject state = ViewDisplayState.of(resolved.container(), resolved.view());
      return isJson() ? state.toString() : ViewDisplayState.text(state);
    }

    /** The preset of that id or name, or an error naming the ones the image offers. */
    private static Object require(ComboItemListener<Object> presets, PresetId id) {
      for (Object item : presets.getAllItem()) {
        if (item != null
            && (id.value().equalsIgnoreCase(presetId(item))
                || id.value().equalsIgnoreCase(item.toString()))) {
          return item;
        }
      }
      throw new IllegalArgumentException(
          "Unknown preset '" + id + "', available: " + presetIds(presets));
    }

    /** The configured id, or one derived from the name for a preset the image itself carries. */
    private static String presetId(Object item) {
      if (item instanceof PresetWindowLevel preset) {
        return DicomViewStateAdapter.idOf(preset);
      }
      String slug = item.toString().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
      return slug.replaceAll("^-+|-+$", "");
    }

    private static List<String> presetIds(ComboItemListener<Object> presets) {
      List<String> ids = new ArrayList<>();
      for (Object item : presets.getAllItem()) {
        if (item != null) {
          ids.add(presetId(item));
        }
      }
      return ids;
    }

    private static String presetsJson(ComboItemListener<Object> presets) {
      Object selected = presets.getSelectedItem();
      JsonArrayBuilder array = Json.createArrayBuilder();
      for (Object item : presets.getAllItem()) {
        if (item == null) {
          continue;
        }
        JsonObjectBuilder o =
            Json.createObjectBuilder()
                .add("preset", presetId(item)) // NON-NLS
                .add("name", item.toString()) // NON-NLS
                .add("selected", item.equals(selected)); // NON-NLS
        if (item instanceof PresetWindowLevel p) {
          o.add("window", p.getWindow()).add("level", p.getLevel()); // NON-NLS
        }
        array.add(o);
      }
      return Json.createObjectBuilder().add("presets", array).build().toString(); // NON-NLS
    }
  }

  @Command(
      name = "rotate",
      description =
          "Turns the image of a pane clockwise and returns its display state; with no argument the "
              + "state is returned unchanged.")
  @Since("5.0")
  static class Rotate extends DisplayVerb {
    @Option(
        names = "--degrees",
        paramLabel = "<degrees>",
        description = "Absolute angle, clockwise; any integer, reduced to 0-359.")
    Degrees degrees;

    @Option(
        names = "--by",
        paramLabel = "<degrees>",
        description = "Turns by this many degrees, negative counter-clockwise.")
    @Since("5.0")
    Integer by;

    @Override
    boolean hasChange() {
      return degrees != null || by != null;
    }

    @Override
    void validate() {
      if (degrees != null && by != null) {
        throw invalid("Give --degrees or --by, not both");
      }
    }

    @Override
    void apply(Target target) {
      SliderChangeListener rotation = action(ActionW.ROTATION);
      int value = degrees != null ? degrees.value() : rotation.getSliderValue() + by;
      rotation.setSliderValue(new Degrees(value).value());
    }
  }

  @Command(
      name = "sort",
      description =
          "Orders the images of the stack a pane shows, by a sort key and a direction, and returns "
              + "the display state ('sort', 'inverseStack'); with no argument the state is "
              + "returned unchanged. 'slice-position' orders along the positive patient axis "
              + "whatever the acquisition direction (feet to head for an axial stack, right to "
              + "left, front to back), so --inverse true reads head to feet.")
  @Since("5.0")
  static class Sort extends DisplayVerb {
    @Option(
        names = "--by",
        paramLabel = "<sort-key>",
        description =
            "instance, slice-position, slice-location, content-time, acquisition-time, b-value.")
    String by;

    @Option(
        names = "--inverse",
        paramLabel = "<true|false>",
        arity = "1",
        description = "Reads the stack backwards.")
    Boolean inverse;

    @Override
    boolean hasChange() {
      return by != null || inverse != null;
    }

    @Override
    void validate() {
      if (by != null && SortSeriesStack.fromKey(by).isEmpty()) {
        throw invalid("Unknown sort key: " + by);
      }
    }

    @Override
    void apply(Target target) {
      if (by != null) {
        ComboItemListener<?> sort = action(ActionW.SORT_STACK);
        sort.setSelectedItem(SortSeriesStack.fromKey(by).orElseThrow());
      }
      if (inverse != null) {
        ToggleButtonListener direction = action(ActionW.INVERSE_STACK);
        direction.setSelected(inverse);
      }
    }
  }

  enum Switch {
    ON,
    OFF;

    @Override
    public String toString() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  /** The axis a flip mirrors the image about. */
  enum FlipAxis {
    HORIZONTAL,
    VERTICAL;

    @Override
    public String toString() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  @Command(
      name = "flip",
      description =
          "Mirrors the image of a pane and returns its display state; with no argument the state "
              + "is returned unchanged. A vertical flip is the horizontal one turned by 180°, the "
              + "form DICOM presentation states use.")
  @Since("5.0")
  static class Flip extends DisplayVerb {
    @Parameters(arity = "0..1", paramLabel = "on|off", description = "${COMPLETION-CANDIDATES}.")
    Switch state;

    @Option(
        names = "--axis",
        paramLabel = "<flip-axis>",
        defaultValue = "horizontal",
        description = "horizontal or vertical (default: ${DEFAULT-VALUE}).")
    @Since("5.0")
    FlipAxis axis;

    @Override
    boolean hasChange() {
      return state != null;
    }

    @Override
    void apply(Target target) {
      boolean on = state == Switch.ON;
      action(ActionW.FLIP).setSelected(on);
      if (axis == FlipAxis.VERTICAL) {
        SliderChangeListener rotation = action(ActionW.ROTATION);
        int half = on ? 180 : -180;
        rotation.setSliderValue(new Degrees(rotation.getSliderValue() + half).value());
      }
    }
  }

  @Command(
      name = "annotate",
      description =
          "Adds a text annotation on the AI layer of a pane: with --at it points at that place with "
              + "an arrow, with --label-at alone it is a caption, text where you put it and nothing "
              + "else drawn.")
  @Since("5.0")
  static class Annotate extends AbstractVerb {
    private static final double LABEL_OFFSET = 30;

    @Mixin ViewOptions target;

    @Option(
        names = "--at",
        paramLabel = "<image-point>",
        description = "Place the annotation points at; without it the text stands alone.")
    ImagePoint at;

    @Option(names = "--text", required = true, paramLabel = "<text>", description = "Text to show.")
    String text;

    @Option(
        names = "--label-at",
        paramLabel = "<image-point>",
        description = "Position of the text itself (default: above and right of --at).")
    ImagePoint labelAt;

    @Option(
        names = "--canvas",
        description = "Coordinates are canvas pixels (as in a capture) instead of image pixels.")
    boolean canvasCoordinates;

    @Override
    protected void run() {
      if (text.isBlank()) {
        throw invalid("--text must not be blank");
      }
      if (at == null && labelAt == null) {
        throw invalid("Give --at to point at a place, or --label-at to write a caption");
      }
      String body =
          GuiExecutor.invokeAndRethrow(
              () -> {
                Target resolved = target.withImage();
                DefaultView2d<DicomImageElement> view = resolved.view();
                Point2D anchorPt = at == null ? null : toImage(view, at, canvasCoordinates);
                Point2D labelPt =
                    labelAt == null
                        ? new Point2D.Double(
                            anchorPt.getX() + LABEL_OFFSET, anchorPt.getY() - LABEL_OFFSET)
                        : toImage(view, labelAt, canvasCoordinates);
                AnnotationGraphic graphic = new AnnotationGraphic();
                try {
                  // a null anchor is the caption form: the graphic then draws the label alone
                  graphic.buildGraphic(Arrays.asList(anchorPt, labelPt));
                } catch (InvalidShapeException e) {
                  throw new IllegalArgumentException("Invalid annotation: " + e.getMessage(), e);
                }
                graphic.setLabels(new String[] {text});
                GraphicLayer layer = aiLayer(view, LayerType.ANNOTATION);
                AbstractGraphicModel.addGraphicToModel(view, layer, graphic);
                view.getJComponent().repaint();
                return Json.createObjectBuilder()
                    .add("target", ViewDisplayState.target(resolved.container(), view)) // NON-NLS
                    .add("id", graphic.getUuid()) // NON-NLS
                    .add("layer", layerJson(layer)) // NON-NLS
                    .add("text", text) // NON-NLS
                    .add("arrow", anchorPt != null) // NON-NLS
                    .build()
                    .toString();
              });
      print(body);
    }
  }

  @Command(
      name = "graphics",
      description =
          "Lists the graphics of the view: id, type, layer, vertices and measurements, so measured "
              + "values come from the viewer rather than from a picture.")
  @Since("5.0")
  static class Graphics extends AbstractVerb {
    @Mixin ViewOptions target;

    @Option(
        names = "--layer",
        paramLabel = "<layer-name>",
        description = "Only the graphics of this layer name, e.g. AI.")
    LayerName layerName;

    @Option(names = "--no-measurements", description = "Skip the measurement values.")
    boolean noMeasurements;

    @Mixin ListOptions bounds;

    @Override
    protected void run() {
      String body =
          GuiExecutor.invokeAndRethrow(
              () -> {
                Target resolved = target.withImage();
                DefaultView2d<DicomImageElement> view = resolved.view();
                var measurable = view.getMeasurableLayer();
                Unit spatial = spatialUnit();
                String values = measurable.getPixelValueUnit();
                List<JsonObject> graphics = new ArrayList<>();
                for (Graphic graphic : view.getGraphicManager().getAllGraphics()) {
                  GraphicLayer graphicLayer = graphic.getLayer();
                  String name = graphicLayer == null ? null : graphicLayer.getName();
                  if (layerName != null && !layerName.value().equalsIgnoreCase(name)) {
                    continue;
                  }
                  JsonObjectBuilder o =
                      Json.createObjectBuilder()
                          .add("id", graphic.getUuid()) // NON-NLS
                          .add("type", typeName(graphic)); // NON-NLS
                  if (graphicLayer != null) {
                    o.add("layer", layerJson(graphicLayer)); // NON-NLS
                  }
                  JsonArrayBuilder points = Json.createArrayBuilder();
                  for (Point2D p : graphic.getPts()) {
                    if (p != null) {
                      points.add(Json.createArrayBuilder().add(p.getX()).add(p.getY()));
                    }
                  }
                  o.add("points", points); // NON-NLS
                  String[] labels =
                      graphic.getGraphicLabel() == null
                          ? null
                          : graphic.getGraphicLabel().getLabels();
                  if (labels != null && labels.length > 0) {
                    JsonArrayBuilder l = Json.createArrayBuilder();
                    for (String label : labels) {
                      l.add(label);
                    }
                    o.add("labels", l); // NON-NLS
                  }
                  if (!noMeasurements) {
                    o.add(
                        "measurements", // NON-NLS
                        measurementsJson(
                            Measurement.computingAll(
                                () -> graphic.computeMeasurements(measurable, true, spatial)),
                            spatial,
                            values));
                  }
                  graphics.add(o.build());
                }
                JsonObjectBuilder listing =
                    Json.createObjectBuilder()
                        .add(
                            "target", // NON-NLS
                            ViewDisplayState.target(resolved.container(), view));
                return JsonUtil.window(listing, "graphics", graphics, bounds) // NON-NLS
                    .build()
                    .toString();
              });
      print(body);
    }

    /**
     * The name of a graphic in the vocabulary of {@code measure --type}: {@code LineGraphic} gives
     * {@code line}, {@code OpenAngleToolGraphic} gives {@code open-angle}, and the two circle tools
     * both give {@code circle}.
     */
    static String typeName(Graphic graphic) {
      String name = SemanticType.nameOf(graphic.getClass());
      for (String suffix : List.of("-tool-graphic", "-graphic")) { // NON-NLS
        if (name.endsWith(suffix)) {
          name = name.substring(0, name.length() - suffix.length());
          break;
        }
      }
      if (name.isEmpty()) {
        return "graphic"; // NON-NLS
      }
      return "three-points-circle".equals(name) ? "circle" : name; // NON-NLS
    }
  }
}
