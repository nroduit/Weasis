/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.acquire.explorer;

import java.awt.BorderLayout;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.util.Collections;
import java.util.List;

import javax.swing.Action;
import javax.swing.Icon;

import org.dcm4che3.data.Tag;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.gui.central.ImageGroupPane;
import org.weasis.acquire.explorer.gui.control.BrowsePanel;
import org.weasis.acquire.explorer.gui.control.ImportPanel;
import org.weasis.acquire.explorer.gui.list.AcquireThumbnailListPane;
import org.weasis.acquire.explorer.media.FileSystemDrive;
import org.weasis.acquire.explorer.media.MediaSource;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.dicom.codec.TagD;

import bibliothek.gui.dock.common.CLocation;
import bibliothek.gui.dock.common.mode.ExtendedMode;

public class AcquisitionView extends PluginTool implements DataExplorerView {
    private static final long serialVersionUID = 661412595299625116L;

    protected static final Logger LOGGER = LoggerFactory.getLogger(AcquisitionView.class);

    public static final String BUTTON_NAME = "dicomizer"; //$NON-NLS-1$
    public static final String NAME = "Acquisition";
    public static final String P_LAST_DIR = "last.dir"; //$NON-NLS-1$
    public static final String PREFERENCE_NODE = "importer"; //$NON-NLS-1$

    public static final int MEDIASOURCELIST_MAX = 5;

    private MediaSource systemDrive;

    private final ImageGroupPane centralPane = new ImageGroupPane("Albums");

    private AcquireThumbnailListPane<MediaElement> acquireThumbnailListPane;
    private BrowsePanel browsePanel;
    private ImportPanel importPanel;

    public AcquisitionView() {
        super(BUTTON_NAME, NAME, POSITION.WEST, ExtendedMode.NORMALIZED, PluginTool.Type.EXPLORER, 20);
        setDockableWidth(400);
        browsePanel = new BrowsePanel(this);
        acquireThumbnailListPane = new AcquireThumbnailListPane<>(centralPane);
        importPanel = new ImportPanel(acquireThumbnailListPane, centralPane);

        setLayout(new BorderLayout(0, 0));
        add(acquireThumbnailListPane, BorderLayout.CENTER);
        add(browsePanel, BorderLayout.NORTH);
        add(importPanel, BorderLayout.SOUTH);

        this.acquireThumbnailListPane.loadDirectory(systemDrive.getID());

        String ptName = TagD.getDicomPersonName(TagD.getTagValue(AcquireManager.GLOBAL, Tag.PatientName, String.class));
        if(!org.weasis.core.api.util.StringUtil.hasLength(ptName)){
            ptName = TagD.NO_VALUE;
        }
        centralPane.setPluginName(ptName);
        
        // Remove dropping capabilities in the central area (limit to import from browse panel)
        UIManager.MAIN_AREA.getComponent().setTransferHandler(null);
    }

    void saveLastPath() {
        if (systemDrive != null) {
            File dir = new File(systemDrive.getID());
            Preferences prefs =
                BundlePreferences.getDefaultPreferences(FrameworkUtil.getBundle(this.getClass()).getBundleContext());
            if (prefs != null) {
                Preferences p = prefs.node(PREFERENCE_NODE);
                BundlePreferences.putStringPreferences(p, P_LAST_DIR, dir.getAbsolutePath());
            }
        }
    }

    public void initImageGroupPane() {
        centralPane.getDockable().setCloseable(false);
        centralPane.showDockable();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        // Do nothing
    }

    @Override
    public Icon getIcon() {
        return null;
    }

    @Override
    public String getUIName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public void dispose() {
        super.closeDockable();
    }

    @Override
    public DataExplorerModel getDataExplorerModel() {
        return null;
    }

    @Override
    public List<Action> getOpenImportDialogAction() {
        return Collections.emptyList();
    }

    @Override
    public List<Action> getOpenExportDialogAction() {
        return Collections.emptyList();
    }

    @Override
    public void importFiles(File[] files, boolean recursive) {
        // Do nothing
    }

    @Override
    public boolean canImportFiles() {
        return false;
    }

    @Override
    protected void changeToolWindowAnchor(CLocation clocation) {
        // Do nothing
    }

    public MediaSource getSystemDrive() {
        return systemDrive;
    }

    public void setSystemDrive(MediaSource systemDrive) {
        this.systemDrive = systemDrive;
    }

    public ImageGroupPane getCentralPane() {
        return centralPane;
    }

    public void applyNewPath(String newRootPath) {
        setSystemDrive(new FileSystemDrive(newRootPath));

        browsePanel.getMediaSourceList().insertItem(0, systemDrive);
        if (browsePanel.getMediaSourceList().getSize() >= MEDIASOURCELIST_MAX) {
            browsePanel.getMediaSourceList().removeItem(MEDIASOURCELIST_MAX - 1);
        }
        browsePanel.getMediaSourceSelectionCombo().setSelectedItem(systemDrive);
        loadSystemDrive();
    }

    public void loadSystemDrive() {
        acquireThumbnailListPane.loadDirectory(systemDrive.getID());
    }
}
