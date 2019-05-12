package keyshuffle;

public class KeyLocation {
    private int region;
    private int sector;
    private int x;
    private int y;
    private int subLocation;

    public KeyLocation(int region, int sector, int x, int y, int subLocation) {
        this.region = region;
        this.sector = sector;
        this.x = x;
        this.y = y;
        this.subLocation = subLocation;
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
}
