package org.weasis.acquire.explorer.gui.central.component;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.core.bean.Serie;
import org.weasis.acquire.explorer.gui.control.AcquirePublishPanel;
import org.weasis.core.api.gui.util.JMVUtils;

public class SerieButtonList extends JScrollPane {
    private static final long serialVersionUID = 3875335843304715915L;
    protected static final Logger LOGGER = LoggerFactory.getLogger(SerieButtonList.class);

    private static final JPanel panel1 = new JPanel(new BorderLayout());
    private static final JPanel panel2 = new JPanel(new GridLayout(0, 1));

    private SortedSet<SerieButton> btns = new TreeSet<>();
    private JPanel publishPanel = new AcquirePublishPanel();

    public SerieButtonList() {
        super(panel1, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        JMVUtils.setPreferredWidth(panel1, 200);
        JMVUtils.setPreferredHeight(panel1, 300);

        panel1.add(panel2, BorderLayout.NORTH);
        panel1.add(publishPanel, BorderLayout.SOUTH);
    }

    public void addButton(SerieButton btn) {
        btns.add(btn);
        int index = btns.headSet(btn).size();
        panel2.add(btn, index);
    }

    public Set<SerieButton> getButtons() {
        return btns;
    }

    private void remove(SerieButton btn) {
        if (btns.remove(btn)) {
            panel2.remove(btn);
        }
    }

    public Optional<SerieButton> removeBySerie(final Serie serie) {
        btns.stream().filter(sb -> sb.getSerie().equals(serie)).findFirst().ifPresent(sb -> remove(sb));
        return btns.stream().sorted().findFirst();
    }
}
