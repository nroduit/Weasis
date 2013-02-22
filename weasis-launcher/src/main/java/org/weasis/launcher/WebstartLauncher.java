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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.imageio.spi.IIORegistry;
import javax.jnlp.ServiceManager;
import javax.jnlp.SingleInstanceListener;
import javax.jnlp.SingleInstanceService;
import javax.jnlp.UnavailableServiceException;
import javax.swing.SwingUtilities;

public class WebstartLauncher extends WeasisLauncher implements SingleInstanceListener {

    private static final WebstartLauncher instance = new WebstartLauncher();
    static {
        try {
            SingleInstanceService singleInstanceService =
                (SingleInstanceService) ServiceManager.lookup("javax.jnlp.SingleInstanceService"); //$NON-NLS-1$
            singleInstanceService.addSingleInstanceListener(instance);
        } catch (UnavailableServiceException use) {
        }

        // Workaround for Java Web Start issue http://forums.oracle.com/forums/thread.jspa?threadID=2148703&tstart=15
        // If imageio.jar is located in the JRE ext directory, unregister imageio services.
        // Launcher of the portable version has the VM parameter -Djava.ext.dirs=""
        IIORegistry registry = IIORegistry.getDefaultInstance();
        Iterator<Class<?>> categories = registry.getCategories();
        ArrayList toRemove = new ArrayList();
        while (categories.hasNext()) {
            Class<?> class1 = categories.next();
            Iterator<?> providers = registry.getServiceProviders(class1, false);
            while (providers.hasNext()) {
                Object provider = providers.next();
                if (provider.getClass().getPackage().getName().startsWith("com.sun.media")) { //$NON-NLS-1$
                    toRemove.add(provider);
                }
            }
        }
        for (Object provider : toRemove) {
            registry.deregisterServiceProvider(provider);
        }
    }

    public static void main(String[] argv) throws Exception {
        launch(argv);
    }

    @Override
    public void newActivation(final String[] argv) {
        synchronized (this) {
            int loop = 0;
            boolean runLoop = true;
            while (runLoop && !frameworkLoaded) {
                try {
                    Thread.sleep(500);
                    loop++;
                    // System.out.println(String.format("Wait to launch new instance: %.1f s", loop * 0.5));
                    if (loop > 100) {
                        throw new InterruptedException();
                    }
                } catch (InterruptedException e) {
                    runLoop = false;
                }
            }
        }
        if (m_tracker != null) {
            if (argv.length > 0) {
                SwingUtilities.invokeLater(new Runnable() {

                    @Override
                    public void run() {
                        m_tracker.open();
                        Object commandSession = getCommandSession(m_tracker.getService());
                        if (commandSession != null) {
                            List<StringBuffer> commandList = splitCommand(argv);
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
                });
            }
        }
    }
}
