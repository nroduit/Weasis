package org.weasis.base.explorer;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;

import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.core.ui.util.WtoolBar;

public class JIThumbnailListPane extends ViewerPlugin implements DiskFileList {

    /**
	 *
	 */
    private static final long serialVersionUID = 3672478063192038911L;

    public static final String NAME = "Media List";
    public static final Icon ICON = new ImageIcon(JIThumbnailListPane.class.getResource("/icon/16x16/folder.png"));

    private final JIThumbnailList list;
    private final ExecutorService pool;
    private final JScrollPane scrollPane;

    public JIThumbnailListPane(final FileTreeModel model, final OrderedFileList dObjList) {
        super(NAME, ICON, null);
        this.pool = Executors.newFixedThreadPool(2);
        this.scrollPane = new JScrollPane();
        if (dObjList != null) {
            list = new JIThumbnailList(model, scrollPane, dObjList);
        } else {
            list = new JIThumbnailList(model, scrollPane);
        }
        this.list.addListSelectionListener(new JIListSelectionAdapter());
        this.scrollPane.setViewportView(list);
        this.setLayout(new BorderLayout());
        this.add(scrollPane, BorderLayout.CENTER);
        this.scrollPane.setAutoscrolls(true);
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
                // JIThumbnailListPane.this.list.notifyStatusBar(JIObservable.DIRECTORY_SIZE);
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

    @Override
    public void close() {
        // TODO Auto-generated method stub

    }

    @Override
    public JMenu fillSelectedPluginMenu(JMenu menu) {
        if (menu != null) {
            menu.removeAll();
            menu.setText(NAME);
        }
        return menu;
    }

    @Override
    public List<Toolbar> getToolBar() {
        return null;
    }

    @Override
    public PluginTool[] getToolPanel() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setSelected(boolean selected) {
        // TODO Auto-generated method stub

    }

    @Override
    public WtoolBar getStatusBar() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void addSeries(MediaSeries sequence) {
        // TODO Auto-generated method stub

    }

    @Override
    public void removeSeries(MediaSeries sequence) {
        // TODO Auto-generated method stub

    }

    @Override
    public List<MediaSeries> getOpenSeries() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<Action> getExportActions() {
        // TODO Auto-generated method stub
        return null;
    }

}
