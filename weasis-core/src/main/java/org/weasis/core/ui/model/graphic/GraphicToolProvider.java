/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic;

import java.util.List;

/**
 * Contributes graphic tools to the {@link GraphicRegistry}. The core registers its own tools this
 * way; a plugin publishes an implementation as a service to add its tools.
 */
public interface GraphicToolProvider {

  List<GraphicToolDescriptor> getTools();
}
