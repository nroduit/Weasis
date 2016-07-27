package org.weasis.acquire.explorer.gui.list;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.gui.dialog.AcquireImportDialog;
import org.weasis.base.explorer.list.AThumbnailList;
import org.weasis.base.explorer.list.IThumbnailModel;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;

@SuppressWarnings("serial")
public class AcquireThumbnailList<E extends MediaElement<?>> extends AThumbnailList<E> {

    private AcquireThumbnailListPane<E> mainPanel;

    public AcquireThumbnailList() {
        super();
    }

    public AcquireThumbnailListPane<E> getMainPanel() {
        return mainPanel;
    }

    public void setMainPanel(AcquireThumbnailListPane<E> mainPanel) {
        this.mainPanel = mainPanel;
    }

    @Override
    public IThumbnailModel<E> newModel() {
        return new AcquireThumbnailModel<>(this);
    }

    @Override
    public JPopupMenu buidContexMenu(MouseEvent e) {
        List<ImageElement> medias = AcquireManager.toImageElement(getSelected(e));
        if (!medias.isEmpty()) {
            JPopupMenu popupMenu = new JPopupMenu();

            popupMenu.add(new JMenuItem(new AbstractAction("Import selection") {

                @Override
                public void actionPerformed(ActionEvent e) {
                    AcquireImportDialog dialog = new AcquireImportDialog(AcquireThumbnailList.this.mainPanel, medias);
                    JMVUtils.showCenterScreen(dialog, WinUtil.getParentWindow(mainPanel));
                }
            }));

            return popupMenu;
        }
        return null;
    }

    @Override
    public void mouseClickedEvent(MouseEvent e) {
        if (e.getClickCount() == 2) { // Manage double click
            List<ImageElement> medias = AcquireManager.toImageElement(getSelected(e));
            if (!medias.isEmpty()) {
                AcquireImportDialog dialog = new AcquireImportDialog(mainPanel, medias);
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
                final List<ImageElement> selected = AcquireManager.toImageElement(mainPanel.getSelectedValuesList());
                if (!selected.isEmpty()) {
                    AcquireImportDialog dialog = new AcquireImportDialog(mainPanel, selected);
                    JMVUtils.showCenterScreen(dialog, WinUtil.getParentWindow(mainPanel));
                }
                e.consume();
                break;
        }
    }
}
