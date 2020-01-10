/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
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

    public static final DataFlavor flavor = createConstant("text/uri-list;class=java.lang.String", null); //$NON-NLS-1$

    private static final DataFlavor[] flavors = new DataFlavor[] { DataFlavor.javaFileListFlavor, flavor };

    private UriListFlavor() {
    }

    private static DataFlavor createConstant(String mt, String prn) {
        try {
            return new DataFlavor(mt, prn, UriListFlavor.class.getClassLoader()); // $NON-NLS-1$
        } catch (Exception e) {
            LOGGER.error("Build uri flavor", e); //$NON-NLS-1$
            return null;
        }
    }

    public static List<File> textURIListToFileList(String uriList) {
        List<File> list = new java.util.ArrayList<>();
        for (java.util.StringTokenizer st = new java.util.StringTokenizer(uriList, "\r\n"); st.hasMoreTokens();) { //$NON-NLS-1$
            String s = st.nextToken();
            // Check if the line is a comment (as per the RFC 2483)
            if (!s.startsWith("#")) { //$NON-NLS-1$
                try {
                    list.add(new File(new URI(s)));
                } catch (Exception e) {
                    LOGGER.error("Build file from URI", e); //$NON-NLS-1$
                }
            }
        }
        return list;
    }

    public static DataFlavor[] getTransferDataFlavors() {
        return Arrays.copyOf(flavors, flavors.length);
    }

    public static boolean isDataFlavorSupported(DataFlavor flavor) {
        return Arrays.asList(flavors).stream().anyMatch(flavor::equals);
    }
}
