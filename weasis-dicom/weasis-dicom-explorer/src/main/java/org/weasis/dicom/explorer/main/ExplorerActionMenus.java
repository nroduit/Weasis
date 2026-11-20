/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.main;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.items.ExplorerAction;
import org.weasis.dicom.explorer.items.ExplorerActionProvider;
import org.weasis.dicom.explorer.items.ExplorerActions;
import org.weasis.dicom.explorer.items.ExplorerActions.Group;
import org.weasis.dicom.explorer.items.ExplorerActions.Offer;

/**
 * The menus and buttons of the actions that plugins offer on a study ({@link
 * ExplorerActionProvider}). Every place shows the same thing: under the title of the provider, its
 * actions on the selected series when the selection is a part of the study, then on the whole
 * study, each under a line that says what would be sent.
 */
final class ExplorerActionMenus {

  private ExplorerActionMenus() {}

  /** The line above the actions of a group: the selected series with their count, or the study. */
  static String scopeLabel(Group group) {
    return group.target().wholeStudy()
        ? Messages.getString("ExplorerActions.study")
        : Messages.format(
            "ExplorerActions.selected",
            group.target().series().size(),
            group.target().studySeriesCount());
  }

  static Icon icon(ExplorerActionProvider provider) {
    return provider.getIcon() == null ? null : ResourceUtil.getIcon(provider.getIcon());
  }

  /**
   * The entries of an offer, the selection first; {@code add} receives labels, items, separators.
   */
  private static void fill(Offer offer, Consumer<Component> add) {
    List<Group> groups = new ArrayList<>(2);
    offer.selection().ifPresent(groups::add);
    offer.study().ifPresent(groups::add);
    for (int i = 0; i < groups.size(); i++) {
      Group group = groups.get(i);
      if (i > 0) {
        add.accept(new JSeparator());
      }
      add.accept(GuiUtils.createMenuSectionLabel(scopeLabel(group)));
      for (ExplorerAction action : group.actions()) {
        JMenuItem item = new JMenuItem(action.label());
        item.setName(offer.provider().getId() + "." + action.id()); // NON-NLS
        item.setToolTipText(action.tooltip());
        item.setEnabled(action.enabled());
        item.addActionListener(_ -> action.run().run());
        add.accept(item);
      }
    }
  }

  /** One submenu per provider that has something for the study, after a separator. */
  static void addTo(
      JPopupMenu menu, DicomModel model, MediaSeriesGroup study, List<DicomSeries> selection) {
    List<Offer> offers = ExplorerActions.offers(model, study, selection);
    if (offers.isEmpty()) {
      return;
    }
    if (menu.getComponentCount() > 0) {
      menu.add(new JSeparator());
    }
    for (Offer offer : offers) {
      JMenu sub = new JMenu(offer.provider().getTitle());
      sub.setName(offer.provider().getId());
      sub.setIcon(icon(offer.provider()));
      fill(offer, sub::add);
      menu.add(sub);
    }
  }

  /** Under the button, or above it for a button at the bottom of a panel. */
  private static void show(JPopupMenu menu, JButton button, boolean above) {
    menu.show(button, 0, above ? -menu.getPreferredSize().height : button.getHeight());
  }

  /** The popup of one offer, as a button of the header or of the content panel opens it. */
  static JPopupMenu popup(Offer offer) {
    JPopupMenu menu = new JPopupMenu();
    fill(offer, menu::add);
    return menu;
  }

  /**
   * One button per provider that has something for the study: the icon of the provider (with its
   * title when {@code withTitle}, or when it has no icon), opening its actions on the series
   * selected at that moment and on the study.
   */
  static List<JButton> buttons(
      DicomModel model,
      MediaSeriesGroup study,
      Supplier<List<DicomSeries>> selection,
      boolean withTitle) {
    List<JButton> buttons = new ArrayList<>();
    for (Offer offer : ExplorerActions.offers(model, study, selection.get())) {
      ExplorerActionProvider provider = offer.provider();
      Icon icon = icon(provider);
      JButton button = new JButton(withTitle || icon == null ? provider.getTitle() + " ▾" : null);
      button.setIcon(icon);
      button.setName(provider.getId());
      button.setToolTipText(provider.getTitle());
      button.setFocusable(false);
      if (!withTitle) {
        button.putClientProperty(
            FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
      }
      button.addActionListener(
          _ ->
              // asked again at the click: the selection may have changed since the button was made
              ExplorerActions.offers(model, study, selection.get()).stream()
                  .filter(o -> o.provider().getId().equals(provider.getId()))
                  .findFirst()
                  .ifPresent(o -> show(popup(o), button, withTitle)));
      buttons.add(button);
    }
    return buttons;
  }
}
