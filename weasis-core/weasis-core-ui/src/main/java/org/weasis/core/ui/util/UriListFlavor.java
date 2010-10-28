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
package org.weasis.core.ui.util;

import java.awt.datatransfer.DataFlavor;
import java.io.BufferedReader;
import java.io.File;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.weasis.core.ui.Messages;

public class UriListFlavor {

    public static DataFlavor uriListFlavor;
    static {
        try {
            uriListFlavor = new DataFlavor("text/uri-list;class=java.lang.String"); //$NON-NLS-1$
        } catch (ClassNotFoundException e) {
            // do nothing
        }
    }

    public static DataFlavor[] FLAVORS = new DataFlavor[] { DataFlavor.javaFileListFlavor, uriListFlavor };

    public static List<File> getFileList(Reader reader) {
        if (reader == null) {
            return null;
        }
        List<File> list = new java.util.ArrayList<File>();
        BufferedReader br = new BufferedReader(reader);
        String uriStr;
        try {
            while ((uriStr = br.readLine()) != null) {
                if (uriStr.startsWith("#")) { //$NON-NLS-1$
                    // the line is a comment (as per the RFC 2483)
                    continue;
                }
                try {
                    list.add(new File(new URI(uriStr)));
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public static List<File> textURIListToFileList(String uriList) {
        List<File> list = new java.util.ArrayList<File>();
        for (java.util.StringTokenizer st = new java.util.StringTokenizer(uriList, "\r\n"); st.hasMoreTokens();) { //$NON-NLS-1$
            String s = st.nextToken();
            if (s.startsWith("#")) { //$NON-NLS-1$
                // the line is a comment (as per the RFC 2483)
                continue;
            }
            try {
                list.add(new File(new URI(s)));
            } catch (Exception e) {
            }
        }
        return list;
    }

    public static DataFlavor[] getTransferDataFlavors() {
        return FLAVORS.clone();
    }

    public static boolean isDataFlavorSupported(DataFlavor flavor) {
        for (int i = 0; i < FLAVORS.length; i++) {
            if (flavor.equals(FLAVORS[i])) {
                return true;
            }
        }
        return false;
    }
}
