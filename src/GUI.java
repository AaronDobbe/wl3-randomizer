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
    private JCheckBox keyShuffleCheck;
    private JCheckBox bossBoxCheck;
    private JCheckBox axeStartCheck;
    private JCheckBox powerStartCheck;
    private JCheckBox excludeJunkCheck;
    private JCheckBox utilityCheck;
    private JCheckBox openCheck;
    private JCheckBox golfShuffleCheck;
    private JCheckBox levelColorCheck;
    private JCheckBox enemyColorCheck;
    private JCheckBox chestColorCheck;
    private JCheckBox xrayCheck;
    private JCheckBox cutsceneSkipCheck;

    private JComboBox<String> logicComboBox;
    private JComboBox<String> hintsComboBox;
    private JComboBox<String> musicComboBox;


    public GUI() {
        super(new GridLayout(1,1));

        log = new JTextArea(5,50);
        log.setMargin(new Insets(5,5,5,5));
        log.setEditable(false);
        logScrollPane = new JScrollPane(log);
        logScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        logScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        log.append("Welcome to WL3 Randomizer!\n");
        log.append("Use the tabs above to see all available options, and hover over anything for an explanation.\n");
        log.append("Be warned - options marked with `!` significantly increase the difficulty of the game!\n");
//        setPreferredSize(new Dimension(640, 350));

        fileChooser = new JFileChooser();

        openButton = new JButton("Open Vanilla ROM...");
        openButton.addActionListener(this);
        openButton.setToolTipText("Choose a clean ROM of Wario Land 3.");

        genButton = new JButton("Generate Randomized Game (may take a while)");
        genButton.setEnabled(false);
        genButton.addActionListener(this);
        genButton.setToolTipText("Once you've loaded up a ROM and chosen all your settings, click here!");

        JLabel seedLabel = new JLabel("Seed:");
        seedField = new JTextField(11);
        seedField.setToolTipText("Input a seed code to generate the same game as someone else. Leave blank if you don't have a code to enter.");

        JLabel logicLabel = new JLabel("Item placement difficulty:");
        String[] logicOptions = {"Easy", "Normal", "Hard!", "Hard + Minor Glitches!!", "Merciless!!!!"};
        logicComboBox = new JComboBox(logicOptions);
        logicComboBox.setSelectedIndex(1);
        logicComboBox.setToolTipText("<html><b>Easy</b>: Ensures you can find helpful items before difficult situations, and avoids hiding things in obscure locations.<br>" +
                "<b>Normal</b>: The standard experience for players who know the original game well.<br>" +
                "<b>Hard</b>: A hardcore difficulty that requires speedrun-level tricks and very difficult/odd maneuvers, but no glitches.<br>" +
                "<b>Hard + Minor Glitches</b>: A super-hardcore difficulty that also requires wallclips, but no out-of-bounds or screenwrapping.<br>" +
				"<b>Merciless</b>: An extremely hardcore difficulty that also requires screen wraps, wrong warps, and double boosts.</html>");
        mapShuffleCheck = new JCheckBox("Map shuffle");
        mapShuffleCheck.setToolTipText("<html>Shuffles the location of every level on the world map.<br>" +
                "<i>(If you're playing with cutscenes enabled, this will mess up the names and locations of the levels displayed during cutscenes.)</i></html>\n");
        keyShuffleCheck = new JCheckBox("Key shuffle!");
        keyShuffleCheck.setToolTipText("Shuffles the locations of all keys and music coins in each level.");
        bossBoxCheck = new JCheckBox("Restrict music boxes");
        bossBoxCheck.setSelected(true);
        bossBoxCheck.setToolTipText("<html>If checked, the five music boxes will be guarded by bosses, just like in the original game.<br>" +
                "If unchecked, they could be anywhere.</html>");
        axeStartCheck = new JCheckBox("Force axe start");
        axeStartCheck.setToolTipText("<html>If checked, the axe will appear in the first chest of the first level.<br>" +
                "<i>(Recommended for beginners, because you'll always be able to access The Temple for a hint.)</i></html>");
        powerStartCheck = new JCheckBox("Powerful start");
        powerStartCheck.setToolTipText("Begin the game with a few randomly selected powerups. (Dramatically speeds up randomization time.)");
        excludeJunkCheck = new JCheckBox("No junk items");
        excludeJunkCheck.setToolTipText("<html>Removes all useless treasures from the game, replacing them with empty chests.<br>" +
                "<i>(You can use the magnifying glass or pause menu to check which chests are empty.)</i></html>");
        utilityCheck = new JCheckBox("QoL Items starter kit");
        utilityCheck.setToolTipText("Gives you the magnifying glass and the time button from the start of the game.");
        openCheck = new JCheckBox("\"Open Mode\" starter kit");
        openCheck.setToolTipText("<html>Gives you a pack of items that open the four borders of the map from the word go.<br>" +
                "<i>(Overrides the \"Force axe start\" option.)</i></html>");
        golfShuffleCheck = new JCheckBox("Shuffle golf!");
        golfShuffleCheck.setToolTipText("<html>Shuffles the order in which golf courses appear over the course of the game.<br>" +
                "Including the really annoying ones from the GAME tower.<br>" +
                "<i>(You will probably regret checking this box.)</i></html>");
        levelColorCheck = new JCheckBox("Random BG palettes");
        levelColorCheck.setSelected(true);
        levelColorCheck.setToolTipText("Changes the colors of every level, background, and various menus.");
        enemyColorCheck = new JCheckBox("Random object palettes");
        enemyColorCheck.setSelected(true);
        enemyColorCheck.setToolTipText("Changes the colors of enemies and other in-game objects.");
        chestColorCheck = new JCheckBox("Random chest palettes");
        chestColorCheck.setToolTipText("<html>Changes the palettes of the four chests + keys in each level.<br>" +
                "<i>(This doesn't change anything about the game, but it will probably be very confusing.)</i>");
        xrayCheck = new JCheckBox("Reveal secret paths");
        xrayCheck.setToolTipText("<html>Reveals most hidden passageways. <i>(Helpful for new players!)</i>");
        cutsceneSkipCheck = new JCheckBox("Skip cutscenes");
        cutsceneSkipCheck.setToolTipText("<html>Lets you skip major cutscenes with the B button, and removes the cutscenes that play after getting a treasure.<br>" +
                "<i>(Tutorials can't be skipped yet.)</i><br>" +
                "<i>(For your first game, I recommend leaving this off so you can see what the items do.)</i></html>");

        JLabel hintsLabel = new JLabel("Temple hints:");
        String[] hintsOptions = {"Unhelpful", "Next item", "Next quest item (Strategic)"};
        hintsComboBox = new JComboBox(hintsOptions);
        hintsComboBox.setSelectedIndex(1);
        hintsComboBox.setToolTipText("<html>Changes the behavior of hints from the Temple.<br>" +
                "<b>Unhelpful:</b> The Temple missed the memo that you're playing a randomizer. Hints probably won't make any sense.<br>" +
                "<b>Next item:</b> Hints will always point you to a level you can get an item in.<br>" +
                "<b>Next quest item:</b> Hints will tell you the location of the next item required to beat the final boss, whether or not it's possible to get that item right now.</html>");

        JLabel musicLabel = new JLabel("Music shuffle:");
        String[] musicOptions = {"Off", "On", "Chaos"};
        musicComboBox = new JComboBox(musicOptions);
        musicComboBox.setSelectedIndex(1);
        musicComboBox.setToolTipText("<html><b>Off:</b> Leaves the music unchanged.<br>" +
                "<b>On:</b> Shuffles all music. Each track will occur in exactly as many places as it did in the original game.<br>" +
                "<b>Chaos:</b> Randomly changes the music with no guarantees about how often each track appears. <i>(This can be hilarious, or very annoying.)</i></html>");

        ToolTipManager.sharedInstance().setInitialDelay(500);
        ToolTipManager.sharedInstance().setDismissDelay(Integer.MAX_VALUE);

        JTabbedPane tabbedPane = new JTabbedPane();

        JComponent mainPanel = new JPanel(new GridBagLayout());

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(10,3,10,3);
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 4;
        mainPanel.add(openButton, c);

        c.gridy++;
        c.gridwidth = 2;
        mainPanel.add(seedLabel, c);

        c.gridx = 2;
        c.gridwidth = 2;
        mainPanel.add(seedField, c);

        c.gridy++;

        c.gridx = 0;
        c.gridwidth = 4;
        mainPanel.add(genButton, c);

        c.gridy++;
        mainPanel.add(logScrollPane, c);

        tabbedPane.addTab("Main", null, mainPanel, "Load your vanilla ROM and generate your game here.");

        JComponent itemPanel = new JPanel(new GridBagLayout());
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;

        itemPanel.add(excludeJunkCheck,c);

        c.gridy++;
        itemPanel.add(utilityCheck,c);

        c.gridy++;
        itemPanel.add(powerStartCheck,c);

        c.gridy++;
        itemPanel.add(openCheck,c);

        tabbedPane.addTab("Items", null, itemPanel, "Adjust the item pool and choose your starting items.");

        JComponent logicPanel = new JPanel(new GridBagLayout());
        c.gridx = 0;
        c.gridy = 0;

        c.gridx = 0;
        c.gridwidth = 2;
        logicPanel.add(logicLabel,c);
        c.gridx = 2;
        logicPanel.add(logicComboBox, c);

        c.gridy++;

        c.gridx = 0;
        c.gridwidth = 4;
        logicPanel.add(mapShuffleCheck,c);

        c.gridy++;

        logicPanel.add(keyShuffleCheck,c);

        c.gridy++;

        logicPanel.add(bossBoxCheck,c);

        c.gridy++;

        logicPanel.add(axeStartCheck,c);

        tabbedPane.addTab("Logic", null, logicPanel, "Choose what gets shuffled, and how it gets shuffled.");

        JComponent otherPanel = new JPanel(new GridBagLayout());
        c.gridy = 0;

        otherPanel.add(golfShuffleCheck,c);

        c.gridy++;

        c.gridwidth = 2;
        otherPanel.add(hintsLabel,c);
        c.gridx = 2;
        otherPanel.add(hintsComboBox, c);
        c.gridx = 0;

        tabbedPane.addTab("Other", null, otherPanel, "Miscellaneous game options.");

        JComponent personalPanel = new JPanel(new GridBagLayout());
        c.gridy = 0;

        personalPanel.add(levelColorCheck,c);

        c.gridx = 2;

        personalPanel.add(enemyColorCheck,c);

        c.gridx = 0;
        c.gridy++;

        personalPanel.add(chestColorCheck,c);

        c.gridx = 2;

        personalPanel.add(cutsceneSkipCheck,c);

        c.gridy++;
        c.gridx = 0;

        personalPanel.add(xrayCheck,c);

        c.gridy++;

        c.gridwidth = 2;
        personalPanel.add(musicLabel,c);
        c.gridx = 2;
        personalPanel.add(musicComboBox, c);

        tabbedPane.addTab("Personal", null, personalPanel, "Set cosmetic preferences here.");

        add(tabbedPane);
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
            String[] logicOptions = {"easy", "normal", "hard", "minorglitches", "merciless"};
            options.put("difficulty", logicOptions[logicComboBox.getSelectedIndex()]);
            options.put("restrictedMusicBoxes",bossBoxCheck.isSelected() ? "true" : "false");
            String[] musicOptions = {"off", "on", "chaos"};
            options.put("musicShuffle",musicOptions[musicComboBox.getSelectedIndex()]);
            options.put("axeStart",axeStartCheck.isSelected() ? "true" : "false");
            options.put("powerStart",powerStartCheck.isSelected() ? "true" : "false");
            options.put("utilityStart",utilityCheck.isSelected() ? "true" : "false");
            options.put("openStart",openCheck.isSelected() ? "true" : "false");
            String[] hintsOptions = {"unhelpful", "nextitem", "strategic"};
            options.put("hints",hintsOptions[hintsComboBox.getSelectedIndex()]);
            options.put("excludeJunk",excludeJunkCheck.isSelected() ? "true" : "false");
            options.put("mapShuffle",mapShuffleCheck.isSelected() ? "true" : "false");
            options.put("keyShuffle",keyShuffleCheck.isSelected() ? "true" : "false");
            options.put("golfShuffle",golfShuffleCheck.isSelected() ? "true" : "false");
            options.put("levelColors",levelColorCheck.isSelected() ? "true" : "false");
            options.put("enemyColors",enemyColorCheck.isSelected() ? "true" : "false");
            options.put("chestColors",chestColorCheck.isSelected() ? "true" : "false");
            options.put("cutsceneSkip",cutsceneSkipCheck.isSelected() ? "true" : "false");
            options.put("revealSecrets",xrayCheck.isSelected() ? "true" : "false");
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
