import com.google.gson.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Patcher {

    /**
     * Create a patched, randomized ROM and write it to disk.
     *
     * @param vanillaPathStr  path to a vanilla ROM
     * @param treasures       ordered list of randomized treasures
     * @param seed            encoded String representation of the seed used to generate treasures
     * @param playthrough     byte array representing order that treasures should be collected in, or null if vanilla
     * @param version         String representing current app version
     * @throws IOException    if something goes wrong reading from or writing to a ROM
     */
    public static void patch(String vanillaPathStr, int[] treasures, String seed, byte[] playthrough, Integer[] music, Integer[] worldMap, String version) throws IOException {
        Path vanillaPath = new File(vanillaPathStr).toPath();
        byte[] romBytes = Files.readAllBytes(vanillaPath);
        romBytes = applyPatch(romBytes, "baseDiff.json");
        romBytes = treasuresPatch(romBytes, treasures, worldMap);
        if (playthrough != null) {
            romBytes = hintPatch(romBytes, playthrough);
        }
        if (music != null) {
            romBytes = musicPatch(romBytes, music);
        }
        if (worldMap != null) {
            romBytes = mapPatch(romBytes, worldMap);
        }
        savePatchedFile(romBytes, seed, version);
    }

    /**
     * Read a ROM file and modify it in memory with the randomizer patch.
     *
     *
     * @return byte array representing a patched ROM
     * @throws IOException
     */
    private static byte[] applyPatch(byte[] romBytes, String patchName) throws IOException {
            Gson gson = new GsonBuilder().create();
            ClassLoader classLoader = Patcher.class.getClassLoader();
            InputStream baseDiff = Patcher.class.getResourceAsStream(patchName);
            BufferedReader br = new BufferedReader(new InputStreamReader(baseDiff));
            String diffStr = br.readLine();
            br.close();
            JsonElement jelem = gson.fromJson(diffStr, JsonElement.class);
            JsonObject jobj = jelem.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : jobj.entrySet()) {
                int index = Integer.parseInt(entry.getKey());
                JsonArray array = entry.getValue().getAsJsonArray();
                for (int i = 0; i < array.size(); i++) {
                    romBytes[index] = array.get(i).getAsByte();
                    index++;
                }
            }
            return romBytes;
    }

    /**
     * Modify the treasure table in a given ROM.
     *
     * @param romBytes  byte array representing a WL3 ROM
     * @param treasures Randomized list of treasures
     * @return byte array representing the new ROM
     */
    private static byte[] treasuresPatch(byte[] romBytes, int[] treasures, Integer[] worldMap) {
        int idx = 0x198f;
        for (int i = 0; i < treasures.length; i++) {
            int treasureIdx = i;

            if (worldMap != null) {
                // if we're using map shuffle, treasure won't be in their proper spots in the treasure table
                // so we need to scramble the table to match the scrambled map
                treasureIdx = (worldMap[i/4] * 4) + (i%4);
            }

            romBytes[idx+i] = (byte)treasures[treasureIdx];
        }
        return romBytes;
    }

    private static byte[] hintPatch(byte[] romBytes, byte[] playthrough) throws IOException {
        int idx = 0x82cc0;
        romBytes[idx] = (byte)0x00;
        for (int i = 0; i < playthrough.length; i++) {
            romBytes[idx+1+i] = playthrough[i];
        }
        romBytes[idx+1+playthrough.length] = (byte)0xeb;
        romBytes = applyPatch(romBytes, "hintsPatch.json");
        return romBytes;
    }

    private static byte[] musicPatch(byte[] romBytes, Integer[] music) {
        int idx = 0x3fe00;
        for (int i = 0; i < music.length; i++) {
            // first 11 tracks are for status effects, should be applied only once
            // remaining tracks are level themes and should be applied four times each
            // (one for each level copy for a given time of day)
            for (int numCopies = (i < 11 ? 1 : 4); numCopies > 0; numCopies--) {
                while (romBytes[idx] == (byte)0xFF || romBytes[idx] == (byte)0x00) {
                    idx++;
                }
                romBytes[idx] = music[i].byteValue();
                idx++;
            }
        }
        return romBytes;
    }

    private static byte[] mapPatch(byte[] romBytes, Integer[] worldMap) throws IOException {
        romBytes = applyPatch(romBytes, "mapShufflePatch.json");
        romBytes = scrambleLevels(romBytes,worldMap, 16, 0xc00be);
        romBytes = scrambleLevels(romBytes,worldMap, 16, 0xc0319);
        romBytes = scrambleLevels(romBytes,worldMap, 8, 0x4eba);
        int[] gfxSkips = {6, 13, 14, 21, 22};
        romBytes = scrambleLevels(romBytes,worldMap, 0x200, 0x94200, gfxSkips);
        romBytes = scrambleLevels(romBytes,worldMap, 0x200, 0x90200, gfxSkips);
        // the warp data for all North and West levels is stored in bank 0x30, while the warp data for all
        // South and East levels is stored in 0x31. For the patch to work we need to supply a list of which
        // map nodes have North and West levels, so the game knows which bank to load from.
        // S1 The Grasslands is a special case, as its Day levels exist on bank 0x30.
        int idx = 0x3d6c;
        int offset = 0;
        int grasslandsIdx = 0;
        for (int i = 0; i < worldMap.length; i++) {
            if (worldMap[i] > 12) {
                continue;
            }
            else if (worldMap[i] == 12) {
                grasslandsIdx = i;
                continue;
            }
            romBytes[idx+offset] = (byte)i;
            offset++;
        }
        romBytes[idx+offset] = (byte)0xff;
        romBytes[idx+offset+1] = (byte)grasslandsIdx;
        // similarly, we need to update level #s in the transition level swap table, and then sort them by level #
        idx = 0x3cd4;
        List<byte[]> levelTransitionSwaps = new Vector<>();
        while (romBytes[idx] != (byte)0xff) {
            int levelIdx = romBytes[idx] & 0xFF;
            for (int i = 0; i < worldMap.length; i++) {
                if (worldMap[i].byteValue() << 3 == levelIdx) {
//                    romBytes[idx] = (byte)(i<<3);
                    byte[] swap = {(byte)(i<<3), romBytes[idx+1], romBytes[idx+2], romBytes[idx+3], romBytes[idx+4]};
                    levelTransitionSwaps.add(swap);
                }
            }
            idx += 0x05;
        }
        Collections.sort(levelTransitionSwaps, new Comparator<byte[]>() {
            @Override
            public int compare(byte[] o1, byte[] o2) {
                return (o1[0] & 0xFF) - (o2[0] & 0xFF);
            }
        });
        idx = 0x3cd4;
        for (byte[] swap : levelTransitionSwaps) {
            for (byte i : swap) {
                romBytes[idx] = i;
                idx++;
            }
        }

        return romBytes;
    }

    private static byte[] scrambleLevels(byte[] romBytes, Integer[] worldMap, int entrySize, int idx) {
        return scrambleLevels(romBytes,worldMap,entrySize,idx,new int[0]);
    }

    private static byte[] scrambleLevels(byte[] romBytes, Integer[] worldMap, int entrySize, int idx, int[] skips) {
        int offset = 0;
        byte[][] levelData = new byte[25][entrySize];
        for (int i = 0; i < 25*entrySize; i++) {
            if (i % entrySize == 0) {
                for (int skip : skips) {
                    if ((i + offset) / entrySize == skip) {
                        offset += entrySize;
                    }
                }
            }
            int adjustIdx = i + offset;
            levelData[i/entrySize][i%entrySize] = romBytes[idx+adjustIdx];
        }

        offset = 0;

        for (int i = 0; i < 25*entrySize; i++) {
            if (i % entrySize == 0) {
                for (int skip : skips) {
                    if ((i + offset) / entrySize == skip) {
                        offset += entrySize;
                    }
                }
            }
            int adjustIdx = i + offset;
            romBytes[idx+adjustIdx] = levelData[worldMap[i/entrySize]][i%entrySize];
        }
        return romBytes;
    }

    /**
     * Write the patched, randomized ROM to disk.
     *
     * @param romBytes  byte array representing the final ROM
     * @param seed      encoded String representation of the seed used to randomize the ROM
     * @param version         String representing current app version
     * @throws IOException
     */
    private static void savePatchedFile(byte[] romBytes, String seed, String version) throws IOException {
        String filename = "WL3-randomizer-" + version + "-" + seed + ".gbc";
        File randoFile = new File(filename);
        Files.write(randoFile.toPath(),romBytes);
    }
}
