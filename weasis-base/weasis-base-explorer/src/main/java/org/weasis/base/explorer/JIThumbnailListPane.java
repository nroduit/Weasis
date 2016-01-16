package org.weasis.base.explorer;

import java.awt.Cursor;
import java.io.File;
import java.util.concurrent.ExecutorService;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;

import org.weasis.core.api.util.ThreadUtil;

public class JIThumbnailListPane extends JScrollPane implements DiskFileList {

    private final JIThumbnailList list;
    private final ExecutorService pool;

    public JIThumbnailListPane(final FileTreeModel model, final OrderedFileList dObjList) {
        this.pool = ThreadUtil.buildNewSingleThreadExecutor("Thumbnail List");
        if (dObjList != null) {
            list = new JIThumbnailList(model, dObjList);
        } else {
            list = new JIThumbnailList(model);
        }
        this.list.addListSelectionListener(new JIListSelectionAdapter());
        this.list.registerListeners();
        this.setViewportView(list);
        this.setAutoscrolls(true);
    }

    public JIThumbnailListPane(FileTreeModel model) {
        this(model, null);
    }

    @Override
    public void loadDirectory(final File dir) {
        try {
            getRootPane().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        } catch (final RuntimeException e) {
        }
        this.pool.execute(new Runnable() {

            @Override
            public void run() {

                ((JIListModel) JIThumbnailListPane.this.list.getModel()).setData();
                JIThumbnailListPane.this.list.setChanged();
                JIThumbnailListPane.this.list.clearChanged();
                try {
                    getRootPane().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                } catch (final RuntimeException e) {
                }
            }
        });
    }

    public void notifyObservers() {
        this.list.notifyObservers(null);
    }

    @Override
    public void notifyObservers(final Object arg) {
        this.list.notifyObservers(arg);
    }

    @Override
    public boolean hasChanged() {
        return this.list.hasChanged();
    }

    @Override
    public JIFileModel getFileListModel() {
        return this.list.getThumbnailListModel();
    }

    public JIExplorerContext getReloadContext() {
        return this.list.getThumbnailListModel().getReloadContext();
    }

    public void setReloadContext(final JIExplorerContext reloadContext) {
        this.list.getThumbnailListModel().setReloadContext(reloadContext);
    }

    final class JIListSelectionAdapter implements javax.swing.event.ListSelectionListener {

        JIListSelectionAdapter() {
        }

        @Override
        public void valueChanged(final ListSelectionEvent e) {
            final Thread runner = new Thread() {

                @Override
                public void run() {
                    Runnable runnable = new Runnable() {

                        @Override
                        public void run() {
                            JIThumbnailListPane.this.list.listValueChanged(e);
                        }
                    };
                    SwingUtilities.invokeLater(runnable);
                }
            };
            runner.start();
        }
    }

    public int[] getSelectedIndices() {
        return this.list.getSelectedIndices();
    }

    public JComponent getRendererComponent() {
        final Object obj = this.list.getCellRenderer();
        return (((obj != null) && (obj instanceof JComponent)) ? (JComponent) obj : null);
    }

}
