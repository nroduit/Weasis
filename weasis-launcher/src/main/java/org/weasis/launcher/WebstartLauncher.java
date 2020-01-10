/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.launcher;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.spi.IIORegistry;
import javax.jnlp.ServiceManager;
import javax.jnlp.SingleInstanceListener;
import javax.jnlp.SingleInstanceService;
import javax.jnlp.UnavailableServiceException;

import org.apache.felix.framework.util.FelixConstants;

@Deprecated
public class WebstartLauncher extends WeasisLauncher implements SingleInstanceListener {
    private static final Logger LOGGER = Logger.getLogger(WebstartLauncher.class.getName());

    static {
        setJnlpSystemProperties();
    }
    
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

    public WebstartLauncher() {
        super(new ConfigData());
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
        if (mTracker != null) {
            ConfigData data = new ConfigData(argv);
            executeCommands(data.getArguments(), null);
        }
    }

    public static void main(String[] argv) throws Exception {
        instance.configData.init(argv);
        // Remove the prefix "jnlp.weasis" of JNLP Properties
        // Workaround for having a fully trusted application with JWS,
        // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6653241
        instance.launch(Type.JWS);
    }

    private static void setJnlpSystemProperties() {
        final String PREFIX = "jnlp.weasis."; //$NON-NLS-1$
        final int PREFIX_LENGTH = PREFIX.length();

        Properties properties = System.getProperties();
        for (String propertyName : properties.stringPropertyNames()) {
            if (propertyName.startsWith(PREFIX)) {
                String value = properties.getProperty(propertyName);
                System.setProperty(propertyName.substring(PREFIX_LENGTH), value);
                properties.remove(propertyName);
            }
        }

        // Disabling extension framework is mandatory to work with Java Web Start.
        // From framework 4.4.1, See https://issues.apache.org/jira/browse/FELIX-4281.
        System.setProperty(FelixConstants.FELIX_EXTENSIONS_DISABLE, Boolean.TRUE.toString());
    }
}
