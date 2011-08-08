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
package org.weasis.core.api.image;

import java.awt.GridBagConstraints;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.weasis.core.api.gui.util.GUIEntry;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * GridBagLayoutModel is the model for the plugin container.
 * 
 */
public class GridBagLayoutModel implements GUIEntry, Cloneable {

    private String title;
    private final Icon icon;

    private final LinkedHashMap<LayoutConstraints, JComponent> constraints;

    public GridBagLayoutModel(String title, int rows, int cols, String defaultClass, Icon icon) {
        this.title = title;
        this.icon = icon;
        if (cols < 1) {
            cols = 1;
        }
        if (rows < 1) {
            rows = 1;
        }
        this.constraints = new LinkedHashMap<LayoutConstraints, JComponent>(cols * rows);
        double weightx = 1.0 / cols;
        double weighty = 1.0 / rows;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                constraints.put(new LayoutConstraints(defaultClass, y * cols + cols, x, y, 1, 1, weightx, weighty,
                    GridBagConstraints.CENTER, GridBagConstraints.BOTH), null);
            }
        }
    }

    public GridBagLayoutModel(LinkedHashMap<LayoutConstraints, JComponent> constraints, String title, Icon icon) {
        if (constraints == null)
            throw new IllegalArgumentException("constraints cannot be null"); //$NON-NLS-1$
        this.title = title;
        this.icon = icon;
        this.constraints = constraints;
    }

    public GridBagLayoutModel(InputStream stream, String title, Icon icon) {
        this.title = title;
        this.icon = icon;
        this.constraints = new LinkedHashMap<LayoutConstraints, JComponent>();
        try {
            loadXML(stream);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return title;
    }

    public LinkedHashMap<LayoutConstraints, JComponent> getConstraints() {
        return constraints;
    }

    public void loadXML(InputStream stream) throws IOException, SAXException {
        try {
            SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
            SAXAdapter adapter = new SAXAdapter();
            parser.parse(stream, adapter);
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        } catch (FactoryConfigurationError e) {
            throw new RuntimeException(e);
        }
    }

    private final class SAXAdapter extends DefaultHandler {
        /**
         * Specifies the component position and size
         * 
         */

        /**
         * @see java.awt.GridBagConstraints#gridx
         */
        private int x;
        /**
         * @see java.awt.GridBagConstraints#gridy
         */
        private int y;
        /**
         * @see java.awt.GridBagConstraints#gridwidth
         */
        private int width;
        /**
         * @see java.awt.GridBagConstraints#gridheight
         */
        private int height;
        /**
         * @see java.awt.GridBagConstraints#weightX
         */
        private double weightx;
        /**
         * @see java.awt.GridBagConstraints#weighty
         */
        private double weighty;
        /**
         * @see java.awt.GridBagConstraints#anchor
         */
        private int position;
        /**
         * @see java.awt.GridBagConstraints#fill
         */
        private int expand;
        /**
         * The component class
         */
        private String type;
        /**
         * ID of the component
         */
        private int increment = 0;

        private int tag = -1;
        private StringBuffer name = new StringBuffer(80);

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (tag != -1) {
                name.append(ch, start, length);
            }
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            if ("element".equals(qName)) { //$NON-NLS-1$
                type = attributes.getValue("type"); //$NON-NLS-1$
                x = Integer.parseInt(attributes.getValue("x")); //$NON-NLS-1$
                y = Integer.parseInt(attributes.getValue("y")); //$NON-NLS-1$
                width = Integer.parseInt(attributes.getValue("width")); //$NON-NLS-1$
                height = Integer.parseInt(attributes.getValue("height")); //$NON-NLS-1$
                weightx = getDoubleValue(attributes.getValue("weightx")); //$NON-NLS-1$
                weighty = getDoubleValue(attributes.getValue("weighty")); //$NON-NLS-1$
                position = Integer.parseInt(attributes.getValue("position")); //$NON-NLS-1$
                expand = Integer.parseInt(attributes.getValue("expand")); //$NON-NLS-1$

            } else if (title == null && "layoutModel".equals(qName)) { //$NON-NLS-1$
                title = attributes.getValue("name"); //$NON-NLS-1$
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if ("element".equals(qName)) { //$NON-NLS-1$
                increment++;
                constraints.put(new LayoutConstraints(type, increment, x, y, width, height, weightx, weighty, position,
                    expand), null);
                name.setLength(0);
                tag = -1;
            }
        }

        private double getDoubleValue(String val) {
            if (val.trim().equals("")) //$NON-NLS-1$
                return 0.0;
            // handle fraction format
            int index = val.indexOf('/');
            if (index != -1)
                return (double) Integer.parseInt(val.substring(0, index)) / Integer.parseInt(val.substring(index + 1));
            return Double.parseDouble(val);
        }

    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public Icon getIcon() {
        return icon;
    }

    @Override
    public String getUIName() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        LinkedHashMap<LayoutConstraints, JComponent> map =
            new LinkedHashMap<LayoutConstraints, JComponent>(constraints.size());
        Iterator<LayoutConstraints> enumVal = constraints.keySet().iterator();
        while (enumVal.hasNext()) {
            map.put((LayoutConstraints) enumVal.next().clone(), null);
        }
        return new GridBagLayoutModel(map, title, icon);
    }
}
