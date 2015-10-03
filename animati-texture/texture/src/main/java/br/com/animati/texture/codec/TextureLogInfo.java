/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */

package br.com.animati.texture.codec;

import java.awt.Color;
import java.awt.Component;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 *
 * @author Rafaelo Pinheiro (rafaelo@animati.com.br)
 * @version 09/08/2013
 */
public class TextureLogInfo {

    private JTextArea textArea = new JTextArea(15, 40);
    private String text = "LOG ABOUT TEXTURE BUILD:";
    private JPanel panelNorth = new JPanel();
    private static final String NAME = "Log Textures";

    public TextureLogInfo() {
        createPanelLogInfo();
    }

    private void createPanelLogInfo() {
        JScrollPane scrollPane = new JScrollPane(textArea);
        textArea.setEditable(false);
        textArea.setBackground(Color.WHITE);
        textArea.setForeground(Color.BLACK);
        panelNorth.add(scrollPane);
    }

    public void showAsDialog(Component parent) {
        JOptionPane.showConfirmDialog(parent, panelNorth, NAME, JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.INFORMATION_MESSAGE);
    }

    public void writeText(String text) {
        this.text = this.text + "\n" + text;
        textArea.setText(this.text);
    }

}
