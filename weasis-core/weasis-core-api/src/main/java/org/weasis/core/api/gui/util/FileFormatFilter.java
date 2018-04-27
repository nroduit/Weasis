/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.api.gui.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import org.weasis.core.api.Messages;

/**
 * The Class FileFormatFilter.
 *
 * @author Nicolas Roduit
 */
public class FileFormatFilter extends FileFilter {

    private final Map<String, FileFormatFilter> fExtensions;
    private String fDescription;
    private String fFullDescription;
    private String fDefaultExtension;
    private boolean fUseExtensionsInDescription;
    protected static Map<String, String> sExtToCodec;

    static {
        // extension alternatives : more than one is separated by comma
        sExtToCodec = new HashMap<>();
        sExtToCodec.put("jpg,jpe", "jpeg"); //$NON-NLS-1$ //$NON-NLS-2$
        sExtToCodec.put("tif", "tiff"); //$NON-NLS-1$ //$NON-NLS-2$
        sExtToCodec.put("pbm,ppm,pgm", "pnm"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public FileFormatFilter(String extension, String description) {
        fExtensions = new TreeMap<>();
        fDescription = null;
        fFullDescription = null;
        fDefaultExtension = null;
        fUseExtensionsInDescription = true;
        if (extension != null) {
            addExtension(extension);
        }
        if (description != null) {
            setDescription(description);
        }
    }

    public FileFormatFilter(String[] filters) {
        this(filters, null);
    }

    public FileFormatFilter(String[] filters, String description) {
        fExtensions = new TreeMap<>();
        fDescription = null;
        fFullDescription = null;
        fDefaultExtension = null;
        fUseExtensionsInDescription = true;
        for (int i = 0; i < filters.length; i++) {
            addExtension(filters[i]);
        }
        if (description != null) {
            setDescription(description);
        }
    }

    public String getDefaultExtension() {
        return fDefaultExtension;
    }

    @Override
    public boolean accept(File f) {
        if (f != null) {
            if (f.isDirectory()) {
                return true;
            }
            String extension = getExtension(f);
            if (extension != null && fExtensions.get(extension) != null) {
                return true;
            }
        }
        return false;
    }

    public String getExtension(File f) {
        if (f != null) {
            String filename = f.getName();
            int i = filename.lastIndexOf(46);
            if (i > 0 && i < filename.length() - 1) {
                return filename.substring(i + 1).toLowerCase();
            }
        }
        return null;
    }

    public void addExtension(String extension) {
        fExtensions.put(extension.replace('*', ' ').replace('.', ' ').trim().toLowerCase(), this);
        if (fDefaultExtension == null) {
            fDefaultExtension = extension;
        }
        fFullDescription = null;
    }

    @Override
    public String getDescription() {
        if (fFullDescription == null) {
            if (fDescription == null || isExtensionListInDescription()) {
                fFullDescription = fDescription != null ? fDescription + " (" : "("; //$NON-NLS-1$ //$NON-NLS-2$
                Set<String> extensions = fExtensions.keySet();
                Iterator<String> it = extensions.iterator();
                if (it.hasNext()) {
                    fFullDescription += "*." + it.next(); //$NON-NLS-1$
                }
                while (it.hasNext()) {
                    fFullDescription += ", *." + it.next(); //$NON-NLS-1$
                }
                fFullDescription += ")"; //$NON-NLS-1$
            } else {
                fFullDescription = fDescription;
            }
        }
        return fFullDescription;
    }

    public void setDescription(String description) {
        fDescription = description;
        fFullDescription = null;
    }

    public void setExtensionListInDescription(boolean b) {
        fUseExtensionsInDescription = b;
        fFullDescription = null;
    }

    public boolean isExtensionListInDescription() {
        return fUseExtensionsInDescription;
    }

    public static void setImageDecodeFilters(JFileChooser chooser) {
        // Get the current available codecs from ImageIO.
        List<String> namesList = Arrays.asList(ImageIO.getReaderFormatNames());
        Collections.sort(namesList);
        Iterator<String> it = namesList.iterator();
        String desc = Messages.getString("FileFormatFilter.all_supported"); //$NON-NLS-1$
        ArrayList<String> names = new ArrayList<>();
        do {
            if (!it.hasNext()) {
                break;
            }
            String name = it.next();
            names.add(name);
            String altExt = getAlternateExtension(name);
            if (altExt != null) {
                if (altExt.indexOf(",") != -1) { //$NON-NLS-1$
                    String[] tab = altExt.split(","); //$NON-NLS-1$
                    for (int i = 0; i < tab.length; i++) {
                        names.add(tab[i]);
                    }
                } else {
                    names.add(altExt);
                }
            }
        } while (true);

        FileFormatFilter allfilter = new FileFormatFilter(names.toArray(new String[names.size()]), desc);
        allfilter.setFFullDescription(desc);
        chooser.addChoosableFileFilter(allfilter);
        it = namesList.iterator();
        do {
            if (!it.hasNext()) {
                break;
            }
            String name = it.next();
            desc = name.toUpperCase();
            FileFormatFilter filter = new FileFormatFilter(name, desc);
            String altExt = getAlternateExtension(name);
            if (altExt != null) {
                if (altExt.indexOf(",") != -1) { //$NON-NLS-1$
                    String[] tab = altExt.split(","); //$NON-NLS-1$
                    for (int i = 0; i < tab.length; i++) {
                        filter.addExtension(tab[i]);
                    }
                } else {
                    filter.addExtension(altExt);
                }
            }
            chooser.addChoosableFileFilter(filter);
        } while (true);
        // Add All filter
        chooser.setAcceptAllFileFilterUsed(true);
        // Set default selected filter
        chooser.setFileFilter(allfilter);
    }

    public static String getAlternateExtension(String codecName) {
        Set<Entry<String, String>> maps = sExtToCodec.entrySet();
        for (Iterator<Entry<String, String>> it = maps.iterator(); it.hasNext();) {
            Entry<String, String> me = it.next();
            String value = me.getValue();
            if (value.equals(codecName)) {
                return me.getKey();
            }
        }
        return null;
    }

    public void setFFullDescription(String fFullDescription) {
        this.fFullDescription = fFullDescription;
    }
}
