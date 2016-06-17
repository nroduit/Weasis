package org.weasis.core.api.util;

@FunctionalInterface
public interface Copyable<T> {
    T copy();
}