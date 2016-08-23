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
package org.weasis.acquire.explorer;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Hashtable;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.weasis.core.api.explorer.DataExplorerViewFactory;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.GzipManager;

@Component(immediate = false)
@Service
@Properties(value = { @Property(name = "service.name", value = "Media Dicomizer"),
    @Property(name = "service.description", value = "Import media and dicomize them") })
public class MediaImporterFactory implements DataExplorerViewFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(MediaImporterFactory.class);

    private AcquisitionView explorer = null;

    @Override
    public AcquisitionView createDataExplorerView(Hashtable<String, Object> properties) {
        if (explorer == null) {
            initGlobalTags();
            explorer = new AcquisitionView();
            explorer.initImageGroupPane();
        }
        return explorer;
    }

    @Activate
    protected void activate(ComponentContext context) {
        // Do nothing
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        if (explorer != null) {
            explorer.saveLastPath();
        }
    }

    private void initGlobalTags() {
        String xml = BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.patient.context"); //$NON-NLS-1$

        if (xml == null) {
            // TODO read service
        } else {

            InputStream stream = null;
            try {
                byte[] buf = null;
                boolean isPatientContextGzip =
                    Boolean.valueOf(BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.patient.context.gzip", "TRUE"));

                if (isPatientContextGzip) {
                    // byte[] byteArray = Base64.getDecoder().decode(xml.getBytes(StandardCharsets.UTF_8));
                    byte[] byteArray = Base64.getUrlDecoder().decode(xml.getBytes(StandardCharsets.UTF_8));

                    buf = GzipManager.gzipUncompressToByte(byteArray);
                } else {
                    buf = xml.getBytes(StandardCharsets.UTF_8);
                }
                stream = new ByteArrayInputStream(buf);

                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                Document doc = dBuilder.parse(stream);

                AcquireManager.GLOBAL.init(doc);
            } catch (Exception e) {
                LOGGER.error("Loading gobal tags", e);
            } finally {
                FileUtil.safeClose(stream);
            }
        }
    }

}