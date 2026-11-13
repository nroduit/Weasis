/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mip;

import java.util.List;
import java.util.Optional;
import org.weasis.core.api.gui.util.DecFormatter;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.View2d;
import org.weasis.dicom.viewer2d.mip.MipView.Type;
import org.weasis.dicom.viewer2d.mpr.MprAxis;
import org.weasis.dicom.viewer2d.mpr.MprController;
import org.weasis.dicom.viewer2d.mpr.MprView;

/**
 * The thick slab of a pane: a projection mode and a thickness, whatever builds it. A stack shows it
 * through a {@link MipView} that takes the place of its pane, an MPR view through the thickness of
 * its axis. The thickness is counted by each in its own unit (images or voxels on each side of the
 * center, the "extent") and shown in mm.
 */
public abstract sealed class Slab permits Slab.Stack, Slab.Mip, Slab.Mpr {

  /** The thickness given to a slab turned on from a pane without one. */
  static final double DEFAULT_MM = 10.0;

  /** The thicknesses offered as shortcuts, in mm. */
  static final List<Double> PRESETS_MM = List.of(5.0, 10.0, 20.0, 40.0);

  /** The slab of a view, empty when the view cannot have one (no volume, a tile...). */
  public static Optional<Slab> of(ViewCanvas<DicomImageElement> view) {
    return switch (view) {
      case MprView mpr when mpr.getMprAxis() != null && mpr.getMprController() != null ->
          Optional.of(new Mpr(mpr));
      case MprView _ -> Optional.empty();
      case MipView mip -> Optional.of(new Mip(mip));
      case View2d stack when Stack.applies(stack) -> Optional.of(new Stack(stack));
      case null, default -> Optional.empty();
    };
  }

  /** "MIP 10 mm" for a pane showing a slab, null otherwise (cheap for a plain stack). */
  public static String summaryOf(ViewCanvas<DicomImageElement> view) {
    return view instanceof MipView || view instanceof MprView
        ? of(view).map(Slab::summary).orElse(null)
        : null;
  }

  /** The projection, {@link Type#NONE} when the pane shows plain slices. */
  public abstract Type type();

  /** The images or voxels on each side of the center. */
  public abstract int extent();

  /** The thickness in mm of a slab of that extent, 0 when unknown. */
  public abstract double millimeters(int extent);

  /** The extent closest to a thickness in mm. */
  public abstract int extentOf(double millimeters);

  public abstract int maxExtent();

  /** The unit of the extent, for the hint of the editor ("images", "voxels"). */
  abstract String extentUnit();

  /**
   * Sets the projection and the extent. A stack is replaced by a MIP view and a MIP view by a stack
   * when the projection is turned on or off.
   *
   * @return the slab of the pane afterwards, which may be another view
   */
  public abstract Slab apply(Type type, int extent);

  /** Builds a series from the slab (a new series in the explorer). */
  abstract void saveAsSeries();

  public abstract View2d view();

  public boolean isOn() {
    return type() != Type.NONE && extent() > 0;
  }

  public double millimeters() {
    return millimeters(extent());
  }

  /** "MIP 10 mm", or null when the slab is off. */
  public String summary() {
    if (!isOn()) {
      return null;
    }
    double mm = millimeters();
    String size =
        mm > 0
            ? DecFormatter.allNumber(mm) + " mm" // NON-NLS
            : extent() + " " + extentUnit();
    return modeLabel(type()) + " " + size;
  }

  /** The short name of a projection: MIP, MinIP, Mean. */
  public static String modeLabel(Type type) {
    return switch (type) {
      case MAX -> Messages.getString("Slab.max");
      case MIN -> Messages.getString("Slab.min");
      case MEAN -> Messages.getString("Slab.mean");
      case NONE -> Messages.common("none");
    };
  }

  // ---------------------------------------------------------------------------------------------

  /** A stack without projection: turning it on replaces the pane by a MIP view. */
  static final class Stack extends Slab {
    private final View2d view;

    Stack(View2d view) {
      this.view = view;
    }

    static boolean applies(View2d view) {
      MediaSeries<DicomImageElement> series = view.getSeries();
      return series != null
          && series.size(null) >= DefaultView2d.MINIMAL_IMAGES_FOR_3D
          && view.getEventManager() instanceof EventManager em
          && em.getSelectedViewPane() == view
          && em.getAction(MipMenu.AVAILABLE).map(a -> a.isActionEnabled()).orElse(false);
    }

    @Override
    public Type type() {
      return Type.NONE;
    }

    @Override
    public int extent() {
      return 0;
    }

    @Override
    public double millimeters(int extent) {
      return MipView.slabThickness(view.getSeries(), extent);
    }

    @Override
    public int extentOf(double millimeters) {
      return MipView.slabExtent(view.getSeries(), millimeters);
    }

    @Override
    public int maxExtent() {
      return Math.max(view.getSeries().size(null) / 2, 1);
    }

    @Override
    String extentUnit() {
      return Messages.getString("Slab.images");
    }

    @Override
    public Slab apply(Type type, int extent) {
      if (type == Type.NONE || !(view.getEventManager() instanceof EventManager em)) {
        return this;
      }
      ImageViewerPlugin<DicomImageElement> container = em.getSelectedView2dContainer();
      MipView mip = MipMenu.createMipView(em, container, view);
      if (mip == null) {
        return this;
      }
      return new Mip(mip).apply(type, extent);
    }

    @Override
    void saveAsSeries() {
      // nothing to build without projection
    }

    @Override
    public View2d view() {
      return view;
    }
  }

  /** A stack shown through a MIP view. */
  static final class Mip extends Slab {
    private final MipView view;

    Mip(MipView view) {
      this.view = view;
    }

    @Override
    public Type type() {
      return view.getActionValue(MipView.MIP.cmd()) instanceof Type t ? t : Type.NONE;
    }

    @Override
    public int extent() {
      return view.getActionValue(MipView.MIP_THICKNESS.cmd()) instanceof Integer i ? i : 0;
    }

    @Override
    public double millimeters(int extent) {
      return MipView.slabThickness(view.getSeries(), extent);
    }

    @Override
    public int extentOf(double millimeters) {
      return MipView.slabExtent(view.getSeries(), millimeters);
    }

    @Override
    public int maxExtent() {
      MediaSeries<DicomImageElement> series = view.getSeries();
      return series == null ? 1 : Math.max(series.size(null) / 2, 1);
    }

    @Override
    String extentUnit() {
      return Messages.getString("Slab.images");
    }

    @Override
    public Slab apply(Type type, int extent) {
      if (type == Type.NONE) {
        view.exitMipMode(view.getSeries(), null);
        View2d stack = view.getEventManager().getSelectedViewPane() instanceof View2d v ? v : view;
        return new Stack(stack);
      }
      int newExtent = Math.clamp(extent, 1, maxExtent());
      boolean changed = type() != type || extent() != newExtent;
      view.setActionsInView(MipView.MIP.cmd(), type);
      view.setActionsInView(MipView.MIP_THICKNESS.cmd(), newExtent);
      if (changed) {
        view.activateCrosslinesUpdate();
        MipView.buildMip(view);
      }
      view.repaint();
      return this;
    }

    @Override
    void saveAsSeries() {
      MipMenu.rebuildSeries(view);
    }

    @Override
    public View2d view() {
      return view;
    }
  }

  /**
   * An MPR view: the thickness belongs to its axis, the projection to the three views of the MPR (a
   * single setting of the MPR controller).
   */
  static final class Mpr extends Slab {
    private final MprView view;

    Mpr(MprView view) {
      this.view = view;
    }

    private MprController controller() {
      return view.getMprController();
    }

    private double voxel() {
      return controller().getVolume() == null ? 0 : controller().getVolume().getMinPixelRatio();
    }

    @Override
    public Type type() {
      return view.getMprAxis().getThicknessExtension() > 0
          ? (Type) controller().getMipTypeOption().getSelectedItem()
          : Type.NONE;
    }

    @Override
    public int extent() {
      return view.getMprAxis().getThicknessExtension();
    }

    @Override
    public double millimeters(int extent) {
      return (extent * 2 + 1) * voxel();
    }

    @Override
    public int extentOf(double millimeters) {
      double v = voxel();
      return v <= 0 ? 1 : Math.max(1, (int) Math.round((millimeters / v - 1) / 2));
    }

    @Override
    public int maxExtent() {
      return controller().getVolume() == null ? 1 : controller().getVolume().getSliceSize();
    }

    @Override
    String extentUnit() {
      return Messages.getString("Slab.voxels");
    }

    @Override
    public Slab apply(Type type, int extent) {
      apply(List.of(view.getMprAxis()), type, extent);
      return this;
    }

    /** Applies the slab to the three views of the MPR. */
    void applyToAll() {
      MprController c = controller();
      apply(List.of(c.getAxial(), c.getCoronal(), c.getSagittal()), type(), extent());
    }

    private void apply(List<MprAxis> axes, Type type, int extent) {
      MprController c = controller();
      boolean typeChanged = false;
      if (type != Type.NONE && c.getMipTypeOption().getSelectedItem() != type) {
        c.getMipTypeOption().setSelectedItemWithoutTriggerAction(type);
        typeChanged = true;
      }
      int newExtent = type == Type.NONE ? 0 : Math.clamp(extent, 1, maxExtent());
      for (MprAxis axis : axes) {
        axis.setThicknessExtension(newExtent);
        axis.updateImage();
      }
      if (typeChanged) {
        c.updateAllViews();
      }
      // The slab lines of this axis are drawn in the two other views
      for (MprAxis axis : List.of(c.getAxial(), c.getCoronal(), c.getSagittal())) {
        if (axis.getMprView() != null) {
          axis.getMprView().repaint();
        }
      }
    }

    @Override
    void saveAsSeries() {
      view.rebuildView(false);
    }

    @Override
    public View2d view() {
      return view;
    }
  }
}
