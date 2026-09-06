package opensagetv.vibe.miniclient.dvd;

public final class DvdPtsClockSelfTest {
    public static void main(String[] args) {
        DvdPtsClock clock = new DvdPtsClock();
        clock.onNewCell(45000);
        TestData.check(clock.mapPesPts(90000) == 180000, "NEWCELL PTS offset");

        long nearWrap = DvdPtsClock.MODULO - 100;
        clock.updateExactClock90k(nearWrap);
        long unwrapped = clock.mapPesPts(DvdPtsClock.MODULO + 50 - 90000); // +90000 => wrapped 50
        TestData.check(unwrapped == DvdPtsClock.MODULO + 50, "33-bit PTS unwrap");

        clock.resetAll();
        clock.setServerStc45k(45000);
        clock.updatePlayerClockMillis(1000);
        TestData.check(clock.updatePlayerClockMillis(1500) == 135000, "player/STC anchor");
        System.out.println("PASS DvdPtsClockSelfTest");
    }
}
