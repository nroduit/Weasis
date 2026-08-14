/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.gui.central.meta.panel;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import net.miginfocom.swing.MigLayout;
import org.dcm4che3.data.PersonName;
import org.dcm4che3.data.PersonName.Component;
import org.dcm4che3.data.PersonName.Group;
import org.weasis.acquire.explorer.Messages;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.util.StringUtil;

/**
 * Editor of a DICOM person name (VR PN) splitting the value into its five components: family name,
 * given name, middle name, prefix and suffix.
 */
public class PersonNameView extends JPanel {

  /** Maximum length of a component group as defined by the DICOM standard. */
  private static final int MAX_GROUP_CHARS = 64;

  private final Map<Component, JTextField> fields = new EnumMap<>(Component.class);
  private final PersonName personName;
  private final JLabel dicomValue = new JLabel();
  private final Color defaultColor;

  public PersonNameView(String value) {
    this.personName = new PersonName(value, true);
    this.defaultColor = dicomValue.getForeground();
    setLayout(new MigLayout("wrap 2, insets 0", "[right][grow,fill]")); // NON-NLS

    addComponentField(Component.FamilyName, Messages.getString("person.name.family"));
    addComponentField(Component.GivenName, Messages.getString("person.name.given"));
    addComponentField(Component.MiddleName, Messages.getString("person.name.middle"));
    addComponentField(Component.NamePrefix, Messages.getString("person.name.prefix"));
    addComponentField(Component.NameSuffix, Messages.getString("person.name.suffix"));

    dicomValue.setFont(FontItem.SMALL.getFont());
    add(
        new JLabel(Messages.getString("person.name.value") + StringUtil.COLON),
        "gaptop 10"); // NON-NLS
    add(dicomValue, "gaptop 10"); // NON-NLS
    updateDicomValue();
  }

  /** DICOM encoded value, or null when all the components are empty. */
  public String getDicomPersonName() {
    fields.forEach((c, field) -> personName.set(Group.Alphabetic, c, field.getText()));
    return personName.isEmpty() ? null : personName.toString();
  }

  JTextField getField(Component component) {
    return fields.get(component);
  }

  private void addComponentField(Component component, String label) {
    JTextField field = new JTextField(personName.get(Group.Alphabetic, component));
    ((AbstractDocument) field.getDocument()).setDocumentFilter(new PersonNameFilter());
    field
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(DocumentEvent e) {
                updateDicomValue();
              }

              @Override
              public void removeUpdate(DocumentEvent e) {
                updateDicomValue();
              }

              @Override
              public void changedUpdate(DocumentEvent e) {
                updateDicomValue();
              }
            });
    GuiUtils.setPreferredWidth(field, 250);
    fields.put(component, field);
    add(new JLabel(label + StringUtil.COLON));
    add(field);
  }

  private void updateDicomValue() {
    PersonName preview = new PersonName();
    fields.forEach((c, field) -> preview.set(Group.Alphabetic, c, field.getText()));
    String value = preview.toString(Group.Alphabetic, true);
    boolean tooLong = value.length() > MAX_GROUP_CHARS;
    dicomValue.setText(value);
    dicomValue.setForeground(tooLong ? IconColor.ACTIONS_RED.getColor() : defaultColor);
    dicomValue.setToolTipText(
        tooLong
            ? String.format(Messages.getString("person.name.too.long"), MAX_GROUP_CHARS)
            : null);
  }

  /** Rejects the DICOM delimiters and limits the length of a single component. */
  private static class PersonNameFilter extends DocumentFilter {

    @Override
    public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr)
        throws BadLocationException {
      replace(fb, offset, 0, text, attr);
    }

    @Override
    public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attr)
        throws BadLocationException {
      String val = text == null ? StringUtil.EMPTY_STRING : text.replaceAll("[\\^=\\\\]", "");
      int free = MAX_GROUP_CHARS - (fb.getDocument().getLength() - length);
      if (val.length() > free) {
        val = val.substring(0, Math.max(free, 0));
      }
      super.replace(fb, offset, length, val, attr);
    }
  }
}
