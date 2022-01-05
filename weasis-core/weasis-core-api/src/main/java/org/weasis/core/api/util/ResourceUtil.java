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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;

public class ResourceUtil {
  private static final Logger LOGGER = LoggerFactory.getLogger(ResourceUtil.class);

  public static final int TOOLBAR_ICON_SIZE = 30;

  public interface ResourcePath {
    String getPath();
  }

  public interface ResourceIconPath extends ResourcePath {}

  public enum LogoIcon implements ResourceIconPath {
    LARGE("svg/logo/WeasisAbout.svg"),
    SMALL("svg/logo/Weasis.svg"),
    SMALL_DICOMIZER("svg/logo/Dicomizer.svg");

    private String path;

    LogoIcon(String path) {
      this.path = path;
    }

    public String getPath() {
      return path;
    }
  }

  public enum FileIcon implements ResourceIconPath {
    AUDIO("svg/file/audio.svg"),
    DICOM("svg/file/dicom.svg"),
    ECG("svg/file/ecg.svg"),
    IMAGE("svg/file/image.svg"),
    PDF("svg/file/pdf.svg"),
    TEXT("svg/file/text.svg"),
    UNKNOWN("svg/file/unknown.svg"),
    VIDEO("svg/file/video.svg"),
    XML("svg/file/xml.svg");

    private String path;

    FileIcon(String path) {
      this.path = path;
    }

    public String getPath() {
      return path;
    }
  }

  public enum ActionIcon implements ResourceIconPath {
    CONTEXT_MENU("svg/action/contextMenu.svg"),
    CROSSHAIR("svg/action/crosshair.svg"),
    DRAW("svg/action/draw.svg"),
    EXECUTE("svg/action/execute.svg"),
    FLIP("svg/action/flip.svg"),
    INVERSE_LUT("svg/action/inverseLut.svg"),
    LAYOUT("svg/action/layout.svg"),
    LUT("svg/action/lut.svg"),
    MEASURE("svg/action/measure.svg"),
    METADATA("svg/action/metadata.svg"),
    MOUSE_LEFT("svg/action/mouseLeft.svg"),
    MOUSE_MIDDLE("svg/action/mouseMiddle.svg"),
    MOUSE_RIGHT("svg/action/mouseRight.svg"),
    MOUSE_WHEEL("svg/action/mouseWheel.svg"),
    NONE("svg/action/none.svg"),
    PAN("svg/action/pan.svg"),
    PIPETTE("svg/action/pipette.svg"),
    PRINT("svg/action/print.svg"),
    RESET("svg/action/reset.svg"),
    ROTATE_CLOCKWISE("svg/action/rotateClockwise.svg"),
    ROTATE_COUNTERCLOCKWISE("svg/action/rotateCounterclockwise.svg"),
    ROTATION("svg/action/rotation.svg"),
    SELECTION("svg/action/selection.svg"),
    SELECTION_DELETE("svg/action/selectionDelete.svg"),
    SEQUENCE("svg/action/sequence.svg"),
    SUSPEND("svg/action/suspend.svg"),
    SYNCH("svg/action/synch.svg"),
    TILE("svg/action/tile.svg"),
    WINDOW_LEVEL("svg/action/winLevel.svg"),
    ZOOM("svg/action/zoom.svg"),
    ZOOM_AREA("svg/action/zoomArea.svg"),
    ZOOM_BEST_FIT("svg/action/zoomBestFit.svg"),
    ZOOM_ORIGINAL("svg/action/zoomOriginal.svg"),
    ZOOM_PAN("svg/action/zoomPan.svg"),
    ZOOM_REAL_WORLD("svg/action/zoomRealWorld.svg");

    private String path;

    ActionIcon(String path) {
      this.path = path;
    }

    public String getPath() {
      return path;
    }
  }

  public enum OtherIcon implements ResourceIconPath {
    AUDIO("svg/other/audio.svg"),
    CALENDAR("svg/other/calendar.svg"),
    ECG("svg/other/ecg.svg"),
    IMAGE_EDIT("svg/other/imageEdit.svg"),
    PATIENT("svg/other/patient.svg"),
    RASTER_IMAGE("svg/other/rasterImage.svg"),
    VIEW_SETTING("svg/other/viewSettings.svg"),
    XRAY("svg/other/xray.svg");

    private String path;

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
    int size = TOOLBAR_ICON_SIZE;
    return getIcon(path, size, size);
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

  public static File getResource(Path endOfPath) {
    return getResourcePath().resolve(endOfPath).toFile();
  }

  public static File getResource(ResourcePath path) {
    return getResourcePath().resolve(path.getPath()).toFile();
  }
}
