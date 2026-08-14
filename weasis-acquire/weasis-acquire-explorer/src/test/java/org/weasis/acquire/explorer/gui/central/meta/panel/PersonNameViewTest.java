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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.dcm4che3.data.PersonName.Component;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PersonNameViewTest {

  @Test
  @DisplayName("Splits a person name into its components")
  void splitsComponents() {
    PersonNameView view = new PersonNameView("Smith^John^A^Dr^Jr");
    assertAll(
        () -> assertEquals("Smith", view.getField(Component.FamilyName).getText()),
        () -> assertEquals("John", view.getField(Component.GivenName).getText()),
        () -> assertEquals("A", view.getField(Component.MiddleName).getText()),
        () -> assertEquals("Dr", view.getField(Component.NamePrefix).getText()),
        () -> assertEquals("Jr", view.getField(Component.NameSuffix).getText()));
  }

  @Test
  @DisplayName("Builds a DICOM value without trailing delimiters")
  void buildsDicomValue() {
    PersonNameView view = new PersonNameView(null);
    view.getField(Component.FamilyName).setText("Smith");
    view.getField(Component.GivenName).setText("John");
    assertEquals("Smith^John", view.getDicomPersonName());
  }

  @Test
  @DisplayName("Returns null when no component is filled")
  void returnsNullWhenEmpty() {
    PersonNameView view = new PersonNameView("Smith^John");
    view.getField(Component.FamilyName).setText("");
    view.getField(Component.GivenName).setText("  ");
    assertNull(view.getDicomPersonName());
  }

  @Test
  @DisplayName("Keeps the ideographic and phonetic component groups")
  void keepsOtherGroups() {
    PersonNameView view = new PersonNameView("Yamada^Tarou=山田^太郎=やまだ^たろう");
    view.getField(Component.FamilyName).setText("Yamada");
    assertEquals("Yamada^Tarou=山田^太郎=やまだ^たろう", view.getDicomPersonName());
  }

  @Test
  @DisplayName("Rejects the DICOM delimiters and limits the component length")
  void rejectsDelimiters() {
    PersonNameView view = new PersonNameView(null);
    view.getField(Component.FamilyName).setText("Smith^John=Doe\\Roe");
    view.getField(Component.GivenName).setText("J".repeat(80));
    assertAll(
        () -> assertEquals("SmithJohnDoeRoe", view.getField(Component.FamilyName).getText()),
        () -> assertEquals(64, view.getField(Component.GivenName).getText().length()));
  }
}
