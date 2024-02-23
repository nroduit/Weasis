/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.service;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import org.junit.jupiter.api.Test;

class WPropertiesTest {
  private static final String MAGENTA = "ff00ff"; // NON-NLS
  private static final String MAGENTA_ALPHA = "ffff00ff"; // NON-NLS
  private static final String GREY = "808080";
  private static final String GREY_ALPHA = "80808080";
  private static final Color COLOR_ALPHA = new Color(128, 128, 128, 128);

  @Test
  void testSetPropertyString() {
    WProperties prop = new WProperties();
    prop.setProperty("string", "test!"); // NON-NLS
    assertEquals("test!", prop.getProperty("string", null));
    prop.setProperty("string", null); // NON-NLS
    assertEquals("test!", prop.getProperty("string", null));
  }

  @Test
  void testPutIntProperty() {
    WProperties prop = new WProperties();
    prop.putIntProperty("int", Integer.MIN_VALUE);
    assertEquals(Integer.MIN_VALUE, prop.getIntProperty("int", 0));
    prop.putIntProperty("int", Integer.MAX_VALUE);
    assertEquals(Integer.MAX_VALUE, prop.getIntProperty("int", 0));
  }

  @Test
  void testPutLongProperty() {
    WProperties prop = new WProperties();
    prop.putLongProperty("long", Long.MIN_VALUE); // NON-NLS
    assertEquals(Long.MIN_VALUE, prop.getLongProperty("long", 0L)); // NON-NLS
    prop.putLongProperty("long", Long.MAX_VALUE); // NON-NLS
    assertEquals(Long.MAX_VALUE, prop.getLongProperty("long", 0L)); // NON-NLS
  }

  @Test
  void testPutBooleanProperty() {
    WProperties prop = new WProperties();
    prop.putBooleanProperty("boolean", true); // NON-NLS
    assertTrue(prop.getBooleanProperty("boolean", false)); // NON-NLS
    prop.putBooleanProperty("boolean", false); // NON-NLS
    assertFalse(prop.getBooleanProperty("boolean", true)); // NON-NLS
  }

  @Test
  void testPutFloatProperty() {
    WProperties prop = new WProperties();
    prop.putFloatProperty("float", Float.MAX_VALUE); // NON-NLS
    assertEquals(Float.MAX_VALUE, prop.getFloatProperty("float", 0.0f)); // NON-NLS
    prop.putFloatProperty("float", 0.0f); // NON-NLS
    assertEquals(0.0f, prop.getFloatProperty("float", 0.0f)); // NON-NLS
  }

  @Test
  void testPutDoubleProperty() {
    WProperties prop = new WProperties();
    prop.putDoubleProperty("double", Math.PI); // NON-NLS
    assertEquals(Math.PI, prop.getDoubleProperty("double", 0.0)); // NON-NLS
    prop.putDoubleProperty("double", Double.NEGATIVE_INFINITY); // NON-NLS
    assertEquals(Double.NEGATIVE_INFINITY, prop.getDoubleProperty("double", 0.0)); // NON-NLS
    prop.putDoubleProperty("double", 0.0f); // NON-NLS
    assertEquals(0.0, prop.getDoubleProperty("double", 0.0)); // NON-NLS
  }

  @Test
  void testPutColorProperty() {
    WProperties prop = new WProperties();
    prop.putColorProperty("color", COLOR_ALPHA); // NON-NLS
    assertEquals(COLOR_ALPHA, prop.getColorProperty("color")); // NON-NLS
    prop.putColorProperty("color", Color.GREEN); // NON-NLS
    assertEquals(Color.GREEN, prop.getColorProperty("color")); // NON-NLS
    prop.putColorProperty("color", null); // NON-NLS
    // Return the previous value, null value not allow
    assertEquals(Color.GREEN, prop.getColorProperty("color")); // NON-NLS
  }

  @Test
  void testPutByteArrayProperty() {
    WProperties prop = new WProperties();
    byte[] data = new byte[] {0, 3, 43, 32, 34, 54, 127, 0, (byte) 255};
    prop.putByteArrayProperty("byte", data); // NON-NLS
    assertArrayEquals(data, prop.getByteArrayProperty("byte", null)); // NON-NLS
    prop.putByteArrayProperty("byte", null); // NON-NLS
    assertNull(prop.getByteArrayProperty("byte", null)); // NON-NLS
    prop.putByteArrayProperty("byte", new byte[] {}); // NON-NLS
    assertNull(prop.getByteArrayProperty("byte", null)); // NON-NLS
  }

  @Test
  void color2Hexadecimal() {
    assertEquals(MAGENTA, WProperties.color2Hexadecimal(Color.MAGENTA, false));
    assertEquals(MAGENTA_ALPHA, WProperties.color2Hexadecimal(Color.MAGENTA, true));
    assertEquals(GREY_ALPHA, WProperties.color2Hexadecimal(COLOR_ALPHA, true));
    assertEquals(GREY, WProperties.color2Hexadecimal(COLOR_ALPHA, false));
  }

  @Test
  void hexadecimal2Color() {
    assertEquals(Color.MAGENTA, WProperties.hexadecimal2Color(MAGENTA));
    assertEquals(Color.MAGENTA, WProperties.hexadecimal2Color(MAGENTA_ALPHA));
    assertEquals(COLOR_ALPHA, WProperties.hexadecimal2Color(GREY_ALPHA));
    assertNotEquals(COLOR_ALPHA, WProperties.hexadecimal2Color(GREY));
    assertEquals(Color.BLACK, WProperties.hexadecimal2Color(null));
    assertEquals(Color.BLACK, WProperties.hexadecimal2Color("sf")); // NON-NLS
  }
}
