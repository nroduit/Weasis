/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
import org.weasis.core.util.FileUtil;

public class GzipManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(GzipManager.class);

    private static final String ERROR_CTX = "Cannot gzip uncompress"; //$NON-NLS-1$

    private GzipManager() {
    }

    public static boolean gzipCompress(InputStream in, String gzipFilename) {
        try (FileOutputStream out = new FileOutputStream(gzipFilename)) {
            return gzipCompress(in, out);
        } catch (IOException e) {
            LOGGER.error("Cannot gzip compress", e); //$NON-NLS-1$
            return false;
        } finally {
            FileUtil.safeClose(in);
        }
    }

    private static boolean gzipCompress(InputStream in, OutputStream out) throws IOException {
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(out)) {
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

    private static boolean gzipUncompress(InputStream inputStream, OutputStream out) throws IOException {
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
            return gzipUncompress(inputStream, outputStream);
        } catch (IOException e) {
            LOGGER.error(ERROR_CTX, e);
            return false;
        }
    }

    public static boolean gzipUncompressToFile(URL url, File outFilename) {
        try (InputStream input = url.openStream(); FileOutputStream outputStream = new FileOutputStream(outFilename)) {
            return gzipUncompress(input, outputStream);
        } catch (IOException e) {
            LOGGER.error(ERROR_CTX, e);
            return false;
        }
    }

    public static byte[] gzipUncompressToByte(byte[] bytes) throws IOException {
        if (isGzip(bytes)) {
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                            ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);) {
                gzipUncompress(inputStream, outputStream);
                return outputStream.toByteArray();
            }
        } else {
            return bytes;
        }
    }

    public static byte[] gzipCompressToByte(byte[] bytes) throws IOException {
        return gzipCompressToByte(bytes, 1);
    }

    /**
     * @param bytes
     * @param requiredByteNumber
     *            for applying gzip. On network the safe value is 1400 (as MTU is 1500)
     * @return
     * @throws IOException
     */
    public static byte[] gzipCompressToByte(byte[] bytes, int requiredByteNumber) throws IOException {
        if (bytes.length >= requiredByteNumber) {
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                            ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);) {
                gzipCompress(inputStream, outputStream);
                return outputStream.toByteArray();
            }
        }
        return bytes;
    }

    public static boolean isGzip(byte[] bytes) {
        // Check to see if it's gzip-compressed
        // GZIP Magic Two-Byte Number: 0x8b1f (35615)
        if (bytes != null && bytes.length >= 4) {
            int head = (bytes[0] & 0xff) | ((bytes[1] << 8) & 0xff00);
            if (GZIPInputStream.GZIP_MAGIC == head) {
                return true;
            }
        }
        return false;
    }

    public static boolean gzipUncompressToFile(byte[] bytes, File outFilename) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
            if (isGzip(bytes)) {
                try (FileOutputStream outputStream = new FileOutputStream(outFilename)) {
                    return gzipUncompress(inputStream, outputStream);
                }
            }
            return FileUtil.writeStream(inputStream, outFilename) == -1;
        } catch (IOException e) {
            LOGGER.error(ERROR_CTX, e);
            return false;
        }
    }
}
