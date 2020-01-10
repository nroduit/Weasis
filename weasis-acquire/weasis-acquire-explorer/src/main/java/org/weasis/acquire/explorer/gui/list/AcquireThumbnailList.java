/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.explorer.gui.list;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.Messages;
import org.weasis.acquire.explorer.gui.control.ImportPanel;
import org.weasis.acquire.explorer.gui.dialog.AcquireImportDialog;
import org.weasis.base.explorer.JIThumbnailCache;
import org.weasis.base.explorer.list.AbstractThumbnailList;
import org.weasis.base.explorer.list.IThumbnailModel;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.ui.util.DefaultAction;

@SuppressWarnings("serial")
public class AcquireThumbnailList<E extends MediaElement> extends AbstractThumbnailList<E> {

    private AcquireThumbnailListPane<E> mainPanel;

    public AcquireThumbnailList(JIThumbnailCache thumbCache) {
        super(thumbCache);
    }

    public AcquireThumbnailListPane<E> getMainPanel() {
        return mainPanel;
    }

    public void setMainPanel(AcquireThumbnailListPane<E> mainPanel) {
        this.mainPanel = mainPanel;
    }

    @Override
    public IThumbnailModel<E> newModel() {
        return new AcquireThumbnailModel<>(this, thumbCache);
    }

    @Override
    public JPopupMenu buidContexMenu(MouseEvent e) {
        ImportPanel importPanel = AcquireManager.getInstance().getAcquireExplorer().getImportPanel();
        List<ImageElement> medias = AcquireManager.toImageElement(getSelected(e));
        if (!medias.isEmpty() && !importPanel.isLoading()) {
            JPopupMenu popupMenu = new JPopupMenu();

            popupMenu
                .add(new JMenuItem(new DefaultAction(Messages.getString("AcquireThumbnailList.import_sel"), event -> { //$NON-NLS-1$
                    AcquireImportDialog dialog = new AcquireImportDialog(importPanel, medias);
                    JMVUtils.showCenterScreen(dialog, WinUtil.getParentWindow(mainPanel));
                })));

            return popupMenu;
        }
        return null;
    }

    @Override
    public void mouseClickedEvent(MouseEvent e) {
        if (e.getClickCount() == 2) { // Manage double click
            ImportPanel importPanel = AcquireManager.getInstance().getAcquireExplorer().getImportPanel();
            List<ImageElement> medias = AcquireManager.toImageElement(getSelected(e));
            if (!medias.isEmpty() && !importPanel.isLoading()) {
                AcquireImportDialog dialog = new AcquireImportDialog(importPanel, medias);
                JMVUtils.showCenterScreen(dialog, WinUtil.getParentWindow(mainPanel));
            }
        }
    }

    @Override
    public void jiThumbnailKeyPressed(KeyEvent e) {

        switch (e.getKeyCode()) {
            case KeyEvent.VK_PAGE_DOWN:
                nextPage(e);
                break;
            case KeyEvent.VK_PAGE_UP:
                lastPage(e);
                break;
            case KeyEvent.VK_ENTER:
                ImportPanel importPanel = AcquireManager.getInstance().getAcquireExplorer().getImportPanel();
                final List<ImageElement> selected = AcquireManager.toImageElement(mainPanel.getSelectedValuesList());
                if (!selected.isEmpty() && !importPanel.isLoading()) {
                    AcquireImportDialog dialog = new AcquireImportDialog(importPanel, selected);
                    JMVUtils.showCenterScreen(dialog, WinUtil.getParentWindow(mainPanel));
                }
                e.consume();
                break;
        }
    }
}
