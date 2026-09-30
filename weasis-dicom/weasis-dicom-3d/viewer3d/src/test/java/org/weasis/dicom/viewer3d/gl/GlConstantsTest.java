/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.gl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL2GL3;
import com.jogamp.opengl.GL3ES3;
import com.jogamp.opengl.GL4;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The facade spells the GL enumerants out instead of importing them, so a typo would only show as a
 * wrong rendering at run time. Every value is checked here against the binding that still provides
 * them.
 */
class GlConstantsTest {

  private static final List<Class<?>> JOGL_INTERFACES =
      List.of(GL.class, GL2ES2.class, GL2ES3.class, GL2GL3.class, GL3ES3.class, GL4.class);

  /** Enumerants of extensions the binding does not cover, with their registry values. */
  private static final Map<String, Integer> EXTENSION_VALUES =
      Map.of("GL_COMPLETION_STATUS_KHR", 0x91B1);

  @Test
  @DisplayName("Every GL_ constant of the facade has the value the OpenGL headers give it")
  void constantsMatchTheBinding() {
    List<String> unknown = new ArrayList<>();
    int checked = 0;
    for (Field field : Gl.class.getDeclaredFields()) {
      if (!field.getName().startsWith("GL_")
          || field.getType() != int.class
          || !Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      Optional<Integer> expected = joglValue(field.getName());
      if (expected.isEmpty()) {
        unknown.add(field.getName());
        continue;
      }
      assertEquals(expected.get(), value(field), field.getName());
      checked++;
    }
    assertEquals(List.of(), unknown, "constants the binding does not define");
    assertTrue(checked > 50, "only " + checked + " constants were compared");
  }

  private static Optional<Integer> joglValue(String name) {
    if (EXTENSION_VALUES.containsKey(name)) {
      return Optional.of(EXTENSION_VALUES.get(name));
    }
    for (Class<?> type : JOGL_INTERFACES) {
      try {
        return Optional.of(value(type.getField(name)));
      } catch (NoSuchFieldException e) {
        // Declared by another interface of the binding, or not at all.
      }
    }
    return Optional.empty();
  }

  private static int value(Field field) {
    try {
      return field.getInt(null);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(field.getName(), e);
    }
  }
}
