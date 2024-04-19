/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import java.awt.BorderLayout;
import java.util.Locale;
import javax.swing.JPanel;
import org.weasis.core.api.gui.Insertable;

public abstract class AbstractTabLicense extends JPanel implements Insertable {

  protected final GUIEntry guiEntry;

  public AbstractTabLicense(GUIEntry entry) {
    this.guiEntry = entry;
    setBorder(GuiUtils.getEmptyBorder(10));
    setLayout(new BorderLayout());
  }

  public GUIEntry getGuiEntry() {
    return guiEntry;
  }

  public String getFileName() {
    return guiEntry.getUIName().toLowerCase(Locale.US);
  }

  @Override
  public String getComponentName() {
    return guiEntry.getUIName();
  }

  @Override
  public final Type getType() {
    return Type.LICENSE;
  }

  @Override
  public boolean isComponentEnabled() {
    return true;
  }

  @Override
  public void setComponentEnabled(boolean enabled) {
    // Do nothing
  }

  @Override
  public int getComponentPosition() {
    return 0;
  }

  @Override
  public void setComponentPosition(int position) {
    // Do nothing
  }

  public abstract void closing();
}
