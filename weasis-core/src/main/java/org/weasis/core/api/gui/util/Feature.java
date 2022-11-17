/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Toolkit;
import java.util.Map;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.ui.editor.image.CrosshairListener;
import org.weasis.core.ui.editor.image.PannerListener;

public abstract class Feature<T> implements KeyActionValue {
  private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(Feature.class);
  public static final String DRAW_CMD_PREFIX = "draw.sub."; // NON-NLS

  public static final class BooleanValue extends Feature<Boolean> {
    public BooleanValue(String title, String command, int keyEvent, int modifier, Cursor cursor) {
      super(title, command, keyEvent, modifier, cursor);
    }
  }

  public static final class IntegerValue extends Feature<Integer> {
    public IntegerValue(String title, String command, int keyEvent, int modifier, Cursor cursor) {
      super(title, command, keyEvent, modifier, cursor);
    }
  }

  public static final class DoubleValue extends Feature<Double> {
    public DoubleValue(String title, String command, int keyEvent, int modifier, Cursor cursor) {
      super(title, command, keyEvent, modifier, cursor);
    }
  }

  public static final class SliderChangeListenerValue extends Feature<SliderChangeListener> {
    public SliderChangeListenerValue(
        String title, String command, int keyEvent, int modifier, Cursor cursor) {
      super(title, command, keyEvent, modifier, cursor);
    }
  }

  public static final class SliderCineListenerValue extends Feature<SliderCineListener> {
    public SliderCineListenerValue(
        String title, String command, int keyEvent, int modifier, Cursor cursor) {
      super(title, command, keyEvent, modifier, cursor);
    }
  }

  public static final class ComboItemListenerValue<V> extends Feature<ComboItemListener<V>> {
    public ComboItemListenerValue(
        String title, String command, int keyEvent, int modifier, Cursor cursor) {
      super(title, command, keyEvent, modifier, cursor);
    }
  }

  public static final class ToggleButtonListenerValue extends Feature<ToggleButtonListener> {
    public ToggleButtonListenerValue(
        String title, String command, int keyEvent, int modifier, Cursor cursor) {
      super(title, command, keyEvent, modifier, cursor);
    }
  }

  public static class BasicActionStateValue extends Feature<BasicActionState> {
    public BasicActionStateValue(
        String title, String command, int keyEvent, int modifier, Cursor cursor) {
      super(title, command, keyEvent, modifier, cursor);
    }
  }

  public static final class PannerListenerValue extends Feature<PannerListener> {
    public PannerListenerValue(
        String title, String command, int keyEvent, int modifier, Cursor cursor) {
      super(title, command, keyEvent, modifier, cursor);
    }
  }

  public static final class CrosshairListenerValue extends Feature<CrosshairListener> {
    public CrosshairListenerValue(
        String title, String command, int keyEvent, int modifier, Cursor cursor) {
      super(title, command, keyEvent, modifier, cursor);
    }
  }

  private final String title;
  private final String command;
  private final FlatSVGIcon icon;
  private final int keyCode;
  private final int modifier;
  private final Cursor cursor;

  public Feature(String title, String command, int keyEvent, int modifier, Cursor cursor) {
    this.title = title;
    this.command = command;
    this.keyCode = keyEvent;
    this.modifier = modifier;
    this.cursor = cursor;
    this.icon = ResourceUtil.getIcon("svg/action/" + command + ".svg"); // NON-NLS
  }

  public T getValue(Map<String, Object> map) {
    if (map != null) {
      try {
        return (T) map.get(command);
      } catch (ClassCastException e) {
        LOGGER.error("Cannot cast the value of \"{}\"", this, e);
      }
    }
    return null;
  }

  public String getTitle() {
    return title;
  }

  public String cmd() {
    return command;
  }

  @Override
  public String toString() {
    return title;
  }

  public FlatSVGIcon getIcon() {
    return icon;
  }

  @Override
  public int getKeyCode() {
    return keyCode;
  }

  public Cursor getCursor() {
    return cursor;
  }

  @Override
  public int getModifier() {
    return modifier;
  }

  public boolean isDrawingAction() {
    return false;
  }

  public boolean isGraphicListAction() {
    return command.startsWith(DRAW_CMD_PREFIX);
  }

  public Icon getDropButtonIcon() {
    return new DropButtonIcon(icon);
  }

  public static Cursor getSvgCursor(
      String filename, String cursorName, float hotSpotX, float hotSpotY) {
    return getCursor("svg/cursor/" + filename, cursorName, hotSpotX, hotSpotY); // NON-NLS
  }

  public static Cursor getImageCursor(
      String filename, String cursorName, float hotSpotX, float hotSpotY) {
    return getCursor("images/cursor/" + filename, cursorName, hotSpotX, hotSpotY); // NON-NLS
  }

  private static Cursor getCursor(String path, String cursorName, float hotSpotX, float hotSpotY) {
    Toolkit defaultToolkit = Toolkit.getDefaultToolkit();
    ImageIcon icon;
    Dimension bestCursorSize;
    if (path.startsWith("svg/")) { // NON-NLS
      FlatSVGIcon svgIcon = ResourceUtil.getIcon(path);
      bestCursorSize = defaultToolkit.getBestCursorSize(22, 22);
      icon = svgIcon.derive(bestCursorSize.width, bestCursorSize.height);
    } else {
      icon = new ImageIcon(ResourceUtil.getResource(path).getAbsolutePath());
      bestCursorSize = defaultToolkit.getBestCursorSize(icon.getIconWidth(), icon.getIconHeight());
    }
    Point hotSpot =
        new Point(
            Math.round(hotSpotX * bestCursorSize.width),
            Math.round(hotSpotY * bestCursorSize.height));
    return defaultToolkit.createCustomCursor(icon.getImage(), hotSpot, cursorName);
  }
}
