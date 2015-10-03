/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */
package br.com.animati.texture.mpr3dview;

import java.awt.Window;
import java.awt.event.ActionEvent;

import javax.swing.Action;
import javax.swing.JOptionPane;

import br.com.animati.texture.codec.FormattedException;
import br.com.animati.texture.mpr3dview.internal.Messages;

/**
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2015, 08 Apr.
 */
public class ExceptionUtil {

    public static void showUserMessage(FormattedException ex, Window win) {

        if (ex == null) {
            return;
        }
        StringBuilder msg = new StringBuilder();
        msg.append("<html><b>");
        msg.append(Messages.getString("ExceptionUtil.msgCode"));
        msg.append(" ").append(ex.getErrorCode()).append("</b></body></html>");
        msg.append("\n\n");
        msg.append(ex.getUserMessage());

        if (ex.getHelpAction() == null) {
            JOptionPane.showMessageDialog(win, msg, ex.getTitle(), JOptionPane.WARNING_MESSAGE);
        } else {
            Action helpAction = ex.getHelpAction();
            int opt = JOptionPane.showOptionDialog(win, msg, ex.getTitle(), JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE, null, new String[] { (String) helpAction.getValue(Action.NAME), "OK" },
                "default");
            if (JOptionPane.OK_OPTION == opt) {
                helpAction.actionPerformed(new ActionEvent(opt, opt, null));
            }
        }
    }

}
