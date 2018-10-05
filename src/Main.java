import javax.swing.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class Main {
    private static String[] locationNames;
    private static int[] finalTreasures;
    private static int fails = 0;

    private static String vanillaFileLocation;

    private static GUI gui;

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
    public static void generateGame(String userSeed) {
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
        // keep unshuffled lists in case we need re-randomization
        List<Integer> pureInventory = new ArrayList<>(inventory);
        List<Integer> pureLocations = new ArrayList<>(locations);

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
        int numFails=0;
        // attempt to place treasures
        while (!placeItemsLeft(new Vector<>(), inventory, locations, treasures)) {
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
            Collections.shuffle(inventory, rng);
            Collections.shuffle(locations, rng);
            fails = 0;
        }

        // items have been placed, now shuffle list of junk and use it to fill in the remaining locations
        Collections.shuffle(junkList,rng);
        for (Integer junkItem : junkList) {
            for (int i = 0; i < 100; i++) {
                if (finalTreasures[i] == 0) {
                    finalTreasures[i] = junkItem;
                    break;
                }
            }
        }

        // patch vanilla ROM file and create randomized ROM
        try {
            Patcher.patch(vanillaFileLocation,finalTreasures,encodeSeed(seed));
        } catch (IOException e) {
            gui.log("Error occurred while generating randomized game: " + e.getMessage());
            return;
        }

        gui.log("Generated randomized game with seed " + encodeSeed(seed));
        gui.log("Randomized ROM has been saved as WL3-randomizer-" + encodeSeed(seed) + ".gbc");
    }

    /**
     * Initialize and show the GUI.
     */
    private static void createGUI() {
        JFrame frame = new JFrame("Wario Land 3 Randomizer v0.8");
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
     * Place an item from rightInventory at the end of the sequence, then call placeItemsLeft if there are items left to place.
     *
     * @param leftInventory  Items already placed at the beginning of the sequence.
     * @param rightInventory Items yet to be placed anywhere.
     * @param locations      Locations without treasure.
     * @param treasures      All placed treasures, ordered by location (using null to represent still-empty locations)
     * @return true if all items were placed successfully, false otherwise
     */
    private static boolean placeItemsRight(List<Integer> leftInventory, List<Integer> rightInventory, List<Integer> locations, List<Integer> treasures) {
        List<Integer> mergedInventory = new Vector<>(rightInventory);
        mergedInventory.addAll(leftInventory);
        int curLocations = 0;
        for (Integer checkLocation : locations) {
            if (treasures.get(checkLocation) == null && canAccess(locationNames[checkLocation], mergedInventory)) {
                curLocations++;
            }
        }
        for (Integer location : locations) {
            if (!canAccess(locationNames[location], mergedInventory)) {
                continue;
            }

            for (Integer item : rightInventory) {
                List<Integer> nextRightInventory = new Vector<>(rightInventory);
                nextRightInventory.remove(item);
                List<Integer> nextInventory = new Vector<>(nextRightInventory);
                nextInventory.addAll(leftInventory);

                if (location == 0 && nextInventory.size() > 0) {
                    continue;
                }
                if (!canAccess(locationNames[location], nextInventory)) {
                    continue;
                }
                List<Integer> nextTreasures = new Vector<>(treasures);
                List<Integer> nextLocations = new Vector<>();
                nextTreasures.set(location, item);
                int locationsLeft = 0;
                for (Integer checkLocation : locations) {
                    if (!checkLocation.equals(location) && treasures.get(checkLocation) == null && canAccess(locationNames[checkLocation], nextInventory)) {
                        locationsLeft++;
                        nextLocations.add(checkLocation);
                    }
                }

                if (locationsLeft < nextRightInventory.size()) {
                    fails++;
                    continue;
                }
                if (nextRightInventory.size() == 0) {
                    for (int i = 0; i < nextTreasures.size(); i++) {
                        if (nextTreasures.get(i) != null) {
                            finalTreasures[i] = nextTreasures.get(i);
                        }
                    }
                    return true;
                }
                else if (placeItemsLeft(leftInventory, nextRightInventory, nextLocations, nextTreasures)) {
                    return true;
                }
                else if (fails > 40000) {
                    return false;
                }

            }
        }
        return false;
    }

    /**
     * Place an item from rightInventory at the beginning of the sequence (moving it to leftInventory), then call placeItemsRight if there are items left to place.
     *
     * @param leftInventory  Items already placed at the beginning of the sequence.
     * @param rightInventory Items yet to be placed anywhere.
     * @param locations      Locations without treasure.
     * @param treasures      All placed treasures, ordered by location (using null to represent still-empty locations)
     * @return true if all items were placed successfully, false otherwise
     */
    private static boolean placeItemsLeft(List<Integer> leftInventory, List<Integer> rightInventory, List<Integer> locations, List<Integer> treasures) {
        boolean forwardGPStart = treasures.get(0) != null && (treasures.get(0).equals(Items.BLUE_OVERALLS) || treasures.get(0).equals(Items.RED_OVERALLS));
        for (Integer location : locations) {
            if (!canAccess(locationNames[location], leftInventory, forwardGPStart)) {
                continue;
            }
            for (Integer item : rightInventory) {
                List<Integer> nextLeftInventory = new Vector<>(leftInventory);
                nextLeftInventory.add(item);
                List<Integer> nextRightInventory = new Vector<>(rightInventory);
                nextRightInventory.remove(item);
                List<Integer> nextTreasures = new Vector<>(treasures);
                List<Integer> nextLocations = new Vector<>(locations);
                nextTreasures.set(location, item);
                nextLocations.remove(location);
                forwardGPStart = nextTreasures.get(0) != null && (nextTreasures.get(0).equals(Items.BLUE_OVERALLS) || nextTreasures.get(0).equals(Items.RED_OVERALLS));
                int locationsLeft = 0;
                for (Integer checkLocation : locations) {
                    if (!checkLocation.equals(location) && treasures.get(checkLocation) == null && canAccess(locationNames[checkLocation], nextLeftInventory, forwardGPStart)) {
                        locationsLeft++;
                    }
                }
                if (locationsLeft == 0) {
                    continue;
                }
                if (nextRightInventory.size() == 0) {
                    for (int i = 0; i < nextTreasures.size(); i++) {
                        if (nextTreasures.get(i) != null) {
                            finalTreasures[i] = nextTreasures.get(i);
                        }
                    }
                    return true;
                }
                else if (placeItemsRight(nextLeftInventory, nextRightInventory, nextLocations, nextTreasures)) {
                    return true;
                }
                else if (fails > 40000) {
                    return false;
                }
            }
        }
        return false;
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
     * Check if the provided inventory allows Wario to access the given location.
     *
     * @param location A string representing a location, in one of the following formats:
     *                  - a level code, e.g. "E2", will return whether or not Wario can reach that level on the world map
     *                  - a two-character diagonal direction, e.g. "NW", will return whether or not Wario can warp to
     *                    that border using NEXT MAP
     *                  - a full three-character location code, e.g. "N2R", will return whether or not Wario can collect
     *                    a treasure at that location
     */
    private static boolean canAccess(String location, List<Integer> inventory) {
        return canAccess(location, inventory, false);
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
     * @param forwardGPStart true if this is being called to place an item at the beginning of the sequence, and
     *                        there is an overalls treasure at N1S (affects accessibility of N1R)
     */
    private static boolean canAccess(String location, List<Integer> inventory, boolean forwardGPStart) {
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
        else if (location.equals("N1")) {
            return true;
        }
        else if (location.equals("N2")) {
            return inventory.contains(Items.AXE) || inventory.contains(Items.TORCH);
        }
        else if (location.equals("N3")) {
            return inventory.contains(Items.AXE) || (inventory.contains(Items.KEYSTONE_L) && inventory.contains(Items.KEYSTONE_R));
        }
        else if (location.equals("N4")) {
            return inventory.contains(Items.MUSIC_BOX_2) && canAccess("N3", inventory);
        }
        else if (location.equals("N5")) {
            return canAccess("N4", inventory);
        }
        else if (location.equals("N6")) {
            return inventory.contains(Items.GARLIC) && canAccess("N5", inventory);
        }
        else if (location.equals("W5")) {
            return inventory.contains(Items.MUSIC_BOX_4) && canAccess("W2", inventory);
        }
        else if (location.equals("W6")) {
            return inventory.contains(Items.RED_ARTIFACT) && inventory.contains(Items.GREEN_ARTIFACT) && inventory.contains(Items.BLUE_ARTIFACT)
                    && canAccess("W3", inventory);
        }
        else if (location.equals("S4")) {
            return inventory.contains(Items.ANGER_HALBERD) && inventory.contains(Items.ANGER_SPELL)
                    && canAccess("S2", inventory);
        }
        else if (location.equals("S5")) {
            return inventory.contains(Items.MUSIC_BOX_3)
                    && canAccess("S2", inventory);
        }
        else if (location.equals("S6")) {
            return inventory.contains(Items.SKY_KEY)
                    && canAccess("S3", inventory);
        }
        else if (location.equals("E3")) {
            return inventory.contains(Items.LAMP) && inventory.contains(Items.FLAME)
                    && canAccess("E1", inventory);
        }
        else if (location.equals("E5")) {
            return inventory.contains(Items.WARP_COMPACT)
                    && canAccess("E7", inventory);
        }
        else if (location.equals("E6")) {
            return inventory.contains(Items.CRATER_MAP)
                    && canAccess("E3", inventory);
        }
        else if (location.length() == 2) {
            return canAccessFromWest(location, inventory) || canAccessFromEast(location, inventory);
        }
        else if (location.equals("N1S")) {
            return canAccess("N1", inventory);
        }
        else if (location.equals("N1R")) {
            return canAccess("N1", inventory)
                    && canGP(inventory)
                    && (inventory.contains(Items.AXE) || forwardGPStart);
        }
        else if (location.equals("N1G")) {
            return canAccess("N1", inventory)
                    && inventory.contains(Items.JUMP_BOOTS)
                    && inventory.contains(Items.WIND)
                    && inventory.contains(Items.WIND_BAG)
                    && inventory.contains(Items.AXE);
        }
        else if (location.equals("N1B")) {
            return canAccess("N1", inventory)
                    && inventory.contains(Items.POWDER)
                    && inventory.contains(Items.JUMP_BOOTS)
                    && canLift(inventory)
                    && canGP(inventory)
                    && inventory.contains(Items.AXE);
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
            return canAccess("N4", inventory)
                    && (canSwim(inventory) || inventory.contains(Items.JUMP_BOOTS));
        }
        else if (location.equals("N4R")) {
            return canAccess("N4", inventory)
                    && inventory.contains(Items.GARLIC)
                    && canSwim(inventory);
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
                    && canSwim(inventory)
                    && (inventory.contains(Items.SPIKED_HELMET) || canSuperSwim(inventory));
        }
        else if (location.equals("N5G")) {
            return canAccess("N5", inventory)
                    && inventory.contains(Items.WIRE_WIZARD)
                    && inventory.contains(Items.GARLIC);
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
                    && inventory.contains(Items.NIGHT_VISION_GOGGLES)
                    && inventory.contains(Items.JUMP_BOOTS);
        }
        else if (location.equals("W1S")) {
            return canAccess("W1", inventory);
        }
        else if (location.equals("W1R")) {
            return canAccess("W1", inventory);
        }
        else if (location.equals("W1G")) {
            return canAccess("W1", inventory)
                    && inventory.contains(Items.SPIKED_HELMET)
                    && canGP(inventory);
        }
        else if (location.equals("W1B")) {
            return canAccess("W1", inventory)
                    && canSuperGP(inventory)
                    && (canLift(inventory) || inventory.contains(Items.JUMP_BOOTS));
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
                    && inventory.contains(Items.FLUTE)
                    && canSwim(inventory);
        }
        else if (location.equals("W2B")) {
            return canAccess("W2", inventory)
                    && inventory.contains(Items.STONE_FOOT)
                    && canSwim(inventory)
                    && (inventory.contains(Items.SPIKED_HELMET) || canSuperSwim(inventory));
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
                    && inventory.contains(Items.SPIKED_HELMET);
        }
        else if (location.equals("W4G")) {
            return canAccess("W4", inventory)
                    && inventory.contains(Items.JUMP_BOOTS)
                    && canSuperLift(inventory)
                    && canSuperGP(inventory);
        }
        else if (location.equals("W4B")) {
            return canAccess("W4", inventory)
                    && inventory.contains(Items.PROPELLOR)
                    && canLift(inventory);
        }
        else if (location.equals("W5S")) {
            return canAccess("W5", inventory)
                    && inventory.contains(Items.JUMP_BOOTS)
                    && canSwim(inventory);
        }
        else if (location.equals("W5R")) {
            return canAccess("W5", inventory)
                    && canSuperSwim(inventory);
        }
        else if (location.equals("W5G")) {
            return canAccess("W5", inventory)
                    && inventory.contains(Items.GROWTH_SEED)
                    && canSwim(inventory)
                    && canLift(inventory);
        }
        else if (location.equals("W5B")) {
            return canAccess("W5", inventory)
                    && inventory.contains(Items.BLUE_CHEMICAL)
                    && inventory.contains(Items.RED_CHEMICAL)
                    && canSwim(inventory)
                    && canLift(inventory);
        }
        else if (location.equals("W6S")) {
            return canAccess("W6", inventory)
                    && canGP(inventory);
        }
        else if (location.equals("W6R")) {
            return canAccess("W6", inventory)
                    && canSuperGP(inventory)
                    && canLift(inventory);
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
                    && canLift(inventory)
                    && canSwim(inventory)
                    && canGP(inventory);
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
                    && inventory.contains(Items.WIRE_WIZARD)
                    && inventory.contains(Items.JUMP_BOOTS);
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
            return canAccess("S5", inventory)
                    && canLift(inventory);
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
                    && inventory.contains(Items.STONE_FOOT)
                    && canLift(inventory);
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
                    && (canSuperSwim(inventory) || (
                        inventory.contains(Items.SUN_FRAGMENT_L) && inventory.contains(Items.SUN_FRAGMENT_R)
                    ));
        }
        else if (location.equals("E3S")) {
            return canAccess("E3", inventory)
                    && canLift(inventory)
                    && canGP(inventory);
        }
        else if (location.equals("E3R")) {
            return canAccess("E3", inventory)
                    && (canSuperLift(inventory) || (canLift(inventory) && inventory.contains(Items.SUN_FRAGMENT_L) && inventory.contains(Items.SUN_FRAGMENT_R)))
                    && (canSuperGP(inventory) || inventory.contains(Items.JUMP_BOOTS));
        }
        else if (location.equals("E3G")) {
            return canAccess("E3", inventory)
                    && canSuperLift(inventory);
        }
        else if (location.equals("E3B")) {
            return canAccess("E3", inventory)
                    && inventory.contains(Items.BRICK)
                    && canLift(inventory)
                    && canGP(inventory)
                    && (inventory.contains(Items.JUMP_BOOTS) || (inventory.contains(Items.SUN_FRAGMENT_R) && inventory.contains(Items.SUN_FRAGMENT_L)));
        }
        else if (location.equals("E4S")) {
            return canAccess("E4", inventory);
        }
        else if (location.equals("E4R")) {
            return canAccess("E4", inventory)
                    && inventory.contains(Items.GARLIC)
                    && canLift(inventory);
        }
        else if (location.equals("E4G")) {
            return canAccess("E4", inventory)
                    && ((inventory.contains(Items.SUN_FRAGMENT_L)
                    && inventory.contains(Items.SUN_FRAGMENT_R))
                    || (inventory.contains(Items.JUMP_BOOTS)));
        }
        else if (location.equals("E4B")) {
            return canAccess("E4", inventory)
                    && inventory.contains(Items.DETONATOR)
                    && inventory.contains(Items.JUMP_BOOTS);
        }
        else if (location.equals("E5S")) {
            return canAccess("E5", inventory)
                    && canLift(inventory);
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
                    && canLift(inventory)
                    && canSuperGP(inventory);
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
                    && inventory.contains(Items.JUMP_BOOTS)
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
}
