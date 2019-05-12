package keyshuffle;

import java.util.ArrayList;
import java.util.List;

public class Level {
    private int levelNumber;
    private String levelName;
    private KeyLocation[] locations;
    private List<List<Integer>> inventories;

    public Level(int levelNumber, String levelName) {
        locations = new KeyLocation[12];
        this.levelNumber = levelNumber;
        this.levelName = levelName;
        inventories = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            inventories.add(null);
        }
    }

    public Level(Level other) {
        levelNumber = other.getLevelNumber();
        levelName = other.getLevelName();
        locations = other.getLocations().clone();
        inventories = other.inventories == null ? null : new ArrayList<>(other.inventories);
    }

    public void setLocation(int idx, KeyLocation location) {
        locations[idx] = location;
    }

    public void setLocations(KeyLocation[] locations) {
        this.locations = locations;
    }

    public KeyLocation[] getLocations() {
        return locations;
    }

    public KeyLocation getLocation(int idx) {
        return locations[idx];
    }

    public int getLevelNumber() {
        return levelNumber;
    }

    public String getLevelName() {
        return levelName;
    }

    public List<List<Integer>> getInventories() {
        return inventories;
    }

    public void setInventories(List<List<Integer>> inventories) {
        this.inventories = inventories;
    }

    public List<Integer> getInventory(int idx) {
        return inventories.get(idx);
    }

    public void setInventory(int idx, List<Integer> inventory) {
        inventories.set(idx, inventory == null ? null : new ArrayList<>(inventory));
    }
}
