/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.explorer;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.felix.service.command.CommandProcessor;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerViewFactory;

@org.osgi.service.component.annotations.Component(service = DataExplorerViewFactory.class, immediate = false)
public class MediaImporterFactory implements DataExplorerViewFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(MediaImporterFactory.class);

    private AcquireExplorer explorer = null;

    @Override
    public AcquireExplorer createDataExplorerView(Hashtable<String, Object> properties) {
        if (explorer == null) {
            explorer = new AcquireExplorer();
            explorer.initImageGroupPane();
            AcquireManager.getInstance().registerDataExplorerView(explorer);
        }
        return explorer;
    }

    // ================================================================================
    // OSGI service implementation
    // ================================================================================

    @Activate
    protected void activate(ComponentContext context) {
        registerCommands(context);
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        if (explorer != null) {
            explorer.saveLastPath();
            AcquireManager.getInstance().unRegisterDataExplorerView();
            // TODO handle user message if all data is not published !!!
        }

    }

    private void registerCommands(ComponentContext context) {
        if (context != null) {
            ServiceReference<?>[] val = null;

            String serviceClassName = AcquireManager.class.getName();
            try {
                val = context.getBundleContext().getServiceReferences(serviceClassName, null);
            } catch (InvalidSyntaxException e) {
                LOGGER.error("Get media importer services", e); //$NON-NLS-1$
            }
            if (val == null || val.length == 0) {
                Dictionary<String, Object> dict = new Hashtable<>();
                dict.put(CommandProcessor.COMMAND_SCOPE, "acquire"); //$NON-NLS-1$
                dict.put(CommandProcessor.COMMAND_FUNCTION, AcquireManager.functions.toArray(new String[AcquireManager.functions.size()]));
                context.getBundleContext().registerService(serviceClassName, AcquireManager.getInstance(), dict);
            }
        }
    }

}