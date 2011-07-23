package org.weasis.base.explorer;

import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

import org.weasis.core.api.gui.util.AbstractProperties;
import org.weasis.core.api.media.data.MediaElement;

public class OrderedFileList extends Vector<MediaElement> {

    /**
	 *
	 */
    private static final long serialVersionUID = -6210090952004948225L;
    private static final int SORT_BY_PATH = 4;
    private static final int SORT_BY_DATE = 3;
    private static final int SORT_BY_SIZE = 2;
    private static final int SORT_BY_TYPE = 1;
    private static final int SORT_BY_NAME = 0;

    private static final Vector<Comparator<MediaElement>> comparators = new Vector<Comparator<MediaElement>>(5);

    static {
        comparators.add(SORT_BY_NAME, new SortName());
        comparators.add(SORT_BY_TYPE, new SortType());
        comparators.add(SORT_BY_SIZE, new SortSize());
        comparators.add(SORT_BY_DATE, new SortDate());
        comparators.add(SORT_BY_PATH, new SortPath());
    };

    private int currentIndex = 0;

    public OrderedFileList() {
        super();
    }

    public OrderedFileList(final MediaElement[] list) {
        super(list.length);
        this.elementData = list;
        this.setSize(this.elementData.length);
    }

    public OrderedFileList(final MediaElement[] list, final int index) {
        super(list.length);
        this.elementData = list;
        this.setSize(this.elementData.length);
        this.currentIndex = index;
    }

    @Override
    public final boolean isEmpty() {
        return (size() > 0) ? false : true;
    }

    protected final int nextImageIndex() {
        return (this.currentIndex < size() - 1) ? ++this.currentIndex : (this.currentIndex = 0);
    }

    protected final int previousImageIndex() {
        return ((this.currentIndex > 0) ? --this.currentIndex : (this.currentIndex = size() - 1));
    }

    public final MediaElement currentImage() {
        if (isEmpty()) {
            return null;
        }
        return elementAt(this.currentIndex);
    }

    public final MediaElement previousImage() {
        if (isEmpty()) {
            return null;
        }
        return elementAt(previousImageIndex());
    }

    public final MediaElement nextImage() {
        if (isEmpty()) {
            return null;
        }
        return elementAt(nextImageIndex());
    }

    public final MediaElement nextImageFile(final int dir) {
        // 0 = forward, 1 = backward, 2 = random
        switch (dir) {
            case 2:
                return randImage();
            case 1:
                return previousImage();
            default:
                return nextImage();
        }
    }

    public final MediaElement randImage() {
        this.currentIndex = (int) Math.round((size() - 1) * Math.random());
        return elementAt(this.currentIndex);
    }

    public final void sort(final int type) {
        Collections.sort(this, comparators.elementAt(type > 4 ? 0 : type));
    }

    /**
     * @return the currentIndex
     */
    public final synchronized int getCurrentIndex() {
        return this.currentIndex;
    }

    /**
     * @param currentIndex
     *            the currentIndex to set
     */
    public final synchronized void setCurrentIndex(final int currentIndex) {
        this.currentIndex = currentIndex;
    }

}

class SortDate implements Comparator<MediaElement> {

    public int compare(final MediaElement a, final MediaElement b) {
        if (a == null) {
            return (AbstractProperties.isThumbnailSortDesend()) ? -1 : 1;
        }
        if (b == null) {
            return (AbstractProperties.isThumbnailSortDesend()) ? 1 : -1;
        }
        final int result =
            (!AbstractProperties.isThumbnailSortDesend()) ? (int) (a.getLastModified() - b.getLastModified())
                : (int) (b.getLastModified() - a.getLastModified());

        return (result != 0) ? result : ((!AbstractProperties.isThumbnailSortDesend()) ? a.getFile().getAbsolutePath()
            .compareTo(b.getFile().getAbsolutePath()) : b.getFile().getAbsolutePath().compareTo(
            a.getFile().getAbsolutePath()));
    }
}

class SortSize implements Comparator<MediaElement> {

    public int compare(final MediaElement a, final MediaElement b) {
        if (a == null) {
            return (AbstractProperties.isThumbnailSortDesend()) ? -1 : 1;
        }
        if (b == null) {
            return (AbstractProperties.isThumbnailSortDesend()) ? 1 : -1;
        }
        final int result =
            (!AbstractProperties.isThumbnailSortDesend()) ? (int) (a.getLength() - b.getLength()) : (int) (b
                .getLength() - a.getLength());

        return (result != 0) ? result : ((!AbstractProperties.isThumbnailSortDesend()) ? a.getFile().getAbsolutePath()
            .compareTo(b.getFile().getAbsolutePath()) : b.getFile().getAbsolutePath().compareTo(
            a.getFile().getAbsolutePath()));
    }
}

class SortType implements Comparator<MediaElement> {

    public int compare(final MediaElement a, final MediaElement b) {
        if (a == null) {
            return (AbstractProperties.isThumbnailSortDesend()) ? -1 : 1;
        }
        if (b == null) {
            return (AbstractProperties.isThumbnailSortDesend()) ? 1 : -1;
        }
        return (!AbstractProperties.isThumbnailSortDesend()) ? (a.getMimeType() + a.getName()).compareToIgnoreCase((b
            .getMimeType() + b.getName())) : (b.getMimeType() + b.getName()).compareToIgnoreCase((a.getMimeType() + a
            .getName()));
    }
}

class SortName implements Comparator<MediaElement> {

    public int compare(final MediaElement a, final MediaElement b) {
        if (a == null) {
            return (AbstractProperties.isThumbnailSortDesend()) ? -1 : 1;
        }
        if (b == null) {
            return (AbstractProperties.isThumbnailSortDesend()) ? 1 : -1;
        }
        return (!AbstractProperties.isThumbnailSortDesend()) ? a.getName().compareToIgnoreCase(b.getName()) : b
            .getName().compareToIgnoreCase(a.getName());
    }
}

class SortPath implements Comparator<MediaElement> {

    public int compare(final MediaElement a, final MediaElement b) {
        if (a == null) {
            return (AbstractProperties.isThumbnailSortDesend()) ? -1 : 1;
        }
        if (b == null) {
            return (AbstractProperties.isThumbnailSortDesend()) ? 1 : -1;
        }
        return (!AbstractProperties.isThumbnailSortDesend()) ? a.getFile().getAbsolutePath().compareTo(
            b.getFile().getAbsolutePath()) : b.getFile().getAbsolutePath().compareTo(a.getFile().getAbsolutePath());
    }
}