/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.util;

import java.util.Map;

public class URLParameters {

    private final Map<String, String> headers;
    private final long ifModifiedSince;
    private final int connectTimeout;
    private final int readTimeout;
    private final boolean httpPost;
    private final boolean useCaches;
    private final boolean allowUserInteraction;

    public URLParameters() {
        this(null, null, null, null, null, null, null);
    }

    public URLParameters(Map<String, String> headers) {
        this(headers, null, null, null, null, null, null);
    }

    public URLParameters(Map<String, String> headers, boolean httpPost) {
        this(headers, null, null, httpPost, null, null, null);
    }

    public URLParameters(Map<String, String> headers, int connectTimeout, int readTimeout) {
        this(headers, connectTimeout, readTimeout, null, null, null, null);
    }

    public URLParameters(Map<String, String> headers, Integer connectTimeout, Integer readTimeout, Boolean httpPost,
        Boolean useCaches, Long ifModifiedSince, Boolean allowUserInteraction) {
        this.headers = headers;
        this.ifModifiedSince = ifModifiedSince == null ? 0L : ifModifiedSince;
        this.connectTimeout = connectTimeout == null ? NetworkUtil.getUrlConnectionTimeout() : connectTimeout;
        this.readTimeout = readTimeout == null ? NetworkUtil.getUrlReadTimeout() : readTimeout;
        this.httpPost = httpPost == null ? Boolean.FALSE : httpPost;
        this.useCaches = useCaches == null ? Boolean.TRUE : useCaches;
        this.allowUserInteraction = allowUserInteraction == null ? Boolean.FALSE : allowUserInteraction;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public long getIfModifiedSince() {
        return ifModifiedSince;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public boolean isHttpPost() {
        return httpPost;
    }

    public boolean isUseCaches() {
        return useCaches;
    }

    public boolean isAllowUserInteraction() {
        return allowUserInteraction;
    }

}
