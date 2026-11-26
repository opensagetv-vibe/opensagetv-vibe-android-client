package opensagetv.vibe.miniclient.android.video.exoplayer2;

import android.net.Uri;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.ts.PsExtractor;
import com.google.android.exoplayer2.extractor.ts.TsExtractor;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Retains only a successfully sniffed MPEG container for this Push source.
 * A FIFO cannot replay its initial header after a fatal renderer/parser retry.
 * TS/PS readers can resynchronize at subsequent packets; their normal read()
 * still validates data. Never guess a container, manufacture bytes or rewind.
 */
final class PushRecoveryExtractorsFactory implements ExtractorsFactory
{
    interface Eligibility { boolean canResume(Extractor extractor); }

    private final ExtractorsFactory delegate;
    private final Eligibility eligibility;
    private Class<?> identifiedContainer;
    private boolean recovering;

    PushRecoveryExtractorsFactory(ExtractorsFactory delegate)
    {
        this(delegate, extractor -> extractor instanceof TsExtractor
                || extractor instanceof PsExtractor);
    }

    // Injection keeps tests independent of Android platform extractor internals.
    PushRecoveryExtractorsFactory(ExtractorsFactory delegate, Eligibility eligibility)
    {
        this.delegate = delegate;
        this.eligibility = eligibility;
    }

    synchronized boolean beginRecovery()
    {
        if (identifiedContainer == null) return false;
        recovering = true;
        return true;
    }

    @Override public Extractor[] createExtractors()
    {
        return wrap(delegate.createExtractors());
    }

    @Override public Extractor[] createExtractors(Uri uri, Map<String, List<String>> headers)
    {
        return wrap(delegate.createExtractors(uri, headers));
    }

    private synchronized Extractor[] wrap(Extractor[] extractors)
    {
        if (recovering)
        {
            Extractor selected = null;
            for (Extractor extractor : extractors)
            {
                if (selected == null && extractor.getClass() == identifiedContainer)
                    selected = extractor;
                else
                    extractor.release();
            }
            // Do not fall back to guessing a different format during recovery.
            return selected == null ? new Extractor[0]
                    : new Extractor[] {new RememberingExtractor(selected, true)};
        }
        Extractor[] result = new Extractor[extractors.length];
        for (int i = 0; i < extractors.length; i++)
            result[i] = new RememberingExtractor(extractors[i], false);
        return result;
    }

    private final class RememberingExtractor implements Extractor
    {
        private final Extractor extractor;
        private final boolean resume;

        RememberingExtractor(Extractor extractor, boolean resume)
        {
            this.extractor = extractor;
            this.resume = resume;
        }

        @Override public boolean sniff(ExtractorInput input) throws IOException
        {
            if (resume) return true;
            boolean accepted = extractor.sniff(input);
            if (accepted && eligibility.canResume(extractor))
            {
                synchronized (PushRecoveryExtractorsFactory.this)
                {
                    if (identifiedContainer == null)
                        identifiedContainer = extractor.getClass();
                }
            }
            return accepted;
        }

        @Override public void init(ExtractorOutput output) { extractor.init(output); }
        @Override public int read(ExtractorInput input, PositionHolder holder) throws IOException
        { return extractor.read(input, holder); }
        @Override public void seek(long position, long timeUs) { extractor.seek(position, timeUs); }
        @Override public void release() { extractor.release(); }
    }
}
