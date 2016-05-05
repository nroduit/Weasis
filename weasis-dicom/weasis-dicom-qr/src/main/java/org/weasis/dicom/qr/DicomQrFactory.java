package org.weasis.dicom.qr;

import java.io.File;
import java.util.Hashtable;
import java.util.Properties;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.explorer.DicomImportFactory;
import org.weasis.dicom.explorer.ImportDicom;

@Component(immediate = false)
@Service
@Property(name = "service.name", value = "DICOM Send")
public class DicomQrFactory implements DicomImportFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(DicomQrFactory.class);

    public static final Properties IMPORT_PERSISTENCE = new Properties();

    @Override
    public ImportDicom createDicomImportPage(Hashtable<String, Object> properties) {
        return new DicomQrView();
    }

    @Activate
    protected void activate(ComponentContext context) throws Exception {
        LOGGER.info("DICOM Q/R is activated");
        FileUtil.readProperties(
            new File(BundlePreferences.getDataFolder(context.getBundleContext()), "import.properties"), //$NON-NLS-1$
            IMPORT_PERSISTENCE);
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        LOGGER.info("DICOM Q/R is deactivated");
        FileUtil.storeProperties(
            new File(BundlePreferences.getDataFolder(context.getBundleContext()), "import.properties"), //$NON-NLS-1$
            IMPORT_PERSISTENCE, null);

    }

}
