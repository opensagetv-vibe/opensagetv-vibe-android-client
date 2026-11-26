package opensagetv.vibe.miniclient.android.ui.settings;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;
import opensagetv.vibe.miniclient.prefs.PrefStore;

public class PlaybackSettingCatalogTest {
    @Test public void allDefinitionsHaveUniqueStableKeysAndMetadata(){
        Set<String> keys=new HashSet<>();
        for(PlaybackSettingCatalog.Definition d:PlaybackSettingCatalog.ALL){
            assertTrue(keys.add(d.key));assertNotNull(d.title);assertNotNull(d.defaultValue);
            assertNotNull(d.timing);assertNotNull(d.capability);assertSame(d,PlaybackSettingCatalog.find(d.key));
            if(d.labels!=0)assertTrue(d.values!=0);
        }
        assertEquals(36,keys.size());assertNull(PlaybackSettingCatalog.find("unknown"));
    }
    @Test public void savedKeysAndExistingDefaultsAreNotRenamed(){
        assertEquals(PrefStore.Keys.disable_audio_passthrough,PlaybackSettingCatalog.AUDIO_OUTPUT.key);
        assertEquals("true",PlaybackSettingCatalog.AUDIO_OUTPUT.defaultValue); // decoded PCM remains default
        assertEquals("sync",PlaybackSettingCatalog.MEDIA3_QUEUE.defaultValue);
        assertEquals("sync",PlaybackSettingCatalog.EXO_QUEUE.defaultValue);
        assertEquals("exoplayer",PlaybackSettingCatalog.PLAYER.defaultValue);
        assertEquals("off",PlaybackSettingCatalog.PROCESSING.defaultValue);
        assertEquals("18",PlaybackSettingCatalog.SUBTITLE_SAFE.defaultValue);
    }
    @Test public void nextPlaybackOnlyFixedChoicesNeverOfferFalseSessionScope(){
        for(PlaybackSettingCatalog.Definition d:new PlaybackSettingCatalog.Definition[]{PlaybackSettingCatalog.ADJUSTMENT,PlaybackSettingCatalog.MINIMUM,PlaybackSettingCatalog.MAXIMUM}){
            assertFalse(d.session);assertEquals(PlaybackSettingCatalog.Timing.NEXT,d.timing);
        }
        assertTrue(PlaybackSettingCatalog.AUDIO_OFFSET.session);
        assertEquals(PlaybackSettingCatalog.Timing.RECONNECT,PlaybackSettingCatalog.PROCESSING.timing);
        assertEquals(PlaybackSettingCatalog.Timing.IMMEDIATE,PlaybackSettingCatalog.AUDIO_OFFSET.timing);
    }
    @Test public void numericValidationRetainsDocumentedLimits(){
        assertTrue(PlaybackSettingCatalog.AUDIO_OFFSET.validNumber("-4000"));assertTrue(PlaybackSettingCatalog.AUDIO_OFFSET.validNumber("4000"));
        assertFalse(PlaybackSettingCatalog.AUDIO_OFFSET.validNumber("4001"));assertFalse(PlaybackSettingCatalog.AUDIO_OFFSET.validNumber("NaN"));
        assertFalse(PlaybackSettingCatalog.SUBTITLE_OFFSET.validNumber("-2001"));assertTrue(PlaybackSettingCatalog.SUBTITLE_OFFSET.validNumber("2000"));
        assertTrue(PlaybackSettingCatalog.MINIMUM.validNumber("250"));assertFalse(PlaybackSettingCatalog.MINIMUM.validNumber("249"));
        assertFalse(PlaybackSettingCatalog.SUBTITLE_SAFE.validNumber("36"));assertTrue(PlaybackSettingCatalog.SUBTITLE_SCALE.validNumber("150"));
    }
    @Test public void startupApplicabilityDoesNotInventHardwareSupport(){
        assertNotNull(PlaybackSettingCatalog.defaultUnavailable(PlaybackSettingCatalog.IJK_FILTER,"media3"));
        assertNull(PlaybackSettingCatalog.defaultUnavailable(PlaybackSettingCatalog.IJK_FILTER,"ijkplayer"));
        assertNotNull(PlaybackSettingCatalog.defaultUnavailable(PlaybackSettingCatalog.MEDIA3_QUEUE,"ijkplayer"));
    }
    @Test public void runtimeCapabilitiesDisableUnsupportedControls(){
        PlaybackSettingCatalog.RuntimeSupport none=new PlaybackSettingCatalog.RuntimeSupport(false,false,false,false,false,false,false);
        assertNull(PlaybackSettingCatalog.runtimeUnavailable(PlaybackSettingCatalog.PLAYER,none));
        assertNotNull(PlaybackSettingCatalog.runtimeUnavailable(PlaybackSettingCatalog.AUDIO_OFFSET,none));
        assertNotNull(PlaybackSettingCatalog.runtimeUnavailable(PlaybackSettingCatalog.PROCESSING,none));
        PlaybackSettingCatalog.RuntimeSupport ijk=new PlaybackSettingCatalog.RuntimeSupport(true,false,true,false,false,true,false);
        assertNotNull(PlaybackSettingCatalog.runtimeUnavailable(PlaybackSettingCatalog.MEDIA3_QUEUE,ijk));
        assertNotNull(PlaybackSettingCatalog.runtimeUnavailable(PlaybackSettingCatalog.GPU_FILTER,ijk));
        assertNull(PlaybackSettingCatalog.runtimeUnavailable(PlaybackSettingCatalog.IJK_FILTER,ijk));
    }
    @Test public void configuringFutureFixedDefaultsDoesNotRequireAnActiveProducer(){
        PlaybackSettingCatalog.RuntimeSupport future=new PlaybackSettingCatalog.RuntimeSupport(false,false,false,false,false,false,true);
        assertNull(PlaybackSettingCatalog.runtimeUnavailable(PlaybackSettingCatalog.MINIMUM,future));
        assertNull(PlaybackSettingCatalog.runtimeUnavailable(PlaybackSettingCatalog.ADJUSTMENT,future));
    }
    @Test public void encoderPolicyAndCopyControlsHaveHonestApplicability(){
        assertEquals("auto",PlaybackSettingCatalog.HARDWARE_ENCODER.defaultValue);
        assertNotNull(PlaybackSettingCatalog.fixedUnavailable(PlaybackSettingCatalog.HARDWARE_ENCODER,"hls",false,"needed","software"));
        assertNull(PlaybackSettingCatalog.fixedUnavailable(PlaybackSettingCatalog.HARDWARE_ENCODER,"hls",false,"needed","hardware_only"));
        for(String mode:new String[]{"copy","off"})assertNotNull(PlaybackSettingCatalog.fixedUnavailable(PlaybackSettingCatalog.HARDWARE_ENCODER,mode,false,"needed"));
        assertEquals("server",PlaybackSettingCatalog.ENCODER.defaultValue);
        for(String mode:new String[]{"copy","off"})assertNotNull(PlaybackSettingCatalog.fixedUnavailable(PlaybackSettingCatalog.ENCODER,mode,false,"needed"));
        assertNull(PlaybackSettingCatalog.fixedUnavailable(PlaybackSettingCatalog.ENCODER,"transcode",false,"needed"));
        assertNotNull(PlaybackSettingCatalog.fixedUnavailable(PlaybackSettingCatalog.MINIMUM,"transcode",true,"needed"));
        assertNull(PlaybackSettingCatalog.fixedUnavailable(PlaybackSettingCatalog.MINIMUM,"hls",true,"needed"));
        assertNotNull(PlaybackSettingCatalog.fixedUnavailable(PlaybackSettingCatalog.AUDIO_CODEC,"copy",false,"needed"));
        assertEquals("aac",PlaybackSettingCatalog.AUDIO_CODEC.defaultValue);assertEquals("128",PlaybackSettingCatalog.AUDIO_BITRATE.defaultValue);
        for(PlaybackSettingCatalog.Definition d:PlaybackSettingCatalog.FIXED_OUTPUT){assertFalse(d.session);assertEquals(PlaybackSettingCatalog.Timing.NEXT,d.timing);}
    }
}
