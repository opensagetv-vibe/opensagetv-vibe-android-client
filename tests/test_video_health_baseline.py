"""Compile actual pure debug probe methods; no Android/server/device mutation."""
from pathlib import Path
import shutil,subprocess,tempfile,unittest
ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/PlaybackHealthProbe.java'

def method(text,name):
    # Locate the exact declaration, not an earlier unrelated method.
    start=text.rfind('    static ',0,text.index(name)+1)
    brace=text.index('{',text.index(name));depth=1;end=brace+1
    while depth:
        depth+=(text[end]=='{')-(text[end]=='}');end+=1
    return text[start:end]

class Baseline(unittest.TestCase):
    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'Run in reusable JDK container')
    def test_actual_pure_probe_before_after_and_negative_cases(self):
        source=SOURCE.read_text(encoding='utf-8')
        methods='\n'.join(method(source,name) for name in ('counterAdvanced','videoOutputAdvanced','videoRecoveryBaseline'))
        program='''final class Probe {
          static final class Snapshot {long videoRendered;Snapshot(long n){videoRendered=n;}}
          METHODS
          public static void main(String[] args){
            Snapshot before=new Snapshot(415),missing=new Snapshot(-1),newOutput=new Snapshot(718),reset=new Snapshot(5),zero=new Snapshot(0);
            if(videoOutputAdvanced(missing,newOutput))throw new AssertionError("old unavailable baseline must fail");
            if(videoRecoveryBaseline(before,zero)!=zero)throw new AssertionError("discarded valid post-command baseline");
            if(videoRecoveryBaseline(before,missing)!=before)throw new AssertionError("missing counter never rebased");
            if(!videoOutputAdvanced(videoRecoveryBaseline(before,missing),newOutput))throw new AssertionError("actual progress lost");
            if(!videoOutputAdvanced(videoRecoveryBaseline(before,missing),reset))throw new AssertionError("new decoder output lost");
            if(videoOutputAdvanced(before,before)||videoOutputAdvanced(before,zero)||videoOutputAdvanced(missing,missing))throw new AssertionError("invented output");
            System.out.println("PASS6 actual video-baseline cases; no playback behavior change");
          }}'''.replace('METHODS',methods)
        with tempfile.TemporaryDirectory(prefix='vibe-video-health-') as directory:
            path=Path(directory);(path/'Probe.java').write_text(program,encoding='utf-8')
            subprocess.run(['javac','--release','8',str(path/'Probe.java')],check=True,capture_output=True)
            result=subprocess.run(['java','-cp',str(path),'Probe'],check=True,capture_output=True,text=True)
            self.assertIn('PASS6',result.stdout)
        checks=(SOURCE.parent/'DebugAsyncPlayerChecks.java').read_text(encoding='utf-8')
        self.assertIn('PlaybackHealthProbe.videoOutputAdvanced(videoRecoveryBaseline, latestHealth)',checks)
        self.assertIn('PlaybackHealthProbe.videoOutputAdvanced(recovered, healthFinal)',checks)
        self.assertIn('&& surfaceStillHealthy',checks)

if __name__=='__main__':unittest.main()
