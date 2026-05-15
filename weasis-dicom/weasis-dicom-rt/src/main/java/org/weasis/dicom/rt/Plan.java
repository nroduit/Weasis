/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.rt;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomMediaIO;

/**
 * @author Tomas Skripcak
 */
public class Plan extends RtSpecialElement {
  private String sopInstanceUid;
  private Date date;
  private String name;
  private String description;
  private String geometry;
  private Double rxDose;
  private final List<Dose> doses = new ArrayList<>();

  public Plan(DicomMediaIO mediaIO) {
    super(mediaIO);
  }

  public String getSopInstanceUid() {
    return sopInstanceUid;
  }

  public void setSopInstanceUid(String sopInstanceUid) {
    this.sopInstanceUid = sopInstanceUid;
  }

  public void setLabel(String value) {
    this.label = value;
  }

  public Date getDate() {
    return date == null ? null : new Date(date.getTime());
  }

  public void setDate(Date value) {
    this.date = value == null ? null : new Date(value.getTime());
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getGeometry() {
    return geometry;
  }

  public void setGeometry(String geometry) {
    this.geometry = geometry;
  }

  public Double getRxDose() {
    return rxDose;
  }

  public void setRxDose(Double rxDose) {
    this.rxDose = rxDose;
  }

  public List<Dose> getDoses() {
    return doses;
  }

  public Dose getFirstDose() {
    return doses.isEmpty() ? null : doses.getFirst();
  }

  public void appendName(String text) {
    this.name = StringUtil.hasText(this.name) ? this.name + " - " + text : text;
  }

  @Override
  public String toString() {
    if (!StringUtil.hasText(label)) {
      return StringUtil.hasText(name) ? name : TagW.NO_VALUE;
    }
    return (StringUtil.hasText(name) && !name.equals(label)) ? label + " (" + name + ")" : label;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), date, label);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Plan other) || !super.equals(obj)) return false;
    return Objects.equals(date, other.date) && Objects.equals(label, other.label);
  }
}
