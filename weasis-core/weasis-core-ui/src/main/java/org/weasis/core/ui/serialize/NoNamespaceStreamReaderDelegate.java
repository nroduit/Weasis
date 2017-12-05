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
            return "xsi"; //$NON-NLS-1$
        }
        throw new NullPointerException();
    }

    @Override
    public String getNamespaceURI() {
        return null;
    }

    @Override
    public String getNamespaceURI(String prefix) {
        if ("xsi".equals(prefix)) { //$NON-NLS-1$
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
