/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.codec.wado;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.StringUtil;

public class WadoParameters {
    private static final Logger LOGGER = LoggerFactory.getLogger(WadoParameters.class);

    public static final String TAG_DOCUMENT_ROOT = "wado_query"; //$NON-NLS-1$
    public static final String TAG_WADO_URL = "wadoURL"; //$NON-NLS-1$
    public static final String TAG_WADO_ONLY_SOP_UID = "requireOnlySOPInstanceUID"; //$NON-NLS-1$
    public static final String TAG_WADO_ADDITIONNAL_PARAMETERS = "additionnalParameters"; //$NON-NLS-1$
    public static final String TAG_WADO_OVERRIDE_TAGS = "overrideDicomTagsList"; //$NON-NLS-1$
    public static final String TAG_WADO_WEB_LOGIN = "webLogin"; //$NON-NLS-1$
    public static final String TAG_HTTP_TAG = "httpTag"; //$NON-NLS-1$

    private final String archiveID;
    private final String wadoURL;
    private final boolean requireOnlySOPInstanceUID;
    private final String additionnalParameters;
    private final int[] overrideDicomTagIDList;
    private final String overrideDicomTagsList;
    private final String webLogin;
    private final List<WadoParameters.HttpTag> httpTaglist;

    public WadoParameters(String archiveID, String wadoURL, boolean requireOnlySOPInstanceUID,
        String additionnalParameters, String overrideDicomTagsList, String webLogin) {
        if (wadoURL == null) {
            throw new IllegalArgumentException("wadoURL cannot be null"); //$NON-NLS-1$
        }
        this.archiveID = archiveID;
        this.wadoURL = wadoURL;
        this.httpTaglist = new ArrayList<>(3);
        // Add possible session tags
        if (BundleTools.SESSION_TAGS_FILE.size() > 0) {
            for (Iterator<Entry<String, String>> iter = BundleTools.SESSION_TAGS_FILE.entrySet().iterator(); iter
                .hasNext();) {
                Entry<String, String> element = iter.next();
                addHttpTag(element.getKey(), element.getValue());
            }
        }
        this.webLogin = webLogin == null ? null : webLogin.trim();
        this.requireOnlySOPInstanceUID = requireOnlySOPInstanceUID;
        this.additionnalParameters = additionnalParameters == null ? "" : additionnalParameters; //$NON-NLS-1$
        this.overrideDicomTagsList = overrideDicomTagsList;
        if (StringUtil.hasText(overrideDicomTagsList)) {
            String[] val = overrideDicomTagsList.split(","); //$NON-NLS-1$
            overrideDicomTagIDList = new int[val.length];
            for (int i = 0; i < val.length; i++) {
                try {
                    overrideDicomTagIDList[i] = Integer.decode(val[i].trim());
                } catch (NumberFormatException e) {
                    LOGGER.error("Cannot read dicom tag list", e); //$NON-NLS-1$
                }
            }
        } else {
            overrideDicomTagIDList = null;
        }
    }

    public List<WadoParameters.HttpTag> getHttpTaglist() {
        return httpTaglist;
    }

    public void addHttpTag(String key, String value) {
        if (key != null && value != null) {
            httpTaglist.add(new HttpTag(key, value));
        }
    }

    public String getWebLogin() {
        return webLogin;
    }

    public String getArchiveID() {
        return archiveID;
    }

    public String getWadoURL() {
        return wadoURL;
    }

    public boolean isRequireOnlySOPInstanceUID() {
        return requireOnlySOPInstanceUID;
    }

    public String getAdditionnalParameters() {
        return additionnalParameters;
    }

    public int[] getOverrideDicomTagIDList() {
        return overrideDicomTagIDList;
    }

    public String getOverrideDicomTagsList() {
        return overrideDicomTagsList;
    }

    public boolean isOverrideTag(TagW tagElement) {
        if (overrideDicomTagIDList != null) {
            int tagID = tagElement.getId();
            for (int overTag : overrideDicomTagIDList) {
                if (tagID == overTag) {
                    return true;
                }
            }
        }
        return false;
    }

    public static class HttpTag {
        private final String key;
        private final String value;

        public HttpTag(String key, String value) {
            this.key = key;
            this.value = value;
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }

    }
}
