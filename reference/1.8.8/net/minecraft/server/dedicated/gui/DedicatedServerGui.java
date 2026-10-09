package net.minecraft.server.dedicated.gui;

import com.mojang.util.QueueLogAppender;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DedicatedServerGui extends JComponent {
    private static final Font FONT_MONOSPACE = new Font("Monospaced", 0, 12);
    private static final Logger LOGGER = LogManager.getLogger();
    private DedicatedServer server;

    public static void create(DedicatedServer server) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception exception) {
        }

        DedicatedServerGui dedicatedservergui = new DedicatedServerGui(server);
        JFrame jframe = new JFrame("Minecraft server");
        jframe.add(dedicatedservergui);
        jframe.pack();
        jframe.setLocationRelativeTo(null);
        jframe.setVisible(true);
        jframe.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                server.stop();

                while (!server.hasStopped()) {
                    try {
                        Thread.sleep(100L);
                    } catch (InterruptedException interruptedexception) {
                        interruptedexception.printStackTrace();
                    }
                }

                System.exit(0);
            }
        });
    }

    public DedicatedServerGui(DedicatedServer server) {
        this.server = server;
        this.setPreferredSize(new Dimension(854, 480));
        this.setLayout(new BorderLayout());

        try {
            this.add(this.createLogPanel(), "Center");
            this.add(this.createStatsPanel(), "West");
        } catch (Exception exception) {
            LOGGER.error("Couldn't build server GUI", exception);
        }
    }

    private JComponent createStatsPanel() throws Exception {
        JPanel jpanel = new JPanel(new BorderLayout());
        jpanel.add(new PlayerStatsGui(this.server), "North");
        jpanel.add(this.createPlaysPanel(), "Center");
        jpanel.setBorder(new TitledBorder(new EtchedBorder(), "Stats"));
        return jpanel;
    }

    private JComponent createPlaysPanel() throws Exception {
        JList jlist = new PlayerListGui(this.server);
        JScrollPane jscrollpane = new JScrollPane(jlist, 22, 30);
        jscrollpane.setBorder(new TitledBorder(new EtchedBorder(), "Players"));
        return jscrollpane;
    }

    private JComponent createLogPanel() throws Exception {
        JPanel jpanel = new JPanel(new BorderLayout());
        final JTextArea jtextarea = new JTextArea();
        final JScrollPane jscrollpane = new JScrollPane(jtextarea, 22, 30);
        jtextarea.setEditable(false);
        jtextarea.setFont(FONT_MONOSPACE);
        final JTextField jtextfield = new JTextField();
        jtextfield.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String s = jtextfield.getText().trim();
                if (s.length() > 0) {
                    DedicatedServerGui.this.server.queueCommand(s, MinecraftServer.getInstance());
                }

                jtextfield.setText("");
            }
        });
        jtextarea.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
            }
        });
        jpanel.add(jscrollpane, "Center");
        jpanel.add(jtextfield, "South");
        jpanel.setBorder(new TitledBorder(new EtchedBorder(), "Log and chat"));
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                String s;
                while ((s = QueueLogAppender.getNextLogEvent("ServerGuiConsole")) != null) {
                    DedicatedServerGui.this.appendToConsole(jtextarea, jscrollpane, s);
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
        return jpanel;
    }

    public void appendToConsole(JTextArea textArea, JScrollPane scrollPane, String logString) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    DedicatedServerGui.this.appendToConsole(textArea, scrollPane, logString);
                }
            });
        } else {
            Document document = textArea.getDocument();
            JScrollBar jscrollbar = scrollPane.getVerticalScrollBar();
            boolean flag = false;
            if (scrollPane.getViewport().getView() == textArea) {
                flag = jscrollbar.getValue() + jscrollbar.getSize().getHeight() + FONT_MONOSPACE.getSize() * 4 > jscrollbar.getMaximum();
            }

            try {
                document.insertString(document.getLength(), logString, null);
            } catch (BadLocationException badlocationexception) {
            }

            if (flag) {
                jscrollbar.setValue(Integer.MAX_VALUE);
            }
        }
    }
}
