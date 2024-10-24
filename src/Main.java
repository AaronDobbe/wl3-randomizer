import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import keyshuffle.KeyLocation;
import keyshuffle.Level;

import javax.swing.*;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class Main {
    private static String[] locationNames;
    private static int[] finalTreasures;

    private static Integer[] worldMap;

    private static Level[] allKeyLocations;
    private static Level[] finalKeyLocations;
    private static boolean keyShuffle;
    private static boolean enableNewLogic;
    private static boolean openMode;
    private static boolean utilityStart;
    private static boolean itemStart;
    private static boolean mapShuffle;
    private static boolean powerfulStart;
    private static boolean fullPowerStart;
    private static boolean axeStart;
    private static int difficulty;
    private static List<Integer> startingItems;
    private static final int NUM_POWERS = 3;

    private static int fails = 0;

    private static String vanillaFileLocation;

    private static GUI gui;

    private static final String VERSION = "v0.12.0-RC4";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                Main.createGUI();
            }
        });
    }

    /**
     * Generate a randomized game.
     *
     * @param userSeed  Seed provided by the user. null if no seed was specified.
     */
    public static void generateGame(String userSeed, Map<String,String> options) {
        fails = 0;
        // separate junk items from non-junk items
        Integer[] junk = {Items.ROCKETSHIP, Items.POKEMON_PIKACHU, Items.FIGHTER, Items.TELEPHONE, Items.CROWN,
                      Items.TIME_BUTTON, Items.RUBY, Items.EMERALD, Items.SAPPHIRE, Items.CLUBS, Items.SPADES,
                      Items.HEARTS, Items.DIAMONDS, Items.CLAY_FIGURE, Items.SABRE, Items.GLASS, Items.TEAPOT,
                      Items.MAGNIFYING_GLASS, Items.UFO, Items.CAR, Items.TRAIN, Items.RED_CRAYON, Items.BROWN_CRAYON,
                      Items.YELLOW_CRAYON, Items.GREEN_CRAYON, Items.CYAN_CRAYON, Items.BLUE_CRAYON, Items.PINK_CRAYON};
        List<Integer> junkList = Arrays.asList(junk);
        List<Integer> inventory = new Vector<>();
        for (int i = 1; i <= 0x64; i++) {
            if (!junkList.contains(i)) {
                inventory.add(i);
            }
        }
        // init locations
        List<Integer> treasures = new ArrayList<>(100);
        List<Integer> locations = new ArrayList<>(100);
        locationNames = new String[100];
        for (int i = 0; i < 100; i++) {
            locationNames[i] = "" + "NWSEE".charAt(i/24) + ((i >= 96) ? '7' : Character.forDigit(((i % 24)/4) + 1, 10)) + "SRGB".charAt(i%4);
            locations.add(i);
            treasures.add(null);
        }
        finalTreasures = new int[100];
        finalKeyLocations = new Level[25];
        keyShuffle = options.containsKey("keyShuffle") && "true".equals(options.get("keyShuffle"));

        try {
            // load list of all potential key locations
            allKeyLocations = new Level[25];
            Gson gson = new GsonBuilder().create();
            InputStream baseDiff = Main.class.getResourceAsStream("/keyshuffle/keyLocations.json");
            BufferedReader br = new BufferedReader(new InputStreamReader(baseDiff));
            String keyLocStr = br.readLine();
            br.close();
            allKeyLocations = gson.fromJson(keyLocStr,allKeyLocations.getClass());
        } catch (IOException e) {
            gui.log(e.getMessage());
        }
        List<Level> levelList = new ArrayList<>();
        if (keyShuffle) {
            // init list of empty levels
            for (int i = 0; i < 25; i++) {
                levelList.add(new Level(i, locationNames[i * 4].substring(0, 2)));
            }
        }
        else {
            try {
                // init list of levels with vanilla key placements
                Gson gson = new GsonBuilder().create();
                InputStream baseDiff = Main.class.getResourceAsStream("keyshuffle/keyLocations_vanilla.json");
                BufferedReader br = new BufferedReader(new InputStreamReader(baseDiff));

                String keyLocStr = br.readLine();
                br.close();
                Level[] levelArray = new Level[25];
                levelArray = gson.fromJson(keyLocStr,levelArray.getClass());
                levelList = Arrays.asList(levelArray);
            } catch (IOException e) {
                gui.log(e.getMessage());
            }
        }

        List<Integer> mapList = new ArrayList<Integer>();
        for (int i = 0; i < 25; i++) {
            mapList.add(i);
        }
        mapShuffle = options.containsKey("mapShuffle") && "true".equals(options.get("mapShuffle"));

        // keep unshuffled lists in case we need re-randomization
        List<Integer> pureInventory = new ArrayList<>(inventory);
        List<Integer> pureLocations = new ArrayList<>(locations);
        List<Integer> pureMapList = new ArrayList<>(mapList);
        List<Level> pureLevelList = cloneLevelList(levelList);

        // Set up the random object
        Random seedRNG = new Random();
        long seed;
        if (userSeed == null || userSeed.length() == 0) {
            seed = seedRNG.nextLong();
        }
        else if (userSeed.length() != 11) {
            gui.log("Invalid seed. Please double-check the seed and try again.");
            return;
        }
        else {
            try {
                seed = decodeSeed(userSeed);
            } catch (Exception e) {
                gui.log("Invalid seed. Please double-check the seed and try again.");
                fails = 0;
                return;
            }
        }

        Random rng = new Random(seed);

        openMode = options.containsKey("openStart") && !options.get("openStart").equals("false");
        utilityStart = options.containsKey("utilityStart") && !options.get("utilityStart").equals("false");
        axeStart = !openMode && options.containsKey("axeStart") && options.get("axeStart").equals("true");
        powerfulStart = options.containsKey("powerStart") && !options.get("powerStart").equals("false");
        fullPowerStart = options.containsKey("powerStart") && options.get("powerStart").equals("full");
        enableNewLogic = true;
        itemStart = openMode || powerfulStart || utilityStart;

        switch (options.get("difficulty")) {
            case "easy":
                difficulty = Difficulty.EASY;
                break;
            case "normal":
                difficulty = Difficulty.NORMAL;
                break;
            case "hard":
                difficulty = Difficulty.HARD;
                break;
            case "minorglitches":
                difficulty = Difficulty.S_HARD;
                break;
			case "merciless": //added MERCILESS difficulty
				difficulty = Difficulty.MERCILESS;
				break;
        }

        // randomize lists of non-junk items and locations
        List<Integer> leftInventory = new Vector<>();
        List<List<Integer>> keyIndexes = new ArrayList<>();

        int attempts = 0;
        // attempt to place treasures
        boolean bossBoxes = options.containsKey("restrictedMusicBoxes") && options.get("restrictedMusicBoxes").equals("true");
        while (!prepareLists(inventory, leftInventory, locations, treasures, mapList, levelList, keyIndexes, rng)
                || ((bossBoxes && !placeItemsAssumed(leftInventory, inventory, locations, treasures, levelList, keyIndexes, 5))
                || (!bossBoxes && !placeItemsLeft(leftInventory, inventory, locations, treasures, levelList, keyIndexes)))
                || !testDifficulty(itemStart)) {
            // could not finish in reasonable time, or seed difficulty was incorrect
            // if no user seed provided, generate a new seed and re-randomize using that
            if (userSeed != null && userSeed.length() > 0) {
                gui.log("Invalid seed. Please double-check the seed and try again.");
                return;
            }

            attempts++;
            finalTreasures = new int[100];
            finalKeyLocations = new Level[25];
            seed = seedRNG.nextLong();
            rng = new Random(seed);
            inventory = new ArrayList<>(pureInventory);
            leftInventory = new Vector<>();
            locations = new ArrayList<>(pureLocations);
            mapList = new ArrayList<>(pureMapList);
            levelList = cloneLevelList(pureLevelList);
            keyIndexes = new ArrayList<>();
            fails = 0;

//            prepareLists(inventory, leftInventory, locations, treasures, mapList, levelList, keyIndexes, rng);
        }

        // items have been placed, now shuffle list of junk and use it to fill in the remaining locations
        boolean excludeJunk = options.containsKey("excludeJunk") && "true".equals(options.get("excludeJunk"));

        List<Integer> miscItems = new ArrayList<>(junkList);
        for (Integer startingItem : startingItems) {
            if (!miscItems.contains(startingItem)) {
                miscItems.add(Items.EMPTY);
            }
        }

        Collections.shuffle(miscItems,rng);

        int nextLocation = 0;
        for (Integer junkItem : miscItems) {
            if (excludeJunk && junkItem != Items.TIME_BUTTON && junkItem != Items.MAGNIFYING_GLASS) {
                // if junk items are excluded, replace with empty boxes
                junkItem = Items.EMPTY;
            }
            if (utilityStart && (junkItem == Items.TIME_BUTTON || junkItem == Items.MAGNIFYING_GLASS)) {
                junkItem = Items.EMPTY;
            }

            for (int i = nextLocation; i < 100; i++) {
                if (finalTreasures[i] == 0) {
                    finalTreasures[i] = junkItem;
                    nextLocation = i + 1;
                    break;
                }
            }
        }

        // build playthrough to set up hints if requested
        byte[] playthrough = null;
        if (options.containsKey("hints") && !options.get("hints").equals("unhelpful")) {
            boolean strategicHints = options.get("hints").equals("strategic");
            playthrough = buildPlaythrough(rng, strategicHints, itemStart);
        }

        // randomize music if requested
        Integer[] music = null;
        if (options.containsKey("musicShuffle")) {
            if (options.get("musicShuffle").equals("on")) {
                music = shuffleMusic(false, rng);
            }
            else if (options.get("musicShuffle").equals("chaos")) {
                music = shuffleMusic(true, rng);
            }
        }

        // randomize level palettes if requested
        // 2336 numbers
        int[] paletteSwitches = null;
        int[] titleSwitches = null;
        int[] otherSwitches = null;
        if (options.containsKey("levelColors") && "true".equals(options.get("levelColors"))) {
            // There are some sets of palettes that we want to ensure get the same transformation.
            // This is usually because of rooms that cycle through palettes (e.g. N1 underground),
            // or because of levels that change their palettes in response to Wario's inventory (e.g. S4)
            int[][] associations = {{0x2,0x3,0x73,0x74},
                                    {0x20,0x4a},
                                    {0x23,0x70},
                                    {0x24,0x58,0x59},
                                    {0x2b,0x6c},
                                    {0x2f,0x76,0x77,0x78,0x79,0x7a,0x7b,0x7c},
                                    {0x31,0x55,0x56,0x57,0x5e,0x5f,0x60},
                                    {0x44,0x7d,0x7e,0x7f,0x80,0x81,0x82,0x83},
                                    {0x45,0x84,0x85,0x86,0x87,0x88,0x89,0x8a},
                                    {0x46,0x8b,0x8c,0x8d,0x8e,0x8f,0x90,0x91},
                                    {0x4b,0x4c},
                                    {0x5a,0x5b,0x5c},
                                    {0x65,0x66,0x67},
                                    {0x68,0x69,0x6a},
                                    {0x6d,0x6e},
                                    {0x71,0x72}};
            int[][] assocSwitches = new int[associations.length][8];
            for (int i = 0; i < assocSwitches.length; i++) {
                for (int j = 0; j < assocSwitches[i].length; j++) {
                    assocSwitches[i][j] = -1;
                }
            }

            paletteSwitches = new int[1168];
            for (int i = 0; i < paletteSwitches.length; i++) {
                int assocIdx = -1;
                for (int x = 0; x < associations.length; x++) {
                    for (int y = 0; y < associations[x].length; y++) {
                        if (associations[x][y] == i/8) {
                            assocIdx = x;
                        }
                    }
                }
                if (assocIdx > -1 && assocSwitches[assocIdx][i%8] != -1) {
                    paletteSwitches[i] = assocSwitches[assocIdx][i%8];
                    continue;
                }
                paletteSwitches[i] = rng.nextInt(100000);
                if (assocIdx > -1) {
                    assocSwitches[assocIdx][i%8] = paletteSwitches[i];
                }
            }

            titleSwitches = new int[4];
            for (int i = 0; i < titleSwitches.length; i++) {
                titleSwitches[i] = rng.nextInt(100000);
            }

            otherSwitches = new int[6];
            for (int i = 0; i < otherSwitches.length; i++) {
                otherSwitches[i] = rng.nextInt(100000);
            }
        }

        int[] objColors = null;
        // randomize object palettes if requested
        if (options.containsKey("enemyColors") && "true".equals(options.get("enemyColors"))) {
            objColors = new int[480];
            for (int i = 0; i < objColors.length; i++) {
                objColors[i] = rng.nextInt(100000);
            }
        }

        // randomize key/chest palettes
        int[] chestColors = null;
        if (options.containsKey("chestColors") && "true".equals(options.get("chestColors"))) {
            // pick four each of hue, sat, val values that are distinct
            chestColors = new int[12];
            for (int set = 0; set < 3; set++) {
                for (int val = 0; val < 4; val++) {
                    int candidate = 0;
                    boolean ok = false;
                    while (!ok) {
                        if (set == 1) {
                            candidate = rng.nextInt(70000) + 30000;
                        }
                        else if (set == 2) {
                            candidate = rng.nextInt(50000) + 50000;
                        }
                        else {
                            candidate = rng.nextInt(100000);
                        }
                        ok = true;
                        for (int i = 0; i < val; i++) {
                            if (Math.abs(candidate - chestColors[set*4 + i]) < (set == 0 ? 10000 : 8750)) {
                                ok = false;
                                break;
                            }
                            else if (set == 0 && (Math.abs(((candidate+50000)%100000) - ((chestColors[set*4 + i]+50000)%100000)) < 10000)) {
                                ok = false;
                                break;
                            }
                        }
                    }
                    chestColors[set*4 + val] = candidate;
                }
            }
        }

        // randomize golf if requested
        Integer[] golfOrder = null;
        if (options.containsKey("golfShuffle") && "true".equals(options.get("golfShuffle"))) {
            golfOrder = shuffleGolf(rng);
        }

        boolean cutsceneSkip = options.containsKey("cutsceneSkip") && "true".equals(options.get("cutsceneSkip"));
        boolean revealSecrets = options.containsKey("revealSecrets") && "true".equals(options.get("revealSecrets"));

        // patch vanilla ROM file and create randomized ROM
        try {
            Patcher.patch(vanillaFileLocation,
                    finalTreasures,
                    encodeSeed(seed),
                    playthrough,
                    music,
                    mapShuffle ? worldMap : null,
                    paletteSwitches,
                    titleSwitches,
                    otherSwitches,
                    objColors,
                    chestColors,
                    keyShuffle ? finalKeyLocations : null,
                    golfOrder,
                    cutsceneSkip,
                    revealSecrets,
                    startingItems,
                    VERSION);
        } catch (IOException e) {
            gui.log("Error occurred while generating randomized game: " + e.getMessage());
            return;
        }

        gui.log("Generated randomized game with seed " + encodeSeed(seed));
        gui.log("Randomized ROM has been saved as WL3-randomizer-" + VERSION + "-" + encodeSeed(seed) + ".gbc");

        try {
            SpoilerLog.writeSpoiler(startingItems, finalTreasures, keyShuffle ? finalKeyLocations : null,
                    mapShuffle ? worldMap : null, encodeSeed(seed),
                    buildPlaythrough(null, false, itemStart),
                    options, VERSION);
            gui.log("Wrote spoiler log to wl3spoiler-"+VERSION+"-"+encodeSeed(seed)+".txt");
        }
        catch (IOException e) {
            gui.log("Error occurred while writing spoiler log: " + e.getMessage());
        }
    }

    /**
     * Initialize lists in preparation for the game logic to place items.
     */
    private static boolean prepareLists(List<Integer> inventory,
                                     List<Integer> leftInventory,
                                     List<Integer> locations,
                                     List<Integer> treasures,
                                     List<Integer> mapList,
                                     List<Level> levelList,
                                     List<List<Integer>> keyIndexes,
                                     Random rng) {
        Collections.shuffle(inventory, rng);
        Collections.shuffle(locations, rng);
        if (itemStart) {
            startingItems = new LinkedList<>();
            // determine starting items
            if (fullPowerStart) {
                for (int i = Items.SWIM_FINS; i <= Items.SPIKED_HELMET; i++) {
                    startingItems.add(i);
                }
            }
            else if (powerfulStart) {
                while (startingItems.size() < NUM_POWERS) {
                    int power = rng.nextInt(9) + Items.SWIM_FINS;
                    if (startingItems.contains(power) ||
                            (power == Items.SWIM_FINS && !startingItems.contains(Items.FROG_GLOVES)) ||
                            (power == Items.GOLD_GLOVES && !startingItems.contains(Items.RED_GLOVES)) ||
                            (power == Items.RED_OVERALLS && !startingItems.contains(Items.BLUE_OVERALLS))) {
                        continue;
                    }
                    startingItems.add(power);
                }
            }

            if (openMode) {
                List<Integer> openItems = Arrays.asList(Items.AXE,
                        Items.KEYSTONE_L, Items.KEYSTONE_R,
                        Items.COG_WHEEL_A, Items.COG_WHEEL_B,
                        Items.MIST_FAN, Items.TORCH);

                startingItems.addAll(openItems);
            }

            if (utilityStart) {
                startingItems.add(Items.MAGNIFYING_GLASS);
                startingItems.add(Items.TIME_BUTTON);
            }

            for (int item : startingItems) {
                inventory.remove(new Integer(item));
                leftInventory.add(item);
                Collections.replaceAll(treasures, item,null);
            }
        }
        else {
            startingItems = new Vector<>();
        }
        if (mapShuffle) {
            mapList = shuffleMap(mapList,itemStart,rng);
        }

        worldMap = mapList.toArray(new Integer[25]);

        if (keyShuffle) {
            // prepare key index (ordered list of locations per level where keys will be attempted to be placed)
            for (int i = 0; i < 25; i++) {
                List<Integer> subIndex = new ArrayList<>();
                for (int j = 0; j < 12; j++) {
                    subIndex.add(j);
                }
                Collections.shuffle(subIndex,rng);
                keyIndexes.add(subIndex);
            }
        }

        if (axeStart) {
            // pre-place the axe in the gray chest of level 0
            inventory.remove(new Integer(Items.AXE));
            locations.remove(new Integer(worldMap[0]*4));
            leftInventory.add(Items.AXE);
            Collections.replaceAll(treasures,Items.AXE,null);
            treasures.set(worldMap[0]*4, Items.AXE);
            if (keyShuffle) {
                // also place gray key
                if (!placeKey(levelList.get(worldMap[0]),worldMap[0],0,keyIndexes.get(worldMap[0]),new ArrayList<>())) {
                    return false;
                }
            }

            // sanity check - might fail this if a lategame level appears at N1's spot
            if (!canAccess(locationNames[worldMap[0]*4], new ArrayList<>(), levelList)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Initialize and show the GUI.
     */
    private static void createGUI() {
        JFrame frame = new JFrame("Wario Land 3 Randomizer " + VERSION);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        gui = new GUI();
        frame.add(gui);
        frame.pack();
        frame.setVisible(true);
    }

    /**
     * Sets the location of the vanilla WL3 ROM.
     */
    public static void setVanillaFile(String fileLocation) {
        vanillaFileLocation = fileLocation;
    }

    /**
     * Verify that the given file is actually a WL3 ROM.
     *
     * @param f  file to check
     * @return true if the file is a WL3 ROM, false otherwise
     * @throws IOException
     */
    public static boolean verifyFile(File f) throws IOException {
        FileInputStream fis = new FileInputStream(f);
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return false;
        }
        byte[] byteArray = new byte[1024];
        int bytesCount = 0;
        while ((bytesCount = fis.read(byteArray)) != -1) {
            digest.update(byteArray, 0, bytesCount);
        };
        fis.close();
        byte[] bytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for(int i=0; i< bytes.length ;i++)
        {
            sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString().equals("16bb3fb83e8cbbf2c4c510b9f50cf4ee");
    }

    /**
     * Place an item from rightInventory somewhere it can be logically acquired.
     *
     * @param leftInventory  Items already placed at the beginning of the sequence.
     * @param rightInventory Items yet to be placed anywhere.
     * @param locations      Locations without treasure.
     * @param treasures      All placed treasures, ordered by location (using null to represent still-empty locations)
     * @param levelList      List of levels and their current key placements.
     * @param keyIndexes     For each level, the order of locations in which key placements should be attempted
     * @param bossBoxes      Number of music boxes to give to bosses
     * @return true if all items were placed successfully, false otherwise
     */
    private static boolean placeItemsAssumed(List<Integer> leftInventory, List<Integer> rightInventory, List<Integer> locations, List<Integer> treasures, List<Level> levelList, List<List<Integer>> keyIndexes, int bossBoxes) {
        Integer[] bossArray = {
                3,  // anonster
                14, // pesce
                20, // octo
                27, // helio
                28, // dollboy
                34, // kezune
                37, // shoot
                48, // wormwould
                73, // muddee
                74  // jamano
        };
        boolean powersRemain = false;
        if (bossBoxes == 0 && enableNewLogic) {
            for (Integer item : rightInventory) {
                if (item >= Items.SWIM_FINS && item <= Items.SPIKED_HELMET) {
                    powersRemain = true;
                    break;
                }
            }
        }
        List<Integer> bosses = Arrays.asList(bossArray);
        for (Integer item : rightInventory) {
            if (bossBoxes > 0 && item > Items.MUSIC_BOX_5) {
                continue;
            }
            if (powersRemain && !(item >= Items.SWIM_FINS && item <= Items.SPIKED_HELMET)) {
                continue;
            }
            List<Integer> nextRightInventory = new Vector<>(rightInventory);
            nextRightInventory.remove(item);
            List<Integer> curInventory = new Vector<>(nextRightInventory);
            if (itemStart) {
                curInventory.addAll(startingItems);
            }
            List<Integer> candidateLocations = new Vector<>();
            List<Integer> checkedList = new Vector<>();
            boolean foundLocation;
            do {
                List<Integer> newItems = new Vector<>();
                List<Integer> newCandidateLocations = new Vector<>();
                List<Integer> newCheckedList = new Vector<>();
                foundLocation = false;
                for (int location = 0; location < treasures.size(); location++) {
                    if (checkedList.contains(location) || (bossBoxes > 0 && !bosses.contains(location))) {
                        continue;
                    }
                    if (canAccess(locationNames[location],curInventory,levelList)) {
                        foundLocation = true;
                        newCheckedList.add(location);
                        if (treasures.get(location) == null) {
                            if (keyShuffle) {
                                // try and place a key; if we can't, we can't get this treasure
                                List<Level> nextLevelList = cloneLevelList(levelList);
                                int levelNum = location / 4;
                                Level level = nextLevelList.get(levelNum);
                                int keyNum = location % 4;
                                List<Integer> subIndexes = keyIndexes.get(levelNum);
                                if (!placeKey(level,levelNum,keyNum,subIndexes,curInventory)) {
                                    continue;
                                }
                            }
                            newCandidateLocations.add(location);
                        }
                        else {
                            newItems.add(treasures.get(location));
                        }
                    }
                }
                if (!curInventory.contains(Items.AXE)) {
                    if (newItems.contains(Items.TORCH)) {
                        // without the axe, we must have found the torch in level 0
                        // take only the torch and restart scan (to avoid softlock potential)
                        curInventory.add(Items.TORCH);
                        checkedList.add(treasures.indexOf(Items.TORCH));
                        continue;
                    }
                    else if (newItems.contains(Items.KEYSTONE_L) && newItems.contains(Items.KEYSTONE_R)) {
                        // similarly, we want to avoid a rare softlock that could occur if we escape from N1 by heading west
                        curInventory.add(Items.KEYSTONE_L);
                        curInventory.add(Items.KEYSTONE_R);
                        checkedList.add(treasures.indexOf(Items.KEYSTONE_L));
                        checkedList.add(treasures.indexOf(Items.KEYSTONE_R));
                        continue;
                    }
                }
                curInventory.addAll(newItems);
                candidateLocations.addAll(newCandidateLocations);
                checkedList.addAll(newCheckedList);
            } while (foundLocation);

            if (candidateLocations.size() == 0) {
                fails++;
                if (fails >= 500) return false;
                continue;
            }

            for (int location : locations) {
                if (candidateLocations.contains(location)) {
                    List<Level> nextLevelList = levelList;
                    if (keyShuffle) {
                        // place key
                        nextLevelList = cloneLevelList(levelList);
                        int levelNum = location / 4;
                        Level level = nextLevelList.get(levelNum);
                        int keyNum = location % 4;
                        List<Integer> subIndexes = keyIndexes.get(levelNum);
                        if (!placeKey(level,levelNum,keyNum,subIndexes,curInventory)) {
                            continue;
                        }
                    }


                    List<Integer> nextTreasures = new Vector<>(treasures);
                    List<Integer> nextLocations = new Vector<>(locations);
                    nextLocations.remove(new Integer(location));
                    nextTreasures.set(location, item);
                    if (nextRightInventory.size() == 0) {
                        for (int i = 0; i < nextTreasures.size(); i++) {
                            if (nextTreasures.get(i) != null) {
                                finalTreasures[i] = nextTreasures.get(i);
                            }
                        }
                        if (itemStart) {
                            nextTreasures.addAll(startingItems);
                        }
                        for (int i = 0; i < finalKeyLocations.length; i++) {
                            finalKeyLocations[i] = nextLevelList.get(i);
                            for (int j = 0; j < 4; j++) {
                                if (finalKeyLocations[i].getLocation(j) == null) {
                                    boolean success = placeKey(finalKeyLocations[i],i,j,keyIndexes.get(i),nextTreasures);
                                    if (!success) {
                                        return false; // this shouldn't happen!
                                    }
                                }
                            }
                        }
                        return true;
                    }
                    else if (bossBoxes > 1 && placeItemsAssumed(leftInventory, nextRightInventory, nextLocations, nextTreasures, nextLevelList, keyIndexes, bossBoxes-1)) {
                        return true;
                    }
                    else if (bossBoxes == 1 && placeItemsLeft(leftInventory, nextRightInventory, nextLocations, nextTreasures, nextLevelList, keyIndexes)) {
                        return true;
                    }
                    else if (bossBoxes < 1 && placeItemsAssumed(leftInventory, nextRightInventory, nextLocations, nextTreasures, nextLevelList, keyIndexes, 0)) {
                        return true;
                    }
                    fails++;
                    if (fails >= 500) return false;
                }
            }
        }
        return false;
    }

    /**
     * Place the requested color key in a given level, so that it can be acquired with the given inventory.
     *
     * @param level      Level object representing the current state of the level.
     * @param levelNum   Level number from 0-24 representing which in-game level this is.
     * @param keyNum     Key number from 0-3; 0 = gray, 1 = red, 2 = green, 3 = blue
     * @param subIndexes This level's entry in the key index; the order in which to attempt key locations
     * @param inventory  The items Wario currently has
     *
     * @return true if a key was successfully placed
     */
    private static boolean placeKey(Level level, int levelNum, int keyNum, List<Integer> subIndexes, List<Integer> inventory) {
        for (Integer index : subIndexes) {
            KeyLocation candidate = allKeyLocations[levelNum].getLocation(index);
            int region = candidate.getRegion();

            int location = candidate.getSubLocation();

            boolean clash = false;
            for (int i = 0; i < 4; i++) {
                if (level.getLocation(i) != null && level.getLocation(i).getRegion() == region) {
                    clash = true;
                    break;
                }
            }

            if (!clash) {
                if ((canAccessKeyLocation(level.getLevelName(),candidate.getRegion(),location,true, keyNum, inventory) && canAccess(locationNames[levelNum*4+keyNum],inventory,null,false,false,true)) ||
                        (canAccessKeyLocation(level.getLevelName(),candidate.getRegion(),location,false, keyNum, inventory) && canAccess(locationNames[levelNum*4+keyNum],inventory,null,false,false,false))) {
                    level.setLocation(keyNum, candidate);
                    level.setInventory(keyNum, inventory);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Place an item from rightInventory at the beginning of the sequence (moving it to leftInventory).
     *
     * @param leftInventory  Items already placed at the beginning of the sequence.
     * @param rightInventory Items yet to be placed anywhere.
     * @param locations      Locations without treasure.
     * @param treasures      All placed treasures, ordered by location (using null to represent still-empty locations)
     * @param levelList      List of levels and their current key placements.
     * @param keyIndexes     For each level, the order of locations in which key placements should be attempted
     *
     * @return true if all items were placed successfully, false otherwise
     */
    private static boolean placeItemsLeft(List<Integer> leftInventory, List<Integer> rightInventory, List<Integer> locations, List<Integer> treasures, List<Level> levelList, List<List<Integer>> keyIndexes) {
        if (fails >= (keyShuffle ? 500 : 500)) {
            return false;
        }
        int cutoff = 55;
        int numPowers = 0;
        for (Integer item : leftInventory) {
            if (item >= Items.SWIM_FINS && item <= Items.SPIKED_HELMET) {
                numPowers++;
            }
        }
        //boolean forwardGPStart = treasures.get(0) != null && (treasures.get(0).equals(Items.BLUE_OVERALLS) || treasures.get(0).equals(Items.RED_OVERALLS));
        for (Integer location : locations) {
            boolean forwardGPStart = !leftInventory.contains(Items.AXE) && !leftInventory.contains(Items.TORCH) && !(leftInventory.contains(Items.KEYSTONE_L) && leftInventory.contains(Items.KEYSTONE_R));
            if (!canAccess(locationNames[location], leftInventory, levelList, forwardGPStart, false)) {
                continue;
            }
            List<Level> nextLevelList = levelList;
            if (keyShuffle) {
                nextLevelList = cloneLevelList(levelList);
                if (!placeKey(cloneLevelList(levelList).get(location / 4), location / 4, location % 4, keyIndexes.get(location / 4), leftInventory)) {
                    continue;
                }
            }

            boolean forceTorch = false;
            boolean forceKeys = false;
            if (forwardGPStart) {
                // because of the possibility of multiple treasures being available in the first level of map shuffle,
                // we need to be sure the player can't softlock by warping out with the torch before getting all
                // treasures.
                boolean torchFirst = false;
                // same with the two keystones
                int keystones = 0;
                for (Integer item : rightInventory) {
                    if (item == Items.AXE) {
                        break;
                    }
                    else if (item == Items.KEYSTONE_L || item == Items.KEYSTONE_R) {
                        keystones++;
                        if (keystones == 2) {
                            break;
                        }
                    }
                    else if (item == Items.TORCH) {
                        torchFirst = true;
                        break;
                    }
                }
                if (torchFirst) {
                    int levelIdx = (location / 4) * 4;
                    int locationsLeft = 0;
                    for (int i = levelIdx; i < levelIdx + 4; i++) {
                        if (treasures.get(i) == null && canAccess(locationNames[i], leftInventory, levelList)) {
                            locationsLeft++;
                        }
                    }
                    if (locationsLeft > 1) {
                        forceTorch = true;
                    }
                }
                else if (keystones == 2) {
                    int levelIdx = (location / 4) * 4;
                    int locationsLeft = 0;
                    for (int i = levelIdx; i < levelIdx + 4; i++) {
                        if (treasures.get(i) == null && canAccess(locationNames[i], leftInventory, levelList)) {
                            locationsLeft++;
                        }
                    }
                    if (locationsLeft > 1) {
                        forceKeys = true;
                    }
                }
            }

            for (Integer item : rightInventory) {
                if (enableNewLogic && numPowers > -1 && item >= Items.SWIM_FINS && item <= Items.SPIKED_HELMET) {
                    continue;
                }
                if (forceTorch && item != Items.TORCH) {
                    continue;
                }
                else if (forceKeys && item != Items.KEYSTONE_R && item != Items.KEYSTONE_L) {
                    continue;
                }
                else if (item == Items.TORCH && location/4 == worldMap[0] && treasures.indexOf(Items.AXE)/4 == worldMap[0]) {
                    continue;
                }
                List<Integer> nextLeftInventory = new Vector<>(leftInventory);
                nextLeftInventory.add(item);
                List<Integer> nextRightInventory = new Vector<>(rightInventory);
                nextRightInventory.remove(item);
                List<Integer> nextTreasures = new Vector<>(treasures);
                List<Integer> nextLocations = new Vector<>(locations);
                nextTreasures.set(location, item);
                nextLocations.remove(location);
                if (keyShuffle) {
                    // also place the appropriate key
                    nextLevelList = cloneLevelList(levelList);
                    if (!placeKey(nextLevelList.get(location / 4), location / 4, location % 4, keyIndexes.get(location / 4), leftInventory)) {
                        continue;
                    }
                }

                int locationsLeft;
                boolean restartScan;
                do {
                    locationsLeft = 0;
                    restartScan = false;
                    for (Integer checkLocation = 0; checkLocation < 100; checkLocation++) {
                        if (!checkLocation.equals(location) && canAccess(locationNames[checkLocation], nextLeftInventory, nextLevelList, forwardGPStart, false)) {
                            if (treasures.get(checkLocation) != null) {
                                if (!nextLeftInventory.contains(treasures.get(checkLocation))) {
                                    nextLeftInventory.add(treasures.get(checkLocation));
                                    restartScan = true;
                                    break;
                                }
                            }
                            else if (keyShuffle && !placeKey(cloneLevelList(nextLevelList).get(checkLocation/4),checkLocation/4,checkLocation%4,keyIndexes.get(checkLocation / 4),nextLeftInventory)) {
                                // do nothing
                            }
                            else {
                                locationsLeft++;
                            }
                        }
                    }
                } while (restartScan);
                if (locationsLeft == 0) {
                    continue;
                }
                if (nextRightInventory.size() == 0) {
                    for (int i = 0; i < nextTreasures.size(); i++) {
                        if (nextTreasures.get(i) != null) {
                            finalTreasures[i] = nextTreasures.get(i);
                        }
                    }
                    if (itemStart) {
                        nextTreasures.addAll(startingItems);
                    }
                    for (int i = 0; i < finalKeyLocations.length; i++) {
                        finalKeyLocations[i] = nextLevelList.get(i);
                        for (int j = 0; j < 4; j++) {
                            if (finalKeyLocations[i].getLocation(j) == null) {
                                boolean success = placeKey(finalKeyLocations[i],i,j,keyIndexes.get(i),nextTreasures);
                                if (!success) {
                                    return false; // this shouldn't happen!
                                }
                            }
                        }
                    }
                    return true;
                }
                else if (nextRightInventory.size() < cutoff && placeItemsAssumed(nextLeftInventory, nextRightInventory, nextLocations, nextTreasures,nextLevelList,keyIndexes, 0)) {
                    return true;
                }
                else if ((nextRightInventory.size() >= cutoff) && placeItemsLeft(nextLeftInventory,nextRightInventory,nextLocations,nextTreasures,nextLevelList,keyIndexes)) {
                    return true;
                }
                else if (fails >= (keyShuffle ? 500 : 500)) {
                    return false;
                }
            }
            fails++;
        }
        return false;
    }

    /**
     * Randomize the game's music.
     *
     * @param chaotic false if music should be shuffled; true if it should be completely randomized
     * @param rng random object to use
     * @return a list of music tracks in the order they should be added to the ROM
     */
    private static Integer[] shuffleMusic(boolean chaotic, Random rng) {
        Integer[] vanilla = { 0x16, 0x1a, 0x1f, 0x17, 0x1c, 0x19, 0x18, 0x1b, 0x20, 0x1e, 0x1d, // statuses
                          0x01, 0x02, 0x07, 0x08, 0x0e, 0x0f, 0x10, 0x10, 0x11, 0x11, 0x11, 0x11, // north
                          0x05, 0x05, 0x0c, 0x0b, 0x13, 0x14, 0x07, 0x08, 0x11, 0x11, 0x0d, 0x0d, // west
                          0x0e, 0x0f, 0x10, 0x10, 0x05, 0x05, 0x10, 0x10, 0x12, 0x12, 0x09, 0x0a, // south
                          0x13, 0x14, 0x06, 0x06, 0x0c, 0x0b, 0x12, 0x12, 0x04, 0x04, 0x0d, 0x0d, 0x03, 0x03, //east
                          0x21, 0x22, 0x23, 0x25, 0x26, 0x27, 0x28, 0x29, 0x2e, 0x2f, 0x30, 0x32, 0x32, 0x33, 0x37, 0x35, 0x36, 0x38, 0x3a}; // other songs (titlescreen, map, etc)

        if (chaotic) {
            Integer[] notLooped = { 0x15, 0x24, 0x2a, 0x2b, 0x2c, 0x2d, 0x31, 0x34, 0x39 };
            List<Integer> disallowed = Arrays.asList(notLooped);
            for (int i = 0; i < vanilla.length; i++) {
                Integer newMusic;
                do {
                    newMusic = rng.nextInt(0x3A) + 1;
                } while (disallowed.contains(newMusic));
                vanilla[i] = newMusic;
            }
            return vanilla;
        }
        else {
            List<Integer> newMusic = Arrays.asList(vanilla);
            Collections.shuffle(newMusic, rng);
            return newMusic.toArray(new Integer[newMusic.size()]);
        }
    }

    /**
     * Randomize the game's world map. Ensures that the first level has an item reachable with an empty inventory.
     *
     * @param initialMap  initial list of locations to shuffle
     * @param rng         random object to use
     * @return A shuffled list of locations
     */
    private static List<Integer> shuffleMap(List<Integer> initialMap, boolean powerStart, Random rng) {
        Vector<Integer> shuffledMap = new Vector<>(initialMap);
        Collections.shuffle(shuffledMap, rng);
        worldMap = shuffledMap.toArray(new Integer[25]);
        if (!fullPowerStart) {
            Integer firstLevel = 0;
//            Integer[] firstLevelsArr = {0, 1, 2, 4, 6, 7, 9, 13, 14, 15, 17, 18, 19, 21, 24};
//            List<Integer> firstLevels = Arrays.asList(firstLevelsArr);

            for (Integer level : shuffledMap) {
//                if (firstLevels.contains(level)) {
                if (axeStart) {
                    if (canAccess(locationNames[level*4], startingItems, null, false, true)) {
                        firstLevel = level;
                        break;
                    }
                }
                else if (canAccess(locationNames[level*4], startingItems, null, false, true) ||
                        canAccess(locationNames[level*4+1], startingItems, null, false, true) ||
                        canAccess(locationNames[level*4+2], startingItems, null, false, true) ||
                        canAccess(locationNames[level*4+3], startingItems, null, false, true)) {
                    firstLevel = level;
                    break;
                }
            }
            shuffledMap.remove(firstLevel);
            shuffledMap.insertElementAt(firstLevel, 0);
        }
        return shuffledMap;
    }

    /**
     * Shuffle golf courses.
     *
     * @param rng  Seeded random object to use
     * @return an array of Integers representing the order in which golf courses should be shuffled
     */
    private static Integer[] shuffleGolf(Random rng) {
        List<Integer> golfOrder = new ArrayList<>();
        for (int i = 0; i < 0x14; i++) {
            golfOrder.add(i);
        }
        Collections.shuffle(golfOrder,rng);
        return golfOrder.toArray(new Integer[0x14]);
    }

    /**
     * Encode a random seed, transforming it into a user-friendly 11-character String representation.
     *
     * @param seed  a random seed in long format
     * @return a String encoding of the provided seed
     */
    private static String encodeSeed(long seed) {
        char[] chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_!".toCharArray();
        String codedSeed = "";
        int idx = 0;
        for (int i = 1; i <= 64; i++) {
            idx >>>= 1;
            if ((seed & 1) == 1) {
                idx += 32;
            }
            seed >>>= 1;
            if (i % 6 == 0) {
                codedSeed = chars[idx] + codedSeed;
                idx = 0;
            }
        }
        idx >>>= 2;
        codedSeed = chars[idx] + codedSeed;
        return codedSeed;
    }

    /**
     * Decode a string representation of a random seed, transforming it into a usable long that can be passed in a Random constructor.
     *
     * @param codedSeed  an 11-character String representation of a random seed
     * @return the decoded long form of the provided seed
     */
    private static long decodeSeed(String codedSeed) throws Exception {
        String chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_!";
        char[] codedChars = codedSeed.toCharArray();

        for (char c : codedSeed.toCharArray()) {
            if (chars.indexOf(c) < 0) {
                throw new Exception();
            }
        }
        long seed = 0L;
        for (int i = 0; i < codedChars.length; i++) {
            seed <<= 6;
            int val = chars.indexOf(codedChars[i]);
            seed |= val;
        }
        return seed;
    }

    /**
     * Tests the difficulty of the seed.
     *
     * @param powerStart Whether or not Wario began the game with items
     * @return true if the difficulty is appropriate for the currently set level
     */
    private static boolean testDifficulty(boolean powerStart) {
        int targetDifficulty = difficulty;
        List<Integer> inventory = new Vector<>();
        if (powerStart) {
            inventory.addAll(startingItems);
        }
        List<Integer> locationsChecked = new Vector<>();

        List<Level> finalKeyLocationList = Arrays.asList(finalKeyLocations);

        difficulty = Difficulty.EASY;
        int[] blockers = {-1, -1, -1, -1, -1};
        int[] winBlockers = {0, 0, 0, 0, 0};
        while (difficulty <= Difficulty.MERCILESS) {
            boolean gotItem;
            do {
                List<Integer> newItems = new Vector<>();
                gotItem = false;
                for (int i = 0; i < finalTreasures.length; i++) {

                    if (locationsChecked.contains(i)) continue;
                    if (canAccess(locationNames[i], inventory, finalKeyLocationList, !inventory.contains(Items.AXE) && !inventory.contains(Items.TORCH), false)) {
                        locationsChecked.add(i);
                        gotItem = true;
                        difficulty = Difficulty.EASY;
                        newItems.add(finalTreasures[i]);
                        if (finalTreasures[i] == Items.TORCH) break;
                    }
                }
                inventory.addAll(newItems);
            } while (gotItem);
            blockers[difficulty]++;
            if (!inventory.containsAll(Arrays.asList(Items.MUSIC_BOX_1,
                    Items.MUSIC_BOX_2,
                    Items.MUSIC_BOX_3,
                    Items.MUSIC_BOX_4,
                    Items.MUSIC_BOX_5,
                    Items.AXE,
                    Items.GOLD_GLOVES)) ||
                !canGP(inventory)) {
                winBlockers[difficulty]++;
            }
            difficulty++;
        }
        difficulty = targetDifficulty;
        for (int i = 0; i < 4; i++) {
            if (i == targetDifficulty - 1 && winBlockers[i] < 2 && difficulty > Difficulty.NORMAL) {
                return false;
            }
            else if (i >= targetDifficulty && blockers[i] > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Build a playthrough to set up hints.
     *
     * @param rng random object to use
     * @param strategic true for "strategic" hints
     * @return An array of treasures, in the order they should be hinted at
     */
    private static byte[] buildPlaythrough(Random rng, boolean strategic, boolean powerStart) {
        List<Integer> inventory = new Vector<>();
        if (powerStart) {
            inventory.addAll(startingItems);
        }
        List<Integer> locationsChecked = new Vector<>();
        byte[] playthrough = new byte[100];
        int idx = 0;
        int stratIdx = 1;

        int sphere = 0;

        if (strategic) {
            playthrough[0] = Items.AXE;
            playthrough[6] = Items.GOLD_GLOVES;
            playthrough[7] = Items.RED_OVERALLS;
            idx = 8;
        }

        List<Level> finalKeyLocationList = Arrays.asList(finalKeyLocations);

        boolean gotItem;
        do {
            List<Integer> newItems = new Vector<>();
            gotItem = false;
            for (int i = 0; i < finalTreasures.length; i++) {

                if (locationsChecked.contains(i)) continue;
                if (canAccess(locationNames[i], inventory, finalKeyLocationList, !inventory.contains(Items.AXE) && !inventory.contains(Items.TORCH), false)) {
                    locationsChecked.add(i);
                    gotItem = true;
                    newItems.add(finalTreasures[i]);
                    if (finalTreasures[i] == Items.TORCH) break;
                }
            }
            if (rng != null) {
                Collections.shuffle(newItems, rng);
            }
            for (int item : newItems) {
                if (strategic && (item == Items.AXE || item == Items.GOLD_GLOVES || item == Items.RED_OVERALLS || item == Items.EMPTY)) {
                    // do nothing; this item has already been preset as a strategic hint
                }
                else if (strategic && item <= Items.MUSIC_BOX_5) {
                    playthrough[stratIdx] = (byte)item;
                    stratIdx++;
                }
                else {
                    playthrough[idx] = (byte) item;
                    idx++;
                }
            }
            inventory.addAll(newItems);
            sphere++;
        } while (gotItem);
        return playthrough;
    }

    /**
     * Check if the provided inventory allows Wario to swim.
     */
    private static boolean canSwim(List<Integer> inventory) {
        return inventory.contains(Items.FROG_GLOVES) || inventory.contains(Items.SWIM_FINS);
    }

    /**
     * Check if the provided inventory allows Wario to swim against currents.
     */
    private static boolean canSuperSwim(List<Integer> inventory) {
        return inventory.contains(Items.SWIM_FINS);
    }

    /**
     * Check if the provided inventory allows Wario to ground pound.
     */
    private static boolean canGP(List<Integer> inventory) {
        return inventory.contains(Items.BLUE_OVERALLS) || inventory.contains(Items.RED_OVERALLS);
    }

    /**
     * Check if the provided inventory allows Wario to super ground pound.
     */
    private static boolean canSuperGP(List<Integer> inventory) {
        return inventory.contains(Items.RED_OVERALLS);
    }

    /**
     * Check if the provided inventory allows Wario to lift small objects.
     */
    private static boolean canLift(List<Integer> inventory) {
        return inventory.contains(Items.RED_GLOVES) || inventory.contains(Items.GOLD_GLOVES);
    }

    /**
     * Check if the provided inventory allows wario to lift large objects.
     */
    private static boolean canSuperLift(List<Integer> inventory) {
        return inventory.contains(Items.GOLD_GLOVES);
    }

    /**
     * Check if the given level can be entered in the daytime given the provided inventory, assuming it can be entered at all.
     */
    private static boolean isDaytime(String location, List<Integer> inventory) {
        if (inventory.contains(Items.SUN_FRAGMENT_L) && inventory.contains(Items.SUN_FRAGMENT_R)) {
            return true;
        }
        int falseIdx = "NWSE".indexOf(location.charAt(0))*6 + (Integer.parseInt(location.substring(1))-1);
        for (int i = 0; i < worldMap.length; i++) {
            if (worldMap[i] == falseIdx) {
                return i < 18;
            }
        }
        return false;
    }

    /**
     * Check if the provided inventory allows Wario to access the given location.
     *
     * @param location A string representing a location, in one of the following formats:
     *                  - a level code, e.g. "E2", will return whether or not Wario can reach that level on the world map
     *                  - a two-character diagonal direction, e.g. "NW", will return whether or not Wario can warp to
     *                    that border using NEXT MAP
     */
    private static boolean canAccess(String location, List<Integer> inventory) {
        if (location.length() == 3) {
            return false; // this shouldn't happen
        }
        return canAccess(location, inventory, null, false, false);
    }

    /**
     * Check if the provided inventory allows Wario to access the given location.
     *
     * @param location A string representing a location, in one of the following formats:
     *                  - a level code, e.g. "E2", will return whether or not Wario can reach that level on the world map
     *                  - a two-character diagonal direction, e.g. "NW", will return whether or not Wario can warp to
     *                    that border using NEXT MAP
     *                  - a full three-character location code, e.g. "N2R", will return whether or not Wario can collect
     *                    a treasure at that location
     */
    private static boolean canAccess(String location, List<Integer> inventory, List<Level> keyLocations) {
        return canAccess(location, inventory, keyLocations, false, false);
    }

    /**
     * Check if the provided inventory allows Wario to access the given location.
     *
     * @param location A string representing a location, in one of the following formats:
     *                  - a level code, e.g. "E2", will return whether or not Wario can reach that level on the world map
     *                  - a two-character diagonal direction, e.g. "NW", will return whether or not Wario can warp to
     *                    that border using NEXT MAP
     *                  - a full three-character location code, e.g. "N2R", will return whether or not Wario can collect
     *                    a treasure at that location
     * @param adjusted if true, and location is a two-character level code, will not adjust for the effects of map shuffle
     */
    private static boolean canAccess(String location, List<Integer> inventory, List<Level> keyLocations, boolean forwardGPStart, boolean adjusted) {
        return canAccess(location, inventory, keyLocations, forwardGPStart, adjusted, true)
                || canAccess(location, inventory, keyLocations, forwardGPStart, adjusted, false);
    }

    /**
     * Check if the provided inventory allows Wario to access the given location.
     *
     * @param location A string representing a location, in one of the following formats:
     *                  - a level code, e.g. "E2", will return whether or not Wario can reach that level on the world map
     *                  - a two-character diagonal direction, e.g. "NW", will return whether or not Wario can warp to
     *                    that border using NEXT MAP
     *                  - a full three-character location code, e.g. "N2R", will return whether or not Wario can collect
     *                    a treasure at that location
     * @param adjusted if true, and location is a two-character level code, will not adjust for the effects of map shuffle
     */
    private static boolean canAccess(String location, List<Integer> inventory, List<Level> keyLocations, boolean forwardGPStart, boolean adjusted, boolean dayOnly) {

        if (location.equals("NW")) {
            return inventory.contains(Items.KEYSTONE_L) && inventory.contains(Items.KEYSTONE_R);
        }
        else if (location.equals("NE")) {
            return inventory.contains(Items.TORCH);
        }
        else if (location.equals("SW")) {
            return inventory.contains(Items.COG_WHEEL_A) && inventory.contains(Items.COG_WHEEL_B)
                    && (canAccess("NW",inventory) ||
                        ((canAccess("NE",inventory) && inventory.contains(Items.MIST_FAN))));
        }
        else if (location.equals("SE")) {
            return inventory.contains(Items.MIST_FAN)
                    && (canAccess("NE",inventory) ||
                        ((canAccess("NW",inventory) && (inventory.contains(Items.COG_WHEEL_A) && inventory.contains(Items.COG_WHEEL_B)))));
        }

        if (!adjusted && location.length() == 2) {
            int falseIdx = "NWSE".indexOf(location.charAt(0))*6 + (Integer.parseInt(location.substring(1))-1);
            for (int i = 0; i < worldMap.length; i++) {
                if (worldMap[i] == falseIdx) {
                    location = "" + ("NWSEE".charAt(i/6)) + ((i == 24) ? "7" : Character.forDigit((i%6)+1,10));
                    break;
                }
            }
        }

        if (location.equals("N1")) {
            return true;
        }
        else if (location.equals("N2")) {
            return inventory.contains(Items.AXE) || inventory.contains(Items.TORCH);
        }
        else if (location.equals("N3")) {
            return inventory.contains(Items.AXE) || (inventory.contains(Items.KEYSTONE_L) && inventory.contains(Items.KEYSTONE_R));
        }
        else if (location.equals("N4")) {
            return inventory.contains(Items.MUSIC_BOX_2) && canAccess("N3", inventory, keyLocations, false, true);
        }
        else if (location.equals("N5")) {
            return canAccess("N4", inventory, keyLocations, false, true);
        }
        else if (location.equals("N6")) {
            return inventory.contains(Items.GARLIC) && canAccess("N5", inventory, keyLocations, false, true);
        }
        else if (location.equals("W5")) {
            return inventory.contains(Items.MUSIC_BOX_4) && canAccess("W3", inventory, keyLocations, false, true);
        }
        else if (location.equals("W6")) {
            return inventory.contains(Items.RED_ARTIFACT) && inventory.contains(Items.GREEN_ARTIFACT) && inventory.contains(Items.BLUE_ARTIFACT)
                    && canAccess("W2", inventory, keyLocations, false, true);
        }
        else if (location.equals("S4")) {
            return inventory.contains(Items.ANGER_HALBERD) && inventory.contains(Items.ANGER_SPELL)
                    && canAccess("S2", inventory, keyLocations, false, true);
        }
        else if (location.equals("S5")) {
            return inventory.contains(Items.MUSIC_BOX_3)
                    && canAccess("S2", inventory, keyLocations, false, true);
        }
        else if (location.equals("S6")) {
            return inventory.contains(Items.SKY_KEY)
                    && canAccess("S3", inventory, keyLocations, false, true);
        }
        else if (location.equals("E3")) {
            return inventory.contains(Items.LAMP) && inventory.contains(Items.FLAME)
                    && canAccess("E1", inventory, keyLocations, false, true);
        }
        else if (location.equals("E5")) {
            return inventory.contains(Items.WARP_COMPACT)
                    && canAccess("E7", inventory, keyLocations, false, true);
        }
        else if (location.equals("E6")) {
            return inventory.contains(Items.CRATER_MAP)
                    && canAccess("E3", inventory, keyLocations, false, true);
        }
        else if (location.length() == 2) {
            return canAccessFromWest(location, inventory) || canAccessFromEast(location, inventory);
        }

        // check N1 node accessibility
        if (inventory.contains(Items.TORCH) || (inventory.contains(Items.KEYSTONE_L) && inventory.contains(Items.KEYSTONE_R))) {
            int falseIdx = "NWSE".indexOf(location.charAt(0)) * 6 + (Integer.parseInt(location.substring(1, 2)) - 1);
            if (worldMap[0] == falseIdx) {
                if (!inventory.contains(Items.AXE)) {
                    return false;
                }
            }
        }

        // if we're restricted to daytime, make sure we can actually enter this level in the daytime
        if (dayOnly && !isDaytime(location.substring(0,2),inventory)) {
            return false;
        }

        // if a key has been placed, make sure we can get it
        if (keyLocations != null && !canAccessKey(keyLocations,location,dayOnly,inventory)) {
            return false;
        }

        if (location.equals("N1S")) {
            return canAccess("N1", inventory);
        }
        else if (location.equals("N1R")) {
            return canAccess("N1", inventory)
                    && (canGP(inventory)
                    || (difficulty > Difficulty.EASY && inventory.contains(Items.GARLIC)));
        }
        else if (location.equals("N1G")) {
            return canAccess("N1", inventory)
                    && ((dayOnly && difficulty >= Difficulty.HARD && inventory.contains(Items.JUMP_BOOTS))
                    || (inventory.contains(Items.WIND) && inventory.contains(Items.WIND_BAG)));
        }
        else if (location.equals("N1B")) {
            return canAccess("N1", inventory)
			/*
			* Added MERCILESS Logic to this chest. With a throw + dashjump wallclip,
			* you can skip the need for both Overalls and Garlic by skipping the boss fight outright.
			*/
                    && inventory.contains(Items.POWDER)
                    && inventory.contains(Items.JUMP_BOOTS)
                    && canLift(inventory)
                    && (canGP(inventory) && (difficulty > Difficulty.EASY || inventory.contains(Items.GARLIC))
						|| difficulty >= Difficulty.MERCILESS);
        }
        else if (location.equals("N2S")) {
            return canAccess("N2", inventory);
        }
        else if (location.equals("N2R")) {
            return canAccess("N2", inventory)
                    && (inventory.contains(Items.FLUTE)
                    || inventory.contains(Items.JUMP_BOOTS)
                    || (inventory.contains(Items.GARLIC) && canSuperGP(inventory))
                    || (difficulty >= Difficulty.S_HARD));
        }
        else if (location.equals("N2G")) {
            return canAccess("N2", inventory)
                    && canGP(inventory)
                    && (inventory.contains(Items.FLUTE)
                    || inventory.contains(Items.JUMP_BOOTS)
                    || (inventory.contains(Items.GARLIC) && canSuperGP(inventory))
                    || (difficulty >= Difficulty.S_HARD));
        }
        else if (location.equals("N2B")) {
            return canAccess("N2", inventory)
                    && canSuperGP(inventory);
        }
        else if (location.equals("N3S")) {
            return canAccess("N3", inventory);
        }
        else if (location.equals("N3R")) {
            return canAccess("N3", inventory)
                    && canGP(inventory);
        }
        else if (location.equals("N3G")) {
			/*
			* Added MERCILESS logic for this chest.
			* 1. Have a Teruteru land on Wario and take it to the area under Mad Scienstein.
			* 2. Walk to the left of the Seeing Eye Door to release the Teruteru.
			* 3. Align with the proper pixel and do a dashjump wallclip up to near Mad Scienstein. 
			*		=> This is the last point where you can make a suspend save before committing to an attempt at this.
			* 4. Have the Teruteru land on Wario again.
			* 5. Jump repeatedly to reach the top of the level while the Teruteru is still on Wario.
			* 6. Walk off the right side and continue holding Left to release the Teruteru and reach the Overhang with the pipe.
			*		=> This execution skips the need for any powerups for this chest.
			*/
            return canAccess("N3", inventory)
                    && (inventory.contains(Items.BEANSTALK_SEEDS)
                    || (difficulty >= Difficulty.HARD && canLift(inventory) && inventory.contains(Items.JUMP_BOOTS))
					|| (difficulty >= Difficulty.MERCILESS));
        }
        else if (location.equals("N3B")) {
            return canAccess("N3", inventory)
                    && inventory.contains(Items.BLUE_CHEMICAL)
                    && inventory.contains(Items.RED_CHEMICAL);
        }
        else if (location.equals("N4S")) {
            return canAccess("N4", inventory);
        }
        else if (location.equals("N4R")) {
            return canAccess("N4", inventory)
                    && inventory.contains(Items.GARLIC);
        }
        else if (location.equals("N4G")) {
            return canAccess("N4", inventory)
                    && canSuperSwim(inventory);
        }
        else if (location.equals("N4B")) {
			/*
			* Added MINOR GLITCHES logic to this chest.
			* Perform a well-timed highjump waterclip to reach the room.
			*/
            return canAccess("N4", inventory)
                    && ((difficulty >= Difficulty.S_HARD && canSwim(inventory) && inventory.contains(Items.JUMP_BOOTS)) || inventory.contains(Items.PUMP))
                    && (difficulty >= Difficulty.HARD || canLift(inventory));
        }
        else if (location.equals("N5S")) {
            return canAccess("N5", inventory);
        }
        else if (location.equals("N5R")) {
            return canAccess("N5", inventory)
                    && canLift(inventory)
                    && canSwim(inventory);
        }
        else if (location.equals("N5G")) {
            return canAccess("N5", inventory)
                    && (difficulty >= Difficulty.S_HARD || inventory.contains(Items.WIRE_WIZARD))
                    && !dayOnly;
        }
        else if (location.equals("N5B")) {
            return canAccess("N5", inventory)
                    && inventory.contains(Items.GROWTH_SEED)
                    && inventory.contains(Items.GARLIC)
                    && canSwim(inventory);
        }
        else if (location.equals("N6S")) {
			/*
			* New logic addition for this chest for MINOR GLITCHES. 
			* You can skip the need for Boots using a Walljump when compared to HARD.
			*/
            return canAccess("N6", inventory)
                    && inventory.contains(Items.GARLIC)
                    && inventory.contains(Items.SPIKED_HELMET)
                    && ((difficulty >= Difficulty.HARD && inventory.contains(Items.JUMP_BOOTS) || difficulty >= Difficulty.S_HARD)
                        || canSwim(inventory))
                    && canGP(inventory);
        }
        else if (location.equals("N6R")) {
            return canAccess("N6", inventory)
                    && inventory.contains(Items.GARLIC)
                    && inventory.contains(Items.PURITY_STAFF)
                    && (difficulty >= Difficulty.HARD || canSwim(inventory))
                    && canGP(inventory);
        }
        else if (location.equals("N6G")) {
            return canAccess("N6", inventory)
                    && canSuperGP(inventory);
        }
        else if (location.equals("N6B")) {
            return canAccess("N6", inventory)
                    && canSuperGP(inventory)
                    && (difficulty >= Difficulty.HARD
                        || inventory.contains(Items.JUMP_BOOTS))
                    && inventory.contains(Items.NIGHT_VISION_GOGGLES);
        }
        else if (location.equals("W1S")) {
            return canAccess("W1", inventory) && (dayOnly || inventory.contains(Items.GARLIC));
        }
        else if (location.equals("W1R")) {
            return canAccess("W1", inventory) && (!dayOnly || inventory.contains(Items.GARLIC));
        }
        else if (location.equals("W1G")) {
            return canAccess("W1", inventory)
                    && inventory.contains(Items.SPIKED_HELMET)
                    && (!dayOnly || inventory.contains(Items.GARLIC));
        }
        else if (location.equals("W1B")) {
            return canAccess("W1", inventory)
                    && canSuperGP(inventory)
                    && (canLift(inventory) || (difficulty > Difficulty.EASY && inventory.contains(Items.JUMP_BOOTS)))
                    && (!dayOnly || inventory.contains(Items.GARLIC));
        }
        else if (location.equals("W2S")) {
            return canAccess("W2", inventory);
        }
        else if (location.equals("W2R")) {
            /*
             * The MINOR GLITCHES execution added involves performing a big dashjump wallclip from the deactivated Trolley.
             * Then do a midair enemy bounce to reach the platforms leading to the Golf.
             * Finally, navigate rightwards while avoiding any other hazards on the way to reach the Red Chest.
             */
            return canAccess("W2", inventory)
                && (inventory.contains(Items.WHEELS)
			        || (difficulty >= Difficulty.S_HARD && canLift(inventory) && inventory.contains(Items.JUMP_BOOTS)));
        }
        else if (location.equals("W2G")) {
			/*
			* Added MERCILESS logic to W2G.
			* Use Ladder Scrolling. Once screen wrapped, jump on the bottom trolley, and high bounce off the first Firebot.
			* This skips the need for a Glove when compared to HARD.
			* The MINOR GLITCHES execution added involves performing a big dashjump wallclip from the deactivated Trolley.
			* The same enemy bounce from HARD can then be used to reach the Golf that leads to the chest.
			*/
            return canAccess("W2", inventory)
                && ((inventory.contains(Items.WHEELS) && inventory.contains(Items.FLUTE))
			    || (difficulty >= Difficulty.HARD && inventory.contains(Items.WHEELS) && inventory.contains(Items.JUMP_BOOTS) && canLift(inventory))
			    || (difficulty >= Difficulty.S_HARD && inventory.contains(Items.JUMP_BOOTS) && canLift(inventory))
                || (difficulty >= Difficulty.MERCILESS && inventory.contains(Items.WHEELS) && inventory.contains(Items.JUMP_BOOTS)));
        }
        else if (location.equals("W2B")) {
            return canAccess("W2", inventory)
                    && inventory.contains(Items.STONE_FOOT);
        }
        else if (location.equals("W3S")) {
		/*
		* Additional HARD Logic added for this check.
		* With Flippers or Beanstalk Seeds, Glove, and Boots, you can access this without Overalls.
		* If no Beanstalk Seeds, Swim beyond the first two sets of pipes in the main area,
		* then do a midair enemy bounce using the Paragoom.
		* If you have Beanstalk Seeds, climb the beanstalk, then immediately fall down.
		* Go across the 2nd set of pipes, then do the same midair enemy bounce using the Paragoom.
		* Added MINOR GLITCHES logic for this chest as well.
		* With a dashjump wallclip, it's possible to reach this chest without needing a Glove when compared to HARD.
		*/
            return canAccess("W3", inventory)
                && (canGP(inventory)
			|| (difficulty >= Difficulty.HARD
				&& (canSwim(inventory) || inventory.contains(Items.BEANSTALK_SEEDS))
				&& inventory.contains(Items.JUMP_BOOTS)
                        	&& (difficulty >= Difficulty.S_HARD || canLift(inventory))));
        }
        else if (location.equals("W3R")) {
            return canAccess("W3", inventory)
                    && inventory.contains(Items.BEANSTALK_SEEDS);
        }
        else if (location.equals("W3G")) {
            return canAccess("W3", inventory)
                    && canSwim(inventory);
        }
        else if (location.equals("W3B")) {
			/*
			* Added MERCILESS logic to this chest.
			* From Main Area - Top Center, use a Throw + Dashjump wallclip to reach the top of the area on the left side. 
			* Next, do a charge to the right and jump. You should screen scroll down and land on the 4th pipe. 
			* Do a High Jump from the 4th pipe over to the 6th pipe, then do another High Jump from the 6th pipe to the 5th pipe. 
			* If done right, walk off the left side of the 5th pipe, then Press Up. 
			* If you are placed at the door that is underwater, press up again to enter this region, which is the Jellyfish Room.
			* Note, a Soft Reset is required. Do this soft reset after making a suspend save before the Throw + Dashjump wallclip. 
			* This execution skips the need for the Air Pump.
			*/
            return canAccess("W3", inventory)
        		&& ((inventory.contains(Items.PUMP) && canSwim(inventory))
				|| (difficulty >= Difficulty.MERCILESS && canGP(inventory) && canLift(inventory) && inventory.contains(Items.JUMP_BOOTS)));
        }
        else if (location.equals("W4S")) {
            return canAccess("W4", inventory);
        }
        else if (location.equals("W4R")) {
			/*
			* Additional MINOR GLITCHES Logic added for this chest.
			* You can access this chest without a Glove or Helmet by using just Boots.
			* Jump off 1 Firebot to get up the Ledge that leads to the Zombie Room - Below Third Platform check,
			* then do a High Walljump to reach the Zombies section.
			*/
            return canAccess("W4", inventory)
                    && (inventory.contains(Items.SPIKED_HELMET) 
			|| (difficulty > Difficulty.EASY && canLift(inventory) && inventory.contains(Items.JUMP_BOOTS))
			|| (difficulty >= Difficulty.S_HARD && inventory.contains(Items.JUMP_BOOTS)));
        }
        else if (location.equals("W4G")) {
			/*
			* Added MERCILESS Logic for this Chest.
			* You can use Ladder Scrolling to skip the need for the Golden Glove and the Boots 
			* when compared to MINOR GLITCHES.
			*/
            return canAccess("W4", inventory)
                && (difficulty >= Difficulty.MERCILESS || canSuperLift(inventory))
                && (difficulty >= Difficulty.S_HARD || inventory.contains(Items.JUMP_BOOTS))
                && canSuperGP(inventory);
        }
        else if (location.equals("W4B")) {
            return canAccess("W4", inventory);
        }
        else if (location.equals("W5S")) {
            return canAccess("W5", inventory)
                    && canSwim(inventory);
        }
        else if (location.equals("W5R")) {
            return canAccess("W5", inventory)
                    && canSwim(inventory)
                    && (difficulty > Difficulty.EASY || inventory.contains(Items.JUMP_BOOTS));
        }
        else if (location.equals("W5G")) {
            return canAccess("W5", inventory)
                    && canSwim(inventory)
                    && canLift(inventory)
                    && (difficulty > Difficulty.EASY || inventory.contains(Items.JUMP_BOOTS));
        }
        else if (location.equals("W5B")) {
			// New HARD Logic for W5 Blue Chest: Dismount from the ladder while the Donuteer prepares to throw its Donut to the right.
			// Timed properly, you can get up the ledge to eat the donut and break the donut blocks without needing a Glove.
            return canAccess("W5", inventory)
                    && canSwim(inventory)
                    && (difficulty >= Difficulty.HARD || canLift(inventory))
                    && (difficulty > Difficulty.EASY || inventory.contains(Items.JUMP_BOOTS));
        }
        else if (location.equals("W6S")) {
            return canAccess("W6", inventory)
                    && canGP(inventory);
        }
        else if (location.equals("W6R")) {
            return canAccess("W6", inventory)
                    && canSuperGP(inventory);
        }
        else if (location.equals("W6G")) {
			/* 
			* Added a check for MERCILESS difficulty for reaching the Green Chest Area (Platforming Challenge) without the Fire Extinguisher.
			* This area is reached on MERCILESS difficulty via Ladder Scrolling after using I-Frames to pass the first 2 fires.
			*/ 
            return canAccess("W6", inventory)
                    && (difficulty >= Difficulty.S_HARD || inventory.contains(Items.JUMP_BOOTS))
                    && (difficulty >= Difficulty.MERCILESS || inventory.contains(Items.FIRE_EXTINGUISHER));
        }
        else if (location.equals("W6B")) {
			/*
			* Added a check for MERCILESS difficulty for reaching the Blue Chest without the Rust Spray.
			* Perform the MINOR GLITCHES execution for reaching Main Area - Excavate Right.
			* Afterwards, do a single tile High Walljump to reach the area.
			*/
            return canAccess("W6", inventory)
                    && (inventory.contains(Items.RUST_SPRAY)
						|| (difficulty >= Difficulty.MERCILESS && inventory.contains(Items.SPIKED_HELMET) && inventory.contains(Items.JUMP_BOOTS)));
        }
        else if (location.equals("S1S")) {
            return canAccess("S1", inventory)
                    && canGP(inventory);
        }
        else if (location.equals("S1R")) {
            return canAccess("S1", inventory)
                    && inventory.contains(Items.BEANSTALK_SEEDS)
                    && canGP(inventory);
        }
        else if (location.equals("S1G")) {
            return canAccess("S1", inventory)
                    && canSwim(inventory)
                    && (inventory.contains(Items.FLUTE) || inventory.contains(Items.JUMP_BOOTS) || difficulty >= Difficulty.S_HARD);
        }
        else if (location.equals("S1B")) {
            return canAccess("S1", inventory)
                    && (difficulty >= Difficulty.S_HARD || inventory.contains(Items.JUMP_BOOTS));
        }
        else if (location.equals("S2S")) {
            return canAccess("S2", inventory);
        }
        else if (location.equals("S2R")) {
            return canAccess("S2", inventory)
                    && canSwim(inventory)
                    && canGP(inventory);
        }
        else if (location.equals("S2G")) {
            return canAccess("S2", inventory)
                    && canLift(inventory);
        }
        else if (location.equals("S2B")) {
            return canAccess("S2", inventory)
                    && inventory.contains(Items.PURITY_STAFF)
                    && (difficulty >= Difficulty.S_HARD ||
                    (inventory.contains(Items.GARLIC) && inventory.contains(Items.SPIKED_HELMET)))
                    && canSwim(inventory);
        }
        else if (location.equals("S3S")) {
            return canAccess("S3", inventory);
        }
        else if (location.equals("S3R")) {
            return canAccess("S3", inventory)
                    && inventory.contains(Items.BLUE_EYE_L)
                    && inventory.contains(Items.BLUE_EYE_R);
        }
        else if (location.equals("S3G")) {
            return canAccess("S3", inventory)
                    && inventory.contains(Items.WIRE_WIZARD);
        }
        else if (location.equals("S3B")) {
            return canAccess("S3", inventory)
                    && inventory.contains(Items.WIRE_WIZARD)
                    && inventory.contains(Items.GOLD_EYE_L)
                    && inventory.contains(Items.GOLD_EYE_R)
                    && inventory.contains(Items.SPIKED_HELMET)
                    && inventory.contains(Items.GARLIC)
                    && (difficulty >= Difficulty.S_HARD || inventory.contains(Items.JUMP_BOOTS))
                    && canSuperLift(inventory);
        }
        else if (location.equals("S4S")) {
            return canAccess("S4", inventory);
        }
        else if (location.equals("S4R")) {
            return canAccess("S4", inventory)
                    && inventory.contains(Items.STONE_FOOT);
        }
        else if (location.equals("S4G")) {
		/*
		* Added MINOR GLITCHES logic for reaching the Green Chest.
		* Perform Dashjump wallclips to get through the Green Chest Room without lifting up the Togēbas.
		*/
            return canAccess("S4", inventory)
                && inventory.contains(Items.STONE_FOOT) && canSuperSwim(inventory)
                && (canSuperGP(inventory) || (difficulty >= Difficulty.S_HARD && inventory.contains(Items.JUMP_BOOTS)));
        }
        else if (location.equals("S4B")) {
            return canAccess("S4", inventory)
                    && inventory.contains(Items.RUST_SPRAY)
                    && canGP(inventory);
        }
        else if (location.equals("S5S")) {
            return canAccess("S5", inventory);
        }
        else if (location.equals("S5R")) {
            return canAccess("S5", inventory)
                    && inventory.contains(Items.JUMP_BOOTS)
                    && canLift(inventory)
                    && (difficulty >= Difficulty.HARD || canSuperGP(inventory));
        }
        else if (location.equals("S5G")) {
            return canAccess("S5", inventory)
                    && inventory.contains(Items.DETONATOR);
        }
        else if (location.equals("S5B")) {
		/*
		* Added MINOR GLITCHES logic for reaching the Blue Chest.
		* Do a regular high enemy bounce after entering the room.
		* Next, charge towards the platform that contains the Throw Blocks.
		* Then, charge to the left to reach the next high bounce platform.
		* Finally, do a High Walljump to pass through the final high bounce platform.
		* One last regular Highjump will then reach Blue Chest Room - Upper Left.
		* To reach the Blue Chest from there, perform a high walljump from the upper left Spearbot platform.
		*/
            return canAccess("S5", inventory)
                    && inventory.contains(Items.RUST_SPRAY)
                    && inventory.contains(Items.JUMP_BOOTS)
                    && canGP(inventory)
                    && (difficulty >= Difficulty.S_HARD || canLift(inventory));
        }
        else if (location.equals("S6S")) {
            return canAccess("S6", inventory);
        }
        else if (location.equals("S6R")) {
            return canAccess("S6", inventory)
			/*
			* Downgraded S6R logic without a Glove from MINOR GLITCHES to NORMAL.
			* You just need to break the block, exit the room, then use the other entrance to reach the chest without needing a High Walljump.
			*/
                    && inventory.contains(Items.JUMP_BOOTS)
                    && ((difficulty > Difficulty.EASY && inventory.contains(Items.SPIKED_HELMET)) || canLift(inventory))
                    && (difficulty >= Difficulty.HARD || inventory.contains(Items.SPIKED_HELMET));
        }
        else if (location.equals("S6G")) {
            return canAccess("S6", inventory)
                    && inventory.contains(Items.SCISSORS)
                    && inventory.contains(Items.JUMP_BOOTS);
        }
        else if (location.equals("S6B")) {
			/*
			* Added a check for MERCILESS difficulty on this chest.
			* Wrong Warp from the bottom outside area to enter the Blue Chest Room.
			* Note, a soft reset is required for this to work, and this wrong warp only works during the day.
			* The soft reset can occur either immediately before entering the stage or after a suspend save.
			*/
            return canAccess("S6", inventory)
		        && inventory.contains(Items.JUMP_BOOTS)
                && ((inventory.contains(Items.GONG) && inventory.contains(Items.SCISSORS) && canSuperGP(inventory) && canLift(inventory))
			        || (dayOnly && difficulty >= Difficulty.MERCILESS));
        }
        else if (location.equals("E1S")) {
            return canAccess("E1", inventory);
        }
        else if (location.equals("E1R")) {
            return canAccess("E1", inventory)
				/*
				* HARD logic added to the E1 Red Chest. It can be obtained without the Super Flippers with just regular Flippers.
				* Pedal upwards repeatedly while in the water and time jumps in a rhythm. 
				* A bit of headway will be gained each time rhythm is kept, and this will also skip the boss fight.
				*/
                    && inventory.contains(Items.STONE_FOOT)
                    && (canGP(inventory) 
				        || (inventory.contains(Items.JUMP_BOOTS) && (canSuperSwim(inventory) || (difficulty >= Difficulty.HARD && canSwim(inventory)))));
        }
        else if (location.equals("E1G")) {
		// Added MINOR GLITCHES execution for this chest. Perform a walljump to reach the pipe that leads to Jamano.
            return canAccess("E1", inventory)
                    && inventory.contains(Items.STONE_FOOT)
			&& (inventory.contains(Items.JUMP_BOOTS) || difficulty >= Difficulty.S_HARD);
        }
        else if (location.equals("E1B")) {
			/*
			* Added MERCILESS logic for this chest. 
			* Have a Spearhead get clipped into the wall opposite the spike near the Blue Chest.
			* Once it gets unstunned, hit it such that it doesn't clip out of the wall.
			* Lift the other Spearhead and then do a Midair Enemy Bounce into the Spearhead that is clipped while it's facing right.
			* This will damage boost you and allow you to get enough height to reach the Blue Chest.
			*/
            return canAccess("E1", inventory)
                && (inventory.contains(Items.DETONATOR)
			        || (difficulty >= Difficulty.MERCILESS && canLift(inventory) && inventory.contains(Items.JUMP_BOOTS) && inventory.contains(Items.SPIKED_HELMET)));
        }
        else if (location.equals("E2S")) {
            return canAccess("E2", inventory);
        }
        else if (location.equals("E2R")) {
            return canAccess("E2", inventory)
                    && canLift(inventory);
        }
        else if (location.equals("E2G")) {
            return canAccess("E2", inventory)
                    && inventory.contains(Items.PURITY_STAFF)
                    && canSwim(inventory);
        }
        else if (location.equals("E2B")) {
			/*
				* Added MERCILESS logic to this chest.
				* Use Water Scrolling to screen wrap upwards, then use the terrain to collect Main Area - Above Rock. 
				* Next, get hit by the Polar Bear closest to the rock and have it send you to the right. 
				* Go just left to the wall you hit without crouching. 
				* Then, do a suspend save to set your position to the bottom of the pool. 
				* Charge and immediately crouch to get past the crouch space. Make sure you're holding neutral when the crouch charge ends.
				* Finally, jump up to get past the current and climb the ladder to reach this area without dayTime or Super Flippers.
			*/
            return canAccess("E2", inventory)
                && (dayOnly || canSuperSwim(inventory)
			        || (difficulty >= Difficulty.MERCILESS && canSwim(inventory) && inventory.contains(Items.GARLIC)));
        }
        else if (location.equals("E3S")) {
            return canAccess("E3", inventory)
                    && (difficulty >= Difficulty.S_HARD || canGP(inventory));
        }
        else if (location.equals("E3R")) {
            return canAccess("E3", inventory)
                    && canLift(inventory);
        }
        else if (location.equals("E3G")) {
            return canAccess("E3", inventory)
                    && (difficulty >= Difficulty.S_HARD || canLift(inventory));
        }
        else if (location.equals("E3B")) {
            return canAccess("E3", inventory)
                    && (difficulty >= Difficulty.S_HARD || canLift(inventory));
        }
        else if (location.equals("E4S")) {
            return canAccess("E4", inventory);
        }
        else if (location.equals("E4R")) {
            return canAccess("E4", inventory)
                    && inventory.contains(Items.GARLIC);
        }
        else if (location.equals("E4G")) {
            return canAccess("E4", inventory)
                    && (difficulty >= Difficulty.S_HARD
                    || dayOnly
                    || (inventory.contains(Items.JUMP_BOOTS)));
        }
        else if (location.equals("E4B")) {
            return canAccess("E4", inventory)
                    && inventory.contains(Items.DETONATOR)
                    && inventory.contains(Items.JUMP_BOOTS);
        }
        else if (location.equals("E5S")) {
            return canAccess("E5", inventory);
        }
        else if (location.equals("E5R")) {
            return canAccess("E5", inventory)
                    && inventory.contains(Items.WARP_REMOTE);
        }
        else if (location.equals("E5G")) {
			/* 
			* Added MERCILESS Logic for this chest.
			* This is reached by doing a single-tile walljump to scale the bottom set of throw blocks after entering the area.
			* A Glove check has been added specifically for the Key Cards option since that option strictly requires having a Glove.
			*/ 
            return canAccess("E5", inventory)
                && ((inventory.contains(Items.BLUE_KEY_CARD) && inventory.contains(Items.RED_KEY_CARD) && canLift(inventory))
			        || (inventory.contains(Items.WARP_REMOTE) && (difficulty >= Difficulty.MERCILESS || canLift(inventory))));
        }
        else if (location.equals("E5B")) {
        	/*
		* Added MERCILESS logic for this chest. 
		* You can do a Double Bear Bounce from the starting area to reach this region. 
		* This requires the Remote Control to gain access, but skips needing the Blue + Red Keycards.
		* The Boots and Golden Glove are both required to perform this execution.
		*/ 
		return canAccess("E5", inventory)
			&& ((inventory.contains(Items.BLUE_KEY_CARD) && inventory.contains(Items.RED_KEY_CARD) && canLift(inventory))
			    || (difficulty >= Difficulty.MERCILESS && inventory.contains(Items.WARP_REMOTE)
				    && canSuperLift(inventory) && inventory.contains(Items.JUMP_BOOTS)));
        }
        else if (location.equals("E6S")) {
            return canAccess("E6", inventory)
                    && (difficulty >= Difficulty.S_HARD || canLift(inventory));
        }
        else if (location.equals("E6R")) {
            // can manip pneumo to get past the fire
			// In addition, you can damage boost using the 2nd Paragoom to get through and reach this chest without needing Overalls.
            return canAccess("E6", inventory)
                    && (difficulty >= Difficulty.HARD || inventory.contains(Items.FIRE_EXTINGUISHER))
                    && canLift(inventory)
                    && (difficulty >= Difficulty.HARD || canGP(inventory));
        }
        else if (location.equals("E6G")) {
            return canAccess("E6", inventory)
                    && inventory.contains(Items.JACKHAMMER)
                    && canLift(inventory);
        }
        else if (location.equals("E6B")) {
			// To reach the Blue Chest without Boots, use a Pneumo to turn Wario allergic in the main area and float upwards into this room.
            return canAccess("E6", inventory)
                    && inventory.contains(Items.PICKAXE)
                    && canLift(inventory)
					&& (difficulty > Difficulty.EASY || inventory.contains(Items.JUMP_BOOTS));
        }
        else if (location.equals("E7S")) {
            return canAccess("E7", inventory);
        }
        else if (location.equals("E7R")) {
            return canAccess("E7", inventory)
                    && inventory.contains(Items.VALVE)
                    && canSuperLift(inventory);
        }
        else if (location.equals("E7G")) {
            return canAccess("E7", inventory)
                    && inventory.contains(Items.VALVE)
                    && canLift(inventory);
        }
        else if (location.equals("E7B")) {
            return canAccess("E7", inventory)
                    && inventory.contains(Items.DEMON_BLOOD);
        }
        return false;
    }

    /**
     * Check if Wario can get a given key with his current inventory.
     *
     * @param keyLocations  List of levels with their placed keys
     * @param location      Three-character location code of the key to check (e.g. "E3R")
     * @param daytime       True if it's daytime
     * @param inventory     Wario's inventory
     * @return true if the key can be acquired
     */
    private static boolean canAccessKey(List<Level> keyLocations, String location, boolean daytime, List<Integer> inventory) {
        if (keyLocations == null) {
            return true;
        }
        int lvl = "NWSE".indexOf(location.charAt(0))*6 + Integer.parseInt("" + location.charAt(1))-1;
        int idx = "SRGB".indexOf(location.charAt(2));
        Level level = keyLocations.get(lvl);
        KeyLocation keyLoc = level.getLocation(idx);
        if (keyLoc == null) {
            return true;
        }

        return canAccessKeyLocation(location.substring(0,2),keyLoc.getRegion(),keyLoc.getSubLocation(),daytime,idx,inventory);
    }

    /**
     * Check if Wario can access a potential key location with his current inventory.
     *
     * @param level     Two-character level code (e.g. "E3")
     * @param region    Which region the location exists in (identified by the number of its top-left sector
     * @param location  In a region, which sub-location points to the key
     * @param daytime   True if it's daytime
     * @param keyColor  0-3, representing the color of the key Wario is looking for
     * @param inventory Wario's current inventory
     * @return True if the key location can be reached
     */
    private static boolean canAccessKeyLocation(String level, int region, int location, boolean daytime, int keyColor, List<Integer> inventory) {
        if (level.equals("N1")) {
            if (region == 0x2) {
                return inventory.contains(Items.POWDER) && inventory.contains(Items.JUMP_BOOTS);
            }
            else if (region == 0x3) {
                if (location == 2) {
                    return true;
                }
                else if (location == 0){
                    return inventory.contains(Items.JUMP_BOOTS);
                }
                else {
                    return difficulty >= Difficulty.S_HARD
                            || inventory.contains(Items.JUMP_BOOTS);
                }
            }
            else if (region == 0x6) {
                return inventory.contains(Items.POWDER) && inventory.contains(Items.JUMP_BOOTS);
            }
            else if (region == 0x7) {
                if (location == 0) {
                    return canSuperGP(inventory) ||
                            (difficulty >= Difficulty.HARD &&
                                    canLift(inventory) &&
                                    inventory.contains(Items.JUMP_BOOTS));
                }
                else {
                    return true;
                }
            }
            else if (region == 0xd) {
                return canGP(inventory) ||
                        (difficulty > Difficulty.EASY && inventory.contains(Items.GARLIC));
            }
            else if (region == 0x14) {
				// Added a check for MINOR GLITCHES to reach Starting Area - Treetops without a Glove, using just Boots.
				// Done by either doing a Reverse high walljump after breaking the top set of blocks below, or with a dashjump wallclip.
                return (canLift(inventory) && inventory.contains(Items.JUMP_BOOTS))
					|| (difficulty >= Difficulty.S_HARD && (canLift(inventory) || inventory.contains(Items.JUMP_BOOTS)));
            }
            else if (region == 0x17) {
                return canSwim(inventory);
            }
        }
        else if (level.equals("N2")) {
            if (region == 0x1) {
                if (location == 2) {
					// Main Area - Excavate Lower Right is obtainable without the Helmet or Red Overalls.
					// This is done by ladder scrolling at the ladder closest to the snake pot and navigating the terrain.
					// A walljump is also required if you don't have Boots, but this won't check for Boots as MINOR GLITCHES is lower than MERCILESS on the difficulty scale.
                    return (canSuperGP(inventory) && inventory.contains(Items.SPIKED_HELMET)) || difficulty >= Difficulty.MERCILESS;
                }
                else if (location == 0) {
					// Main Area - Behind Wall Right of Start is obtainable without Garlic.
					// This is done by ladder scrolling at the ladder closest to the snake pot and navigating the terrain.
                    return !daytime || inventory.contains(Items.GARLIC) || difficulty >= Difficulty.MERCILESS;
                }
                else {
                    return true;
                }
            }
            else if (region == 0x5) {
                return true;
            }
            else if (region == 0x6) {
                if (location == 0) {
                    return difficulty >= Difficulty.S_HARD
                            || inventory.contains(Items.JUMP_BOOTS)
                            || inventory.contains(Items.FLUTE);
                }
                else {
                    return difficulty > Difficulty.EASY
                            || inventory.contains(Items.JUMP_BOOTS)
                            || inventory.contains(Items.FLUTE)
                            || canSuperGP(inventory);
                }
            }
            else if (region == 0x8) {
                return canSuperGP(inventory) && inventory.contains(Items.GARLIC);
            }
            else if (region == 0x14) {
                return canSuperGP(inventory);
            }
            else if (region == 0x1c) {
                if (location == 0) {
                    return canGP(inventory)
                            && ((!daytime && (difficulty >= Difficulty.S_HARD || inventory.contains(Items.JUMP_BOOTS) || inventory.contains(Items.FLUTE)))
                                || (canSuperGP(inventory) && inventory.contains(Items.GARLIC)));
                }
                else {
                    return ((!daytime && (difficulty >= Difficulty.S_HARD || inventory.contains(Items.JUMP_BOOTS) || inventory.contains(Items.FLUTE)))
                            || (canSuperGP(inventory) && inventory.contains(Items.GARLIC)));
                }
            }
        }
        else if (level.equals("N3")) {
            if (region == 0x1) {
                if (location == 0) {
                    return inventory.contains(Items.BEANSTALK_SEEDS);
                }
                else if (location == 2) {
                    return difficulty >= Difficulty.HARD || (inventory.contains(Items.BLUE_CHEMICAL) && inventory.contains(Items.RED_CHEMICAL));
                }
                else {
                    return true;
                }
            }
            else if (region == 0x6) {
                if (location == 0) {
                    // this location doesn't spawn until the seeds are planted
                    return inventory.contains(Items.BEANSTALK_SEEDS);
                }
                else {
					/*
					* Added MERCILESS logic for this check.
					* 1. Have a Teruteru land on Wario and take it to the area under Mad Scienstein.
					* 2. Walk to the left of the Seeing Eye Door to release the Teruteru.
					* 3. Align with the proper pixel and do a dashjump wallclip up to near Mad Scienstein. 
					*		=> This is the last point where you can make a suspend save before committing to an attempt at this.
					* 4. Have the Teruteru land on Wario again.
					* 5. Jump repeatedly to reach the top of the level while the Teruteru is still on Wario.
					* 6. Walk off the right side and continue holding Left to release the Teruteru and reach the Overhang with the pipe.
					*		=> This execution skips the need for any powerups for this check.
					*/
                    return inventory.contains(Items.BEANSTALK_SEEDS)
                        || ((difficulty >= Difficulty.HARD && canLift(inventory) && inventory.contains(Items.JUMP_BOOTS))
						|| (difficulty >= Difficulty.MERCILESS));
                }
            }
            else if (region == 0x14) {
				/*
				* Added MERCILESS logic for this check.
				* 1. Have a Teruteru land on Wario and take it to the area under Mad Scienstein.
				* 2. Walk to the left of the Seeing Eye Door to release the Teruteru.
				* 3. Align with the proper pixel and do a dashjump wallclip up to near Mad Scienstein. 
				*		=> This is the last point where you can make a suspend save before committing to an attempt at this.
				* 4. Have the Teruteru land on Wario again.
				* 5. Jump repeatedly to reach the top of the level while the Teruteru is still on Wario.
				* 6. Walk off the right side and continue holding Left to release the Teruteru and reach the Overhang with the pipe.
				*		=> This execution skips the need for any powerups for this check.
				*/
                return inventory.contains(Items.BEANSTALK_SEEDS)
                        || ((difficulty >= Difficulty.HARD && canLift(inventory) && inventory.contains(Items.JUMP_BOOTS))
						|| (difficulty >= Difficulty.MERCILESS));
            }
            else if (region == 0x16) {
                return canGP(inventory);
            }
            else if (region == 0x19) {
                return canSwim(inventory);
            }
            else if (region == 0x1a) {
                return inventory.contains(Items.BLUE_CHEMICAL) && inventory.contains(Items.RED_CHEMICAL);
            }
        }
        else if (level.equals("N4")) {
            if (region == 0x1) {
                if (location == 0) {
                    return canSwim(inventory)
                            || (difficulty >= Difficulty.HARD && canLift(inventory) && inventory.contains(Items.JUMP_BOOTS));
                }
                else {
					// Added MERCILESS LOGIC to Main Area - Right. Do Single tile walljumps to do the backtracking without Flippers or Boots.
                    return canSwim(inventory) || inventory.contains(Items.JUMP_BOOTS) || difficulty >= Difficulty.MERCILESS;
                }
            }
            else if (region == 0xa) {
                return canSuperSwim(inventory);
            }
            else if (region == 0x12) {
		/*
		* Added NORMAL and MINOR GLITCHES logic to the Torch Room - Above Rock Check.
		* NORMAL: Do a charge and then a well timed jump from the middle platform at the bottom to reach this.
		* MINOR GLITCHES: Do a well timed highjump waterclip to reach the Bat room without the Air Pump.
		* This can be reached by extension as long as you have access to the Bat Room.
		*/
                return ((difficulty >= Difficulty.S_HARD && canSwim(inventory) && inventory.contains(Items.JUMP_BOOTS)) || inventory.contains(Items.PUMP)) 
			&& (difficulty > Difficulty.EASY || inventory.contains(Items.JUMP_BOOTS));
            }
            else if (region == 0x15) {
				// New MINOR GLITCHES Logic for Inside 2nd Hill.
				// You can skip needing Boots if you have Red Overalls by breaking the blocks in a specific way, and then doing a walljump after obtaining this check.
                return inventory.contains(Items.GARLIC) && canLift(inventory)
                        && (canSwim(inventory) || ((difficulty >= Difficulty.HARD && inventory.contains(Items.JUMP_BOOTS)) || (difficulty >= Difficulty.S_HARD && canSuperGP(inventory)));
            }
            else if (region == 0x16) {
				// New MINOR GLITCHES Logic for Inside 4th Hill.
				// You can skip needing Boots if you have Red Overalls by breaking the blocks in a specific way, and then doing a reverse walljump after obtaining this check.
                return inventory.contains(Items.GARLIC) 
						&& (canSwim(inventory) || (difficulty >= Difficulty.S_HARD && (inventory.contains(Items.JUMP_BOOTS) || canSuperGP(inventory))));
            }
            else if (region == 0x17) {
		/*
		* Added MINOR GLITCHES logic to the Bat Room region.
		* Do a well timed highjump waterclip from the starting area to reach this region without the Air Pump.
		* This requires Flippers and Boots to be able to execute.
		*/
                return (difficulty >= Difficulty.S_HARD && canSwim(inventory) && inventory.contains(Items.JUMP_BOOTS)) || inventory.contains(Items.PUMP);
            }
            else if (region == 0x1d) {
		/*
		* Added MINOR GLITCHES logic to the Inside Third Hill check.
		* The Glove can be skipped by using a High Walljump, when compared to the HARD Logic execution.
		*/
                return canSwim(inventory) 
			        || (difficulty >= Difficulty.HARD && inventory.contains(Items.JUMP_BOOTS)
				        && (difficulty >= Difficulty.S_HARD || canLift(inventory)));
            }
        }
        else if (level.equals("N5")) {
            if (region == 0x1) {
                if (location == 1) {
		/*
		* Added MERCILESS logic to this check. Use Water Scrolling to screen wrap upwards.
		* You can then go across the top to reach this check without Garlic.
		* This only works at night since the water level is higher at night.
		*/
                    return (inventory.contains(Items.GARLIC)
						|| (difficulty >= Difficulty.MERCILESS && !daytime && canSwim(inventory)));
                }
                else if (location == 3) {
                    if (daytime) {
                        return canSwim(inventory) || inventory.contains(Items.JUMP_BOOTS);
                    }
                    else {
                        return canSwim(inventory);
                    }
                }
                else {
                    return true;
                }
            }
            else if (region == 0x6) {
                return canSwim(inventory) && inventory.contains(Items.GARLIC) && inventory.contains(Items.GROWTH_SEED);
            }
            else if (region == 0x9) {
                if (location == 0) {
                    return canLift(inventory) && canSwim(inventory)
                            && (canSuperSwim(inventory)
                            || inventory.contains(Items.SPIKED_HELMET)
                            || difficulty >= Difficulty.HARD);
                }
                else {
                    return canLift(inventory) && canSwim(inventory)
                            && (canSuperSwim(inventory) || inventory.contains(Items.SPIKED_HELMET));
                }
            }
            else if (region == 0xa) {
                return canLift(inventory) && canSwim(inventory);
            }
            else if (region == 0x1b) {
                return inventory.contains(Items.GARLIC);
            }
        }
        else if (level.equals("N6")) {
            if (region == 0x1) {
                if (location == 0) {
                    return inventory.contains(Items.GARLIC) && canGP(inventory)
                            && (canSwim(inventory) || (difficulty > Difficulty.EASY && inventory.contains(Items.JUMP_BOOTS)));
                }
                else if (location == 1) {
                    return inventory.contains(Items.GARLIC) && inventory.contains(Items.SPIKED_HELMET) && canGP(inventory);
                }
                else if (location == 2) {
                    return inventory.contains(Items.GARLIC)
                            && inventory.contains(Items.SPIKED_HELMET)
                            && canGP(inventory)
                            && (canSwim(inventory) || (difficulty > Difficulty.HARD && inventory.contains(Items.JUMP_BOOTS)));
                }
                else if (location == 3) {
                    return inventory.contains(Items.GARLIC) && inventory.contains(Items.SPIKED_HELMET) && canGP(inventory)
                            && (canSwim(inventory) || inventory.contains(Items.JUMP_BOOTS));
                }
                else {
                    return inventory.contains(Items.GARLIC) && inventory.contains(Items.SPIKED_HELMET) && canGP(inventory) && canSwim(inventory);
                }
            }
            else if (region == 0x5) {
		/*
		* New addition for this check for MINOR GLITCHES. 
		* You can skip the need for Boots using a Walljump when compared to HARD.
		* This is for the Boss Room - Above Silver Chest check.
		*/
                return inventory.contains(Items.GARLIC) && inventory.contains(Items.SPIKED_HELMET) && canGP(inventory)
                        && (canSwim(inventory) 
				|| (difficulty >= Difficulty.HARD
                                && (inventory.contains(Items.JUMP_BOOTS) || difficulty >= Difficulty.S_HARD)));
            }
            else if (region == 0x6) {
                return canSuperGP(inventory) && inventory.contains(Items.NIGHT_VISION_GOGGLES)
                        && (difficulty >= Difficulty.HARD || inventory.contains(Items.JUMP_BOOTS));
            }
            else if (region == 0x14) {
                return canSuperGP(inventory);
            }
            else if (region == 0x19) {
                return canSuperGP(inventory) && inventory.contains(Items.GARLIC);
            }
            else if (region == 0x1c) {
                return inventory.contains(Items.GARLIC) && canGP(inventory) && inventory.contains(Items.PURITY_STAFF)
                        && (canSwim(inventory) || (difficulty >= Difficulty.HARD && keyColor == 1));
            }
        }
        else if (level.equals("W1")) {
            if (region == 0x1) {
                if (location == 0) {
					// New MERCILESS Logic for Main Area - By Underground Ladder.
					// This can be obtained during the day by Ladder Scrolling at the start to screen wrap downwards.
                    return (!daytime || (daytime && difficulty >= Difficulty.MERCILESS)) || (inventory.contains(Items.GARLIC) && canSuperGP(inventory));
                }
                else {
                    if (daytime) {
						// New MERCILESS Logic for Main Area - Above Underground Quicksand Pool.
						// This can be obtained during the day by Ladder Scrolling at the start to screen wrap downwards.
						// It can be reached at night without any powerups in the same manner as well.
                        return (inventory.contains(Items.GARLIC) && canSuperGP(inventory)) || difficulty >= Difficulty.MERCILESS;
                    }
                    else {
                        return inventory.contains(Items.GARLIC) || canGP(inventory) || difficulty >= Difficulty.MERCILESS;
                    }
                }
            }
            else if (region == 0x5) {
                return canSuperGP(inventory)
                        && ((difficulty > Difficulty.EASY && inventory.contains(Items.JUMP_BOOTS))
                            || canLift(inventory))
                        && (!daytime || inventory.contains(Items.GARLIC));
            }
            else if (region == 0x6) {
                if (location == 0) {
                    return inventory.contains(Items.GARLIC)
                            || (!daytime && inventory.contains(Items.SPIKED_HELMET) && canGP(inventory));
                }
                else {
                    return canSuperGP(inventory) && canLift(inventory) && (!daytime || inventory.contains(Items.GARLIC));
                }
            }
            else if (region == 0x8) {
                if (!daytime && !inventory.contains(Items.GARLIC)) {
                    return false;
                }
                else if (location == 0) {
                    return ((difficulty >= Difficulty.S_HARD && canGP(inventory)) || inventory.contains(Items.SPIKED_HELMET)) && canLift(inventory);
                }
                else if (location == 2) {
					// Day Ruins Basement can be obtainable in MERCILESS without Overalls by Ladder Scrolling.
                    return canGP(inventory) || difficulty >= Difficulty.MERCILESS;
                }
                else {
                    return true;
                }
            }
            else if (region == 0xa) {
                return true;
            }
            else if (region == 0x14) {
                return true;
            }
            else if (region == 0x18) {
                return canSuperGP(inventory)
                        && (canLift(inventory)
                            || (difficulty > Difficulty.EASY && inventory.contains(Items.JUMP_BOOTS)))
                        && (!daytime || inventory.contains(Items.GARLIC));
            }
        }
        else if (level.equals("W2")) {
            if (region == 0x1) {
				if (location == 0) {
					/*
					* The MINOR GLITCHES execution for Sky - Near Snake Pot involves performing a big dashjump wallclip from the deactivated Trolley.
					* Then do a midair enemy bounce to reach the platforms leading to the Golf.
					* Finally, navigate rightwards and reach the check as normal.
					*/
					return inventory.contains(Items.WHEELS)
						|| (difficulty >= Difficulty.S_HARD && inventory.contains(Items.JUMP_BOOTS) && canLift(inventory));
				}
				else if (location == 2) {
					/*
					* For Sky - Below Center Ledge, perform a big dashjump wallclip from the deactivated trolley to reach 
					* the platform containing the first Firebot. From there, navigate rightwards
					* to the lower ledge and perform a precise dashjump to reach this check.
					*/
					return inventory.contains(Items.WHEELS)
						|| (difficulty >= Difficulty.S_HARD && inventory.contains(Items.JUMP_BOOTS));
				}
                else if (location == 1 && difficulty > Difficulty.EASY) {
                    return true;
                }
                else {
                    return inventory.contains(Items.WHEELS);
                }
            }
            else if (region == 0x5) {
                return true;
            }
            else if (region == 0x7) {
                return canGP(inventory);
            }
            else if (region == 0x8) {
                return inventory.contains(Items.STONE_FOOT) && canSwim(inventory)
                        && (inventory.contains(Items.SPIKED_HELMET) || canSuperSwim(inventory));
            }
            else if (region == 0x9) {
                return inventory.contains(Items.STONE_FOOT) && canSwim(inventory);
            }
            else if (region == 0xa) {
		/*
		* The MINOR GLITCHES for the Flooded Vampire Room checks involves performing a big dashjump wallclip from the deactivated Trolley.
		* Then do a midair enemy bounce to reach the platforms leading to the Golf.
		* Finally, navigate rightwards and reach the check as normal.
		*/
                if (location == 0) {
                    return inventory.contains(Items.WHEELS)
			|| (difficulty >= Difficulty.S_HARD && inventory.contains(Items.JUMP_BOOTS) && canLift(inventory));
                }
                else {
                    return canSwim(inventory) 
			&& (inventory.contains(Items.WHEELS) 
				|| (difficulty >= Difficulty.S_HARD && inventory.contains(Items.JUMP_BOOTS) && canLift(inventory)));
                }
            }
            else if (region == 0x14) {
                return true;
            }
        }
        else if (level.equals("W3")) {
            if (region == 0x1) {
                if (location == 0) {
                    return inventory.contains(Items.BEANSTALK_SEEDS)
                            || (difficulty >= Difficulty.HARD && canGP(inventory) && canLift(inventory) && inventory.contains(Items.JUMP_BOOTS));
                }
                else if (location == 1) {
			/*
			* Additional HARD Logic added for this check, which is Main Area - Top Center.
			* With Flippers or Beanstalk Seeds, Glove, and Boots, you can access this without Overalls.
			* If no Beanstalk Seeds, Swim beyond the first two sets of pipes in the main area,
			* then do a midair enemy bounce using the Paragoom.
			* If you have Beanstalk Seeds, climb the beanstalk, then immediately fall down.
			* Then go across the 2nd set of pipes and do the same midair enemy bounce using the Paragoom.
			* The latter option can only be in logic if this check is specifically the Grey or Red Keys.
			* This is because you would not be able to swim down if it's the Green or Blue Keys in this situation.
			*/
                    return canGP(inventory)
			            || (difficulty >= Difficulty.HARD
				            && ((canSwim(inventory) || (inventory.contains(Items.BEANSTALK_SEEDS)))
                		        && canLift(inventory)
                        	    && inventory.contains(Items.JUMP_BOOTS)));
                }
                else if (location == 2) {
                    return canSwim(inventory);
                }
                else if (location == 3) {
                    return canSuperSwim(inventory);
                }
            }
            else if (region == 0x7) {
                if (!canSwim(inventory)) {
                    return false;
                }
                else if (location == 1 || location == 2) {
                    return inventory.contains(Items.SPIKED_HELMET);
                }
                else {
                    return true;
                }
            }
            else if (region == 0x14) {
                return inventory.contains(Items.BEANSTALK_SEEDS);
            }
            else if (region == 0x1a) {
		/*
		* Added MERCILESS logic to this region.
		* From Main Area - Top Center, use a Throw + Dashjump wallclip to reach the top of the area on the left side. 
		* Next, do a charge to the right and jump. You should screen scroll down and land on the 4th pipe. 
		* Do a High Jump from the 4th pipe over to the 6th pipe, then do another High Jump from the 6th pipe to the 5th pipe. 
		* If done right, walk off the left side of the 5th pipe, then Press Up. 
		* If you are placed at the door that is underwater, press up again to enter this region, which is the Jellyfish Room.
		* Note, a Soft Reset is required. Do this soft reset after making a suspend save before the Throw + Dashjump wallclip. 
		* This execution skips the need for the Air Pump.
		*/
                return (inventory.contains(Items.PUMP) && canSwim(inventory))
			        || (difficulty >= Difficulty.MERCILESS && canGP(inventory) && canLift(inventory) && inventory.contains(Items.JUMP_BOOTS)
				        && (canSwim(inventory) || keyColor == 3));
            }
        }
        else if (level.equals("W4")) {
            if (region == 0x1) {
                return true;
            }
            else if (region == 0x5) {
		if (location == 1) {
		/*
		* Added MERCILESS Logic for this region, which is Switch Puzzle Main.
		* You can use Ladder Scrolling to skip the need for the Golden Glove and the Boots 
		* when compared to MINOR GLITCHES.
		*/
			return ((difficulty >= Difficulty.MERCILESS || canSuperLift(inventory))
				&& (difficulty >= Difficulty.S_HARD || inventory.contains(Items.JUMP_BOOTS))
				&& canSuperGP(inventory))
				/*
				* Alternative execution for MINOR GLITCHES, which can be done without Red Overalls.
				* Line up with the proper pixel, then do a Reverse High Walljump
				* to break the blocks that lead to this check, which is Switch Puzzle Main - Lower Left.
				*/
					|| (difficulty >= Difficulty.S_HARD 
						&& inventory.contains(Items.JUMP_BOOTS) 
						&& inventory.contains(Items.SPIKED_HELMET)
						&& (difficulty >= Difficulty.MERCILESS || canSuperLift(inventory)));
		}
                return (difficulty >= Difficulty.MERCILESS || canSuperLift(inventory))
						&& (difficulty >= Difficulty.S_HARD || inventory.contains(Items.JUMP_BOOTS))
						&& canSuperGP(inventory);
            }
            else if (region == 0x7) {
                return canLift(inventory) && inventory.contains(Items.PROPELLOR);
            }
            else if (region == 0x9) {
                if (location == 0) {
                    return true;
                }
                else {
		/*
		* This was not originally checking for Boots, which is required for HARD logic. I fixed this issue.
		* I also added a MINOR GLITCHES alternative for this check. 
		* Break a small alcove in the right wall 1 block up, and leave the bottom left block able to be jumped on. 
		* Then use a high walljump to escape out of the pit. 
		* This MINOR GLITCHES alternative Skips needing the Helmet and Glove when compared to HARD Logic.
		*/
                    return canSuperGP(inventory)
                        && (inventory.contains(Items.SPIKED_HELMET)
                            || (difficulty >= Difficulty.HARD && canLift(inventory) && inventory.contains(Items.JUMP_BOOTS))
				|| (difficulty >= Difficulty.S_HARD && inventory.contains(Items.JUMP_BOOTS)));
                }
            }
            else if (region == 0x16) {
		/*
		* Added MERCILESS logic for this check, which is Switch Puzzle Side Room 1 - Upper Right.
		* If you use Ladder Scrolling to reach the region, only a regular glove is needed to get this check.
		*/
                return canSuperLift(inventory)
					|| (difficulty >= Difficulty.MERCILESS && canLift(inventory));
            }
            else if (region == 0x17) {
                if (location == 0) {
                    return canLift(inventory);
                }
                else {
                    return true;
                }
            }
            else if (region == 0x19) {
		/*
		* Added MINOR GLITCHES logic for this check, which is Zombie Room - Below Third Platform.
		* With a Reverse Walljump, it's possible to obtain this check without either the Helmet or Boots.
		*/
                return inventory.contains(Items.SPIKED_HELMET) || inventory.contains(Items.JUMP_BOOTS)
			        || difficulty >= Difficulty.S_HARD;
            }
            else if (region == 0x1c) {
		/*
		* Added MERCILESS logic for this check, which is Switch Puzzle Side Room 2 - Upper Right.
		* If you use Ladder Scrolling to reach the region, only the Red Overalls are needed to get this check.
		*/
                return canSuperGP(inventory)
			&& (canSuperLift(inventory) || difficulty >= Difficulty.MERCILESS);
            }
        }
        else if (level.equals("W5")) {
            if (region == 0xc) {
                if (location == 0) {
                    return inventory.contains(Items.JUMP_BOOTS);
                }
                else {
                    return inventory.contains(Items.JUMP_BOOTS) && canLift(inventory);
                }
            }
            else if (!canSwim(inventory)) {
                return false;
            }
            else if (region == 0x1) {
                if (location == 0) {
                    return inventory.contains(Items.GROWTH_SEED) && canSuperSwim(inventory);
                }
                else {
                    return inventory.contains(Items.GROWTH_SEED);
                }
            }
            else if (region == 0x4) {
                return difficulty > Difficulty.EASY;
            }
            else if (region == 0xa) {
                return canSuperSwim(inventory);
            }
            else if (region == 0x16) {
                if (!canSuperSwim(inventory)) {
                    return false;
                }
                else if (location == 0) {
                    return canSuperLift(inventory) && inventory.contains(Items.SPIKED_HELMET);
                }
                else if (location == 2) {
                    return inventory.contains(Items.SPIKED_HELMET);
                }
                else {
                    return true;
                }
            }
            else if (region == 0x18) {
                return inventory.contains(Items.JUMP_BOOTS) || difficulty >= Difficulty.S_HARD;
            }
            else if (region == 0x1B) {
                return inventory.contains(Items.RED_CHEMICAL) && inventory.contains(Items.BLUE_CHEMICAL);
            }
        }
        else if (level.equals("W6")) {
            if (region == 0x1) {
                if (location == 1) {
                    return (difficulty >= Difficulty.S_HARD || inventory.contains(Items.RUST_SPRAY))
                            && canGP(inventory);
                }
                else {
                    return true;
                }
            }
            else if (region == 0x4) {
		/* 
		* Added a check for MERCILESS difficulty for reaching the Green Chest Area (Platforming Challenge) without the Fire Extinguisher.
		* This area is reached on MERCILESS difficulty via Ladder Scrolling after using I-Frames to pass the first 2 fires.
		*/ 
                if (!inventory.contains(Items.FIRE_EXTINGUISHER) && difficulty < Difficulty.MERCILESS) {
                    return false;
                }
                if (difficulty < Difficulty.S_HARD) {
                    return inventory.contains(Items.JUMP_BOOTS);
                }
                else {
			if (location == 1) {
			// MERCILESS Logic added for Platforming Challenge - Left.
			// This is done with walljumps, but there is a single tile walljump required to reach this check.
			return (inventory.contains(Items.JUMP_BOOTS) || inventory.contains(Items.SPIKED_HELMET))
				|| difficulty >= Difficulty.MERCILESS;
			}
        	else if (location == 2) {
			// MERCILESS Logic added for Platforming Challenge - Right. 
			// This is done with walljumps, but there is a single tile walljump along the way to reach this check.
                        return inventory.contains(Items.JUMP_BOOTS) || difficulty >= Difficulty.MERCILESS;
                    }
                    else {
                        return inventory.contains(Items.JUMP_BOOTS)
                                || inventory.contains(Items.SPIKED_HELMET);
                    }
                }
            }
            else if (region == 0x8) {
		/*
		* Added MERCILESS Logic to the checks in the Vent Room.
		* Perform the MINOR GLITCHES logic to reach Main Area - Excavate Right.
		* Afterwards, do a single tile High Walljump. Requires the Helmet and Boots, skips the need for the Rust Spray.
		* If you have the Rust Spray, this is reachable without Boots on MERCILESS Difficulty as well, using a single-tile walljump.
		*/
                if (location == 2) {
                    return (inventory.contains(Items.RUST_SPRAY) && (difficulty >= Difficulty.MERCILESS || inventory.contains(Items.JUMP_BOOTS)))
			|| (difficulty >= Difficulty.MERCILESS && inventory.contains(Items.JUMP_BOOTS) && inventory.contains(Items.SPIKED_HELMET));
                }
                else {
		// The NORMAL logic added for the Throw Block checks is to use Fat Wario to reach the Golf required as opposed to Overalls.
                    return canLift(inventory)
			&& (difficulty > Difficulty.EASY || canGP(inventory))
			&& (inventory.contains(Items.RUST_SPRAY)
				|| (difficulty >= Difficulty.MERCILESS && inventory.contains(Items.SPIKED_HELMET) && inventory.contains(Items.JUMP_BOOTS)));
                }
            }
            else if (region == 0xa) {
                return canLift(inventory) && canGP(inventory);
            }
            else if (region == 0x18) {
                return canSuperGP(inventory);
            }
        }
        else if (level.equals("S1")) {
            if (region == 0x1) {
                if (location == 0) {
			// The MINOR GLITCHES Logic for the Tree Interior Region can also apply to Main Area - Left Tree.
	                return inventory.contains(Items.JUMP_BOOTS) || inventory.contains(Items.FLUTE) || difficulty >= Difficulty.S_HARD;
                }
                else if (location == 2) {
                    return inventory.contains(Items.BEANSTALK_SEEDS);
                }
                else {
                    return true;
                }
            }
            else if (region == 0x9) {
		/*
		* Added MINOR GLITCHES Logic for the Tree Interior Region.
		* Use a dashjump wallclip to reach the ledge leading to the doors.
		*/
                return inventory.contains(Items.JUMP_BOOTS) || inventory.contains(Items.FLUTE) || difficulty >= Difficulty.S_HARD;
            }
            else if (region == 0x15) {
                return true;
            }
            else if (region == 0x17) {
                return inventory.contains(Items.BEANSTALK_SEEDS);
            }
            else if (region == 0x19) {
                return difficulty >= Difficulty.S_HARD || inventory.contains(Items.JUMP_BOOTS);
            }
            else if (region == 0x1a) {
                if (location == 0) {
                    return difficulty >= Difficulty.S_HARD || inventory.contains(Items.JUMP_BOOTS);
                }
                else {
		/*
		* Additional HARD Logic added for this check.
		* Bonk off enemies just as they are becoming unstunned near a block.
		* If done right, this will cause the enemy to gain 1 block of height.
		* Do this twice with the rightmost Spearhead so that it reaches the area above the Blue Chest. 
		* From there, High Jump off of the Spearhead to reach the area as normal.
		*/
            return difficulty > Difficulty.EASY 
		&& inventory.contains(Items.JUMP_BOOTS) 
			&& (canLift(inventory) || (difficulty >= Difficulty.HARD));
		// Secret Attic will never be required on Easy. Hard may require you to nudge enemies up to the top
		// without a glove
                }
            }
        }
        else if (level.equals("S2")) {
            if (region == 0x1) {
                if (location == 0) {
                    return canLift(inventory);
                }
                else if (location == 2) {
                    return canGP(inventory);
                }
                else {
                    return true;
                }
            }
            else if (region == 0x6) {
                if (location == 0) {
                    return inventory.contains(Items.PURITY_STAFF) && canSwim(inventory);
                }
                else {
                    return inventory.contains(Items.PURITY_STAFF) && canSwim(inventory)
                	&& (difficulty >= Difficulty.S_HARD ||
                	(inventory.contains(Items.SPIKED_HELMET) && inventory.contains(Items.GARLIC)));
                }
            }
            else if (region == 0xa) {
                if (location == 0) {
                    return canSwim(inventory)
                            && canGP(inventory)
                            && (canSuperGP(inventory)
                                || (difficulty >= Difficulty.HARD
                                    && canLift(inventory)
                                    && inventory.contains(Items.JUMP_BOOTS)));
                }
                else {
                    return canSwim(inventory) && canGP(inventory)
                            && (canSuperGP(inventory) || canLift(inventory));
                }
            }
            else if (region == 0x14) {
                return canGP(inventory) && canSwim(inventory);
            }
            else if (region == 0x19) {

                if (location == 0) { // S2 Spiders Upper Right
                    return (canSwim(inventory) || (difficulty >= Difficulty.S_HARD && canLift(inventory) && inventory.contains(Items.JUMP_BOOTS)))
                        && (inventory.contains(Items.GARLIC) || (difficulty >= Difficulty.S_HARD && inventory.contains(Items.JUMP_BOOTS)))
		            && (canSuperGP(inventory) || (difficulty >= Difficulty.HARD && inventory.contains(Items.JUMP_BOOTS)));
                }
                else { // S2 Spiders Lower Right
                    return (canSwim(inventory) || (difficulty >= Difficulty.S_HARD && inventory.contains(Items.JUMP_BOOTS) && canLift(inventory)))
		            && (inventory.contains(Items.GARLIC) || (difficulty >= Difficulty.S_HARD && inventory.contains(Items.JUMP_BOOTS)));
                }
            }
        }
        else if (level.equals("S3")) {
            if (region == 0x1) {
                return inventory.contains(Items.WIRE_WIZARD)
                        && inventory.contains(Items.GOLD_EYE_L)
                        && inventory.contains(Items.GOLD_EYE_R);
            }
            else if (region == 0x2) {
                if (location == 0) {
                    return inventory.contains(Items.WIRE_WIZARD);
                }
                else if (location == 1) {
                    return inventory.contains(Items.WIRE_WIZARD)
                            && (difficulty >= Difficulty.S_HARD || inventory.contains(Items.JUMP_BOOTS));
                }
                else if (location == 2) {
                    return inventory.contains(Items.WIRE_WIZARD)
                            && (difficulty >= Difficulty.S_HARD || inventory.contains(Items.JUMP_BOOTS))
                            && inventory.contains(Items.GARLIC);
                }
            }
            else if (region == 0x4 || region == 0x18 || region == 0x1a) {
                return inventory.contains(Items.BLUE_EYE_L) && inventory.contains(Items.BLUE_EYE_R);
            }
            else if (region == 0x8) {
                return true;
            }
            else if (region == 0xa) {
                return inventory.contains(Items.WIRE_WIZARD)
                        && inventory.contains(Items.GOLD_EYE_L)
                        && inventory.contains(Items.GOLD_EYE_R)
                        && inventory.contains(Items.SPIKED_HELMET)
                        && inventory.contains(Items.GARLIC)
                        && (difficulty >= Difficulty.S_HARD || inventory.contains(Items.JUMP_BOOTS))
                        && canSuperLift(inventory);
            }
        }
        else if (level.equals("S4")) {
            if (region == 0x1) {
                return true;
            }
            else if (region == 0xa || region == 0x19) {
		/* 
		* Consolidated the Green Chest Room and Spike Maze regions.
		* Added MINOR GLITCHES logic for the Green Chest Room and Spike Maze regions as well.
		* Perform Dashjump wallclips to get through the Green Chest Room without lifting up the Togēbas.
		*/
                return inventory.contains(Items.STONE_FOOT) && canSuperSwim(inventory) 
					&& (canSuperGP(inventory) || (difficulty >= Difficulty.S_HARD && inventory.contains(Items.JUMP_BOOTS)));
            }
            else if (region == 0x11 || region == 0x1c) {
				// Consolidated the 2 Blue Chest Area regions
                return inventory.contains(Items.RUST_SPRAY) && canGP(inventory);
            }
            else if (region == 0x15) {
                return inventory.contains(Items.STONE_FOOT);
            }
        }
        else if (level.equals("S5")) {
            if (region == 0x1) {
		/*
		* Added MINOR GLITCHES logic for Blue Chest Room - Upper Left.
		* Do a regular high enemy bounce after entering the room.
		* Next, charge towards the platform that contains the Throw Blocks.
		* Then, charge to the left to reach the next high bounce platform.
		* Finally, do a High Walljump to pass through the final high bounce platform.
		* One last regular Highjump will then reach this check.
		*/
		if (location == 0) {
			return inventory.contains(Items.RUST_SPRAY) && inventory.contains(Items.JUMP_BOOTS) && canGP(inventory)
                	        && (canLift(inventory) || difficulty >= Difficulty.S_HARD);
		}
		else {
			return inventory.contains(Items.RUST_SPRAY) 
				&& inventory.contains(Items.JUMP_BOOTS)
				&& canGP(inventory)
				&& canLift(inventory);
			}
		}
		else if (region == 0x3) {
                	return inventory.contains(Items.RUST_SPRAY)
				&& canGP (inventory)
				&& canLift(inventory)
				&& inventory.contains(Items.JUMP_BOOTS);
            }
            else if (region == 0x6) {
		/*
		* Added MINOR GLITCHES logic for the Smasher Room.
		* 1. Do a regular high enemy bounce after entering the room.
		* 2. Charge towards the platform that contains the Throw Blocks.
		* 3. Charge to the left to reach the next high bounce platform.
		* 4. Do a High Walljump to pass through the final high bounce platform.
		* 5. A regular Highjump will then reach Blue Chest Room - Upper Left.
		* 6. Jump across the gap, then do another walljump to reach the platform leading to the Smasher Room.
		* 7. Execute the Smasher Room as normal to reach the check.
		*/
                return inventory.contains(Items.RUST_SPRAY) 
			&& inventory.contains(Items.JUMP_BOOTS) 
			&& canGP(inventory)
			&& (difficulty >= Difficulty.S_HARD || canLift(inventory));
            }
            else if (region == 0x7) {
				// New MERCILESS Logic for Main Area - Center Left Ledge.
				// Using Ladder Scrolling at the start and a walljump, this check can be reached without needing Boots.
                if (location == 0) {
                    return inventory.contains(Items.JUMP_BOOTS) || difficulty >= Difficulty.MERCILESS;
                }
                else {
		/*
		* Added MERCILESS logic for this check, which is Main Area - Lower Right.
		* Using Ladder Scrolling at the start, this check can be reached without a Glove.
		*/
                    return (canLift(inventory) || difficulty >= Difficulty.MERCILESS);
                }
            }
            else if (region == 0xa) {
                return inventory.contains(Items.JUMP_BOOTS) && canLift(inventory)
                        && (difficulty >= Difficulty.HARD || canSuperGP(inventory));
            }
            else if (region == 0xe) {
                return difficulty >= Difficulty.S_HARD || inventory.contains(Items.SPIKED_HELMET);
            }
            else if (region == 0x15) {
		// Added MINOR GLITCHES logic to reach the Water Current Room. Do a walljump to reach this region.
                return (inventory.contains(Items.JUMP_BOOTS)
			|| difficulty >= Difficulty.S_HARD);
            }
            else if (region == 0x18) {
		// Added MINOR GLITCHES logic for the Invisibility Room. You can use a dashjump wallclip to reach the platform that has the pipe.
                return inventory.contains(Items.DETONATOR)
                        && (inventory.contains(Items.JUMP_BOOTS) || keyColor == 2 || difficulty >= Difficulty.S_HARD);
            }
        }
        else if (level.equals("S6")) {
            if (region == 0x1) {
                return true;
            }
            else if (region == 0x3) {
                return difficulty > Difficulty.EASY || canSuperGP(inventory);
            }
            else if (!inventory.contains(Items.JUMP_BOOTS)) {
                return false;
            }
            else if (region == 0x5) {
                if (location == 0) {
                    return true;
                }
                else {
			/*
			* Added a check for MERCILESS difficulty for Outside Upper - Near Moon Door.
			* Wrong Warp from the bottom outside area into the moon, then exit through the doors to reach this check.
			* Note, a soft reset is required for this to work, and this wrong warp only works during the day.
			* The soft reset can occur either immediately before entering the stage or after a suspend save.
			*/
                	return (inventory.contains(Items.SCISSORS) || (daytime && difficulty >= Difficulty.MERCILESS));
                }
            }
            else if (region == 0x1c) {
                if (location == 0) {
                    return true;
                }
                else {
                    return (difficulty >= Difficulty.HARD || inventory.contains(Items.SPIKED_HELMET)) && canLift(inventory);
                }
            }
            else if (!inventory.contains(Items.SCISSORS)) {
                return false;
            }
            else if (region == 0x7) {
                if (location == 2) {
                    return inventory.contains(Items.SPIKED_HELMET);
                }
                else {
                    return true;
                }
            }
            else if (region == 0x17) {
                return inventory.contains(Items.GONG) && canLift(inventory) && canSuperGP(inventory);
            }
        }
        else if (level.equals("E1")) {
            if (region == 0x1) {
                return true;
            }
            else if (region == 0x6) {
                return inventory.contains(Items.STONE_FOOT) && inventory.contains(Items.JUMP_BOOTS) && canLift(inventory);
            }
            else if (region == 0x7) {
                if (location == 0) {
                    return inventory.contains(Items.DETONATOR)
                            || (difficulty >= Difficulty.HARD
                                && canLift(inventory)
                                && inventory.contains(Items.JUMP_BOOTS));
                }
                else if (location == 1) {
                    return difficulty >= Difficulty.S_HARD || inventory.contains(Items.SPIKED_HELMET);
                }
                else {
                    return inventory.contains(Items.DETONATOR);
                }
            }
            else if (region == 0x14 || region == 0x16) {
                return inventory.contains(Items.STONE_FOOT);
            }
        }
        else if (level.equals("E2")) {
            if (region == 0x1) {
                if (location == 0) {
                    return true;
                }
                else if (location == 1) {
			/*
			* Added MERCILESS logic to this check, which is Main Area - Above Rock.
			* Use Water Scrolling to screen wrap upwards. The terrain available will let you reach this check.
			*/
        		return inventory.contains(Items.JUMP_BOOTS)
				|| (difficulty >= Difficulty.MERCILESS && canSwim(inventory));
                }
                else {
			/*
			* Added MERCILESS logic to this check, which is Main Area - Lower Right.
			* Use Water Scrolling to screen wrap upwards, then use the terrain to collect Main Area - Above Rock. 
			* Next, get hit by the Polar Bear closest to the rock and have it send you to the right. 
			* Go just left to the wall you hit without crouching. 
			* Then, do an in-map save to set your position to the bottom of the pool. 
			* Charge and immediately crouch to get past the crouch space to reach this check without dayTime or Super Flippers. 
			* Note, make sure you're holding neutral when the crouch charge ends.
			*/
        		return daytime || canSuperSwim(inventory)
				|| (difficulty >= Difficulty.MERCILESS && canSwim(inventory) && inventory.contains(Items.GARLIC));
                }
            }
            else if (region == 0x6 || region == 0x17) {
                return inventory.contains(Items.PURITY_STAFF) && canSwim(inventory);
            }
            else if (region == 0x8) {
                return canLift(inventory);
            }
            else if (region == 0x14) {
                return canLift(inventory);
            }
            else if (region == 0x15) {
                return inventory.contains(Items.PURITY_STAFF) && canSwim(inventory) && inventory.contains(Items.SPIKED_HELMET);
            }
            else if (region == 0x1A) {
		/*
		* Added MERCILESS logic to this region, which is the Blue Chest Area.
		* Use Water Scrolling to screen wrap upwards, then use the terrain to collect Main Area - Above Rock. 
		* Next, get hit by the Polar Bear closest to the rock and have it send you to the right. 
		* Go just left to the wall you hit without crouching. 
		* Then, do a suspend save to set your position to the bottom of the pool. 
		* Charge and immediately crouch to get past the crouch space. Make sure you're holding neutral when the crouch charge ends.
		* Finally, jump up to get past the current and climb the ladder to reach this region without dayTime or Super Flippers.
		*/
                return daytime || canSuperSwim(inventory)
			|| (difficulty >= Difficulty.MERCILESS && canSwim(inventory) && inventory.contains(Items.GARLIC));
            }
        }
        else if (level.equals("E3")) {
            if (region == 0x1) {
                if (location == 1) {
                    return true;
                }
                else {
                    return canLift(inventory) || difficulty >= Difficulty.S_HARD;
                }
            }
            else if (!canLift(inventory) && difficulty < Difficulty.S_HARD) {
                return false;
            }
            else if (region == 0x4 || region == 0x9) {
                return canSuperLift(inventory);
            }
            else if (region == 0x7) {
		/*
		* Added MINOR GLITCHES logic to this region, the E3 Spike Maze.
		* These checks can be accessed without a Glove by grabbing the right-side owl and dismounting while clipped into the platforms.
		* You can then use the spikes to damage boost to the other owl and then reach the checks as normal.
		* In essence, this is an extension of the MINOR GLITCHES execution for the starting room.
		*/
                if (location < 2) {
                    return inventory.contains(Items.BRICK) && (difficulty >= Difficulty.S_HARD || canLift(inventory));
                }
                else {
                    return inventory.contains(Items.BRICK) && canGP(inventory) && (difficulty >= Difficulty.S_HARD || canLift(inventory));
                }
            }
            else if (region == 0x1a) {
                if (!daytime && !canSuperLift(inventory) && difficulty < Difficulty.S_HARD) {
                    return false;
                }
                else if (location == 0) {
                    return inventory.contains(Items.JUMP_BOOTS);
                }
                else {
                    if (difficulty >= Difficulty.HARD) {
                        return true;
                    }
                    else {
                        return canSuperGP(inventory) || inventory.contains(Items.JUMP_BOOTS);
                    }
                }
            }
        }
        else if (level.equals("E4")) {
            if (region == 0x1) {
                if (location == 1) {
                    return daytime || inventory.contains(Items.JUMP_BOOTS) || difficulty >= Difficulty.S_HARD;
                }
                else {
                    return true;
                }
            }
            else if (region == 0x4) {
                return true;
            }
            else if (region == 0x7) {
                return inventory.contains(Items.DETONATOR) && inventory.contains(Items.JUMP_BOOTS);
            }
            else if (region == 0xa) {
                return inventory.contains(Items.GARLIC)
                        && (inventory.contains(Items.SPIKED_HELMET)
                            || (difficulty > Difficulty.EASY && inventory.contains(Items.JUMP_BOOTS)));
            }
            else if (region == 0x14) {
                return inventory.contains(Items.GARLIC) && canLift(inventory);
            }
            else if (region == 0x1B) {
                if (location == 1) {
                    return daytime || inventory.contains(Items.JUMP_BOOTS) || difficulty >= Difficulty.S_HARD;
                }
                else {
                    return (daytime && canSuperLift(inventory))
                            || (!daytime
                                && (inventory.contains(Items.JUMP_BOOTS) || difficulty >= Difficulty.S_HARD)
                                && canLift(inventory));
                }
            }
        }
        else if (level.equals("E5")) {
            if (region == 0x3) {
		/*
		* Added MERCILESS logic for this region, which is the Blue Hub Room (0x3). 
		* You can do a Double Bear Bounce from the starting area to reach this region. 
		* This requires the Remote Control to gain access, but skips needing the Blue + Red Keycards.
		* The Boots and Golden Glove are both required to perform this execution.
		* Added HARD logic to the Blue Hub Room as well. 
		* These checks can be reached without the Helmet by performing very precise dashes to create footholds in the middle set of blocks.
		& This also puts the MERCILESS execution in logic without the Helmet as well.
		*/ 
                return ((inventory.contains(Items.BLUE_KEY_CARD) && inventory.contains(Items.RED_KEY_CARD) && canLift(inventory))
					&& (inventory.contains(Items.SPIKED_HELMET) || (difficulty >= Difficulty.HARD && inventory.contains(Items.JUMP_BOOTS))))
						|| (difficulty >= Difficulty.MERCILESS && inventory.contains(Items.WARP_REMOTE)
							&& canSuperLift(inventory) && inventory.contains(Items.JUMP_BOOTS));
            }
            else if (region == 0x6) {
		/* 
		* Added MERCILESS Logic to the Green Falling Warp Room - Bottom Center check.
		* This is reached by doing a single-tile walljump to scale the bottom set of throw blocks after entering the area.
		* A Glove check has been added specifically for the Key Cards option since that option strictly requires having a Glove.
		*/ 
                return (inventory.contains(Items.BLUE_KEY_CARD) && inventory.contains(Items.RED_KEY_CARD) && canLift(inventory))
			|| (inventory.contains(Items.WARP_REMOTE) && (difficulty >= Difficulty.MERCILESS || canLift(inventory)));
			}
			else if (region == 0x7) {
				return canLift(inventory)
                    && ((inventory.contains(Items.BLUE_KEY_CARD) && inventory.contains(Items.RED_KEY_CARD))
                        || inventory.contains(Items.WARP_REMOTE));
            }
            else if (region == 0x9) {
                return inventory.contains(Items.WARP_REMOTE);
            }
            else if (region == 0xa || region == 0xc) {
		/*
		* Added MERCILESS logic for these regions, which are the Blue Unstable Platforms Room (0xa) and the Starting Area (0xc). 
		* You can do a Double Bear Bounce from the starting area to reach this region. 
		* This requires the Remote Control to gain access, but skips needing the Blue + Red Keycards.
		* The Boots and Golden Glove are both required to perform this execution.
		*/ 
                return (inventory.contains(Items.BLUE_KEY_CARD) && inventory.contains(Items.RED_KEY_CARD) && canLift(inventory))
			|| (difficulty >= Difficulty.MERCILESS && inventory.contains(Items.WARP_REMOTE)
				&& canSuperLift(inventory) && inventory.contains(Items.JUMP_BOOTS));
            }
            else if (region == 0x18) {
		if (location == 2) {
			// NORMAL logic added for Hammerbot Room - Upper Right.
			// You still need to get hit by the Hammerbot, but you have to sneak under a spike while springing and can't lift the Hammerbot.
			return canSuperLift(inventory)
				|| (difficulty > Difficulty.EASY && canLift(inventory));
		}
		else {
			return canLift(inventory);
		}
            }
        }
        else if (level.equals("E6")) {
            if (region == 0x1) {
                if (location == 0) {
                    return true;
                }
                else if (location == 1) {
                    if (difficulty <= Difficulty.NORMAL) {
                        return canLift(inventory) && canSuperGP(inventory);
                    }
                    else if (difficulty <= Difficulty.HARD) {
                        // require lift to get through walls but allow jellybob manip
                        return canLift(inventory) &&
                            (canGP(inventory) || inventory.contains(Items.SPIKED_HELMET));
                    }
                    else {
                        return canGP(inventory) || inventory.contains(Items.SPIKED_HELMET);
                    }
                }
                else {
                    return canLift(inventory);
                }
            }
            else if (region == 0x4) {
                return canLift(inventory) && inventory.contains(Items.JACKHAMMER);
            }
            else if (region == 0x7) {
                if (location == 0) {
                    return canLift(inventory) && canGP(inventory)
                            && (difficulty >= Difficulty.HARD || inventory.contains(Items.FIRE_EXTINGUISHER));
                }
                else {
                    return canLift(inventory)
                            && (difficulty >= Difficulty.HARD || inventory.contains(Items.FIRE_EXTINGUISHER));
                }
            }
            else if (region == 0x11) {
		// MINOR GLITCHES logic added for Smasher Room.
		// Wallclip to climb over the barriers that you'd need to throw barrels at.
                return (difficulty >= Difficulty.HARD || inventory.contains(Items.FIRE_EXTINGUISHER))
					&& (canLift(inventory) || difficulty >= Difficulty.S_HARD);
            }
            else if (region == 0x14) {
                return (difficulty >= Difficulty.S_HARD || canLift(inventory))
                        && (keyColor == 0 || canSuperGP(inventory));
            }
            else if (region == 0x18) {
		/* 
		/ Added MERCILESS logic to Barrel Puzzle Room - Upper Floor Near Start.
		/ You can use a Single Tile Walljump to escape the spot without needing Boots.
		/ The NORMAL logic that was added is to use the Pneumo to float into the room without Boots.
		/ A Glove check has been added here to EASY Difficulty, which is to break the Throw Blocks traditionally.
		*/
                if (location == 0) {
                    return inventory.contains(Items.PICKAXE) 
						&& (difficulty > Difficulty.EASY || canLift(inventory))
						&& (difficulty >= Difficulty.MERCILESS || inventory.contains(Items.JUMP_BOOTS));
                }
                else {
		// The NORMAL logic that was added is to use the Pneumo to float into the room without Boots.
                    return inventory.contains(Items.PICKAXE) && canLift(inventory)
						&& (difficulty > Difficulty.EASY || inventory.contains(Items.JUMP_BOOTS));
                }
            }
        }
        else if (level.equals("E7")) {
            if (region == 0x1) {
                if (location == 0) {
                    return inventory.contains(Items.JUMP_BOOTS);
                }
                else {
                    return true;
                }
            }
            else if (region == 0x4) {
                if (location == 0) {
                    return inventory.contains(Items.VALVE) && canLift(inventory);
                }
                else {
		/* 
		* Added MINOR GLITCHES logic for the Vampire Area - Center Left check.
		* This check can be obtained without a Glove, using just Boots.
		* Perform a High Walljump to reach the Spearhead located in the upper right corner.
		* Stun it, then walk into it towards the left and knock it down the ledges necessary.
		* Once it's off the ledge that the door leading to the Hammerbot room is on, do the jumps across like normal to reach the check.
		*/
                return difficulty > Difficulty.EASY 
			&& inventory.contains(Items.VALVE) 
			&& inventory.contains(Items.JUMP_BOOTS)
			&& (difficulty >= Difficulty.S_HARD || canLift(inventory));
                }
            }
            else if (region == 0x7) {
                if (location == 0) {
                    return inventory.contains(Items.VALVE);
                }
                else {
                    return inventory.contains(Items.VALVE) && canSuperLift(inventory);
                }
            }
            else if (region == 0xa) {
                return true;
            }
            else if (region == 0x15) {
                if (location == 0) {
                    return true;
                }
                else {
                    return inventory.contains(Items.DEMON_BLOOD) || difficulty >= Difficulty.S_HARD;
                }
            }
            else if (region == 0x18) {
                return inventory.contains(Items.VALVE) && (difficulty >= Difficulty.S_HARD || inventory.contains(Items.JUMP_BOOTS)) && canSuperLift(inventory);
            }
        }
        return false;
    }

    /**
     * Helper function for canAccess() - checks if Wario can access the given level by approaching from the west.
     * This was implemented to avoid circular dependencies.
     */
    private static boolean canAccessFromWest(String location, List<Integer> inventory) {
        if (location.equals("W1")) {
            return canAccess("NW", inventory);
        }
        else if (location.equals("W2")) {
            return inventory.contains(Items.DOCUMENT_A) && inventory.contains(Items.DOCUMENT_B)
                    && canAccessFromWest("W1", inventory);
        }
        else if (location.equals("W3")) {
            return inventory.contains(Items.RAINCLOUD_JAR)
                    && canAccessFromWest("W2", inventory);
        }
        else if (location.equals("W4")) {
            return inventory.contains(Items.RAINCLOUD_JAR)
                    && canAccessFromWest("W3", inventory);
        }
        else if (location.equals("S1")) {
            return canAccess("SW", inventory);
        }
        else if (location.equals("S2")) {
            return inventory.contains(Items.MUSIC_BOX_1)
                    && canAccessFromWest("S1", inventory);
        }
        else if (location.equals("S3")) {
            return ((inventory.contains(Items.BLUE_RING) && inventory.contains(Items.RED_RING))
                    || (inventory.contains(Items.ANGER_HALBERD) && inventory.contains(Items.ANGER_SPELL)))
                    && canAccessFromWest("S2", inventory);
        }
        else if (location.equals("E1")) {
            return canAccess("SE", inventory);
        }
        else if (location.equals("E2")) {
            return inventory.contains(Items.FREEZE_CANE) && inventory.contains(Items.FREEZE_SPELL)
                    && canAccessFromWest("E1", inventory);
        }
        else if (location.equals("E4")) {
            return inventory.contains(Items.RED_ARTIFACT) && inventory.contains(Items.GREEN_ARTIFACT) && inventory.contains(Items.BLUE_ARTIFACT)
                    && canAccessFromWest("E2", inventory);
        }
        else if (location.equals("E7")) {
            return inventory.contains(Items.TORCH)
                    && canAccessFromWest("E4", inventory);
        }
        return false;
    }

    /**
     * Helper function for canAccess() - checks if Wario can access the given level by approaching from the east.
     * This was implemented to avoid circular dependencies.
     */
    private static boolean canAccessFromEast(String location, List<Integer> inventory) {
        if (location.equals("W1")) {
            return inventory.contains(Items.DOCUMENT_A) && inventory.contains(Items.DOCUMENT_B)
                    && canAccessFromEast("W2", inventory);
        }
        else if (location.equals("W2")) {
            return inventory.contains(Items.RAINCLOUD_JAR)
                    && canAccessFromEast("W3", inventory);
        }
        else if (location.equals("W3")) {
            return inventory.contains(Items.RAINCLOUD_JAR)
                    && canAccessFromEast("W4", inventory);
        }
        else if (location.equals("W4")) {
            return canAccess("SW", inventory);
        }
        else if (location.equals("S1")) {
            return inventory.contains(Items.MUSIC_BOX_1)
                    && canAccessFromEast("S2", inventory);
        }
        else if (location.equals("S2")) {
            return inventory.contains(Items.BLUE_RING) && inventory.contains(Items.RED_RING)
                    && canAccessFromEast("S3", inventory);
        }
        else if (location.equals("S3")) {
            return canAccess("SE", inventory);
        }
        else if (location.equals("E1")) {
            return inventory.contains(Items.FREEZE_CANE) && inventory.contains(Items.FREEZE_SPELL)
                    && canAccessFromEast("E2", inventory);
        }
        else if (location.equals("E2")) {
            return inventory.contains(Items.RED_ARTIFACT) && inventory.contains(Items.GREEN_ARTIFACT) && inventory.contains(Items.BLUE_ARTIFACT)
                    && canAccessFromEast("E4", inventory);
        }
        else if (location.equals("E4")) {
            return inventory.contains(Items.TORCH);
        }
        else if (location.equals("E7")) {
            return inventory.contains(Items.TORCH);
        }
        return false;
    }

    /**
     * Deep copy a level list
     *
     * @param levelList list to copy
     */
    private static List<Level> cloneLevelList(List<Level> levelList) {
        List<Level> ret = new ArrayList<>(levelList);
        for (int i = 0; i < ret.size(); i++) {
            ret.set(i,new Level(ret.get(i)));
        }
        return ret;
    }
}
