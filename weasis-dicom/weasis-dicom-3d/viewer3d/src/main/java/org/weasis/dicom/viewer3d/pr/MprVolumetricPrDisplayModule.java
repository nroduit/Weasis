/*
 * Copyright (c) 2022 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.pr;

import org.dcm4che3.data.Attributes;
import org.weasis.dicom.macro.Module;

/**
 * <a href="https://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.11.27.html">MPR
 * Volumetric Presentation State Display Module</a>
 */
public class MprVolumetricPrDisplayModule extends Module {

  public MprVolumetricPrDisplayModule(Attributes dcmItems) {
    super(dcmItems);
  }
}
