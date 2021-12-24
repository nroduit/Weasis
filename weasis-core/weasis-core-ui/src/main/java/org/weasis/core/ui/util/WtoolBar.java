/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import org.weasis.core.api.gui.Insertable;

@SuppressWarnings("serial")
public class WtoolBar extends JPanel implements Toolbar {

  public static final Dimension SEPARATOR_2x24 = new Dimension(2, 24);

  private final String barName;

  private int barPosition = 100;
  private Insertable attachedInsertable;

  public WtoolBar(String barName, int position) {
    FlowLayout flowLayout = (FlowLayout) getLayout();
    flowLayout.setVgap(0);
    flowLayout.setHgap(0);
    flowLayout.setAlignment(FlowLayout.LEADING);
    this.barName = barName;
    this.barPosition = position;
    this.setAlignmentX(LEFT_ALIGNMENT);
    this.setAlignmentY(TOP_ALIGNMENT);
    setOpaque(false);
    addSeparator(SEPARATOR_2x24);
  }

  @Override
  public Type getType() {
    return Type.TOOLBAR;
  }

  @Override
  public int getComponentPosition() {
    return barPosition;
  }

  @Override
  public void setComponentPosition(int position) {
    this.barPosition = position;
  }

  public Insertable getAttachedInsertable() {
    return attachedInsertable;
  }

  public void setAttachedInsertable(Insertable attachedInsertable) {
    this.attachedInsertable = attachedInsertable;
  }

  public void addSeparator(Dimension dim) {
    JSeparator s = new JSeparator(SwingConstants.VERTICAL);
    s.setPreferredSize(dim);
    add(s);
  }

  /** Overridden to track AbstractButton added */
  @Override
  public Component add(Component comp) {
    if (comp instanceof AbstractButton) {
      return add((AbstractButton) comp);
    } else {
      return super.add(comp);
    }
  }

  /** Adds a new button to this toolbar */
  public Component add(AbstractButton button) {
    super.add(button);
    configureButton(button);
    return button;
  }

  /** Adds a new button to this toolbar */
  public Component add(JButton button) {
    // this method is here to maintain backward compatibility
    return add((AbstractButton) button);
  }

  /**
   * Install custom UI for this button : a light rollover effet and a custom rounded/shaded border.
   *
   * <p>This method can be overridden to replace the provided "look and feel" which uses the
   * follwing configuration :
   *
   * <ul>
   *   <li>install a VLButtonUI
   *   <li>set 2 pixels margins
   *   <li>set a ToolBarButtonBorder.
   * </ul>
   */
  public static void installButtonUI(AbstractButton button) {
    button.setMargin(new Insets(2, 2, 2, 2));
    button.setUI(new RolloverButtonUI());
    button.setBorder(new ToolBarButtonBorder());
  }

  /**
   * This method is invoked upon adding a button to the toolbar. It can be overridden to provide
   * another look or feel.
   *
   * <p>Default settings are :
   *
   * <ul>
   *   <li>setRolloverEnabled(true)
   *   <li>setContentAreaFilled(false);
   *   <li>setOpaque(false)
   *   <li>setBorderPainted(false)
   * </ul>
   */
  public static void configureButton(AbstractButton button) {
    button.setRolloverEnabled(true);
    button.setContentAreaFilled(false);
    button.setOpaque(false);
    button.setBorderPainted(false);
  }

  @Override
  public String toString() {
    return "WtoolBar " + getName(); // NON-NLS
  }

  @Override
  public String getComponentName() {
    return barName;
  }

  @Override
  public final WtoolBar getComponent() {
    return this;
  }

  @Override
  public boolean isComponentEnabled() {
    return isEnabled();
  }

  @Override
  public void setComponentEnabled(boolean enabled) {
    if (isComponentEnabled() != enabled) {
      setEnabled(enabled);
    }
  }
}
