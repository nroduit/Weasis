/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.serialize;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.util.StreamReaderDelegate;

public final class NoNamespaceStreamReaderDelegate extends StreamReaderDelegate {
  NoNamespaceStreamReaderDelegate(XMLStreamReader reader) {
    super(reader);
  }

  @Override
  public int getNamespaceCount() {
    return 1;
  }

  @Override
  public String getNamespacePrefix(int index) {
    if (index == 0) {
      return "xsi"; // NON-NLS
    }
    throw new NullPointerException();
  }

  @Override
  public String getNamespaceURI() {
    return null;
  }

  @Override
  public String getNamespaceURI(String prefix) {
    if ("xsi".equals(prefix)) { // NON-NLS
      return XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI;
    }
    return null;
  }

  @Override
  public String getNamespaceURI(int index) {
    if (index == 0) {
      return XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI;
    }
    return null;
  }
}
