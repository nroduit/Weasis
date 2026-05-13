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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.core.util.StringUtil;

public class JLocaleLanguage extends JComboBox<JLocale> implements ItemListener, Refreshable {

  private final ArrayList<JLocalePercentage> languages = new ArrayList<>();
  private final Map<Locale, JLocalePercentage> localeMap = new HashMap<>();
  private JLocalePercentage englishEntry = null;

  public JLocaleLanguage() {
    super();
    initLocalesWithPercentages();
    sortLocales();
  }

  private void initLocalesWithPercentages() {
    Map<String, Integer> percentages = new HashMap<>();
    // Load percentages if available
    String percentageData = System.getProperty("weasis.languages.percentage", null);
    if (StringUtil.hasText(percentageData)) {
      String[] items = percentageData.split(",");
      for (String item : items) {
        String entry = item.trim();
        int index = entry.indexOf('.');
        if (index > 0) {
          String langCode = entry.substring(0, index);
          try {
            int percentage = Integer.parseInt(entry.substring(index + 1));
            if (percentage > 30) { // Add only if percentage > 30
              percentages.put(langCode, percentage);
            }
          } catch (NumberFormatException e) {
            // Ignore invalid percentages
          }
        }
      }
    }

    // Load locales
    String languageList = System.getProperty("weasis.languages", null);
    if (languageList != null) {
      String[] items = languageList.split(",");
      for (String s : items) {
        String item = s.trim();
        int index = item.indexOf(' ');
        String langCode = index > 0 ? item.substring(0, index) : item;
        Locale locale = LocalUtil.textToLocale(langCode);
        if (locale != null && percentages.getOrDefault(langCode, 0) > 30) {
          JLocalePercentage entry = new JLocalePercentage(locale, percentages.get(langCode));
          languages.add(entry);
          localeMap.put(locale, entry);
          if (Locale.ENGLISH.equals(locale)) {
            englishEntry = entry;
          }
        }
      }
    }
    if (languages.isEmpty()) {
      JLocalePercentage defaultEntry = new JLocalePercentage(Locale.ENGLISH, 100);
      languages.add(defaultEntry);
      localeMap.put(Locale.ENGLISH, defaultEntry);
      englishEntry = defaultEntry;
    }
  }

  private void sortLocales() {
    Locale defaultLocale = Locale.getDefault();
    // Allow sorting correctly for each language
    final Collator collator = Collator.getInstance(defaultLocale);
    languages.sort(
        (l1, l2) ->
            collator.compare(l1.getLocale().getDisplayName(), l2.getLocale().getDisplayName()));

    // Swap the model in one shot to avoid firing an event per addItem() call
    DefaultComboBoxModel<JLocale> model = new DefaultComboBoxModel<>();
    JLocalePercentage defaultLocaleEntry = null;
    for (JLocalePercentage localePercentage : languages) {
      if (localePercentage.getLocale().equals(defaultLocale)) {
        defaultLocaleEntry = localePercentage;
      }
      model.addElement(localePercentage);
    }
    setModel(model);
    if (defaultLocaleEntry != null) {
      setSelectedItem(defaultLocaleEntry);
    }
  }

  public void selectLocale(String localeCode) {
    Locale selectedLocale = LocalUtil.textToLocale(localeCode);

    // Fast early-exit: already showing the right locale
    Object item = getSelectedItem();
    if (item instanceof JLocalePercentage jLocalePercentage
        && selectedLocale != null
        && selectedLocale.equals(jLocalePercentage.getLocale())) {
      return;
    }

    JLocalePercentage target = selectedLocale != null ? localeMap.get(selectedLocale) : null;
    if (target == null) {
      target = englishEntry; // fall back to English
    }
    if (target != null) {
      setSelectedItem(target);
    }
  }

  @Override
  public void itemStateChanged(ItemEvent iEvt) {
    if (iEvt.getStateChange() == ItemEvent.SELECTED) {
      Object item = getSelectedItem();
      if (item instanceof JLocalePercentage jLocalePercentage) {
        removeItemListener(this);
        Locale locale = jLocalePercentage.getLocale();
        Locale.setDefault(locale);
        GuiUtils.getUICore()
            .getSystemPreferences()
            .setProperty("locale.lang.code", LocalUtil.localeToText(locale));
        // Re-sort display names for the new locale and swap the model in one shot
        sortLocales();
        addItemListener(this);
        firePropertyChange("locale", null, locale);
        valueHasChanged();
      }
    }
  }
}
