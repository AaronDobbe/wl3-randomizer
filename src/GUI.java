import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.io.File;
import java.io.IOException;

public class GUI extends JPanel implements ActionListener {

    private JButton openButton, genButton;
    private JFileChooser fileChooser;
    private JTextArea log;
    private JScrollPane logScrollPane;
    private JTextField seedField;

    public GUI() {
        super(new GridBagLayout());

        log = new JTextArea(5,40);
        log.setMargin(new Insets(5,5,5,5));
        log.setEditable(false);
        logScrollPane = new JScrollPane(log);
        logScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        fileChooser = new JFileChooser();

        openButton = new JButton("Open Vanilla ROM...");
        openButton.addActionListener(this);

        genButton = new JButton("Generate Randomized Game");
        genButton.setEnabled(false);
        genButton.addActionListener(this);

        JLabel seedLabel = new JLabel("Seed (blank for random):");
        seedField = new JTextField(11);

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(10,3,10,3);
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        add(openButton, c);

        c.gridy = 1;
        c.gridwidth = 1;
        add(seedLabel, c);

        c.gridx = 1;
        add(seedField, c);

        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 2;
        add(genButton, c);

        c.gridy = 3;
        add(logScrollPane, c);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == openButton) {
            int returnVal = fileChooser.showOpenDialog(this);

            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();

                try {
                    if (Main.verifyFile(file)) {
                        Main.setVanillaFile(file.getAbsolutePath());
                        genButton.setEnabled(true);
                        log("Opened vanilla ROM.");
                    }
                    else {
                        log("Error occurred while opening vanilla ROM: File is not a vanilla WL3 ROM");
                    }
                } catch (IOException ioe) {
                    log("Error occurred while opening vanilla ROM: " + ioe.getMessage());
                }
            }
        }
        else if (e.getSource() == genButton) {
            Main.generateGame(seedField.getText());
        }
    }

    /**
     * Output a message to the on-screen log.
     *
     * @param message the message to display
     */
    public void log(String message) {
        log.append(message + "\n");
        JScrollBar verticalBar = logScrollPane.getVerticalScrollBar();
        AdjustmentListener downScroller = new AdjustmentListener() {
            @Override
            public void adjustmentValueChanged(AdjustmentEvent e) {
                Adjustable adjustable = e.getAdjustable();
                adjustable.setValue(adjustable.getMaximum());
                verticalBar.removeAdjustmentListener(this);
            }
        };
        verticalBar.addAdjustmentListener(downScroller);
    }
}
