/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StreamBackingStoreImplTest {

  @Test
  void acceptsValidPreferenceNames() {
    assertTrue(StreamBackingStoreImpl.isValidElementName("importtoolbar"));
    assertTrue(StreamBackingStoreImpl.isValidElementName("imagetool"));
    assertTrue(StreamBackingStoreImpl.isValidElementName("cPosition"));
    assertTrue(StreamBackingStoreImpl.isValidElementName("view2dcontainer"));
    assertTrue(StreamBackingStoreImpl.isValidElementName("weasis.color.wl.apply"));
    assertTrue(StreamBackingStoreImpl.isValidElementName("mpr.crosshair.center.gap"));
    assertTrue(StreamBackingStoreImpl.isValidElementName("_private"));
    assertTrue(StreamBackingStoreImpl.isValidElementName("mouse.action"));
  }

  @Test
  void rejectsCorruptedNames() {
    assertFalse(StreamBackingStoreImpl.isValidElementName("?mporttoolbar"));
    assertFalse(StreamBackingStoreImpl.isValidElementName("?magetool"));
    assertFalse(StreamBackingStoreImpl.isValidElementName("2toolbar")); // digit start
    assertFalse(StreamBackingStoreImpl.isValidElementName("foo bar")); // whitespace
    assertFalse(StreamBackingStoreImpl.isValidElementName("a<b"));
    assertFalse(StreamBackingStoreImpl.isValidElementName(""));
    assertFalse(StreamBackingStoreImpl.isValidElementName(null));
  }
}
