/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.explorer.mf;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.serialize.XmlSerializer;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.KOSpecialElement;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.pr.DicomPrSerializer;
import org.weasis.dicom.mf.ArcParameters;
import org.weasis.dicom.mf.ArcQuery;
import org.weasis.dicom.mf.QueryResult;
import org.weasis.dicom.mf.WadoParameters;

public class ManifestBuilder {

    private ManifestBuilder() {
    }

    public static Set<WadoParameters> getWadoParameters(DicomModel model, MediaSeriesGroup patient) {
        Set<WadoParameters> params = new LinkedHashSet<>();
        if (model != null && patient != null) {
            for (MediaSeriesGroup study : model.getChildren(patient)) {
                for (MediaSeriesGroup series : model.getChildren(study)) {
                    if (series instanceof MediaSeries) {
                        WadoParameters wadoParams = (WadoParameters) series.getTagValue(TagW.WadoParameters);
                        if (wadoParams != null && StringUtil.hasText(wadoParams.getBaseURL())) {
                            params.add(wadoParams);
                        }
                    }
                }
            }
        }
        return params;
    }

    public static void writeExtendedManifest(ArcQuery arquery, File outFile) throws IOException {
        if (arquery == null || outFile == null) {
            throw new IllegalArgumentException("ArcQuery and File cannot be null"); //$NON-NLS-1$
        }

        try (BufferedWriter buf =
            new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), arquery.getCharsetEncoding()))) {

            writeExtendedManifest(arquery, buf);
            buf.flush();
        } catch (IOException e) {
            FileUtil.delete(outFile);
            throw new IOException(e);
        }
    }

    private static void writeExtendedManifest(ArcQuery arquery, Writer buf) throws IOException {
        arquery.writeHeader(buf);
        arquery.writeArcQueries(buf);

        List<KOSpecialElement> koEditable = new ArrayList<>();
        List<DicomImageElement> images = new ArrayList<>();

        for (QueryResult query : arquery.getQueryList()) {
            if (query instanceof DicomModelQueryResult) {
                Set<KOSpecialElement> kos = ((DicomModelQueryResult) query).getKoEditable();
                if (!kos.isEmpty()) {
                    koEditable.addAll(kos);
                }
                Set<DicomImageElement> imgs = ((DicomModelQueryResult) query).getImages();
                if (!imgs.isEmpty()) {
                    images.addAll(imgs);
                }
            }
        }

        if (!images.isEmpty()) {
            buf.append("\n<"); //$NON-NLS-1$
            buf.append(ArcParameters.TAG_PR_ROOT);
            buf.append(">\n"); //$NON-NLS-1$

            for (DicomImageElement img : images) {
                GraphicModel model = (GraphicModel) img.getTagValue(TagW.PresentationModel);
                if (model != null && model.hasSerializableGraphics()) {
                    GraphicModel m = DicomPrSerializer.getModelForSerialization(model, null);
                    XmlSerializer.writePresentation(m, buf);
                }
                buf.append("\n"); //$NON-NLS-1$
            }

            buf.append("\n</"); //$NON-NLS-1$
            buf.append(ArcParameters.TAG_PR_ROOT);
            buf.append(">"); //$NON-NLS-1$
        }

        KOSpecialElement.writeSelection(koEditable, buf);

        arquery.writeEndOfDocument(buf);
    }

}
