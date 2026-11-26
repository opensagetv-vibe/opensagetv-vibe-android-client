import hashlib,unittest,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video'
ORIGINALS={'extension-media3-ffmpeg-1.11.0.aar':'62f16d106c6ea04905d63d4663877def776887cc1915105b486cb22cd4c53f07',
           'extension-ffmpeg-2.18.0.aar':'e9e34c833298c1177247b3f7cfef8e8be45035ff4f8076d667b8f5c9dc9c4b12'}

class AacPceClientTest(unittest.TestCase):
    def test_shared_client_parser_is_bounded_without_framework_or_network(self):
        source=(BASE/'AacProgramConfig.java').read_text()
        for token in ('asc.length == 2','<13','tracks.size()>=8','new byte[512]','channels>8','bits.read+7','bits.write&7','size==offered','offset==0','!encrypted'):
            self.assertIn(token,source)
        for forbidden in ('import android.','import androidx.','java.net.','static final Store','System.out','System.err'):
            self.assertNotIn(forbidden,source)

    def test_playback_owned_state_survives_decoder_rebuild_not_new_load(self):
        for folder,name in [('media3','Media3'),('exoplayer2','Exo2')]:
            base=BASE/folder
            player=(base/(name+'MediaPlayerImpl.java')).read_text()
            load=player.index('public void load(');end=player.index('super.load(',load)
            self.assertIn('aacProgramConfigStore = new ',player[load:end])
            self.assertIn('AacProgramConfig.Store',player)
            self.assertIn('.setExtractorFactory(new AacPceHlsExtractorFactory(aacProgramConfigStore))',player)
            self.assertIn('else if (isMimDirectMediaUrlActive() && mimDirectController().owns(this))\n                player.setMediaSource(mediaSource, 0L);',player)
            self.assertIn('.rendererFormat.sampleMimeType : ""',player)
            factory=(base/(name+'AudioExtensionRenderersFactory.java')).read_text()
            self.assertNotIn('setProgramConfigStore',factory)
        probe=(ROOT/'source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/PlaybackHealthProbe.java').read_text()
        self.assertIn('out.audioOutputChannels = readIntField(decoder, "channelCount", -1)',probe)
    def test_original_audio_archives_stay_byte_identical(self):
        import xml.etree.ElementTree as ET
        metadata=ET.parse(ROOT/'source/dev/gradle/verification-metadata.xml')
        for name,original in ORIGINALS.items():
            path=ROOT/'source/dev/libs'/name;digest=hashlib.sha256(path.read_bytes()).hexdigest()
            self.assertEqual(original,digest)
            artifact=next(n for n in metadata.iter() if n.tag.rsplit('}',1)[-1]=='artifact' and n.get('name')==name.replace('.aar','-.aar'))
            self.assertEqual([n.get('value') for n in artifact if n.tag.rsplit('}',1)[-1]=='sha256'],[digest])
            self.assertFalse(any(n.tag.rsplit('}',1)[-1]=='also-trust' for n in artifact.iter()))
            with zipfile.ZipFile(path) as archive:self.assertIsNone(archive.testzip())
        for label in ('media3','exoplayer'):
            self.assertNotIn('rebuild_aac_pce_extensions.py',(ROOT/'source/dev'/label/'buildffmpegext.sh').read_text())

    def test_both_adapters_preserve_payload_and_metadata_and_only_observe_main_audio(self):
        for folder in ('media3','exoplayer2'):
            source=(BASE/folder/'AacPceHlsExtractorFactory.java').read_text()
            for token in ('type==C.TRACK_TYPE_AUDIO','"audio/mp4a-latm".equals','part==SAMPLE_DATA_PART_MAIN',
                          'target.sampleData(input,length,part)','target.sampleMetadata(time,flags,size,offset,crypto)',
                          'sample.finish(size,offset,crypto!=null)','config.channels','store.find(value.id,header)'):
                self.assertIn(token,source)
        source=(BASE/'media3/AacPceHlsExtractorFactory.java').read_text()
        for token in ('delegate.setSubtitleParserFactory','delegate.experimentalParseSubtitlesDuringExtraction',
                      'delegate.experimentalSetCodecsToParseWithinGopSampleDependencies','delegate.getOutputTextFormat','target.durationUs(duration)'):
            self.assertIn(token,source)
if __name__=='__main__':unittest.main()
