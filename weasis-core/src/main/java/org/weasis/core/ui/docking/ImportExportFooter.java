/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.docking;

import java.awt.BorderLayout;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.weasis.core.api.gui.FooterPanelProvider;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.UICore;
import org.weasis.core.api.setting.CoreSettings;
import org.weasis.core.api.setting.Settings;

/**
 * Bottom-left footer: one toggle button per {@link FooterPanelProvider} (DICOM import, export, …)
 * stacked under the left button stripe. Expanding a provider shows its panel across the full window
 * width, in a vertical split under the viewing area; the split height is persisted.
 */
public class ImportExportFooter extends JPanel {

  private static final int DEFAULT_HEIGHT = 350;

  private final JPanel panelHost = new JPanel(new BorderLayout());
  private final Map<FooterPanelProvider, JToggleButton> toggles = new LinkedHashMap<>();

  private JSplitPane verticalSplit;
  private int dividerSize;
  private FooterPanelProvider currentProvider;
  private JComponent currentPanel;

  public ImportExportFooter() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

    List<FooterPanelProvider> providers =
        UICore.getInstance().getPluginManager().getExtensions(FooterPanelProvider.class).stream()
            .sorted(Comparator.comparingInt(FooterPanelProvider::getPosition))
            .toList();
    for (FooterPanelProvider provider : providers) {
      JToggleButton button = new JToggleButton(provider.getIcon());
      button.setToolTipText(provider.getTitle());
      button.setFocusable(false);
      button.setAlignmentX(CENTER_ALIGNMENT);
      button.setMaximumSize(button.getPreferredSize());
      button.addActionListener(e -> toggle(provider));
      toggles.put(provider, button);
      add(button);
    }
    setVisible(!providers.isEmpty());
  }

  /** The bottom child of the vertical split; hidden while no footer panel is expanded. */
  public JPanel getPanelHost() {
    return panelHost;
  }

  /** Attach the vertical split (explorer above, footer panel below) this footer controls. */
  public void attachTo(JSplitPane split) {
    this.verticalSplit = split;
    this.dividerSize = split.getDividerSize();
    split.setDividerSize(0);
    panelHost.setVisible(false);
  }

  private void toggle(FooterPanelProvider provider) {
    if (provider == currentProvider) {
      collapse();
    } else {
      expand(provider);
    }
  }

  private void expand(FooterPanelProvider provider) {
    collapse();
    currentProvider = provider;
    currentPanel = provider.createPanel(this::collapse);
    panelHost.removeAll();
    panelHost.add(currentPanel, BorderLayout.CENTER);
    panelHost.setVisible(true);
    verticalSplit.setDividerSize(dividerSize);
    int height =
        Settings.get(CoreSettings.FOOTER_HEIGHT, () -> GuiUtils.getScaleLength(DEFAULT_HEIGHT));
    SwingUtilities.invokeLater(
        () ->
            verticalSplit.setDividerLocation(
                Math.max(0, verticalSplit.getHeight() - height - dividerSize)));
    updateToggles();
    panelHost.revalidate();
    panelHost.repaint();
  }

  /** Collapses the expanded footer panel, persisting its height. */
  public void collapse() {
    if (currentProvider != null) {
      if (panelHost.getHeight() > 0) {
        Settings.set(CoreSettings.FOOTER_HEIGHT, panelHost.getHeight());
      }
      currentProvider.onCollapse(currentPanel);
      panelHost.removeAll();
      currentProvider = null;
      currentPanel = null;
    }
    panelHost.setVisible(false);
    if (verticalSplit != null) {
      verticalSplit.setDividerSize(0);
      verticalSplit.revalidate();
    }
    updateToggles();
  }

  private void updateToggles() {
    toggles.forEach((provider, button) -> button.setSelected(provider == currentProvider));
  }
}
