package opensagetv.vibe.miniclient.video;

import java.util.Locale;

/**
 * Classifies player failures before presentation or recovery is selected.
 *
 * <p>This class deliberately depends on error names rather than either Exo
 * generation so Media3 and legacy ExoPlayer make the same decision. It never
 * infers a decoder failure from a datasource or container error.</p>
 */
public final class PlaybackFailureClassifier
{
    public enum Kind
    {
        DATASOURCE,
        CONTAINER,
        DECODER_INITIALIZATION,
        DECODER_RUNTIME,
        AUDIO_OUTPUT,
        UNKNOWN
    }

    public enum Recovery
    {
        NONE,
        REATTACH_SOURCE,
        REPREPARE_PLAYER,
        REBUILD_PLAYER_WITH_DECODER_EXCLUSION
    }

    public static final class Decision
    {
        public final Kind kind;
        public final Recovery recovery;
        public final boolean showToUser;
        public final boolean transitionStale;
        public final boolean mayExcludeVideoDecoder;
        public final int maxAutomaticAttempts;

        Decision(Kind kind, Recovery recovery, boolean showToUser,
                boolean transitionStale, boolean mayExcludeVideoDecoder,
                int maxAutomaticAttempts)
        {
            this.kind = kind;
            this.recovery = recovery;
            this.showToUser = showToUser;
            this.transitionStale = transitionStale;
            this.mayExcludeVideoDecoder = mayExcludeVideoDecoder;
            this.maxAutomaticAttempts = maxAutomaticAttempts;
        }
    }

    private PlaybackFailureClassifier()
    {
    }

    public static Decision classify(String errorCodeName, String causeClassName,
            String causeMessage, boolean firstVideoFrameRendered,
            boolean seekPending, boolean flushPending, boolean tearingDown)
    {
        String code = normalize(errorCodeName);
        String cause = normalize(causeClassName) + " " + normalize(causeMessage);
        Kind kind = classifyKind(code, cause);

        if (tearingDown)
            return new Decision(kind, Recovery.NONE, false, true, false, 0);

        boolean activeTransition = seekPending || flushPending;
        boolean stale = activeTransition && firstVideoFrameRendered
                && (kind == Kind.CONTAINER || kind == Kind.DECODER_RUNTIME
                || code.contains("TIMEOUT"));
        boolean show = !stale && (kind != Kind.CONTAINER
                || PlaybackErrorPresentationPolicy.shouldShowFirstError(
                        errorCodeName, firstVideoFrameRendered));

        switch (kind)
        {
            case DATASOURCE:
                return new Decision(kind, Recovery.REATTACH_SOURCE, show,
                        false, false, 2);
            case CONTAINER:
                return new Decision(kind, Recovery.REATTACH_SOURCE, show,
                        stale, false, 2);
            case DECODER_INITIALIZATION:
                // Media3/Exo initialization fallback already walked every
                // policy-eligible candidate. Repeating the same list cannot
                // make a failed initialization valid.
                return new Decision(kind, Recovery.NONE, show,
                        false, false, 0);
            case DECODER_RUNTIME:
                return new Decision(kind,
                        Recovery.REBUILD_PLAYER_WITH_DECODER_EXCLUSION,
                        show, stale, true, 1);
            case AUDIO_OUTPUT:
                return new Decision(kind, Recovery.REPREPARE_PLAYER, show,
                        false, false, 1);
            case UNKNOWN:
            default:
                return new Decision(kind, Recovery.REPREPARE_PLAYER, show,
                        stale, false, 1);
        }
    }

    private static Kind classifyKind(String code, String cause)
    {
        if (code.contains("DECODER_INIT") || cause.contains("DECODERINITIALIZATION"))
            return Kind.DECODER_INITIALIZATION;
        if (code.contains("DECODING_FAILED") || code.contains("DECODER_FAILED")
                || cause.contains("MEDIACODEC$CODECEXCEPTION")
                || cause.contains("MEDIACODECCODECEXCEPTION"))
            return Kind.DECODER_RUNTIME;
        if (code.contains("AUDIO_TRACK") || cause.contains("AUDIOTRACK"))
            return Kind.AUDIO_OUTPUT;
        if (code.contains("PARSING_") || code.contains("MALFORMED")
                || code.contains("CONTAINER") || cause.contains("PARSEREXCEPTION")
                || cause.contains("UNRECOGNIZEDINPUTFORMAT"))
            return Kind.CONTAINER;
        if (code.contains("IO_") || code.contains("NETWORK")
                || code.contains("SOURCE") || cause.contains("IOEXCEPTION")
                || cause.contains("HTTP") || cause.contains("SOCKET"))
            return Kind.DATASOURCE;
        return Kind.UNKNOWN;
    }

    private static String normalize(String value)
    {
        return value == null ? "" : value.toUpperCase(Locale.US);
    }
}
