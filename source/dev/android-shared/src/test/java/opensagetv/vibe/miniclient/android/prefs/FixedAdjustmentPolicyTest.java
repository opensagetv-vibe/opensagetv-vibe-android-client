package opensagetv.vibe.miniclient.android.prefs;

import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;
import opensagetv.vibe.miniclient.prefs.PrefStore;

public class FixedAdjustmentPolicyTest {
    private SharedPreferences memory(Map<String,Object> values) {
        SharedPreferences.Editor editor=(SharedPreferences.Editor)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{SharedPreferences.Editor.class},(p,m,a)->{
            if(m.getName().startsWith("put")){values.put((String)a[0],a[1]);return p;}
            if(m.getName().equals("apply"))return null;
            if(m.getName().equals("commit"))return true;
            throw new AssertionError(m.getName());
        });
        return (SharedPreferences)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{SharedPreferences.class},(p,m,a)->{
            if(m.getName().equals("edit"))return editor;
            if(m.getName().equals("getAll"))return new HashMap<>(values);
            if(m.getName().equals("getString")||m.getName().equals("getBoolean"))return values.getOrDefault((String)a[0],a[1]);
            throw new AssertionError(m.getName());
        });
    }
    @Test public void allFlagCombinationsHaveOneSelection(){
        assertEquals("off",FixedAdjustmentPolicy.selection(false,false));
        assertEquals("off",FixedAdjustmentPolicy.selection(false,true));
        assertEquals("switch",FixedAdjustmentPolicy.selection(true,false));
        assertEquals("in_place",FixedAdjustmentPolicy.selection(true,true));
    }
    @Test public void onlyThreeChoicesAreAccepted(){
        assertTrue(FixedAdjustmentPolicy.valid("off"));assertTrue(FixedAdjustmentPolicy.valid("switch"));assertTrue(FixedAdjustmentPolicy.valid("in_place"));
        assertFalse(FixedAdjustmentPolicy.valid(null));assertFalse(FixedAdjustmentPolicy.valid("auto"));
    }
    @Test public void choiceSetsStablePlaybackFlagsWithoutChangingTransport(){
        Map<String,Object> values=new HashMap<>();values.put("streaming_mode","dynamic");values.put("password","private-fixture");
        SharedPreferences saved=memory(values);
        for(String choice:new String[]{"off","switch","in_place"}){
            AndroidPrefStore.setAdjustmentMethod(saved,choice);assertEquals(choice,AndroidPrefStore.adjustmentMethod(saved));
            assertEquals("dynamic",values.get("streaming_mode"));assertEquals("private-fixture",values.get("password"));
        }
    }
    @Test public void legacyAdjustedMigratesOnceWithoutReplacingProcessingOrBounds(){
        Map<String,Object> values=new HashMap<>();values.put("streaming_mode","fixed_adjusted");
        values.put(PrefStore.Keys.mim_direct_mode,"hls");values.put(PrefStore.Keys.mim_direct_runtime_rate,true);
        values.put("fixed_encoding/video_bitrate_kbps","4000");SharedPreferences saved=memory(values);
        AndroidPrefStore.migrateTransportSelection(saved);assertEquals("fixed",values.get("streaming_mode"));
        assertEquals("in_place",AndroidPrefStore.adjustmentMethod(saved));assertEquals("hls",values.get(PrefStore.Keys.mim_direct_mode));
        assertEquals("4000",values.get("fixed_encoding/video_bitrate_kbps"));
        Map<String,Object> after=new HashMap<>(values);AndroidPrefStore.migrateTransportSelection(saved);assertEquals(after,values);
    }
    @Test public void pushPullFixedAndSmbAreNotRewritten(){
        for(String mode:new String[]{"dynamic","pull","fixed","smb_direct","smb_auto"}){
            Map<String,Object> values=new HashMap<>();values.put("streaming_mode",mode);values.put(PrefStore.Keys.mim_direct_adaptive_quality,true);
            Map<String,Object> before=new HashMap<>(values);AndroidPrefStore.migrateTransportSelection(memory(values));assertEquals(before,values);
        }
    }
    @Test public void unknownChoiceDoesNotWriteAnything(){
        Map<String,Object> values=new HashMap<>();SharedPreferences saved=memory(values);
        try{AndroidPrefStore.setAdjustmentMethod(saved,"guess");fail();}catch(IllegalArgumentException expected){}
        assertTrue(values.isEmpty());
    }
    @Test public void resetProtectsPersistentIdentityAndSecrets(){
        for(String key:new String[]{"clientid","client_id","server_id","smb_direct/password","smb_profiles/name","core_token","settings/section/playback/expanded"})
            assertTrue(key,opensagetv.vibe.miniclient.android.ui.settings.SettingsDefaults.protectedKey(key));
        for(String key:new String[]{"streaming_mode","default_player","fixed_encoding/video_bitrate_kbps","mim_direct/mode"})
            assertFalse(key,opensagetv.vibe.miniclient.android.ui.settings.SettingsDefaults.protectedKey(key));
    }
    @Test public void freshInstallGetsUserApprovedDefaults(){
        Map<String,Object> values=new HashMap<>();AndroidPrefStore.migrateMenuDefaults(memory(values));
        assertEquals(true,values.get(PrefStore.Keys.use_opengl_ui));assertEquals(true,values.get(PrefStore.Keys.unified_graphics_surfaces));
    }
    @Test public void upgradePreservesExplicitOffAndOn(){
        for(boolean value:new boolean[]{false,true}){
            Map<String,Object> values=new HashMap<>();values.put(PrefStore.Keys.use_opengl_ui,value);values.put(PrefStore.Keys.unified_graphics_surfaces,value);
            values.put("clientid","private-test-identity");AndroidPrefStore.migrateMenuDefaults(memory(values));
            assertEquals(value,values.get(PrefStore.Keys.use_opengl_ui));assertEquals(value,values.get(PrefStore.Keys.unified_graphics_surfaces));
            assertEquals("private-test-identity",values.get("clientid"));
        }
    }
    @Test public void upgradeMissingKeysUseCurrentDefaultsAndMigrationIsIdempotent(){
        Map<String,Object> values=new HashMap<>();values.put("streaming_mode","dynamic");SharedPreferences saved=memory(values);
        AndroidPrefStore.migrateMenuDefaults(saved);assertEquals(true,values.get(PrefStore.Keys.use_opengl_ui));assertEquals(true,values.get(PrefStore.Keys.unified_graphics_surfaces));
        Map<String,Object> after=new HashMap<>(values);AndroidPrefStore.migrateMenuDefaults(saved);assertEquals(after,values);
    }
    @Test public void missingUpgradeValueUsesDefaultWithoutChangingOtherExplicitChoice(){
        for(String missing:new String[]{PrefStore.Keys.use_opengl_ui,PrefStore.Keys.unified_graphics_surfaces}){
            String retained=missing.equals(PrefStore.Keys.use_opengl_ui)?PrefStore.Keys.unified_graphics_surfaces:PrefStore.Keys.use_opengl_ui;
            Map<String,Object> values=new HashMap<>();values.put(retained,false);
            values.put("settings/menu_defaults_revision",1);
            AndroidPrefStore.migrateMenuDefaults(memory(values));
            assertEquals(true,values.get(missing));assertEquals(false,values.get(retained));
        }
    }
    @Test public void mixedExplicitGraphicsChoicesAreNeverResetOnUpgrade(){
        Map<String,Object> values=new HashMap<>();values.put(PrefStore.Keys.use_opengl_ui,true);
        values.put(PrefStore.Keys.unified_graphics_surfaces,false);values.put("settings/menu_defaults_revision",1);
        Map<String,Object> before=new HashMap<>(values);AndroidPrefStore.migrateMenuDefaults(memory(values));assertEquals(before,values);
    }
}
