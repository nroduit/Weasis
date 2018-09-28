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
package org.weasis.launcher;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.spi.IIORegistry;
import javax.jnlp.ServiceManager;
import javax.jnlp.SingleInstanceListener;
import javax.jnlp.SingleInstanceService;
import javax.jnlp.UnavailableServiceException;

public class WebstartLauncher extends WeasisLauncher implements SingleInstanceListener {
    private static final Logger LOGGER = Logger.getLogger(WebstartLauncher.class.getName());

    private static final WebstartLauncher instance = new WebstartLauncher();

    static {
        try {
            SingleInstanceService singleInstanceService =
                (SingleInstanceService) ServiceManager.lookup("javax.jnlp.SingleInstanceService"); //$NON-NLS-1$
            singleInstanceService.addSingleInstanceListener(instance);
        } catch (UnavailableServiceException e) {
            LOGGER.log(Level.SEVERE, "Unable to get SingleInstanceService", e); //$NON-NLS-1$
        }

        // Workaround for Java Web Start issue http://forums.oracle.com/forums/thread.jspa?threadID=2148703&tstart=15
        // If imageio.jar is located in the JRE ext directory, unregister imageio services.
        // For the portable version, the services are unregistered later in
        // org.weasis.imageio.codec.internal.Activator.java
        IIORegistry registry = IIORegistry.getDefaultInstance();
        Iterator<Class<?>> categories = registry.getCategories();
        ArrayList<Object> toRemove = new ArrayList<>();
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

    @Override
    public void newActivation(final String[] argv) {
        synchronized (this) {
            int loop = 0;
            boolean runLoop = true;
            while (runLoop && !frameworkLoaded) {
                try {
                    TimeUnit.MILLISECONDS.sleep(200);
                    loop++;
                    if (loop > 100) {
                        runLoop = false;
                    }
                } catch (InterruptedException e) {
                    runLoop = false;
                    Thread.currentThread().interrupt();
                }
            }
        }
        if (m_tracker != null && argv.length > 0) {
            executeCommands(splitCommand(argv), null);
        }
    }

    public static void launch(String[] argv) throws Exception {
        WeasisLauncher.launch(argv);
    }

    public static void main(String[] argv) throws Exception {
        launch(argv);
    }
}
