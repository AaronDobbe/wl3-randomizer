import com.google.gson.*;

import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import keyshuffle.KeyLocation;
import keyshuffle.Level;

public class Patcher {

    /**
     * Create a patched, randomized ROM and write it to disk. Any NULL options will not be shuffled.
     *
     * @param vanillaPathStr  path to a vanilla ROM
     * @param treasures       ordered list of randomized treasures
     * @param seed            encoded String representation of the seed used to generate treasures
     * @param playthrough     byte array representing order that treasures should be collected in, or null if vanilla
     * @param music           ordered array of music ids
     * @param worldMap        ordered array of level ids
     * @param levelColors     list of ints from 0 to 100000 representing an amount to rotate level color hues by
     * @param titleBGColors   list of ints from 0 to 100000 representing an amount to rotate title screen color hues by
     * @param otherBGColors   list of ints from 0 to 100000 representing an amount to rotate misc screen color hues by
     * @param objColors       list of ints from 0 to 100000 representing an amount to rotate enemy/object color hues by
     * @param chestColors     list of ints from 0 to 100000 representing four hues, four saturations, and four values to apply to the four key/chest pairs (highlights and outlines will be auto-generated)
     * @param keyLocations    list of levels and their key placements
     * @param golfOrder       ordered array of golf course ids
     * @param cutsceneSkip    true if the cutscene skip patch should be applied
     * @param version         String representing current app version
     * @throws IOException    if something goes wrong reading from or writing to a ROM
     */
    public static void patch(String vanillaPathStr,
                             int[] treasures,
                             String seed,
                             byte[] playthrough,
                             Integer[] music,
                             Integer[] worldMap,
                             int[] levelColors,
                             int[] titleBGColors,
                             int[] otherBGColors,
                             int[] objColors,
                             int[] chestColors,
                             Level[] keyLocations,
                             Integer[] golfOrder,
                             boolean cutsceneSkip,
                             boolean revealSecrets,
                             List<Integer> startingPowers,
                             String version) throws IOException {
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
        if (keyLocations != null) {
            romBytes = keyShufflePatch(romBytes, keyLocations);
        }
        if (worldMap != null) {
            romBytes = mapPatch(romBytes, worldMap, keyLocations != null);
        }
        if (golfOrder != null) {
            romBytes = shuffleGolf(romBytes, golfOrder);
        }
        if (levelColors != null) {
            romBytes = shuffleBGPalettes(romBytes,levelColors);
        }
        if (titleBGColors != null) {
            romBytes = shuffleTitleBGPalettes(romBytes,titleBGColors);
        }
        if (otherBGColors != null) {
            romBytes = shuffleOtherBGPalettes(romBytes,otherBGColors);
        }
        if (objColors != null) {
            romBytes = shuffleObjPalettes(romBytes, objColors);
        }
        if (chestColors != null) {
            romBytes = shuffleChestPalettes(romBytes, chestColors);
        }
        if (cutsceneSkip) {
            romBytes = applyPatch(romBytes,"cutSkipPatch.json");
        }
        if (startingPowers != null && startingPowers.size() > 0) {
            romBytes = addStartingPowers(romBytes, startingPowers);
        }
        if (revealSecrets) {
            romBytes = revealSecrets(romBytes);
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
    private static byte[] mapPatch(byte[] romBytes, Integer[] worldMap, boolean keyShuffle) throws IOException {
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

        if (keyShuffle) {
            scrambleLevels(romBytes,worldMap,3,0x36eb);
        }

        // also scramble the music table
        scrambleLevels(romBytes, worldMap, 16, 0x3fe40);

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
        byte[][] levelData = new byte[worldMap.length][entrySize];
        for (int i = 0; i < worldMap.length*entrySize; i++) {
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
        for (int i = 0; i < worldMap.length*entrySize; i++) {
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

    /**
     * Rotate level background palettes by the given amounts.
     */
    private static byte[] shuffleBGPalettes(byte[] romBytes, int[] switches) {
        // c0b1b = start of table, +C8000
        int tableIdx = 0xc0b1b;
        int palIdx = 0xc8000;
        for (int i = 0; i < switches.length; i += 8) {
            int offset = ((romBytes[tableIdx+(i/4)+1] & 0xff) << 8) + (romBytes[tableIdx+(i/4)] & 0xff);
            for (int j = 0; j < 64; j += 8) {
                romBytes = swapColors(romBytes, palIdx + offset + j, switches[i+j/8], false);
            }
        }

        // clean up a few colors to avoid ugly jagged edges
        int[][] cleanups = {
                {0x0,1,0,0,0},
                {0x1,1,0,0,0},
                {0x8,1,0,7,2},
                {0xd,1,0,7,2},
                {0xe,6,0,4,0},
                {0xf,1,0,6,2},
                {0x13,1,0,4,0},
                {0x14,0,1,4,1},
                {0x14,2,2,1,0},
                {0x14,2,3,1,1},
                {0x1b,2,1,4,0},
                {0x1f,6,0,3,2},
                {0x1f,7,0,1,2},
                {0x1f,4,0,1,0},
                {0x20,4,0,3,0},
                {0x2b,4,0,7,0},
                {0x25,2,0,1,0},
                {0x26,1,0,4,0},
                {0x32,4,0,6,2},
                {0x35,2,0,1,1},
                {0x3a,0,1,4,1},
                {0x3b,2,1,4,0},
                {0x3f,4,0,7,0},
                {0x47,6,0,4,0},
                {0x49,6,0,3,2},
                {0x49,7,0,1,2},
                {0x49,4,0,1,0},
                {0x4a,4,0,3,0},
                {0x4b,4,0,3,0},
                {0x4c,4,0,3,0},
                {0x50,6,0,4,1},
                {0x50,6,1,7,0},
                {0x54,2,0,1,0},
                {0x64,2,0,1,1},
                {0x6c,4,0,7,0},
                {0x6d,4,0,7,0},
                {0x6e,4,0,7,0},
                {0x6f,4,0,7,0}
        };

        for (int[] cleanup : cleanups) {
            int palSet = cleanup[0];
            int targetPal = cleanup[1];
            int targetCol = cleanup[2];
            int srcPal = cleanup[3];
            int srcCol = cleanup[4];

            int setOffset = ((romBytes[tableIdx+(palSet*2)+1] & 0xff) << 8) + (romBytes[tableIdx+(palSet*2)] & 0xff);
            int targetOffset = setOffset + targetPal*8 + targetCol*2;
            int srcOffset = setOffset + srcPal*8 + srcCol*2;

            romBytes[palIdx + targetOffset] = romBytes[palIdx + srcOffset];
            romBytes[palIdx + targetOffset + 1] = romBytes[palIdx + srcOffset + 1];
        }

        return romBytes;
    }

    /**
     * Apply randomized colors to title screen.
     */
    private static byte[] shuffleTitleBGPalettes(byte[] romBytes, int[] colors) {
        int tableIdx = 0x5002;
        // rotate four key palettes; build the remaining four from those palettes
        int[] entriesToShuffle = {1, 4, 5, 7};
        for (int i = 0; i < entriesToShuffle.length; i++) {
            romBytes = swapColors(romBytes, tableIdx + entriesToShuffle[i]*8, colors[i], false);
        }
        // treat 2 and 3 same as 1 and 4
        romBytes = swapColors(romBytes, tableIdx + 2*8, colors[0], false);
        romBytes = swapColors(romBytes, tableIdx + 3*8, colors[1], false);

        int[] entriesToBuild = {0, 2, 3, 6};
        int[][][] copies = {
                {{5,0},{-1,-1},{4,2},{-1,-1}},
                {{4,2},{-1,-1},{-1,-1},{-1,-1}},
                {{-1,-1},{-1,-1},{4,2},{-1,-1}},
                {{-1,-1},{5,1},{7,2},{-1,-1}}
        };
        for (int i = 0; i < entriesToBuild.length; i++) {
            int pal = entriesToBuild[i];
            for (int j = 0; j < copies[i].length; j++) {
                int[] copy = copies[i][j];
                if (copy[0] < 0) continue;
                romBytes[tableIdx + pal*8 + j*2] = romBytes[tableIdx + copy[0]*8 + copy[1]*2];
                romBytes[tableIdx + pal*8 + j*2 + 1] = romBytes[tableIdx + copy[0]*8 + copy[1]*2 + 1];
            }
        }

        // this is weird, but we need to edit one sprite's color to not stand out (OBJ 2.2)
        int titleSprIdx = 0x5042;
        romBytes[titleSprIdx + 2*8 + 2*2] = romBytes[tableIdx + 2*8 + 2*2];
        romBytes[titleSprIdx + 2*8 + 2*2 + 1] = romBytes[tableIdx + 2*8 + 2*2 + 1];

        // also edit intro palettes to match
        int introIdx = 0x4f82;
        for (int i = 0; i < 20; i++) {
            romBytes[introIdx + i*2] = romBytes[tableIdx];
            romBytes[introIdx + i*2 + 1] = romBytes[tableIdx + 1];
        }
        for (int i = 0; i < 24; i++) {
            romBytes[introIdx + 40 + i] = romBytes[tableIdx + 40 + i];
        }

        return romBytes;
    }

    /**
     * Apply randomized colors to misc screens.
     */
    private static byte[] shuffleOtherBGPalettes(byte[] romBytes, int[] colors) {
        int[] indexes = {
                0x1ca1cf, // golf lobby
                0x1ca08f, // golf
                0x1f4182, // pause
                0x1e0378, // results 1
                0xd50a4,  // results 2
                0x1f628c // save
        };

        for (int i = 0; i < indexes.length; i++) {
            int idx = indexes[i];
            for (int j = 0; j < 8; j++) {
                romBytes = swapColors(romBytes, idx + j*8, colors[i],false);
            }
        }

        return romBytes;
    }

    /**
     * Rotate sprite palettes by the given amounts.
     */
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

        return romBytes;
    }

    /**
     * Apply colors to the key/chest palettes.
     */
    private static byte[] shuffleChestPalettes(byte[] romBytes, int[] colors) {
        for (int i = 0; i < 4; i++) {
            float hue = (float)colors[i] / 100000f;
            float sat = (float)colors[i+4] / 100000f;
            float val = (float)colors[i+8] / 100000f;

            byte[] main = HSVtoGBC(hue,sat,val);

            float hsat = sat/3f;
            float hval = 0.97f;

            byte[] highlight = HSVtoGBC(hue,hsat,hval);

            float oval = val * 0.2857f;

            byte[] outline = HSVtoGBC(hue,sat,oval);

            byte[] palette = {highlight[0],highlight[1],main[0],main[1],outline[0],outline[1]};
            int idx = 0x64fc9 + (i * 0xe) + 0x2;
            for (int j = 0; j < palette.length; j++) {
                romBytes[idx + j] = palette[j];
            }
        }

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

    /**
     * Converts a HSV color to GBC palette format.
     */
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

    /**
     * Rotates a color palette at the given index.
     */
    private static byte[] swapColors(byte[] romBytes, int idx, int swap, boolean grayKey) {
        for (int i = 0; i < 8; i += 2) {
            int pal = ((romBytes[idx+i+1] & 0xff) << 8) + (romBytes[idx+i] & 0xff);
            int b = (pal & 0x7c00) >>> 10;
            int g = (pal & 0x3e0) >>> 5;
            int r = (pal & 0x1f);

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

            pal = (b << 10) + (g << 5) + r;
            romBytes[idx+i] = (byte)(pal & 0xff);
            romBytes[idx+i+1] = (byte)((pal & 0xff00) >>> 8);
        }
        return romBytes;
    }

    /**
     * Shuffle pointers to golf courses.
     */
    private static byte[] shuffleGolf(byte[] romBytes, Integer[] order) {
        int golfTableIdx = 0x1c8ac3;
        return scrambleLevels(romBytes, order,0xa, golfTableIdx);
    }

    /**
     * Apply logic patch and shuffle keys.
     */
    private static byte[] keyShufflePatch(byte[] romBytes, Level[] keyLocations) throws IOException {
        romBytes = applyPatch(romBytes, "keyshuffle/keyShufflePatch.json");

        // we need to move some objects off of palette 2 and move keys onto palette 2
        romBytes = editPalettes(romBytes,0x63, 0x95,0x4000, 2, 0); // music coin
        romBytes = editPalettes(romBytes,0x63, 0x1ef, 0x23c,3, 2); // key
        romBytes = editPalettes(romBytes,0x3, 0x1287, 0x1499,2, 0); // dust, stars, etc
        romBytes = editPalettes(romBytes,0x60, 0x23, 0x241,2, 0); // coin
        romBytes = editPalettes(romBytes,0x60, 0x296, 0x563,2, 0); // coin
        romBytes = editPalettes(romBytes,0x60, 0xefb, 0x136b,2, 3); // enemy projectiles
        romBytes = editPalettes(romBytes,0x61, 0xb04, 0xeea,2, 3); // enemy projectiles
        romBytes = editPalettes(romBytes,0x62, 0x272f, 0x2894,2, 3); // enemy projectiles

        // start at level pointer table
        int levelTableIdx = 0xc00be;
        for (int levelNum = 0; levelNum < 25; levelNum++) {
            Level level = keyLocations[levelNum];
            for (int levelMod = 0; levelMod < 8; levelMod++) {
                // compute index in level pointer table, get level metadata
                int levelIdx = levelNum * 8 + levelMod;
                int levelAddr = romBytes[levelTableIdx + levelIdx*2 + 1] & 0xff;
                levelAddr <<= 8;
                levelAddr += romBytes[levelTableIdx + levelIdx*2] & 0xff;
                levelAddr -= 0x4000;
                levelAddr += 0xc0000;
                // get ROM bank to examine and address of level object data in that bank
                int bank = romBytes[levelAddr+2] & 0xff;
                int objAddr = romBytes[levelAddr+4] & 0xff;
                objAddr <<= 8;
                objAddr += romBytes[levelAddr+3] & 0xff;
                objAddr -= 0x4000;
                objAddr += bank * 0x4000;
                // scan through rle-compressed level data, replace keys and coins as we find them
                int objOffset = 0;
                int objIdx = 0;
                int locIdx = 0;
                while (objIdx < 0x10 * 0x0a * 0x30) {
                    // check first byte to see if the next bytes are to be expanded or not
                    byte check = (byte)(romBytes[objAddr+objOffset] & 0xff);
                    if ((check & 0x80) == 0) {
                        // next byte is to be expanded, so advance the object index appropriately
                        objOffset += 2;
                        objIdx += check * 2;
                    }
                    else {
                        // next bytes are a literal string, we need to be on the lookout for keys and coins
                        check &= 0x7f;
                        objOffset++;
                        for (int j = 0; j < check; j++) {
                            // check both first and last nybbles for keys/coins (2 and 3) - when found, replace with a key if we find a key that matches in the level list, or a coin otherwise
                            byte objByte = (byte)(romBytes[objAddr+objOffset] & 0xff);
                            if ((objByte & 0xf0) == 0x20 || (objByte & 0xf0) == 0x30) {
                                romBytes[objAddr+objOffset] = (byte)((objByte & 0x0f) + 0x30);
                                for (int key = 0; key < 4; key++) {
                                    KeyLocation loc = level.getLocation(key);
                                    if (loc.getY() * 0xa0 + loc.getX() == objIdx) {
                                        romBytes[objAddr+objOffset] = (byte)((objByte & 0x0f) + 0x20);
                                        break;
                                    }
                                }
                                locIdx++;
                            }
                            objIdx++;
                            if ((objByte & 0x0f) == 0x02 || (objByte & 0x0f) == 0x03) {
                                romBytes[objAddr+objOffset] = (byte)((objByte & 0xf0) + 0x03);
                                for (int key = 0; key < 4; key++) {
                                    KeyLocation loc = level.getLocation(key);
                                    if (loc.getY() * 0xa0 + loc.getX() == objIdx) {
                                        romBytes[objAddr+objOffset] = (byte)((objByte & 0xf0) + 0x02);
                                        break;
                                    }
                                }
                                locIdx++;
                            }
                            objIdx++;
                            objOffset++;
                        }
                    }
                }
            }
        }

        // set up key color table for the key logic patch
        int keyTableIdx = 0x36eb;
        int keyTableOffset = 0;
        for (int levelNum = 0; levelNum < 25; levelNum++) {
            Level level = keyLocations[levelNum];
            for (int key = 3; key > 0; key--) {
                KeyLocation loc = level.getLocation(key);
                int regionCoords = loc.getRegion() / 0xa;
                regionCoords <<= 4;
                regionCoords += loc.getRegion() % 0xa;
                romBytes[keyTableIdx+keyTableOffset] = (byte)regionCoords;
                keyTableOffset++;
            }
        }

        return romBytes;
    }

    /**
     * Edit sprite palette assignments in the given range.
     *
     * @param bank  which ROM bank to examine
     * @param start starting address of palettes to edit
     * @param end   end address of palettes to edit (should point to a 0x80 byte)
     * @param from  palette number (0-7) to change from
     * @param to    palette number (0-7) to change to
     */
    private static byte[] editPalettes(byte[] romBytes, int bank, int start, int end, int from, int to) {
        int idx = bank * 0x4000;
        int offset = start;
        while (offset < end) {
            if ((romBytes[idx+offset] & 0xff) == 0x80) {
                offset++;
                if ((romBytes[idx+offset] & 0xff) == 0xff &&
                        (romBytes[idx+offset+1] & 0xff) == 0xff &&
                        (romBytes[idx+offset+2] & 0xff) == 0xff &&
                        (romBytes[idx+offset+3] & 0xff) == 0xff) {
                    break;
                }
                while ((romBytes[idx+offset+1] & 0xf0) >= 0x40 && (romBytes[idx+offset+1] & 0xf0) <= 0x70) {
                    offset += 2;
                }
            }
            offset += 3;
            if ((romBytes[idx+offset] & 0x07) == from) {
                romBytes[idx+offset] = (byte)((romBytes[idx+offset] & 0xf8) + to);
            }
            offset++;
        }
        return romBytes;
    }

    /**
     * Update game initialization routine to award Wario some treasure on game start.
     */
    private static byte[] addStartingPowers(byte[] romBytes, List<Integer> startingPowers) throws IOException {
        romBytes = applyPatch(romBytes,"powerPatch.json");
        int itemsIdx = 0x83fb3;
        int powersAIdx = 0x83fe2;
        int powersBIdx = 0x83fe6;
        int treasureCountIdx = 0x83ff5;

        for (Integer item : startingPowers) {
            // find which inventory bucket and slot this belongs in
            int bucket = item / 8;
            int slot = item % 8;

            // place item in correct slot
            romBytes[itemsIdx + bucket] |= ((1 << slot) & 0xff);
        }

        byte powersA = 0x00;
        byte powersB = 0x00;
        if (startingPowers.contains(Items.BLUE_OVERALLS)) powersA |= 0x01;
        if (startingPowers.contains(Items.RED_OVERALLS)) powersA |= 0x02;
        if (startingPowers.contains(Items.JUMP_BOOTS)) powersA |= 0x04;
        if (startingPowers.contains(Items.RED_GLOVES)) powersB |= 0x01;
        if (startingPowers.contains(Items.GOLD_GLOVES)) powersB |= 0x02;
        if (startingPowers.contains(Items.SPIKED_HELMET)) powersB |= 0x04;
        if (startingPowers.contains(Items.GARLIC)) powersB |= 0x08;
        if (startingPowers.contains(Items.FROG_GLOVES)) powersB |= 0x10;
        if (startingPowers.contains(Items.SWIM_FINS)) powersB |= 0x20;

        romBytes[powersAIdx] = powersA;
        romBytes[powersBIdx] = powersB;
        // store initial treasure count as binary-coded decimal
        romBytes[treasureCountIdx] = (byte)(((startingPowers.size()/10 & 0xff) << 4) + (startingPowers.size()%10 & 0xff));

        return romBytes;
    }

    /**
     * Update level data to reveal hidden pathways to the player.
     */
    private static byte[] revealSecrets(byte[] romBytes) {
        int tilesetTable  = 0xC04C5;
        int effectsTable  = 0xC8000;
        int subtilesTable = 0xC090D;
        int flagsTable    = 0xC09D1;
        // bank -> location -> data
        SortedMap<Integer, SortedMap<Integer, List<Byte>>> compressedGfxData = new TreeMap<>();
        for (int tilesetId = 1; tilesetId <= 0x99; tilesetId++) {
            int tilesetEntry = tilesetTable + tilesetId*2;
            int tilesetDataOffset = ((romBytes[tilesetEntry+1] & 0xff) << 8) + (romBytes[tilesetEntry] & 0xff);
            int tilesetDataLocation = 0xC0000 + tilesetDataOffset - 0x4000;
            int subtilesIdx = romBytes[tilesetDataLocation] & 0xff;
            int flagsIdx = romBytes[tilesetDataLocation+1] & 0xff;
            int dataBank = 0x38 + subtilesIdx/6;

            byte[] subtiles = new byte[0x200];
            byte[] effects = new byte[0x100];
            byte[] flags = new byte[0x200];
            int compressedFlagsSize = 0;

            int subtilesLocation = ((romBytes[subtilesTable+subtilesIdx*2+1] & 0xff) << 8)
                    + (romBytes[subtilesTable+subtilesIdx*2] & 0xff);
            subtilesLocation = subtilesLocation - 0x4000 + (dataBank * 0x4000);
            System.arraycopy(romBytes, subtilesLocation + 0, subtiles, 0, subtiles.length);
            int effectsLocation = ((romBytes[effectsTable+subtilesIdx*2+1] & 0xff) << 8)
                    + (romBytes[effectsTable+subtilesIdx*2] & 0xff);
            int effectsBank = (subtilesIdx >= 0x3f) ? 0x50 : 0x32;
            effectsLocation = effectsLocation - 0x4000 + (effectsBank * 0x4000);
            System.arraycopy(romBytes, effectsLocation + 0, effects, 0, effects.length);
            int flagsLocation = ((romBytes[flagsTable+flagsIdx*2+1] & 0xff) << 8)
                    + (romBytes[flagsTable+flagsIdx*2] & 0xff);
            flagsLocation = flagsLocation - 0x4000 + (dataBank * 0x4000);
            int flagsPtr = 0;
            int flagsProcessed = 0;
            while (true) {
                int indicator = romBytes[flagsLocation + flagsPtr] & 0xff;
                flagsPtr++;
                if (indicator == 0x00) {
                    break;
                }
                else if ((indicator & 0x80) > 0) {
                    indicator &= 0x7f;
                    for (int i = 0; i < indicator; i++) {
                        flags[flagsProcessed] = romBytes[flagsLocation+flagsPtr];
                        flagsPtr++;
                        flagsProcessed++;
                    }
                }
                else {
                    for (int i = 0; i < indicator; i++) {
                        flags[flagsProcessed] = romBytes[flagsLocation+flagsPtr];
                        flagsProcessed++;
                    }
                    flagsPtr++;
                }
            }
            if (flagsProcessed != 0x200) {
                System.out.println("Error in decompression");
            }
            for (int metatileIdx = 0; metatileIdx < effects.length/2; metatileIdx++) {
                int effect = ((effects[metatileIdx*2+1] & 0xff) << 8) + (effects[metatileIdx*2] & 0xff);
                byte[] newSubtiles = null;
                if ((effect >= 0x49FB && effect <= 0x4A17) || (effect >= 0x4CB4 && effect <= 0x4CD0)) {
                    // regular break
                    newSubtiles = new byte[]{0x68, 0x69, 0x78, 0x79};
                }
                else if (effect >= 0x4D60 && effect <= 0x4D7C) {
                    // hard break
                    newSubtiles = new byte[]{0x6A, 0x6B, 0x7A, 0x7B};
                }
                else if (effect >= 0x4E0E && effect <= 0x4E2A) {
                    // big break BL
                    newSubtiles = new byte[]{0x48, 0x63, 0x78, 0x4A};
                }
                else if (effect >= 0x4EA2 && effect <= 0x4EBE) {
                    // big break BR
                    newSubtiles = new byte[]{0x63, 0x49, 0x4A, 0x79};
                }
                else if (effect >= 0x4F5F && effect <= 0x4F7B) {
                    // big break TR
                    newSubtiles = new byte[]{0x3A, 0x69, 0x63, 0x49};
                }
                else if (effect >= 0x4FF3 && effect <= 0x500F) {
                    // big break TL
                    newSubtiles = new byte[]{0x68, 0x3A, 0x48, 0x63};
                }
                else if (effect >= 0x50E0 && effect <= 0x50FC) {
                    // H big break BL
                    newSubtiles = new byte[]{0x5A, 0x7D, 0x7A, 0x4B};
                }
                else if (effect >= 0x5195 && effect <= 0x51B1) {
                    // H big break BR
                    newSubtiles = new byte[]{0x7D, 0x5B, 0x4B, 0x7B};
                }
                else if (effect >= 0x5273 && effect <= 0x528F) {
                    // H big break TR
                    newSubtiles = new byte[]{0x3B, 0x6B, 0x7D, 0x5B};
                }
                else if (effect >= 0x5328 && effect <= 0x5344) {
                    // H big break TL
                    newSubtiles = new byte[]{0x6A, 0x3B, 0x5A, 0x7D};
                }
                else if (effect >= 0x53FB && effect <= 0x5417) {
                    // throw break
                    newSubtiles = new byte[]{0x5C, 0x5D, 0x6C, 0x6D};
                }
                else if (effect >= 0x544B && effect <= 0x5467) {
                    // fire break
                    newSubtiles = new byte[]{0x5E, 0x5F, 0x6E, 0x6F};
                }
                else if (effect >= 0x5497 && effect <= 0x54B3) {
                    // fat break
                    newSubtiles = new byte[]{0x3E, 0x3F, 0x4E, 0x4F};
                }
                else if (effect >= 0x4A9A && effect <= 0x4C7C) {
                    // snow break
                    newSubtiles = (flags[metatileIdx*4] != 0x05) ? new byte[]{0x3C, 0x3D, 0x4C, 0x4D} : null;
                }
                else if (effect >= 0x54E4 && effect <= 0x55D7) {
                    // yarn break
                    newSubtiles = (flags[metatileIdx*4] != 0x05) ? new byte[]{0x3C, 0x3D, 0x4C, 0x4D} : null;
                }

                if (newSubtiles != null && (flags[metatileIdx*4] != 0x0D || flags[metatileIdx*4+1] != 0x0D)) {
                    System.arraycopy(newSubtiles, 0, subtiles, metatileIdx * 4 + 0, newSubtiles.length);

                    // now we need to deal with the flags...........
                    for (int i = 0; i < 4; i++) {
                        flags[metatileIdx*4 + i] &= 0x07;
                        flags[metatileIdx*4 + i] |= 0x08;
                    }
                }
            }

            // update subtiles
            List<Byte> subtileList = new Vector<>();
            for (byte subtile : subtiles) {
                subtileList.add(subtile);
            }
            compressedGfxData.putIfAbsent(dataBank, new TreeMap<>());
            compressedGfxData.get(dataBank).putIfAbsent(subtilesLocation, subtileList);

            // now we need to recompress flags
            int newCount = 0;
            int newPtr = 0;
            int lookaheadPtr = 0;
            int last = 0x00;
            int run = 0;
            List<Byte> compressedFlagsList = new Vector<>();

            while (newPtr < flags.length) {
                if (lookaheadPtr >= flags.length) {
                    compressedFlagsList.add((byte)((lookaheadPtr - newPtr) | 0x80));
                    newCount++;
                    while (newPtr < lookaheadPtr) {
                        compressedFlagsList.add(flags[newPtr]);
                        newCount++;
                        newPtr++;
                    }
                }
                else if ((flags[lookaheadPtr] & 0xff) == last) {
                    run++;
                    lookaheadPtr++;

                    if (run >= 3) {
                        lookaheadPtr -= 3;
                        if (lookaheadPtr > newPtr) {
                            compressedFlagsList.add((byte) ((lookaheadPtr - newPtr) | 0x80));
                            newCount++;
                            while (newPtr < lookaheadPtr) {
                                compressedFlagsList.add(flags[newPtr]);
                                newCount++;
                                newPtr++;
                            }
                        }
                        while (lookaheadPtr < flags.length && (lookaheadPtr-newPtr < 0x7f) && (flags[lookaheadPtr] & 0xff) == last) {
                            lookaheadPtr++;
                        }
                        compressedFlagsList.add((byte)(lookaheadPtr - newPtr));
                        compressedFlagsList.add((byte)(last));
                        newCount += 2;
                        newPtr = lookaheadPtr;

                        run = 0;
                    }
                }
                else {
                    last = flags[lookaheadPtr] & 0xff;
                    lookaheadPtr++;
                    run = 1;
                }
            }
            compressedFlagsList.add((byte)0x00);
            newCount++;

            compressedGfxData.get(dataBank).putIfAbsent(flagsLocation,compressedFlagsList);
        }

        // All level gfx data has been processed, edited, and recompressed
        // some data will unavoidably end up larger than it was previously when recompressed,
        // so we must move tileset data around to make space (and update pointers to said data!)

        for (Integer dataBank : compressedGfxData.keySet()) {
            int writePtr = -1;
            int bankStart = dataBank * 0x4000;
            for (Map.Entry<Integer, List<Byte>> dataEntry : compressedGfxData.get(dataBank).entrySet()) {
                int location = dataEntry.getKey();
                int relLocation = location % 0x4000;
                List<Byte> data = dataEntry.getValue();

                if (writePtr < 0) {
                    writePtr = relLocation;
                }
                relLocation += 0x4000;
                int newLocation = bankStart + writePtr;
                for (Byte dataByte : data) {
                    romBytes[bankStart + writePtr] = dataByte;
                    writePtr++;
                }
                newLocation = (newLocation % 0x4000) + 0x4000;

                // now update pointers
                int table = 0;
                boolean subtiles = false;
                if (data.size() == 0x200) {
                    // subtile data
                    table = subtilesTable;
                    subtiles = true;
                }
                else {
                    table = flagsTable;
                }
                for (int tilesetId = 1; tilesetId <= 0x99; tilesetId++) {
                    int tilesetEntry = tilesetTable + tilesetId*2;
                    int tilesetDataOffset = ((romBytes[tilesetEntry+1] & 0xff) << 8) + (romBytes[tilesetEntry] & 0xff);
                    int tilesetDataLocation = 0xC0000 + tilesetDataOffset - 0x4000;
                    int subtilesIdx = romBytes[tilesetDataLocation] & 0xff;
                    int flagsIdx = romBytes[tilesetDataLocation+1] & 0xff;
                    int thisBank = 0x38 + subtilesIdx/6;
                    int idx = (subtiles) ? subtilesIdx : flagsIdx;
                    if (thisBank != dataBank) {
                        continue;
                    }

                    int readLocation = ((romBytes[table + idx*2 + 1] & 0xFF) << 8) +
                            (romBytes[table + idx*2] & 0xFF);
                    if (readLocation == relLocation) {
                        romBytes[table + idx*2 + 1] = (byte)(newLocation >>> 8);
                        romBytes[table + idx*2] = (byte)(newLocation & 0xff);
                    }
                }
            }
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
