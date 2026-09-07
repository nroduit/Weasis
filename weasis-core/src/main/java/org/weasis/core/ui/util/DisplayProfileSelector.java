/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.editor.image.DisplayProfile;
import org.weasis.core.ui.editor.image.LiveCaptureView;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.layer.LayerItem;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.util.StringUtil;

/**
 * Chooses what a re-rendered capture of a view shows: as displayed, the image alone, or a custom
 * choice edited like the Display tool and pre-filled from the view. The choice is kept for the
 * session, so the next capture starts from it.
 */
public class DisplayProfileSelector extends JComboBox<DisplayProfileSelector.Mode> {

  public enum Mode {
    AS_DISPLAYED("capture.display.as.displayed"), // NON-NLS
    IMAGE_ONLY("capture.display.image.only"), // NON-NLS
    CUSTOM("capture.display.custom"); // NON-NLS

    private final String key;

    Mode(String key) {
      this.key = key;
    }

    @Override
    public String toString() {
      return Messages.getString(key);
    }
  }

  private static volatile Mode lastMode = Mode.AS_DISPLAYED;
  private static volatile DisplayProfile lastCustom;

  private final Supplier<ViewCanvas<?>> reference;
  private final JButton editButton = new JButton(ResourceUtil.getIcon(OtherIcon.IMAGE_EDIT));
  private DisplayProfile custom = lastCustom;
  private Mode previous;

  /**
   * @param reference the view a custom choice starts from when there is none yet
   */
  public DisplayProfileSelector(Supplier<ViewCanvas<?>> reference) {
    super(Mode.values());
    this.reference = reference;
    setToolTipText(Messages.getString("capture.display.tip"));
    Mode start = lastMode == Mode.CUSTOM && custom == null ? Mode.AS_DISPLAYED : lastMode;
    setSelectedItem(start);
    previous = start;
    editButton.setToolTipText(Messages.getString("capture.display.edit"));
    editButton.addActionListener(_ -> editCustom());
    addActionListener(_ -> modeChanged());
    updateEditButton();
  }

  /** The button that reopens the custom choice, to lay out next to the selector. */
  public JButton getEditButton() {
    return editButton;
  }

  public Mode getMode() {
    return (Mode) getSelectedItem();
  }

  /**
   * What the capture of a view shows under the current choice: {@code null} means as displayed.
   * Resolved per view, so a recording that moves to another view keeps the same kind of choice.
   */
  public Function<ViewCanvas<?>, DisplayProfile> getSelection() {
    DisplayProfile fixed = custom;
    return switch (getMode()) {
      case AS_DISPLAYED -> _ -> null;
      case IMAGE_ONLY -> view -> view == null ? null : DisplayProfile.of(view).imageOnly();
      case CUSTOM -> _ -> fixed;
    };
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    // Null while the superclass constructor runs
    if (editButton != null) {
      updateEditButton();
    }
  }

  private void modeChanged() {
    Mode mode = getMode();
    if (mode == Mode.CUSTOM && mode != previous && !editCustom()) {
      setSelectedItem(previous);
      return;
    }
    previous = mode;
    lastMode = mode;
    updateEditButton();
  }

  private void updateEditButton() {
    editButton.setEnabled(isEnabled() && getMode() == Mode.CUSTOM);
  }

  /** Opens the custom choice; {@code false} when the user cancels or there is nothing to edit. */
  private boolean editCustom() {
    ViewCanvas<?> view = reference.get();
    DisplayProfile start = custom != null ? custom : view == null ? null : DisplayProfile.of(view);
    if (start == null) {
      return false;
    }
    Editor editor = new Editor(start, view);
    int answer =
        JOptionPane.showConfirmDialog(
            this,
            editor.panel,
            Messages.getString("capture.display"),
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);
    if (answer != JOptionPane.OK_OPTION) {
      return false;
    }
    custom = editor.profile();
    lastCustom = custom;
    return true;
  }

  /**
   * The choices of the view's Display tool as check boxes, a group's items enabled only with the
   * group. A view painted live (3D) offers its annotations only; what it does not offer keeps the
   * value it started with.
   */
  private static final class Editor {
    private final JPanel panel = new JPanel(new MigLayout("insets 0, wrap 1, gapy 2lp")); // NON-NLS
    private final JCheckBox image = new JCheckBox(Messages.getString("capture.display.image"));
    private final JCheckBox overlay = dicom(ActionW.IMAGE_OVERLAY.getTitle());
    private final JCheckBox shutter = dicom(ActionW.IMAGE_SHUTTER.getTitle());
    private final JCheckBox padding = dicom(ActionW.IMAGE_PIX_PADDING.getTitle());
    private final JCheckBox annotations =
        new JCheckBox(Messages.getString("capture.display.annotations"));
    private final Map<LayerItem, JCheckBox> items = new LinkedHashMap<>();
    private final JCheckBox drawings = new JCheckBox(ActionW.DRAWINGS.getTitle());
    private final JCheckBox crosslines = new JCheckBox(LayerType.CROSSLINES.toString());

    private final DisplayProfile start;

    Editor(DisplayProfile start, ViewCanvas<?> view) {
      this.start = start;
      image.setSelected(start.image());
      overlay.setSelected(start.overlay());
      shutter.setSelected(start.shutter());
      padding.setSelected(start.pixelPadding());
      annotations.setSelected(start.annotations());
      List<LayerItem> offered =
          view == null ? DisplayProfile.CAPTURE_ITEMS : DisplayProfile.itemsFor(view);
      for (LayerItem item : offered) {
        JCheckBox box = new JCheckBox(item.toString());
        box.setSelected(start.annotationItems().contains(item));
        items.put(item, box);
      }
      drawings.setSelected(start.drawings());
      crosslines.setSelected(start.crosslines());

      boolean live = view instanceof LiveCaptureView;
      if (!live) {
        boolean dicom = view == null || DisplayProfile.hasDicomImageOptions(view);
        group(image, dicom ? List.of(overlay, shutter, padding) : List.of());
      }
      group(annotations, new ArrayList<>(items.values()));
      if (!live) {
        group(drawings, List.of(crosslines));
      }
    }

    private static JCheckBox dicom(String title) {
      return new JCheckBox("DICOM" + StringUtil.SPACE + title); // NON-NLS
    }

    private void group(JCheckBox parent, List<JCheckBox> children) {
      panel.add(parent);
      children.forEach(c -> panel.add(c, "gapleft 20lp")); // NON-NLS
      Runnable sync = () -> children.forEach(c -> c.setEnabled(parent.isSelected()));
      parent.addActionListener(_ -> sync.run());
      sync.run();
    }

    DisplayProfile profile() {
      Set<LayerItem> shown = EnumSet.noneOf(LayerItem.class);
      start.annotationItems().stream().filter(i -> !items.containsKey(i)).forEach(shown::add);
      items.forEach(
          (item, box) -> {
            if (box.isSelected()) {
              shown.add(item);
            }
          });
      return new DisplayProfile(
          image.isSelected(),
          overlay.isSelected(),
          shutter.isSelected(),
          padding.isSelected(),
          annotations.isSelected(),
          shown,
          drawings.isSelected(),
          crosslines.isSelected());
    }
  }

  /** A label for this selector, with the shared wording. */
  public JLabel createLabel() {
    JLabel label = new JLabel(Messages.getString("capture.display") + StringUtil.COLON_AND_SPACE);
    label.setLabelFor(this);
    return label;
  }
}
