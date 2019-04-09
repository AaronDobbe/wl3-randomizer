import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class GUI extends JPanel implements ActionListener {

    private JButton openButton, genButton;
    private JFileChooser fileChooser;
    private JTextArea log;
    private JScrollPane logScrollPane;
    private JTextField seedField;

    private JCheckBox mapShuffleCheck;
    private JCheckBox bossBoxCheck;
    private JCheckBox axeStartCheck;
    private JCheckBox excludeJunkCheck;

    private ButtonGroup hintsButtonGroup;
    private ButtonGroup musicButtonGroup;


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

        mapShuffleCheck = new JCheckBox("Shuffle world map");
        bossBoxCheck = new JCheckBox("Bosses guard music boxes");
        bossBoxCheck.setSelected(true);
        axeStartCheck = new JCheckBox("Guarantee axe start");
        excludeJunkCheck = new JCheckBox("Remove junk items");

        JLabel hintsLabel = new JLabel("Temple hints:");
        hintsButtonGroup = new ButtonGroup();

        JLabel musicLabel = new JLabel("Music:");
        musicButtonGroup = new ButtonGroup();

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(10,3,10,3);
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 4;
        add(openButton, c);

        c.gridy = 1;
        c.gridwidth = 2;
        add(seedLabel, c);

        c.gridx = 2;
        c.gridwidth = 2;
        add(seedField, c);

        c.gridy = 2;

        c.gridx = 0;
        c.gridwidth = 2;
        add(mapShuffleCheck,c);

        c.gridx = 2;
        add(bossBoxCheck,c);

        c.gridy = 3;

        c.gridx = 0;
        add(axeStartCheck,c);
        c.gridx = 2;
        add(excludeJunkCheck,c);

        c.gridy = 4;

        c.gridx = 0;
        c.gridwidth = 1;
        add(hintsLabel,c);
        c.gridx = 1;
        c.gridwidth = 1;
        JRadioButton hintsVanillaButton = new JRadioButton("Vanilla",false);
        hintsVanillaButton.setActionCommand("vanilla");
        hintsButtonGroup.add(hintsVanillaButton);
        add(hintsVanillaButton,c);
        c.gridx = 2;
        JRadioButton hintsCorrectedButton = new JRadioButton("Corrected",true);
        hintsButtonGroup.add(hintsCorrectedButton);
        hintsCorrectedButton.setActionCommand("corrected");
        add(hintsCorrectedButton,c);
        c.gridx = 3;
        JRadioButton hintsStrategicButton = new JRadioButton("Strategic",false);
        hintsButtonGroup.add(hintsStrategicButton);
        hintsStrategicButton.setActionCommand("strategic");
        add(hintsStrategicButton,c);

        c.gridy = 5;

        c.gridx = 0;
        c.gridwidth = 1;
        add(musicLabel,c);
        c.gridx = 1;
        c.gridwidth = 1;
        JRadioButton musicVanillaButton = new JRadioButton("Vanilla",false);
        musicVanillaButton.setActionCommand("vanilla");
        musicButtonGroup.add(musicVanillaButton);
        add(musicVanillaButton,c);
        c.gridx = 2;
        JRadioButton musicShuffledButton = new JRadioButton("Shuffled",false);
        musicButtonGroup.add(musicShuffledButton);
        musicShuffledButton.setActionCommand("shuffled");
        add(musicShuffledButton,c);
        c.gridx = 3;
        JRadioButton musicChaosButton = new JRadioButton("Chaos",true);
        musicButtonGroup.add(musicChaosButton);
        musicChaosButton.setActionCommand("chaos");
        add(musicChaosButton,c);


        c.gridy = 6;

        c.gridx = 0;
        c.gridwidth = 4;
        add(genButton, c);

        c.gridy = 7;
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
            Map<String,String> options = new HashMap<>();
            options.put("bossBoxes",bossBoxCheck.isSelected() ? "true" : "false");
            options.put("music",musicButtonGroup.getSelection().getActionCommand());
            options.put("axeStart",axeStartCheck.isSelected() ? "true" : "false");
            options.put("hints",hintsButtonGroup.getSelection().getActionCommand());
            options.put("excludeJunk",excludeJunkCheck.isSelected() ? "true" : "false");
            options.put("mapShuffle",mapShuffleCheck.isSelected() ? "true" : "false");
            Main.generateGame(seedField.getText(), options);
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
