package opensagetv.vibe.miniclient.video;

/** Caption delivery has a different clock budget from SageTV timeline polling. */
public final class CaptionClockCadence {
    // A 29.97fps CEA-608 source carries one field-one pair every ~33ms. A
    // 500ms timeline tick batches RU/CR/PAC and the next line into one burst,
    // starving the stock server's intermediate caption/roll-up display states.
    public static final long ACTIVE_DELAY_MS = 33L;
    public static final long IDLE_DELAY_MS = 500L;

    private CaptionClockCadence() { }

    public static long delayMs(boolean playing, boolean captionTransportActive) {
        return playing && captionTransportActive ? ACTIVE_DELAY_MS : IDLE_DELAY_MS;
    }
}
