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
package org.weasis.core.api.service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.felix.prefs.BackingStoreManager;
import org.apache.felix.prefs.PreferencesDescription;
import org.apache.felix.prefs.PreferencesImpl;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.prefs.BackingStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractProperties;
import org.weasis.core.api.util.FileUtil;

public class DataFileBackingStoreImpl extends StreamBackingStoreImpl {

    private static final Logger logger = LoggerFactory.getLogger(DataFileBackingStoreImpl.class);

    private final BundleContext context;
    private final File prefRootDirectory;
    private final String user;

    public DataFileBackingStoreImpl(BundleContext context) {
        super(context);
        this.context = context;
        this.prefRootDirectory = new File(AbstractProperties.WEASIS_PATH + File.separator, "preferences"); //$NON-NLS-1$
        prefRootDirectory.mkdirs();
        this.user = System.getProperty("weasis.user", null); //$NON-NLS-1$
    }

    /**
     * @see org.apache.felix.sandbox.preferences.impl.StreamBackingStoreImpl#checkAccess()
     */
    @Override
    protected void checkAccess() throws BackingStoreException {
        if (prefRootDirectory == null || !prefRootDirectory.exists()) {
            throw new BackingStoreException("Saving of data files to the bundle context is currently not supported."); //$NON-NLS-1$
        }
    }

    /**
     * @see org.apache.felix.sandbox.preferences.impl.StreamBackingStoreImpl#getOutputStream(org.apache.felix.sandbox.preferences.PreferencesDescription)
     */
    @Override
    protected OutputStream getOutputStream(PreferencesDescription desc) throws IOException {
        File file = this.getFile(desc);
        // Write user folder if not exists
        FileUtil.isWriteable(file);
        return new FileOutputStream(file);
    }

    /**
     * @see org.apache.felix.prefs.BackingStore#availableBundles()
     */
    public Long[] availableBundles() {
        return new Long[0];
    }

    protected PreferencesDescription getDescription(File file) {
        final String fileName = file.getName();
        // parse the file name to get: bundle id, user|system identifier
        if (fileName.endsWith(".xml")) { //$NON-NLS-1$
            final String key = fileName.substring(0, fileName.length() - 4);
            String identifier = null;
            File parent = file.getParentFile();
            if (parent != null) {
                String folder = file.getName();
                if (!"preferences".equals(folder)) { //$NON-NLS-1$
                    identifier = folder;
                }
            }
            if (identifier == user) {
                Bundle[] bundles = context.getBundles();
                for (Bundle bundle : bundles) {
                    if (bundle.getSymbolicName().equals(key)) {
                        return new PreferencesDescription(bundle.getBundleId(), identifier);
                    }
                }
            }
        }
        return null;
    }

    /**
     * @see org.apache.felix.prefs.BackingStore#remove(java.lang.Long)
     */
    public void remove(Long bundleId) throws BackingStoreException {
        // Do nothing, we don't want to delete the preferences file when a bundle is uninstalled
    }

    /**
     * @see org.apache.felix.prefs.BackingStore#loadAll(org.apache.felix.prefs.BackingStoreManager, java.lang.Long)
     */
    public PreferencesImpl[] loadAll(BackingStoreManager manager, Long bundleId) throws BackingStoreException {
        this.checkAccess();

        PreferencesImpl pref = load(manager, new PreferencesDescription(bundleId, user));

        return pref == null ? new PreferencesImpl[0] : new PreferencesImpl[] { pref };
    }

    /**
     * @see org.apache.felix.prefs.BackingStore#load(org.apache.felix.prefs.BackingStoreManager,
     *      org.apache.felix.prefs.PreferencesDescription)
     */
    public PreferencesImpl load(BackingStoreManager manager, PreferencesDescription desc) throws BackingStoreException {
        this.checkAccess();
        final File file = this.getFile(desc);
        if (file != null && file.exists()) {
            XMLStreamReader xmler = null;
            FileReader fileReader = null;
            try {
                final PreferencesImpl root = new PreferencesImpl(desc, manager);
                XMLInputFactory xmlif = XMLInputFactory.newInstance();
                fileReader = new FileReader(file);
                xmler = xmlif.createXMLStreamReader(fileReader);
                int eventType;
                while (xmler.hasNext()) {
                    eventType = xmler.next();
                    switch (eventType) {
                        case XMLStreamConstants.START_ELEMENT:
                            String key = xmler.getName().getLocalPart();
                            if ("preferences".equals(key)) { //$NON-NLS-1$
                                while (xmler.hasNext()) {
                                    eventType = xmler.next();
                                    switch (eventType) {
                                        // It is a child of the preferences node
                                        case XMLStreamConstants.START_ELEMENT:
                                            this.read(root, xmler, xmler.getName().getLocalPart());
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
                root.getChangeSet().clear();
                return root;
            }

            catch (XMLStreamException e) {
                logger.error("Preferences file is corrupted: ", file.getAbsolutePath()); //$NON-NLS-1$
                throw new BackingStoreException("Unable to load preferences.", e); //$NON-NLS-1$
            } catch (FileNotFoundException e) {
                throw new BackingStoreException("Unable to load preferences.", e); //$NON-NLS-1$
            } finally {
                FileUtil.safeClose(xmler);
                FileUtil.safeClose(fileReader);
            }
        }
        return null;
    }

    /**
     * Get the file for the preferences tree.
     * 
     * @param desc
     * @return the preference file
     */
    protected File getFile(PreferencesDescription desc) {
        Bundle bundle = context.getBundle(desc.getBundleId());
        if (bundle == null) {
            return null;
        }
        if (desc.getIdentifier() != null) {
            return new File(this.prefRootDirectory + File.separator + desc.getIdentifier(), bundle.getSymbolicName()
                + ".xml"); //$NON-NLS-1$
        }
        return new File(this.prefRootDirectory, bundle.getSymbolicName() + ".xml"); //$NON-NLS-1$
    }
}
