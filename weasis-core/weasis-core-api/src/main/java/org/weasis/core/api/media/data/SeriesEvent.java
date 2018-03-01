/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.api.media.data;

public class SeriesEvent {

    public enum Action {
        UPDATE, ADD_IMAGE, REMOVE_IMAGE, UPDATE_IMAGE, PRELOADING
    }

    private final Action actionCommand;
    private final Object source;
    private final Object param;

    public SeriesEvent(Action actionCommand, Object source, Object param) {
        this.actionCommand = actionCommand;
        this.source = source;
        this.param = param;
    }

    public Action getActionCommand() {
        return actionCommand;
    }

    public Object getSource() {
        return source;
    }

    public Object getParam() {
        return param;
    }

}
