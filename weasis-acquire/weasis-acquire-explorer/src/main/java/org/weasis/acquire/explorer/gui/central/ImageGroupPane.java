package org.weasis.acquire.explorer.gui.central;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JMenu;

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.gui.AcquireToolBar;
import org.weasis.base.viewer2d.EventManager;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.core.ui.util.WtoolBar;

public class ImageGroupPane extends ViewerPlugin<ImageElement> {
    private static final long serialVersionUID = -1446534853755678224L;

    public final List<Toolbar> toolBar = Collections.synchronizedList(new ArrayList<Toolbar>(1));

    public final AcquireTabPanel tabbedPane = new AcquireTabPanel();

    // private static Comparator<ImageItem> nameComparator = new ImageItem.NameComparator();
    // private static Comparator<ImageItem> dateComparator = new ImageItem.DateComparator();

    public ImageGroupPane(String PluginName) {
        super(PluginName);

        // Add standard toolbars
        final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
        String bundleName = context.getBundle().getSymbolicName();
        String componentName = InsertableUtil.getCName(this.getClass());
        String key = "enable"; //$NON-NLS-1$

        if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
            InsertableUtil.getCName(AcquireToolBar.class), key, true)) {
            toolBar.add(new AcquireToolBar<ImageElement>(10));
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
    }

    @Override
    public void removeSeries(MediaSeries<ImageElement> series) {
    }

    @Override
    public JMenu fillSelectedPluginMenu(JMenu menu) {
        return null;
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
        }
    }

    @Override
    public void setSelectedAndGetFocus() {
        super.setSelectedAndGetFocus();
        updateAll();
    }

    private void updateAll() {
        AcquireManager.groupBySeries().forEach((k, v) -> {
            tabbedPane.updateSerie(k, v);
        });
        tabbedPane.clearUnusedSeries(AcquireManager.getBySeries());
        tabbedPane.repaint();
    }
}
