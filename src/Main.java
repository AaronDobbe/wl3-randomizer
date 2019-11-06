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

    private static int fails = 0;

    private static String vanillaFileLocation;

    private static GUI gui;

    private static final String VERSION = "v0.10.2";

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
        boolean mapShuffle = options.containsKey("mapShuffle") && "true".equals(options.get("mapShuffle"));

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

        // randomize lists of non-junk items and locations
        Collections.shuffle(inventory, rng);
        Collections.shuffle(locations, rng);
        if (mapShuffle) {
            mapList = shuffleMap(mapList,rng);
        }

        worldMap = mapList.toArray(new Integer[25]);

        List<List<Integer>> keyIndexes = null;
        if (keyShuffle) {
            // prepare key index (ordered list of locations per level where keys will be attempted to be placed)
            keyIndexes = new ArrayList<>();
            for (int i = 0; i < 25; i++) {
                List<Integer> subIndex = new ArrayList<>();
                for (int j = 0; j < 12; j++) {
                    subIndex.add(j);
                }
                Collections.shuffle(subIndex,rng);
                keyIndexes.add(subIndex);
            }
        }

        List<Integer> leftInventory = new Vector<>();
        boolean axeStart = options.containsKey("axeStart") && options.get("axeStart").equals("true");
        if (axeStart) {
            // pre-place the axe in the gray chest of level 0
            inventory.remove(new Integer(Items.AXE));
            locations.remove(new Integer(worldMap[0]*4));
            leftInventory.add(Items.AXE);
            Collections.replaceAll(treasures,Items.AXE,null);
            treasures.set(worldMap[0]*4, Items.AXE);
            if (keyShuffle) {
                // also place gray key
                placeKey(levelList.get(worldMap[0]),worldMap[0],0,keyIndexes.get(worldMap[0]),new ArrayList<>());
            }
        }
        int numFails=0;
        // attempt to place treasures
        boolean bossBoxes = options.containsKey("bossBoxes") && options.get("bossBoxes").equals("true");
        while (((bossBoxes && !placeItemsAssumed(leftInventory, inventory, locations, treasures, levelList, keyIndexes, 5))
                || (!bossBoxes && !placeItemsLeft(leftInventory, inventory, locations, treasures, levelList, keyIndexes)))) {
            // could not finish in reasonable time
            // if no user seed provided, generate a new seed and re-randomize using that
            if (userSeed != null && userSeed.length() > 0) {
                gui.log("Invalid seed. Please double-check the seed and try again.");
                return;
            }

            numFails++;
            seed = seedRNG.nextLong();
            rng = new Random(seed);
            inventory = new ArrayList<>(pureInventory);
            locations = new ArrayList<>(pureLocations);
            mapList = new ArrayList<>(pureMapList);
            levelList = cloneLevelList(pureLevelList);
            Collections.shuffle(inventory, rng);
            Collections.shuffle(locations, rng);
            if (mapShuffle) {
                mapList = shuffleMap(mapList,rng);
            }
            worldMap = mapList.toArray(new Integer[25]);

            if (keyShuffle) {
                keyIndexes = new ArrayList<>();
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
                inventory.remove(new Integer(Items.AXE));
                locations.remove(new Integer(worldMap[0]*4));
                leftInventory.add(Items.AXE);
                Collections.replaceAll(treasures,Items.AXE,null);
                treasures.set(worldMap[0]*4, Items.AXE);
                if (keyShuffle) {
                    placeKey(levelList.get(worldMap[0]),worldMap[0],0,keyIndexes.get(worldMap[0]),new ArrayList<>());
                }
            }
            fails = 0;
        }

        // items have been placed, now shuffle list of junk and use it to fill in the remaining locations
        Collections.shuffle(junkList,rng);
        boolean excludeJunk = options.containsKey("excludeJunk") && "true".equals(options.get("excludeJunk"));

        for (Integer junkItem : junkList) {
            if (excludeJunk && junkItem != Items.TIME_BUTTON && junkItem != Items.MAGNIFYING_GLASS) {
                // if junk items are excluded, replace with empty boxes
                junkItem = Items.EMPTY;
            }
            for (int i = 0; i < 100; i++) {
                if (finalTreasures[i] == 0) {
                    finalTreasures[i] = junkItem;
                    break;
                }
            }
        }

        // build playthrough to set up hints if requested
        byte[] playthrough = null;
        if (options.containsKey("hints") && !options.get("hints").equals("vanilla")) {
            boolean strategicHints = options.get("hints").equals("strategic");
            playthrough = buildPlaythrough(rng, strategicHints);
        }

        // randomize music if requested
        Integer[] music = null;
        if (options.containsKey("music")) {
            if (options.get("music").equals("shuffled")) {
                music = shuffleMusic(false, rng);
            }
            else if (options.get("music").equals("chaos")) {
                music = shuffleMusic(true, rng);
            }
        }

        // randomize level palettes if requested
        // 2336 numbers
        int[] paletteSwitches = null;
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

        // patch vanilla ROM file and create randomized ROM
        try {
            Patcher.patch(vanillaFileLocation,
                    finalTreasures,
                    encodeSeed(seed),
                    playthrough,
                    music,
                    mapShuffle ? worldMap : null,
                    paletteSwitches,
                    objColors,
                    chestColors,
                    keyShuffle ? finalKeyLocations : null,
                    golfOrder,
                    cutsceneSkip,
                    VERSION);
        } catch (IOException e) {
            gui.log("Error occurred while generating randomized game: " + e.getMessage());
            return;
        }

        gui.log("Generated randomized game with seed " + encodeSeed(seed));
        gui.log("Randomized ROM has been saved as WL3-randomizer-" + VERSION + "-" + encodeSeed(seed) + ".gbc");
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
        List<Integer> bosses = Arrays.asList(bossArray);
        for (Integer item : rightInventory) {
            if (bossBoxes > 0 && item > Items.MUSIC_BOX_5) {
                continue;
            }
            List<Integer> nextRightInventory = new Vector<>(rightInventory);
            nextRightInventory.remove(item);
            List<Integer> curInventory = new Vector<>(nextRightInventory);
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
                if ((canAccessKeyLocation(level.getLevelName(),candidate.getRegion(),location,true, inventory) && canAccess(locationNames[levelNum*4+keyNum],inventory,null,false,false,true)) ||
                        (canAccessKeyLocation(level.getLevelName(),candidate.getRegion(),location,false, inventory) && canAccess(locationNames[levelNum*4+keyNum],inventory,null,false,false,false))) {
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
                else if (nextRightInventory.size() < 60 && placeItemsAssumed(nextLeftInventory, nextRightInventory, nextLocations, nextTreasures,nextLevelList,keyIndexes, 0)) {
                    return true;
                }
                else if ((nextRightInventory.size() >= 60) && placeItemsLeft(nextLeftInventory,nextRightInventory,nextLocations,nextTreasures,nextLevelList,keyIndexes)) {
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
    private static List<Integer> shuffleMap(List<Integer> initialMap, Random rng) {
        Vector<Integer> shuffledMap = new Vector<>(initialMap);
        Collections.shuffle(shuffledMap, rng);
        Integer firstLevel = 0;
        Integer[] firstLevelsArr = {0, 1, 2, 4, 6, 7, 9, 13, 14, 15, 17, 18, 19, 21, 24};
        List<Integer> firstLevels = Arrays.asList(firstLevelsArr);
        for (Integer level : shuffledMap) {
            if (firstLevels.contains(level)) {
                firstLevel = level;
                break;
            }
        }
        shuffledMap.remove(firstLevel);
        shuffledMap.insertElementAt(firstLevel,0);
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
            if (seed % 2 == 1) {
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
            seed += val;
        }
        return seed;
    }

    /**
     * Build a playthrough to set up hints.
     *
     * @param rng random object to use
     * @param strategic true for "strategic" hints
     * @return An array of treasures, in the order they should be hinted at
     */
    private static byte[] buildPlaythrough(Random rng, boolean strategic) {
        List<Integer> inventory = new Vector<>();
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
            Collections.shuffle(newItems, rng);
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
                    && (canGP(inventory) || inventory.contains(Items.GARLIC));
        }
        else if (location.equals("N1G")) {
            return canAccess("N1", inventory)
                    && inventory.contains(Items.WIND)
                    && inventory.contains(Items.WIND_BAG);
        }
        else if (location.equals("N1B")) {
            return canAccess("N1", inventory)
                    && inventory.contains(Items.POWDER)
                    && inventory.contains(Items.JUMP_BOOTS)
                    && canLift(inventory)
                    && canGP(inventory);
        }
        else if (location.equals("N2S")) {
            return canAccess("N2", inventory);
        }
        else if (location.equals("N2R")) {
            return canAccess("N2", inventory)
                    && (inventory.contains(Items.FLUTE)
                    || inventory.contains(Items.JUMP_BOOTS)
                    || (inventory.contains(Items.GARLIC) && canSuperGP(inventory)));
        }
        else if (location.equals("N2G")) {
            return canAccess("N2", inventory)
                    && canGP(inventory)
                    && (inventory.contains(Items.FLUTE)
                    || inventory.contains(Items.JUMP_BOOTS)
                    || (inventory.contains(Items.GARLIC) && canSuperGP(inventory)));
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
            return canAccess("N3", inventory)
                    && inventory.contains(Items.BEANSTALK_SEEDS);
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
            return canAccess("N4", inventory)
                    && inventory.contains(Items.PUMP)
                    && canLift(inventory);
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
                    && inventory.contains(Items.WIRE_WIZARD);
        }
        else if (location.equals("N5B")) {
            return canAccess("N5", inventory)
                    && inventory.contains(Items.GROWTH_SEED)
                    && inventory.contains(Items.GARLIC)
                    && canSwim(inventory);
        }
        else if (location.equals("N6S")) {
            return canAccess("N6", inventory)
                    && inventory.contains(Items.GARLIC)
                    && inventory.contains(Items.SPIKED_HELMET)
                    && canSwim(inventory)
                    && canGP(inventory);
        }
        else if (location.equals("N6R")) {
            return canAccess("N6", inventory)
                    && inventory.contains(Items.GARLIC)
                    && inventory.contains(Items.PURITY_STAFF)
                    && canSwim(inventory)
                    && canGP(inventory);
        }
        else if (location.equals("N6G")) {
            return canAccess("N6", inventory)
                    && canSuperGP(inventory);
        }
        else if (location.equals("N6B")) {
            return canAccess("N6", inventory)
                    && canSuperGP(inventory)
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
                    && (canLift(inventory) || inventory.contains(Items.JUMP_BOOTS))
                    && (!dayOnly || inventory.contains(Items.GARLIC));
        }
        else if (location.equals("W2S")) {
            return canAccess("W2", inventory);
        }
        else if (location.equals("W2R")) {
            return canAccess("W2", inventory)
                    && inventory.contains(Items.WHEELS);
        }
        else if (location.equals("W2G")) {
            return canAccess("W2", inventory)
                    && inventory.contains(Items.WHEELS)
                    && inventory.contains(Items.FLUTE);
        }
        else if (location.equals("W2B")) {
            return canAccess("W2", inventory)
                    && inventory.contains(Items.STONE_FOOT);
        }
        else if (location.equals("W3S")) {
            return canAccess("W3", inventory)
                    && canGP(inventory);
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
            return canAccess("W3", inventory)
                    && inventory.contains(Items.PUMP)
                    && canSwim(inventory);
        }
        else if (location.equals("W4S")) {
            return canAccess("W4", inventory);
        }
        else if (location.equals("W4R")) {
            return canAccess("W4", inventory)
                    && (inventory.contains(Items.SPIKED_HELMET) || (canLift(inventory) && inventory.contains(Items.JUMP_BOOTS)));
        }
        else if (location.equals("W4G")) {
            return canAccess("W4", inventory)
                    && inventory.contains(Items.JUMP_BOOTS)
                    && canSuperLift(inventory)
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
                    && canSwim(inventory);
        }
        else if (location.equals("W5G")) {
            return canAccess("W5", inventory)
                    && canSwim(inventory)
                    && canLift(inventory);
        }
        else if (location.equals("W5B")) {
            return canAccess("W5", inventory)
                    && canSwim(inventory)
                    && canLift(inventory);
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
            return canAccess("W6", inventory)
                    && inventory.contains(Items.JUMP_BOOTS)
                    && inventory.contains(Items.FIRE_EXTINGUISHER);
        }
        else if (location.equals("W6B")) {
            return canAccess("W6", inventory)
                    && inventory.contains(Items.RUST_SPRAY)
                    && canLift(inventory);
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
                    && (inventory.contains(Items.FLUTE) || inventory.contains(Items.JUMP_BOOTS));
        }
        else if (location.equals("S1B")) {
            return canAccess("S1", inventory)
                    && inventory.contains(Items.JUMP_BOOTS);
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
                    && inventory.contains(Items.GARLIC)
                    && inventory.contains(Items.SPIKED_HELMET)
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
                    && inventory.contains(Items.JUMP_BOOTS)
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
            return canAccess("S4", inventory)
                    && inventory.contains(Items.STONE_FOOT)
                    && canSuperSwim(inventory)
                    && canSuperGP(inventory);
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
                    && canSuperGP(inventory);
        }
        else if (location.equals("S5G")) {
            return canAccess("S5", inventory)
                    && inventory.contains(Items.DETONATOR);
        }
        else if (location.equals("S5B")) {
            return canAccess("S5", inventory)
                    && inventory.contains(Items.RUST_SPRAY)
                    && inventory.contains(Items.JUMP_BOOTS)
                    && canGP(inventory)
                    && canLift(inventory);
        }
        else if (location.equals("S6S")) {
            return canAccess("S6", inventory);
        }
        else if (location.equals("S6R")) {
            return canAccess("S6", inventory)
                    && inventory.contains(Items.JUMP_BOOTS)
                    && inventory.contains(Items.SPIKED_HELMET)
                    && canLift(inventory);
        }
        else if (location.equals("S6G")) {
            return canAccess("S6", inventory)
                    && inventory.contains(Items.SCISSORS)
                    && inventory.contains(Items.JUMP_BOOTS);
        }
        else if (location.equals("S6B")) {
            return canAccess("S6", inventory)
                    && inventory.contains(Items.GONG)
                    && inventory.contains(Items.SCISSORS)
                    && inventory.contains(Items.JUMP_BOOTS)
                    && canSuperGP(inventory)
                    && canLift(inventory);
        }
        else if (location.equals("E1S")) {
            return canAccess("E1", inventory);
        }
        else if (location.equals("E1R")) {
            return canAccess("E1", inventory)
                    && inventory.contains(Items.STONE_FOOT)
                    && (canGP(inventory) || (canSuperSwim(inventory) && inventory.contains(Items.JUMP_BOOTS)));
        }
        else if (location.equals("E1G")) {
            return canAccess("E1", inventory)
                    && inventory.contains(Items.JUMP_BOOTS)
                    && inventory.contains(Items.STONE_FOOT);
        }
        else if (location.equals("E1B")) {
            return canAccess("E1", inventory)
                    && inventory.contains(Items.DETONATOR);
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
            return canAccess("E2", inventory)
                    && (dayOnly || canSuperSwim(inventory));
        }
        else if (location.equals("E3S")) {
            return canAccess("E3", inventory)
                    && canGP(inventory);
        }
        else if (location.equals("E3R")) {
            return canAccess("E3", inventory)
                    && canLift(inventory);
        }
        else if (location.equals("E3G")) {
            return canAccess("E3", inventory)
                    && canLift(inventory);
        }
        else if (location.equals("E3B")) {
            return canAccess("E3", inventory)
                    && canLift(inventory);
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
                    && (dayOnly
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
            return canAccess("E5", inventory)
                    && (inventory.contains(Items.WARP_REMOTE) || (inventory.contains(Items.BLUE_KEY_CARD) && inventory.contains(Items.RED_KEY_CARD)))
                    && canLift(inventory);
        }
        else if (location.equals("E5B")) {
            return canAccess("E5", inventory)
                    && inventory.contains(Items.BLUE_KEY_CARD)
                    && inventory.contains(Items.RED_KEY_CARD)
                    && canLift(inventory);
        }
        else if (location.equals("E6S")) {
            return canAccess("E6", inventory)
                    && canLift(inventory);
        }
        else if (location.equals("E6R")) {
            return canAccess("E6", inventory)
                    && inventory.contains(Items.FIRE_EXTINGUISHER)
                    && canLift(inventory)
                    && canGP(inventory);
        }
        else if (location.equals("E6G")) {
            return canAccess("E6", inventory)
                    && inventory.contains(Items.JACKHAMMER)
                    && canLift(inventory);
        }
        else if (location.equals("E6B")) {
            return canAccess("E6", inventory)
                    && inventory.contains(Items.PICKAXE)
                    && canLift(inventory);
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

        return canAccessKeyLocation(location.substring(0,2),keyLoc.getRegion(),keyLoc.getSubLocation(),daytime,inventory);
    }

    /**
     * Check if Wario can access a potential key location with his current inventory.
     *
     * @param level     Two-character level code (e.g. "E3")
     * @param region    Which region the location exists in (identified by the number of its top-left sector
     * @param location  In a region, which sub-location points to the key
     * @param daytime   True if it's daytime
     * @param inventory Wario's current inventory
     * @return True if the key location can be reached
     */
    private static boolean canAccessKeyLocation(String level, int region, int location, boolean daytime, List<Integer> inventory) {
        if (level.equals("N1")) {
            if (region == 0x2) {
                return inventory.contains(Items.POWDER) && inventory.contains(Items.JUMP_BOOTS);
            }
            else if (region == 0x3) {
                if (location == 2) {
                    return true;
                }
                else {
                    return inventory.contains(Items.JUMP_BOOTS);
                }
            }
            else if (region == 0x6) {
                return inventory.contains(Items.POWDER) && inventory.contains(Items.JUMP_BOOTS);
            }
            else if (region == 0x7) {
                if (location == 0) {
                    return canSuperGP(inventory);
                }
                else {
                    return true;
                }
            }
            else if (region == 0xd) {
                return canGP(inventory) || inventory.contains(Items.GARLIC);
            }
            else if (region == 0x14) {
                return canLift(inventory) && inventory.contains(Items.JUMP_BOOTS);
            }
            else if (region == 0x17) {
                return canSwim(inventory);
            }
        }
        else if (level.equals("N2")) {
            if (region == 0x1) {
                if (location == 2) {
                    return canSuperGP(inventory) && inventory.contains(Items.SPIKED_HELMET);
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
                    return inventory.contains(Items.JUMP_BOOTS) || inventory.contains(Items.FLUTE);
                }
                else {
                    return true;
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
                            && ((inventory.contains(Items.JUMP_BOOTS) || inventory.contains(Items.FLUTE)) || (canSuperGP(inventory) && inventory.contains(Items.GARLIC)));
                }
                else {
                    return (inventory.contains(Items.JUMP_BOOTS) || inventory.contains(Items.FLUTE)) || (canSuperGP(inventory) && inventory.contains(Items.GARLIC));
                }
            }
        }
        else if (level.equals("N3")) {
            if (region == 0x1) {
                if (location == 0) {
                    return inventory.contains(Items.BEANSTALK_SEEDS);
                }
                else if (location == 2) {
                    return inventory.contains(Items.BLUE_CHEMICAL) && inventory.contains(Items.RED_CHEMICAL);
                }
                else {
                    return true;
                }
            }
            else if (region == 0x6) {
                return inventory.contains(Items.BEANSTALK_SEEDS);
            }
            else if (region == 0x14) {
                return inventory.contains(Items.BEANSTALK_SEEDS);
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
                    return canSwim(inventory);
                }
                else {
                    return canSwim(inventory) || inventory.contains(Items.JUMP_BOOTS);
                }
            }
            else if (region == 0xa) {
                return canSuperSwim(inventory);
            }
            else if (region == 0x12) {
                return inventory.contains(Items.PUMP) && inventory.contains(Items.JUMP_BOOTS);
            }
            else if (region == 0x15) {
                return inventory.contains(Items.GARLIC) && canLift(inventory) && canSwim(inventory);
            }
            else if (region == 0x16) {
                return inventory.contains(Items.GARLIC) && canSwim(inventory);
            }
            else if (region == 0x17) {
                return inventory.contains(Items.PUMP);
            }
            else if (region == 0x1d) {
                return canSwim(inventory);
            }
        }
        else if (level.equals("N5")) {
            if (region == 0x1) {
                if (location == 1) {
                    return inventory.contains(Items.GARLIC);
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
                return canLift(inventory) && canSwim(inventory)
                        && (canSuperSwim(inventory) || inventory.contains(Items.SPIKED_HELMET));
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
                    return inventory.contains(Items.GARLIC) && canGP(inventory) && canSwim(inventory);
                }
                else {
                    return inventory.contains(Items.GARLIC) && inventory.contains(Items.SPIKED_HELMET) && canGP(inventory) && canSwim(inventory);
                }
            }
            else if (region == 0x5) {
                return inventory.contains(Items.GARLIC) && inventory.contains(Items.SPIKED_HELMET) && canGP(inventory) && canSwim(inventory);
            }
            else if (region == 0x6) {
                return canSuperGP(inventory) && inventory.contains(Items.NIGHT_VISION_GOGGLES) && inventory.contains(Items.JUMP_BOOTS);
            }
            else if (region == 0x14) {
                return canSuperGP(inventory);
            }
            else if (region == 0x19) {
                return canSuperGP(inventory) && inventory.contains(Items.GARLIC);
            }
            else if (region == 0x1c) {
                return inventory.contains(Items.GARLIC) && canGP(inventory) && canSwim(inventory) && inventory.contains(Items.PURITY_STAFF);
            }
        }
        else if (level.equals("W1")) {
            if (region == 0x1) {
                if (location == 0) {
                    return !daytime || (inventory.contains(Items.GARLIC) && canSuperGP(inventory));
                }
                else {
                    if (daytime) {
                        return inventory.contains(Items.GARLIC) && canSuperGP(inventory);
                    }
                    else {
                        return inventory.contains(Items.GARLIC) || canGP(inventory);
                    }
                }
            }
            else if (region == 0x5) {
                return canSuperGP(inventory) && (inventory.contains(Items.JUMP_BOOTS) || canLift(inventory)) && (!daytime || inventory.contains(Items.GARLIC));
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
                    return inventory.contains(Items.SPIKED_HELMET) && canLift(inventory);
                }
                else if (location == 2) {
                    return canGP(inventory);
                }
                else {
                    return true;
                }
            }
            else if (region == 0xa) {
                return daytime || inventory.contains(Items.GARLIC);
            }
            else if (region == 0x14) {
                return !daytime || inventory.contains(Items.GARLIC);
            }
            else if (region == 0x18) {
                return canSuperGP(inventory)
                        && (canLift(inventory) || inventory.contains(Items.JUMP_BOOTS))
                        && (!daytime || inventory.contains(Items.GARLIC));
            }
        }
        else if (level.equals("W2")) {
            if (region == 0x1) {
                if (location == 1) {
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
                if (location == 0) {
                    return inventory.contains(Items.WHEELS);
                }
                else {
                    return inventory.contains(Items.WHEELS) && canSwim(inventory);
                }
            }
            else if (region == 0x14) {
                return true;
            }
        }
        else if (level.equals("W3")) {
            if (region == 0x1) {
                if (location == 0) {
                    return inventory.contains(Items.BEANSTALK_SEEDS);
                    // we'll add this back in for harder logic
                    //        || (canGP(inventory) && canLift(inventory) && inventory.contains(Items.JUMP_BOOTS));
                }
                else if (location == 1) {
                    return canGP(inventory);
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
                return inventory.contains(Items.PUMP) && canSwim(inventory);
            }
        }
        else if (level.equals("W4")) {
            if (region == 0x1) {
                return true;
            }
            else if (region == 0x5) {
                return inventory.contains(Items.JUMP_BOOTS) && canSuperGP(inventory) && canSuperLift(inventory);
            }
            else if (region == 0x7) {
                return canLift(inventory) && inventory.contains(Items.PROPELLOR);
            }
            else if (region == 0x9) {
                if (location == 0) {
                    return true;
                }
                else {
                    return inventory.contains(Items.SPIKED_HELMET) && canSuperGP(inventory);
                }
            }
            else if (region == 0x16) {
                return canSuperLift(inventory);
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
                return inventory.contains(Items.SPIKED_HELMET) || inventory.contains(Items.JUMP_BOOTS);
            }
            else if (region == 0x1c) {
                return canSuperLift(inventory) && canSuperGP(inventory);
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
                return true;
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
                return inventory.contains(Items.JUMP_BOOTS);
            }
            else if (region == 0x1B) {
                return inventory.contains(Items.RED_CHEMICAL) && inventory.contains(Items.BLUE_CHEMICAL);
            }
        }
        else if (level.equals("W6")) {
            if (region == 0x1) {
                if (location == 1) {
                    return inventory.contains(Items.RUST_SPRAY) && canGP(inventory);
                }
                else {
                    return true;
                }
            }
            else if (region == 0x4) {
                return inventory.contains(Items.FIRE_EXTINGUISHER) && inventory.contains(Items.JUMP_BOOTS);
            }
            else if (region == 0x8) {
                if (location == 2) {
                    return inventory.contains(Items.RUST_SPRAY) && inventory.contains(Items.JUMP_BOOTS);
                }
                else {
                    return inventory.contains(Items.RUST_SPRAY) && canLift(inventory);
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
                    return inventory.contains(Items.JUMP_BOOTS) || inventory.contains(Items.FLUTE);
                }
                else if (location == 2) {
                    return inventory.contains(Items.BEANSTALK_SEEDS);
                }
                else {
                    return true;
                }
            }
            else if (region == 0x9) {
                return inventory.contains(Items.JUMP_BOOTS) || inventory.contains(Items.FLUTE);
            }
            else if (region == 0x15) {
                return true;
            }
            else if (region == 0x17) {
                return inventory.contains(Items.BEANSTALK_SEEDS);
            }
            else if (region == 0x19) {
                return inventory.contains(Items.JUMP_BOOTS);
            }
            else if (region == 0x1a) {
                if (location == 0) {
                    return inventory.contains(Items.JUMP_BOOTS);
                }
                else {
                    return inventory.contains(Items.JUMP_BOOTS) && canLift(inventory);
                }
            }
        }
        else if (level.equals("S2")) {
            if (region == 0x1) {
                if (location == 0) {
                    return canLift(inventory);
                }
                else if (location == 2) {
                    return canGP(inventory) && canSwim(inventory);
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
                            && inventory.contains(Items.SPIKED_HELMET) && inventory.contains(Items.GARLIC);
                }
            }
            else if (region == 0xa) {
                if (location == 0) {
                    return canSwim(inventory) && canSuperGP(inventory);
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
                if (location == 0) {
                    return inventory.contains(Items.PURITY_STAFF) && canSwim(inventory)
                            && inventory.contains(Items.GARLIC) && canSuperGP(inventory);
                }
                else {
                    return inventory.contains(Items.PURITY_STAFF) && canSwim(inventory)
                            && inventory.contains(Items.GARLIC);
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
                    return inventory.contains(Items.WIRE_WIZARD) && inventory.contains(Items.JUMP_BOOTS);
                }
                else if (location == 2) {
                    return inventory.contains(Items.WIRE_WIZARD) && inventory.contains(Items.JUMP_BOOTS) && inventory.contains(Items.GARLIC);
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
                        && inventory.contains(Items.JUMP_BOOTS)
                        && canSuperLift(inventory);
            }
        }
        else if (level.equals("S4")) {
            if (region == 0x1) {
                return true;
            }
            else if (region == 0xa) {
                return inventory.contains(Items.STONE_FOOT) && canSuperSwim(inventory) && canSuperGP(inventory);
            }
            else if (region == 0x11) {
                return inventory.contains(Items.RUST_SPRAY) && canGP(inventory);
            }
            else if (region == 0x15) {
                return inventory.contains(Items.STONE_FOOT);
            }
            else if (region == 0x19) {
                return inventory.contains(Items.STONE_FOOT) && canSuperSwim(inventory) && canSuperGP(inventory);
            }
            else if (region == 0x1c) {
                return inventory.contains(Items.RUST_SPRAY) && canGP(inventory);
            }
        }
        else if (level.equals("S5")) {
            if (region == 0x1 || region == 0x3) {
                return inventory.contains(Items.RUST_SPRAY) && canLift(inventory)
                        && inventory.contains(Items.JUMP_BOOTS);
            }
            else if (region == 0x6) {
                return inventory.contains(Items.RUST_SPRAY) && canLift(inventory)
                        && inventory.contains(Items.JUMP_BOOTS) && canGP(inventory);
            }
            else if (region == 0x7) {
                if (location == 0) {
                    return inventory.contains(Items.JUMP_BOOTS);
                }
                else {
                    return canLift(inventory);
                }
            }
            else if (region == 0xa) {
                return inventory.contains(Items.JUMP_BOOTS) && canLift(inventory);
            }
            else if (region == 0xe) {
                return inventory.contains(Items.SPIKED_HELMET);
            }
            else if (region == 0x15) {
                return inventory.contains(Items.JUMP_BOOTS);
            }
            else if (region == 0x18) {
                return inventory.contains(Items.DETONATOR);
            }
        }
        else if (level.equals("S6")) {
            if (region == 0x1 || region == 0x3) {
                return true;
            }
            else if (!inventory.contains(Items.JUMP_BOOTS)) {
                return false;
            }
            else if (region == 0x5) {
                if (location == 0) {
                    return true;
                }
                else {
                    return inventory.contains(Items.SCISSORS);
                }
            }
            else if (region == 0x1c) {
                if (location == 0) {
                    return true;
                }
                else {
                    return inventory.contains(Items.SPIKED_HELMET) && canLift(inventory);
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
                if (location == 1) {
                    return inventory.contains(Items.SPIKED_HELMET);
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
                    return inventory.contains(Items.JUMP_BOOTS);
                }
                else {
                    return daytime || canSuperSwim(inventory);
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
                return daytime || canSuperSwim(inventory);
            }
        }
        else if (level.equals("E3")) {
            if (region == 0x1) {
                if (location == 1) {
                    return true;
                }
                else {
                    return canLift(inventory);
                }
            }
            else if (region == 0x4 || region == 0x9) {
                return canSuperLift(inventory);
            }
            else if (region == 0x7) {
                if (location < 2) {
                    return canLift(inventory) && inventory.contains(Items.BRICK);
                }
                else {
                    return canLift(inventory) && inventory.contains(Items.BRICK) && canGP(inventory);
                }
            }
            else if (region == 0x1a) {
                if (!daytime && !canSuperLift(inventory)) {
                    return false;
                }
                else if (location == 0) {
                    return inventory.contains(Items.JUMP_BOOTS) && canLift(inventory);
                }
                else {
                    return canLift(inventory);
                }
            }
        }
        else if (level.equals("E4")) {
            if (region == 0x1) {
                if (location == 1) {
                    return daytime || inventory.contains(Items.JUMP_BOOTS);
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
                return inventory.contains(Items.GARLIC) && (inventory.contains(Items.SPIKED_HELMET) || inventory.contains(Items.JUMP_BOOTS));
            }
            else if (region == 0x14) {
                return inventory.contains(Items.GARLIC) && canLift(inventory);
            }
            else if (region == 0x1B) {
                if (location == 1) {
                    return daytime || inventory.contains(Items.JUMP_BOOTS);
                }
                else {
                    return (daytime && canSuperLift(inventory)) || (!daytime && inventory.contains(Items.JUMP_BOOTS) && canLift(inventory));
                }
            }
        }
        else if (level.equals("E5")) {
            if (region == 0x3) {
                return inventory.contains(Items.BLUE_KEY_CARD)
                        && inventory.contains(Items.RED_KEY_CARD)
                        && canLift(inventory) && inventory.contains(Items.SPIKED_HELMET);
            }
            else if (region == 0x6 || region == 0x7) {
                return canLift(inventory)
                        && ((inventory.contains(Items.BLUE_KEY_CARD) && inventory.contains(Items.RED_KEY_CARD))
                            || inventory.contains(Items.WARP_REMOTE));
            }
            else if (region == 0x9) {
                return inventory.contains(Items.WARP_REMOTE);
            }
            else if (region == 0xa || region == 0xc) {
                return inventory.contains(Items.BLUE_KEY_CARD)
                        && inventory.contains(Items.RED_KEY_CARD)
                        && canLift(inventory);
            }
            else if (region == 0x18) {
                return canLift(inventory);
            }
        }
        else if (level.equals("E6")) {
            if (region == 0x1) {
                if (location == 0) {
                    return true;
                }
                else if (location == 1) {
                    return canLift(inventory) && canSuperGP(inventory);
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
                    return canLift(inventory) && canGP(inventory) && inventory.contains(Items.FIRE_EXTINGUISHER);
                }
                else {
                    return canLift(inventory) && inventory.contains(Items.FIRE_EXTINGUISHER);
                }
            }
            else if (region == 0x11) {
                return canLift(inventory) && inventory.contains(Items.FIRE_EXTINGUISHER);
            }
            else if (region == 0x14) {
                return canLift(inventory);
            }
            else if (region == 0x18) {
                if (location == 0) {
                    return inventory.contains(Items.PICKAXE) && inventory.contains(Items.JUMP_BOOTS);
                }
                else {
                    return inventory.contains(Items.PICKAXE) && canLift(inventory);
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
                    return inventory.contains(Items.VALVE) && canLift(inventory) && inventory.contains(Items.JUMP_BOOTS);
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
                    return inventory.contains(Items.DEMON_BLOOD);
                }
            }
            else if (region == 0x18) {
                return inventory.contains(Items.VALVE) && inventory.contains(Items.JUMP_BOOTS) && canSuperLift(inventory);
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
