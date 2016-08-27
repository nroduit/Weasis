/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.ui.editor.image;

import java.util.HashMap;
import java.util.Objects;

import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.util.Copyable;

public class SynchData implements Copyable<SynchData> {

    public enum Mode {
        None, Stack, Tile
    }

    protected final HashMap<String, Boolean> actions;
    protected final Mode mode;

    private boolean original;

    public SynchData(Mode mode, HashMap<String, Boolean> actions) {
        if (actions == null) {
            throw new IllegalArgumentException("A parameter is null!"); //$NON-NLS-1$
        }
        this.actions = actions;
        this.mode = mode;
        this.original = true;
    }

    public SynchData(SynchData synchData) {
        Objects.requireNonNull(synchData);
        this.actions = new HashMap<>(synchData.actions);
        this.mode = synchData.mode;
        this.original = synchData.original;
    }

    public HashMap<String, Boolean> getActions() {
        return actions;
    }

    public boolean isActionEnable(String action) {
        return JMVUtils.getNULLtoFalse(actions.get(action));
    }

    public Mode getMode() {
        return mode;
    }

    @Override
    public SynchData copy() {
        return new SynchData(this);
    }

    public boolean isOriginal() {
        return original;
    }

    public void setOriginal(boolean original) {
        this.original = original;
    }

}