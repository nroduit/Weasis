/*
 * @copyright Copyright (c) 2009 Animati Sistemas de Inform�tica Ltda. (http://www.animati.com.br)
 */

package org.weasis.dicom.explorer.print;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.explorer.internal.Activator;

/**
 * 
 * @author Marcelo Porto (marcelo@animati.com.br)
 * @version Mar 12, 2012
 */
public class DicomPrinter {

    private String description;
    private String aeTitle;
    private String hostname;
    private String port;
    private Boolean colorPrintSupported;

    public static void savePrintersSettings(javax.swing.JComboBox printersComboBox) {
        XMLStreamWriter writer = null;
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        try {
            writer =
                factory.createXMLStreamWriter(new FileWriter(new File(Activator.PREFERENCES.getDataFolder(),
                    "dicomPrinters.xml")));

            writer.writeStartDocument();
            writer.writeStartElement("printers");
            for (int i = 0; i < printersComboBox.getItemCount(); i++) {
                DicomPrinter printer = (DicomPrinter) printersComboBox.getItemAt(i);
                writer.writeStartElement("printer");
                writer.writeAttribute("description", printer.getDescription());
                writer.writeAttribute("aeTitle", printer.getAeTitle());
                writer.writeAttribute("hostname", printer.getHostname());
                writer.writeAttribute("port", printer.getPort());
                writer.writeAttribute("colorPrintSupported", printer.isColorPrintSupported().toString());
                writer.writeEndElement();
            }
            writer.writeEndElement();
            writer.writeEndDocument();
            writer.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            FileUtil.safeClose(writer);
        }
    }

    public static void loadPrintersSettings(javax.swing.JComboBox printersComboBox) {
        XMLStreamReader xmler = null;
        XMLInputFactory factory = XMLInputFactory.newInstance();
        try {
            xmler =
                factory.createXMLStreamReader(new FileReader(new File(Activator.PREFERENCES.getDataFolder(),
                    "dicomPrinters.xml")));
            int eventType;
            while (xmler.hasNext()) {
                eventType = xmler.next();
                switch (eventType) {
                    case XMLStreamConstants.START_ELEMENT:
                        String key = xmler.getName().getLocalPart();
                        if ("printers".equals(key)) { //$NON-NLS-1$
                            while (xmler.hasNext()) {
                                eventType = xmler.next();
                                switch (eventType) {
                                    case XMLStreamConstants.START_ELEMENT:
                                        key = xmler.getName().getLocalPart();
                                        if ("printer".equals(key)) { //$NON-NLS-1$
                                            if (xmler.getAttributeCount() == 5) {
                                                DicomPrinter printer = new DicomPrinter();
                                                printer.setDescription(xmler.getAttributeValue(null, "description"));
                                                printer.setAeTitle(xmler.getAttributeValue(null, "aeTitle"));
                                                printer.setHostname(xmler.getAttributeValue(null, "hostname"));
                                                printer.setPort(xmler.getAttributeValue(null, "port"));
                                                printer.setColorPrintSupported(Boolean.parseBoolean(xmler
                                                    .getAttributeValue(null, "colorPrintSupported")));
                                                printersComboBox.addItem(printer);
                                            }
                                        }
                                        break;
                                    default:
                                        break;
                                }
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            FileUtil.safeClose(xmler);
        }
    }

    public String getAeTitle() {
        return aeTitle;
    }

    public void setAeTitle(String aeTitle) {
        this.aeTitle = aeTitle;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public Boolean isColorPrintSupported() {
        return colorPrintSupported;
    }

    public void setColorPrintSupported(Boolean colorPrintSupported) {
        this.colorPrintSupported = colorPrintSupported;
    }

    @Override
    public String toString() {
        return description;
    }
}
