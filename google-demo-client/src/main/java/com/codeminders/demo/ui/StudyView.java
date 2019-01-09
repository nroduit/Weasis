package com.codeminders.demo.ui;

import java.time.LocalDate;
import java.time.LocalTime;

public class StudyView {

    private String studyId;
    private String patientName;
    private String patientId;
    private String accountNumber;
    private String noi;
    private LocalDate studyDate;
    private LocalTime studyTime;
    private String type;
    private String description;
    private String refPhd;
    private String reqPhd;
    private String location;
    private LocalDate birthDate;

    public String getStudyId() {
        return studyId;
    }

    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getNoi() {
        return noi;
    }

    public void setNoi(String noi) {
        this.noi = noi;
    }

    public LocalDate getStudyDate() {
        return studyDate;
    }

    public void setStudyDate(LocalDate studyDate) {
        this.studyDate = studyDate;
    }

    public LocalTime getStudyTime() {
        return studyTime;
    }

    public void setStudyTime(LocalTime studyTime) {
        this.studyTime = studyTime;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRefPhd() {
        return refPhd;
    }

    public void setRefPhd(String refPhd) {
        this.refPhd = refPhd;
    }

    public String getReqPhd() {
        return reqPhd;
    }

    public void setReqPhd(String reqPhd) {
        this.reqPhd = reqPhd;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }
}
