package opensagetv.vibe.miniclient.dvd;

/** Thrown when a DVD protocol or SPU packet is structurally invalid. */
public final class DvdProtocolException extends Exception {
    private static final long serialVersionUID = 1L;
    public DvdProtocolException(String message) {
        super(message);
    }
}

