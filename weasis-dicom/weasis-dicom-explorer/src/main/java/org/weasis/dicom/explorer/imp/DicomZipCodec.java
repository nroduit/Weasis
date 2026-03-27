/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.imp;

import java.net.URI;
import java.util.Hashtable;
import org.osgi.service.component.annotations.Component;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;

/** An OSGi {@link Codec} service that handles {@code .zip} files containing DICOM data. */
@Component(service = Codec.class)
public class DicomZipCodec implements Codec<MediaElement> {

  public static final String NAME = "DICOM-ZIP"; // NON-NLS
  public static final String MIME_TYPE = DicomZipMediaIO.MIME_TYPE;
  public static final String[] FILE_EXTENSIONS = {"zip"}; // NON-NLS

  @Override
  public String getCodecName() {
    return NAME;
  }

  @Override
  public String[] getReaderMIMETypes() {
    return new String[] {MIME_TYPE};
  }

  @Override
  public String[] getReaderExtensions() {
    return FILE_EXTENSIONS;
  }

  @Override
  public String[] getWriterMIMETypes() {
    return new String[0];
  }

  @Override
  public String[] getWriterExtensions() {
    return new String[0];
  }

  @Override
  public boolean isMimeTypeSupported(String mimeType) {
    return MIME_TYPE.equals(mimeType);
  }

  @Override
  public MediaReader<MediaElement> getMediaIO(
      URI media, String mimeType, Hashtable<String, Object> properties) {
    if (isMimeTypeSupported(mimeType)) {
      return new DicomZipMediaIO(media, this);
    }
    return null;
  }
}
