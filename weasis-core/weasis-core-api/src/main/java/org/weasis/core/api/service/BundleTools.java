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
package org.weasis.core.api.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.weasis.core.api.gui.util.AbstractProperties;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.util.FileUtil;

public class BundleTools {

    public static final List<Codec> CODEC_PLUGINS = Collections.synchronizedList(new ArrayList<Codec>());
    private static final File propsFile;
    static {
        String user = System.getProperty("weasis.user", null); //$NON-NLS-1$
        if (user == null) {
            propsFile = new File(AbstractProperties.WEASIS_PATH, "weasis.properties"); //$NON-NLS-1$
        } else {
            File dir = new File(AbstractProperties.WEASIS_PATH + File.separator + "preferences" + File.separator //$NON-NLS-1$
                + user);
            try {
                dir.mkdirs();
            } catch (Exception e) {
                dir = new File(AbstractProperties.WEASIS_PATH);
                e.printStackTrace();
            }
            propsFile = new File(dir, "weasis.properties"); //$NON-NLS-1$
        }
    }
    public static final WProperties SYSTEM_PREFERENCES = new WProperties();
    static {
        if (propsFile.canRead()) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(propsFile);
                SYSTEM_PREFERENCES.load(fis);

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                FileUtil.safeClose(fis);
            }
        } else {
            try {
                propsFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static Codec getCodec(String mimeType, String preferredCodec) {
        Codec codec = null;
        synchronized (BundleTools.CODEC_PLUGINS) {
            for (Codec c : BundleTools.CODEC_PLUGINS) {
                if (c.isMimeTypeSupported(mimeType)) {
                    if (c.getCodecName().equals(preferredCodec)) {
                        codec = c;
                        break;
                    }
                    // If the preferred codec cannot be find, the first-found codec is retained
                    if (codec == null) {
                        codec = c;
                    }
                }
            }
            return codec;
        }
    }

    public static void saveSystemPreferences() {
        if (propsFile.canRead()) {
            FileOutputStream fout = null;
            try {
                fout = new FileOutputStream(propsFile);
                SYSTEM_PREFERENCES.store(fout, null);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                FileUtil.safeClose(fout);
            }
        }
    }

}
