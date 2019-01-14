package org.weasis.dicom.explorer.wado;

import org.slf4j.LoggerFactory;

import org.apache.commons.fileupload.MultipartStream;
import org.weasis.core.api.gui.util.AppProperties;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class GoogleDicomLoader {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(GoogleDicomLoader.class);
    public static final File DICOM_TMP_DIR = AppProperties.buildAccessibleTempDirectory("gcp_cache"); //$NON-NLS-1$


    public static File[] downloadFiles(String dicomUrl, String googleToken, boolean useCached) {

        try {
            AppProperties.buildAccessibleTempDirectory("downloading");
            HttpURLConnection httpConn = (HttpURLConnection) new URL(dicomUrl).openConnection();
            httpConn.setRequestProperty("Authorization", "Bearer " + googleToken);
            int responseCode = httpConn.getResponseCode();
            LOGGER.info("responseCode={}", responseCode);
            String contentType = httpConn.getContentType();

            LOGGER.info("contentType={}", contentType);
            int index = contentType.indexOf("boundary=");

            String boundary = contentType.substring(index + 10, contentType.length() - 1);
            LOGGER.info("boundary={}", boundary);

            MultipartStream multipart = new MultipartStream(httpConn.getInputStream(), boundary.getBytes());
            boolean nextPart = multipart.skipPreamble();

            ArrayList<File> files = new ArrayList<>();
            long start = System.currentTimeMillis();
            while (nextPart) {
                File outFile = File.createTempFile("gcp_", ".dcm", getDicomTmpDir()); //$NON-NLS-1$ //$NON-NLS-2$
                files.add(outFile);
                String header = multipart.readHeaders();
                LOGGER.info(header);// process headers

                OutputStream output = new FileOutputStream(outFile);
                multipart.readBodyData(output);
                output.close();
                nextPart = multipart.readBoundary();
            }
            LOGGER.info("Elapsed time: {} ", System.currentTimeMillis() - start);
            return files.stream().toArray(File[]::new);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    // Solves missing tmp folder problem (on Windows).
    private static File getDicomTmpDir() {
        if (!DICOM_TMP_DIR.exists()) {
            LOGGER.info("DICOM tmp dir not found. Re-creating it."); //$NON-NLS-1$
            AppProperties.buildAccessibleTempDirectory("gcp_cache"); //$NON-NLS-1$
        }
        return DICOM_TMP_DIR;
    }
}
