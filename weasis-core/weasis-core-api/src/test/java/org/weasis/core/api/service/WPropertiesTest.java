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

import static org.assertj.core.api.Assertions.assertThat;

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
    assertThat(prop.getProperty("string", null)).isEqualTo("test!"); // NON-NLS
    prop.setProperty("string", null); // NON-NLS
    // Return the previous value, do not accept null value
    assertThat(prop.getProperty("string", null)).isEqualTo("test!"); // NON-NLS
  }

  @Test
  void testPutIntProperty() {
    WProperties prop = new WProperties();
    prop.putIntProperty("int", Integer.MIN_VALUE);
    assertThat(prop.getIntProperty("int", 0)).isEqualTo(Integer.MIN_VALUE);
    prop.putIntProperty("int", Integer.MAX_VALUE);
    assertThat(prop.getIntProperty("int", 0)).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void testPutLongProperty() {
    WProperties prop = new WProperties();
    prop.putLongProperty("long", Long.MIN_VALUE); // NON-NLS
    assertThat(prop.getLongProperty("long", 0L)).isEqualTo(Long.MIN_VALUE); // NON-NLS
    prop.putLongProperty("long", Long.MAX_VALUE); // NON-NLS
    assertThat(prop.getLongProperty("long", 0L)).isEqualTo(Long.MAX_VALUE); // NON-NLS
  }

  @Test
  void testPutBooleanProperty() {
    WProperties prop = new WProperties();
    prop.putBooleanProperty("boolean", true); // NON-NLS
    assertThat(prop.getBooleanProperty("boolean", false)).isTrue(); // NON-NLS
    prop.putBooleanProperty("boolean", false); // NON-NLS
    assertThat(prop.getBooleanProperty("boolean", true)).isFalse(); // NON-NLS
  }

  @Test
  void testPutFloatProperty() {
    WProperties prop = new WProperties();
    prop.putFloatProperty("float", Float.MAX_VALUE); // NON-NLS
    assertThat(prop.getFloatProperty("float", 0.0f)).isEqualTo(Float.MAX_VALUE); // NON-NLS
    prop.putFloatProperty("float", 0.0f); // NON-NLS
    assertThat(prop.getFloatProperty("float", 0.0f)).isZero(); // NON-NLS
  }

  @Test
  void testPutDoubleProperty() {
    WProperties prop = new WProperties();
    prop.putDoubleProperty("double", Math.PI); // NON-NLS
    assertThat(prop.getDoubleProperty("double", 0.0)).isEqualTo(Math.PI); // NON-NLS
    prop.putDoubleProperty("double", Double.NEGATIVE_INFINITY); // NON-NLS
    assertThat(prop.getDoubleProperty("double", 0.0)) // NON-NLS
        .isEqualTo(Double.NEGATIVE_INFINITY); // NON-NLS
    prop.putDoubleProperty("double", 0.0f); // NON-NLS
    assertThat(prop.getDoubleProperty("double", 0.0)).isZero(); // NON-NLS
  }

  @Test
  void testPutColorProperty() {
    WProperties prop = new WProperties();
    prop.putColorProperty("color", COLOR_ALPHA); // NON-NLS
    assertThat(prop.getColorProperty("color")).isEqualTo(COLOR_ALPHA); // NON-NLS
    prop.putColorProperty("color", Color.GREEN); // NON-NLS
    assertThat(prop.getColorProperty("color")).isEqualTo(Color.GREEN); // NON-NLS
    prop.putColorProperty("color", null); // NON-NLS
    // Return the previous value, null value not allow
    assertThat(prop.getColorProperty("color")).isEqualTo(Color.GREEN); // NON-NLS
  }

  @Test
  void testPutByteArrayProperty() {
    WProperties prop = new WProperties();
    byte[] data = new byte[] {0, 3, 43, 32, 34, 54, 127, 0, (byte) 255};
    prop.putByteArrayProperty("byte", data); // NON-NLS
    assertThat(data).isEqualTo(prop.getByteArrayProperty("byte", null)); // NON-NLS
    prop.putByteArrayProperty("byte", null); // NON-NLS
    assertThat(prop.getByteArrayProperty("byte", null)).isNull(); // NON-NLS
    prop.putByteArrayProperty("byte", new byte[] {}); // NON-NLS
    assertThat(prop.getByteArrayProperty("byte", null)).isNull(); // NON-NLS
  }

  @Test
  void testColor2Hexadecimal() {
    assertThat(WProperties.color2Hexadecimal(Color.MAGENTA, false)).isEqualTo(MAGENTA);
    assertThat(WProperties.color2Hexadecimal(Color.MAGENTA, true)).isEqualTo(MAGENTA_ALPHA);
    assertThat(WProperties.color2Hexadecimal(COLOR_ALPHA, true)).isEqualTo(GREY_ALPHA);
    assertThat(WProperties.color2Hexadecimal(COLOR_ALPHA, false)).isEqualTo(GREY);
  }

  @Test
  void testHexadecimal2Color() {
    assertThat(WProperties.hexadecimal2Color(MAGENTA)).isEqualTo(Color.MAGENTA);
    assertThat(WProperties.hexadecimal2Color(MAGENTA_ALPHA)).isEqualTo(Color.MAGENTA);
    assertThat(WProperties.hexadecimal2Color(GREY_ALPHA)).isEqualTo(COLOR_ALPHA);
    assertThat(WProperties.hexadecimal2Color(GREY)).isNotEqualTo(COLOR_ALPHA);
    assertThat(WProperties.hexadecimal2Color(null)).isEqualTo(Color.BLACK);
    assertThat(WProperties.hexadecimal2Color("sf")).isEqualTo(Color.BLACK); // NON-NLS
  }
}
