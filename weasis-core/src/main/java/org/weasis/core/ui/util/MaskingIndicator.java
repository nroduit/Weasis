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

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Color;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.UIManager;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.MaskingProfile;

/**
 * Permanent badge shown while session masking is on, so that whoever looks at the screen knows the
 * identities are substitutes. Visible only while {@link IdentityMask#isSessionMasking()}; a click
 * offers to turn masking off.
 */
public class MaskingIndicator extends JButton {

  private static final Color FALLBACK_BACKGROUND = new Color(0xF2C55C);

  private final Runnable maskListener = () -> GuiExecutor.execute(this::refresh);

  public MaskingIndicator() {
    putClientProperty(
        FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
    putClientProperty(FlatClientProperties.STYLE_CLASS, "small"); // NON-NLS
    setFocusable(false);
    setToolTipText(Messages.getString("masking.indicator.tip"));
    Color warning = UIManager.getColor("Component.warning.focusedBorderColor"); // NON-NLS
    setBackground(warning == null ? FALLBACK_BACKGROUND : warning);
    setForeground(Color.BLACK);
    addActionListener(_ -> showMenu());
    refresh();
  }

  /** Badge text for the session profile; also used as the suffix of the window title. */
  public static String label(MaskingProfile sessionProfile) {
    String text = Messages.getString("masking.indicator");
    if (sessionProfile == null || MaskingProfile.DISPLAY_ID.equals(sessionProfile.id())) {
      return text;
    }
    return text + " (" + sessionProfile + ")";
  }

  @Override
  public void addNotify() {
    super.addNotify();
    IdentityMask.addChangeListener(maskListener);
    refresh();
  }

  @Override
  public void removeNotify() {
    IdentityMask.removeChangeListener(maskListener);
    super.removeNotify();
  }

  private void refresh() {
    setText(label(IdentityMask.sessionProfile()));
    boolean masking = IdentityMask.isSessionMasking();
    if (isVisible() != masking) {
      setVisible(masking);
      if (getParent() != null) {
        getParent().revalidate();
        getParent().repaint();
      }
    }
  }

  private void showMenu() {
    JPopupMenu menu = new JPopupMenu();
    JMenuItem off = new JMenuItem(Messages.getString("masking.indicator.off"));
    off.addActionListener(_ -> IdentityMask.setSessionMasking(false));
    menu.add(off);
    menu.show(this, 0, getHeight());
  }
}
