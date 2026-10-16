/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.image.ImageOpNode.Param;
import org.weasis.core.api.setting.CoreSettings;
import org.weasis.core.api.setting.Settings;
import org.weasis.opencv.data.PlanarImage;

/**
 * Simple implementation of OpManager that manages a pipeline of image operations.
 *
 * <p>This class maintains operations in both a list (for ordered processing) and a map (for
 * name-based lookup). Operations can be added, removed, and processed in sequence.
 *
 * <p>A pass runs only what changed: a node whose {@link ImageOpNode#getRevision() revision} and
 * input image are those of its last run keeps its output. Operations never write into their input,
 * so an identical input object means identical pixels; a node passing its input through makes the
 * nodes below it see an unchanged input too. A manager is used by one thread at a time; a {@link
 * #copy()} starts with nothing to reuse.
 *
 * @see OpManager
 * @see ImageOpNode
 */
public class SimpleOpManager implements OpManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(SimpleOpManager.class);

  private static final String IMAGE_OP_NAME = Messages.getString("SimpleOpManager.img_op");

  /** Position for inserting operations relative to a reference node. */
  public enum Position {
    /** Insert before the reference node. */
    BEFORE,
    /** Insert after the reference node. */
    AFTER
  }

  /** What a node saw at its last successful run. */
  private record LastRun(PlanarImage input, long revision) {}

  private final Map<String, ImageOpNode> nodesByName;
  private final List<ImageOpNode> operations;
  private final Map<ImageOpNode, LastRun> lastRuns = new IdentityHashMap<>();
  private boolean lastPassReused;
  private volatile String name;

  /** Creates a new SimpleOpManager with default name. */
  public SimpleOpManager() {
    this(IMAGE_OP_NAME);
  }

  /**
   * Creates a new SimpleOpManager with the specified name.
   *
   * @param name the manager name
   */
  public SimpleOpManager(String name) {
    this.operations = new ArrayList<>();
    this.nodesByName = new HashMap<>();
    setName(name);
  }

  /**
   * Creates a deep copy of another SimpleOpManager.
   *
   * @param other the manager to copy
   */
  public SimpleOpManager(SimpleOpManager other) {
    this(other.name);
    other.operations.forEach(
        node -> {
          var copiedNode = node.copy();
          operations.add(copiedNode);
          nodesByName.put(copiedNode.getName(), copiedNode);
        });
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = Objects.requireNonNullElse(name, IMAGE_OP_NAME);
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public List<ImageOpNode> getOperations() {
    return List.copyOf(operations);
  }

  /**
   * Adds an image operation at the end of the pipeline.
   *
   * @param action the operation to add
   */
  public void addImageOperationAction(ImageOpNode action) {
    addImageOperationAction(action, null, null);
  }

  /**
   * Adds an image operation at the specified position relative to a reference node.
   *
   * @param action the operation to add
   * @param pos the position (BEFORE or AFTER)
   * @param positionRef the reference node, or null to add at the end
   */
  public void addImageOperationAction(ImageOpNode action, Position pos, ImageOpNode positionRef) {
    if (action == null) {
      return;
    }
    var uniqueName = ensureUniqueName(action.getName());
    action.setName(uniqueName);
    nodesByName.put(uniqueName, action);

    var index = calculateInsertIndex(pos, positionRef);
    if (index >= 0 && index < operations.size()) {
      operations.add(index, action);
    } else {
      operations.add(action);
    }
    lastRuns.clear();
  }

  private String ensureUniqueName(String baseName) {
    if (!nodesByName.containsKey(baseName)) {
      return baseName;
    }

    LOGGER.warn("Operation name '{}' already exists, generating unique name", baseName);
    return baseName + " " + findNextAvailableSuffix(baseName);
  }

  private int findNextAvailableSuffix(String baseName) {
    return java.util.stream.IntStream.iterate(2, i -> i + 1)
        .filter(i -> !nodesByName.containsKey("%s %d".formatted(baseName, i)))
        .findFirst()
        .orElseThrow();
  }

  private int calculateInsertIndex(Position pos, ImageOpNode positionRef) {
    if (positionRef == null) {
      return -1;
    }

    var index = operations.indexOf(positionRef);
    return index < 0 ? -1 : (pos == Position.AFTER ? index + 1 : index);
  }

  /**
   * Removes an image operation from the pipeline.
   *
   * @param action the operation to remove
   */
  public void removeImageOperationAction(ImageOpNode action) {
    if (action == null) {
      return;
    }
    operations.remove(action);

    nodesByName.values().removeIf(node -> node == action);
    lastRuns.clear();
  }

  @Override
  public void removeAllImageOperationAction() {
    clearNodeParams();
    operations.clear();
    nodesByName.clear();
    lastRuns.clear();
  }

  @Override
  public void clearNodeParams() {
    operations.forEach(ImageOpNode::clearParams);
    lastRuns.clear();
  }

  @Override
  public void clearNodeIOCache() {
    operations.forEach(ImageOpNode::clearIOCache);
    lastRuns.clear();
  }

  @Override
  public void setFirstNode(PlanarImage imgSource) {
    getFirstNode().ifPresent(node -> node.setParam(Param.INPUT_IMG, imgSource));
  }

  @Override
  public Optional<PlanarImage> getFirstNodeInputImage() {
    return getFirstNode().map(node -> (PlanarImage) node.getParam(Param.INPUT_IMG));
  }

  @Override
  public Optional<ImageOpNode> getFirstNode() {
    return operations.isEmpty() ? Optional.empty() : Optional.of(operations.getFirst());
  }

  @Override
  public Optional<ImageOpNode> getNode(String opName) {
    return Optional.ofNullable(nodesByName.get(opName));
  }

  @Override
  public Optional<ImageOpNode> getLastNode() {
    return operations.isEmpty() ? Optional.empty() : Optional.of(operations.getLast());
  }

  @Override
  public Optional<PlanarImage> getLastNodeOutputImage() {
    return getLastNode().map(node -> (PlanarImage) node.getParam(Param.OUTPUT_IMG));
  }

  /** Resets the output image of the last node to clear preprocessing cache. */
  public void resetLastNodeOutputImage() {
    getLastNode().ifPresent(node -> node.setParam(Param.OUTPUT_IMG, null));
  }

  @Override
  public Optional<PlanarImage> process() {
    var source = getFirstNodeInputImage().orElse(null);
    lastPassReused = false;
    if (source == null || source.width() <= 0) {
      clearNodeIOCache();
      return getLastNodeOutputImage();
    }

    boolean tracking = Settings.get(CoreSettings.DISPLAY_OP_TRACKING);
    if (!tracking) {
      lastRuns.clear();
    }
    int skipped = runChain(tracking);
    lastPassReused = skipped > 0 && skipped == operations.size();
    if (skipped > 0 && Settings.get(CoreSettings.DISPLAY_OP_VERIFY)) {
      lastPassReused &= verifyAgainstFullRun(source);
    }
    return getLastNodeOutputImage();
  }

  /**
   * Whether the last {@link #process()} ran no node at all: its result is the image of the pass
   * before, with the same content. A node passing its input through returns the same object even
   * when that input was changed in place, so the identity of the result does not tell it.
   */
  public boolean isLastPassReused() {
    return lastPassReused;
  }

  /**
   * Runs the nodes in order, skipping those with nothing new when {@code tracking}.
   *
   * @return the number of nodes skipped
   */
  private int runChain(boolean tracking) {
    StringBuilder trace = LOGGER.isDebugEnabled() ? new StringBuilder() : null;
    long passStart = System.nanoTime();
    int skipped = 0;
    for (var i = 0; i < operations.size(); i++) {
      var current = operations.get(i);
      if (i > 0) {
        var previous = operations.get(i - 1);
        current.setParam(Param.INPUT_IMG, previous.getParam(Param.OUTPUT_IMG));
      }
      if (tracking && isUpToDate(current)) {
        skipped++;
        if (trace != null) {
          trace.append(", ").append(current.getName()).append(" skipped");
        }
        continue;
      }
      long start = System.nanoTime();
      executeOperation(current, tracking);
      if (trace != null) {
        trace.append(", ").append(current.getName()).append(' ').append(elapsedMs(start));
      }
    }
    if (trace != null) {
      LOGGER.debug("Pass of {} in {}{}", name, elapsedMs(passStart), trace);
    }
    return skipped;
  }

  private static String elapsedMs(long start) {
    return "%.2f ms".formatted((System.nanoTime() - start) / 1_000_000.0); // NON-NLS
  }

  /**
   * Whether the node's last run had the same revision and the same input, and its output is kept.
   */
  private boolean isUpToDate(ImageOpNode node) {
    LastRun last = lastRuns.get(node);
    if (last == null || !node.isEnabled()) {
      return false;
    }
    long revision = node.getRevision();
    return revision != -1
        && revision == last.revision()
        && last.input() == node.getParam(Param.INPUT_IMG)
        && isAvailable(last.input())
        && node.getParam(Param.OUTPUT_IMG) instanceof PlanarImage output
        && isAvailable(output);
  }

  private static boolean isAvailable(PlanarImage image) {
    return image != null && !image.isReleased() && !(image instanceof Mat mat && mat.empty());
  }

  private void executeOperation(ImageOpNode operation, boolean tracking) {
    // Read before running: an operation that changes its revision while running runs again
    long revision = operation.getRevision();
    lastRuns.remove(operation);
    try {
      if (operation.isEnabled()) {
        operation.process();
        if (tracking && revision != -1) {
          lastRuns.put(
              operation, new LastRun((PlanarImage) operation.getParam(Param.INPUT_IMG), revision));
        }
      } else {
        operation.setParam(Param.OUTPUT_IMG, operation.getParam(Param.INPUT_IMG));
      }
    } catch (Exception e) {
      LOGGER.error("Operation '{}' failed", operation.getParam(Param.NAME), e);
      operation.setParam(Param.OUTPUT_IMG, operation.getParam(Param.INPUT_IMG));
    }
  }

  /**
   * Runs the whole chain on copies of the nodes and compares the result with the tracked one. On a
   * difference, logs the nodes and runs the chain again without reuse, so the display is right.
   *
   * @return true when the tracked result was right
   */
  private boolean verifyAgainstFullRun(PlanarImage source) {
    PlanarImage tracked = getLastNodeOutputImage().orElse(null);
    SimpleOpManager full = new SimpleOpManager(this);
    full.setFirstNode(source);
    full.runChain(false);
    PlanarImage expected = full.getLastNodeOutputImage().orElse(null);
    if (!isSameImage(tracked, expected)) {
      LOGGER.error(
          "Display pass of {} differs from a full run; revisions: {}",
          name,
          operations.stream().map(n -> n.getName() + "=" + n.getRevision()).toList());
      lastRuns.clear();
      runChain(false);
      return false;
    }
    return true;
  }

  static boolean isSameImage(PlanarImage a, PlanarImage b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    return a.type() == b.type()
        && a.width() == b.width()
        && a.height() == b.height()
        && Core.norm(a.toMat(), b.toMat(), Core.NORM_INF) == 0.0;
  }

  @Override
  public Optional<Object> getParamValue(String opName, String param) {
    return getNode(opName).map(node -> node.getParam(param));
  }

  @Override
  public <T> Optional<T> getParamValue(String opName, String param, Class<T> ignored) {
    return getNode(opName).map(node -> node.getParam(param, ignored));
  }

  @Override
  public boolean setParamValue(String opName, String param, Object value) {
    if (opName == null || param == null) {
      return false;
    }
    return getNode(opName)
        .map(
            node -> {
              node.setParam(param, value);
              return true;
            })
        .orElse(false);
  }

  @Override
  public void removeParam(String opName, String param) {
    if (opName != null && param != null) {
      getNode(opName).ifPresent(node -> node.removeParam(param));
    }
  }

  @Override
  public void handleImageOpEvent(ImageOpEvent event) {
    operations.forEach(node -> node.handleImageOpEvent(event));
  }

  @Override
  public SimpleOpManager copy() {
    return new SimpleOpManager(this);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof SimpleOpManager other
        && Objects.equals(name, other.name)
        && Objects.equals(operations, other.operations);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, operations);
  }
}
