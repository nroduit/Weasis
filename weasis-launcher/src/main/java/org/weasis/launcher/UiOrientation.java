/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.launcher;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Container;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.ContainerEvent;
import java.awt.event.WindowEvent;
import java.text.AttributedCharacterIterator;
import java.text.CharacterIterator;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JSplitPane;
import javax.swing.text.html.HTMLEditorKit;

/**
 * The orientation of the interface: right to left for the languages written right to left, left to
 * right otherwise, decided once at start-up from the default locale and the {@value #PREFERENCE}
 * preference.
 *
 * <p>When the interface is right to left, every window, dialog and popup follows when it opens, and
 * every component added later follows when it is added, so that no dialog is mirrored by hand.
 *
 * <p>A {@link JSplitPane} keeps its children where its code put them (Swing would swap them, top
 * and bottom included): a horizontal split that must mirror places its children by the orientation
 * of the interface itself.
 *
 * <p>The viewer panes never mirror: a component marked with {@link #fix(JComponent)} (the client
 * property {@value #FIXED} set to {@code true}) and its whole subtree stay left to right.
 */
public final class UiOrientation {

  /** The preference: {@code auto} (from the language, the default), {@code ltr} or {@code rtl}. */
  public static final String PREFERENCE = "weasis.ui.orientation"; // NON-NLS

  /** The client property of a component whose subtree never mirrors. */
  public static final String FIXED = "weasis.orientation.fixed"; // NON-NLS

  public static final String AUTO = "auto"; // NON-NLS
  public static final String LTR = "ltr"; // NON-NLS
  public static final String RTL = "rtl"; // NON-NLS

  /** U+2068 FIRST STRONG ISOLATE. */
  public static final char FSI = '\u2068';

  /** U+2069 POP DIRECTIONAL ISOLATE. */
  public static final char PDI = '\u2069';

  /** The CSS rule that makes HTML text start on the right. */
  static final String RTL_HTML_RULE =
      "body, p, div, td, li, dd, dt { text-align: right }"; // NON-NLS

  private static volatile ComponentOrientation current;
  private static AWTEventListener listener;

  private UiOrientation() {}

  /**
   * The orientation for a locale and a value of the preference; an unknown or empty value is {@code
   * auto}.
   */
  public static ComponentOrientation resolve(Locale locale, String preference) {
    String mode = preference == null ? AUTO : preference.trim().toLowerCase(Locale.ROOT);
    return switch (mode) {
      case LTR -> ComponentOrientation.LEFT_TO_RIGHT;
      case RTL -> ComponentOrientation.RIGHT_TO_LEFT;
      default ->
          ComponentOrientation.getOrientation(Objects.requireNonNullElse(locale, Locale.ENGLISH))
                  .isLeftToRight()
              ? ComponentOrientation.LEFT_TO_RIGHT
              : ComponentOrientation.RIGHT_TO_LEFT;
    };
  }

  /** The orientation of the interface; from the default locale when none was installed. */
  public static ComponentOrientation get() {
    ComponentOrientation o = current;
    return o == null ? resolve(Locale.getDefault(), AUTO) : o;
  }

  /** True when the interface is right to left. */
  public static boolean isRightToLeft() {
    return !get().isLeftToRight();
  }

  /**
   * Sets the orientation of the interface and, when it differs from left to right or from the one
   * of the locale, makes every window and every component added later follow it. Called once at
   * start-up, after the locale is set and before the first window.
   */
  public static synchronized void install(ComponentOrientation orientation) {
    current = Objects.requireNonNull(orientation);
    Toolkit toolkit = Toolkit.getDefaultToolkit();
    if (listener != null) {
      toolkit.removeAWTEventListener(listener);
      listener = null;
    }
    boolean localeLtr = ComponentOrientation.getOrientation(Locale.getDefault()).isLeftToRight();
    if (orientation.isLeftToRight() && localeLtr) {
      return; // nothing to do: a component is laid out left to right by default
    }
    if (!orientation.isLeftToRight()) {
      // Swing's HTML views (labels, option panes, editor panes) align their text by CSS, not by the
      // orientation of the component: the paragraphs and cells start on the right. The shared style
      // sheet of HTMLEditorKit is linked in every HTML document created afterwards; an explicit
      // align attribute and the centered elements keep theirs.
      new HTMLEditorKit().getStyleSheet().addRule(RTL_HTML_RULE);
    }
    listener = UiOrientation::dispatched;
    toolkit.addAWTEventListener(
        listener, AWTEvent.WINDOW_EVENT_MASK | AWTEvent.CONTAINER_EVENT_MASK);
  }

  private static void dispatched(AWTEvent event) {
    if (event.getID() == WindowEvent.WINDOW_OPENED && event.getSource() instanceof Window w) {
      if (!w.getComponentOrientation().equals(get())) {
        apply(w, get());
        w.validate();
      }
    } else if (event.getID() == ContainerEvent.COMPONENT_ADDED
        && event instanceof ContainerEvent e) {
      Component child = e.getChild();
      if (child != null && !isInFixed(e.getContainer()) && !isFixed(child)) {
        if (!child.getComponentOrientation().equals(get())) {
          apply(child, get());
        }
      }
    }
  }

  /**
   * Applies an orientation to a component and its subtree, except the {@linkplain #fix fixed}
   * subtrees, which are set left to right.
   */
  public static void apply(Component c, ComponentOrientation orientation) {
    if (isFixed(c)) {
      c.applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
      return;
    }
    if (!(c instanceof JSplitPane)) {
      // A split pane swaps its two children when its orientation changes, top and bottom included:
      // it keeps them where its code put them, only its children are oriented
      c.setComponentOrientation(orientation);
    }
    if (c instanceof JMenu menu) {
      apply(menu.getPopupMenu(), orientation);
    }
    if (c instanceof Container container) {
      for (Component child : container.getComponents()) {
        apply(child, orientation);
      }
    }
  }

  /** Marks a component as never mirrored: it and its subtree stay left to right. */
  public static <T extends JComponent> T fix(T c) {
    c.putClientProperty(FIXED, Boolean.TRUE);
    c.applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
    return c;
  }

  /** True for a component marked with {@link #fix(JComponent)}. */
  public static boolean isFixed(Component c) {
    return c instanceof JComponent jc && Boolean.TRUE.equals(jc.getClientProperty(FIXED));
  }

  private static boolean isInFixed(Component c) {
    for (Component p = c; p != null; p = p.getParent()) {
      if (isFixed(p)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Fills a message: with {@link MessageFormat#format} when the interface is left to right; when it
   * is right to left, each argument is also wrapped in Unicode isolates ({@value #FSI} … {@value
   * #PDI}) so that a value of the other direction (a Latin patient name, a path, a UID, {@code 12.5
   * mm}) keeps its order inside the sentence. An argument inside an HTML tag (an attribute value)
   * is left as is.
   */
  public static String format(MessageFormat format, Object... args) {
    Object[] values = args == null ? new Object[0] : args;
    if (!isRightToLeft()) {
      return format.format(values);
    }
    return isolateArguments(format, values);
  }

  static String isolateArguments(MessageFormat format, Object[] values) {
    AttributedCharacterIterator it = format.formatToCharacterIterator(values);
    StringBuilder out = new StringBuilder(it.getEndIndex() - it.getBeginIndex() + 8);
    char c = it.first();
    while (c != CharacterIterator.DONE) {
      int limit = it.getRunLimit(MessageFormat.Field.ARGUMENT);
      boolean isolate = it.getAttribute(MessageFormat.Field.ARGUMENT) != null && !insideTag(out);
      if (isolate) {
        out.append(FSI);
      }
      while (c != CharacterIterator.DONE && it.getIndex() < limit) {
        out.append(c);
        c = it.next();
      }
      if (isolate) {
        out.append(PDI);
      }
    }
    return out.toString();
  }

  private static boolean insideTag(CharSequence text) {
    for (int i = text.length() - 1; i >= 0; i--) {
      char ch = text.charAt(i);
      if (ch == '>') {
        return false;
      }
      if (ch == '<') {
        return true;
      }
    }
    return false;
  }
}
