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
package org.weasis.base.explorer;

import java.util.Hashtable;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.component.ComponentContext;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.DataExplorerViewFactory;

@Component(immediate = false)
@Service
@Properties(value = { @Property(name = "service.name", value = "Media Explorer"),
    @Property(name = "service.description", value = "Explore supported media files in tree view") })
public class DefaultExplorerFactory implements DataExplorerViewFactory {

    private DefaultExplorer explorer = null;
    private FileTreeModel model = null;

    @Override
    public DataExplorerView createDataExplorerView(Hashtable<String, Object> properties) {
        if (model == null) {
            model = JIUtility.createTreeModel();
        }
        if (explorer == null) {
            explorer = new DefaultExplorer(model);
            explorer.iniLastPath();
        }
        return explorer;
    }

    @Activate
    protected void activate(ComponentContext context) {
        if (model == null) {
            model = JIUtility.createTreeModel();
        }
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        if (explorer != null) {
            explorer.saveLastPath();
        }
    }

}