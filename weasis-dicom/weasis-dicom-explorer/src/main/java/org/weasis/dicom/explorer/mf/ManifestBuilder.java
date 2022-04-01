/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
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
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.serialize.XmlSerializer;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.KOSpecialElement;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.pr.DicomPrSerializer;
import org.weasis.dicom.mf.ArcParameters;
import org.weasis.dicom.mf.ArcQuery;
import org.weasis.dicom.mf.QueryResult;
import org.weasis.dicom.mf.WadoParameters;

public class ManifestBuilder {

  private ManifestBuilder() {}

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

  public static void writeExtendedManifest(ArcQuery arcQuery, File outFile) throws IOException {
    if (arcQuery == null || outFile == null) {
      throw new IllegalArgumentException("ArcQuery and File cannot be null");
    }

    try (BufferedWriter buf =
        new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(outFile), arcQuery.getCharsetEncoding()))) {

      writeExtendedManifest(arcQuery, buf);
      buf.flush();
    } catch (IOException e) {
      FileUtil.delete(outFile);
      throw new IOException(e);
    }
  }

  private static void writeExtendedManifest(ArcQuery arcQuery, Writer buf) throws IOException {
    arcQuery.writeHeader(buf);
    arcQuery.writeArcQueries(buf);

    List<KOSpecialElement> koEditable = new ArrayList<>();
    List<DicomImageElement> images = new ArrayList<>();

    for (QueryResult query : arcQuery.getQueryList()) {
      if (query instanceof DicomModelQueryResult modelQueryResult) {
        Set<KOSpecialElement> kos = modelQueryResult.getKoEditable();
        if (!kos.isEmpty()) {
          koEditable.addAll(kos);
        }
        Set<DicomImageElement> imgs = modelQueryResult.getImages();
        if (!imgs.isEmpty()) {
          images.addAll(imgs);
        }
      }
    }

    if (!images.isEmpty()) {
      buf.append("\n<");
      buf.append(ArcParameters.TAG_PR_ROOT);
      buf.append(">\n");

      for (DicomImageElement img : images) {
        GraphicModel model = (GraphicModel) img.getTagValue(TagW.PresentationModel);
        if (model != null && model.hasSerializableGraphics()) {
          GraphicModel m = DicomPrSerializer.getModelForSerialization(model, null);
          XmlSerializer.writePresentation(m, buf);
        }
        buf.append("\n");
      }

      buf.append("\n</");
      buf.append(ArcParameters.TAG_PR_ROOT);
      buf.append(">");
    }

    KOSpecialElement.writeSelection(koEditable, buf);

    arcQuery.writeEndOfDocument(buf);
  }
}
