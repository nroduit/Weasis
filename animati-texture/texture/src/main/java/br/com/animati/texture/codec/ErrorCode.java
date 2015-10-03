/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informatica Ltda.
 * (http://www.animati.com.br)
 */
package br.com.animati.texture.codec;

/**
 *
 * @author Gabriela Carla Bauermann (gabriela@animati.com.br)
 * @version 2015, 14 Jul.
 */
public enum ErrorCode {

    err500("500", "This series has variable RescaleIntercept and/or RescaleSlope. Can't be rendered on H.A.",
                    Messages.getString("err500")),
    err501("501", "Can't send frame to texture: diferent frame sizes or inconsistent size information.",
                    Messages.getString("err501"));

    private final String value;
    private final String logMessage;
    private final String userMessage;

    ErrorCode(String value, String msg, String userMsg) {
        this.value = value;
        logMessage = msg;
        userMessage = userMsg;
    }

    public String getCode() {
        return value;
    }

    public String getLogMessage() {
        return logMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }

}
