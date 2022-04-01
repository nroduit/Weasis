/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import org.weasis.core.api.Messages;
import org.weasis.core.api.service.BundleTools;

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
    for (String filter : filters) {
      addExtension(filter);
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
      return extension != null && fExtensions.get(extension) != null;
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
        StringBuilder builder = new StringBuilder(fDescription != null ? fDescription + " (" : "(");
        Set<String> extensions = fExtensions.keySet();
        Iterator<String> it = extensions.iterator();
        if (it.hasNext()) {
          builder.append("*.");
          builder.append(it.next());
        }

        while (it.hasNext()) {
          builder.append(", *.");
          builder.append(it.next());
        }
        builder.append(")");
        fFullDescription = builder.toString();
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
    // Get the current available codecs.
    List<String> namesList =
        BundleTools.CODEC_PLUGINS.stream()
            .flatMap(c -> Arrays.stream(c.getReaderExtensions()))
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    // Remove DICOM extensions
    namesList.removeAll(Arrays.asList("dcm", "dic", "dicm", "dicom")); // NON-NLS
    Iterator<String> it = namesList.iterator();
    String desc = Messages.getString("FileFormatFilter.all_supported");
    ArrayList<String> names = new ArrayList<>();
    while (it.hasNext()) {
      String name = it.next();
      names.add(name);
    }

    FileFormatFilter allfilter = new FileFormatFilter(names.toArray(new String[0]), desc);
    allfilter.setFFullDescription(desc);
    chooser.addChoosableFileFilter(allfilter);
    it = namesList.iterator();
    while (it.hasNext()) {
      String name = it.next();
      desc = name.toUpperCase();
      FileFormatFilter filter = new FileFormatFilter(name, desc);
      chooser.addChoosableFileFilter(filter);
    }
    // Add All filter
    chooser.setAcceptAllFileFilterUsed(true);
    // Set default selected filter
    chooser.setFileFilter(allfilter);
  }

  public void setFFullDescription(String fFullDescription) {
    this.fFullDescription = fFullDescription;
  }
}
