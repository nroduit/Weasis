/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.vr;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL4;
import org.joml.Vector3d;

public class VolumeTexture extends TextureData {

  private boolean requiredBuilding;
  private Vector3d texelSize;

  public VolumeTexture(int width, int height, int depth, PixelFormat pixelFormat) {
    super(width, height, depth, pixelFormat);
    this.requiredBuilding = true;
    this.texelSize = new Vector3d(1.0, 1.0, 1.0);
  }

  @Override
  public void init(GL4 gl4) {
    super.init(gl4);
    gl4.glActiveTexture(GL.GL_TEXTURE0);
    gl4.glBindTexture(GL2ES2.GL_TEXTURE_3D, getId());
    gl4.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL2ES3.GL_TEXTURE_BASE_LEVEL, 0);
    gl4.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
    gl4.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
    gl4.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
    gl4.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
    gl4.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL2ES2.GL_TEXTURE_WRAP_R, GL.GL_CLAMP_TO_EDGE);
    gl4.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL2ES3.GL_TEXTURE_BASE_LEVEL, 0);
    gl4.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL2ES3.GL_TEXTURE_MAX_LEVEL, 0);
    gl4.glTexImage3D(
        GL2ES2.GL_TEXTURE_3D, 0, internalFormat, width, height, depth, 0, format, type, null);

    if (gl4.glGetError() != 0) {
      throw new RuntimeException(
          "Unable to generate 3D texture; OpenGL error: "
              + jogamp.opengl.glu.error.Error.gluErrorString(gl4.glGetError()));
    }
  }

  public void render(GL4 gl4) {
    update(gl4);
  }

  private void update(GL4 gl4) {
    if (gl4 == null || !requiredBuilding) {
      return;
    }
    if (getId() <= 0) {
      init(gl4);
    }
    gl4.glActiveTexture(GL.GL_TEXTURE0);
    gl4.glBindTexture(GL2ES2.GL_TEXTURE_3D, getId());
    requiredBuilding = false;
  }

  public boolean isRequiredBuilding() {
    return requiredBuilding;
  }

  public void setRequiredBuilding(boolean requiredBuilding) {
    this.requiredBuilding = requiredBuilding;
  }

  public Vector3d getVolumeSize() {
    return new Vector3d(width, height, depth);
  }

  public int getMaxDimensionLength() {
    if (this.texelSize.x == 1.0) {
      return width;
    } else if (this.texelSize.y == 1.0) {
      return height;
    } else {
      return this.texelSize.z == 1.0 ? depth : Math.max(width, Math.max(height, depth));
    }
  }

  public void setTexelSize(Vector3d texelSize) {
    this.texelSize = texelSize;
  }

  public Vector3d getTexelSize() {
    return texelSize;
  }

  public Vector3d getNormalizedTexelSize() {
    int max = this.getMaxDimensionLength();
    return new Vector3d(
        width / (double) max * this.texelSize.x,
        height / (double) max * this.texelSize.y,
        depth / (double) max * this.texelSize.z);
  }
}
