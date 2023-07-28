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
public class HugLicenseTabFactory implements LicenseTabFactory {

  @Override
  public AbstractTabLicense createInstance(Hashtable<String, Object> properties) {
    GUIEntry hugEntry =
        new GUIEntry() {
          @Override
          public String getUIName() {
            return "HUG";
          }

          @Override
          public String getDescription() {
            return "Key Image Plugin";
          }

          @Override
          public Icon getIcon() {
            return new ImageIcon(HugLicenseTabFactory.this.getClass().getResource("/hug.png"));
          }
        };
    return new AbstractTabLicense(hugEntry);
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
