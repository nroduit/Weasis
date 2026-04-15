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
import java.util.HashMap;
import java.util.Locale;
import java.util.Locale.Category;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.SwingWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.UICore;
import org.weasis.core.api.util.LocalUtil;

public class JLocaleFormat extends JComboBox<JLocale> implements ItemListener, Refreshable {
  private static final Logger LOGGER = LoggerFactory.getLogger(JLocaleFormat.class);

  private final Map<Locale, JLocale> localeMap = new HashMap<>();
  private Locale pendingLocale = null;

  public JLocaleFormat() {
    super();
    // Show the current format locale immediately so the combo is usable at once
    DefaultComboBoxModel<JLocale> placeholder = new DefaultComboBoxModel<>();
    placeholder.addElement(new JLocale(Locale.getDefault(Category.FORMAT)));
    setModel(placeholder);
    addItemListener(this);

    // Build the full ~800-locale list off the EDT to keep startup fast
    new SwingWorker<Object[], Void>() {
      @Override
      protected Object[] doInBackground() {
        return buildSortedData();
      }

      @Override
      protected void done() {
        if (!isCancelled()) {
          try {
            removeItemListener(JLocaleFormat.this);
            applyModel(get());
            addItemListener(JLocaleFormat.this);
            handleLocaleChange(getSelectedItem());
          } catch (ExecutionException | InterruptedException e) {
            LOGGER.error("Failed to load format locales", e);
            Thread.currentThread().interrupt();
          }
        }
      }
    }.execute();
  }

  private static Object[] buildSortedData() {
    Locale[] locales = Locale.getAvailableLocales();
    Locale defaultLocale = Locale.getDefault();

    final Collator collator = Collator.getInstance(defaultLocale);
    // Pre-compute display names once (O(n)) to avoid repeated calls inside the O(n log n) sort
    Map<Locale, String> displayNameCache = new HashMap<>(locales.length * 2);
    for (Locale l : locales) {
      displayNameCache.put(l, l.getDisplayName());
    }
    Arrays.sort(
        locales, (l1, l2) -> collator.compare(displayNameCache.get(l1), displayNameCache.get(l2)));

    Map<Locale, JLocale> map = new HashMap<>(locales.length * 2);
    DefaultComboBoxModel<JLocale> model = new DefaultComboBoxModel<>();
    for (Locale locale : locales) {
      JLocale val = new JLocale(locale);
      map.put(locale, val);
      model.addElement(val);
    }
    return new Object[] {model, map};
  }

  /**
   * Swaps the combo model and locale map in one shot. Must be called on the EDT with the item
   * listener already removed.
   */
  @SuppressWarnings("unchecked")
  private void applyModel(Object[] data) {
    localeMap.clear();
    localeMap.putAll((Map<Locale, JLocale>) data[1]);
    setModel((DefaultComboBoxModel<JLocale>) data[0]);

    // Apply any locale that was requested before the map was ready
    Locale toSelect = pendingLocale != null ? pendingLocale : Locale.getDefault(Category.FORMAT);
    pendingLocale = null;
    JLocale target = localeMap.get(toSelect);
    if (target != null) {
      setSelectedItem(target);
    }
  }

  public void selectLocale() {
    Locale sLoc = Locale.getDefault(Category.FORMAT);
    if (localeMap.isEmpty()) {
      pendingLocale = sLoc; // deferred until the worker finishes
      return;
    }
    Object item = getSelectedItem();
    if (item instanceof JLocale jLocale && sLoc.equals(jLocale.getLocale())) {
      return;
    }

    JLocale target = localeMap.get(sLoc);
    if (target != null) {
      setSelectedItem(target);
    }
  }

  @Override
  public void itemStateChanged(ItemEvent e) {
    if (e.getStateChange() == ItemEvent.SELECTED) {
      handleLocaleChange(e.getItem());
    }
  }

  private void handleLocaleChange(Object item) {
    if (item instanceof JLocale jLocale) {
      storeLocale(jLocale.getLocale());
      valueHasChanged();
    }
  }

  private void storeLocale(Locale locale) {
    GuiUtils.getUICore()
        .getSystemPreferences()
        .setProperty(UICore.P_FORMAT_CODE, LocalUtil.localeToText(locale));
    Locale.setDefault(Locale.Category.FORMAT, locale);
  }

  public void refresh() {
    // Synchronous rebuild – called on user action (language change), acceptable delay
    removeItemListener(this);
    applyModel(buildSortedData());
    selectLocale();
    handleLocaleChange(getSelectedItem());
    addItemListener(this);
  }
}
