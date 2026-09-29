/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.pref;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import javax.swing.AbstractAction;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.codec.display.WindowPreset;
import org.weasis.dicom.ref.BodyPart;
import org.weasis.dicom.ref.RegionGroup;
import org.weasis.dicom.viewer2d.Messages;

/**
 * Button next to a field of DICOM codes, opening a filterable check list of the known codes. Codes
 * typed in the field that are not in the list are kept.
 */
final class CodePicker {

  /** A code of the list and its meaning. */
  record Code(String value, String meaning) {
    boolean matches(String filter) {
      return filter.isEmpty()
          || value.toLowerCase(Locale.ROOT).contains(filter)
          || meaning.toLowerCase(Locale.ROOT).contains(filter);
    }
  }

  // Modalities producing images a window applies to
  private static final List<String> IMAGE_MODALITIES =
      List.of(
          "CR", "CT", "DX", "ES", "IO", "IVOCT", "MG", "MR", "NM", "OCT", "OP", "OPT", "OT", "PT",
          "PX", "RF", "RG", "SM", "US", "XA", "XC"); // NON-NLS

  private final JTextField field;
  private final Runnable onChange;
  private final List<Code> codes;
  private final DefaultListModel<Code> model = new DefaultListModel<>();
  private final JList<Code> list = new JList<>(model);
  private final JTextField filter = new JTextField();
  private final JPopupMenu popup = new JPopupMenu();
  private final JButton button = new JButton("\u22EF"); // NON-NLS

  private CodePicker(JTextField field, List<Code> codes, Runnable onChange) {
    this.field = field;
    this.codes = codes;
    this.onChange = onChange;

    filter.putClientProperty(
        FlatClientProperties.PLACEHOLDER_TEXT, Messages.getString("WindowPresetPrefView.filter"));
    filter.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
    filter.getDocument().addDocumentListener(onEdit(this::refilter));

    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setVisibleRowCount(12);
    list.setCellRenderer(new CheckRenderer());
    list.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            int index = list.locationToIndex(e.getPoint());
            if (index >= 0 && list.getCellBounds(index, index).contains(e.getPoint())) {
              toggle(model.get(index));
            }
          }
        });
    list.getInputMap(JComponent.WHEN_FOCUSED)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "toggle"); // NON-NLS
    list.getActionMap()
        .put(
            "toggle", // NON-NLS
            new AbstractAction() {
              @Override
              public void actionPerformed(java.awt.event.ActionEvent e) {
                Code code = list.getSelectedValue();
                if (code != null) {
                  toggle(code);
                }
              }
            });

    JPanel content = new JPanel(new BorderLayout(0, GuiUtils.getScaleLength(4)));
    content.setBorder(GuiUtils.getEmptyBorder(5));
    content.add(filter, BorderLayout.NORTH);
    JScrollPane scroll = new JScrollPane(list);
    GuiUtils.setPreferredWidth(scroll, 320);
    content.add(scroll, BorderLayout.CENTER);
    popup.add(content);

    button.setToolTipText(Messages.getString("WindowPresetPrefView.pick"));
    button.addActionListener(e -> open());
  }

  /** The picker button of a modality field. */
  static JButton modalities(JTextField field, Runnable onChange) {
    List<Code> codes =
        IMAGE_MODALITIES.stream()
            .map(Modality::getModality)
            .filter(m -> m != Modality.DEFAULT)
            .map(m -> new Code(m.name(), m.getDescription()))
            .toList();
    return new CodePicker(field, codes, onChange).button;
  }

  /**
   * The picker button of an anatomy field: the region groups first (LOINC/RSNA Playbook regions),
   * then the common Body Part Examined terms (PS3.16 Table L-1).
   */
  static JButton bodyParts(JTextField field, Runnable onChange) {
    String group = Messages.getString("WindowPresetPrefView.group");
    Stream<Code> groups =
        Arrays.stream(RegionGroup.values())
            .map(g -> new Code(g.name(), group + " \u2014 " + label(g)));
    Stream<Code> terms =
        Arrays.stream(BodyPart.values())
            .filter(b -> b.isCommon() && StringUtil.hasText(b.getLegacyCode()))
            .map(b -> new Code(b.getLegacyCode(), b.getCodeMeaning()))
            .sorted((a, b) -> a.value().compareTo(b.value()));
    return new CodePicker(field, Stream.concat(groups, terms).toList(), onChange).button;
  }

  // UPPER_EXTREMITY -> Upper extremity
  private static String label(RegionGroup group) {
    String text = group.name().replace('_', ' ').toLowerCase(Locale.ROOT);
    return Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }

  private void open() {
    filter.setText("");
    refilter();
    popup.show(button, 0, button.getHeight());
    SwingUtilities.invokeLater(filter::requestFocusInWindow);
  }

  private void refilter() {
    String text = filter.getText().trim().toLowerCase(Locale.ROOT);
    model.clear();
    codes.stream().filter(c -> c.matches(text)).forEach(model::addElement);
  }

  private void toggle(Code code) {
    Set<String> values = new TreeSet<>(WindowPreset.parseCodes(field.getText()));
    if (!values.remove(code.value())) {
      values.add(code.value());
    }
    field.setText(String.join(", ", values));
    list.repaint();
    onChange.run();
  }

  static DocumentListener onEdit(Runnable action) {
    return new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        action.run();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        action.run();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        action.run();
      }
    };
  }

  private final class CheckRenderer implements ListCellRenderer<Code> {
    private final JCheckBox box = new JCheckBox();

    @Override
    public Component getListCellRendererComponent(
        JList<? extends Code> l, Code code, int index, boolean selected, boolean focus) {
      box.setText(code.value() + " — " + code.meaning()); // NON-NLS
      box.setSelected(WindowPreset.parseCodes(field.getText()).contains(code.value()));
      box.setBackground(selected ? l.getSelectionBackground() : l.getBackground());
      box.setForeground(selected ? l.getSelectionForeground() : l.getForeground());
      box.setOpaque(true);
      return box;
    }
  }
}
