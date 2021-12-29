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

  public interface ResourcePath {
    String getPath();
  }

  public interface ResourceIconPath extends ResourcePath {}

  public enum LogoIcon implements ResourceIconPath {
    SMALL("svg/logo/Weasis.svg"),
    LARGE("svg/logo/WeasisAbout.svg"),
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
    UNKNOWN("svg/file/unknown.svg"),
    TEXT("svg/file/text.svg"),
    PDF("svg/file/pdf.svg"),
    AUDIO("svg/file/audio.svg"),
    VIDEO("svg/file/video.svg"),
    XML("svg/file/xml.svg"),
    IMAGE("svg/file/image.svg"),
    ECG("svg/file/ecg.svg"),
    DICOM("svg/file/dicom.svg");

    private String path;

    FileIcon(String path) {
      this.path = path;
    }

    public String getPath() {
      return path;
    }
  }

  public enum OtherIcon implements ResourceIconPath {
    XRAY("svg/other/xray.svg"),
    PATIENT("svg/other/patient.svg"),
    CALENDAR("svg/other/calendar.svg");

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
