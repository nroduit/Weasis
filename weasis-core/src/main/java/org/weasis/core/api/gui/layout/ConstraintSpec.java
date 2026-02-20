/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.layout;

/**
 * Represents comprehensive column or row constraint configuration. Provides full control over
 * MigLayout's grow, shrink, fill, and size bounds.
 *
 * @param growWeight weight for growth distribution (0 = no grow)
 * @param shrinkWeight weight for shrink distribution (0 = no shrink)
 * @param fill whether to fill the available space
 * @param minSize minimum size in pixels (null for default)
 * @param maxSize maximum size in pixels (null for default)
 */
public record ConstraintSpec(
    double growWeight, double shrinkWeight, boolean fill, Integer minSize, Integer maxSize) {

  public ConstraintSpec {
    if (growWeight < 0 || shrinkWeight < 0) {
      throw new IllegalArgumentException("Weights must be non-negative");
    }
    if (minSize != null && minSize < 0) {
      throw new IllegalArgumentException("Min size must be non-negative");
    }
    if (maxSize != null && maxSize < 0) {
      throw new IllegalArgumentException("Max size must be non-negative");
    }
  }

  /** Creates a constraint with grow and fill, default shrink. */
  public ConstraintSpec(double growWeight) {
    this(growWeight, 100.0, true, null, null);
  }

  /** Creates a constraint with grow, shrink, and fill. */
  public ConstraintSpec(double growWeight, double shrinkWeight) {
    this(growWeight, shrinkWeight, true, null, null);
  }

  /** Creates a fixed-size constraint (no grow/shrink). */
  public static ConstraintSpec fixed(int size) {
    return new ConstraintSpec(0, 0, true, size, size);
  }

  /** Creates a constraint with min:max bounds. */
  public static ConstraintSpec withBounds(int minSize, int maxSize) {
    return new ConstraintSpec(100.0, 100.0, true, minSize, maxSize);
  }

  /** Returns a new ConstraintSpec with updated grow weight. */
  public ConstraintSpec withGrowWeight(double newGrowWeight) {
    return new ConstraintSpec(newGrowWeight, shrinkWeight, fill, minSize, maxSize);
  }

  /** Returns a new ConstraintSpec with updated shrink weight. */
  public ConstraintSpec withShrinkWeight(double newShrinkWeight) {
    return new ConstraintSpec(growWeight, newShrinkWeight, fill, minSize, maxSize);
  }

  /** Returns a new ConstraintSpec with updated fill setting. */
  public ConstraintSpec withFill(boolean newFill) {
    return new ConstraintSpec(growWeight, shrinkWeight, newFill, minSize, maxSize);
  }

  /** Returns a new ConstraintSpec with updated size bounds. */
  public ConstraintSpec withSizeBounds(Integer newMin, Integer newMax) {
    return new ConstraintSpec(growWeight, shrinkWeight, fill, newMin, newMax);
  }

  /** Converts this constraint to MigLayout constraint string. */
  public String toConstraintString() {
    return "[%s,shrink %s%s%s]"
        .formatted(formatGrow(), formatShrink(), fill ? ",fill" : "", formatSizeBounds());
  }

  private String formatGrow() {
    return growWeight > 0 ? "grow " + growWeight : "grow 0";
  }

  private String formatShrink() {
    return shrinkWeight > 0 ? String.valueOf(shrinkWeight) : "0";
  }

  private String formatSizeBounds() {
    if (minSize == null && maxSize == null) {
      return "";
    }
    return ",%s::%s".formatted(minSize != null ? minSize : "", maxSize != null ? maxSize : "");
  }

  /** Parses a single bracket-content spec, e.g. "grow 50,shrink 100,fill" */
  public static ConstraintSpec parse(String spec) {
    var builder = new Builder();
    for (String part : spec.split(",")) {
      part = part.trim();
      if (part.startsWith("grow")) {
        builder.growWeight(parseWeightValue(part));
      } else if (part.startsWith("shrink")) {
        builder.shrinkWeight(parseWeightValue(part));
      } else if (part.equals("fill")) {
        builder.fill(true);
      } else if (part.contains(":")) {
        parseSizeBounds(part, builder);
      }
    }
    return builder.build();
  }

  /** Parses a full constraint string like "[grow 50][grow 50]" into an array of specs. */
  public static ConstraintSpec[] parseAll(String constraintString, int count) {
    double defaultWeight = 100.0 / count;
    if (constraintString == null || constraintString.isEmpty()) {
      return createDefaults(count, defaultWeight);
    }

    ConstraintSpec[] specs = new ConstraintSpec[count];
    int index = 0, pos = 0;
    while (pos < constraintString.length() && index < count) {
      int open = constraintString.indexOf('[', pos);
      if (open == -1) break;
      int close = constraintString.indexOf(']', open);
      if (close == -1) break;
      specs[index++] = parse(constraintString.substring(open + 1, close));
      pos = close + 1;
    }
    for (int i = index; i < count; i++) {
      specs[i] = new ConstraintSpec(defaultWeight);
    }
    return specs;
  }

  private static ConstraintSpec[] createDefaults(int count, double weight) {
    ConstraintSpec[] specs = new ConstraintSpec[count];
    for (int i = 0; i < count; i++) {
      specs[i] = new ConstraintSpec(weight);
    }
    return specs;
  }

  private static double parseWeightValue(String part) {
    String[] tokens = part.split("\\s+");
    if (tokens.length > 1) {
      try {
        return Double.parseDouble(tokens[1]);
      } catch (NumberFormatException e) {
        // Fall through to default
      }
    }
    return 100.0;
  }

  /** Parses size bounds (min::max) into builder. */
  private static void parseSizeBounds(String part, Builder builder) {
    String[] sizes = part.split(":");
    try {
      if (sizes.length > 0 && !sizes[0].isEmpty()) {
        builder.minSize(Integer.parseInt(sizes[0]));
      }
      // index 1 is the (now removed) preferred slot — skip it
      if (sizes.length > 2 && !sizes[2].isEmpty()) {
        builder.maxSize(Integer.parseInt(sizes[2]));
      }
    } catch (NumberFormatException e) {
      // Ignore invalid size bounds
    }
  }

  public static final class Builder {
    private double growWeight = 100.0;
    private double shrinkWeight = 100.0;
    private boolean fill = false;
    private Integer minSize = null;
    private Integer maxSize = null;

    public Builder growWeight(double weight) {
      this.growWeight = weight;
      return this;
    }

    public Builder shrinkWeight(double weight) {
      this.shrinkWeight = weight;
      return this;
    }

    public Builder fill(boolean fill) {
      this.fill = fill;
      return this;
    }

    public Builder minSize(Integer size) {
      this.minSize = size;
      return this;
    }

    public Builder maxSize(Integer size) {
      this.maxSize = size;
      return this;
    }

    public ConstraintSpec build() {
      return new ConstraintSpec(growWeight, shrinkWeight, fill, minSize, maxSize);
    }
  }
}
