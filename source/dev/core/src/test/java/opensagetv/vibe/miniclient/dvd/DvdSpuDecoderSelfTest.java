package opensagetv.vibe.miniclient.dvd;

import java.util.List;

public final class DvdSpuDecoderSelfTest {
    public static void main(String[] args) {
        DvdDiagnostics diagnostics = new DvdDiagnostics();
        DvdSpuDecoder decoder = new DvdSpuDecoder(diagnostics);

        List<DvdSpuFrame> basic = decoder.decode(new DvdSpuPacket(0x20, 90000,
                TestData.simpleSpu(false)));
        TestData.check(basic.size() == 1, "basic event count");
        DvdSpuFrame frame = basic.get(0);
        TestData.check(frame.width == 2 && frame.height == 2, "geometry");
        TestData.check((frame.pixels[0] & 3) == 1 && (frame.pixels[1] & 3) == 1, "top field");
        TestData.check((frame.pixels[2] & 3) == 2 && (frame.pixels[3] & 3) == 2, "bottom field");
        TestData.check(frame.colorIndex[0] == 3 && frame.colorIndex[3] == 0, "palette order");

        List<DvdSpuFrame> timed = decoder.decode(new DvdSpuPacket(0x20, 90000,
                TestData.paletteUpdateAndStopSpu()));
        TestData.check(timed.size() == 3, "start/update/stop events");
        TestData.check(timed.get(0).display, "start display");
        TestData.check(timed.get(1).display && timed.get(1).event90k == 90000 + 10 * 1024L,
                "palette-only redraw");
        TestData.check(!timed.get(2).display && timed.get(2).event90k == 90000 + 20 * 1024L,
                "timed stop");

        List<DvdSpuFrame> changed = decoder.decode(new DvdSpuPacket(0x20, 90000,
                TestData.chgColconSpu()));
        TestData.check(changed.size() == 1, "CHG_COLCON event");
        TestData.check(!changed.get(0).colorContrastTable.isEmpty(), "CHG_COLCON parsed");
        TestData.check(changed.get(0).colorContrastTable.paletteWordAt(0, 0) == 0x4444ffff,
                "CHG_COLCON palette word");
        TestData.check(changed.get(0).colorContrastTable.paletteWordAt(0, 1) == -1,
                "CHG_COLCON vertical bounds");

        byte[] invalid = TestData.simpleSpu(false);
        invalid[invalid.length > 0 ? 0 : 0] = invalid[0];
        invalid[24] = 0; invalid[25] = 8; // top offset points at control data
        List<DvdSpuFrame> rejected = decoder.decode(new DvdSpuPacket(0x20, 0, invalid));
        TestData.check(rejected.isEmpty(), "pixel offset validation");

        byte[] missingEnd = TestData.simpleSpu(false);
        missingEnd[missingEnd.length - 1] = 0x01;
        TestData.check(decoder.decode(new DvdSpuPacket(0x20, 0, missingEnd)).isEmpty(),
                "every command block requires END");
        System.out.println("PASS DvdSpuDecoderSelfTest");
    }
}

