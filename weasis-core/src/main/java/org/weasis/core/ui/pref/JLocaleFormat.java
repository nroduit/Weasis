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

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.text.Collator;
import java.util.Arrays;
import java.util.Locale;
import javax.swing.JComboBox;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.LocalUtil;

public class JLocaleFormat extends JComboBox<JLocale> implements ItemListener, Refreshable {

  public JLocaleFormat() {
    super();
    sortLocales();
    addItemListener(this);
  }

  private void sortLocales() {
    Locale[] locales = Locale.getAvailableLocales();

    Locale defaultLocale = Locale.getDefault();
    // Allow sorting correctly string in each language
    final Collator collator = Collator.getInstance(defaultLocale);
    Arrays.sort(locales, (l1, l2) -> collator.compare(l1.getDisplayName(), l2.getDisplayName()));

    JLocale dloc = null;
    for (Locale locale : locales) {
      JLocale val = new JLocale(locale);
      if (val.getLocale().equals(defaultLocale)) {
        dloc = val;
      }
      addItem(val);
    }
    this.setSelectedItem(dloc);
  }

  public void selectLocale() {
    Locale sLoc = LocalUtil.getLocaleFormat();
    Object item = getSelectedItem();
    if (item instanceof JLocale jLocale && sLoc.equals(jLocale.getLocale())) {
      return;
    }

    for (int i = 0; i < getItemCount(); i++) {
      Locale l = getItemAt(i).getLocale();
      if (l.equals(sLoc)) {
        setSelectedIndex(i);
        break;
      }
    }
  }

  @Override
  public void itemStateChanged(ItemEvent e) {
    if (e.getStateChange() == ItemEvent.SELECTED) {
      Object item = e.getItem();
      if (item instanceof JLocale jLocale) {
        setLocalUtil(jLocale.getLocale());
        valueHasChanged();
      }
    }
  }

  private void setLocalUtil(Locale local) {
    if (local == null) {
      BundleTools.SYSTEM_PREFERENCES.remove(BundleTools.P_FORMAT_CODE);
    } else {
      BundleTools.SYSTEM_PREFERENCES.setProperty(
          BundleTools.P_FORMAT_CODE, LocalUtil.localeToText(local));
    }

    Locale l = local == null ? null : local.equals(Locale.getDefault()) ? null : local;
    LocalUtil.setLocaleFormat(l);
  }

  public void refresh() {
    removeItemListener(this);
    removeAllItems();
    sortLocales();
    selectLocale();
    valueHasChanged();
    addItemListener(this);
  }
}
