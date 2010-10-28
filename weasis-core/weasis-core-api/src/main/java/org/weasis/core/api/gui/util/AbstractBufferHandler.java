/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.api.gui.util;

import java.awt.Component;
import java.awt.EventQueue;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import javax.swing.plaf.basic.BasicFileChooserUI;

import org.weasis.core.api.Messages;

/**
 * The Class AbstractBufferHandler.
 * 
 * @author Nicolas Roduit
 */
public abstract class AbstractBufferHandler {

    protected String lastPath;
    private String path;
    private boolean isDirty;
    private final Component parent;
    private String addOpenMessage = ""; //$NON-NLS-1$
    private static Float version;
    private FileFilter fileFilter;

    public AbstractBufferHandler(Component component) {
        parent = component;
        // lastPath = IniProps.getLastRecentFile();
    }

    public void setDirty(boolean flag) {
        isDirty = flag;
    }

    public boolean isDirty() {
        return isDirty;
    }

    protected void setPath(String s) {
        path = s;
        if (s != null) {
            lastPath = s;
        }
    }

    public String getPath() {
        return path;
    }

    public String getLastValidPath() {
        return lastPath;
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
        if (jfilechooser.showOpenDialog(getParentComponent()) != 0 || (file = jfilechooser.getSelectedFile()) == null) {
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
            JOptionPane.showMessageDialog(getParentComponent(), String.format(Messages
                .getString("AbstractBufferHandler.unable_open"), f.getName()) + addOpenMessage, //$NON-NLS-1$
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
                JOptionPane
                    .showMessageDialog(
                        getParentComponent(),
                        Messages.getString("AbstractBufferHandler.unable_save"), Messages.getString("AbstractBufferHandler.save"), 0); //$NON-NLS-1$ //$NON-NLS-2$
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
        String extension = null;
        try {
            extension = "." + ((FileFormatFilter) jfilechooser.getFileFilter()).getDefaultExtension(); //$NON-NLS-1$
        } catch (Exception ex) {
            extension = ""; //$NON-NLS-1$
        }
        if ((file.getPath()).endsWith(extension)) {
            filename = file.getPath();
        } else {
            filename = file.getPath() + extension;
        }
        file = new File(filename);
        if (file.exists()) {
            int i =
                JOptionPane.showConfirmDialog(getParentComponent(), String.format(Messages
                    .getString("AbstractBufferHandler.exist"), file.getName()), Messages //$NON-NLS-1$
                    .getString("AbstractBufferHandler.save_as"), 0); //$NON-NLS-1$
            if (i != 0) {
                return false;
            }
        }
        setPath(file.getPath());
        fileFilter = jfilechooser.getFileFilter();
        if (!handleSaveDocument(getPath())) {
            JOptionPane
                .showMessageDialog(
                    getParentComponent(),
                    Messages.getString("AbstractBufferHandler.unable_save"), Messages.getString("AbstractBufferHandler.save"), 0); //$NON-NLS-1$ //$NON-NLS-2$
            file.delete();
            return false;
        } else {
            isDirty = false;
            return true;
        }
    }

    protected boolean canCloseDocument() {
        if (isDirty) {
            int i =
                JOptionPane
                    .showConfirmDialog(
                        getParentComponent(),
                        Messages.getString("AbstractBufferHandler.unsave_msg"), Messages.getString("AbstractBufferHandler.unsave_t"), 1); //$NON-NLS-1$ //$NON-NLS-2$
            if (i == 0) {
                return saveDocument();
            }
            if (i == 2) {
                return false;
            }
        }
        return true;
    }

    protected JFileChooser configureForLastPath(JFileChooser jfilechooser) {
        String s = getLastValidPath();
        if (s != null) {
            try {
                File file = new File((new File(s)).getParent());
                jfilechooser.setCurrentDirectory(file);
            } catch (NullPointerException nullpointerexception) {
            }
        }
        return jfilechooser;
    }

    protected JFileChooser getOpenFileChooser() {
        JFileChooser fileChooser;
        String jVersion = System.getProperty("java.version"); //$NON-NLS-1$
        String targetVersion = "1.5."; //$NON-NLS-1$
        if (jVersion.startsWith(targetVersion)) { // same version
            fileChooser = new JFileChooser() {

                /** @todo bug http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4678049 */
                @Override
                public void setFileFilter(FileFilter filter) {
                    super.setFileFilter(filter);
                    if (!(getUI() instanceof BasicFileChooserUI)) {
                        return;
                    }
                    final BasicFileChooserUI ui = (BasicFileChooserUI) getUI();
                    final String name = ui.getFileName().trim();
                    if ((name == null) || (name.length() == 0)) {
                        return;
                    }
                    EventQueue.invokeLater(new Thread() {

                        @Override
                        public void run() {
                            String currentName = ui.getFileName();
                            if ((currentName == null) || (currentName.length() == 0)) {
                                ui.setFileName(name);
                            }
                        }
                    });
                }
            };
        } else {
            fileChooser = new JFileChooser();
        }
        fileChooser.setDialogType(JFileChooser.OPEN_DIALOG);
        return configureForLastPath(fileChooser);
    }

    protected JFileChooser getSaveFileChooser() {
        JFileChooser fileChooser = new JFileChooser() {

            /** @todo bug http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4678049 */
            @Override
            public void setFileFilter(FileFilter filter) {
                super.setFileFilter(filter);
                if (!(getUI() instanceof BasicFileChooserUI)) {
                    return;
                }
                final BasicFileChooserUI ui = (BasicFileChooserUI) getUI();
                final String name = ui.getFileName().trim();
                if ((name == null) || (name.length() == 0)) {
                    return;
                }
                EventQueue.invokeLater(new Thread() {

                    @Override
                    public void run() {
                        String currentName = ui.getFileName();
                        if ((currentName == null) || (currentName.length() == 0)) {
                            ui.setFileName(name);
                        }
                    }
                });
            }
        };
        fileChooser.setDialogType(JFileChooser.SAVE_DIALOG);
        fileChooser.setAcceptAllFileFilterUsed(false);
        return configureForLastPath(fileChooser);
    }

    protected Component getParentComponent() {
        return parent;
    }

    protected abstract void handleNewDocument();

    protected boolean handleOpenDocument(String s) {
        FileInputStream fileinputstream = null;
        boolean flag = true;
        try {
            fileinputstream = new FileInputStream(s);
            flag = handleOpenDocument(((fileinputstream)));
        } catch (FileNotFoundException filenotfoundexception) {
            flag = false;
        } finally {
            if (fileinputstream != null) {
                try {
                    fileinputstream.close();
                } catch (IOException ioexception) {
                }
            }
        }
        return flag;
    }

    protected boolean handleSaveDocument(String s) {
        FileOutputStream fileoutputstream = null;
        boolean flag = true;
        try {
            fileoutputstream = new FileOutputStream(s);
            flag = handleSaveDocument(((fileoutputstream)));
        } catch (FileNotFoundException filenotfoundexception) {
            flag = false;
        } finally {
            if (fileoutputstream != null) {
                try {
                    fileoutputstream.flush();
                    fileoutputstream.close();
                } catch (IOException ioexception) {
                }
            }
        }
        return flag;
    }

    protected abstract boolean handleOpenDocument(InputStream inputstream);

    protected abstract boolean handleSaveDocument(OutputStream outputstream);

    public void setAddOpenMessage(String addOpenMessage) {
        this.addOpenMessage = addOpenMessage;
    }

    public static void setVersion(Float fileVersion) {
        version = fileVersion;
    }

    public String getAddOpenMessage() {
        return addOpenMessage;
    }

    public static Float getVersion() {
        return version;
    }

    public FileFilter getFileFilter() {
        return fileFilter;
    }
}
