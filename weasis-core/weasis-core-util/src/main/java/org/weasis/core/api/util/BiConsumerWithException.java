package org.weasis.core.api.util;

@FunctionalInterface
public interface BiConsumerWithException<T, U, E extends Exception> {

    /**
     * Performs this operation on the given arguments.
     *
     * @param t
     *            the first input argument
     * @param u
     *            the second input argument
     */
    void accept(T t, U u) throws E;

}