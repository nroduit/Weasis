package org.weasis.core.api.util;

/**
 * @author Nicolas Roduit
 *
 * @param <T>
 *            set the Type of the returning element
 */
@FunctionalInterface
public interface Copyable<T> {
    /**
     * @return a new instance with identical values
     */
    T copy();
}