/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.explorer.gui.central;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.Messages;
import org.weasis.acquire.explorer.gui.AcquireToolBar;
import org.weasis.base.explorer.JIThumbnailCache;
import org.weasis.base.viewer2d.EventManager;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.util.ToolBarContainer;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.core.ui.util.WtoolBar;

@SuppressWarnings("serial")
public class ImageGroupPane extends ViewerPlugin<ImageElement> {

    public final List<Toolbar> toolBar = Collections.synchronizedList(new ArrayList<Toolbar>(1));

    public final AcquireTabPanel tabbedPane;

    public ImageGroupPane(String pluginName, JIThumbnailCache thumbCache) {
        super(pluginName);
        this.tabbedPane = new AcquireTabPanel(thumbCache);
        // Add standard toolbars
        final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
        String bundleName = context.getBundle().getSymbolicName();
        String componentName = InsertableUtil.getCName(this.getClass());
        String key = "enable"; //$NON-NLS-1$

        if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
            InsertableUtil.getCName(AcquireToolBar.class), key, true)) {
            toolBar.add(ToolBarContainer.EMPTY);
        }

        init();
    }

    private void init() {
        setLayout(new BorderLayout());
        add(tabbedPane, BorderLayout.CENTER);
    }

    @Override
    public List<MediaSeries<ImageElement>> getOpenSeries() {
        return null;
    }

    @Override
    public void addSeries(MediaSeries<ImageElement> series) {
        // Do nothing
    }

    @Override
    public void removeSeries(MediaSeries<ImageElement> series) {
        // Do nothing
    }

    @Override
    public JMenu fillSelectedPluginMenu(JMenu menuRoot) {
        if (menuRoot != null) {
            menuRoot.removeAll();
            // JMenuItem item = new JMenuItem("Build a new study");
            // item.addActionListener(e -> AcquireManager.GLOBAL.setTag(TagD.get(Tag.StudyInstanceUID),
            // UIDUtils.createUID()));
            // menuRoot.add(item);
            JMenuItem item2 = new JMenuItem(Messages.getString("ImageGroupPane.rm_all")); //$NON-NLS-1$
            item2.addActionListener(e -> AcquireManager.getInstance().removeAllImages());
            menuRoot.add(item2);
        }
        return menuRoot;
    }

    @Override
    public synchronized List<Toolbar> getToolBar() {
        return toolBar;
    }

    @Override
    public WtoolBar getStatusBar() {
        return null;
    }

    @Override
    public List<DockableTool> getToolPanel() {
        return null;
    }

    @Override
    public void setSelected(boolean selected) {
        if (selected) {
            EventManager.getInstance()
                .fireSeriesViewerListeners(new SeriesViewerEvent(this, null, null, EVENT.SELECT_VIEW));
            tabbedPane.refreshGUI();
        }
    }

    @Override
    public void setSelectedAndGetFocus() {
        super.setSelectedAndGetFocus();
        updateAll();
    }

    private void updateAll() {
        tabbedPane.clearUnusedSeries(AcquireManager.getBySeries());
        tabbedPane.setSelected(tabbedPane.getSelected());
        tabbedPane.revalidate();
        tabbedPane.repaint();
    }
}
