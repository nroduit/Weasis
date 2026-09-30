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

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.viewer3d.gl.Gl;

public class Program {
  private static final Logger LOGGER = LoggerFactory.getLogger(Program.class);

  private final Map<String, Integer> uniformLocations = new HashMap<>();
  private final Map<String, BiConsumer<Gl, Integer>> uniforms = new HashMap<>();

  private final String name;
  private final Map<Integer, String> shaderCode = new HashMap<>();
  private final Map<Integer, Integer> shaderIds = new HashMap<>();
  private Integer programId = null;
  private boolean valid;

  /** Set between the link request and the reading of its status. */
  private boolean linking;

  public Program(String name, String... filenames) {
    this.name = name;
    for (String val : filenames) {
      switch (val.substring(val.lastIndexOf(".") + 1)) {
        case "frag", "fs" -> // NON-NLS
            shaderCode.put(Gl.GL_FRAGMENT_SHADER, ShaderManager.getCode(val));
        case "vert", "vs" -> // NON-NLS
            shaderCode.put(Gl.GL_VERTEX_SHADER, ShaderManager.getCode(val));
        case "cs", "comp", "compute" -> // NON-NLS
            shaderCode.put(Gl.GL_COMPUTE_SHADER, ShaderManager.getCode(val));
        case "gs", "geom" -> // NON-NLS
            shaderCode.put(Gl.GL_GEOMETRY_SHADER, ShaderManager.getCode(val));
        default ->
            throw new UnsupportedOperationException(
                String.format(
                    "Program cannot read the type of shader from file extension of %s ", val));
      }
    }
  }

  /** Compiles and links, waiting for the driver. */
  public void init(Gl gl) {
    startLink(gl);
    if (linking) {
      finishLink(gl);
    }
  }

  /**
   * Hands the sources to the driver without reading any status, which is what would make the call
   * wait. With parallel shader compile the driver then works on its own threads; see {@link
   * #isReady}.
   */
  public void startLink(Gl gl) {
    if (programId != null) {
      return;
    }
    valid = true;
    for (Map.Entry<Integer, String> shader : shaderCode.entrySet()) {
      int shaderId = gl.glCreateShader(shader.getKey());
      shaderIds.put(shader.getKey(), shaderId);
      gl.glShaderSource(shaderId, shader.getValue());
      gl.glCompileShader(shaderId);
    }
    programId = gl.glCreateProgram();
    for (Map.Entry<Integer, Integer> shaderId : shaderIds.entrySet()) {
      gl.glAttachShader(programId, shaderId.getValue());
    }
    if (!shaderIds.isEmpty()) {
      gl.glLinkProgram(programId);
      linking = true;
    }
  }

  /**
   * Whether the program can be used without waiting, starting its link if needed. {@code false}
   * only while a driver with parallel shader compile is still at it; any other driver links
   * synchronously here.
   */
  public boolean isReady(Gl gl) {
    startLink(gl);
    if (!linking) {
      return true;
    }
    if (gl.isParallelShaderCompile()
        && gl.glGetProgrami(programId, Gl.GL_COMPLETION_STATUS_KHR) == Gl.GL_FALSE) {
      return false;
    }
    finishLink(gl);
    return true;
  }

  private void finishLink(Gl gl) {
    linking = false;
    for (int shaderId : shaderIds.values()) {
      if (gl.glGetShaderi(shaderId, Gl.GL_COMPILE_STATUS) != Gl.GL_TRUE) {
        valid = false;
        LOGGER.error("Cannot compile shader {}: {}", name, gl.glGetShaderInfoLog(shaderId));
      }
    }
    if (gl.glGetProgrami(programId, Gl.GL_LINK_STATUS) != Gl.GL_TRUE) {
      valid = false;
      LOGGER.error("Cannot link program {}: {}", name, gl.glGetProgramInfoLog(programId));
    }

    // The shaders are linked into the program and of no further use; forgetting their names here
    // is what keeps destroy() from deleting them a second time.
    for (Map.Entry<Integer, Integer> shaderId : shaderIds.entrySet()) {
      gl.glDetachShader(programId, shaderId.getValue());
      gl.glDeleteShader(shaderId.getValue());
    }
    shaderIds.clear();
  }

  public void allocateUniform(Gl gl, String uniformName, BiConsumer<Gl, Integer> function) {
    init(gl);
    uniformLocations.put(uniformName, gl.glGetUniformLocation(programId, uniformName));
    uniforms.put(uniformName, function);
  }

  /**
   * Registers, under this program, the uniform setters {@code source} already holds for the given
   * names, so two programs sharing an include set their common uniforms from one definition.
   */
  public void shareUniforms(Gl gl, Program source, String... uniformNames) {
    for (String name : uniformNames) {
      BiConsumer<Gl, Integer> setter = source.uniforms.get(name);
      if (setter != null) {
        allocateUniform(gl, name, setter);
      }
    }
  }

  /**
   * Registers every uniform setter of {@code source} under this program, for a program built from
   * the same includes; a uniform the shader does not use resolves to no location and is skipped.
   */
  public void shareAllUniforms(Gl gl, Program source) {
    source.uniforms.forEach((name, setter) -> allocateUniform(gl, name, setter));
  }

  public void setUniforms(Gl gl) {
    for (Map.Entry<String, BiConsumer<Gl, Integer>> uniform : uniforms.entrySet()) {
      uniform.getValue().accept(gl, uniformLocations.get(uniform.getKey()));
    }
  }

  public void use(Gl gl) {
    init(gl);
    gl.glUseProgram(programId);
  }

  public int getProgramId() {
    return programId != null ? programId : 0;
  }

  /** {@code false} when a shader did not compile or the program did not link; see the log. */
  public boolean isValid() {
    return valid;
  }

  public void destroy(Gl gl) {
    if (programId != null) {
      // A link never read leaves its shaders attached; deleting them flags them for deletion
      // with the program.
      shaderIds.values().forEach(gl::glDeleteShader);
      shaderIds.clear();
      gl.glDeleteProgram(programId);
      programId = null;
      linking = false;
      valid = false;
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
