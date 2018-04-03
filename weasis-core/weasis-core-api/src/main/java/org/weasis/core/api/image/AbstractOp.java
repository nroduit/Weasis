/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.api.image;

import java.util.HashMap;
import java.util.Map;

import org.weasis.core.api.util.LangUtil;

public abstract class AbstractOp implements ImageOpNode {

    protected HashMap<String, Object> params;

    public AbstractOp() {
        params = new HashMap<>();
    }

    public AbstractOp(AbstractOp op) {
        params = new HashMap<>(op.params);
        clearIOCache();
    }

    @Override
    public void clearParams() {
        params.clear();
    }

    @Override
    public void clearIOCache() {
        for (String key : params.keySet()) {
            if (key.startsWith("op.input") || key.startsWith("op.output")) { //$NON-NLS-1$ //$NON-NLS-2$
                params.put(key, null);
            }
        }
    }

    @Override
    public Object getParam(String key) {
        if (key == null) {
            return null;
        }
        return params.get(key);
    }

    @Override
    public void setParam(String key, Object value) {
        if (key != null) {
            params.put(key, value);
        }
    }

    @Override
    public void setAllParameters(Map<String, Object> map) {
        if (map != null) {
            params.putAll(map);
        }
    }

    @Override
    public void removeParam(String key) {
        if (key != null) {
            params.remove(key);
        }
    }

    @Override
    public boolean isEnabled() {
        return LangUtil.getNULLtoTrue((Boolean) params.get(Param.ENABLE));
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
    }

}
