/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.api.gui.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import com.sun.media.jai.codec.ImageCodec;

/**
 * The Class FileFormatFilter.
 * 
 * @author Nicolas Roduit
 */
public class FileFormatFilter extends FileFilter {

    private final Map fExtensions;
    private String fDescription;
    private String fFullDescription;
    private String fDefaultExtension;
    private boolean fUseExtensionsInDescription;
    protected static Map sExtToCodec;
    static {
        // extension alternatives : more than one is separated by comma
        sExtToCodec = new HashMap();
        sExtToCodec.put("jpg,jpe", "jpeg"); //$NON-NLS-1$ //$NON-NLS-2$
        sExtToCodec.put("tif", "tiff"); //$NON-NLS-1$ //$NON-NLS-2$
        sExtToCodec.put("pbm,ppm,pgm", "pnm"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public FileFormatFilter(String extension, String description) {
        fExtensions = new TreeMap();
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

    public FileFormatFilter(String filters[]) {
        this(filters, null);
    }

    public FileFormatFilter(String filters[], String description) {
        fExtensions = new TreeMap();
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
            if (f.isDirectory())
                return true;
            String extension = getExtension(f);
            if (extension != null && fExtensions.get(extension) != null)
                return true;
        }
        return false;
    }

    public String getExtension(File f) {
        if (f != null) {
            String filename = f.getName();
            int i = filename.lastIndexOf(46);
            if (i > 0 && i < filename.length() - 1)
                return filename.substring(i + 1).toLowerCase();
        }
        return null;
    }

    public void addExtension(String extension) {
        extension = extension.replace('*', ' ');
        extension = extension.replace('.', ' ');
        fExtensions.put(extension.trim().toLowerCase(), this);
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
                Collection extensions = fExtensions.keySet();
                Iterator it = extensions.iterator();
                if (it.hasNext()) {
                    fFullDescription += "*." + (String) it.next(); //$NON-NLS-1$
                }
                while (it.hasNext()) {
                    fFullDescription += ", *." + (String) it.next(); //$NON-NLS-1$
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
        // get the current available codecs in jai lib
        Enumeration codecs = ImageCodec.getCodecs();
        ArrayList namesList = new ArrayList(20);
        ImageCodec ic;
        for (; codecs.hasMoreElements(); namesList.add(ic.getFormatName())) {
            ic = (ImageCodec) codecs.nextElement();
        }
        Collections.sort(namesList);
        Iterator it = namesList.iterator();
        String desc = "All supported files"; //$NON-NLS-1$
        Vector names = new Vector();
        do {
            if (!it.hasNext()) {
                break;
            }
            String name = (String) it.next();
            names.addElement(name);
            String altExt = getAlternateExtension(name);
            if (altExt != null) {
                if (altExt.indexOf(",") != -1) { //$NON-NLS-1$
                    String[] tab = altExt.split(","); //$NON-NLS-1$
                    for (int i = 0; i < tab.length; i++) {
                        names.addElement(tab[i]);
                    }
                } else {
                    names.addElement(altExt);
                }
            }
        } while (true);
        String[] list = new String[names.size()];
        for (int i = 0; i < names.size(); i++) {
            list[i] = (String) names.elementAt(i);
        }
        FileFormatFilter allfilter = new FileFormatFilter(list, desc);
        allfilter.setFFullDescription(desc);
        chooser.addChoosableFileFilter(allfilter);
        it = namesList.iterator();
        do {
            if (!it.hasNext()) {
                break;
            }
            String name = (String) it.next();
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
        // ajoute à la fin tous les fichiers
        chooser.setAcceptAllFileFilterUsed(true);
        // filtre par défaut, tous les types d'image
        chooser.setFileFilter(allfilter);
    }

    public static void creatOneFilter(JFileChooser chooser, String name, String desc, boolean allfiles) {
        FileFormatFilter filter = new FileFormatFilter(name, desc);
        chooser.addChoosableFileFilter(filter);
        if (allfiles) {
            // ajoute à la fin tous les fichiers
            chooser.setAcceptAllFileFilterUsed(true);
        }
        chooser.setFileFilter(filter);
    }

    public static synchronized void setTifFilters(JFileChooser chooser, boolean allfiles) {
        String name = "tif"; //$NON-NLS-1$
        String desc = "Tiled TIFF"; //$NON-NLS-1$
        creatOneFilter(chooser, name, desc, allfiles);
    }

    public static synchronized void setJpgFilters(JFileChooser chooser, boolean allfiles) {
        String name = "jpg"; //$NON-NLS-1$
        String desc = "JPEG"; //$NON-NLS-1$
        creatOneFilter(chooser, name, desc, allfiles);
    }

    public static synchronized void setPngFilters(JFileChooser chooser, boolean allfiles) {
        String name = "png"; //$NON-NLS-1$
        String desc = "PNG"; //$NON-NLS-1$
        creatOneFilter(chooser, name, desc, allfiles);
    }

    public static String getAlternateExtension(String codecName) {
        Collection maps = sExtToCodec.entrySet();
        for (Iterator it = maps.iterator(); it.hasNext();) {
            java.util.Map.Entry me = (java.util.Map.Entry) it.next();
            String value = (String) me.getValue();
            if (value.equals(codecName))
                return (String) me.getKey();
        }
        return null;
    }

    public void setFFullDescription(String fFullDescription) {
        this.fFullDescription = fFullDescription;
    }
}
