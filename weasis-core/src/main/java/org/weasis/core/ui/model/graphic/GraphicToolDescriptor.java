/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic;

import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import javax.swing.JComponent;

/**
 * Describes one graphic tool: its stable key, the palette it belongs to, how to build a prototype,
 * the class that serializes it and the system property that hides it.
 *
 * @param key stable identity, lower case, namespaced ({@code weasis.line}, {@code myplugin.tool})
 * @param category palette of the tool
 * @param prototype builds a configured instance; the registry keeps one as the palette item
 * @param xmlType class bound to the tool's XML element, {@code null} when it is not persisted
 * @param hideProperty system property whose {@code false} value removes the tool
 * @param panel builds the panel shown in the Draw &amp; Measure tool while the tool is active or
 *     one of its graphics is selected; {@code null} for tools without options
 * @param shortcutKeyCode default key selecting the tool, {@code 0} when it has none; the user
 *     changes it in the shortcut preferences
 * @param shortcutModifier modifier mask of the default key
 * @param minPoints vertices the graphic cannot go below when one is removed; two for an open path,
 *     three for a closed one, more for a tool that needs it
 */
public record GraphicToolDescriptor(
    String key,
    ToolCategory category,
    Supplier<Graphic> prototype,
    Class<? extends Graphic> xmlType,
    String hideProperty,
    Function<GraphicToolContext, JComponent> panel,
    int shortcutKeyCode,
    int shortcutModifier,
    int minPoints) {

  public static final String HIDE_PROPERTY_PREFIX = "weasis.tool."; // NON-NLS

  /** Vertices below which a variable-point graphic is no longer a shape. */
  public static final int DEFAULT_MIN_POINTS = 2;

  private static final Pattern KEY = Pattern.compile("[a-z0-9]++(?:[.-][a-z0-9]++)*+");

  public GraphicToolDescriptor {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(category, "category");
    Objects.requireNonNull(prototype, "prototype");
    if (!KEY.matcher(key).matches()) {
      throw new IllegalArgumentException("Invalid tool key: " + key);
    }
    if (hideProperty == null) {
      hideProperty = HIDE_PROPERTY_PREFIX + key;
    }
    if (minPoints < DEFAULT_MIN_POINTS) {
      minPoints = DEFAULT_MIN_POINTS;
    }
  }

  /** Descriptor whose XML type is the prototype class when it carries an {@code XmlRootElement}. */
  public static GraphicToolDescriptor of(
      String key, ToolCategory category, Supplier<Graphic> prototype) {
    return new GraphicToolDescriptor(
        key, category, prototype, xmlTypeOf(prototype.get()), null, null, 0, 0, DEFAULT_MIN_POINTS);
  }

  public GraphicToolDescriptor withHideProperty(String property) {
    return new GraphicToolDescriptor(
        key,
        category,
        prototype,
        xmlType,
        property,
        panel,
        shortcutKeyCode,
        shortcutModifier,
        minPoints);
  }

  public GraphicToolDescriptor withPanel(Function<GraphicToolContext, JComponent> panelFactory) {
    return new GraphicToolDescriptor(
        key,
        category,
        prototype,
        xmlType,
        hideProperty,
        panelFactory,
        shortcutKeyCode,
        shortcutModifier,
        minPoints);
  }

  public GraphicToolDescriptor withShortcut(int keyCode, int modifier) {
    return new GraphicToolDescriptor(
        key, category, prototype, xmlType, hideProperty, panel, keyCode, modifier, minPoints);
  }

  public GraphicToolDescriptor withMinPoints(int min) {
    return new GraphicToolDescriptor(
        key,
        category,
        prototype,
        xmlType,
        hideProperty,
        panel,
        shortcutKeyCode,
        shortcutModifier,
        min);
  }

  public boolean hasPanel() {
    return panel != null;
  }

  /** Class used for the XML binding: the graphic class, or its parent for an anonymous subclass. */
  public static Class<? extends Graphic> xmlTypeOf(Graphic graphic) {
    Class<? extends Graphic> type = baseClass(graphic);
    return type.isAnnotationPresent(XmlRootElement.class) ? type : null;
  }

  @SuppressWarnings("unchecked")
  static Class<? extends Graphic> baseClass(Graphic graphic) {
    Class<?> type = graphic.getClass();
    while (type.isAnonymousClass()) {
      type = type.getSuperclass();
    }
    return (Class<? extends Graphic>) type;
  }
}
