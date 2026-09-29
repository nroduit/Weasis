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

import java.util.List;
import java.util.Optional;
import org.weasis.core.api.image.ImageOpNode.Param;
import org.weasis.core.api.util.Copyable;
import org.weasis.opencv.data.PlanarImage;

/**
 * Manager for image operations maintaining a pipeline of processing nodes.
 *
 * <p>Provides thread-safe methods to manage operations, parameters, and execute the processing
 * pipeline. All operations are executed in the order they were added to the pipeline.
 */
public interface OpManager extends OpEventListener, Copyable<OpManager> {

  /**
   * Name of the operation burning redaction regions into the pixels, which must also be applied to
   * any image exported from a view.
   */
  String REDACTION_OP_NAME = "Redaction"; // NON-NLS

  /** Removes all image operations from the manager. */
  void removeAllImageOperationAction();

  /** Clears all parameters from all nodes in the operation pipeline. */
  void clearNodeParams();

  /** Clears the input/output cache from all nodes in the operation pipeline. */
  void clearNodeIOCache();

  /**
   * Returns an unmodifiable view of all operations in the pipeline.
   *
   * @return immutable list of operations in processing order
   */
  List<ImageOpNode> getOperations();

  /** Sets the input image for the first node in the pipeline. */
  void setFirstNode(PlanarImage imgSource);

  /** Returns the input image of the first node. */
  Optional<PlanarImage> getFirstNodeInputImage();

  /** Returns the first node in the pipeline. */
  Optional<ImageOpNode> getFirstNode();

  /** Returns the node with the specified name. */
  Optional<ImageOpNode> getNode(String opName);

  /** Returns the last node in the pipeline. */
  Optional<ImageOpNode> getLastNode();

  /** Returns the output image of the last node. */
  Optional<PlanarImage> getLastNodeOutputImage();

  /**
   * Processes the entire pipeline and returns the final output image.
   *
   * @return the processed image, or empty if the pipeline is empty or processing failed
   */
  Optional<PlanarImage> process();

  /** Returns the parameter value for the specified operation and parameter name. */
  Optional<Object> getParamValue(String opName, String param);

  /**
   * Returns the parameter value for the specified operation and parameter name with type casting.
   *
   * @param opName the operation name
   * @param param the parameter name
   * @param ignored the class type to cast to (not used)
   * @param <T> the expected type of the parameter value
   * @return the parameter value cast to type T
   */
  <T> Optional<T> getParamValue(String opName, String param, Class<T> ignored);

  /**
   * Sets a parameter value for the specified operation.
   *
   * @return true if the parameter was set successfully
   */
  boolean setParamValue(String opName, String param, Object value);

  /** Removes a parameter from the specified operation. */
  void removeParam(String opName, String param);

  /** Checks if any node in the pipeline needs processing. */
  default boolean needProcessing() {
    return getOperations().stream()
        .anyMatch(
            op -> op.getParam(Param.INPUT_IMG) == null || op.getParam(Param.OUTPUT_IMG) == null);
  }

  /** Checks if the pipeline has any operations. */
  default boolean hasOperations() {
    return !getOperations().isEmpty();
  }

  /** Returns the number of operations in the pipeline. */
  default int getOperationCount() {
    return getOperations().size();
  }
}
