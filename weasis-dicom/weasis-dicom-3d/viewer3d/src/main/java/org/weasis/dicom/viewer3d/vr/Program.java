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

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL3ES3;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Program {
  private static final Logger LOGGER = LoggerFactory.getLogger(Program.class);

  private final Map<String, Integer> uniformLocations = new HashMap<>();
  private final Map<String, BiConsumer<GL2, Integer>> uniforms = new HashMap<>();

  private final String name;
  private final Map<Integer, String> shaderCode = new HashMap<>();
  private final Map<Integer, Integer> shaderIds = new HashMap<>();
  private Integer programId = null;

  public Program(String name, String... filenames) {
    this.name = name;
    for (String val : filenames) {
      switch (val.substring(val.lastIndexOf(".") + 1)) {
        case "frag", "fs" -> shaderCode.put(GL2ES2.GL_FRAGMENT_SHADER, ShaderManager.getCode(val));
        case "vert", "vs" -> shaderCode.put(GL2ES2.GL_VERTEX_SHADER, ShaderManager.getCode(val));
        case "cs", "comp", "compute" -> shaderCode.put(
            GL3ES3.GL_COMPUTE_SHADER, ShaderManager.getCode(val));
        case "gs", "geom" -> shaderCode.put(GL3ES3.GL_GEOMETRY_SHADER, ShaderManager.getCode(val));
        default -> throw new UnsupportedOperationException(
            String.format(
                "Program cannot read the type of shader from file extension of %s ", val));
      }
    }
  }

  public void init(GL2 gl2) {
    if (programId != null) {
      return;
    }

    IntBuffer success = IntBuffer.allocate(1);
    ByteBuffer openglLog = ByteBuffer.allocate(512);
    for (Map.Entry<Integer, String> shader : shaderCode.entrySet()) {
      int shaderId = gl2.glCreateShader(shader.getKey());
      shaderIds.put(shader.getKey(), shaderId);
      gl2.glShaderSource(shaderId, 1, new String[] {shader.getValue()}, null);
      gl2.glCompileShader(shaderId);
      gl2.glGetShaderiv(shaderId, GL2ES2.GL_COMPILE_STATUS, success);
      if (success.get(0) != 1) {
        gl2.glGetShaderInfoLog(shaderId, 512, null, openglLog);
        LOGGER.warn(
            "Not success compiled status of compute shader: {}",
            new String(openglLog.array(), StandardCharsets.UTF_8));
      }
    }

    programId = gl2.glCreateProgram();
    for (Map.Entry<Integer, Integer> shaderId : shaderIds.entrySet()) {
      gl2.glAttachShader(programId, shaderId.getValue());
    }
    if (shaderIds.size() == 0) {
      return;
    }
    gl2.glLinkProgram(programId);

    gl2.glGetProgramiv(programId, GL2ES2.GL_LINK_STATUS, success);
    if (success.get(0) != 1) {
      gl2.glGetProgramInfoLog(programId, 512, null, openglLog);
      LOGGER.warn(
          "Cannot link compute shader: {}", new String(openglLog.array(), StandardCharsets.UTF_8));
    }

    for (Map.Entry<Integer, Integer> shaderId : shaderIds.entrySet()) {
      gl2.glDetachShader(programId, shaderId.getValue());
      gl2.glDeleteShader(shaderId.getValue());
    }
  }

  public void allocateUniform(GL2 gl2, String uniformName, BiConsumer<GL2, Integer> function) {
    init(gl2);
    uniformLocations.put(uniformName, gl2.glGetUniformLocation(programId, uniformName));
    uniforms.put(uniformName, function);
  }

  public void setUniforms(GL2 gl2) {
    for (Map.Entry<String, BiConsumer<GL2, Integer>> uniform : uniforms.entrySet()) {
      uniform.getValue().accept(gl2, uniformLocations.get(uniform.getKey()));
    }
  }

  public void use(GL2 gl2) {
    init(gl2);
    gl2.glUseProgram(programId);
  }

  public void destroy(GL2 gl) {
    if (programId != null) {
      for (final Map.Entry<Integer, Integer> shaderId : shaderIds.entrySet()) {
        gl.glDetachShader(programId, shaderId.getValue());
        gl.glDeleteShader(shaderId.getValue());
      }
      gl.glDeleteProgram(programId);
      programId = null;
      uniforms.clear();
      uniformLocations.clear();
    }
  }

  @Override
  public String toString() {
    if (name == null) {
      return super.toString();
    }
    return name;
  }
}
