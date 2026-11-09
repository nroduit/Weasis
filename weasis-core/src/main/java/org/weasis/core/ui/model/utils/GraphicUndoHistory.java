/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.utils;

import java.util.Objects;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.UndoManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.serialize.XmlSerializer;

/**
 * The undo history of the graphics of one view: a bounded stack of snapshots of the graphic model
 * of the image the view shows, as the XML the model persists, so an edit made by the mouse, by a
 * command or by a plugin is undone the same way. {@link UndoManager} does the bookkeeping only
 * (limit, can undo, can redo); an edit holds the model before and after and swaps them, no edit
 * knows how to invert itself. The view feeds it: {@link #track} when it shows an image, {@link
 * #commit} once a burst of changes has settled, and the restorer it was built with puts a snapshot
 * back on the image.
 *
 * <p>Not undoable, by design: closing a view, changing the series (the history is cleared), and the
 * import of a presentation state. A model above {@link #P_MAX_GRAPHICS} graphics is not tracked, a
 * snapshot per edit would cost too much there.
 */
public final class GraphicUndoHistory {

  private static final Logger LOGGER = LoggerFactory.getLogger(GraphicUndoHistory.class);

  /** Entries kept per view; 0 disables the history. */
  public static final String P_LIMIT = "weasis.draw.undo.limit"; // NON-NLS

  public static final int DEFAULT_LIMIT = 50;

  /** A model with more graphics than this is not tracked. */
  public static final String P_MAX_GRAPHICS = "weasis.draw.undo.maxGraphics"; // NON-NLS

  public static final int DEFAULT_MAX_GRAPHICS = 500;

  private static volatile int limit = -1;
  private static volatile int maxGraphics = -1;

  /** Puts a snapshot back on an image: the view's side of an undo or a redo. */
  @FunctionalInterface
  public interface Restorer {
    void restore(ImageElement image, String xml);
  }

  /** One edit: the model before and after a burst of changes. */
  private final class SnapshotEdit extends AbstractUndoableEdit {
    private final ImageElement edited;
    private final String before;
    private final String after;

    SnapshotEdit(ImageElement edited, String before, String after) {
      this.edited = edited;
      this.before = before;
      this.after = after;
    }

    @Override
    public void undo() {
      super.undo();
      apply(edited, before);
    }

    @Override
    public void redo() {
      super.redo();
      apply(edited, after);
    }

    @Override
    public String getPresentationName() {
      return ""; // the menu says "Undo" and "Redo", the kind of edit is not named
    }
  }

  private final UndoManager manager = new UndoManager();
  private final Restorer restorer;
  private ImageElement image;
  private GraphicModel model;
  private String current;
  private boolean applying;

  public GraphicUndoHistory(Restorer restorer) {
    this(restorer, limit());
  }

  GraphicUndoHistory(Restorer restorer, int entries) {
    this.restorer = Objects.requireNonNull(restorer);
    manager.setLimit(Math.max(0, entries));
  }

  /** The entries kept per view, from the preferences. */
  public static int limit() {
    int value = limit;
    if (value < 0) {
      value = readInt(P_LIMIT, DEFAULT_LIMIT);
      limit = value;
    }
    return value;
  }

  static int maxGraphics() {
    int value = maxGraphics;
    if (value < 0) {
      value = readInt(P_MAX_GRAPHICS, DEFAULT_MAX_GRAPHICS);
      maxGraphics = value;
    }
    return value;
  }

  /** Forgets the cached preferences, after the preference page changed them. */
  public static void reload() {
    limit = -1;
    maxGraphics = -1;
  }

  private static int readInt(String key, int def) {
    try {
      return GuiUtils.getUICore().getSystemPreferences().getIntProperty(key, def);
    } catch (RuntimeException e) {
      return def; // no UI core in a headless test
    }
  }

  /** True when the history keeps entries at all. */
  public boolean isEnabled() {
    return manager.getLimit() > 0;
  }

  /**
   * Follows the model of the image the view shows now. The history keeps its entries: an entry of
   * another image of the same series is undone on that image.
   */
  public void track(ImageElement image, GraphicModel model) {
    this.image = image;
    this.model = model;
    this.current = snapshot(model);
  }

  /** Forgets everything: the view changed series, or the history was turned off. */
  public void clear() {
    manager.discardAllEdits();
    image = null;
    model = null;
    current = null;
  }

  /**
   * Records what changed since the last snapshot as one entry.
   *
   * @return true when an entry was added; false when nothing changed, nothing is tracked, the
   *     history is off, or a snapshot is being applied
   */
  public boolean commit() {
    if (applying || model == null || !isEnabled()) {
      return false;
    }
    String after = snapshot(model);
    if (after == null || after.equals(current)) {
      return false;
    }
    if (current != null) {
      manager.addEdit(new SnapshotEdit(image, current, after));
    }
    current = after;
    return true;
  }

  public boolean canUndo() {
    return isEnabled() && manager.canUndo();
  }

  public boolean canRedo() {
    return isEnabled() && manager.canRedo();
  }

  /** Undoes the last entry; false when there is none. */
  public boolean undo() {
    if (!canUndo()) {
      return false;
    }
    manager.undo();
    return true;
  }

  /** Redoes the last undone entry; false when there is none. */
  public boolean redo() {
    if (!canRedo()) {
      return false;
    }
    manager.redo();
    return true;
  }

  private void apply(ImageElement edited, String xml) {
    applying = true;
    try {
      restorer.restore(edited, xml);
      if (edited == image) {
        current = xml;
      }
    } finally {
      applying = false;
    }
  }

  private static String snapshot(GraphicModel model) {
    if (model == null || model.getAllGraphics().size() > maxGraphics()) {
      return null;
    }
    try {
      return XmlSerializer.toXml(model);
    } catch (Exception e) {
      LOGGER.debug("Cannot snapshot the graphics", e);
      return null;
    }
  }
}
