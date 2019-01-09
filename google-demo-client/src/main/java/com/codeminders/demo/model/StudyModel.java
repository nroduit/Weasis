package com.codeminders.demo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StudyModel {

    @JsonProperty("0020000D")
    private RecordPlain studyInstanceUID;

    @JsonProperty("00100010")
    private RecordObjects patientName;

    @JsonProperty("00100020")
    private RecordPlain patientId;

    @JsonProperty("00080050")
    private RecordPlain accessionNumber;

    @JsonProperty("00080020")
    private RecordPlain studyDate;

    @JsonProperty("00080030")
    private RecordPlain studyTime;

    @JsonProperty("00081030")
    private RecordPlain studyDescription;

    @JsonProperty("00321032")
    private RecordObjects reqPhd;

    @JsonProperty("00080090")
    private RecordObjects refPhd;

    @JsonProperty("00200050")
    private RecordObjects location;

    @JsonProperty("00100030")
    private RecordPlain birthDate;

    public RecordPlain getStudyInstanceUID() {
        return studyInstanceUID;
    }

    public void setStudyInstanceUID(RecordPlain studyInstanceUID) {
        this.studyInstanceUID = studyInstanceUID;
    }

    public RecordObjects getPatientName() {
        return patientName;
    }

    public void setPatientName(RecordObjects patientName) {
        this.patientName = patientName;
    }

    public RecordPlain getPatientId() {
        return patientId;
    }

    public void setPatientId(RecordPlain patientId) {
        this.patientId = patientId;
    }

    public RecordPlain getAccessionNumber() {
        return accessionNumber;
    }

    public void setAccessionNumber(RecordPlain accessionNumber) {
        this.accessionNumber = accessionNumber;
    }

    public RecordPlain getStudyDate() {
        return studyDate;
    }

    public void setStudyDate(RecordPlain studyDate) {
        this.studyDate = studyDate;
    }

    public RecordPlain getStudyTime() {
        return studyTime;
    }

    public void setStudyTime(RecordPlain studyTime) {
        this.studyTime = studyTime;
    }

    public RecordPlain getStudyDescription() {
        return studyDescription;
    }

    public void setStudyDescription(RecordPlain studyDescription) {
        this.studyDescription = studyDescription;
    }

    public RecordObjects getReqPhd() {
        return reqPhd;
    }

    public void setReqPhd(RecordObjects reqPhd) {
        this.reqPhd = reqPhd;
    }

    public RecordObjects getRefPhd() {
        return refPhd;
    }

    public void setRefPhd(RecordObjects refPhd) {
        this.refPhd = refPhd;
    }

    public RecordObjects getLocation() {
        return location;
    }

    public void setLocation(RecordObjects location) {
        this.location = location;
    }

    public RecordPlain getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(RecordPlain birthDate) {
        this.birthDate = birthDate;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RecordPlain {
        private String vr;

        @JsonProperty("Value")
        private List<String> value;

        public String getVr() {
            return vr;
        }

        public void setVr(String vr) {
            this.vr = vr;
        }

        public List<String> getValue() {
            return value;
        }

        public void setValue(List<String> value) {
            this.value = value;
        }

        public Optional<String> getFirstValue() {
            if (value == null || value.size() == 0) {
                return Optional.empty();
            } else {
                return Optional.ofNullable(value.get(0));
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RecordObjects {
        private String vr;

        @JsonProperty("Value")
        private List<Value> value;

        public String getVr() {
            return vr;
        }

        public void setVr(String vr) {
            this.vr = vr;
        }

        public List<Value> getValue() {
            return value;
        }

        public void setValue(List<Value> value) {
            this.value = value;
        }

        public Optional<Value> getFirstValue() {
            if (value == null || value.size() == 0) {
                return Optional.empty();
            } else {
                return Optional.ofNullable(value.get(0));
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Value {

        @JsonProperty("Alphabetic")
        private String alphabetic;

        @JsonProperty("Ideographic")
        private String ideographic;

        @JsonProperty("Phonetic")
        private String phonetic;

        public String getAlphabetic() {
            return alphabetic;
        }

        public void setAlphabetic(String alphabetic) {
            this.alphabetic = alphabetic;
        }

        public String getIdeographic() {
            return ideographic;
        }

        public void setIdeographic(String ideographic) {
            this.ideographic = ideographic;
        }

        public String getPhonetic() {
            return phonetic;
        }

        public void setPhonetic(String phonetic) {
            this.phonetic = phonetic;
        }
    }
}
