package org.weasis.base.explorer;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.explorer.model.TreeModelNode;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.service.BundleTools;

public class FileTreeModel extends DefaultTreeModel implements DataExplorerModel {

    private final PropertyChangeSupport propertyChange;

    public FileTreeModel(TreeNode root) {
        this(root, false);

    }

    public FileTreeModel(TreeNode root, boolean asksAllowsChildren) {
        super(root, asksAllowsChildren);
        propertyChange = new PropertyChangeSupport(this);
    }

    public void addPropertyChangeListener(PropertyChangeListener propertychangelistener) {
        propertyChange.addPropertyChangeListener(propertychangelistener);
    }

    public void removePropertyChangeListener(PropertyChangeListener propertychangelistener) {
        propertyChange.removePropertyChangeListener(propertychangelistener);

    }

    public void firePropertyChange(final ObservableEvent event) {
        if (event == null) {
            throw new NullPointerException();
        }
        if (SwingUtilities.isEventDispatchThread()) {
            propertyChange.firePropertyChange(event);
        } else {
            SwingUtilities.invokeLater(new Runnable() {

                public void run() {
                    propertyChange.firePropertyChange(event);
                }
            });
        }
    }

    @Override
    public List<Codec> getCodecPlugins() {
        return BundleTools.CODEC_PLUGINS;
    }

    @Override
    public TreeModelNode getTreeModelNodeForNewPlugin() {
        return null;
    }

    @Override
    public boolean applySplittingRules(Series original, MediaElement media) {
        return false;
    }

}
