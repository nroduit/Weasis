/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import org.pf4j.ExtensionPoint;

public interface DicomSpecialElementFactory extends ExtensionPoint {

  String getSeriesMimeType();

  String[] getModalities();

  default boolean isHidden() {
    return false;
  }

  DicomSpecialElement buildDicomSpecialElement(DicomMediaIO mediaIO);

  /**
   * What the explorer shows for one of the elements this factory builds: kind, label, date, creator
   * and references. The default reads the standard attributes; a plugin overrides it when its
   * objects carry the information elsewhere.
   */
  default ObjectDescriptor describe(DicomSpecialElement element) {
    return ObjectDescriptor.generic(element);
  }
}
