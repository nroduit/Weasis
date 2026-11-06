/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi.source;

import java.io.IOException;
import org.dcm4che3.img.stream.BytesWithImageDescriptor;

/**
 * The encoded frames of the instances of a series, by SOP Instance UID: local files, or later
 * frames retrieved from a DICOMweb server. Implementations are thread-safe.
 */
public interface FrameStore extends AutoCloseable {

  /**
   * @throws IOException when the instance is unknown or cannot be read
   */
  BytesWithImageDescriptor frames(String sopInstanceUID) throws IOException;

  @Override
  void close();
}
