/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.model;

public class PerformanceModel {

  private String type;
  private String seriesUID;
  private String modality;
  private int nbImages;
  private long size;
  private long time;
  private String rate;
  private int errors;

  public PerformanceModel(
      String type,
      String seriesUID,
      String modality,
      int nbImages,
      long size,
      long time,
      String rate,
      int errors) {

    this.type = type;
    this.seriesUID = seriesUID;
    this.modality = modality;
    this.nbImages = nbImages;
    this.size = size;
    this.time = time;
    this.rate = rate;
    this.errors = errors;
  }

  public int getErrors() {
    return errors;
  }

  public void setErrors(int errors) {
    this.errors = errors;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getSeriesUID() {
    return seriesUID;
  }

  public void setSeriesUID(String seriesUID) {
    this.seriesUID = seriesUID;
  }

  public String getModality() {
    return modality;
  }

  public void setModality(String modality) {
    this.modality = modality;
  }

  public int getNbImages() {
    return nbImages;
  }

  public void setNbImages(int nbImages) {
    this.nbImages = nbImages;
  }

  public long getSize() {
    return size;
  }

  public void setSize(long size) {
    this.size = size;
  }

  public long getTime() {
    return time;
  }

  public void setTime(long time) {
    this.time = time;
  }

  public String getRate() {
    return rate;
  }

  public void setRate(String rate) {
    this.rate = rate;
  }
}
