package keyshuffle;

public class KeyLocation {
    private int region;
    private int sector;
    private int x;
    private int y;
    private int subLocation;
    private String name;

    public KeyLocation(int region, int sector, int x, int y, int subLocation, String name) {
        this.region = region;
        this.sector = sector;
        this.x = x;
        this.y = y;
        this.subLocation = subLocation;
        this.name = name;
    }

    public int getRegion() {
        return region;
    }

    public int getSector() {
        return sector;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getSubLocation() {
        return subLocation;
    }

    public String getName() {
        return name;
    }
}
