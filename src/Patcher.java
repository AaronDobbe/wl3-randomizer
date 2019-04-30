import com.google.gson.*;

import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

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
    public static void patch(String vanillaPathStr, int[] treasures, String seed, byte[] playthrough, Integer[] music, Integer[] worldMap, int[] paletteSwitches, int[] objColors, String version) throws IOException {
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
        if (paletteSwitches != null) {
            romBytes = shuffleBGPalettes(romBytes,paletteSwitches);
        }
        if (objColors != null) {
            romBytes = shuffleObjPalettes(romBytes, objColors);
        }
        savePatchedFile(romBytes, seed, version);
    }

    /**
     * Given a byte array representing a ROM, apply the given patch file.
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

    /**
     * Modifies the given ROM's hint sequence.
     *
     * @param playthrough Ordered list of treasures to hint at
     * @throws IOException
     */
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

    /**
     * Modifies the given ROM's game music.
     *
     * @param music List of music IDs to use.
     */
    private static byte[] musicPatch(byte[] romBytes, Integer[] music) {
        int idx = 0x3fe00;
        for (int i = 0; i < music.length; i++) {
            if (i < 61) {
                // first 11 tracks are for status effects, should be applied only once
                // remaining tracks are level themes and should be applied four times each
                // (one for each level copy for a given time of day)
                for (int numCopies = (i < 11 ? 1 : 4); numCopies > 0; numCopies--) {
                    while (romBytes[idx] == (byte) 0xFF || romBytes[idx] == (byte) 0x00) {
                        idx++;
                    }
                    romBytes[idx] = music[i].byteValue();
                    idx++;
                }
            }
            else {
                int[] otherAddrs = {0x168a, 0x168c, 0x168e, 0x3ba5, 0x3bb2, 0x448e, 0x4cf0d, 0x9a3df, 0xace55, 0xae628, 0xaf7f5, 0xdb381, 0xdc060, /*0x1600f4,*/ 0x1c80b4, 0x1c89e4, 0x1e01b7, 0x1e01e3, 0x1f00a6, 0x1f802a};
                romBytes[otherAddrs[i-61]] = music[i].byteValue();
            }
        }
        romBytes[0x44f5] = romBytes[0x448e]; // titlescreen music loaded twice in quick succession from different places (volume related??)
        return romBytes;
    }

    /**
     * Applies map shuffle to the given ROM.
     *
     * @param worldMap List of level IDs, in the order they should appear on the map.
     * @return
     * @throws IOException
     */
    private static byte[] mapPatch(byte[] romBytes, Integer[] worldMap) throws IOException {
        // logic update patch, allowing for levels from the first half of the game to appear in the second and vice versa
        romBytes = applyPatch(romBytes, "mapShufflePatch.json");
        // reorder level tile/object pointer table
        romBytes = scrambleLevels(romBytes,worldMap, 16, 0xc00be);
        // reorder level warp pointer table
        romBytes = scrambleLevels(romBytes,worldMap, 16, 0xc0319);
        // reorder level entry table
        romBytes = scrambleLevels(romBytes,worldMap, 8, 0x4eba);
        // reassign nameplates on world map in both english and japanese
        int[] gfxSkips = {6, 13, 14, 21, 22};
        romBytes = scrambleLevels(romBytes,worldMap, 0x200, 0x94200, gfxSkips);
        romBytes = scrambleLevels(romBytes,worldMap, 0x200, 0x90200, gfxSkips);
        // reassign text level names (for temple hints) in both english and japanese
        romBytes = scrambleHintText(romBytes,worldMap,0xb211a,0x74000);
        romBytes[0xacc4b] = (byte)0x00;
        romBytes[0xacc4c] = (byte)0x40;
        romBytes[0xacc4e] = (byte)0x1D;
        romBytes = scrambleHintText(romBytes,worldMap,0xb1fd7, 0x74220);
        romBytes[0xacc46] = (byte)0x20;
        romBytes[0xacc47] = (byte)0x42;

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

    /**
     * Reorder a section of rom (typically a table) to match the reordered world map.
     *
     * @param worldMap  list of level IDs in the order they appear in-game
     * @param entrySize size of each block of data to move
     * @param idx       index of the first byte of the first block to move
     */
    private static byte[] scrambleLevels(byte[] romBytes, Integer[] worldMap, int entrySize, int idx) {
        return scrambleLevels(romBytes,worldMap,entrySize,idx,new int[0]);
    }

    /**
     * Reorder a section of rom (typically a table) to match the reordered world map.
     *
     * @param worldMap  list of level IDs in the order they appear in-game
     * @param entrySize size of each block of data to move
     * @param idx       index of the first byte of the first block to move
     * @param skips     a list of indexes to skip; blocks with these indexes will be passed over as if they didn't exist.
     */
    private static byte[] scrambleLevels(byte[] romBytes, Integer[] worldMap, int entrySize, int idx, int[] skips) {
        // extract each block to move
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

        // write the blocks back into memory in the correct order
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
     * Reassigns text level names to their correct levels.
     *
     * @param worldMap List of level IDs, in the order they appear on the map
     * @param idx      Index of first byte of first string to reorder
     */
    private static byte[] scrambleHintText(byte[] romBytes, Integer[] worldMap, int idx) {
        return scrambleHintText(romBytes,worldMap,idx,idx);
    }

    /**
     * Reassigns text level names to their correct levels.
     *
     * @param worldMap List of level IDs, in the order they appear on the map
     * @param idx      Index of first byte of first string to reorder
     * @param outIdx   Index of where to rewrite level names
     */
    private static byte[] scrambleHintText(byte[] romBytes, Integer[] worldMap, int idx, int outIdx) {
        // first, decompress level text as one big string
        byte[] levelText = new byte[64*16];
        int offset = 0;
        int textIdx = 0;
        while (textIdx < levelText.length) {
            int len = (romBytes[idx+offset] & 0xff) - 0x80;
            offset++;
            for (int i = 0; i < len; i++) {
                levelText[textIdx] = (byte)(romBytes[idx+offset] & 0xff);
                textIdx++;
                offset++;
            }
            len = romBytes[idx+offset] & 0xff;
            offset++;
            offset++;
            for (int i = 0; i < len; i++) {
                levelText[textIdx] = (byte)0x7f;
                textIdx++;
            }
        }

        // swap level names around
        int[] skips = {0, 1, 8, 15, 22}; // empty spaces where N7, W7, and S7 would be
        levelText = scrambleLevels(levelText,worldMap,32,0, skips);

        // compress level names again
        List<Byte> compressedNames = new Vector<>();
        textIdx = 0;
        while (textIdx < levelText.length) {
            // find three or more spaces in a row to signify end of string
            int scanIdx = textIdx;
            while (scanIdx < levelText.length - 2 && (levelText[scanIdx] != 0x7f || levelText[scanIdx+1] != 0x7f || levelText[scanIdx+2] != 0x7f)) {
                scanIdx++;
            }
            // write string length followed by string
            int len = scanIdx - textIdx;
            compressedNames.add((byte)(0x80 + (len & 0xff)));
            for (int i = 0; i < len; i++) {
                compressedNames.add(levelText[textIdx]);
                textIdx++;
            }
            // determine number of spaces between this string and next
            scanIdx = textIdx;
            while (scanIdx < levelText.length && levelText[scanIdx] == 0x7f) {
                scanIdx++;
            }
            len = scanIdx - textIdx;
            compressedNames.add((byte)len);
            compressedNames.add((byte)0x7f);
            textIdx = scanIdx;
        }
        // persist recompressed text to the working copy of the ROM
        for (int i = 0; i < compressedNames.size(); i++) {
            romBytes[outIdx + i] = compressedNames.get(i);
        }
        if (idx != outIdx) {
            //add ending tag
            romBytes[outIdx + compressedNames.size()] = (byte) 0x00;
            romBytes[outIdx + compressedNames.size() + 1] = (byte) 0x7f;
        }

        return romBytes;
    }

    private static byte[] shuffleBGPalettes(byte[] romBytes, int[] switches) {
        // c0b1b = start of table, +C8000
        int tableIdx = 0xc0b1b;
        int palIdx = 0xc8000;
        for (int i = 0; i < switches.length; i += 8) {
            int offset = ((romBytes[tableIdx+(i/4)+1] & 0xff) << 8) + (romBytes[tableIdx+(i/4)] & 0xff);
            for (int j = 0; j < 64; j += 8) {
                romBytes = swapColors(romBytes, palIdx + offset + j, switches[i+j/8], false);
//                romBytes = swapColors(romBytes, palIdx + offset + j, switches[i + 1]);
//                romBytes = swapColors(romBytes, palIdx + offset + j, switches[i + 2]);
            }
        }
        return romBytes;
    }

    private static byte[] shuffleObjPalettes(byte[] romBytes, int[] colors) {
        int idx = 0x65251;
        int offset = 0;
        int colorIdx = 8;
        while (colorIdx < colors.length) {
            offset += 9; // skip object gfx pointers
            // look for terminating pointer in object list
            while ((romBytes[idx+offset] & 0xff) != 0xff || (romBytes[idx+offset+1] & 0xff) != 0xff) {
                offset += 2;
            }
            offset += 2; // now we're looking at object palettes!
            for (int i = 0; i < 4; i++) {
                romBytes = swapColors(romBytes,idx+offset,colors[colorIdx], false);
                offset += 8;
                colorIdx++;
            }
        }

        // now apply one color rotation to all four key/chest palettes
        romBytes = swapColors(romBytes,0x64fc9, colors[0], true);
        romBytes = swapColors(romBytes,0x64fd7, colors[0], false);
        romBytes = swapColors(romBytes,0x64fe5, colors[0], false);
        romBytes = swapColors(romBytes,0x64ff3, colors[0], false);

        // rotate rudy's colors
        romBytes = swapColors(romBytes,0xdb000,colors[1],false);
        romBytes = swapColors(romBytes,0xdb008,colors[1],false);
        romBytes = swapColors(romBytes,0xdb010,colors[1],false);
        romBytes = swapColors(romBytes,0xdb018,colors[1],false);
        romBytes = swapColors(romBytes,0xdb020,colors[2],false);
        romBytes = swapColors(romBytes,0xdb028,colors[3],false);
        romBytes = swapColors(romBytes,0xdb030,colors[4],false);
        romBytes = swapColors(romBytes,0xdb038,colors[5],false);
        romBytes = swapColors(romBytes,0xdb040,colors[6],false);
        romBytes = swapColors(romBytes,0xdb048,colors[1],false);
        romBytes = swapColors(romBytes,0xdb050,colors[7],false);
        romBytes = swapColors(romBytes,0xdb058,colors[7],false);
        romBytes = swapColors(romBytes,0xdb060,colors[7],false);

        romBytes = swapColors(romBytes,0x4d01b,colors[1],false);
        romBytes = swapColors(romBytes,0x4d023,colors[1],false);
        romBytes = swapColors(romBytes,0x4d02b,colors[1],false);
        romBytes = swapColors(romBytes,0x4d033,colors[1],false);
        romBytes = swapColors(romBytes,0x4d03b,colors[2],false);
        romBytes = swapColors(romBytes,0x4d043,colors[3],false);

        romBytes = swapColors(romBytes,0x4d04b,colors[1],false);
        romBytes = swapColors(romBytes,0x4d053,colors[1],false);
        romBytes = swapColors(romBytes,0x4d05b,colors[1],false);
        romBytes = swapColors(romBytes,0x4d063,colors[1],false);
        romBytes = swapColors(romBytes,0x4d06b,colors[2],false);
        romBytes = swapColors(romBytes,0x4d073,colors[3],false);

        romBytes = swapColors(romBytes,0x4d07b,colors[1],false);
        romBytes = swapColors(romBytes,0x4d083,colors[1],false);
        romBytes = swapColors(romBytes,0x4d08b,colors[1],false);
        romBytes = swapColors(romBytes,0x4d093,colors[1],false);
        romBytes = swapColors(romBytes,0x4d09b,colors[2],false);
        romBytes = swapColors(romBytes,0x4d0a3,colors[3],false);

        // need to clean up a few colors for rudy
        romBytes[0xdb020] = romBytes[0xdb000];
        romBytes[0xdb021] = romBytes[0xdb001];
        romBytes[0xdb024] = romBytes[0xdb02c];
        romBytes[0xdb025] = romBytes[0xdb02d];
        romBytes[0xdb028] = romBytes[0xdb000];
        romBytes[0xdb029] = romBytes[0xdb001];

        romBytes[0x4d03b] = romBytes[0x4d01b];
        romBytes[0x4d03c] = romBytes[0x4d01c];
        romBytes[0x4d03f] = romBytes[0x4d047];
        romBytes[0x4d040] = romBytes[0x4d048];
        romBytes[0x4d043] = romBytes[0x4d01b];
        romBytes[0x4d044] = romBytes[0x4d01c];

        romBytes[0x4d06b] = romBytes[0x4d04b];
        romBytes[0x4d06c] = romBytes[0x4d04c];
        romBytes[0x4d06f] = romBytes[0x4d077];
        romBytes[0x4d070] = romBytes[0x4d078];
        romBytes[0x4d073] = romBytes[0x4d04b];
        romBytes[0x4d074] = romBytes[0x4d04c];

        romBytes[0x4d09b] = romBytes[0x4d07b];
        romBytes[0x4d09c] = romBytes[0x4d07c];
        romBytes[0x4d09f] = romBytes[0x4d0a7];
        romBytes[0x4d0a0] = romBytes[0x4d0a8];
        romBytes[0x4d0a3] = romBytes[0x4d07b];
        romBytes[0x4d0a4] = romBytes[0x4d07c];



        // set menu key colors to match real key colors
        romBytes[0x1f41de] = romBytes[0x64fcd];
        romBytes[0x1f41df] = romBytes[0x64fce];
        romBytes[0x1e03aa] = romBytes[0x64fcd];
        romBytes[0x1e03ab] = romBytes[0x64fce];
        romBytes[0x1e042a] = romBytes[0x64fcd];
        romBytes[0x1e042b] = romBytes[0x64fce];

        romBytes[0x1f41e0] = romBytes[0x64fdb];
        romBytes[0x1f41e1] = romBytes[0x64fdc];
        romBytes[0x1e03ac] = romBytes[0x64fdb];
        romBytes[0x1e03ad] = romBytes[0x64fdc];
        romBytes[0x1e042c] = romBytes[0x64fdb];
        romBytes[0x1e042d] = romBytes[0x64fdc];

        romBytes[0x1f41e6] = romBytes[0x64fe9];
        romBytes[0x1f41e7] = romBytes[0x64fea];
        romBytes[0x1e03b2] = romBytes[0x64fe9];
        romBytes[0x1e03b3] = romBytes[0x64fea];
        romBytes[0x1e0432] = romBytes[0x64fe9];
        romBytes[0x1e0433] = romBytes[0x64fea];

        romBytes[0x1f41e8] = romBytes[0x64ff7];
        romBytes[0x1f41e9] = romBytes[0x64ff8];
        romBytes[0x1e03b4] = romBytes[0x64ff7];
        romBytes[0x1e03b5] = romBytes[0x64ff8];
        romBytes[0x1e0434] = romBytes[0x64ff7];
        romBytes[0x1e0435] = romBytes[0x64ff8];

        return romBytes;
    }

    private static byte[] HSVtoGBC(float hue, float sat, float val) {
        int color = Color.HSBtoRGB(hue, sat, val);
        int b = (color & 0xFF) >>> 3;
        int g = (color & 0xFF00) >>> 11;
        int r = (color & 0xFF0000) >>> 19;
        color = (b << 10) + (g << 5) + r;
        byte[] retBytes = new byte[2];
        retBytes[0] = (byte)(color & 0xff);
        retBytes[1] = (byte)((color & 0xff00) >>> 8);
        return retBytes;
    }

    private static byte[] swapColors(byte[] romBytes, int idx, int swap, boolean grayKey) {
        for (int i = 0; i < 8; i += 2) {
            int pal = ((romBytes[idx+i+1] & 0xff) << 8) + (romBytes[idx+i] & 0xff);
            int b = (pal & 0x7c00) >>> 10;
            int g = (pal & 0x3e0) >>> 5;
            int r = (pal & 0x1f);

//            int sb = (swap & 0x7c00) >>> 10;
//            int sg = (swap & 0x3e0) >>> 5;
//            int sr = (swap & 0x1f);
//
//            b -= (b - sb) * 80 / 100;
//            g -= (g - sb) * 80 / 100;
//            r -= (r - sr) * 80 / 100;

            float[] hsv = Color.RGBtoHSB(r*8, g*8, b*8, null);
            if (grayKey) {
                hsv[0] = 0.7778f;
                if (i == 2) {
                    hsv[1] = 0.32f;
                    hsv[2] = 0.97f;
                }
                else if (i == 4) {
                    hsv[1] = 1.0f;
                    hsv[2] = 0.5f;
                }
            }
            pal = Color.HSBtoRGB(hsv[0] + (float)(swap)/100000.0f,hsv[1],hsv[2]);
            b = (pal & 0xFF) >>> 3;
            g = (pal & 0xFF00) >>> 11;
            r = (pal & 0xFF0000) >>> 19;

//            int temp;
//            switch (swap) {
//                case 0:
//                    break;
//                case 1:
//                    temp = b;
//                    b = g;
//                    g = temp;
//                    break;
//                case 2:
//                    temp = r;
//                    r = g;
//                    g = temp;
//                    break;
//                case 3:
//                    temp = r;
//                    r = b;
//                    b = temp;
//            }
            pal = (b << 10) + (g << 5) + r;
            romBytes[idx+i] = (byte)(pal & 0xff);
            romBytes[idx+i+1] = (byte)((pal & 0xff00) >>> 8);
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
