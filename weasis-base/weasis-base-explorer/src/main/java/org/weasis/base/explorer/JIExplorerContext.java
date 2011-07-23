package org.weasis.base.explorer;

import java.util.Vector;

import org.weasis.core.api.media.data.MediaElement;

public class JIExplorerContext {

    public static final int DIRECTORY_STATE = 1;
    public static final int KEY_WORDS_STATE = 2;
    public static final int CATEGORY_STATE = 3;
    public static final int DATE_STATE = 4;

    protected MediaElement[] selectedDiskObjects = null;
    protected Vector<TreeNode> selectedDirNodes = null;

    protected boolean includeKeyWords = false;
    protected boolean includeDiskObjects = false;
    protected boolean includeCatNodes = false;
    protected boolean includeDateNodes = false;
    protected boolean includeDirNodes = false;

    protected int lastSelectedKeyWordsIndex = -1;
    protected int lastSelectedDiskObjectIndex = -1;
    protected int lastSelectedCatNodesIndex = -1;
    protected int lastSelectedDateNodesIndex = -1;
    protected int lastSelectedDirNodesIndex = -1;

    protected boolean statusBarProgressTaskRunning = false;

    protected int imageCnt = -1;

    protected int state = -1;

    /**
     * @return the selsectedDirNodes
     */
    public final synchronized Vector<TreeNode> getSelectedDirNodes() {
        return this.selectedDirNodes;
    }

    /**
     * @param selectedDirNodes
     *            the selectedDirNodes to set
     */
    public final synchronized void setSelectedDirNodes(final Vector<TreeNode> selectedDirNodes,
        final TreeNode lastSelectedDirNodes) {
        this.selectedDirNodes = selectedDirNodes;
        this.lastSelectedDirNodesIndex = this.selectedDirNodes.indexOf(lastSelectedDirNodes);
    }

    /**
     * @param selsectedCatNodes
     *            the selsectedCatNodes to set
     * @param lastSelectedDirNodesIndex
     *            the lastSelectedDirNodesIndex to set
     */
    public final synchronized void setSelectedDirNodes(final Vector<TreeNode> selectedDirNodes,
        final int lastSelectedDirNodesIndex) {
        this.selectedDirNodes = selectedDirNodes;
        this.lastSelectedDirNodesIndex = lastSelectedDirNodesIndex;
    }

    /**
     * @return the selsectedDiskObjects
     */
    public final synchronized MediaElement[] getSelectedDiskObjects() {
        return this.selectedDiskObjects;
    }

    /**
     * @param selsectedDiskObjects
     *            the selsectedDiskObjects to set
     * @param lastSelectedDiskObject
     *            the lastSelectedDiskObjectIndex to set
     */
    public final synchronized void setSelectedDiskObjects(final MediaElement[] selsectedDiskObjects,
        final MediaElement lastSelectedDiskObject) {
        // log.debug("setSelectedDiskObjects - " + selsectedDiskObjects);
        this.selectedDiskObjects = selsectedDiskObjects;
        if ((selsectedDiskObjects != null) && (lastSelectedDiskObject != null)) {
            for (int i = 0; i < this.selectedDiskObjects.length; i++) {
                if (lastSelectedDiskObject.equals(this.selectedDiskObjects[i])) {
                    this.lastSelectedDiskObjectIndex = i;
                    return;
                }
            }
        }
    }

    /**
     * @param selsectedDiskObjects
     *            the selsectedDiskObjects to set
     * @param lastSelectedDiskObject
     *            the lastSelectedDiskObjectIndex to set
     */
    public final synchronized void setSelectedDiskObjects(final MediaElement[] selsectedDiskObjects,
        final int lastSelectedDiskObjectIndex) {
        // log.debug("setSelectedDiskObjects - " + selsectedDiskObjects);
        this.selectedDiskObjects = selsectedDiskObjects;
        this.lastSelectedDiskObjectIndex = lastSelectedDiskObjectIndex;
    }

    /**
     * @return the includeCatNodes
     */
    public final synchronized boolean isIncludeCatNodes() {
        return this.includeCatNodes;
    }

    /**
     * @param includeCatNodes
     *            the includeCatNodes to set
     */
    public final synchronized void setIncludeCatNodes(final boolean includeCatNodes) {
        this.includeCatNodes = includeCatNodes;
    }

    /**
     * @return the includeDateNodes
     */
    public final synchronized boolean isIncludeDateNodes() {
        return this.includeDateNodes;
    }

    /**
     * @param includeDateNodes
     *            the includeDateNodes to set
     */
    public final synchronized void setIncludeDateNodes(final boolean includeDateNodes) {
        this.includeDateNodes = includeDateNodes;
    }

    /**
     * @return the includeDirNodes
     */
    public final synchronized boolean isIncludeDirNodes() {
        return this.includeDirNodes;
    }

    /**
     * @param includeDirNodes
     *            the includeDirNodes to set
     */
    public final synchronized void setIncludeDirNodes(final boolean includeDirNodes) {
        this.includeDirNodes = includeDirNodes;
    }

    /**
     * @return the includeKeyWords
     */
    public final synchronized boolean isIncludeKeyWords() {
        return this.includeKeyWords;
    }

    /**
     * @param includeKeyWords
     *            the includeKeyWords to set
     */
    public final synchronized void setIncludeKeyWords(final boolean includeKeyWords) {
        this.includeKeyWords = includeKeyWords;
    }

    /**
     * @return the state
     */
    public final synchronized int getState() {
        return this.state;
    }

    /**
     * @param state
     *            the state to set
     */
    public final synchronized void setState(final int state) {
        this.state = state;
        clearListObjects();
    }

    /**
     * @return the includeDiskObjects
     */
    public final synchronized boolean isIncludeDiskObjects() {
        return this.includeDiskObjects;
    }

    /**
     * @param includeDiskObjects
     *            the includeDiskObjects to set
     */
    public final synchronized void setIncludeDiskObjects(final boolean includeDiskObjects) {
        this.includeDiskObjects = includeDiskObjects;
    }

    /**
     * @return the lastSelectedDirNodesIndex
     */
    public final synchronized int getLastSelectedDirNodesIndex() {
        return this.lastSelectedDirNodesIndex > -1 ? this.lastSelectedDirNodesIndex : 0;
    }

    /**
     * @return the lastSelectedDiskObjectIndex
     */
    public final synchronized int getLastSelectedDiskObjectIndex() {
        return this.lastSelectedDiskObjectIndex > -1 ? this.lastSelectedDiskObjectIndex : 0;
    }

    /**
     * @return the lastSelectedKeyWordsIndex
     */
    public final synchronized int getLastSelectedKeyWordsIndex() {
        return this.lastSelectedKeyWordsIndex > -1 ? this.lastSelectedKeyWordsIndex : 0;
    }

    /**
     * @return the imageCnt
     */
    public final synchronized int getImageCnt() {
        return this.imageCnt;
    }

    /**
     * @param imageCnt
     *            the imageCnt to set
     */
    public final synchronized void setImageCnt(final int imageCnt) {
        this.imageCnt = imageCnt;
    }

    /**
     * @return the statusBarProgressTaskRunning
     */
    public final synchronized boolean isStatusBarProgressTaskRunning() {
        return this.statusBarProgressTaskRunning;
    }

    /**
     * @param statusBarProgressTaskRunning
     *            the statusBarProgressTaskRunning to set
     */
    public final synchronized void setStatusBarProgressTaskRunning(final boolean statusBarProgressTaskRunning) {
        this.statusBarProgressTaskRunning = statusBarProgressTaskRunning;
    }

    public final synchronized MediaElement getLastSelectedDiskObj() {
        if ((this.lastSelectedDiskObjectIndex > -1) && (this.selectedDiskObjects != null)
            && (this.selectedDiskObjects.length > this.lastSelectedDiskObjectIndex))
            return this.selectedDiskObjects[this.lastSelectedDiskObjectIndex];
        return null;
    }

    public final synchronized void clearListObjects() {
        // log.debug("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!clearListObjects!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        this.selectedDiskObjects = null;
        this.lastSelectedDiskObjectIndex = -1;
        this.imageCnt = -1;
    }
}
