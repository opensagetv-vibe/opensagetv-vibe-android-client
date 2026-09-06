package opensagetv.vibe.miniclient.dvd;

import java.util.ArrayList;
import java.util.List;

public final class DvdSpuAssemblerSelfTest {
    public static void main(String[] args) {
        byte[] packet = TestData.simpleSpu(false);
        DvdDiagnostics diagnostics = new DvdDiagnostics();
        DvdSpuAssembler assembler = new DvdSpuAssembler(diagnostics);
        ArrayList<DvdSpuPacket> output = new ArrayList<DvdSpuPacket>();
        output.addAll(assembler.feed(new DvdSpuPacket(0x20, 90000, slice(packet, 0, 5))));
        output.addAll(assembler.feed(new DvdSpuPacket(0x20, -1, slice(packet, 5, packet.length - 5))));
        TestData.check(output.size() == 1, "fragment reassembly");
        TestData.check(output.get(0).pts90k == 90000, "first-fragment PTS");
        TestData.check(output.get(0).data.length == packet.length, "packet size");

        List<DvdSpuPacket> two = assembler.feed(new DvdSpuPacket(0x21, 100,
                TestData.concat(packet, packet)));
        TestData.check(two.size() == 2, "two packets in one PES");
        TestData.check(two.get(0).pts90k == 100 && two.get(1).pts90k == 100,
                "one PES PTS applies to both packets");

        assembler.reset();
        int split = 5;
        List<DvdSpuPacket> first = assembler.feed(new DvdSpuPacket(
                0x22, 100, slice(packet, 0, split)));
        TestData.check(first.isEmpty(), "split first packet incomplete");
        byte[] restAndSecond = TestData.concat(
                slice(packet, split, packet.length - split), packet);
        List<DvdSpuPacket> marked = assembler.feed(new DvdSpuPacket(0x22, 200, restAndSecond));
        TestData.check(marked.size() == 2, "PTS marker across packet boundary");
        TestData.check(marked.get(0).pts90k == 100, "first packet keeps original PTS");
        TestData.check(marked.get(1).pts90k == 200, "second packet receives newer PES PTS");
        System.out.println("PASS DvdSpuAssemblerSelfTest");
    }

    private static byte[] slice(byte[] source, int offset, int length) {
        byte[] result = new byte[length];
        System.arraycopy(source, offset, result, 0, length);
        return result;
    }
}

