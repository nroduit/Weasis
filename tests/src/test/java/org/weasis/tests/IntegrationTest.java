/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.codec.TagD;

class IntegrationTest {

  @Test
  void integrationTest1() {
    LocalDate date1 = TagD.getDicomDate("19930822");
    assertEquals(LocalDate.of(1993, 8, 22), date1);
  }
}
