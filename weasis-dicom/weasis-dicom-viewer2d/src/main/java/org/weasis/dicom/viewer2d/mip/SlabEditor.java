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

import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import org.weasis.core.api.gui.util.DecFormatter;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.ui.editor.image.InfoValueEditor;
import org.weasis.core.ui.editor.image.ShowPopup;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.mip.MipView.Type;

/**
 * The editor of the slab of a pane, the same from the corner text, the toolbar and the context
 * menu: the projection (the buttons keep the popup open), the thickness in mm (Enter applies it; on
 * a pane without slab, a thickness turns a MIP on), shortcuts of thickness, and the series built
 * from the slab.
 */
public final class SlabEditor {

  /** The order of the projections in the editor: off first, then the most used. */
  private static final List<Type> MODES = List.of(Type.NONE, Type.MAX, Type.MIN, Type.MEAN);

  private final JPopupMenu popup = new JPopupMenu();
  private Slab slab;
  private final JSpinner thickness;
  private final JLabel hint = new JLabel();
  private final List<JComponent> needSlab = new ArrayList<>();

  /** Set while the editor itself changes the thickness field. */
  private boolean updating;

  private SlabEditor(Slab slab) {
    this.slab = slab;
    int gap = GuiUtils.getScaleLength(5);

    JPanel modes = new JPanel(new FlowLayout(FlowLayout.LEADING, gap, 0));
    modes.setOpaque(false);
    ButtonGroup group = new ButtonGroup();
    for (Type type : MODES) {
      JRadioButton radio = new JRadioButton(Slab.modeLabel(type), type == slab.type());
      radio.setOpaque(false);
      group.add(radio);
      radio.addActionListener(_ -> selectMode(type));
      modes.add(radio);
    }

    double mm = slab.isOn() ? slab.millimeters() : Slab.DEFAULT_MM;
    double max = Math.max(slab.millimeters(slab.maxExtent()), mm);
    double step = Math.max(slab.millimeters(1) - slab.millimeters(0), 0.1);
    thickness =
        new JSpinner(
            new SpinnerNumberModel(round(mm), 0.0, Math.max(round(max), 1.0), round(step)));
    thickness.setEditor(new JSpinner.NumberEditor(thickness, "0.#"));
    GuiUtils.setSpinnerWidth(thickness, 5);
    // Applied when the value changes (arrows, Enter, losing the focus), as the other spinners of
    // Weasis; a short delay keeps the arrows held down from building a projection at each step
    GuiUtils.formatCheckAction(thickness);
    Timer applyLater = new Timer(250, _ -> applyThickness(value()));
    applyLater.setRepeats(false);
    thickness.addChangeListener(
        _ -> {
          updateHint();
          if (!updating) {
            applyLater.restart();
          }
        });
    textField().addActionListener(_ -> popup.setVisible(false));
    // A typed thickness is applied when the popup closes, whatever closes it
    popup.addPopupMenuListener(
        new PopupMenuListener() {
          @Override
          public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            try {
              thickness.commitEdit();
            } catch (ParseException _) {
              // an invalid text is reverted
            }
            if (applyLater.isRunning()) {
              applyLater.stop();
              applyThickness(value());
            }
          }

          @Override
          public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            // nothing to do
          }

          @Override
          public void popupMenuCanceled(PopupMenuEvent e) {
            // nothing to do
          }
        });

    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);
    panel.setBorder(BorderFactory.createEmptyBorder(gap, gap * 2, gap, gap * 2));
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(gap / 2, 0, gap / 2, gap);
    c.anchor = GridBagConstraints.LINE_START;
    c.gridy = 0;
    panel.add(new JLabel(Messages.getString("MipPopup.projection") + StringUtil.COLON), c);
    c.gridwidth = 2;
    panel.add(modes, c);
    c.gridwidth = 1;
    c.gridy = 1;
    panel.add(new JLabel(Messages.getString("Slab.thickness") + StringUtil.COLON), c);
    JPanel size = new JPanel(new FlowLayout(FlowLayout.LEADING, gap, 0));
    size.setOpaque(false);
    size.add(thickness);
    size.add(new JLabel("mm")); // NON-NLS
    size.add(hint);
    c.gridwidth = 2;
    panel.add(size, c);
    popup.add(panel);

    popup.addSeparator();
    for (double preset : Slab.PRESETS_MM) {
      JMenuItem item = new JMenuItem(DecFormatter.allNumber(preset) + " mm"); // NON-NLS
      item.addActionListener(_ -> applyThickness(preset));
      popup.add(item);
    }
    if (slab instanceof Slab.Mpr) {
      popup.addSeparator();
      JMenuItem all = new JMenuItem(Messages.getString("Slab.apply_all"));
      all.addActionListener(_ -> ((Slab.Mpr) this.slab).applyToAll());
      popup.add(all);
      needSlab.add(all);
    }
    popup.addSeparator();
    JMenuItem save = new JMenuItem(Messages.getString("build.series"));
    save.addActionListener(_ -> this.slab.saveAsSeries());
    popup.add(save);
    needSlab.add(save);

    updateEnabled();
    updateHint();
  }

  /** The editor of the slab of the pane, opened above a point (the values are at the bottom). */
  public static ShowPopup editor(ViewCanvas<DicomImageElement> view) {
    return (invoker, x, y) ->
        Slab.of(view).ifPresent(s -> new SlabEditor(s).showAbove(invoker, x, y));
  }

  /** Shows the editor below a point (a toolbar button). */
  public static void show(ViewCanvas<DicomImageElement> view, Component invoker, int x, int y) {
    Slab.of(view)
        .ifPresent(
            s -> {
              SlabEditor editor = new SlabEditor(s);
              editor.popup.show(invoker, x, y);
              editor.focusField();
            });
  }

  /** The Slab submenu of the context menu, null when the pane cannot have a slab. */
  public static JMenu menu(ViewCanvas<DicomImageElement> view, Component invoker, int x, int y) {
    Slab slab = Slab.of(view).orElse(null);
    if (slab == null) {
      return null;
    }
    JMenu menu = new JMenu(Messages.getString("Slab.title"));
    ButtonGroup group = new ButtonGroup();
    for (Type type : MODES) {
      JRadioButtonMenuItem item =
          new JRadioButtonMenuItem(Slab.modeLabel(type), type == slab.type());
      item.addActionListener(
          _ -> {
            Slab current = Slab.of(view).orElse(slab);
            int extent = current.isOn() ? current.extent() : current.extentOf(Slab.DEFAULT_MM);
            current.apply(type, extent);
          });
      group.add(item);
      menu.add(item);
    }
    menu.addSeparator();
    JMenuItem edit = new JMenuItem(Messages.getString("Slab.edit"));
    edit.addActionListener(
        _ ->
            Slab.of(view)
                .ifPresent(
                    s -> {
                      SlabEditor editor = new SlabEditor(s);
                      editor.popup.show(invoker, x, y);
                      editor.focusField();
                    }));
    menu.add(edit);
    return menu;
  }

  private void showAbove(Component invoker, int x, int y) {
    InfoValueEditor.showAbove(popup, invoker, x, y);
    focusField();
  }

  private void focusField() {
    JFormattedTextField field = textField();
    SwingUtilities.invokeLater(
        () -> {
          field.requestFocusInWindow();
          field.selectAll();
        });
  }

  private void selectMode(Type type) {
    int extent = slab.isOn() ? slab.extent() : slab.extentOf(value());
    slab = slab.apply(type, extent);
    updateEnabled();
    updateHint();
    if (type == Type.NONE) {
      popup.setVisible(false);
    }
  }

  private void applyThickness(double mm) {
    Type type = slab.isOn() ? slab.type() : Type.MAX;
    slab = slab.apply(type, slab.extentOf(mm));
    // Shows the thickness the slab got (whole images or voxels) without applying it again
    updating = true;
    try {
      thickness.setValue(round(slab.millimeters()));
    } finally {
      updating = false;
    }
    updateEnabled();
  }

  private void updateEnabled() {
    boolean on = slab.isOn();
    needSlab.forEach(c -> c.setEnabled(on));
  }

  /** What the typed thickness gives: the extent rounds it to whole images or voxels. */
  private void updateHint() {
    int extent = Math.clamp(slab.extentOf(value()), 1, slab.maxExtent());
    double mm = slab.millimeters(extent);
    String unit = slab.extentUnit();
    hint.setText(
        mm > 0
            ? "= %s mm (%d %s)".formatted(DecFormatter.allNumber(mm), extent * 2 + 1, unit)
            : "");
  }

  private double value() {
    try {
      thickness.commitEdit();
    } catch (ParseException _) {
      // keep the last valid value
    }
    return ((Number) thickness.getValue()).doubleValue();
  }

  private JFormattedTextField textField() {
    return ((JSpinner.DefaultEditor) thickness.getEditor()).getTextField();
  }

  /** One decimal, as the corner text shows the thickness. */
  private static double round(double v) {
    return Math.round(v * 10.0) / 10.0;
  }
}
