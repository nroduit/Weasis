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

import java.awt.Color;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.weasis.core.util.LangUtil;
import org.weasis.opencv.data.PlanarImage;

/**
 * Base implementation for image operations providing parameter management.
 *
 * <p>The parameter map tracks its own changes: a write that can alter the output, through the
 * setters or directly on {@link #params}, moves the {@link #getRevision() revision}. The input and
 * output images never do. An operation reading something outside its parameters calls {@link
 * #invalidate()} when it changes.
 */
public abstract class AbstractOp implements ImageOpNode {

  protected static final String INPUT_PREFIX = "op.input";
  protected static final String OUTPUT_PREFIX = "op.output";

  private static final Set<Class<?>> VALUE_TYPES =
      Set.of(
          String.class,
          Boolean.class,
          Character.class,
          Byte.class,
          Short.class,
          Integer.class,
          Long.class,
          Float.class,
          Double.class,
          BigInteger.class,
          BigDecimal.class);

  // ImmutableCollections, the nest host of List.of() and Stream.toList()
  private static final Class<?> IMMUTABLE_COLLECTIONS = List.of().getClass().getNestHost();

  protected final Map<String, Object> params;
  private long revision;

  protected AbstractOp() {
    this.params = new TrackedParams();
  }

  protected AbstractOp(AbstractOp op) {
    this.params = new TrackedParams(op.params);
    clearIOCache();
  }

  @Override
  public long getRevision() {
    return revision;
  }

  @Override
  public void invalidate() {
    revision++;
  }

  /**
   * Whether the output is either the input object or a new image that nothing but the chain holds
   * (no cache of the operation, no image of the model). Only then may the {@link SimpleOpManager}
   * release an output it replaced; false by default.
   */
  protected boolean ownsOutput() {
    return false;
  }

  @Override
  public void clearParams() {
    params.clear();
  }

  @Override
  public void clearIOCache() {
    params.keySet().removeIf(AbstractOp::isIOCacheKey);
  }

  private static boolean isIOCacheKey(Object key) {
    return key instanceof String k && (k.startsWith(INPUT_PREFIX) || k.startsWith(OUTPUT_PREFIX));
  }

  /**
   * Whether replacing {@code old} by {@code value} can change what the operation reads. Equal
   * values count as unchanged only when both are immutable: a mutable object may have been modified
   * after it was stored, and setting the same mutable object again is how a caller says it was.
   */
  static boolean isChange(Object old, Object value) {
    if (old == value) {
      return !isImmutable(value);
    }
    return !(isImmutable(old) && isImmutable(value) && Objects.equals(old, value));
  }

  /**
   * Values that cannot change once stored. A record counts as one: the records set as parameters
   * are value carriers, rebuilt rather than modified.
   */
  static boolean isImmutable(Object value) {
    return switch (value) {
      case null -> true;
      case Enum<?> _, Color _, Record _ -> true;
      case List<?> list ->
          list.getClass().getNestHost() == IMMUTABLE_COLLECTIONS
              && list.stream().allMatch(AbstractOp::isImmutable);
      default -> VALUE_TYPES.contains(value.getClass());
    };
  }

  /**
   * The parameter map: every mutation of a key that is not an input or output image moves the
   * revision when the value really changes. The views ({@code keySet()}, {@code values()}, {@code
   * entrySet()}) are not tracked; they are used to drop the input and output images only.
   */
  private final class TrackedParams extends HashMap<String, Object> {

    TrackedParams() {
      super();
    }

    TrackedParams(Map<String, Object> map) {
      super(map);
    }

    private void track(Object key, boolean present, Object old, Object value) {
      if (!isIOCacheKey(key) && (present ? isChange(old, value) : value != null)) {
        revision++;
      }
    }

    @Override
    public Object put(String key, Object value) {
      boolean present = containsKey(key);
      Object old = super.put(key, value);
      track(key, present, old, value);
      return old;
    }

    @Override
    public Object putIfAbsent(String key, Object value) {
      boolean present = containsKey(key);
      Object old = super.putIfAbsent(key, value);
      if (!present || old == null) {
        track(key, present, old, value);
      }
      return old;
    }

    @Override
    public void putAll(Map<? extends String, ?> map) {
      map.forEach(this::put);
    }

    @Override
    public Object remove(Object key) {
      boolean present = containsKey(key);
      Object old = super.remove(key);
      if (present) {
        track(key, true, old, null);
      }
      return old;
    }

    @Override
    public boolean remove(Object key, Object value) {
      boolean removed = super.remove(key, value);
      if (removed) {
        track(key, true, value, null);
      }
      return removed;
    }

    @Override
    public Object replace(String key, Object value) {
      return containsKey(key) ? put(key, value) : null;
    }

    @Override
    public boolean replace(String key, Object oldValue, Object newValue) {
      if (containsKey(key) && Objects.equals(get(key), oldValue)) {
        put(key, newValue);
        return true;
      }
      return false;
    }

    @Override
    public void replaceAll(BiFunction<? super String, ? super Object, ?> function) {
      for (String key : List.copyOf(keySet())) {
        put(key, function.apply(key, get(key)));
      }
    }

    @Override
    public Object computeIfAbsent(String key, Function<? super String, ?> mapping) {
      Object old = get(key);
      if (old != null) {
        return old;
      }
      Object value = mapping.apply(key);
      if (value != null) {
        put(key, value);
      }
      return value;
    }

    @Override
    public Object computeIfPresent(
        String key, BiFunction<? super String, ? super Object, ?> remapping) {
      Object old = get(key);
      return old == null ? null : storeComputed(key, remapping.apply(key, old));
    }

    @Override
    public Object compute(String key, BiFunction<? super String, ? super Object, ?> remapping) {
      return storeComputed(key, remapping.apply(key, get(key)));
    }

    @Override
    public Object merge(
        String key, Object value, BiFunction<? super Object, ? super Object, ?> remapping) {
      Object old = get(key);
      return storeComputed(key, old == null ? value : remapping.apply(old, value));
    }

    private Object storeComputed(String key, Object value) {
      if (value == null) {
        remove(key);
      } else {
        put(key, value);
      }
      return value;
    }

    @Override
    public void clear() {
      if (keySet().stream().anyMatch(k -> !isIOCacheKey(k))) {
        revision++;
      }
      super.clear();
    }
  }

  @Override
  public Object getParam(String key) {
    return params.get(key);
  }

  @Override
  public void setParam(String key, Object value) {
    if (key != null) {
      params.put(key, value);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T getParam(String key, Class<T> ignored) {
    return (T) params.get(key);
  }

  protected <T> T getParam(String key, Class<T> type, T defaultValue) {
    T value = getParam(key, type);
    return value != null ? value : defaultValue;
  }

  @Override
  public void setAllParameters(Map<String, Object> map) {
    if (map != null) {
      params.putAll(map);
    }
  }

  @Override
  public void removeParam(String key) {
    params.remove(key);
  }

  @Override
  public boolean isEnabled() {
    return LangUtil.nullToTrue((Boolean) params.get(Param.ENABLE));
  }

  @Override
  public void setEnabled(boolean enabled) {
    params.put(Param.ENABLE, enabled);
  }

  @Override
  public String getName() {
    return (String) params.get(Param.NAME);
  }

  @Override
  public void setName(String name) {
    if (name != null) {
      params.put(Param.NAME, name);
    }
  }

  @Override
  public void handleImageOpEvent(ImageOpEvent event) {
    // Default implementation does nothing
  }

  /**
   * Retrieves the source image from parameters.
   *
   * @return the source PlanarImage
   * @throws IllegalArgumentException if INPUT_IMG is not a PlanarImage
   */
  protected PlanarImage getSourceImage() {
    return switch (params.get(Param.INPUT_IMG)) {
      case PlanarImage img -> img;
      case null -> throw new IllegalArgumentException("INPUT_IMG parameter is missing");
      default -> throw new IllegalArgumentException("INPUT_IMG parameter must be a PlanarImage");
    };
  }
}
