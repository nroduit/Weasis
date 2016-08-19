package org.weasis.acquire.explorer.gui.central.tumbnail;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.AbstractAction;
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
    public JPopupMenu buidContexMenu(final MouseEvent e) {
        final List<E> medias = getSelected(e);

        if (!medias.isEmpty()) {
            JPopupMenu popupMenu = new JPopupMenu();

            popupMenu.add(new JMenuItem(new AbstractAction("Remove") {

                @Override
                public void actionPerformed(ActionEvent e) {
                    AcquireCentralThumnailList.this.acquireTabPanel
                        .removeElements(AcquireManager.toImageElement(medias));
                    repaint();
                }
            }));

            JMenu moveToMenu = new JMenu("Move to...");

            moveToOther(moveToMenu, medias);
            moveToMenu.addSeparator();
            moveToExisting(moveToMenu, medias);
            moveToMenu.addSeparator();
            moveToByDate(moveToMenu, medias);
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

    private void moveToByDate(JMenu moveToMenu, final List<E> medias) {
        moveToMenu.add(new JMenuItem(new AbstractAction("Related date serie") {

            @Override
            public void actionPerformed(ActionEvent e) {
                AcquireCentralThumnailList.this.acquireTabPanel
                    .moveElementsByDate(AcquireManager.toImageElement(medias));
                repaint();
            }
        }));
    }

    private void moveToExisting(JMenu moveToMenu, final List<E> medias) {
        AcquireCentralThumnailList.this.acquireTabPanel.getSeries().stream().forEach(s -> {
            if (!s.equals(AcquireCentralThumnailList.this.acquireTabPanel.getSelected().getSerie())
                && !s.equals(Serie.DEFAULT_SERIE)) {
                moveToMenu.add(new JMenuItem(new AbstractAction(s.getDisplayName()) {
                    private static final long serialVersionUID = 6492377383458373875L;

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        AcquireCentralThumnailList.this.acquireTabPanel.moveElements(s,
                            AcquireManager.toImageElement(medias));
                        repaint();
                    }
                }));
            }
        });
    }

    private void moveToOther(JMenu moveToMenu, final List<E> medias) {
        moveToMenu.add(new JMenuItem(new AbstractAction(Serie.DEFAULT_SERIE_NAME) {
            private static final long serialVersionUID = -6470108225311449444L;

            @Override
            public void actionPerformed(ActionEvent e) {
                AcquireCentralThumnailList.this.acquireTabPanel.moveElements(Serie.DEFAULT_SERIE,
                    AcquireManager.toImageElement(medias));
                repaint();
            }
        }));
    }

    private void moveToNewSerie(JMenu moveToMenu, final List<E> medias) {
        moveToMenu.add(new JMenuItem(new AbstractAction("New Serie") {
            @Override
            public void actionPerformed(ActionEvent e) {
                JDialog dialog = new AcquireNewSerieDialog(AcquireCentralThumnailList.this.acquireTabPanel,
                    AcquireManager.toImageElement(medias));
                JMVUtils.showCenterScreen(dialog, AcquireCentralThumnailList.this.acquireTabPanel);
                repaint();
            }
        }));
    }

    private void operationRotate(JMenu operationsMenu, final List<E> medias, String label, int angle) {
        operationsMenu.add(new JMenuItem(new AbstractAction(label) {

            @Override
            public void actionPerformed(ActionEvent e) {
                medias.stream().filter(m -> m instanceof ImageElement).map(ImageElement.class::cast).forEach(i -> {
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
                });
            }
        }));
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
        List<E> selected = getSelectedValuesList();

        switch (e.getKeyCode()) {
            case KeyEvent.VK_PAGE_DOWN:
                nextPage(e);
                break;
            case KeyEvent.VK_PAGE_UP:
                lastPage(e);
                break;
            case KeyEvent.VK_DELETE:
                if (!selected.isEmpty()) {
                    AcquireCentralThumnailList.this.acquireTabPanel
                        .removeElements(AcquireManager.toImageElement(selected));
                }
                break;
        }
    }
}
