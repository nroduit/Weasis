package com.codeminders.demo.ui.dicomstore;

import com.codeminders.demo.GoogleAPIClient;
import com.codeminders.demo.GoogleAuthStub;
import com.codeminders.demo.model.DicomStore;
import com.codeminders.demo.model.StudyModel;
import com.codeminders.demo.model.StudyModel.Value;
import com.codeminders.demo.model.StudyQuery;
import com.codeminders.demo.ui.StudyView;
import com.codeminders.demo.util.NetworkUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URLConnection;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;

import static com.codeminders.demo.util.StringUtils.*;
import static java.util.stream.Collectors.toList;

public class LoadStudiesTask extends AbstractDicomSelectorTask<List<StudyView>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadStudiesTask.class);
    private static final String GOOGLE_API_BASE_PATH = "https://healthcare.googleapis.com/v1alpha/";
    private static final String DICOM_WEB_STUDIES = "/dicomWeb/studies";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FORMAT = new DateTimeFormatterBuilder()
            .appendPattern("HH[:]mm[:]ss")
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .toFormatter();

    private final DicomStore store;
    private final StudyQuery query;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public LoadStudiesTask(DicomStore store,
                           GoogleAPIClient api,
                           DicomStoreSelector view) {
        this(store, api, view, null);
    }

    public LoadStudiesTask(DicomStore store,
                           GoogleAPIClient api,
                           DicomStoreSelector view,
                           StudyQuery studyQuery) {
        super(api, view);
        this.store = store;
        this.query = studyQuery;
    }

    @Override
    protected List<StudyView> doInBackground() throws Exception {
        String fullQuery = GOOGLE_API_BASE_PATH + "projects/" + store.getProject().getId()
                + "/locations/" + store.getLocation().getId()
                + "/datasets/" + store.getParent().getName()
                + "/dicomStores/" + store.getName()
                + DICOM_WEB_STUDIES + formatQuery(query);

        URLConnection connection = GoogleAuthStub.googleApiConnection(fullQuery);

        LOGGER.info("Reading from " + fullQuery);
        try (InputStream stream = NetworkUtils.getUrlInputStream(connection)) {
            List<StudyModel> studies = objectMapper.readValue(stream, new TypeReference<List<StudyModel>>() {});

            return studies.stream().map(this::parse).collect(toList());
        }
    }

    private StudyView parse(StudyModel model) {
        StudyView view = new StudyView();

        if (model.getStudyInstanceUID() != null) {
            view.setStudyId(model.getStudyInstanceUID().getFirstValue().orElse(""));
        }
        if (model.getPatientName() != null) {
            view.setPatientName(model.getPatientName().getFirstValue().map(Value::getAlphabetic).orElse(""));
        }
        if (model.getPatientId() != null) {
            view.setPatientId(model.getPatientId().getFirstValue().orElse(""));
        }
        if (model.getAccessionNumber() != null) {
            view.setAccountNumber(model.getAccessionNumber().getFirstValue().orElse(""));
        }
        if (model.getStudyDate() != null) {
            view.setStudyDate(model.getStudyDate().getFirstValue().map(s -> LocalDate.parse(s, DATE_FORMAT)).orElse(null));
        }
        if (model.getStudyTime() != null) {
            view.setStudyTime(model.getStudyTime().getFirstValue().map(s -> LocalTime.parse(s, TIME_FORMAT)).orElse(null));
        }
        if (model.getStudyDescription() != null) {
            view.setDescription(model.getStudyDescription().getFirstValue().orElse(""));
        }
        if (model.getRefPhd() != null) {
            view.setRefPhd(model.getRefPhd().getFirstValue().map(Value::getAlphabetic).orElse(""));
        }
        if (model.getReqPhd() != null) {
            view.setReqPhd(model.getReqPhd().getFirstValue().map(Value::getAlphabetic).orElse(""));
        }
        if (model.getLocation() != null) {
            view.setLocation(model.getLocation().getFirstValue().map(Value::getAlphabetic).orElse(""));
        }
        if (model.getBirthDate() != null) {
            view.setBirthDate(model.getBirthDate().getFirstValue().map(s -> LocalDate.parse(s, DATE_FORMAT)).orElse(null));
        }

        return view;
    }

    private String formatQuery(StudyQuery query) {
        if (query == null) {
            return "";
        }

        List<String> parameters = new ArrayList<>();
        if (isNotBlank(query.getPatientName())) {
            parameters.add("PatientName=" + urlEncode(query.getPatientName()));
        }

        if (isNotBlank(query.getPatientId())) {
            parameters.add("PatientID=" + urlEncode(query.getPatientId()));
        }

        if (isNotBlank(query.getAccessionNumber())) {
            parameters.add("AccessionNumber=" + urlEncode(query.getAccessionNumber()));
        }

        if (query.getStartDate() != null && query.getEndDate() != null) {
            parameters.add("StudyDate="
                    + urlEncode(DATE_FORMAT.format(query.getStartDate()))
                    + "-" + urlEncode(DATE_FORMAT.format(query.getEndDate()))
            );
        }

        if (isNotBlank(query.getPhysicianName())) {
            parameters.add("ReferringPhysicianName=" + urlEncode(query.getPhysicianName()));
        }

        if (parameters.isEmpty()) {
            return "";
        } else {
            return "?" + join(parameters, "&");
        }
    }

    @Override
    protected void onCompleted(List<StudyView> result) {
        view.updateTable(result);
    }
}
