/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import java.awt.Component;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JOptionPane;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.Taggable;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicArea;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.Redaction;
import org.weasis.dicom.codec.Redaction.Scope;
import org.weasis.dicom.codec.display.RedactionOp;

/**
 * Turns drawn closed shapes into redaction regions burned over the pixels, for identity printed
 * into the image that annotation masking cannot reach.
 *
 * <p>Two steps on purpose: the user draws and sees the outline, then chooses the scope and commits.
 * A shape applied on the spot would give no chance to check what is about to be hidden, and the
 * scope is exactly the decision worth pausing on.
 */
public class RedactionToolBar extends WtoolBar {

  private final ImageViewerEventManager<DicomImageElement> eventManager;
  private final JComboBox<Scope> scopeCombo = new JComboBox<>(Scope.values());

  public RedactionToolBar(ImageViewerEventManager<DicomImageElement> eventManager, int index) {
    super(Messages.getString("RedactionToolBar.title"), index);
    if (eventManager == null) {
      throw new IllegalArgumentException("EventManager cannot be null");
    }
    this.eventManager = eventManager;

    scopeCombo.setRenderer(labelRenderer(v -> v instanceof Scope scope ? scopeLabel(scope) : v));
    scopeCombo.setToolTipText(Messages.getString("RedactionToolBar.scope.tip"));
    GuiUtils.setPreferredWidth(scopeCombo, 100);
    add(scopeCombo);

    add(
        iconButton(
            ActionIcon.REDACT,
            "RedactionToolBar.apply", // NON-NLS
            "RedactionToolBar.apply.tip", // NON-NLS
            this::applySelection));
    add(
        iconButton(
            ActionIcon.REDACT_CLEAR,
            "RedactionToolBar.clear", // NON-NLS
            "RedactionToolBar.clear.tip", // NON-NLS
            this::clear));
  }

  /** An icon-only button; its label stays available to screen readers. */
  private static JButton iconButton(
      ActionIcon icon, String nameKey, String tipKey, Runnable action) {
    JButton button = new JButton(ResourceUtil.getToolBarIcon(icon));
    button.setToolTipText(Messages.getString(tipKey));
    button.getAccessibleContext().setAccessibleName(Messages.getString(nameKey));
    button.addActionListener(_ -> action.run());
    return button;
  }

  private static DefaultListCellRenderer labelRenderer(UnaryOperator<Object> label) {
    return new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(
          JList<?> list, Object value, int index, boolean selected, boolean focus) {
        return super.getListCellRendererComponent(list, label.apply(value), index, selected, focus);
      }
    };
  }

  private static String scopeLabel(Scope scope) {
    return switch (scope) {
      case IMAGE -> Messages.getString("RedactionToolBar.scope.image");
      case SERIES -> Messages.getString("RedactionToolBar.scope.series");
    };
  }

  private Scope selectedScope() {
    return (Scope) scopeCombo.getSelectedItem();
  }

  /**
   * Converts the selected closed shapes into regions, then removes them: the pixels now hide them.
   * Only a closed outline delimits an area to hide, so lines, angles and text are left alone.
   */
  private void applySelection() {
    ViewCanvas<DicomImageElement> view = currentView();
    if (view == null) {
      return;
    }
    List<Graphic> used = new ArrayList<>();
    for (Graphic graphic : List.copyOf(view.getGraphicManager().getSelectedGraphics())) {
      if (graphic instanceof GraphicArea
          && Boolean.TRUE.equals(graphic.isGraphicComplete())
          && graphic.getShape() != null) {
        used.add(graphic);
      }
    }
    if (used.isEmpty()) {
      JOptionPane.showMessageDialog(
          this,
          Messages.getString("RedactionToolBar.no.selection"),
          Messages.getString("RedactionToolBar.title"),
          JOptionPane.INFORMATION_MESSAGE);
      return;
    }

    Taggable target = target(view, selectedScope());
    for (Graphic graphic : used) {
      // A copy: the graphic, and the shape it owns, are removed right after
      Redaction.add(target, new Path2D.Double(graphic.getShape()));
    }
    used.forEach(Graphic::fireRemoveAndRepaintAction);
    refresh(view, selectedScope());
  }

  private void clear() {
    ViewCanvas<DicomImageElement> view = currentView();
    if (view != null) {
      Redaction.clear(target(view, selectedScope()));
      refresh(view, selectedScope());
    }
  }

  /**
   * A series mask is stored on the series, never copied onto its frames: a multi-frame series is
   * one image element per frame, so copies would drift as soon as one is edited.
   */
  private static Taggable target(ViewCanvas<DicomImageElement> view, Scope scope) {
    return scope == Scope.SERIES ? view.getSeries() : view.getImage();
  }

  /**
   * The operation reads the regions only when the image changes, so every view of the container
   * showing the edited series gets them pushed before its display is rebuilt.
   */
  private void refresh(ViewCanvas<DicomImageElement> view, Scope scope) {
    ImageViewerPlugin<DicomImageElement> container = eventManager.getSelectedView2dContainer();
    List<ViewCanvas<DicomImageElement>> views =
        container == null ? List.of(view) : container.getImagePanels();
    for (ViewCanvas<DicomImageElement> v : views) {
      if (v.getSeries() == view.getSeries()) {
        v.getDisplayOpManager()
            .setParamValue(
                RedactionOp.OP_NAME,
                RedactionOp.P_MASK,
                Redaction.effective(v.getImage(), v.getSeries()));
        v.getImageLayer().updateDisplayOperations();
        v.getJComponent().repaint();
      }
    }
    if (scope == Scope.SERIES) {
      // The explorer thumbnail is built outside the view pipeline and cached: rebuild it, or it
      // keeps showing the pixels the mask now hides.
      Redaction.refreshThumbnail(view.getSeries());
    }
  }

  private ViewCanvas<DicomImageElement> currentView() {
    return eventManager.getSelectedViewPane();
  }
}
