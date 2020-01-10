/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.gui.util;

import java.awt.Component;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.Messages;

/**
 * The Class AbstractBufferHandler.
 *
 */
public abstract class AbstractBufferHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractBufferHandler.class);

    private String path;
    private boolean isDirty;
    private final Component parent;
    private String addOpenMessage = ""; //$NON-NLS-1$
    private FileFilter fileFilter;

    public AbstractBufferHandler(Component component) {
        parent = component;
    }

    public void setDirty(boolean flag) {
        isDirty = flag;
    }

    public boolean isDirty() {
        return isDirty;
    }

    protected void setPath(String s) {
        path = s;
    }

    public String getPath() {
        return path;
    }

    public boolean newDocument() {
        if (isDirty() && !canCloseDocument()) {
            return false;
        } else {
            setDirty(false);
            setPath(null);
            handleNewDocument();
            return true;
        }
    }

    public boolean openDocument() {
        if (isDirty() && !canCloseDocument()) {
            return false;
        }
        JFileChooser jfilechooser = getOpenFileChooser();
        File file;
        if (jfilechooser.showOpenDialog(getParentComponent()) != JFileChooser.APPROVE_OPTION
            || (file = jfilechooser.getSelectedFile()) == null) {
            return false;
        } else {
            return openDocument(file.getPath(), false);
        }
    }

    protected boolean openDocument(String s) {
        return openDocument(s, true);
    }

    protected boolean openDocument(String s, boolean flag) {
        if (flag && isDirty() && !canCloseDocument()) {
            return false;
        }
        setPath(s);
        if (!handleOpenDocument(s)) {
            File f = new File(s);
            JOptionPane.showMessageDialog(getParentComponent(),
                String.format(Messages.getString("AbstractBufferHandler.unable_open"), f.getName()) + "\n" //$NON-NLS-1$ //$NON-NLS-2$
                    + addOpenMessage,
                Messages.getString("AbstractBufferHandler.open"), 0); //$NON-NLS-1$
            setPath(null);
            addOpenMessage = ""; //$NON-NLS-1$
            return false;
        } else {
            isDirty = false;
            return true;
        }
    }

    public boolean saveDocument() {
        if (path == null) {
            return saveAsDocument();
        } else {
            if (!handleSaveDocument(getPath())) {
                JOptionPane.showMessageDialog(getParentComponent(),
                    Messages.getString("AbstractBufferHandler.unable_save"), //$NON-NLS-1$
                    Messages.getString("AbstractBufferHandler.save"), 0); //$NON-NLS-1$
                File file = new File(getPath());
                file.delete();
                return false;
            } else {
                isDirty = false;
                return true;
            }
        }
    }

    public boolean saveAsDocument() {
        JFileChooser jfilechooser = getSaveFileChooser();
        if (jfilechooser.showSaveDialog(getParentComponent()) != 0) {
            return false;
        }
        File file = jfilechooser.getSelectedFile();
        String filename;
        String extension = ""; //$NON-NLS-1$

        FileFilter filter = jfilechooser.getFileFilter();
        if (filter instanceof FileFormatFilter) {
            extension = "." + ((FileFormatFilter) filter).getDefaultExtension(); //$NON-NLS-1$
        }

        if ((file.getPath()).endsWith(extension)) {
            filename = file.getPath();
        } else {
            filename = file.getPath() + extension;
        }
        file = new File(filename);
        if (file.exists()) {
            int i = JOptionPane.showConfirmDialog(getParentComponent(),
                String.format(Messages.getString("AbstractBufferHandler.exist"), file.getName()), //$NON-NLS-1$
                Messages.getString("AbstractBufferHandler.save_as"), //$NON-NLS-1$
                0);
            if (i != 0) {
                return false;
            }
        }
        setPath(file.getPath());
        fileFilter = jfilechooser.getFileFilter();
        if (!handleSaveDocument(getPath())) {
            JOptionPane.showMessageDialog(getParentComponent(), Messages.getString("AbstractBufferHandler.unable_save"), //$NON-NLS-1$
                Messages.getString("AbstractBufferHandler.save"), 0); //$NON-NLS-1$
            file.delete();
            return false;
        } else {
            isDirty = false;
            return true;
        }
    }

    protected boolean canCloseDocument() {
        if (isDirty) {
            int i = JOptionPane.showConfirmDialog(getParentComponent(),
                Messages.getString("AbstractBufferHandler.unsave_msg"), //$NON-NLS-1$
                Messages.getString("AbstractBufferHandler.unsave_t"), 1); //$NON-NLS-1$
            if (i == 0) {
                return saveDocument();
            }
            if (i == 2) {
                return false;
            }
        }
        return true;
    }

    protected JFileChooser getOpenFileChooser() {
        JFileChooser fileChooser = new JFileChooser(getLastFolder());
        fileChooser.setDialogType(JFileChooser.OPEN_DIALOG);
        return fileChooser;
    }

    protected JFileChooser getSaveFileChooser() {
        JFileChooser fileChooser = new JFileChooser(getLastFolder());
        fileChooser.setDialogType(JFileChooser.SAVE_DIALOG);
        fileChooser.setAcceptAllFileFilterUsed(false);
        return fileChooser;
    }

    protected Component getParentComponent() {
        return parent;
    }

    protected abstract void handleNewDocument();

    protected boolean handleOpenDocument(String s) {
        boolean flag = true;
        try (FileInputStream fileInputstream = new FileInputStream(s)) {
            flag = handleOpenDocument(fileInputstream);
        } catch (IOException e) {
            flag = false;
            LOGGER.error("Cannot open {}", s, e); //$NON-NLS-1$
        }
        return flag;
    }

    protected boolean handleSaveDocument(String s) {
        boolean flag = true;
        try (FileOutputStream fileoutputstream = new FileOutputStream(s)) {
            flag = handleSaveDocument(fileoutputstream);
        } catch (IOException e) {
            flag = false;
            LOGGER.error("Cannot save {}", s, e); //$NON-NLS-1$
        }
        return flag;
    }

    protected abstract boolean handleOpenDocument(InputStream inputstream);

    protected abstract boolean handleSaveDocument(OutputStream outputstream);

    protected String getLastFolder() {
        return null;
    }

    public void setAddOpenMessage(String addOpenMessage) {
        this.addOpenMessage = addOpenMessage;
    }

    public String getAddOpenMessage() {
        return addOpenMessage;
    }

    public FileFilter getFileFilter() {
        return fileFilter;
    }
}
