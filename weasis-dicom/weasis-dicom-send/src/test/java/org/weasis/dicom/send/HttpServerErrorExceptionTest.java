/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.send;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link HttpServerErrorException} — the unchecked exception thrown by the STOW-RS sender
 * when the destination DICOMweb server returns a non-success HTTP status. Its job is purely to
 * propagate the failure cause and message up to the UI; the test below pins that contract so a
 * regression in either constructor cannot silently lose the original cause.
 */
class HttpServerErrorExceptionTest {

  @Test
  void messageOnlyConstructor_preservesMessage() {
    HttpServerErrorException ex = new HttpServerErrorException("500 Internal Server Error");

    assertAll(
        () -> assertEquals("500 Internal Server Error", ex.getMessage()),
        () -> assertNull(ex.getCause(), "no cause provided"));
  }

  @Test
  void messageAndCauseConstructor_preservesBoth() {
    Throwable rootCause = new IllegalStateException("connection refused");

    HttpServerErrorException ex = new HttpServerErrorException("HTTP request failed", rootCause);

    assertAll(
        () -> assertEquals("HTTP request failed", ex.getMessage()),
        () -> assertSame(rootCause, ex.getCause(), "cause must be propagated, not wrapped"));
  }

  @Test
  void isRuntimeException() {
    // The send pipeline expects unchecked failure — a checked-exception conversion would break
    // every call site silently.
    assertInstanceOf(RuntimeException.class, new HttpServerErrorException("x"));
  }

  @Test
  void nullMessageIsPermitted() {
    HttpServerErrorException ex = new HttpServerErrorException(null);

    assertNull(ex.getMessage(), "null message permitted (matches RuntimeException contract)");
  }
}
