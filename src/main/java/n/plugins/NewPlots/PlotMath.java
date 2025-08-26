// File: src/main/java/n/plugins/NewPlots/PlotMath.java
package n.plugins.NewPlots;

public final class PlotMath {

    private PlotMath() {}

    public static class Index {
        public final int ix, iz;
        public Index(int ix, int iz) { this.ix = ix; this.iz = iz; }
    }

    public enum Area { PLOT, ROAD }

    private static int floorDiv(int a, int b) {
        int q = a / b;
        int r = a % b;
        if ((r != 0) && ((a ^ b) < 0)) q--;
        return q;
    }

    public static Index toIndex(int x, int z, int pitch) {
        int ix = floorDiv(x, pitch);
        int iz = floorDiv(z, pitch);
        return new Index(ix, iz);
    }

    public static Area classifyArea(int x, int z, Index idx, int plotSize, int pitch) {
        int baseX = idx.ix * pitch;
        int baseZ = idx.iz * pitch;
        int offX = x - baseX;
        int offZ = z - baseZ;
        boolean inPlotX = (offX >= 0 && offX < plotSize);
        boolean inPlotZ = (offZ >= 0 && offZ < plotSize);
        return (inPlotX && inPlotZ) ? Area.PLOT : Area.ROAD;
    }
}
