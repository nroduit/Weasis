/*******************************************************************************
 * Copyright (c) 2011 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.ui.pref;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

import javax.swing.JComboBox;

import org.weasis.core.api.service.BundleTools;

public class JLocaleCombo extends JComboBox implements ItemListener {

    private static final long serialVersionUID = -5355456986860584918L;

    public JLocaleCombo() {
        super();
        sortLocales();
        addItemListener(this);
    }

    private void sortLocales() {
        Locale[] locales = Locale.getAvailableLocales();
        Arrays.sort(locales, new Comparator<Locale>() {

            @Override
            public int compare(Locale l1, Locale l2) {
                return l1.getDisplayName().compareTo(l2.getDisplayName());
            }
        });
        Locale defaultLocale = Locale.getDefault();
        JLocale dloc = null;
        for (int i = 0; i < locales.length; i++) {
            if (locales[i].getCountry().length() > 0) {
                JLocale val = new JLocale(locales[i]);
                if (val.getLocale().equals(defaultLocale)) {
                    dloc = val;
                }
                addItem(val);
            }
        }
        if (dloc != null) {
            this.setSelectedItem(dloc);
        }
    }

    public void selectLocale(String language, String country, String variant) {
        Object item = getSelectedItem();
        if (item instanceof JLocale) {
            Locale l = ((JLocale) item).getLocale();
            if (l.getLanguage().equals(language) && l.getCountry().equals(country) && l.getVariant().equals(variant)) {
                return;
            }
        }

        for (int i = 0; i < getItemCount(); i++) {
            Locale l = ((JLocale) getItemAt(i)).getLocale();
            if (l.getLanguage().equals(language) && l.getCountry().equals(country) && l.getVariant().equals(variant)) {
                setSelectedIndex(i);
                break;
            }
        }

    }

    @Override
    public void itemStateChanged(ItemEvent iEvt) {
        if (iEvt.getStateChange() == ItemEvent.SELECTED) {
            Object item = getSelectedItem();
            if (item instanceof JLocale) {
                Locale locale = ((JLocale) item).getLocale();
                Locale.setDefault(locale);
                firePropertyChange("locale", null, locale); //$NON-NLS-1$
                BundleTools.SYSTEM_PREFERENCES.put("locale.language", locale.getLanguage()); //$NON-NLS-1$
                BundleTools.SYSTEM_PREFERENCES.put("locale.country", locale.getCountry()); //$NON-NLS-1$
                BundleTools.SYSTEM_PREFERENCES.put("locale.variant", locale.getVariant()); //$NON-NLS-1$
                removeItemListener(this);
                removeAllItems();
                sortLocales();
                addItemListener(this);
            }
        }
    }

    static class JLocale {
        private final Locale locale;

        JLocale(Locale l) {
            if (l == null) {
                throw new IllegalArgumentException("locale cannot be null"); //$NON-NLS-1$
            }
            locale = l;
        }

        @Override
        public String toString() {
            return locale.getDisplayName();
        }

        public Locale getLocale() {
            return locale;
        }
    }
}
