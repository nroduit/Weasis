/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.wave;

import java.text.DecimalFormat;

import org.weasis.core.api.util.LocalUtil;

public class MarkerAnnotation {
    public static final DecimalFormat secondFormatter = new DecimalFormat("##.#### s", LocalUtil.getDecimalFormatSymbols()); //$NON-NLS-1$
    public static final DecimalFormat mVFormatter = new DecimalFormat("##.#### mV", LocalUtil.getDecimalFormatSymbols()); //$NON-NLS-1$

    private final Lead lead;

    private Double startSeconds;
    private Double startMiliVolt;
    private Double stopSeconds;
    private Double stopMiliVolt;

    private Double duration;
    private Double diffmV;
    private Double amplitude;

    public MarkerAnnotation(Lead lead) {
        this.lead = lead;
    }

    public void setStartValues(Double seconds, Double miliVolt) {
        this.startSeconds = seconds;
        this.startMiliVolt = miliVolt;
    }

    public void setStopValues(Double seconds, Double miliVolt) {
        this.stopSeconds = seconds;
        this.stopMiliVolt = miliVolt;
    }

    public void setSelectionValues(Double duration, Double diffmV, Double amplitude) {
        this.duration = duration;
        this.diffmV = diffmV;
        this.amplitude = amplitude;
    }

    public Double getStartSeconds() {
        return startSeconds;
    }

    public void setStartSeconds(Double startSeconds) {
        this.startSeconds = startSeconds;
    }

    public Double getStartMiliVolt() {
        return startMiliVolt;
    }

    public void setStartMiliVolt(Double startMiliVolt) {
        this.startMiliVolt = startMiliVolt;
    }

    public Double getStopSeconds() {
        return stopSeconds;
    }

    public void setStopSeconds(Double stopSeconds) {
        this.stopSeconds = stopSeconds;
    }

    public Double getStopMiliVolt() {
        return stopMiliVolt;
    }

    public void setStopMiliVolt(Double stopMiliVolt) {
        this.stopMiliVolt = stopMiliVolt;
    }

    public Double getDuration() {
        return duration;
    }

    public void setDuration(Double duration) {
        this.duration = duration;
    }

    public Double getDiffmV() {
        return diffmV;
    }

    public void setDiffmV(Double diffmV) {
        this.diffmV = diffmV;
    }

    public Double getAmplitude() {
        return amplitude;
    }

    public void setAmplitude(Double amplitude) {
        this.amplitude = amplitude;
    }

    public Lead getLead() {
        return lead;
    }

}
