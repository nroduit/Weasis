/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.explorer.pref.node;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.ui.util.PrintOptions;
import org.weasis.dicom.codec.TransferSyntax;
import org.weasis.dicom.explorer.print.DicomPrintDialog.FilmSize;
import org.weasis.dicom.explorer.print.DicomPrintOptions;

public class DicomPrintNode extends DefaultDicomNode {

    private static final String T_MEDIUM_TYPE = "mediumType"; //$NON-NLS-1$
    private static final String T_PRIORITY = "priority"; //$NON-NLS-1$
    private static final String T_FILM_DEST = "filmDestination"; //$NON-NLS-1$
    private static final String T_NUM_COPIES = "numberOfCopies";//$NON-NLS-1$
    private static final String T_COLOR = "colorPrint";//$NON-NLS-1$
    private static final String T_FILM_ORIENTATION = "filmOrientation"; //$NON-NLS-1$
    private static final String T_FILM_SIZE = "filmSizeId";//$NON-NLS-1$
    private static final String T_IMG_DISP_FORMAT = "imageDisplayFormat"; //$NON-NLS-1$
    private static final String T_MAGNIFICATION_TYPE = "magnificationType"; //$NON-NLS-1$
    private static final String T_SMOOTHING_TYPE = "smoothingType"; //$NON-NLS-1$
    private static final String T_BORDER_DENSITY = "borderDensity"; //$NON-NLS-1$
    private static final String T_TRIM = "trim"; //$NON-NLS-1$
    private static final String T_EMPTY_DENSITY = "emptyDensity"; //$NON-NLS-1$
    private static final String T_SHOW_ANNOTATIONS = "showingAnnotations"; //$NON-NLS-1$
    private static final String T_PRINT_SEL_VIEW = "printOnlySelectedView"; //$NON-NLS-1$
    private static final String T_DPI = "dpi"; //$NON-NLS-1$

    private final DicomPrintOptions printOptions;

    public DicomPrintNode(String description, String aeTitle, String hostname, Integer port) {
        this(description, aeTitle, hostname, port, null);
    }

    public DicomPrintNode(String description, String aeTitle, String hostname, Integer port,
        DicomPrintOptions printOptions) {
        super(description, aeTitle, hostname, port, UsageType.STORAGE);
        this.printOptions = printOptions == null ? new DicomPrintOptions() : printOptions;
    }

    public DicomPrintOptions getPrintOptions() {
        return printOptions;
    }

    @Override
    public void saveDicomNode(XMLStreamWriter writer) throws XMLStreamException {
        super.saveDicomNode(writer);

        writer.writeAttribute(T_MEDIUM_TYPE, printOptions.getMediumType());
        writer.writeAttribute(T_PRIORITY, printOptions.getPriority());
        writer.writeAttribute(T_FILM_DEST, printOptions.getFilmDestination());
        writer.writeAttribute(T_NUM_COPIES, String.valueOf(printOptions.getNumOfCopies()));
        writer.writeAttribute(T_COLOR, Boolean.toString(printOptions.isColorPrint()));

        writer.writeAttribute(T_FILM_ORIENTATION, printOptions.getFilmOrientation());
        writer.writeAttribute(T_FILM_SIZE, printOptions.getFilmSizeId().name());
        writer.writeAttribute(T_IMG_DISP_FORMAT, printOptions.getImageDisplayFormat());
        writer.writeAttribute(T_MAGNIFICATION_TYPE, printOptions.getMagnificationType());
        writer.writeAttribute(T_SMOOTHING_TYPE, printOptions.getSmoothingType());
        writer.writeAttribute(T_BORDER_DENSITY, printOptions.getBorderDensity());
        writer.writeAttribute(T_TRIM, printOptions.getTrim());
        writer.writeAttribute(T_EMPTY_DENSITY, printOptions.getEmptyDensity());

        writer.writeAttribute(T_SHOW_ANNOTATIONS, Boolean.toString(printOptions.isShowingAnnotations()));
        writer.writeAttribute(T_PRINT_SEL_VIEW, Boolean.toString(printOptions.isPrintOnlySelectedView()));
        writer.writeAttribute(T_DPI, printOptions.getDpi().name());
    }

    public static DicomPrintNode buildDicomPrintNode(XMLStreamReader xmler) {
        DicomPrintNode node =
            new DicomPrintNode(xmler.getAttributeValue(null, T_DESCRIPTION), xmler.getAttributeValue(null, T_AETITLE),
                xmler.getAttributeValue(null, T_HOST), TagUtil.getIntegerTagAttribute(xmler, T_PORT, 104));
        node.setTsuid(TransferSyntax.getTransferSyntax(xmler.getAttributeValue(null, T_TSUID)));

        node.printOptions
            .setMediumType(TagUtil.getTagAttribute(xmler, T_MEDIUM_TYPE, DicomPrintOptions.DEF_MEDIUM_TYPE));
        node.printOptions.setPriority(TagUtil.getTagAttribute(xmler, T_PRIORITY, DicomPrintOptions.DEF_PRIORITY));
        node.printOptions
            .setFilmDestination(TagUtil.getTagAttribute(xmler, T_FILM_DEST, DicomPrintOptions.DEF_FILM_DEST));
        node.printOptions
            .setNumOfCopies(TagUtil.getIntegerTagAttribute(xmler, T_NUM_COPIES, DicomPrintOptions.DEF_NUM_COPIES));
        node.printOptions.setColorPrint(TagUtil.getBooleanTagAttribute(xmler, T_COLOR, DicomPrintOptions.DEF_COLOR));

        node.printOptions.setFilmOrientation(
            TagUtil.getTagAttribute(xmler, T_FILM_ORIENTATION, DicomPrintOptions.DEF_FILM_ORIENTATION));
        node.printOptions.setFilmSizeId(
            FilmSize.getInstance(xmler.getAttributeValue(null, T_FILM_SIZE), DicomPrintOptions.DEF_FILM_SIZE));
        node.printOptions.setImageDisplayFormat(
            TagUtil.getTagAttribute(xmler, T_IMG_DISP_FORMAT, DicomPrintOptions.DEF_IMG_DISP_FORMAT));
        node.printOptions.setMagnificationType(
            TagUtil.getTagAttribute(xmler, T_MAGNIFICATION_TYPE, DicomPrintOptions.DEF_MAGNIFICATION_TYPE));
        node.printOptions
            .setSmoothingType(TagUtil.getTagAttribute(xmler, T_SMOOTHING_TYPE, DicomPrintOptions.DEF_SMOOTHING_TYPE));
        node.printOptions
            .setBorderDensity(TagUtil.getTagAttribute(xmler, T_BORDER_DENSITY, DicomPrintOptions.DEF_BORDER_DENSITY));
        node.printOptions.setTrim(TagUtil.getTagAttribute(xmler, T_TRIM, DicomPrintOptions.DEF_TRIM));
        node.printOptions
            .setEmptyDensity(TagUtil.getTagAttribute(xmler, T_EMPTY_DENSITY, DicomPrintOptions.DEF_EMPTY_DENSITY));

        node.printOptions.setShowingAnnotations(
            TagUtil.getBooleanTagAttribute(xmler, T_SHOW_ANNOTATIONS, DicomPrintOptions.DEF_SHOW_ANNOTATIONS));
        node.printOptions.setPrintOnlySelectedView(
            TagUtil.getBooleanTagAttribute(xmler, T_PRINT_SEL_VIEW, DicomPrintOptions.DEF_PRINT_SEL_VIEW));
        node.printOptions.setDpi(
            PrintOptions.DotPerInches.getInstance(xmler.getAttributeValue(null, T_DPI), DicomPrintOptions.DEF_DPI));

        return node;
    }
}
