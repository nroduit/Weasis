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

public class SignalMarker {
    public enum Type {
        START, STOP, ANY
    }

    public enum Measure {
        VERTICAL, HORIZONTAL, MULTIPLE
    }

    private Measure tool;
    private Type type;
    private int position;

    public SignalMarker(Measure tool, Type type, int position) {
        this.tool = tool;
        this.type = type;
        this.position = position;
    }

    public Measure getTool() {
        return tool;
    }

    public void setTool(Measure tool) {
        this.tool = tool;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public int getPostion() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
