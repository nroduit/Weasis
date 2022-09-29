/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.explorer.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

public class Tree<T> {

  private final T head;

  private final ArrayList<Tree<T>> leafs = new ArrayList<>();

  private Tree<T> parent = null;

  private HashMap<T, Tree<T>> locate = new HashMap<>();

  public Tree(T head) {
    this.head = head;
    locate.put(head, this);
  }

  public synchronized void addLeaf(T root, T leaf) {
    if (locate.containsKey(root)) {
      locate.get(root).addLeaf(leaf);
    } else {
      addLeaf(root).addLeaf(leaf);
    }
  }

  private synchronized Tree<T> addLeaf(T leaf) {
    Tree<T> t = new Tree<>(leaf);
    leafs.add(t);
    t.parent = this;
    t.locate = this.locate;
    locate.put(leaf, t);
    return t;
  }

  public synchronized void removeLeaf(T leaf) {
    Tree<T> t = locate.remove(leaf);
    if (t != null) {
      t.parent.leafs.remove(t);
      t.parent = null;
      t.locate = null;
    }
  }

  public synchronized Tree<T> setAsParent(T parentRoot) {
    Tree<T> t = new Tree<>(parentRoot);
    t.leafs.add(this);
    this.parent = t;
    t.locate = this.locate;
    t.locate.put(head, this);
    t.locate.put(parentRoot, t);
    return t;
  }

  public T getHead() {
    return head;
  }

  public synchronized Tree<T> getTree(T element) {
    return locate.get(element);
  }

  public synchronized Tree<T> getParent() {
    return parent;
  }

  public synchronized Collection<T> getSuccessors(T root) {
    Collection<T> successors = new ArrayList<>();
    Tree<T> tree = getTree(root);
    if (null != tree) {
      for (Tree<T> leaf : tree.leafs) {
        successors.add(leaf.head);
      }
    }
    return successors;
  }

  public Collection<Tree<T>> getSubTrees() {
    return leafs;
  }

  public static <T> Collection<T> getSuccessors(T of, Collection<Tree<T>> in) {
    for (Tree<T> tree : in) {
      if (tree.locate.containsKey(of)) {
        return tree.getSuccessors(of);
      }
    }
    return new ArrayList<>();
  }

  @Override
  public String toString() {
    return printTree(0);
  }

  public synchronized void clear() {
    locate.clear();
    leafs.clear();
    locate.put(head, this);
  }

  private String printTree(int increment) {
    char[] value = new char[increment];
    Arrays.fill(value, ' ');
    String inc = new String(value);
    StringBuilder s = new StringBuilder(inc + head);
    for (Tree<T> child : leafs) {
      s.append("\n");
      s.append(child.printTree(increment + 2));
    }
    return s.toString();
  }
}
