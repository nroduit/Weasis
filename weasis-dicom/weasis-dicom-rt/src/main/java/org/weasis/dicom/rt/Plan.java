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
import org.weasis.core.util.StringUtil;

/**
 * @author Tomas Skripcak
 */
public class Plan {

  private String sopInstanceUid;
  private String label;
  private Date date;
  private String name;
  private String description;
  private String geometry;
  private Double rxDose;
  private List<Dose> doses = new ArrayList<>();

  public String getSopInstanceUid() {
    return this.sopInstanceUid;
  }

  public void setSopInstanceUid(String sopInstanceUid) {
    this.sopInstanceUid = sopInstanceUid;
  }

  public String getLabel() {
    return this.label;
  }

  public void setLabel(String value) {
    this.label = value;
  }

  public Date getDate() {
    return this.date;
  }

  public void setDate(Date value) {
    this.date = value;
  }

  public String getName() {
    return this.name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return this.description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getGeometry() {
    return this.geometry;
  }

  public void setGeometry(String geometry) {
    this.geometry = geometry;
  }

  public Double getRxDose() {
    return this.rxDose;
  }

  public void setRxDose(Double rxDose) {
    this.rxDose = rxDose;
  }

  public List<Dose> getDoses() {
    return this.doses;
  }

  public void setDoses(List<Dose> doses) {
    this.doses = doses;
  }

  public boolean hasAssociatedDose() {
    return !this.doses.isEmpty();
  }

  public Dose getFirstDose() {
    if (!this.doses.isEmpty()) {
      return this.doses.get(0);
    }

    return null;
  }

  public void appendName(String text) {
    if (StringUtil.hasText(this.name)) {
      this.name += " (" + text + ")";
    } else if (StringUtil.hasText(this.label)) {
      this.name = this.label + " (" + text + ")";
    }
  }

  @Override
  public String toString() {
    if (StringUtil.hasText(this.name)) {
      return this.name;
    } else if (StringUtil.hasText(this.label)) {
      return this.label;
    }
    return StringUtil.EMPTY_STRING;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((date == null) ? 0 : date.hashCode());
    result = prime * result + ((label == null) ? 0 : label.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (getClass() != obj.getClass()) return false;
    Plan other = (Plan) obj;
    if (date == null) {
      if (other.date != null) return false;
    } else if (!date.equals(other.date)) return false;
    if (label == null) {
      return other.label == null;
    } else return label.equals(other.label);
  }
}
