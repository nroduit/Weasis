/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractTabLicense;

public interface LicenseTabFactory extends InsertableFactory {

  static final Logger LOGGER = LoggerFactory.getLogger(LicenseTabFactory.class);

  static final String keyFileName = "config-key.properties";

  @Override
  AbstractTabLicense createInstance(Hashtable<String, Object> properties);

  default URI[] loadUrisFromFile(String fileName, String urisPropertyName) {
    String urlsFileName = fileName + ".properties";
    LOGGER.debug("Loading boot URLs resource: {}", urlsFileName);
    Properties p = new Properties();
    try (InputStream is = this.getClass().getClassLoader().getResourceAsStream(urlsFileName)) {
      LOGGER.debug("Boot URLs file loaded: {}", is);
      p.load(is);
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
      return null;
    }
    Properties keyProp = new Properties();
    try (InputStream is = this.getClass().getClassLoader().getResourceAsStream(keyFileName)) {
      keyProp.load(is);
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
      return null;
    }
    String uris = p.getProperty(urisPropertyName);
    uris = decode(keyProp.getProperty("key"), uris);
    String[] urisStr = uris.split(",");
    URI[] result = new URI[urisStr.length];
    for (int i = 0; i < urisStr.length; i++) {
      result[i] = URI.create(urisStr[i]);
      LOGGER.trace(urlsFileName);
    }
    return result;
  }

  private String decode(String key, String value) {
    Random r = new Random(key.hashCode());
    StringBuilder switchedString = new StringBuilder();
    for (int i = 0; i < value.length(); ++i)
      switchedString.append((char) (value.charAt(i) + (-1) * ((i * r.nextLong() + r.nextLong()) % 8192)));
    return switchedString.toString();
  }

}
