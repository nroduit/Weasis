/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.gui.model;

/**
 * The listener interface for receiving viewModelChange events. The class that is interested in processing a
 * viewModelChange event implements this interface, and the object created with that class is registered with a
 * component using the component's <code>addViewModelChangeListener<code> method. When the viewModelChange event occurs,
 * that object's appropriate method is invoked.
 *
 * @see ViewModelChangeEvent
 */
@FunctionalInterface
public interface ViewModelChangeListener {

    void handleViewModelChanged(ViewModel viewModel);
}
