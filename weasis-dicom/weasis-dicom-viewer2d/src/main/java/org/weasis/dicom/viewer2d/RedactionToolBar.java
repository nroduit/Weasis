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
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import org.weasis.core.api.gui.util.DropButtonIcon;
import org.weasis.core.api.gui.util.DropDownButton;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MaskingModelRegistry;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.PixelMask;
import org.weasis.core.api.media.data.Taggable;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicArea;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.PixelMaskMatcher;
import org.weasis.dicom.codec.PixelMaskMatcher.Candidate;
import org.weasis.dicom.codec.PixelMaskMatcher.Reason;
import org.weasis.dicom.codec.Redaction;
import org.weasis.dicom.codec.Redaction.Mask;
import org.weasis.dicom.codec.Redaction.Scope;
import org.weasis.dicom.codec.display.RedactionOp;

/**
 * Turns drawn closed shapes into redaction regions burned over the pixels, for identity printed
 * into the image that annotation masking cannot reach.
 *
 * <p>Two steps on purpose: the user draws and sees the outline, then chooses the scope and commits.
 * A shape applied on the spot would give no chance to check what is about to be hidden, and the
 * scope is exactly the decision worth pausing on.
 *
 * <p>Regions already kept for a machine are picked from the device library instead of being
 * redrawn, and what is displayed is saved back into it.
 */
public class RedactionToolBar extends WtoolBar implements SeriesViewerListener {

  private final ImageViewerEventManager<DicomImageElement> eventManager;
  private final JComboBox<Scope> scopeCombo = new JComboBox<>(Scope.values());
  private final JButton saveButton;
  private final JLabel originLabel = new JLabel();

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

    saveButton =
        iconButton(
            ActionIcon.REDACT_SAVE,
            "RedactionToolBar.save", // NON-NLS
            "RedactionToolBar.save.tip", // NON-NLS
            this::saveForDevice);
    add(saveButton);
    add(
        iconButton(
            ActionIcon.REDACT_CLEAR,
            "RedactionToolBar.clear", // NON-NLS
            "RedactionToolBar.clear.tip", // NON-NLS
            this::clear));
    add(libraryButton());
    add(
        iconButton(
            ActionIcon.REDACT,
            "RedactionToolBar.apply", // NON-NLS
            "RedactionToolBar.apply.tip", // NON-NLS
            this::applySelection));
    add(originLabel);
    refreshState();
    // What the toolbar tells depends on the image in view: it has to follow the selection
    eventManager.addSeriesViewerListener(this);
  }

  /**
   * Another view, another series or another frame hides something else, and a reloaded
   * configuration may have locked the library since.
   */
  @Override
  public void changingViewContentEvent(SeriesViewerEvent event) {
    EVENT type = event.getEventType();
    if (type == EVENT.SELECT || type == EVENT.SELECT_VIEW || type == EVENT.LAYOUT) {
      refreshState();
    }
  }

  private void refreshState() {
    updateSaveState();
    updateOrigin(currentView());
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

    Scope scope = selectedScope();
    Taggable target = target(view, scope);
    // An edit starts from what is displayed: a proposal of the device library becomes the user's
    Redaction.materialize(view.getImage(), view.getSeries(), target);
    for (Graphic graphic : used) {
      // A copy: the graphic, and the shape it owns, are removed right after
      Redaction.add(target, new Path2D.Double(graphic.getShape()));
    }
    if (scope == Scope.SERIES) {
      Redaction.unsuppressFrames(view.getSeries());
    }
    used.forEach(Graphic::fireRemoveAndRepaintAction);
    refresh(view, scope);
  }

  /** Takes back what the chosen scope hides; {@link Redaction#reset} says what that means. */
  private void clear() {
    ViewCanvas<DicomImageElement> view = currentView();
    if (view == null) {
      return;
    }
    Redaction.reset(view.getImage(), view.getSeries(), selectedScope());
    refresh(view, selectedScope());
  }

  /** A menu of the device entries the image in view can take, built when it is opened. */
  private DropDownButton libraryButton() {
    DropDownButton button =
        new DropDownButton(
            "redact.library", // NON-NLS
            DropButtonIcon.createDropButtonIcon(
                ResourceUtil.getToolBarIcon(ActionIcon.REDACT_DEVICE)),
            null) {
          @Override
          protected JPopupMenu getPopupMenu() {
            JPopupMenu menu = libraryMenu();
            menu.setInvoker(this);
            return menu;
          }
        };
    button.setToolTipText(Messages.getString("RedactionToolBar.library.tip"));
    button.getAccessibleContext().setAccessibleName(Messages.getString("RedactionToolBar.library"));
    return button;
  }

  /**
   * The entries that accept the device and the shape of the image in view, the one in force ticked.
   * An entry the image cannot take — another device, another layout, disabled — is left out: its
   * regions would land on the anatomy rather than on the text they were drawn over.
   */
  private JPopupMenu libraryMenu() {
    JPopupMenu menu = new JPopupMenu();
    ViewCanvas<DicomImageElement> view = currentView();
    MaskingModelRegistry registry = MaskingModelRegistry.getInstance();
    if (view != null && view.getImage() != null) {
      Mask current = Redaction.effective(view.getImage(), view.getSeries());
      String origin = current == null ? null : current.origin();
      PixelMaskMatcher.evaluate(view.getSeries(), view.getImage(), registry.sessionProfile().id())
          .stream()
          .filter(RedactionToolBar::selectable)
          .forEach(candidate -> menu.add(libraryItem(candidate, origin)));
    }
    if (menu.getComponentCount() == 0) {
      JMenuItem none = new JMenuItem(Messages.getString("RedactionToolBar.library.none"));
      none.setEnabled(false);
      menu.add(none);
    }
    return menu;
  }

  /**
   * An entry restricted to other profiles stays offered: picking it is a decision of the user,
   * where the restriction only governs what the library proposes on its own.
   */
  private static boolean selectable(Candidate candidate) {
    return candidate.applies() || candidate.reason() == Reason.PROFILE;
  }

  /**
   * A radio item, not a check box: it says which entry the view hides, and picking it again is a
   * way to move it to the other scope, where unticking it would have no meaning. Regions are taken
   * back with Clear, which is where the scope of a removal is chosen.
   */
  private JMenuItem libraryItem(Candidate candidate, String origin) {
    PixelMask mask = candidate.mask();
    JMenuItem item = new JRadioButtonMenuItem(mask.name(), mask.id().equals(origin));
    item.setToolTipText(deviceLabel(mask));
    item.addActionListener(_ -> applyLibrary(mask));
    return item;
  }

  private static String deviceLabel(PixelMask mask) {
    List<String> values = mask.match().values();
    return values.isEmpty()
        ? Messages.getString("RedactionToolBar.library.any")
        : String.join(" / ", values);
  }

  /**
   * Burns the regions of a device entry over the view, all of them: the entry was chosen here, not
   * proposed by the profile in force.
   */
  private void applyLibrary(PixelMask entry) {
    ViewCanvas<DicomImageElement> view = currentView();
    if (view == null) {
      return;
    }
    Mask mask = Redaction.of(entry, view.getImage());
    if (mask == null) {
      JOptionPane.showMessageDialog(
          this,
          Messages.getString("RedactionToolBar.library.empty").formatted(entry.name()),
          Messages.getString("RedactionToolBar.title"),
          JOptionPane.WARNING_MESSAGE);
      return;
    }
    Redaction.apply(view.getImage(), view.getSeries(), selectedScope(), mask);
    refresh(view, selectedScope());
  }

  /**
   * Saves the regions shown in this view for the machine that produced the image, so they are
   * proposed on every image of that device while a masking profile is in force.
   */
  private void saveForDevice() {
    ViewCanvas<DicomImageElement> view = currentView();
    if (view == null) {
      return;
    }
    Mask mask = Redaction.effective(view.getImage(), view.getSeries());
    List<Shape> shapes = mask == null ? List.of() : mask.shapes();
    if (shapes.isEmpty()) {
      JOptionPane.showMessageDialog(
          this,
          Messages.getString("RedactionToolBar.save.none"),
          Messages.getString("RedactionToolBar.title"),
          JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    MediaSeries<DicomImageElement> series = view.getSeries();
    DeviceMaskDialog.ask(this, view.getImage(), series, shapes)
        .ifPresent(
            entry -> {
              try {
                MaskingModelRegistry.getInstance().saveUserMask(entry);
              } catch (IOException | RuntimeException e) {
                JOptionPane.showMessageDialog(
                    this,
                    e.getMessage(),
                    Messages.getString("RedactionToolBar.title"),
                    JOptionPane.ERROR_MESSAGE);
              }
            });
  }

  /** Whether a device entry can still be saved: a locked configuration is read-only. */
  private void updateSaveState() {
    saveButton.setEnabled(!MaskingModelRegistry.getInstance().isLocked());
  }

  /** Names the device entry whose regions the view is hiding, so it is never a silent change. */
  private void updateOrigin(ViewCanvas<DicomImageElement> view) {
    Mask mask = view == null ? null : Redaction.effective(view.getImage(), view.getSeries());
    String name =
        mask == null || !mask.fromLibrary()
            ? null
            : MaskingModelRegistry.getInstance()
                .pixelMask(mask.origin())
                .map(PixelMask::name)
                .orElse(mask.origin());
    originLabel.setText(
        name == null ? "" : Messages.getString("RedactionToolBar.applied").formatted(name));
    originLabel.setToolTipText(
        name == null ? null : Messages.getString("RedactionToolBar.applied.tip"));
  }

  /**
   * Resolves the regions again in every open 2D view, after the mask in force or the masking
   * document changed: an entry of the device library applies only under a profile that hides its
   * regions.
   */
  public static void refreshOpenViews() {
    List<ViewerPlugin<?>> plugins = GuiUtils.getUICore().getViewerPlugins();
    synchronized (plugins) {
      for (ViewerPlugin<?> plugin : plugins) {
        if (plugin instanceof View2dContainer container) {
          for (ViewCanvas<DicomImageElement> view : container.getImagePanels()) {
            view.getDisplayOpManager()
                .setParamValue(
                    RedactionOp.OP_NAME,
                    RedactionOp.P_MASK,
                    Redaction.effective(view.getImage(), view.getSeries()));
            view.getImageLayer().updateDisplayOperations();
            view.getJComponent().repaint();
          }
          container.getSeriesViewerUI().toolBars.stream()
              .filter(RedactionToolBar.class::isInstance)
              .map(RedactionToolBar.class::cast)
              .forEach(RedactionToolBar::refreshState);
        }
      }
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
   * A series is shown in more than one view, and in more than one container: the regions are pushed
   * everywhere, not only where the change was made.
   */
  private void refresh(ViewCanvas<DicomImageElement> view, Scope scope) {
    refreshOpenViews();
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
