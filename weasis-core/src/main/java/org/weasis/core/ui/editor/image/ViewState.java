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
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import java.beans.PropertyChangeEvent;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.PseudoColorOp;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.image.lut.ColorMapRegistry;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Taggable;
import org.weasis.core.ui.editor.SeriesResolver;
import org.weasis.core.ui.editor.image.DefaultView2d.ZoomType;
import org.weasis.opencv.op.lut.ByteLut;
import org.weasis.opencv.op.lut.ColorLut;
import org.weasis.opencv.op.lut.LutShape;

/**
 * State of one pane in two parts. The {@link ViewData data} names what the pane shows: the series,
 * the image, the instance. It is patient data and is what a session binds. The {@link
 * ViewPresentation view} says how the pane shows it and holds no identifier: zoom, pan, rotation,
 * flip, window/level or preset id, LUT shape, color map id, inversion, stack order, and a position
 * rule in the stack. A session is the combination, restored exactly; the view part alone is a
 * display template that an adapter applies to other data, solving the differences (image count,
 * pixel size, modality) with the rules in {@link ViewPresentation#applyTo}.
 *
 * <p>Values that refer to registries are ids, never copied definitions. {@link #capture} reads a
 * pane; applying drives it through the same events the synchronization uses.
 */
public record ViewState(ViewData data, ViewPresentation view, Blend blend) {

  /** The state of an empty pane with the default presentation. */
  public static final ViewState EMPTY =
      new ViewState(ViewData.NONE, ViewPresentation.DEFAULT, null);

  public ViewState {
    if (data == null) {
      data = ViewData.NONE;
    }
    if (view == null) {
      view = ViewPresentation.DEFAULT;
    }
  }

  /** A state without a blended series. */
  public ViewState(ViewData data, ViewPresentation view) {
    this(data, view, null);
  }

  /** Reads the state of a pane; an empty pane gives {@link #EMPTY}. */
  public static <E extends ImageElement> ViewState capture(ViewCanvas<E> canvas) {
    MediaSeries<E> series = canvas.getSeries();
    if (series == null) {
      return EMPTY;
    }
    return new ViewState(
        ViewData.capture(canvas),
        ViewPresentation.capture(canvas),
        canvas.getViewStateAdapter().blend(canvas).orElse(null));
  }

  /** Restores the pane exactly, without its blended series (no resolver to find it). */
  public <E extends ImageElement> void apply(ViewCanvas<E> canvas) {
    apply(canvas, id -> Optional.empty());
  }

  /**
   * Restores the pane exactly: the pane already shows {@link ViewData#series()}; the blended
   * series, when there is one, is found through the resolver and blended by the adapter.
   */
  @SuppressWarnings("unchecked")
  public <E extends ImageElement> void apply(ViewCanvas<E> canvas, SeriesResolver resolver) {
    view.applyTo(canvas, data);
    if (blend != null && blend.overlay() != null && canvas.getSeries() != null) {
      resolver
          .resolve(blend.overlay())
          .ifPresent(
              overlay ->
                  canvas
                      .getViewStateAdapter()
                      .blend(canvas, (MediaSeries<E>) overlay, blend.params()));
    }
  }

  public JsonObject toJson() {
    JsonObjectBuilder json = Json.createObjectBuilder();
    if (data.series() != null) {
      json.add("data", data.toJson()); // NON-NLS
    }
    json.add("view", view.toJson()); // NON-NLS
    if (blend != null) {
      json.add("blend", blend.toJson()); // NON-NLS
    }
    return json.build();
  }

  /** Reads a state written by {@link #toJson()}; missing keys take their default value. */
  public static ViewState fromJson(JsonObject json) {
    JsonObject data = json.getJsonObject("data"); // NON-NLS
    JsonObject view = json.getJsonObject("view"); // NON-NLS
    JsonObject blend = json.getJsonObject("blend"); // NON-NLS
    return new ViewState(
        data == null ? ViewData.NONE : ViewData.fromJson(data),
        view == null ? ViewPresentation.DEFAULT : ViewPresentation.fromJson(view),
        blend == null ? null : Blend.fromJson(blend));
  }

  // ---- blend part --------------------------------------------------------------------------

  /**
   * A second series blended over the shown one (fusion): the id of that series is data, the
   * parameters of the blend (color map, window, opacities) are presentation the adapter of the
   * viewer type owns, opaque here.
   */
  public record Blend(String overlay, JsonObject params) {

    public Blend {
      if (params == null) {
        params = Json.createObjectBuilder().build();
      }
    }

    public JsonObject toJson() {
      JsonObjectBuilder json = Json.createObjectBuilder();
      if (overlay != null) {
        json.add("overlay", overlay); // NON-NLS
      }
      return json.add("params", params).build(); // NON-NLS
    }

    public static Blend fromJson(JsonObject json) {
      return new Blend(json.getString("overlay", null), json.getJsonObject("params")); // NON-NLS
    }
  }

  // ---- data part ---------------------------------------------------------------------------

  /**
   * What a pane shows: the series id, the 1-based position of the image in the sorted stack, and
   * the instance id and frame of that image when the media gives them.
   */
  public record ViewData(String series, int image, String instance, Integer frame) {

    public static final ViewData NONE = new ViewData(null, 0, null, null);

    static <E extends ImageElement> ViewData capture(ViewCanvas<E> canvas) {
      MediaSeries<E> series = canvas.getSeries();
      Object seriesId = series.getTagValue(series.getTagID());
      ImageElement image = canvas.getImage();
      if (image == null) {
        return new ViewData(seriesId == null ? null : seriesId.toString(), 0, null, null);
      }
      Object instance = tagValue(image, "SOPInstanceUID"); // NON-NLS
      Integer frames = tagValue(image, "NumberOfFrames") instanceof Integer n ? n : null; // NON-NLS
      Integer frame =
          frames != null && frames > 1 && image.getKey() instanceof Integer index
              ? index + 1
              : null;
      return new ViewData(
          seriesId == null ? null : seriesId.toString(),
          canvas.getFrameIndex() + 1,
          instance == null ? null : instance.toString(),
          frame);
    }

    public JsonObject toJson() {
      JsonObjectBuilder json = Json.createObjectBuilder();
      if (series != null) {
        json.add("series", series); // NON-NLS
      }
      json.add("image", image); // NON-NLS
      if (instance != null) {
        json.add("instance", instance); // NON-NLS
      }
      if (frame != null) {
        json.add("frame", frame); // NON-NLS
      }
      return json.build();
    }

    public static ViewData fromJson(JsonObject json) {
      return new ViewData(
          json.getString("series", null), // NON-NLS
          json.getInt("image", 0), // NON-NLS
          json.getString("instance", null), // NON-NLS
          json.containsKey("frame") ? json.getInt("frame") : null); // NON-NLS
    }
  }

  // ---- view part ---------------------------------------------------------------------------

  /** Where the shown image sits in its stack, independent of the image count. */
  public record Position(double relative, Double location) {
    public static final Position START = new Position(0, null);
  }

  /** What the presentation was captured on, for the adapter to judge compatibility. */
  public record Context(String modality, Double pixelSize) {
    public static final Context UNKNOWN = new Context(null, null);
  }

  /**
   * How a pane presents, without any identifier of the data.
   *
   * @param position where the image sits in the stack, used when no exact image is given
   * @param context modality and pixel size of the capture, for the adapter
   * @param zoomType the zoom mode
   * @param zoom the scale when the user set it explicitly, 0 when the zoom mode derives it
   * @param panX horizontal model offset, applied with an explicit zoom only
   * @param panY vertical model offset, applied with an explicit zoom only
   * @param rotation degrees
   * @param flip horizontal flip
   * @param window window width, {@code null} without a window operation
   * @param level window center, {@code null} without a window operation
   * @param preset id of the applied preset, {@code null} for free values
   * @param lutShape name of the LUT function, {@code null} for the default or a custom table
   * @param colorMap id of the color map, {@code null} for the image's own
   * @param invert whether the LUT is inverted
   * @param sort key of the stack order, {@code null} for the default
   * @param inverseStack whether the stack order is reversed
   */
  public record ViewPresentation(
      Position position,
      Context context,
      ZoomType zoomType,
      double zoom,
      double panX,
      double panY,
      int rotation,
      boolean flip,
      Double window,
      Double level,
      String preset,
      String lutShape,
      String colorMap,
      boolean invert,
      String sort,
      boolean inverseStack) {

    public static final ViewPresentation DEFAULT =
        new ViewPresentation(
            Position.START,
            Context.UNKNOWN,
            ZoomType.BEST_FIT,
            0,
            0,
            0,
            0,
            false,
            null,
            null,
            null,
            null,
            null,
            false,
            null,
            false);

    /** Pixel sizes closer than this fraction are treated as equal by the adapter. */
    private static final double PIXEL_SIZE_TOLERANCE = 0.01;

    public ViewPresentation {
      if (position == null) {
        position = Position.START;
      }
      if (context == null) {
        context = Context.UNKNOWN;
      }
      if (zoomType == null) {
        zoomType = ZoomType.BEST_FIT;
      }
    }

    @SuppressWarnings("unchecked")
    static <E extends ImageElement> ViewPresentation capture(ViewCanvas<E> canvas) {
      MediaSeries<E> series = canvas.getSeries();
      ViewStateAdapter<E> adapter = canvas.getViewStateAdapter();
      OpManager manager = canvas.getDisplayOpManager();
      ImageElement image = canvas.getImage();
      if (manager == null) {
        // a canvas without display operations (volume rendering) has no 2D presentation
        return new ViewPresentation(
            position(canvas, series, image),
            context(series, image),
            ZoomType.BEST_FIT,
            0,
            0,
            0,
            0,
            false,
            null,
            null,
            null,
            null,
            null,
            false,
            null,
            false);
      }
      return new ViewPresentation(
          position(canvas, series, image),
          context(series, image),
          canvas.getActionValue(ViewCanvas.ZOOM_TYPE_CMD) instanceof ZoomType type
              ? type
              : ZoomType.BEST_FIT,
          explicitZoom(canvas),
          canvas.getViewModel().getModelOffsetX(),
          canvas.getViewModel().getModelOffsetY(),
          canvas.getActionValue(ActionW.ROTATION.cmd()) instanceof Integer degrees ? degrees : 0,
          Boolean.TRUE.equals(canvas.getActionValue(ActionW.FLIP.cmd())),
          manager.getParamValue(WindowOp.OP_NAME, ActionW.WINDOW.cmd(), Double.class).orElse(null),
          manager.getParamValue(WindowOp.OP_NAME, ActionW.LEVEL.cmd(), Double.class).orElse(null),
          manager
              .getParamValue(WindowOp.OP_NAME, ActionW.PRESET.cmd())
              .flatMap(adapter::presetId)
              .orElse(null),
          manager
              .getParamValue(WindowOp.OP_NAME, ActionW.LUT_SHAPE.cmd(), LutShape.class)
              .filter(LutShape::isFunction)
              .map(shape -> shape.getFunctionType().name())
              .orElse(null),
          manager
              .getParamValue(PseudoColorOp.OP_NAME, PseudoColorOp.P_LUT, ByteLut.class)
              .map(ViewPresentation::colorMapId)
              .orElse(null),
          manager
              .getParamValue(WindowOp.OP_NAME, ActionW.INVERT_LUT.cmd(), Boolean.class)
              .orElse(false),
          canvas.getActionValue(ActionW.SORT_STACK.cmd()) instanceof Comparator<?> comparator
              ? adapter.sortKey(comparator).orElse(null)
              : null,
          Boolean.TRUE.equals(canvas.getActionValue(ActionW.INVERSE_STACK.cmd())));
    }

    @SuppressWarnings("unchecked")
    private static <E extends ImageElement> Position position(
        ViewCanvas<E> canvas, MediaSeries<E> series, ImageElement image) {
      if (image == null) {
        return Position.START;
      }
      int count = series.size((Filter<E>) canvas.getActionValue(ActionW.FILTERED_SERIES.cmd()));
      double relative = count > 1 ? (double) canvas.getFrameIndex() / (count - 1) : 0;
      Object location = tagValue(image, "SliceLocation"); // NON-NLS
      return new Position(
          Math.clamp(relative, 0, 1), location instanceof Number n ? n.doubleValue() : null);
    }

    private static Context context(MediaSeries<?> series, ImageElement image) {
      Object modality = tagValue(series, "Modality"); // NON-NLS
      if (modality == null && image != null) {
        modality = tagValue(image, "Modality"); // NON-NLS
      }
      return new Context(
          modality == null ? null : modality.toString(),
          image == null ? null : image.getPixelSize());
    }

    /**
     * Drives a pane showing some series to this presentation. With {@code data} the restore is
     * exact (a session): the image by index, the scale and the window values as stored. Without it
     * the pane holds other data (a template) and the adapter decides: the image by slice location
     * or relative position, the explicit scale only for the same pixel size, the window values only
     * for the same modality, the preset by id when the image offers it, color map and stack order
     * by id when known. A template only navigates a pane that owns its presentation (see {@link
     * ViewStateAdapter#ownsPresentation}).
     */
    public <E extends ImageElement> void applyTo(ViewCanvas<E> canvas, ViewData data) {
      applyTo(canvas, data, true);
    }

    /**
     * As {@link #applyTo(ViewCanvas, ViewData)}; without {@code navigate} the stack order and the
     * shown image are left alone, for panes whose image is chosen elsewhere (the planes of an MPR
     * follow the crosshair).
     */
    @SuppressWarnings("unchecked")
    public <E extends ImageElement> void applyTo(
        ViewCanvas<E> canvas, ViewData data, boolean navigate) {
      MediaSeries<E> series = canvas.getSeries();
      if (series == null) {
        return;
      }
      boolean exact = data != null && data.image() > 0;
      ViewStateAdapter<E> adapter = canvas.getViewStateAdapter();
      Filter<E> filter = (Filter<E>) canvas.getActionValue(ActionW.FILTERED_SERIES.cmd());

      if (navigate) {
        SynchEvent order = new SynchEvent(canvas);
        Optional.ofNullable(sort)
            .flatMap(adapter::sort)
            .ifPresent(comparator -> order.put(ActionW.SORT_STACK.cmd(), comparator));
        order.put(ActionW.INVERSE_STACK.cmd(), inverseStack);
        fire(canvas, order);

        Picked<E> picked =
            exact
                ? exactImage(canvas, series, data, filter)
                : positionedImage(canvas, series, filter);
        if (picked != null && picked.media() != canvas.getImage()) {
          fire(
              canvas,
              new SynchCineEvent(canvas, picked.media(), picked.index() - canvas.getTileOffset()));
        }
      }

      if (!exact && adapter.ownsPresentation(canvas)) {
        // A template keeps the zoom, orientation and rendering of a presentation state
        return;
      }

      boolean sameScale = exact || samePixelSize(canvas.getImage());
      canvas.setActionsInView(ViewCanvas.ZOOM_TYPE_CMD, zoomType);
      SynchEvent framing = new SynchEvent(canvas);
      // zoom(0) derives the scale from the zoom type; an explicit scale is applied as such
      framing.put(ActionW.ZOOM.cmd(), zoom > 0 && sameScale ? zoom : 0.0);
      framing.put(ActionW.ROTATION.cmd(), rotation);
      framing.put(ActionW.FLIP.cmd(), flip);
      fire(canvas, framing);
      if (zoom > 0 && sameScale) {
        // the model keeps the offsets of a view not laid out yet; setCenter refreshes a shown one
        canvas.getViewModel().setModelOffset(panX, panY);
        canvas.setCenter(panX, panY);
      }

      // the preset first (it also carries the LUT shape and stays the named preset of the pane),
      // then the stored values, which may have been adjusted after the preset was chosen
      SynchEvent rendering = new SynchEvent(canvas);
      Object presetValue = preset == null ? null : adapter.preset(canvas, preset).orElse(null);
      if (presetValue != null) {
        rendering.put(ActionW.PRESET.cmd(), presetValue);
      }
      colorMapOf(colorMap).ifPresent(lut -> rendering.put(ActionW.LUT.cmd(), lut));
      rendering.put(ActionW.INVERT_LUT.cmd(), invert);
      fire(canvas, rendering);

      SynchEvent values = new SynchEvent(canvas);
      if (window != null && level != null && (exact || sameModality(series, canvas.getImage()))) {
        values.put(ActionW.WINDOW.cmd(), window);
        values.put(ActionW.LEVEL.cmd(), level);
      }
      LutShape shape = presetValue == null ? LutShape.getLutShape(lutShape) : null;
      if (shape != null) {
        values.put(ActionW.LUT_SHAPE.cmd(), shape);
      }
      if (!values.getEvents().isEmpty()) {
        fire(canvas, values);
      }
    }

    /** An image of the stack and its index in the current order. */
    private record Picked<E extends ImageElement>(E media, int index) {}

    private static <E extends ImageElement> Picked<E> exactImage(
        ViewCanvas<E> canvas, MediaSeries<E> series, ViewData data, Filter<E> filter) {
      int index = data.image() - 1;
      E media = series.getMedia(index, filter, canvas.getCurrentSortComparator());
      return media == null ? null : new Picked<>(media, index);
    }

    /** The image at the slice location when both sides have one, else at the relative position. */
    private <E extends ImageElement> Picked<E> positionedImage(
        ViewCanvas<E> canvas, MediaSeries<E> series, Filter<E> filter) {
      Comparator<E> sort = canvas.getCurrentSortComparator();
      if (position.location() != null
          && tagValue(canvas.getImage(), "SliceLocation") != null // NON-NLS
          && series instanceof Series<E> stack) {
        E nearest = series.getNearestImage(position.location(), 0, filter, sort);
        if (nearest != null) {
          return new Picked<>(nearest, stack.getImageIndex(nearest, filter, sort));
        }
      }
      int count = series.size(filter);
      if (count <= 1) {
        return null;
      }
      int index = Math.clamp(Math.round(position.relative() * (count - 1)), 0, count - 1);
      E media = series.getMedia(index, filter, sort);
      return media == null ? null : new Picked<>(media, index);
    }

    private boolean samePixelSize(ImageElement image) {
      if (context.pixelSize() == null || image == null) {
        return false;
      }
      double size = image.getPixelSize();
      return size > 0
          && Math.abs(size - context.pixelSize()) <= PIXEL_SIZE_TOLERANCE * context.pixelSize();
    }

    private boolean sameModality(MediaSeries<?> series, ImageElement image) {
      Object modality = tagValue(series, "Modality"); // NON-NLS
      if (modality == null && image != null) {
        modality = tagValue(image, "Modality"); // NON-NLS
      }
      return context.modality() != null
          && modality != null
          && context.modality().equals(modality.toString());
    }

    /**
     * The scale the user set, or 0 when the zoom mode computed it: the zoom action keeps a negative
     * value for a derived scale (see {@code Canvas.getZoomRatioFromType}).
     */
    private static double explicitZoom(ViewCanvas<?> canvas) {
      return canvas.getActionValue(ActionW.ZOOM.cmd()) instanceof Double ratio && ratio > 0
          ? canvas.getViewModel().getViewScale()
          : 0;
    }

    /** The registry id of a color map, or the name of a built-in LUT. */
    private static String colorMapId(ByteLut lut) {
      return lut.source() != null ? lut.source().id() : lut.name();
    }

    private static Optional<ByteLut> colorMapOf(String id) {
      return ViewState.colorMapOf(id);
    }

    public JsonObject toJson() {
      JsonObjectBuilder json = Json.createObjectBuilder();
      JsonObjectBuilder pos =
          Json.createObjectBuilder().add("relative", position.relative()); // NON-NLS
      if (position.location() != null) {
        pos.add("location", position.location()); // NON-NLS
      }
      json.add("position", pos); // NON-NLS
      JsonObjectBuilder ctx = Json.createObjectBuilder();
      if (context.modality() != null) {
        ctx.add("modality", context.modality()); // NON-NLS
      }
      if (context.pixelSize() != null) {
        ctx.add("pixelSize", context.pixelSize()); // NON-NLS
      }
      json.add("context", ctx) // NON-NLS
          .add("zoomType", zoomType.name().toLowerCase(Locale.ROOT)) // NON-NLS
          .add("zoom", zoom) // NON-NLS
          .add("pan", Json.createObjectBuilder().add("x", panX).add("y", panY)) // NON-NLS
          .add("rotation", rotation) // NON-NLS
          .add("flip", flip); // NON-NLS
      if (window != null && level != null) {
        JsonObjectBuilder wl =
            Json.createObjectBuilder().add("window", window).add("level", level); // NON-NLS
        if (preset != null) {
          wl.add("preset", preset); // NON-NLS
        }
        if (lutShape != null) {
          wl.add("shape", lutShape); // NON-NLS
        }
        json.add("windowLevel", wl); // NON-NLS
      }
      if (colorMap != null) {
        json.add("colorMap", colorMap); // NON-NLS
      }
      json.add("invert", invert); // NON-NLS
      JsonObjectBuilder stack = Json.createObjectBuilder();
      if (sort != null) {
        stack.add("sort", sort); // NON-NLS
      }
      stack.add("inverse", inverseStack); // NON-NLS
      return json.add("stack", stack).build(); // NON-NLS
    }

    public static ViewPresentation fromJson(JsonObject json) {
      JsonObject pos = json.getJsonObject("position"); // NON-NLS
      JsonObject ctx = json.getJsonObject("context"); // NON-NLS
      JsonObject pan = json.getJsonObject("pan"); // NON-NLS
      JsonObject wl = json.getJsonObject("windowLevel"); // NON-NLS
      JsonObject stack = json.getJsonObject("stack"); // NON-NLS
      return new ViewPresentation(
          pos == null
              ? Position.START
              : new Position(number(pos, "relative"), optionalNumber(pos, "location")), // NON-NLS
          ctx == null
              ? Context.UNKNOWN
              : new Context(
                  ctx.getString("modality", null), optionalNumber(ctx, "pixelSize")), // NON-NLS
          zoomType(json.getString("zoomType", null)), // NON-NLS
          number(json, "zoom"), // NON-NLS
          pan == null ? 0 : number(pan, "x"), // NON-NLS
          pan == null ? 0 : number(pan, "y"), // NON-NLS
          json.getInt("rotation", 0), // NON-NLS
          json.getBoolean("flip", false), // NON-NLS
          wl == null ? null : number(wl, "window"), // NON-NLS
          wl == null ? null : number(wl, "level"), // NON-NLS
          wl == null ? null : wl.getString("preset", null), // NON-NLS
          wl == null ? null : wl.getString("shape", null), // NON-NLS
          json.getString("colorMap", null), // NON-NLS
          json.getBoolean("invert", false), // NON-NLS
          stack == null ? null : stack.getString("sort", null), // NON-NLS
          stack != null && stack.getBoolean("inverse", false)); // NON-NLS
    }

    private static ZoomType zoomType(String name) {
      if (name == null) {
        return ZoomType.BEST_FIT;
      }
      try {
        return ZoomType.valueOf(name.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
        return ZoomType.BEST_FIT;
      }
    }
  }

  /** The color map of that id: a built-in LUT by name, else one of the registry. */
  public static Optional<ByteLut> colorMapOf(String id) {
    if (id == null) {
      return Optional.empty();
    }
    for (ColorLut lut : ColorLut.values()) {
      if (id.equals(lut.getByteLut().name())) {
        return Optional.of(lut.getByteLut());
      }
    }
    ColorMapRegistry registry = ColorMapRegistry.getInstance();
    return registry.findById(id).map(registry::byteLut);
  }

  // ---- helpers -----------------------------------------------------------------------------

  private static void fire(ViewCanvas<?> canvas, SynchEvent event) {
    canvas.propertyChange(new PropertyChangeEvent(canvas, ActionW.SYNCH.cmd(), null, event));
  }

  /** A tag value by keyword, or {@code null} when the tag is unknown here or absent. */
  private static Object tagValue(Taggable taggable, String keyword) {
    if (taggable == null) {
      return null;
    }
    TagW tag = TagW.get(keyword);
    return tag == null ? null : taggable.getTagValue(tag);
  }

  private static double number(JsonObject json, String key) {
    Double value = optionalNumber(json, key);
    return value == null ? 0 : value;
  }

  private static Double optionalNumber(JsonObject json, String key) {
    return json.get(key) instanceof JsonNumber n && n.getValueType() == JsonValue.ValueType.NUMBER
        ? n.doubleValue()
        : null;
  }
}
