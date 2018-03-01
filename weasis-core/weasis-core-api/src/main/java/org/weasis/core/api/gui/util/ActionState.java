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
package org.weasis.core.api.gui.util;

import org.weasis.core.api.Messages;

public interface ActionState {

    enum NoneLabel {
        NONE(Messages.getString("ActionState.none")), //$NON-NLS-1$

        NONE_SERIES(Messages.getString("ActionState.none_all")); //$NON-NLS-1$

        private final String title;

        NoneLabel(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    void enableAction(boolean enabled);

    boolean isActionEnabled();

    ActionW getActionW();

    public boolean registerActionState(Object c);

    public void unregisterActionState(Object c);

}
