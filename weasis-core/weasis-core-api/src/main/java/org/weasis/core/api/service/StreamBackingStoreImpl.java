/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.api.service;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.felix.prefs.BackingStore;
import org.apache.felix.prefs.PreferencesDescription;
import org.apache.felix.prefs.PreferencesImpl;
import org.osgi.framework.BundleContext;
import org.osgi.service.prefs.BackingStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.EscapeChars;
import org.weasis.core.api.util.FileUtil;

/**
 * This is an abstract implementation of a backing store which uses streams to read/write the preferences and stores a
 * complete preferences tree in a single stream.
 */
public abstract class StreamBackingStoreImpl implements BackingStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamBackingStoreImpl.class);

    /** The bundle context. */
    protected final BundleContext bundleContext;

    public StreamBackingStoreImpl(BundleContext context) {
        this.bundleContext = context;

    }

    /**
     * This method is invoked to check if the backing store is accessible right now.
     *
     * @throws BackingStoreException
     */
    protected abstract void checkAccess() throws BackingStoreException;

    /**
     * Get the output stream to write the preferences.
     */
    protected abstract OutputStream getOutputStream(PreferencesDescription desc) throws IOException;

    /**
     * @see org.apache.felix.prefs.BackingStore#store(org.apache.felix.prefs.PreferencesImpl)
     */
    @Override
    public void store(PreferencesImpl prefs) throws BackingStoreException {
        // do we need to store at all?
        if (!this.hasChanges(prefs)) {
            return;
        }
        this.checkAccess();
        // load existing data
        PreferencesImpl savedData = null;
        try {
            savedData = this.load(prefs.getBackingStoreManager(), prefs.getDescription());
        } catch (BackingStoreException e1) {
            // if the file is empty or corrupted
            LOGGER.error("Cannot store preferences", e1); //$NON-NLS-1$
        }

        final PreferencesImpl rootPrefs;
        if (savedData == null) {
            rootPrefs = prefs.getRoot();
        } else {
            // merge with saved version
            final PreferencesImpl n = savedData.getOrCreateNode(prefs.absolutePath());
            n.applyChanges(prefs);
            rootPrefs = n.getRoot();
        }

        XMLStreamWriter writer = null;
        try {
            final OutputStream os = this.getOutputStream(rootPrefs.getDescription());
            XMLOutputFactory factory = XMLOutputFactory.newInstance();
            writer = factory.createXMLStreamWriter(os, "UTF-8"); //$NON-NLS-1$
            writer.writeStartDocument("UTF-8", "1.0"); //$NON-NLS-1$ //$NON-NLS-2$
            writer.writeStartElement("preferences"); //$NON-NLS-1$
            this.write(rootPrefs, writer);
            writer.writeEndElement();
            writer.writeEndDocument();
        } catch (IOException ioe) {
            throw new BackingStoreException("Unable to store preferences.", ioe); //$NON-NLS-1$
        } catch (XMLStreamException e) {
            throw new BackingStoreException("Unable to store preferences.", e); //$NON-NLS-1$
        } finally {
            FileUtil.safeClose(writer);
        }
    }

    /**
     * Has the tree changes.
     *
     * @param prefs
     *            the prefs
     * @return true, if successful
     */
    protected boolean hasChanges(PreferencesImpl prefs) {
        if (prefs.getChangeSet().hasChanges()) {
            return true;
        }
        final Iterator<?> i = prefs.getChildren().iterator();
        while (i.hasNext()) {
            final PreferencesImpl current = (PreferencesImpl) i.next();
            if (this.hasChanges(current)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @see org.apache.felix.prefs.BackingStore#update(org.apache.felix.prefs.PreferencesImpl)
     */
    @Override
    public void update(PreferencesImpl prefs) throws BackingStoreException {
        // Do nothing, only update when writing
    }

    /**
     * Write the preferences recursively to the output stream.
     *
     * @param prefs
     * @param os
     * @throws IOException
     * @throws XMLStreamException
     */
    protected void write(PreferencesImpl prefs, XMLStreamWriter writer) throws XMLStreamException {
        final int size = prefs.getProperties().size();
        if (size > 0) {
            this.writePreferences(prefs, writer);
        }
        final Collection<?> children = prefs.getChildren();
        final Iterator<?> i = children.iterator();
        while (i.hasNext()) {
            final PreferencesImpl child = (PreferencesImpl) i.next();
            writer.writeStartElement(child.name());
            this.write(child, writer);
            writer.writeEndElement();
        }
        writer.flush();
    }

    protected void read(PreferencesImpl prefs, XMLStreamReader xmler, String startKey) throws XMLStreamException {
        int eventType;
        while (xmler.hasNext()) {
            eventType = xmler.next();
            switch (eventType) {
                // It is a properties of the node
                case XMLStreamConstants.CHARACTERS:
                    prefs.getProperties().put(startKey, xmler.getText());
                    break;
                // It is a child of the node
                case XMLStreamConstants.START_ELEMENT:
                    PreferencesImpl impl = prefs.getOrCreateNode(startKey);
                    this.read(impl, xmler, xmler.getName().getLocalPart());
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    // In case the tag does not contain values or inner tag
                    if (prefs.getProperties().isEmpty() && prefs.getChildren().isEmpty()) {
                        prefs.getOrCreateNode(startKey);
                    }
                    if (startKey.equals(xmler.getName().getLocalPart())) {
                        return; // Return to the parent tag
                    }
                    break;
                default:
                    break;
            }
        }

    }

    protected void writePreferences(PreferencesImpl prefs, XMLStreamWriter writer) throws XMLStreamException {
        final Iterator<?> i = prefs.getProperties().entrySet().iterator();
        while (i.hasNext()) {
            final Map.Entry<?, ?> entry = (Map.Entry<?, ?>) i.next();
            writer.writeStartElement(entry.getKey().toString());
            writer.writeCharacters(EscapeChars.forXML(entry.getValue().toString()));
            writer.writeEndElement();
        }
        writer.flush();
    }
}
