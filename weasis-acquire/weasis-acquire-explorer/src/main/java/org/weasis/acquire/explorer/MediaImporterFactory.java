package org.weasis.acquire.explorer;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
        explorer = null;
    }

    private void initGlobalTags() {
        String xml = BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.patient.context"); //$NON-NLS-1$
        if (xml == null) {
            // TODO read service
        } else {
            try {
                InputStream stream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                Document doc = dBuilder.parse(stream);

                AcquireManager.GLOBAL.init(doc);
            } catch (Exception e) {
                LOGGER.error("Loading gobal tags", e);
            }
        }
    }

}