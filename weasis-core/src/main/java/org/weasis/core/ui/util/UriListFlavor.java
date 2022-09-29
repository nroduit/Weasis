/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UriListFlavor {
  private static final Logger LOGGER = LoggerFactory.getLogger(UriListFlavor.class);

  public static final DataFlavor flavor = createDataFlavor();

  private static final DataFlavor[] flavors =
      new DataFlavor[] {DataFlavor.javaFileListFlavor, flavor};

  private UriListFlavor() {}

  private static DataFlavor createDataFlavor() {
    try {
      return new DataFlavor(
          "text/uri-list;class=java.lang.String", null, UriListFlavor.class.getClassLoader());
    } catch (Exception e) {
      LOGGER.error("Build uri flavor", e);
      return null;
    }
  }

  public static List<File> textURIListToFileList(String uriList) {
    List<File> list = new java.util.ArrayList<>();
    for (java.util.StringTokenizer st = new java.util.StringTokenizer(uriList, "\r\n");
        st.hasMoreTokens(); ) {
      String s = st.nextToken();
      // Check if the line is a comment (as per the RFC 2483)
      if (!s.startsWith("#")) {
        try {
          list.add(new File(new URI(s)));
        } catch (Exception e) {
          LOGGER.error("Build file from URI", e);
        }
      }
    }
    return list;
  }

  public static DataFlavor[] getTransferDataFlavors() {
    return Arrays.copyOf(flavors, flavors.length);
  }

  public static boolean isDataFlavorSupported(DataFlavor flavor) {
    return Arrays.stream(flavors).anyMatch(flavor::equals);
  }
}
