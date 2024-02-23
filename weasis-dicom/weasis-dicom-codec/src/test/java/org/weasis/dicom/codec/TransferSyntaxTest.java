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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TransferSyntaxTest {

  @Test
  void getLabel() {
    assertEquals(Messages.getString("keep.original.tsuid"), TransferSyntax.NONE.getLabel());
  }

  @Test
  void getTransferSyntaxUID() {
    assertEquals("1.2.840.10008.1.2.4.90", TransferSyntax.JPEG2000_LOSSLESS.getTransferSyntaxUID());
  }

  @Test
  void testToString() {
    TransferSyntax le = TransferSyntax.EXPLICIT_VR_LE;
    assertEquals(le.getLabel() + " [" + le.getTransferSyntaxUID() + "]", le.toString());
  }

  @Test
  void getTransferSyntax() {
    assertEquals(TransferSyntax.MPEG_4, TransferSyntax.getTransferSyntax("MPEG_4")); // NON-NLS
    assertEquals(TransferSyntax.NONE, TransferSyntax.getTransferSyntax("UNKNOWN"));
  }
}
