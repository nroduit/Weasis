/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */
package br.com.animati.texture.codec;

import javax.swing.Action;

/**
 * Used to encapsulate information to show to user and to log.
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2015, 08 Apr.
 */
public class FormattedException extends Exception {

    private String errorCode;
    private String userMessage;
    private Action helpAction;

    public FormattedException(String code, String logMessage, Throwable cause) {
        this(code, logMessage, cause, null, null);
    }

    public FormattedException(String code, String logMessage, String userMessage) {
        this(code, logMessage, null, userMessage, null);
    }

    public FormattedException(String code, String logMessage, Throwable cause, String userMessage) {
        this(code, logMessage, cause, userMessage, null);
    }

    public FormattedException(String code, String logMessage, Throwable cause, String userMessage, Action helpAction) {
        super(logMessage, cause);
        errorCode = code;
        this.userMessage = userMessage;
        this.helpAction = helpAction;
    }

    public String getLogMessage() {
        return getMessage();
    }

    public String getUserMessage() {
        return userMessage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getTitle() {
        return "3D Viewer";
    }

    public Action getHelpAction() {
        return helpAction;
    }

}
