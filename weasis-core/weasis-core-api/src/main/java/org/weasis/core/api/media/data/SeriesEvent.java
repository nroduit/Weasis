/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.api.media.data;

public class SeriesEvent {

    public enum Action {
        Add, Remove, Update, AddImage, RemoveImage, UpdateImage, loadImageInMemory
    };

    private final Action actionCommand;
    private final Object source;
    private final Object param;

    public SeriesEvent(Action actionCommand, Object source, Object param) {
        super();
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
