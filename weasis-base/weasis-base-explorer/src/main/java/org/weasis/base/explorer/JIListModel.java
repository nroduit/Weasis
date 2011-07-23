/*
 * JIListModel.java
 * 
 * Created on March 27, 2005, 1:48 AM
 */

package org.weasis.base.explorer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import javax.swing.AbstractListModel;
import javax.swing.JList;

import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.service.BundleTools;

public class JIListModel extends AbstractListModel implements JIFileModel {

    /** 
	 *
	 */
    private static final long serialVersionUID = -6616279538578529897L;

    private final OrderedFileList sortedList;

    private JIExplorerContext reloadContext;
    private boolean loading = false;
    private final JList list;

    public JIListModel(final JList list) {
        this.list = list;
        this.sortedList = new OrderedFileList();
    }

    public JIListModel(final JList list, final OrderedFileList delegate) {
        this.list = list;
        this.sortedList = delegate;
    }

    public synchronized boolean loading() {
        return this.loading;
    }

    public void sort(final int type) {
        this.sortedList.sort(type);
        synchronized (this) {
            fireContentsChanged(this, 0, getSize() - 1);
            this.loading = false;
        }
        ;
    }

    public synchronized void notifyAsUpdated(final int index) {
        fireContentsChanged(this, index, index);
    }

    public MediaElement[] copyInto() {
        final List<MediaElement> al = new ArrayList<MediaElement>();
        for (int i = 0; i < this.getSize(); i++) {
            final MediaElement dObj = elementAt(i);
            if (dObj.isLocalFile()) {
                al.add(dObj);
            }
        }
        final MediaElement[] dol = new MediaElement[al.size()];
        return al.toArray(dol);
    }

    public OrderedFileList getDiskObjectList() {
        return this.sortedList;
    }

    public List<MediaElement> getListCopy() {
        return this.sortedList.subList(0, sortedList.size());
    }

    public void setData() {
        if (this.loading) {
            return;
        }

        this.reloadContext = DefaultExplorer.getTreeContext();
        reloadContext.setState(JIExplorerContext.DIRECTORY_STATE);

        if (this.reloadContext.getSelectedDirNodes() != null) {
            synchronized (this) {
                this.loading = true;
            }
            ;
            this.list.getSelectionModel().setValueIsAdjusting(true);
            this.list.requestFocusInWindow();
            getData(this.reloadContext.getSelectedDirNodes());
            reloadContext.setImageCnt(getSize());
            synchronized (this) {
                fireContentsChanged(this, 0, getSize() - 1);
                this.loading = false;
            }
            ;
            this.list.getSelectionModel().setValueIsAdjusting(false);
        }
    }

    public void reload() {
        setData();
    }

    // Gets the table data from the left tree.
    public void getData(final Vector<TreeNode> nodes) {
        final File selectedDir = nodes.elementAt(0).getFile();
        if (selectedDir == null) {
            return;
        }

        final File[] files = selectedDir.listFiles();

        // The selected dir or driver might have no children.
        if (files == null) {
            return;
        }

        Arrays.sort(files, new NameSortingComparator<File>());

        final int fileNum = files.length;

        clear();

        for (int i = 0; i < fileNum; i++) {
            final File file = files[i];

            // StatusBarPanel.getInstance().getLblSrc().setText(
            // "Loading ... " + (int) (100 * (((double) i) / (double) fileNum) + 1) + "%");

            if (!file.isDirectory()) {
                MediaReader media = getMedia(file);
                if (media != null) {
                    MediaElement preview = media.getPreview();
                    // JIThumbnailService.getInstance().getDiskObject(dObj);
                    if (preview != null) {
                        addElement(preview);
                    }
                    if (size() == 1) {
                        this.list.setSelectedIndex(0);
                    }
                }
            }
        }
        return;
    }

    public MediaReader getMedia(File file) {
        if (file != null && file.canRead()) {
            String mime = null;
            try {
                mime = MimeInspector.getMimeType(file);
            } catch (IOException e) {
            }
            if (mime != null) {
                String[] mimeTypes = mime.split(",");
                for (String mimeType : mimeTypes) {
                    // TODO should be in data explorer model
                    Codec codec = BundleTools.getCodec(mimeType, null);
                    if (codec != null) {
                        return codec.getMediaIO(file.toURI(), mimeType, null);
                    }
                }
            }
        }
        return null;
    }

    /**
     * @return the reloadContext
     */
    public final synchronized JIExplorerContext getReloadContext() {
        return this.reloadContext;
    }

    /**
     * @param reloadContext
     *            the reloadContext to set
     */
    public final synchronized void setReloadContext(final JIExplorerContext reloadContext) {
        this.reloadContext = reloadContext;
    }

    /**
     * @return the loading
     */
    public final synchronized boolean isLoading() {
        return this.loading;
    }

    public int getSize() {
        if (this.sortedList == null) {
            return 0;
        }
        return this.sortedList.size();
    }

    public Object getElementAt(final int index) {
        if (this.sortedList == null) {
            return null;
        }

        return ((index >= this.sortedList.size()) || (index < 0)) ? null : this.sortedList.elementAt(index);
    }

    public void setSize(final int newSize) {
        final int oldSize = this.sortedList.size();
        this.sortedList.setSize(newSize);
        if (oldSize > newSize) {
            fireIntervalRemoved(this, newSize, oldSize - 1);
        } else if (oldSize < newSize) {
            fireIntervalAdded(this, oldSize, newSize - 1);
        }
    }

    public int size() {
        return this.sortedList.size();
    }

    public boolean isEmpty() {
        return this.sortedList.isEmpty();
    }

    public boolean contains(final MediaElement elem) {
        return this.sortedList.contains(elem);
    }

    public int indexOf(final MediaElement elem) {
        return this.sortedList.indexOf(elem);
    }

    public int indexOf(final MediaElement elem, final int index) {
        return this.sortedList.indexOf(elem, index);
    }

    public int lastIndexOf(final MediaElement elem) {
        return this.sortedList.lastIndexOf(elem);
    }

    public int lastIndexOf(final MediaElement elem, final int index) {
        return this.sortedList.lastIndexOf(elem, index);
    }

    public MediaElement elementAt(final int index) {
        return this.sortedList.elementAt(index);
    }

    public MediaElement firstElement() {
        return this.sortedList.firstElement();
    }

    public MediaElement lastElement() {
        return this.sortedList.lastElement();
    }

    public void MediaElement(final MediaElement obj, final int index) {
        this.sortedList.setElementAt(obj, index);
        fireContentsChanged(this, index, index);
    }

    public void insertElementAt(final MediaElement obj, final int index) {
        this.sortedList.insertElementAt(obj, index);
        fireIntervalAdded(this, index, index);
    }

    public void addElement(final MediaElement obj) {
        final int index = this.sortedList.size();
        this.sortedList.addElement(obj);
        fireIntervalAdded(this, index, index);
    }

    public boolean removeElement(final MediaElement obj) {
        final int index = indexOf(obj);
        final boolean rv = this.sortedList.removeElement(obj);
        if (index >= 0) {
            fireIntervalRemoved(this, index, index);
        }
        return rv;
    }

    @Override
    public String toString() {
        return this.sortedList.toString();
    }

    public MediaElement[] toArray() {
        final MediaElement[] rv = new MediaElement[this.sortedList.size()];
        this.sortedList.copyInto(rv);
        return rv;
    }

    public MediaElement get(final int index) {
        return this.sortedList.elementAt(index);
    }

    public MediaElement set(final int index, final MediaElement element) {
        final MediaElement rv = this.sortedList.elementAt(index);
        this.sortedList.setElementAt(element, index);
        fireContentsChanged(this, index, index);
        return rv;
    }

    public void add(final int index, final MediaElement element) {
        this.sortedList.insertElementAt(element, index);
        fireIntervalAdded(this, index, index);
    }

    public MediaElement remove(final int index) {
        final MediaElement rv = this.sortedList.elementAt(index);
        this.sortedList.removeElementAt(index);
        fireIntervalRemoved(this, index, index);
        return rv;
    }

    /**
     * Removes all of the elements from this list. The list will be empty after this call returns (unless it throws an
     * exception).
     */
    public void clear() {
        final int index1 = this.sortedList.size() - 1;
        this.sortedList.removeAllElements();
        if (index1 >= 0) {
            fireIntervalRemoved(this, 0, index1);
        }
    }

}
