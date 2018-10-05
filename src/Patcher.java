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
     * @throws IOException    if something goes wrong reading from or writing to a ROM
     */
    public static void patch(String vanillaPathStr, int[] treasures, String seed) throws IOException {
        Path vanillaPath = new File(vanillaPathStr).toPath();
        byte[] romBytes = basePatch(vanillaPath);
        romBytes = treasuresPatch(romBytes, treasures);
        savePatchedFile(romBytes, seed);
    }

    /**
     * Read a ROM file and modify it in memory with the randomizer patch.
     *
     * @param vanillaPath  path to a vanilla ROM
     * @return byte array representing a patched ROM
     * @throws IOException
     */
    private static byte[] basePatch(Path vanillaPath) throws IOException {
            byte[] romBytes = Files.readAllBytes(vanillaPath);
            Gson gson = new GsonBuilder().create();
            ClassLoader classLoader = Patcher.class.getClassLoader();
            InputStream baseDiff = Patcher.class.getResourceAsStream("baseDiff.json");
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

    /**
     * Write the patched, randomized ROM to disk.
     *
     * @param romBytes  byte array representing the final ROM
     * @param seed      encoded String representation of the seed used to randomize the ROM
     * @throws IOException
     */
    private static void savePatchedFile(byte[] romBytes, String seed) throws IOException {
        String filename = "WL3-randomizer-" + seed + ".gbc";
        File randoFile = new File(filename);
        Files.write(randoFile.toPath(),romBytes);
    }
}
