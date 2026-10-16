"""Bounded native/software-only field correction and truthful control scope."""
from pathlib import Path
import hashlib
import unittest
import zipfile
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
NATIVE = ROOT / 'source/dev/ijkplayer/native'
JAVA = ROOT / 'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android'

class IjkDeinterlaceTests(unittest.TestCase):
    def test_progressive_bypass_and_seek_serial_history_reset(self):
        text = (NATIVE / 'vibe_deinterlace.h').read_text(encoding='utf-8')
        self.assertIn('if (!frame->interlaced_frame) return 1;', text)
        self.assertIn('avfilter_register_all();', text)
        self.assertIn('if (!buffer || !deinterlacer || !sink)', text)
        self.assertIn('filter->serial != serial', text)
        self.assertIn('vibe_deinterlace_close(filter)', text)
        self.assertIn('mode=send_frame:parity=auto:deint=interlaced', text)
        self.assertIn('av_rescale_q(frame->pts', text)
        self.assertIn('av_buffersink_get_time_base(filter->sink), time_base', text)
        self.assertIn('drain ? NULL : frame', text)

    def test_native_patch_does_not_enable_legacy_audio_filter_path(self):
        text = (NATIVE / 'software-deinterlace.patch').read_text(encoding='utf-8')
        self.assertNotIn('define CONFIG_AVFILTER 1', text)
        self.assertIn('is->viddec.finished != is->viddec.pkt_serial', text)
        self.assertIn('vibe_deinterlace_close(&vibe_filter)', text)
        self.assertIn('OPTION_INT(0, 0, 1)', text)
        self.assertIn('"vibe-deinterlace-filter"', text)
        self.assertIn('OPTION_INT(0, 0, 2)', text)
        self.assertIn('vibe_filter.fast_active ? 2', text)
        self.assertNotIn('mode=send_field', text)

    def test_annex_b_parameter_sets_are_supplied_to_mediacodec(self):
        text = (NATIVE / 'software-deinterlace.patch').read_text(encoding='utf-8')
        self.assertIn('opaque->codecpar->codec_id == AV_CODEC_ID_H264 ||', text)
        self.assertIn('opaque->codecpar->codec_id == AV_CODEC_ID_HEVC', text)
        self.assertIn('SDL_AMediaFormat_setBuffer(opaque->input_aformat, "csd-0",', text)
        self.assertIn('opaque->codecpar->extradata_size);', text)
        self.assertIn('csd-0: Annex-B parameter sets', text)
        self.assertIn('decoder opened on a mid-GOP packet', text)

    def test_filter_menu_is_capability_scoped_and_returns_to_its_parent(self):
        text = (JAVA / 'ActivePlayerAdjustmentsDialog.java').read_text(encoding='utf-8')
        self.assertIn('"Video filters", videoFilterValue()', text)
        self.assertIn('hasClientFilterChoice()', text)
        self.assertIn('this backend does not expose algorithm selection', text)
        self.assertIn('HD may be slow', text)
        self.assertIn('setIjkDeinterlaceFilter(value)', text)
        self.assertIn('showVideoFilters();', text)
        decoder = (JAVA / 'video/ijkplayer/IjkDecoderOptions.java').read_text(encoding='utf-8')
        self.assertIn('"vibe-deinterlace-filter"', decoder)
        self.assertIn('DeinterlacePolicy.nativeFilter(', decoder)

    def test_hd_spatial_path_keeps_first_field_and_never_changes_pts(self):
        text = (NATIVE / 'vibe_deinterlace.h').read_text(encoding='utf-8')
        fast = text[text.index('static int vibe_deinterlace_fast_hd'):text.index('/* Return one output frame')]
        self.assertIn('av_frame_make_writable(frame)', fast)
        self.assertIn('frame->top_field_first ? 0 : 1', fast)
        self.assertIn('row += 2', fast)
        self.assertNotIn('frame->pts =', fast)
        self.assertIn('frame->interlaced_frame = 0', fast)
        self.assertIn('frame->format == AV_PIX_FMT_YUV420P', text)

    @unittest.skipUnless(shutil.which('cc'), 'native pixel test runs in unified build container')
    def test_first_field_interpolation_preserves_pixels_pts_and_padding(self):
        header = (NATIVE / 'vibe_deinterlace.h').read_text(encoding='utf-8')
        function = header[header.index('static int vibe_deinterlace_fast_hd'):header.index('/* Return one output frame')]
        harness = '''#include <stdint.h>
#include <assert.h>
#include <string.h>
typedef struct { int width,height,top_field_first,linesize[3],interlaced_frame; long pts; uint8_t *data[3]; } AVFrame;
static int av_frame_make_writable(AVFrame *f) { return 0; }
''' + function + '''
int main(void) {
  for(int parity=0;parity<2;parity++) {
    uint8_t data[3][180], original[3][180];
    AVFrame f={.width=10,.height=9,.top_field_first=!parity,.linesize={20,20,20},.interlaced_frame=1,.pts=123456789};
    for(int p=0;p<3;p++) {
      f.data[p]=data[p]; memset(data[p],231,180);
      int w=p?5:10,h=p?5:9;
      for(int y=0;y<h;y++) for(int x=0;x<w;x++) data[p][y*20+x]=(uint8_t)(y*20+x);
      memcpy(original[p],data[p],180);
    }
    assert(vibe_deinterlace_fast_hd(&f)==1); assert(f.pts==123456789 && f.interlaced_frame==0);
    for(int p=0;p<3;p++) {
      int w=p?5:10,h=p?5:9;
      for(int y=0;y<h;y++) for(int x=0;x<20;x++) {
        unsigned expected=original[p][y*20+x];
        if(x<w && (y%2)!=parity) {
          int a=y-1,b=y+1; if(a<0)a=b; if(b>=h)b=a;
          expected=((unsigned)original[p][a*20+x]+original[p][b*20+x]+1)>>1;
        }
        assert(data[p][y*20+x]==expected);
      }
    }
  }
  return 0;
}
'''
        with tempfile.TemporaryDirectory(prefix='vibe-pixel-test-') as directory:
            source = Path(directory)/'pixels.c'; executable = Path(directory)/'pixels'
            source.write_text(harness, encoding='utf-8')
            subprocess.run(['cc','-O2','-fsanitize=address,undefined',str(source),'-o',str(executable)],check=True,capture_output=True)
            subprocess.run([str(executable)],check=True,capture_output=True)

    def test_java_option_and_saved_auto_off_setting(self):
        text = (JAVA / 'video/ijkplayer/IjkDecoderOptions.java').read_text(encoding='utf-8')
        self.assertIn('"vibe-software-deinterlace"', text)
        self.assertIn('DeinterlacePolicy.clientSoftwareEnabled(', text)
        self.assertIn('CodecSelector.isKnownBrokenMpeg2HardwareDevice()', text)
        overrides = (JAVA / 'video/ActivePlayerSessionOverrides.java').read_text(encoding='utf-8')
        self.assertEqual(overrides.count('ijkSoftwareDeinterlace = null;'), 2)
        self.assertIn('|| ijkSoftwareDeinterlace != null', overrides)

    def test_menu_does_not_claim_mim_on_for_native_pull(self):
        text = (JAVA / 'ActivePlayerAdjustmentsDialog.java').read_text(encoding='utf-8')
        self.assertNotIn('"MIM deinterlace"', text)
        self.assertIn('new String[] { "Auto", "Off", "Client", "Client GPU", "MIM" }', text)
        self.assertIn('new String[] { "Auto", "Off", "Client", "Client GPU" }', text)
        self.assertIn('"fixed".equals(client.properties().getStreamingMode())', text)
        self.assertIn('ClientDeinterlaceStatus.implementation(', text)
        self.assertIn('ClientDeinterlaceStatus.current(', text)
        self.assertIn('"IJKPlayer (next: "', text)

    def test_each_hardware_engine_honors_client_without_changing_audio(self):
        for relative in ('video/media3/Media3CodecSelector.java',
                         'video/exoplayer2/CustomMediaCodecSelector.java'):
            text = (JAVA / relative).read_text(encoding='utf-8')
            self.assertLess(text.index('if (!AndroidCodecPolicy.isVideoMimeType'),
                            text.index('DeinterlacePolicy.requiresDeviceMpeg2('))
            self.assertIn('switch (clientFields ? DecodingMethod.HARDWARE : decodingMethod)', text)
        text = (JAVA / 'video/ClientDeinterlaceStatus.java').read_text(encoding='utf-8')
        self.assertIn('getDelegateForDebug()', text)
        self.assertIn('isSoftwareDeinterlaceActive()', text)
        self.assertIn('"Unavailable" : "Device"', text)
        self.assertIn('"Device controlled" : "Unknown"', text)

    def test_mim_status_uses_accepted_request_not_pending_preference(self):
        text = (JAVA / 'video/MimDirectSessionClient.java').read_text(encoding='utf-8')
        self.assertEqual(text.count('acceptedDeinterlace = deinterlace;'), 2)
        self.assertIn('acceptedDeinterlace = "";', text)
        self.assertIn('MimDirectTransportPolicy.TRANSCODE.equals(activeMode)', text)
        menu = (JAVA / 'ActivePlayerAdjustmentsDialog.java').read_text(encoding='utf-8')
        self.assertIn('"ready_transcode".equals(server) || !accepted.isEmpty()', menu)
        self.assertNotIn('"active_transcode".equals(server)', menu)
        self.assertIn('current setting unchanged', menu)

    def test_mcp_snapshot_does_not_drop_actual_filter_state(self):
        text = (ROOT / 'mcp/src/sagetv_dev_mcp/server.py').read_text(encoding='utf-8')
        compact = text[text.index('def _compact_state'):text.index('def _compact_state')+22000]
        for key in ('videoDeinterlaceMode', 'videoDeinterlaceState',
                    'ijkSoftwareDeinterlaceActive', 'mimAcceptedDeinterlace'):
            self.assertIn('"'+key+'"', compact)

    def test_both_abis_keep_ffmpeg_sdl_and_have_the_native_option(self):
        expected = {
            'armv7a': ('armeabi-v7a', '93dfb2f6041df20b5ce12023be946a1ff5d051cefcb9715087ba07f9ede68197', 'ca4c171d2ad383cf4f6e3c069050f6b5dceeca353955d622c00091f433276c69'),
            'arm64': ('arm64-v8a', '276cd96063b50aa008e62161450cc6a0c73eee65e2d5bece6aec4c3f70b6930a', '0f75ae11ae419bfe882479369f4167629e0452b2e4925712832eb0e7085ce86b'),
        }
        for short, (abi, ffmpeg, sdl) in expected.items():
            with zipfile.ZipFile(ROOT / f'source/dev/libs/ijkplayer-{short}-0.8.8-SNAPSHOT.aar') as package:
                self.assertEqual(hashlib.sha256(package.read(f'jni/{abi}/libijkffmpeg.so')).hexdigest(), ffmpeg)
                self.assertEqual(hashlib.sha256(package.read(f'jni/{abi}/libijksdl.so')).hexdigest(), sdl)
                self.assertIn(b'vibe-software-deinterlace', package.read(f'jni/{abi}/libijkplayer.so'))

if __name__ == '__main__':
    unittest.main()
