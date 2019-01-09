package org.weasis.dicom.explorer.google;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Study {

    @JsonProperty("0020000D")
    private Record studyInstanceUID;

    public Record getStudyInstanceUID() {
        return studyInstanceUID;
    }

    public void setStudyInstanceUID(Record studyInstanceUID) {
        this.studyInstanceUID = studyInstanceUID;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Record {
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
}
