/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.ui.gui;

import java.util.Hashtable;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.LicenseTabFactory;
import org.weasis.core.api.gui.util.AbstractTabLicense;
import org.weasis.core.api.gui.util.GUIEntry;

@org.osgi.service.component.annotations.Component(service = LicenseTabFactory.class)
public class AnimatiLicenseTabFactory implements LicenseTabFactory {

  @Override
  public AbstractTabLicense createInstance(Hashtable<String, Object> properties) {
    GUIEntry animatiEntry =
        new GUIEntry() {
          @Override
          public String getUIName() {
            return "Animati";
          }

          @Override
          public String getDescription() {
            return "MPR and 3D volume rendering";
          }

          @Override
          public Icon getIcon() {
            return new ImageIcon(
                AnimatiLicenseTabFactory.this.getClass().getResource("/animati.png"));
          }
        };
    return new AbstractTabLicense(animatiEntry);
  }

  @Override
  public void dispose(Insertable component) {}

  @Override
  public boolean isComponentCreatedByThisFactory(Insertable component) {
    return component instanceof AbstractTabLicense;
  }

  @Override
  public Type getType() {
    return Type.LICENSE;
  }
}
