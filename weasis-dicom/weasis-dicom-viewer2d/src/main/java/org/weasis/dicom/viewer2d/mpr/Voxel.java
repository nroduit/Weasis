/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

public class Voxel<T extends Number> {
  private final T[] values;
  private final int channels;

  @SuppressWarnings("unchecked")
  public Voxel(int channels) {
    this.channels = channels;
    this.values = (T[]) new Number[channels];
  }

  public void setValue(int channel, T value) {
    if (channel >= 0 && channel < channels) {
      values[channel] = value;
    }
  }

  public T getValue(int channel) {
    return (channel >= 0 && channel < channels) ? values[channel] : null;
  }

  public T getValue() {
    return getValue(0);
  }

  public int getChannels() {
    return channels;
  }

  public T[] getValues() {
    return values;
  }
}
