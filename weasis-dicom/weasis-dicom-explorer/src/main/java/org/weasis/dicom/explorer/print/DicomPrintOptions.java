/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.print;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.weasis.core.ui.print.Paper;
import org.weasis.core.ui.util.PrintOptions;
import org.weasis.core.util.StringUtil;

/**
 * The attributes of a DICOM print job (PS3.3 C.13: film session, film box) and the resolution of
 * the film raster, configured on the print node as defaults and adjusted for a job.
 *
 * @author Marcelo Porto (marcelo@animati.com.br)
 * @author Nicolas Roduit
 * @since 09/01/2012
 */
public class DicomPrintOptions extends PrintOptions {
  public static final String DEF_MEDIUM_TYPE = "BLUE FILM"; // NON-NLS
  public static final String DEF_PRIORITY = "LOW";
  public static final String DEF_FILM_DEST = "MAGAZINE";
  public static final int DEF_NUM_COPIES = 1;
  public static final boolean DEF_COLOR = false;
  public static final String DEF_FILM_ORIENTATION = "PORTRAIT";
  public static final String DEF_IMG_DISP_FORMAT = "STANDARD\\1,1"; // NON-NLS
  public static final Paper DEF_FILM_SIZE = Paper.IN_8X10;
  public static final String DEF_MAGNIFICATION_TYPE = "CUBIC";
  public static final String DEF_SMOOTHING_TYPE = "MEDIUM";
  public static final String DEF_BORDER_DENSITY = "WHITE";
  public static final String DEF_TRIM = "NO";
  public static final String DEF_EMPTY_DENSITY = "BLACK";
  public static final boolean DEF_SHOW_ANNOTATIONS = true;

  /**
   * The resolution of the film raster Weasis renders, in dots per inch. Not a DICOM attribute: the
   * printer receives a pixel matrix and scales it to its own with the magnification type; a value
   * close to the native resolution of the printer avoids that resampling.
   */
  public static final int DEF_FILM_RESOLUTION = 150;

  /** Common values, the native resolution of the usual dry imagers (320, 508) among them. */
  public static final List<Integer> FILM_RESOLUTIONS =
      List.of(100, 150, 200, 250, 300, 320, 400, 508, 600);

  /** Unset: the attribute is not sent and the printer applies its own default. */
  public static final String PRINTER_DEFAULT = "";

  /** The values of Requested Resolution ID (2020,0050), after the printer default. */
  public static final List<String> REQUESTED_RESOLUTIONS =
      List.of(PRINTER_DEFAULT, "STANDARD", "HIGH"); // NON-NLS

  /** The only magnification type the smoothing type applies to (PS3.3 C.13.3). */
  public static final String CUBIC = "CUBIC";

  /** The film sizes of the 4.x enum, such as {@code IN8_5X11} or {@code CM24X30}. */
  private static final Pattern LEGACY_INCH = Pattern.compile("IN(\\d+(?:_\\d+)?)X(\\d+)");

  private static final Pattern LEGACY_CM = Pattern.compile("CM(\\d+)X(\\d+)");
  private static final Pattern LEGACY_DPI = Pattern.compile("DPI_(\\d+)");

  private String mediumType;
  private String priority;
  private String filmDestination;
  private int numOfCopies;
  private String filmOrientation;
  private Paper filmSize;
  private String imageDisplayFormat;
  private String magnificationType;
  private String smoothingType;
  private String borderDensity;
  private String trim;
  private String emptyDensity;
  private int minDensity;
  private int maxDensity;
  private int filmResolution;
  private String requestedResolution = PRINTER_DEFAULT;
  private String configurationInformation = "";

  public DicomPrintOptions() {
    super();
    this.mediumType = DEF_MEDIUM_TYPE;
    this.priority = DEF_PRIORITY;
    this.filmDestination = DEF_FILM_DEST;
    this.numOfCopies = DEF_NUM_COPIES;
    setColorPrint(DEF_COLOR);
    this.filmOrientation = DEF_FILM_ORIENTATION;
    this.filmSize = DEF_FILM_SIZE;
    this.imageDisplayFormat = DEF_IMG_DISP_FORMAT;
    this.magnificationType = DEF_MAGNIFICATION_TYPE;
    this.smoothingType = DEF_SMOOTHING_TYPE;
    this.borderDensity = DEF_BORDER_DENSITY;
    this.trim = DEF_TRIM;
    this.emptyDensity = DEF_EMPTY_DENSITY;
    // 0: not sent, the printer applies its own densities
    this.minDensity = 0;
    this.maxDensity = 0;
    setShowingAnnotations(DEF_SHOW_ANNOTATIONS);
    this.filmResolution = DEF_FILM_RESOLUTION;
  }

  /** The papers a film printer knows: those of the catalogue with a DICOM film size id. */
  public static List<Paper> filmSizes() {
    return Paper.CATALOGUE.stream().filter(Paper::hasFilmSizeId).toList();
  }

  /** The paper of a stored film size: a DICOM {@code FilmSizeID} or the name of the 4.x enum. */
  public static Paper filmSizeOf(String stored, Paper defaultValue) {
    if (!StringUtil.hasText(stored)) {
      return defaultValue;
    }
    String id = stored.trim();
    Matcher inch = LEGACY_INCH.matcher(id);
    Matcher cm = LEGACY_CM.matcher(id);
    if (inch.matches()) {
      id = inch.group(1) + "INX" + inch.group(2) + "IN"; // NON-NLS
    } else if (cm.matches()) {
      id = cm.group(1) + "CMX" + cm.group(2) + "CM"; // NON-NLS
    }
    return Paper.fromFilmSizeId(id).orElse(defaultValue);
  }

  /** The resolution of a stored value: a number of dots per inch or the name of the 4.x enum. */
  public static int filmResolutionOf(String stored, int defaultValue) {
    if (!StringUtil.hasText(stored)) {
      return defaultValue;
    }
    Matcher legacy = LEGACY_DPI.matcher(stored.trim());
    String digits = legacy.matches() ? legacy.group(1) : stored.trim();
    try {
      int dpi = Integer.parseInt(digits);
      return dpi > 0 ? dpi : defaultValue;
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /** A copy, to adjust for one job without touching the defaults of the node. */
  public DicomPrintOptions copy() {
    DicomPrintOptions c = new DicomPrintOptions();
    c.setShowingAnnotations(isShowingAnnotations());
    c.setCenter(isCenter());
    c.setColorPrint(isColorPrint());
    c.setTextSize(getTextSize());
    c.setBackground(getBackground());
    c.setBorder(getBorder());
    c.mediumType = mediumType;
    c.priority = priority;
    c.filmDestination = filmDestination;
    c.numOfCopies = numOfCopies;
    c.filmOrientation = filmOrientation;
    c.filmSize = filmSize;
    c.imageDisplayFormat = imageDisplayFormat;
    c.magnificationType = magnificationType;
    c.smoothingType = smoothingType;
    c.borderDensity = borderDensity;
    c.trim = trim;
    c.emptyDensity = emptyDensity;
    c.minDensity = minDensity;
    c.maxDensity = maxDensity;
    c.filmResolution = filmResolution;
    c.requestedResolution = requestedResolution;
    c.configurationInformation = configurationInformation;
    return c;
  }

  public String getBorderDensity() {
    return borderDensity;
  }

  public void setBorderDensity(String borderDensity) {
    this.borderDensity = borderDensity;
  }

  public String getEmptyDensity() {
    return emptyDensity;
  }

  public void setEmptyDensity(String emptyDensity) {
    this.emptyDensity = emptyDensity;
  }

  public String getFilmDestination() {
    return filmDestination;
  }

  public void setFilmDestination(String filmDestination) {
    this.filmDestination = filmDestination;
  }

  public String getFilmOrientation() {
    return filmOrientation;
  }

  public void setFilmOrientation(String filmOrientation) {
    this.filmOrientation = filmOrientation;
  }

  /** The film, a paper with a DICOM film size id. */
  public Paper getFilmSizeId() {
    return filmSize;
  }

  public void setFilmSizeId(Paper filmSize) {
    this.filmSize = filmSize == null ? DEF_FILM_SIZE : filmSize;
  }

  public String getImageDisplayFormat() {
    return imageDisplayFormat;
  }

  public void setImageDisplayFormat(String imageDisplayFormat) {
    this.imageDisplayFormat = imageDisplayFormat;
  }

  public String getMagnificationType() {
    return magnificationType;
  }

  public void setMagnificationType(String magnificationType) {
    this.magnificationType = magnificationType;
  }

  /** The maximum density in hundredths of optical density, or 0 for the printer default. */
  public int getMaxDensity() {
    return maxDensity;
  }

  public void setMaxDensity(int maxDensity) {
    this.maxDensity = Math.max(0, maxDensity);
  }

  /** Requested Resolution ID (2020,0050): STANDARD, HIGH, or empty for the printer default. */
  public String getRequestedResolution() {
    return requestedResolution;
  }

  public void setRequestedResolution(String requestedResolution) {
    this.requestedResolution =
        requestedResolution == null ? PRINTER_DEFAULT : requestedResolution.trim();
  }

  /**
   * Configuration Information (2010,0150): the character string the conformance statement of the
   * printer defines for its own settings, or empty for none.
   */
  public String getConfigurationInformation() {
    return configurationInformation;
  }

  public void setConfigurationInformation(String configurationInformation) {
    this.configurationInformation =
        configurationInformation == null ? "" : configurationInformation.trim();
  }

  public String getMediumType() {
    return mediumType;
  }

  public void setMediumType(String mediumType) {
    this.mediumType = mediumType;
  }

  /** The minimum density in hundredths of optical density, or 0 for the printer default. */
  public int getMinDensity() {
    return minDensity;
  }

  public void setMinDensity(int minDensity) {
    this.minDensity = Math.max(0, minDensity);
  }

  public int getNumOfCopies() {
    return numOfCopies;
  }

  public void setNumOfCopies(int numOfCopies) {
    this.numOfCopies = numOfCopies;
  }

  public String getPriority() {
    return priority;
  }

  public void setPriority(String priority) {
    this.priority = priority;
  }

  public String getSmoothingType() {
    return smoothingType;
  }

  /** The smoothing type, a value of the printer, or empty for its default. */
  public void setSmoothingType(String smoothingType) {
    this.smoothingType = smoothingType == null ? PRINTER_DEFAULT : smoothingType.trim();
  }

  public String getTrim() {
    return trim;
  }

  public void setTrim(String trim) {
    this.trim = trim;
  }

  /** The resolution of the film raster in dots per inch. */
  public int getFilmResolution() {
    return filmResolution;
  }

  public void setFilmResolution(int filmResolution) {
    this.filmResolution = filmResolution > 0 ? filmResolution : DEF_FILM_RESOLUTION;
  }
}
