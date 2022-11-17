/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.pref;

import java.awt.GridLayout;
import java.util.List;
import javax.swing.JPanel;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.PageItem;
import org.weasis.core.ui.Messages;

public class ViewerPrefView extends AbstractItemDialogPage {

  private final JPanel menuPanel = new JPanel();

  public ViewerPrefView(PreferenceDialog dialog) {
    super(Messages.getString("viewer"), 500);

    menuPanel.setLayout(new GridLayout(0, 2));
    add(menuPanel);
    add(GuiUtils.boxVerticalStrut(BLOCK_SEPARATOR));

    add(GuiUtils.boxYLastElement(LAST_FILLER_HEIGHT));

    List<AbstractItemDialogPage> childPages = List.of(new LabelsPrefView());
    childPages.forEach(p -> addSubPage(p, a -> dialog.showPage(p.getTitle()), menuPanel));
  }

  @Override
  public JPanel getMenuPanel() {
    return menuPanel;
  }

  @Override
  public void closeAdditionalWindow() {
    for (PageItem subpage : getSubPages()) {
      subpage.closeAdditionalWindow();
    }
  }

  @Override
  public void resetToDefaultValues() {
    // Do nothing
  }
}
