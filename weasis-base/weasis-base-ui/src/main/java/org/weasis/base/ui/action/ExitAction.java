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
package org.weasis.base.ui.action;

import java.awt.event.ActionEvent;

import org.weasis.base.ui.Messages;
import org.weasis.base.ui.gui.WeasisWin;
import org.weasis.core.ui.util.AbstractUIAction;

public class ExitAction extends AbstractUIAction {

    /** The singleton instance of this singleton class. */
    private static ExitAction exitAction = null;

    /** Return the singleton instance */
    public static ExitAction getInstance() {
        if (exitAction == null) {
            exitAction = new ExitAction();
        }
        return exitAction;
    }

    private ExitAction() {
        super(Messages.getString("ExitAction.title")); //$NON-NLS-1$
        setDescription(Messages.getString("ExitAction.description")); //$NON-NLS-1$
    }

    public void actionPerformed(ActionEvent e) {
        WeasisWin.getInstance().closeWindow();
    }
}
