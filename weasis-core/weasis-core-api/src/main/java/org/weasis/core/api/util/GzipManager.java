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
package org.weasis.core.api.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GzipManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(GzipManager.class);

    private static final String ERROR_CTX = "Cannot gzip uncompress"; //$NON-NLS-1$

    private GzipManager() {
    }

    public static boolean gzipCompress(InputStream in, String gzipFilename) {
        try (FileOutputStream inputStream = new FileOutputStream(gzipFilename);
                        GZIPOutputStream gzipOut = new GZIPOutputStream(inputStream)) {
            byte[] buf = new byte[1024];
            int offset;
            while ((offset = in.read(buf)) > 0) {
                gzipOut.write(buf, 0, offset);
            }

            // Finishes writing compressed data
            gzipOut.finish();
            return true;
        } catch (IOException e) {
            LOGGER.error("Cannot gzip compress", e); //$NON-NLS-1$
            return false;
        } finally {
            FileUtil.safeClose(in);
        }
    }

    private static boolean gzipUncompressToFile(InputStream inputStream, OutputStream out) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(inputStream)) {
            byte[] buf = new byte[1024];
            int offset;
            while ((offset = in.read(buf)) > 0) {
                out.write(buf, 0, offset);
            }
            return true;
        }
    }

    public static boolean gzipUncompressToFile(File inputFile, File outFilename) {
        try (FileInputStream inputStream = new FileInputStream(inputFile);
                        FileOutputStream outputStream = new FileOutputStream(outFilename)) {
            return gzipUncompressToFile(inputStream, outputStream);
        } catch (IOException e) {
            LOGGER.error(ERROR_CTX, e);
            return false;
        }
    }

    public static boolean gzipUncompressToFile(URL url, File outFilename) {
        try (InputStream input = url.openStream(); FileOutputStream outputStream = new FileOutputStream(outFilename)) {
            return gzipUncompressToFile(input, outputStream);
        } catch (IOException e) {
            LOGGER.error(ERROR_CTX, e);
            return false;
        }
    }

}
