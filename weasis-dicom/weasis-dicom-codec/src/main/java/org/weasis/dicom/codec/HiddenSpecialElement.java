/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import org.weasis.core.api.util.ResourceUtil.ResourceIconPath;

public abstract class HiddenSpecialElement extends DicomSpecialElement {

  public HiddenSpecialElement(DicomMediaIO mediaIO) {
    super(mediaIO);
  }

  public abstract ResourceIconPath getIconPath();
}
