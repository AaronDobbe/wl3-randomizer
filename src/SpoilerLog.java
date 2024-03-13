import keyshuffle.KeyLocation;
import keyshuffle.Level;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class SpoilerLog {
    private static final String quads = "NWSEE";

    public static void writeSpoiler(List<Integer> startingInventory, int[] items, Level[] keyLocations,
                                    Integer[] worldMap, String seed, byte[] playthrough, Map<String,String> options,
                                    String version) throws IOException {
        FileWriter spoiler = new FileWriter("wl3spoiler-"+version+"-"+seed+".txt");
        String[] colors = {"Silver  ", "Red     ", "Green   ", "Blue    "};

        spoiler.write("Game options:\n-------------\n");
        List<String> optionsList = new ArrayList<>();
        for (Map.Entry<String, String> option : options.entrySet()) {
            optionsList.add(option.getKey() + ": " + option.getValue() + "\n");
        }
        Collections.sort(optionsList);
        for (String option : optionsList) {
            spoiler.write(option);
        }
        spoiler.write("\n");

        if (startingInventory != null && startingInventory.size() > 0) {
            spoiler.write("Starting inventory:\n");
            for (Integer item : startingInventory) {
                spoiler.write(Items.ITEM_NAMES[item] + "\n");
            }
            spoiler.write("\n\n");
        }

        List<Integer> itemToLocation = Arrays.asList(new Integer[101]);
        spoiler.write("All Treasures and Key Locations:\n-------------------\n");
        for (int i = 0; i < items.length; i++) {
            spoiler.write(intToLevelName(i) + " " + colors[i%4] + ": ");
            spoiler.write(Items.ITEM_NAMES[items[i]]);
            if (keyLocations != null) {
                Level level = keyLocations[i / 4];
                spoiler.write("; Key: " + level.getLocation(i % 4).getName());
            }
            spoiler.write("\n");
            itemToLocation.set(items[i],i);
        }

        spoiler.write("\n");

        if (worldMap != null) {
            spoiler.write("World Map Locations -> Levels:\n-------------------\n");
            for (int i = 0; i < worldMap.length; i++) {
                spoiler.write(intToLevelName(i*4) + " -> " + intToLevelName(worldMap[i]*4) + "\n");
            }
            spoiler.write("\n");
        }

        spoiler.write("Playthrough:\n-------------------\n");
        int step = 1;
        for (int i = 0; i < playthrough.length; i++) {
            int item = (int)(playthrough[i] & 0xff);
            if (item == 0) continue;
            int location = itemToLocation.get(item);
            spoiler.write(String.format("%3d", step) + ". " + intToLevelName(location) + " " + colors[location%4] + ": ");
            step++;
            spoiler.write(Items.ITEM_NAMES[item] + "\n");
        }
        spoiler.close();
    }

    private static String intToLevelName(Integer location) {
        return "" + quads.charAt(location/24) + (location >= 96 ? 7 : ((location % 24)/4 + 1));
    }
}
