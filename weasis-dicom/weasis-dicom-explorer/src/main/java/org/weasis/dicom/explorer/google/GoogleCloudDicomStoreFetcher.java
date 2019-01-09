package org.weasis.dicom.explorer.google;

import com.codeminders.demo.GoogleAuthStub;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.util.NetworkUtil;
import org.weasis.dicom.explorer.wado.LoadRemoteDicomURL;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.List;
import java.util.Optional;

import static org.weasis.dicom.explorer.DicomModel.LOADING_EXECUTOR;

public class GoogleCloudDicomStoreFetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleCloudDicomStoreFetcher.class);

    private static final String GOOGLE_API_BASE_PATH = "https://healthcare.googleapis.com/v1alpha/";
    private static final String DICOM_WEB_STUDIES = "/dicomWeb/studies";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DataExplorerModel dicomExplorer;

    public GoogleCloudDicomStoreFetcher(DataExplorerModel dicomExplorer) {
        this.dicomExplorer = dicomExplorer;
    }

    public void run(String relativePath) {
        LOADING_EXECUTOR.execute(new BackgroundProcess(relativePath));
    }

    private class BackgroundProcess implements Runnable {

        private final String relativePath;

        public BackgroundProcess(String relativePath) {
            this.relativePath = relativePath;
        }

        @Override
        public void run() {
            String fullQuery = GOOGLE_API_BASE_PATH + relativePath + DICOM_WEB_STUDIES;

            try {
                URLConnection connection = GoogleAuthStub.googleApiConnection(fullQuery);

                LOGGER.info("Reading from " + fullQuery);
                try (InputStream stream = NetworkUtil.getUrlInputStream(connection)) {
                    List<Study> studies = objectMapper.readValue(stream, new TypeReference<List<Study>>() {
                    });

                    String[] urls = studies.stream()
                            .map(Study::getStudyInstanceUID)
                            .map(Study.Record::getFirstValue)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .map(id -> GOOGLE_API_BASE_PATH + relativePath + DICOM_WEB_STUDIES + "/" + id)
                            .toArray(String[]::new);

                    LOADING_EXECUTOR.execute(new LoadRemoteDicomURL(urls, dicomExplorer));
                }

            } catch (IOException ex) {
                LOGGER.error("Can't read from google storage", ex);
            }
        }
    }
}
