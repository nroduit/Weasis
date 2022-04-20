/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.util;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.weasis.core.util.StringUtil;

public class ResourceUtil {

  public static final int TOOLBAR_ICON_SIZE = 30;

  public interface ResourcePath {
    String getPath();
  }

  public interface ResourceIconPath extends ResourcePath {}

  public enum LogoIcon implements ResourceIconPath {
    LARGE("svg/logo/WeasisAbout.svg"), // NON-NLS
    SMALL("svg/logo/Weasis.svg"), // NON-NLS
    SMALL_DICOMIZER("svg/logo/Dicomizer.svg"); // NON-NLS

    private final String path;

    LogoIcon(String path) {
      this.path = path;
    }

    public String getPath() {
      return path;
    }
  }

  public enum FileIcon implements ResourceIconPath {
    AUDIO("svg/file/audio.svg"), // NON-NLS
    DICOM("svg/file/dicom.svg"), // NON-NLS
    ECG("svg/file/ecg.svg"), // NON-NLS
    IMAGE("svg/file/image.svg"), // NON-NLS
    PDF("svg/file/pdf.svg"), // NON-NLS
    TEXT("svg/file/text.svg"), // NON-NLS
    UNKNOWN("svg/file/unknown.svg"), // NON-NLS
    VIDEO("svg/file/video.svg"), // NON-NLS
    XML("svg/file/xml.svg"); // NON-NLS

    private final String path;

    FileIcon(String path) {
      this.path = path;
    }

    public String getPath() {
      return path;
    }
  }

  public enum ActionIcon implements ResourceIconPath {
    CONTEXT_MENU("svg/action/contextMenu.svg"), // NON-NLS
    CROSSHAIR("svg/action/crosshair.svg"), // NON-NLS
    DRAW("svg/action/draw.svg"), // NON-NLS
    DRAW_4POINTS_ANGLE("svg/action/draw4PointsAngle.svg"), // NON-NLS
    DRAW_ANGLE("svg/action/drawAngle.svg"), // NON-NLS
    DRAW_CIRCLE("svg/action/drawCircle.svg"), // NON-NLS
    DRAW_COBB("svg/action/drawCobb.svg"), // NON-NLS
    DRAW_ELLIPSE("svg/action/drawEllipse.svg"), // NON-NLS
    DRAW_LINE("svg/action/drawLine.svg"), // NON-NLS
    DRAW_OPEN_ANGLE("svg/action/drawOpenAngle.svg"), // NON-NLS
    DRAW_PARALLEL("svg/action/drawParallel.svg"), // NON-NLS
    DRAW_PERPENDICULAR("svg/action/drawPerpendicular.svg"), // NON-NLS
    DRAW_PIXEL_INFO("svg/action/drawPixelInfo.svg"), // NON-NLS
    DRAW_POLYGON("svg/action/drawPolygon.svg"), // NON-NLS //NON-NLS
    DRAW_POLYLINE("svg/action/drawPolyline.svg"), // NON-NLS
    DRAW_RECTANGLE("svg/action/drawRectangle.svg"), // NON-NLS
    DRAW_SELECTION("svg/action/drawSelection.svg"), // NON-NLS
    DRAW_TEXT("svg/action/drawText.svg"), // NON-NLS
    DRAW_TOP_LEFT("svg/action/drawTopLeft.svg"), // NON-NLS
    EDIT_KEY_IMAGE("svg/action/editKeyImage.svg"), // NON-NLS
    EXECUTE("svg/action/execute.svg"), // NON-NLS
    EXPORT_CLIPBOARD("svg/action/exportClipboard.svg"), // NON-NLS
    EXPORT_DICOM("svg/action/exportDicom.svg"), // NON-NLS
    EXPORT_IMAGE("svg/action/exportImage.svg"), // NON-NLS
    FILTER("svg/action/filter.svg"), // NON-NLS
    FLIP("svg/action/flip.svg"), // NON-NLS
    HELP("svg/action/help.svg"), // NON-NLS
    IMPORT_CD("svg/action/importCd.svg"), // NON-NLS
    IMPORT_DICOM("svg/action/importDicom.svg"), // NON-NLS
    IMPORT_IMAGE("svg/action/importImage.svg"), // NON-NLS
    INVERSE_LUT("svg/action/inverseLut.svg"), // NON-NLS
    LAYOUT("svg/action/layout.svg"), // NON-NLS
    LUT("svg/action/lut.svg"), // NON-NLS
    MEASURE("svg/action/measure.svg"), // NON-NLS
    MEASURE_TOP_LEFT("svg/action/measureTopLeft.svg"), // NON-NLS
    METADATA("svg/action/metadata.svg"), // NON-NLS
    MINUS("svg/action/minus.svg"), // NON-NLS
    MORE_H("svg/action/moreHorizontal.svg"), // NON-NLS
    MORE_V("svg/action/moreVertical.svg"), // NON-NLS
    MOUSE_LEFT("svg/action/mouseLeft.svg"), // NON-NLS
    MOUSE_MIDDLE("svg/action/mouseMiddle.svg"), // NON-NLS
    MOUSE_RIGHT("svg/action/mouseRight.svg"), // NON-NLS
    MOUSE_WHEEL("svg/action/mouseWheel.svg"), // NON-NLS
    NEXT("svg/action/next.svg"), // NON-NLS
    NONE("svg/action/none.svg"), // NON-NLS
    OPEN_EXTERNAL("svg/action/openExternal.svg"), // NON-NLS
    OPEN_NEW_TAB("svg/action/openNewTab.svg"), // NON-NLS
    PAN("svg/action/pan.svg"), // NON-NLS
    PIPETTE("svg/action/pipette.svg"), // NON-NLS
    PLUS("svg/action/plus.svg"), // NON-NLS
    PRINT("svg/action/print.svg"), // NON-NLS
    PREVIOUS("svg/action/previous.svg"), // NON-NLS
    RESET("svg/action/reset.svg"), // NON-NLS
    ROTATE_CLOCKWISE("svg/action/rotateClockwise.svg"), // NON-NLS
    ROTATE_COUNTERCLOCKWISE("svg/action/rotateCounterclockwise.svg"), // NON-NLS
    ROTATION("svg/action/rotation.svg"), // NON-NLS
    SELECTION("svg/action/selection.svg"), // NON-NLS
    SELECTION_DELETE("svg/action/selectionDelete.svg"), // NON-NLS
    SEQUENCE("svg/action/sequence.svg"), // NON-NLS
    STAR("svg/action/star.svg"), // NON-NLS
    SKIP_END("svg/action/skipEnd.svg"), // NON-NLS
    SKIP_START("svg/action/skipStart.svg"), // NON-NLS
    STAR_ALL("svg/action/starAll.svg"), // NON-NLS
    SUSPEND("svg/action/suspend.svg"), // NON-NLS
    SYNCH("svg/action/synch.svg"), // NON-NLS
    SYNCH_LARGE("svg/action/synchLarge.svg"), // NON-NLS
    SYNCH_STAR("svg/action/synchStar.svg"), // NON-NLS
    TILE("svg/action/tile.svg"), // NON-NLS
    WINDOW_LEVEL("svg/action/winLevel.svg"), // NON-NLS
    ZOOM("svg/action/zoom.svg"), // NON-NLS
    ZOOM_AREA("svg/action/zoomArea.svg"), // NON-NLS
    ZOOM_BEST_FIT("svg/action/zoomBestFit.svg"), // NON-NLS
    ZOOM_ORIGINAL("svg/action/zoomOriginal.svg"), // NON-NLS
    ZOOM_PAN("svg/action/zoomPan.svg"), // NON-NLS
    ZOOM_REAL_WORLD("svg/action/zoomRealWorld.svg"); // NON-NLS

    private final String path;

    ActionIcon(String path) {
      this.path = path;
    }

    public String getPath() {
      return path;
    }
  }

  public enum OtherIcon implements ResourceIconPath {
    AUDIO("svg/other/audio.svg"), // NON-NLS
    CALENDAR("svg/other/calendar.svg"), // NON-NLS
    CDROM("svg/other/cdrom.svg"), // NON-NLS
    ECG("svg/other/ecg.svg"), // NON-NLS
    HISTOGRAM("svg/other/histogram.svg"), // NON-NLS
    IMAGE_EDIT("svg/other/imageEdit.svg"), // NON-NLS
    IMAGE_PRESENTATION("svg/other/imagePresentation.svg"), // NON-NLS
    KEY_IMAGE("svg/other/keyImage.svg"), // NON-NLS
    PATIENT("svg/other/patient.svg"), // NON-NLS
    RADIOACTIVE("svg/other/radioactive.svg"), // NON-NLS
    RASTER_IMAGE("svg/other/rasterImage.svg"), // NON-NLS
    TICK_OFF("svg/other/tickOff.svg"), // NON-NLS
    TICK_ON("svg/other/tickOn.svg"), // NON-NLS
    VIEW_MIP("svg/other/veins.svg"), // NON-NLS
    VIEW_3D("svg/other/view3D.svg"), // NON-NLS
    VIEW_SETTING("svg/other/viewSettings.svg"), // NON-NLS
    XRAY("svg/other/xray.svg"); // NON-NLS

    private final String path;

    OtherIcon(String path) {
      this.path = path;
    }

    public String getPath() {
      return path;
    }
  }

  private static final AtomicReference<Path> path = new AtomicReference<>(Path.of(""));

  private ResourceUtil() {}

  public static URL getResourceURL(String resource, Class<?> c) {
    URL url = null;
    if (c != null) {
      ClassLoader classLoader = c.getClassLoader();
      if (classLoader != null) {
        url = classLoader.getResource(resource);
      }
    }
    if (url == null) {
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      if (classLoader != null) {
        url = classLoader.getResource(resource);
      }
    }
    if (url == null) {
      url = ClassLoader.getSystemResource(resource);
    }
    return url;
  }

  public static void setResourcePath(String path) {
    if (!StringUtil.hasText(path)) {
      throw new IllegalArgumentException("No value for property: weasis.resources.path");
    }
    ResourceUtil.path.set(Path.of(path));
  }

  private static Path getResourcePath() {
    return path.get();
  }

  public static FlatSVGIcon getIcon(String path) {
    return new FlatSVGIcon(getResourcePath().resolve(path).toUri());
  }

  public static FlatSVGIcon getIcon(ResourceIconPath path) {
    return new FlatSVGIcon(getResourcePath().resolve(path.getPath()).toUri());
  }

  public static FlatSVGIcon getToolBarIcon(ResourceIconPath path) {
    return getIcon(path, TOOLBAR_ICON_SIZE, TOOLBAR_ICON_SIZE);
  }

  public static FlatSVGIcon getIcon(ResourceIconPath path, int width, int height) {
    return new FlatSVGIcon(getResourcePath().resolve(path.getPath()).toUri()).derive(width, height);
  }

  public static File getResource(String filename) {
    if (!StringUtil.hasText(filename)) {
      throw new IllegalArgumentException("Empty filename");
    }
    return getResourcePath().resolve(filename).toFile();
  }

  public static Path getResource(Path endOfPath) {
    return getResourcePath().resolve(endOfPath);
  }

  public static File getResource(ResourcePath path) {
    return getResourcePath().resolve(path.getPath()).toFile();
  }
}
