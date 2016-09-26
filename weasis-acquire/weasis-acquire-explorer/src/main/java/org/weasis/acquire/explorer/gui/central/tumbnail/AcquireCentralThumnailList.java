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
package org.weasis.acquire.explorer.gui.central.tumbnail;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;

import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.core.bean.Serie;
import org.weasis.acquire.explorer.gui.central.AcquireTabPanel;
import org.weasis.acquire.explorer.gui.central.component.SerieButton;
import org.weasis.acquire.explorer.gui.dialog.AcquireNewSerieDialog;
import org.weasis.base.explorer.list.AThumbnailList;
import org.weasis.base.explorer.list.IThumbnailModel;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.image.ImageOpNode;
import org.weasis.core.api.image.RotationOp;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.ui.util.DefaultAction;

@SuppressWarnings({ "serial" })
public class AcquireCentralThumnailList<E extends MediaElement> extends AThumbnailList<E> {

    private AcquireTabPanel acquireTabPanel;

    public AcquireCentralThumnailList() {
        super();
    }

    @Override
    public IThumbnailModel<E> newModel() {
        return new AcquireCentralThumbnailModel<>(this);
    }

    public void setAcquireTabPanel(AcquireTabPanel acquireTabPanel) {
        this.acquireTabPanel = acquireTabPanel;
    }

    public SerieButton getSelectedSerie() {
        if (acquireTabPanel != null) {
            return acquireTabPanel.getSelected();
        }
        return null;
    }

    @Override
    public void registerDragListeners() {
        // No drag capabilities
    }

    @Override
    public void openSelection() {
        for (E s : getSelectedValuesList()) {
            openSelection(Arrays.asList(s), true, true, false);
        }
    }

    @Override
    public JPopupMenu buidContexMenu(final MouseEvent e) {
        final List<E> medias = getSelected(e);

        if (!medias.isEmpty()) {
            JPopupMenu popupMenu = new JPopupMenu();

            popupMenu.add(new JMenuItem(new DefaultAction("Edit", event -> openSelection())));

            popupMenu.add(new JMenuItem(new DefaultAction("Remove", event -> {
                clearSelection();
                AcquireCentralThumnailList.this.acquireTabPanel.removeElements(AcquireManager.toImageElement(medias));
                repaint();
            })));

            JMenu moveToMenu = new JMenu("Move to...");

            moveToOther(moveToMenu, medias);
            moveToMenu.addSeparator();
            moveToExisting(moveToMenu, medias);
            if (moveToMenu.getItemCount() > 3) {
                moveToMenu.addSeparator();
            }
            moveToNewSerie(moveToMenu, medias);

            JMenu operationsMenu = new JMenu("Operations...");
            operationRotate(operationsMenu, medias, "Rotate +90°", 90);
            operationRotate(operationsMenu, medias, "Rotate -90°", 270);

            popupMenu.add(moveToMenu);
            popupMenu.add(operationsMenu);

            return popupMenu;
        }

        return null;

    }

    private void moveToExisting(JMenu moveToMenu, final List<E> medias) {
        AcquireCentralThumnailList.this.acquireTabPanel.getSeries().stream().forEach(s -> {
            if (!s.equals(AcquireCentralThumnailList.this.acquireTabPanel.getSelected().getSerie())
                && !s.equals(Serie.DEFAULT_SERIE)) {
                moveToMenu.add(new JMenuItem(new DefaultAction(s.getDisplayName(), event -> {
                    AcquireCentralThumnailList.this.acquireTabPanel.moveElements(s,
                        AcquireManager.toImageElement(medias));
                    repaint();

                })));
            }
        });
    }

    private void moveToOther(JMenu moveToMenu, final List<E> medias) {
        moveToMenu.add(new JMenuItem(new DefaultAction(Serie.DEFAULT_SERIE_NAME, event -> {
            AcquireCentralThumnailList.this.acquireTabPanel.moveElements(Serie.DEFAULT_SERIE,
                AcquireManager.toImageElement(medias));
            repaint();
        })));
    }

    private void moveToNewSerie(JMenu moveToMenu, final List<E> medias) {
        moveToMenu.add(new JMenuItem(new DefaultAction("New Serie", event -> {
            JDialog dialog = new AcquireNewSerieDialog(AcquireCentralThumnailList.this.acquireTabPanel,
                AcquireManager.toImageElement(medias));
            JMVUtils.showCenterScreen(dialog, AcquireCentralThumnailList.this.acquireTabPanel);
            repaint();
        })));
    }

    private void operationRotate(JMenu operationsMenu, final List<E> medias, String label, final int angle) {
        operationsMenu.add(new JMenuItem(new DefaultAction(label,
            event -> medias.stream().filter(ImageElement.class::isInstance).map(ImageElement.class::cast).forEach(i -> {
                AcquireImageInfo info = AcquireManager.findByImage(i);
                
                int change = (info.getNextValues().getFullRotation() + angle >= 0)
                    ? info.getNextValues().getRotation() + angle : info.getNextValues().getRotation() + 360 + angle;
                info.getNextValues().setRotation(change);

                ImageOpNode node = info.getPreProcessOpManager().getNode(RotationOp.OP_NAME);
                if (node == null) {
                    node = new RotationOp();
                    info.addPreProcessImageOperationAction(node);
                } else {
                    node.clearIOCache();
                }
                node.setParam(RotationOp.P_ROTATE, info.getNextValues().getFullRotation());
            }))));
    }

    @Override
    public void mouseClickedEvent(MouseEvent e) {
        if (e.getClickCount() == 2) {
            openSelection();
        }
    }

    public void updateAll() {
        AcquireManager.groupBySeries().forEach(acquireTabPanel::updateSerie);
        acquireTabPanel.clearUnusedSeries(AcquireManager.getBySeries());
        acquireTabPanel.revalidate();
        acquireTabPanel.repaint();
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
            case KeyEvent.VK_DELETE:
                List<E> selected = getSelectedValuesList();
                if (!selected.isEmpty()) {
                    List<ImageElement> list = AcquireManager.toImageElement(selected);
                    clearSelection();
                    AcquireCentralThumnailList.this.acquireTabPanel.removeElements(list);
                }
                break;
        }
    }
}
