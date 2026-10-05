/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.pref.node;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import javax.xml.stream.XMLStreamReader;
import org.weasis.core.api.media.data.AttributeSource;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.XmlAttributeSource;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.core.ui.util.PrintOptions;
import org.weasis.dicom.codec.TransferSyntax;
import org.weasis.dicom.explorer.print.DicomPrintDialog.FilmSize;
import org.weasis.dicom.explorer.print.DicomPrintOptions;

public class DicomPrintNode extends DefaultDicomNode {

  /** The member of the JSON entry holding the print options. */
  static final String T_PRINT = "print"; // NON-NLS

  private static final String T_MEDIUM_TYPE = "mediumType";
  private static final String T_PRIORITY = "priority"; // NON-NLS
  private static final String T_FILM_DEST = "filmDestination";
  private static final String T_NUM_COPIES = "numberOfCopies";
  private static final String T_COLOR = "colorPrint";
  private static final String T_FILM_ORIENTATION = "filmOrientation";
  private static final String T_FILM_SIZE = "filmSizeId";
  private static final String T_IMG_DISP_FORMAT = "imageDisplayFormat";
  private static final String T_MAGNIFICATION_TYPE = "magnificationType";
  private static final String T_SMOOTHING_TYPE = "smoothingType";
  private static final String T_BORDER_DENSITY = "borderDensity";
  private static final String T_TRIM = "trim"; // NON-NLS
  private static final String T_EMPTY_DENSITY = "emptyDensity";
  private static final String T_SHOW_ANNOTATIONS = "showingAnnotations";
  private static final String T_PRINT_SEL_VIEW = "printOnlySelectedView";
  private static final String T_DPI = "dpi"; // NON-NLS

  private final DicomPrintOptions printOptions;

  public DicomPrintNode(String description, String aeTitle, String hostname, Integer port) {
    this(description, aeTitle, hostname, port, null);
  }

  public DicomPrintNode(
      String description,
      String aeTitle,
      String hostname,
      Integer port,
      DicomPrintOptions printOptions) {
    super(description, aeTitle, hostname, port, UsageType.STORAGE);
    this.printOptions = printOptions == null ? new DicomPrintOptions() : printOptions;
  }

  public DicomPrintOptions getPrintOptions() {
    return printOptions;
  }

  /** A printer is always a storage node, so the member is never written. */
  @Override
  protected UsageType defaultUsageType() {
    return UsageType.STORAGE;
  }

  @Override
  protected void writeJson(JsonObjectBuilder b, boolean userDocument) {
    super.writeJson(b, userDocument);
    JsonObjectBuilder p = Json.createObjectBuilder();
    JsonUtil.addIfPresent(p, T_MEDIUM_TYPE, printOptions.getMediumType());
    JsonUtil.addIfPresent(p, T_PRIORITY, printOptions.getPriority());
    JsonUtil.addIfPresent(p, T_FILM_DEST, printOptions.getFilmDestination());
    p.add(T_NUM_COPIES, printOptions.getNumOfCopies());
    p.add(T_COLOR, printOptions.isColorPrint());
    JsonUtil.addIfPresent(p, T_FILM_ORIENTATION, printOptions.getFilmOrientation());
    p.add(T_FILM_SIZE, printOptions.getFilmSizeId().name());
    JsonUtil.addIfPresent(p, T_IMG_DISP_FORMAT, printOptions.getImageDisplayFormat());
    JsonUtil.addIfPresent(p, T_MAGNIFICATION_TYPE, printOptions.getMagnificationType());
    JsonUtil.addIfPresent(p, T_SMOOTHING_TYPE, printOptions.getSmoothingType());
    JsonUtil.addIfPresent(p, T_BORDER_DENSITY, printOptions.getBorderDensity());
    JsonUtil.addIfPresent(p, T_TRIM, printOptions.getTrim());
    JsonUtil.addIfPresent(p, T_EMPTY_DENSITY, printOptions.getEmptyDensity());
    p.add(T_SHOW_ANNOTATIONS, printOptions.isShowingAnnotations());
    p.add(T_PRINT_SEL_VIEW, printOptions.isPrintOnlySelectedView());
    p.add(T_DPI, printOptions.getDpi().name());
    b.add(T_PRINT, p);
  }

  static DicomPrintNode fromJson(JsonObject json) {
    DicomPrintNode node =
        new DicomPrintNode(
            json.getString(T_DESCRIPTION, null),
            json.getString(T_AETITLE, null),
            json.getString(T_HOST, null),
            JsonUtil.getInt(json, T_PORT, 104));
    node.readJson(json);

    JsonObject p = json.get(T_PRINT) instanceof JsonObject o ? o : JsonValue.EMPTY_JSON_OBJECT;
    DicomPrintOptions options = node.printOptions;
    options.setMediumType(p.getString(T_MEDIUM_TYPE, DicomPrintOptions.DEF_MEDIUM_TYPE));
    options.setPriority(p.getString(T_PRIORITY, DicomPrintOptions.DEF_PRIORITY));
    options.setFilmDestination(p.getString(T_FILM_DEST, DicomPrintOptions.DEF_FILM_DEST));
    options.setNumOfCopies(JsonUtil.getInt(p, T_NUM_COPIES, DicomPrintOptions.DEF_NUM_COPIES));
    options.setColorPrint(JsonUtil.getBoolean(p, T_COLOR, DicomPrintOptions.DEF_COLOR));
    options.setFilmOrientation(
        p.getString(T_FILM_ORIENTATION, DicomPrintOptions.DEF_FILM_ORIENTATION));
    options.setFilmSizeId(
        FilmSize.getInstance(p.getString(T_FILM_SIZE, null), DicomPrintOptions.DEF_FILM_SIZE));
    options.setImageDisplayFormat(
        p.getString(T_IMG_DISP_FORMAT, DicomPrintOptions.DEF_IMG_DISP_FORMAT));
    options.setMagnificationType(
        p.getString(T_MAGNIFICATION_TYPE, DicomPrintOptions.DEF_MAGNIFICATION_TYPE));
    options.setSmoothingType(p.getString(T_SMOOTHING_TYPE, DicomPrintOptions.DEF_SMOOTHING_TYPE));
    options.setBorderDensity(p.getString(T_BORDER_DENSITY, DicomPrintOptions.DEF_BORDER_DENSITY));
    options.setTrim(p.getString(T_TRIM, DicomPrintOptions.DEF_TRIM));
    options.setEmptyDensity(p.getString(T_EMPTY_DENSITY, DicomPrintOptions.DEF_EMPTY_DENSITY));
    options.setShowingAnnotations(
        JsonUtil.getBoolean(p, T_SHOW_ANNOTATIONS, DicomPrintOptions.DEF_SHOW_ANNOTATIONS));
    options.setPrintOnlySelectedView(
        JsonUtil.getBoolean(p, T_PRINT_SEL_VIEW, DicomPrintOptions.DEF_PRINT_SEL_VIEW));
    options.setDpi(
        PrintOptions.DotPerInches.getInstance(p.getString(T_DPI, null), DicomPrintOptions.DEF_DPI));
    return node;
  }

  public static DicomPrintNode buildDicomPrintNode(XMLStreamReader xmler) {
    AttributeSource source = new XmlAttributeSource(xmler);
    DicomPrintNode node =
        new DicomPrintNode(
            xmler.getAttributeValue(null, T_DESCRIPTION),
            xmler.getAttributeValue(null, T_AETITLE),
            xmler.getAttributeValue(null, T_HOST),
            TagUtil.getIntegerTagAttribute(source, T_PORT, 104));
    node.setTsuid(TransferSyntax.getTransferSyntax(xmler.getAttributeValue(null, T_TSUID)));

    node.printOptions.setMediumType(
        TagUtil.getTagAttribute(source, T_MEDIUM_TYPE, DicomPrintOptions.DEF_MEDIUM_TYPE));
    node.printOptions.setPriority(
        TagUtil.getTagAttribute(source, T_PRIORITY, DicomPrintOptions.DEF_PRIORITY));
    node.printOptions.setFilmDestination(
        TagUtil.getTagAttribute(source, T_FILM_DEST, DicomPrintOptions.DEF_FILM_DEST));
    node.printOptions.setNumOfCopies(
        TagUtil.getIntegerTagAttribute(source, T_NUM_COPIES, DicomPrintOptions.DEF_NUM_COPIES));
    node.printOptions.setColorPrint(
        TagUtil.getBooleanTagAttribute(source, T_COLOR, DicomPrintOptions.DEF_COLOR));

    node.printOptions.setFilmOrientation(
        TagUtil.getTagAttribute(
            source, T_FILM_ORIENTATION, DicomPrintOptions.DEF_FILM_ORIENTATION));
    node.printOptions.setFilmSizeId(
        FilmSize.getInstance(
            xmler.getAttributeValue(null, T_FILM_SIZE), DicomPrintOptions.DEF_FILM_SIZE));
    node.printOptions.setImageDisplayFormat(
        TagUtil.getTagAttribute(source, T_IMG_DISP_FORMAT, DicomPrintOptions.DEF_IMG_DISP_FORMAT));
    node.printOptions.setMagnificationType(
        TagUtil.getTagAttribute(
            source, T_MAGNIFICATION_TYPE, DicomPrintOptions.DEF_MAGNIFICATION_TYPE));
    node.printOptions.setSmoothingType(
        TagUtil.getTagAttribute(source, T_SMOOTHING_TYPE, DicomPrintOptions.DEF_SMOOTHING_TYPE));
    node.printOptions.setBorderDensity(
        TagUtil.getTagAttribute(source, T_BORDER_DENSITY, DicomPrintOptions.DEF_BORDER_DENSITY));
    node.printOptions.setTrim(TagUtil.getTagAttribute(source, T_TRIM, DicomPrintOptions.DEF_TRIM));
    node.printOptions.setEmptyDensity(
        TagUtil.getTagAttribute(source, T_EMPTY_DENSITY, DicomPrintOptions.DEF_EMPTY_DENSITY));

    node.printOptions.setShowingAnnotations(
        TagUtil.getBooleanTagAttribute(
            source, T_SHOW_ANNOTATIONS, DicomPrintOptions.DEF_SHOW_ANNOTATIONS));
    node.printOptions.setPrintOnlySelectedView(
        TagUtil.getBooleanTagAttribute(
            source, T_PRINT_SEL_VIEW, DicomPrintOptions.DEF_PRINT_SEL_VIEW));
    node.printOptions.setDpi(
        PrintOptions.DotPerInches.getInstance(
            xmler.getAttributeValue(null, T_DPI), DicomPrintOptions.DEF_DPI));

    return node;
  }
}
