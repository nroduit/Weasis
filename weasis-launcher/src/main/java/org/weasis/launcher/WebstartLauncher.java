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
package org.weasis.launcher;

import java.util.List;

import javax.jnlp.ServiceManager;
import javax.jnlp.SingleInstanceListener;
import javax.jnlp.SingleInstanceService;
import javax.jnlp.UnavailableServiceException;

public class WebstartLauncher extends WeasisLauncher implements SingleInstanceListener {

    private static final WebstartLauncher instance = new WebstartLauncher();
    static {
        try {
            SingleInstanceService singleInstanceService =
                (SingleInstanceService) ServiceManager.lookup("javax.jnlp.SingleInstanceService"); //$NON-NLS-1$
            singleInstanceService.addSingleInstanceListener(instance);
        } catch (UnavailableServiceException use) {
        }
    }

    public static void main(String[] argv) throws Exception {
        launch(argv);
    }

    public void newActivation(String[] argv) {
        if (m_tracker != null) {
            if (argv.length > 0) {
                m_tracker.open();
                Object commandSession = getCommandSession(m_tracker.getService());
                System.out.println("New Activation: Session" + commandSession); //$NON-NLS-1$
                List<StringBuffer> commandList = splitCommand(argv);
                System.out.println("New Activation: command List" + commandList); //$NON-NLS-1$
                if (commandSession != null) {
                    // Set the main window visible and to the front
                    commandSession_execute(commandSession, "weasis:ui -v"); //$NON-NLS-1$
                    // execute the commands from main argv
                    for (StringBuffer command : commandList) {
                        commandSession_execute(commandSession, command);
                    }
                    commandSession_close(commandSession);
                }
                m_tracker.close();
            }
        }
    }
}
