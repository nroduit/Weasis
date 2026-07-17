/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.ui.FlatLineBorder;
import com.formdev.flatlaf.ui.FlatUIUtils;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.Objects;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import org.weasis.core.api.util.FontItem;

/**
 * A titled section whose body can be folded away by clicking its header. Designed to stack several
 * sections in a vertical {@link javax.swing.BoxLayout} tool (e.g. inside a {@link
 * javax.swing.JScrollPane}); collapsing a section frees its vertical space.
 *
 * <p>When a non-null {@code persistenceKey} is supplied, the expanded/collapsed state is saved to
 * the local persistence store and restored on the next session, overriding the default state.
 */
public class CollapsiblePanel extends JPanel {

  private final JComponent content;
  private final JButton header;
  private final String persistenceKey;
  private boolean expanded;

  public CollapsiblePanel(String title, JComponent content, boolean defaultExpanded) {
    this(title, content, defaultExpanded, null);
  }

  public CollapsiblePanel(
      String title, JComponent content, boolean defaultExpanded, String persistenceKey) {
    super(new BorderLayout());
    this.content = Objects.requireNonNull(content);
    this.persistenceKey = persistenceKey;
    this.expanded = restoreState(defaultExpanded);

    header = new JButton(title);
    header.setFont(FontItem.DEFAULT_SEMIBOLD.getFont());
    header.setHorizontalAlignment(SwingConstants.LEADING);
    header.setHorizontalTextPosition(SwingConstants.TRAILING);
    header.setIconTextGap(GuiUtils.getScaleLength(6));
    header.setFocusable(false);
    // Borderless, full-width disclosure header that blends with the tool background but still shows
    // a hover highlight to signal it is clickable.
    header.putClientProperty(
        FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
    header.addActionListener(e -> setExpanded(!expanded));

    setAlignmentX(Component.LEFT_ALIGNMENT);
    setBorder(buildSectionBorder());
    add(header, BorderLayout.NORTH);
    add(content, BorderLayout.CENTER);

    applyState();
  }

  /**
   * A rounded, theme-coloured card border around the whole section (header + body) with an outer
   * gap so stacked sections are visually separated. Callers may override with {@link #setBorder}
   * for a different look.
   */
  private static Border buildSectionBorder() {
    Color line = FlatUIUtils.getUIColor("Component.borderColor", Color.GRAY); // NON-NLS
    Border card = new FlatLineBorder(new Insets(2, 2, 2, 2), line, 1f, 8);
    return new CompoundBorder(GuiUtils.getEmptyBorder(6, 3, 2, 3), card);
  }

  private boolean restoreState(boolean defaultExpanded) {
    if (persistenceKey == null) {
      return defaultExpanded;
    }
    return GuiUtils.getUICore()
        .getLocalPersistence()
        .getBooleanProperty(persistenceKey, defaultExpanded);
  }

  @Override
  public Dimension getMaximumSize() {
    // Stretch full width but keep natural height, so the section does not grab vertical space in a
    // BoxLayout (Y_AXIS) tool.
    Dimension dim = getPreferredSize();
    dim.width = Integer.MAX_VALUE;
    return dim;
  }

  public boolean isExpanded() {
    return expanded;
  }

  public JComponent getContent() {
    return content;
  }

  public void setExpanded(boolean expand) {
    if (expand == expanded) {
      return;
    }
    expanded = expand;
    if (persistenceKey != null) {
      GuiUtils.getUICore().getLocalPersistence().putBooleanProperty(persistenceKey, expand);
    }
    applyState();
    revalidate();
    repaint();
  }

  private void applyState() {
    content.setVisible(expanded);
    header.setIcon(disclosureIcon(expanded));
  }

  private static Icon disclosureIcon(boolean expanded) {
    return UIManager.getIcon(expanded ? "Tree.expandedIcon" : "Tree.collapsedIcon"); // NON-NLS
  }
}
