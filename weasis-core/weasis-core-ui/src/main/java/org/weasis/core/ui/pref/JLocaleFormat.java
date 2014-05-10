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

import java.text.Collator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

import javax.swing.JComboBox;

import org.weasis.core.api.util.LocalUtil;

public class JLocaleFormat extends JComboBox {

    public JLocaleFormat() {
        super();
        sortLocales();
    }

    private void sortLocales() {
        Locale[] locales = Locale.getAvailableLocales();

        Locale defaultLocale = Locale.getDefault();
        // Allow to sort correctly string in each language
        final Collator collator = Collator.getInstance(defaultLocale);
        Arrays.sort(locales, new Comparator<Locale>() {

            @Override
            public int compare(Locale l1, Locale l2) {
                return collator.compare(l1.getDisplayName(), l2.getDisplayName());
            }
        });

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

    public void selectLocale() {
        Locale sLoc = LocalUtil.getLocaleFormat();
        Object item = getSelectedItem();
        if (item instanceof JLocale) {
            if (sLoc.equals(((JLocale) item).getLocale())) {
                return;
            }
        }

        for (int i = 0; i < getItemCount(); i++) {
            Locale l = ((JLocale) getItemAt(i)).getLocale();
            if (l.equals(sLoc)) {
                setSelectedIndex(i);
                break;
            }
        }
    }

}
