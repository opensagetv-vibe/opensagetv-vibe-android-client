package opensagetv.vibe.miniclient.android.video;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import opensagetv.vibe.miniclient.prefs.PrefStore;
public class MimOutputSettingsTest {
    @Test public void hardwareChoiceIsExplicitImmutableAndAbsentForOldDefaults(){
        assertFalse(new MimOutputSettings(preferences(Collections.emptyMap()),"transcode").query(false).contains("hardwareBackend="));
        for(String backend:new String[]{"qsv","nvenc","vaapi","amf","videotoolbox","d3d12va"}){
            Map<String,String> p=new HashMap<>();p.put("mim_direct/hardware_backend",backend);
            MimOutputSettings value=new MimOutputSettings(preferences(p),"transcode");p.put("mim_direct/hardware_backend","auto");
            assertTrue(value.strict());assertTrue(value.query(false).contains("hardwareBackend="+backend));
        }
    }
    @Test public void copyAndSoftwareIgnoreButDoNotEraseSavedHardwareChoice(){
        Map<String,String> p=new HashMap<>();p.put("mim_direct/hardware_backend","qsv");
        assertFalse(new MimOutputSettings(preferences(p),"copy").query(true).contains("hardwareBackend="));
        p.put("mim_direct/encoder_policy","software");
        assertEquals("auto",new MimOutputSettings(preferences(p),"hls").hardwareBackend);assertEquals("qsv",p.get("mim_direct/hardware_backend"));
    }
    @Test public void corruptedHardwareChoiceCannotInjectQueryParameters(){
        MimOutputSettings value=new MimOutputSettings(preferences(Collections.singletonMap("mim_direct/hardware_backend","qsv&encoderPolicy=software")),"hls");
        assertFalse(value.query(false).contains("&encoderPolicy=software"));assertTrue(value.strict());
    }
    PrefStore preferences(Map<String,String> changes){
        Map<String,String> values=new HashMap<String,String>();
        values.put("getFixedEncodingVideoResolution","SOURCE");values.put("getFixedEncodingFPS","SOURCE");
        values.put("getFixedEncodingAudioCodec","aac");values.put("getFixedEncodingAudioChannels","");
        values.put("getFixedRemuxingPreference","needed");values.put("getFixedRemuxingFormat","mpegts");values.put("getFixedEncodingContainerFormat","mpegts");values.putAll(changes);
        return (PrefStore)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PrefStore.class},(proxy,method,args)->
            "getString".equals(method.getName())?values.getOrDefault(String.valueOf(args[0]),String.valueOf(args[1])):values.get(method.getName()));
    }
    @Test public void newDefaultsPreserveStoredAudioDefaults(){MimOutputSettings value=new MimOutputSettings(preferences(Collections.emptyMap()),"transcode");assertFalse(value.strict());assertTrue(value.query(false).contains("audioCodec=aac"));assertTrue(value.query(false).contains("audioKbps=128"));}
    @Test public void eachExplicitEncoderPolicyRequiresNegotiation(){for(String policy:new String[]{"prefer_hardware","software","hardware_only"}){MimOutputSettings value=new MimOutputSettings(preferences(Collections.singletonMap("mim_direct/encoder_policy",policy)),"transcode");assertTrue(value.strict());assertTrue(value.query(false).contains("encoderPolicy="+policy));}}
    @Test public void copyNeverRequestsAnAudioOrVideoEncode(){Map<String,String> values=new HashMap<>();values.put("mim_direct/encoder_policy","hardware_only");values.put("fixed_encoding/video_codec","hevc");values.put("getFixedEncodingAudioCodec","source");MimOutputSettings value=new MimOutputSettings(preferences(values),"copy");assertFalse(value.query(true).contains("audioCodec="));assertFalse(value.query(true).contains("videoCodec="));assertFalse(value.strict());}
    @Test public void sourceAudioChoicesRemainExplicit(){Map<String,String> values=new HashMap<>();values.put("getFixedEncodingAudioCodec","source");values.put("fixed_encoding/audio_bitrate_kbps","source");MimOutputSettings value=new MimOutputSettings(preferences(values),"transcode");assertTrue(value.query(false).contains("audioCodec=source"));assertTrue(value.query(false).contains("audioKbps=source"));assertTrue(value.strict());}
    @Test public void illegalContainersAndAudioAreNotSilentlyIgnored(){Map<String,String> values=new HashMap<>();values.put("getFixedEncodingContainerFormat","matroska");assertFalse(new MimOutputSettings(preferences(values),"transcode").validationError(false).isEmpty());values.put("getFixedEncodingContainerFormat","mpegts");values.put("getFixedEncodingAudioCodec","mp2");values.put("getFixedEncodingAudioChannels","6");assertFalse(new MimOutputSettings(preferences(values),"transcode").validationError(false).isEmpty());}
}
