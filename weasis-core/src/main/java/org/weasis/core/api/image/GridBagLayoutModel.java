/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image;

import com.formdev.flatlaf.ui.FlatUIUtils;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.geom.Rectangle2D;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.Icon;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GUIEntry;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.api.gui.util.RadioMenuItem;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.util.Copyable;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.util.StringUtil;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** GridBagLayoutModel is the model for the plugin container. */
public class GridBagLayoutModel implements GUIEntry, Copyable<GridBagLayoutModel> {

  private static final Logger LOGGER = LoggerFactory.getLogger(GridBagLayoutModel.class);
  public static final int ICON_SIZE = 22;

  private String title;
  private Icon icon;
  private final String id;

  private final Map<LayoutConstraints, Component> constraints;

  public GridBagLayoutModel(String id, String title, int rows, int cols, String defaultClass) {
    this.title = title;
    this.id = id;
    this.constraints = new LinkedHashMap<>(cols * rows);
    double weightX = 1.0 / cols;
    double weightY = 1.0 / rows;
    for (int y = 0; y < rows; y++) {
      for (int x = 0; x < cols; x++) {
        constraints.put(
            new LayoutConstraints(
                defaultClass,
                y * cols + cols,
                x,
                y,
                1,
                1,
                weightX,
                weightY,
                GridBagConstraints.CENTER,
                GridBagConstraints.BOTH),
            null);
      }
    }
    this.icon = buildIcon();
  }

  public GridBagLayoutModel(
      Map<LayoutConstraints, Component> constraints, String id, String title) {
    if (constraints == null) {
      throw new IllegalArgumentException("constraints cannot be null");
    }
    this.title = title;
    this.id = id;
    this.constraints = constraints;
    this.icon = buildIcon();
  }

  public GridBagLayoutModel(InputStream stream, String id, String title) {
    this.title = title;
    this.id = id;
    this.constraints = new LinkedHashMap<>();
    try {
      SAXParserFactory factory = SAXParserFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      SAXParser parser = factory.newSAXParser();
      parser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      parser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      parser.parse(stream, new SAXAdapter());
    } catch (Exception e) {
      LOGGER.error("Loading layout xml", e);
    }
    this.icon = icon == null ? buildIcon() : icon;
  }

  public GridBagLayoutModel(GridBagLayoutModel layoutModel) {
    this.title = layoutModel.title;
    this.id = layoutModel.id;
    this.icon = layoutModel.icon;

    this.constraints = new LinkedHashMap<>(layoutModel.constraints.size());
    for (LayoutConstraints layoutConstraints : layoutModel.constraints.keySet()) {
      this.constraints.put(layoutConstraints.copy(), null);
    }
  }

  private Icon buildIcon() {
    return new Icon() {

      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2d = (Graphics2D) g;
        Color oldColor = g.getColor();
        Object[] hints = GuiUtils.setRenderingHints(g, true, true, false);

        Color background = IconColor.ACTIONS_GREY.color;
        Color foreground = FlatUIUtils.getUIColor("MenuItem.background", Color.WHITE);
        if (c instanceof RadioMenuItem menuItem && menuItem.isArmed()) {
          background = FlatUIUtils.getUIColor("MenuItem.selectionForeground", Color.DARK_GRAY);
          foreground = FlatUIUtils.getUIColor("MenuItem.selectionBackground", Color.LIGHT_GRAY);
        } else if (c instanceof RadioMenuItem menuItem && menuItem.isSelected()) {
          foreground = FlatUIUtils.getUIColor("MenuItem.checkBackground", Color.BLUE);
        }
        g2d.setColor(background);
        g2d.fillRect(x, y, getIconWidth(), getIconHeight());
        g2d.setColor(foreground);
        Dimension dim = getGridSize();
        double stepX = getIconWidth() / dim.getWidth();
        double stepY = getIconHeight() / dim.getHeight();

        for (LayoutConstraints l : constraints.keySet()) {
          Rectangle2D rect =
              new Rectangle2D.Double(
                  x + l.gridx * stepX,
                  y + l.gridy * stepY,
                  l.gridwidth * stepX,
                  l.gridheight * stepY);
          Color color = l.getColor();
          if (color != null) {
            g2d.setColor(color);
            g2d.fill(rect);
            g2d.setColor(foreground);
          }
          g2d.draw(rect);
        }
        g2d.setColor(oldColor);
        GuiUtils.resetRenderingHints(g, hints);
      }

      @Override
      public int getIconWidth() {
        return GuiUtils.getScaleLength(ICON_SIZE);
      }

      @Override
      public int getIconHeight() {
        return GuiUtils.getScaleLength(ICON_SIZE);
      }
    };
  }

  public String getId() {
    return id;
  }

  @Override
  public String toString() {
    return title;
  }

  public Map<LayoutConstraints, Component> getConstraints() {
    return constraints;
  }

  public Dimension getGridSize() {
    int lastx = 0;
    int lasty = 0;
    for (LayoutConstraints key : constraints.keySet()) {
      if (key.gridx > lastx) {
        lastx = key.gridx;
      }
      if (key.gridy > lasty) {
        lasty = key.gridy;
      }
    }
    return new Dimension(lastx + 1, lasty + 1);
  }

  @Override
  public String getDescription() {
    return null;
  }

  @Override
  public Icon getIcon() {
    return icon;
  }

  public void setIcon(Icon icon) {
    this.icon = icon;
  }

  @Override
  public String getUIName() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  @Override
  public GridBagLayoutModel copy() {
    return new GridBagLayoutModel(this);
  }

  private final class SAXAdapter extends DefaultHandler {
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
     * @see java.awt.GridBagConstraints#weightx
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
    /** The component class */
    private String type;
    /** ID of the component */
    private int increment = 0;

    private Color color;

    private int tag = -1;
    private final StringBuilder name = new StringBuilder(80);

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
      if (tag != -1) {
        name.append(ch, start, length);
      }
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
        throws SAXException {
      if ("element".equals(qName)) { // NON-NLS
        type = attributes.getValue("type");
        x = Integer.parseInt(attributes.getValue("x")); // NON-NLS
        y = Integer.parseInt(attributes.getValue("y")); // NON-NLS
        width = Integer.parseInt(attributes.getValue("width")); // NON-NLS
        height = Integer.parseInt(attributes.getValue("height")); // NON-NLS
        weightx = getDoubleValue(attributes.getValue("weightx")); // NON-NLS
        weighty = getDoubleValue(attributes.getValue("weighty")); // NON-NLS
        position = Integer.parseInt(attributes.getValue("position")); // NON-NLS
        expand = Integer.parseInt(attributes.getValue("expand")); // NON-NLS
        String ctype = attributes.getValue("typeColor");
        if (StringUtil.hasText(ctype)) {
          color = WProperties.hexadecimal2Color(ctype);
        }
      } else if ("layoutModel".equals(qName)) {
        if (title == null) {
          title = attributes.getValue("name"); // NON-NLS
        }
        String path = attributes.getValue("icon");
        if (StringUtil.hasText(path)) {
          icon = ResourceUtil.getIcon(path).derive(ICON_SIZE, ICON_SIZE);
        }
      }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
      if ("element".equals(qName)) { // NON-NLS
        increment++;
        LayoutConstraints c =
            new LayoutConstraints(
                type, increment, x, y, width, height, weightx, weighty, position, expand);
        c.setColor(color);
        constraints.put(c, null);
        name.setLength(0);
        tag = -1;
      }
    }

    private double getDoubleValue(String val) {
      if (!StringUtil.hasText(val)) {
        return 0.0;
      }
      // handle fraction format
      int index = val.indexOf('/');
      if (index != -1) {
        return (double) Integer.parseInt(val.substring(0, index))
            / Integer.parseInt(val.substring(index + 1));
      }
      return Double.parseDouble(val);
    }
  }
}
