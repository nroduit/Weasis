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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.xml.sax.InputSource;

public class GzipManager {

    public static boolean gzipCompress(InputStream in, String gzipFilename) {
        GZIPOutputStream gzipOut = null;
        try {
            gzipOut = new GZIPOutputStream(new FileOutputStream(gzipFilename));
            byte[] buf = new byte[1024];
            int offset;
            while ((offset = in.read(buf)) > 0) {
                gzipOut.write(buf, 0, offset);
            }

            // Finishes writing compressed data
            gzipOut.finish();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            FileUtil.safeClose(in);
            FileUtil.safeClose(gzipOut);
        }
    }

    public static boolean gzipUncompressToFile(InputStream inputStream, OutputStream out) {
        GZIPInputStream in = null;
        try {
            in = new GZIPInputStream(inputStream);

            byte[] buf = new byte[1024];
            int offset;
            while ((offset = in.read(buf)) > 0) {
                out.write(buf, 0, offset);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            FileUtil.safeClose(in);
            FileUtil.safeClose(out);
        }
    }

    public static boolean gzipUncompressToFile(String inputFile, String outFilename) {
        FileInputStream inputStream;
        FileOutputStream outputStream;
        try {
            inputStream = new FileInputStream(inputFile);
            outputStream = new FileOutputStream(outFilename);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }
        return gzipUncompressToFile(inputStream, outputStream);
    }

    public static boolean gzipUncompressToFile(File inputFile, File outFilename) {
        FileInputStream inputStream;
        FileOutputStream outputStream;
        try {
            inputStream = new FileInputStream(inputFile);
            outputStream = new FileOutputStream(outFilename);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }
        return gzipUncompressToFile(inputStream, outputStream);

    }

    public static boolean gzipUncompressToFile(URL url, File outFilename) {
        InputStream input;
        try {
            input = url.openStream();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        FileOutputStream outputStream;
        try {
            outputStream = new FileOutputStream(outFilename);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }
        return gzipUncompressToFile(input, outputStream);
    }

    public static InputStream gzipUncompressToStream(URL url) {
        try {
            return new BufferedInputStream(new GZIPInputStream(url.openStream()));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static InputSource gzipUncompressToInputSource(URL url) {
        try {
            return new InputSource(new BufferedInputStream((new GZIPInputStream(url.openStream()))));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static InputSource gzipUncompressToInputSource(File file) {
        try {
            return new InputSource(new BufferedInputStream((new GZIPInputStream(new FileInputStream(file)))));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
