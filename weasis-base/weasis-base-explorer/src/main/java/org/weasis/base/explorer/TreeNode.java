/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.explorer;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.StreamSupport;
import javax.swing.tree.DefaultMutableTreeNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TreeNode extends DefaultMutableTreeNode {

  private static final Logger LOGGER = LoggerFactory.getLogger(TreeNode.class);

  private boolean explored = false;
  private boolean root = false;

  public TreeNode(Path path) {
    setUserObject(path);
  }

  @Override
  public boolean getAllowsChildren() {
    return isDirectory();
  }

  @Override
  public boolean isLeaf() {
    if (!this.explored) {
      return false;
    }
    return (this.children == null) || this.children.isEmpty();
  }

  public Path getNodePath() {
    return (Path) getUserObject();
  }

  public void setNodePath(Path path) {
    setUserObject(path);
  }

  public boolean isExplored() {
    return this.explored;
  }

  public boolean isDirectory() {
    return Files.isDirectory(getNodePath());
  }

  public void refresh() {
    this.explored = false;
    this.removeAllChildren();
    explore();
  }

  public void explore() {
    Path path = getNodePath();

    if (!Files.isDirectory(path)) {
      return;
    }

    if (!isExplored()) {
      if (isRoot()) {
        path.getFileSystem().getRootDirectories().forEach(p -> add(new TreeNode(p)));
      } else {
        DirectoryStream.Filter<Path> filter = Files::isDirectory;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path, filter)) {
          StreamSupport.stream(stream.spliterator(), false)
              .sorted(Comparator.comparing(Path::getFileName))
              .forEachOrdered(p -> add(new TreeNode(p)));
        } catch (IOException e) {
          LOGGER.error("Building child directories", e);
        }
      }
      this.explored = true;
    }
  }

  @Override
  public String toString() {
    Path p = getNodePath().getFileName();
    return p == null ? getNodePath().toString() : p.toString();
  }

  @Override
  public final boolean isRoot() {
    return this.root;
  }

  public final void setRoot(final boolean root) {
    this.root = root;
  }
}
