import com.google.gson.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

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
    public static void patch(String vanillaPathStr, int[] treasures, String seed, byte[] playthrough, Integer[] music, String version) throws IOException {
        Path vanillaPath = new File(vanillaPathStr).toPath();
        byte[] romBytes = Files.readAllBytes(vanillaPath);
        romBytes = applyPatch(romBytes, "baseDiff.json");
        romBytes = treasuresPatch(romBytes, treasures);
        if (playthrough != null) {
            romBytes = hintPatch(romBytes, playthrough);
        }
        if (music != null) {
            romBytes = musicPatch(romBytes, music);
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
    private static byte[] treasuresPatch(byte[] romBytes, int[] treasures) {
        int idx = 0x198f;
        for (int i = 0; i < treasures.length; i++) {
            romBytes[idx+i] = (byte)treasures[i];
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
