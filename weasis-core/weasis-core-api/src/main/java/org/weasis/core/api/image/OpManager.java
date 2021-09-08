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
import org.weasis.core.api.image.ImageOpNode.Param;
import org.weasis.core.api.util.Copyable;
import org.weasis.opencv.data.PlanarImage;

public interface OpManager extends OpEventListener, Copyable<OpManager> {

  void removeAllImageOperationAction();

  void clearNodeParams();

  void clearNodeIOCache();

  List<ImageOpNode> getOperations();

  void setFirstNode(PlanarImage imgSource);

  PlanarImage getFirstNodeInputImage();

  ImageOpNode getFirstNode();

  ImageOpNode getNode(String opName);

  ImageOpNode getLastNode();

  PlanarImage getLastNodeOutputImage();

  PlanarImage process();

  Object getParamValue(String opName, String param);

  boolean setParamValue(String opName, String param, Object value);

  void removeParam(String opName, String param);

  default boolean needProcessing() {
    for (ImageOpNode op : getOperations()) {
      if (op.getParam(Param.INPUT_IMG) == null || op.getParam(Param.OUTPUT_IMG) == null) {
        return true;
      }
    }
    return false;
  }
}
