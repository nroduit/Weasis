/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */

package br.com.animati.texture.codec;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 *
 * @author Rafaelo Pinheiro (rafaelo@animati.com.br)
 * @version 26/07/2013
 */
public class Messages {

    private static final String BUNDLE_NAME = "br.com.animati.texture.codec.messages";

    private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);

    private Messages() {
    }

    public static String getString(String key) {
        try {
            return RESOURCE_BUNDLE.getString(key);
        } catch (MissingResourceException e) {
            return '!' + key + '!';
        }
    }
}
