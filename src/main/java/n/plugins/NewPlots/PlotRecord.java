// File: src/main/java/n/plugins/NewPlots/PlotRecord.java
package n.plugins.NewPlots;

public class PlotRecord {
    public final long id;
    public final String owner;
    public final String world;
    public final int ix;
    public final int iz;
    public final long createdAt;

    public PlotRecord(long id, String owner, String world, int ix, int iz, long createdAt) {
        this.id = id;
        this.owner = owner;
        this.world = world;
        this.ix = ix;
        this.iz = iz;
        this.createdAt = createdAt;
    }
}
