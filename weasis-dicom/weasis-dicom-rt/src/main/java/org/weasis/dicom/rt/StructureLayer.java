/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.rt;

import java.util.Objects;

/**
 * @author Tomas Skripcak
 */
public class StructureLayer extends RtLayer {
  private final Structure structure;

  public StructureLayer(Structure structure) {
    super();
    this.structure = Objects.requireNonNull(structure);
    this.layer.setName(structure.getRoiName());
  }

  public Structure getStructure() {
    return structure;
  }

  @Override
  public String toString() {
    return structure.toString();
  }
}
