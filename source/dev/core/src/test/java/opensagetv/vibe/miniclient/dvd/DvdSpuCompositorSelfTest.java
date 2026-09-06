package opensagetv.vibe.miniclient.dvd;

import java.util.Arrays;
import java.util.List;

public final class DvdSpuCompositorSelfTest {
    public static void main(String[] args) {
        DvdSpuFrame frame = new DvdSpuDecoder(new DvdDiagnostics())
                .decode(new DvdSpuPacket(0x20, 0, TestData.chgColconSpu())).get(0);
        int[] clut = new int[16];
        clut[1] = 0x00808080;
        clut[2] = 0x00eb8080;
        clut[4] = 0x00518080;

        DvdSpuCompositor compositor = new DvdSpuCompositor();
        DvdArgbFrame normal = compositor.compose(frame, clut, true, DvdHighlight.hidden());
        TestData.check(normal.argb.length == 4, "ARGB size");
        // CHG_COLCON applies only to y=0, so rows must differ for slots 1/2.
        TestData.check(normal.argb[0] != normal.argb[2], "CHG_COLCON composition");

        DvdHighlight highlight = new DvdHighlight(true, 0, 0, 1, 1, 0x2222ffff);
        DvdArgbFrame highlighted = compositor.compose(frame, clut, true, highlight);
        TestData.check(highlighted.argb[0] != normal.argb[0], "highlight precedence");

        DvdColorContrastTable.LineRegion region = new DvdColorContrastTable.LineRegion(
                10, 10, Arrays.asList(
                        new DvdColorContrastTable.PixelChange(11, 0x2222ffff),
                        new DvdColorContrastTable.PixelChange(13, 0x4444ffff)));
        DvdSpuFrame regions = new DvdSpuFrame(
                20, 0x20, 0, true, false, 10, 10, 4, 2,
                new byte[] {1, 1, 1, 1, 1, 1, 1, 1},
                new int[] {0, 1, 2, 3}, new int[] {15, 15, 15, 15},
                new DvdColorContrastTable(Arrays.asList(region)));
        DvdArgbFrame regionOutput = compositor.compose(
                regions, clut, true, DvdHighlight.hidden());
        TestData.check(regionOutput.argb[0] != regionOutput.argb[1],
                "main palette before first CHG_COLCON x");
        TestData.check(regionOutput.argb[1] == regionOutput.argb[2],
                "first CHG_COLCON horizontal region");
        TestData.check(regionOutput.argb[2] != regionOutput.argb[3],
                "second CHG_COLCON horizontal region");
        TestData.check(regionOutput.argb[4] == regionOutput.argb[0],
                "CHG_COLCON vertical region ends inclusively");

        DvdArgbFrame regionHighlight = compositor.compose(
                regions, clut, true, new DvdHighlight(true, 12, 10, 12, 10, 0x1111ffff));
        TestData.check(regionHighlight.argb[2] != regionOutput.argb[2],
                "highlight overrides CHG_COLCON at selected pixel");
        TestData.check(regionHighlight.argb[1] == regionOutput.argb[1],
                "highlight does not alter neighboring pixel");

        DvdArgbFrame noClut = compositor.compose(frame, null, true, null);
        TestData.check(noClut.argb.length == frame.width * frame.height,
                "missing CLUT falls back safely");
        System.out.println("PASS DvdSpuCompositorSelfTest");
    }
}

