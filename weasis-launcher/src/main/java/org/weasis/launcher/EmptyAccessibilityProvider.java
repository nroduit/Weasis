package org.weasis.launcher;

import javax.accessibility.AccessibilityProvider;

public class EmptyAccessibilityProvider extends AccessibilityProvider {
    public String getName() {
        return "EmptyAccessibilityProvider";
    }

    public void activate() {}
}