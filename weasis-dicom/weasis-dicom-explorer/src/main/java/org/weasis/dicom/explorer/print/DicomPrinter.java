/*******************************************************************************
 * Copyright (c) 2011 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Marcelo Porto - initial API and implementation, Animati Sistemas de Informática Ltda. (http://www.animati.com.br)
 *     Nicolas Roduit
 *     
 ******************************************************************************/

package org.weasis.dicom.explorer.print;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.util.FileUtil;

/**
 * 
 * @author Marcelo Porto (marcelo@animati.com.br)
 * @version Mar 12, 2012
 */
public class DicomPrinter {

    private String description;
    private String aeTitle;
    private String hostname;
    private int port;
    private boolean colorPrintSupported;

    public static void savePrintersSettings(javax.swing.JComboBox printersComboBox) {
        XMLStreamWriter writer = null;
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        final BundleContext context = FrameworkUtil.getBundle(DicomPrinter.class).getBundleContext();
        try {
            writer =
                factory.createXMLStreamWriter(new FileOutputStream(new File(BundlePreferences.getDataFolder(context),
                    "dicomPrinters.xml")), "UTF-8"); //$NON-NLS-1$ //$NON-NLS-2$

            writer.writeStartDocument("UTF-8", "1.0"); //$NON-NLS-1$ //$NON-NLS-2$
            writer.writeStartElement("printers"); //$NON-NLS-1$
            for (int i = 0; i < printersComboBox.getItemCount(); i++) {
                DicomPrinter printer = (DicomPrinter) printersComboBox.getItemAt(i);
                writer.writeStartElement("printer"); //$NON-NLS-1$
                writer.writeAttribute("description", printer.getDescription()); //$NON-NLS-1$
                writer.writeAttribute("aeTitle", printer.getAeTitle()); //$NON-NLS-1$
                writer.writeAttribute("hostname", printer.getHostname()); //$NON-NLS-1$
                writer.writeAttribute("port", "" + printer.getPort()); //$NON-NLS-1$ //$NON-NLS-2$
                writer.writeAttribute("colorPrintSupported", printer.isColorPrintSupported().toString()); //$NON-NLS-1$
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
        final BundleContext context = FrameworkUtil.getBundle(DicomPrinter.class).getBundleContext();
        File prefs = new File(BundlePreferences.getDataFolder(context), "dicomPrinters.xml"); //$NON-NLS-1$
        if (prefs.canRead()) {
            XMLStreamReader xmler = null;
            XMLInputFactory factory = XMLInputFactory.newInstance();
            try {
                xmler = factory.createXMLStreamReader(new FileInputStream(prefs));
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
                                                    printer
                                                        .setDescription(xmler.getAttributeValue(null, "description")); //$NON-NLS-1$
                                                    printer.setAeTitle(xmler.getAttributeValue(null, "aeTitle")); //$NON-NLS-1$
                                                    printer.setHostname(xmler.getAttributeValue(null, "hostname")); //$NON-NLS-1$
                                                    printer.setPort(FileUtil.getIntegerTagAttribute(xmler, "port", 0)); //$NON-NLS-1$
                                                    printer.setColorPrintSupported(Boolean.parseBoolean(xmler
                                                        .getAttributeValue(null, "colorPrintSupported"))); //$NON-NLS-1$
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

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public Boolean isColorPrintSupported() {
        return colorPrintSupported;
    }

    public void setColorPrintSupported(boolean colorPrintSupported) {
        this.colorPrintSupported = colorPrintSupported;
    }

    @Override
    public String toString() {
        return description;
    }

}
