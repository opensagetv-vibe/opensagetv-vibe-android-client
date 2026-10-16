"""Offline HTML5 regression; model data is NOT physical playback proof.
Run explicitly with RUN_DIAGNOSTIC_BROWSER_TESTS=1 in the existing web-Python
environment. Normal host/CI discovery skips this optional browser dependency.
"""
import json,os,threading,unittest
from pathlib import Path
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'source/dev/android-shared/src/main/assets/diagnostics'

@unittest.skipUnless(os.environ.get('RUN_DIAGNOSTIC_BROWSER_TESTS')=='1','Explicit offline browser gate')
class DiagnosticBrowserTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        from playwright.sync_api import sync_playwright,expect
        cls.expect=staticmethod(expect)
        cls.requests=[]
        cls.data={'schema':1,'capturedAtMs':1000,'device':{'model':'Fixture','version':'test','sdk':25},
                  'playback':{'backend':'Media3','state':'Play','buffering':True,'positionMs':12000,'bufferedAheadMs':3000,'configuredAudioOffsetMs':-400,'details':'<script>bad()</script>'},
                  'captions':{'cc1Track':1,'cc2Track':2,'services':[{'id':1,'type':'CEA-608','category':'Broadcast CC','evidence':'Extractor-declared; no CEA packets observed','language':'eng','supported':True}]},
                  'timeline':[{'timeMs':1000,'kind':'server-seek-request','requestedMs':30000,'observedMs':None,'detail':'<img src=x>'}],
                  'incidents':[{'id':'1234567890abcdef','timeMs':1000,'kind':'push-stall','bytes':300,'summary':'Recovered'}],
                  'settings':{'uploadStatus':'Unavailable; local logs retained','localBundleCount':2}}
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                cls.requests.append(self.path)
                if self.path=='/api/diagnostics':body=json.dumps(cls.data).encode();type='application/json'
                elif self.path=='/logs.txt':body=b'12:01:01 INFO Media3 playing\n12:01:03 ERROR Media3 stalled\n12:01:05 WARN IJK retry\n<svg onload=bad()>\n';type='text/plain'
                elif self.path.startswith('/incident.txt?id='):body=b'redacted incident';type='text/plain'
                elif self.path=='/bundle.zip':body=b'PK\x03\x04';type='application/zip'
                else:
                    name='index.html' if self.path=='/' else self.path.lstrip('/')
                    if name not in ('index.html','diagnostics.js','diagnostics.css'):self.send_error(404);return
                    body=(ASSETS/name).read_bytes();type={'html':'text/html','js':'application/javascript','css':'text/css'}[name.split('.')[-1]]
                self.send_response(200);self.send_header('Content-Type',type);self.send_header('Content-Length',str(len(body)))
                self.send_header('Content-Security-Policy',"default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; base-uri 'none'")
                self.end_headers();self.wfile.write(body)
            def log_message(self,*args):pass
        cls.server=ThreadingHTTPServer(('127.0.0.1',0),Handler);cls.thread=threading.Thread(target=cls.server.serve_forever,daemon=True);cls.thread.start()
        cls.playwright=sync_playwright().start();cls.browser=cls.playwright.chromium.launch(executable_path='/usr/local/bin/vibe-chromium',headless=True,args=['--no-sandbox'])
        cls.url='http://127.0.0.1:'+str(cls.server.server_port)+'/'
    @classmethod
    def tearDownClass(cls):cls.browser.close();cls.playwright.stop();cls.server.shutdown();cls.server.server_close();cls.thread.join(timeout=3)
    def setUp(self):
        self.page=self.browser.new_page(viewport={'width':1100,'height':800});self.errors=[]
        self.page.on('pageerror',lambda e:self.errors.append(str(e)))
        self.page.goto(self.url);self.expect(self.page.locator('#status')).to_contain_text('Updated')
    def tearDown(self):self.assertEqual([],self.errors);self.page.close()
    def test_dashboard_timeline_captions_history_and_safe_text(self):
        self.assertIn('Media3',self.page.locator('#metrics').inner_text())
        self.assertIn('Play / BUFFERING',self.page.locator('#metrics').inner_text())
        self.assertIn('Unavailable',self.page.locator('#metrics').inner_text())
        self.assertIn('30.000 s',self.page.locator('#timeline-rows').inner_text())
        self.assertIn('Extractor-declared',self.page.locator('#caption-rows').inner_text())
        self.assertEqual(0,self.page.locator('#details script, #timeline-rows img').count())
        self.assertEqual('/incident.txt?id=1234567890abcdef',self.page.locator('#incident-rows a').get_attribute('href'))
        self.assertIn('local logs retained',self.page.locator('#settings-values').inner_text())
    def test_log_filter_severity_component_time_copy_fallback(self):
        self.page.click('#load-logs');self.expect(self.page.locator('#log-text')).to_contain_text('stalled')
        self.page.select_option('#severity','ERROR');self.assertIn('stalled',self.page.locator('#log-text').inner_text());self.assertNotIn('playing',self.page.locator('#log-text').inner_text())
        self.page.fill('#component','IJK');self.assertIn('No matching',self.page.locator('#log-text').inner_text())
        self.page.fill('#component','');self.page.fill('#time-from','12:01:04');self.assertIn('No matching',self.page.locator('#log-text').inner_text())
        self.page.fill('#time-from','nonsense');self.assertIn('HH:mm:ss',self.page.locator('#log-status').inner_text())
        self.page.evaluate("Object.defineProperty(navigator,'clipboard',{value:undefined,configurable:true})")
        self.page.click('#copy-logs');self.assertFalse(self.page.locator('#copy-fallback').get_attribute('hidden') is not None)
    def test_issue_helper_never_posts_and_mobile_layout(self):
        self.page.fill('#issue-summary','Playback jumps back');self.page.fill('#issue-steps','Play; seek once');self.page.click('#generate-issue')
        report=self.page.locator('#issue-output').input_value();self.assertIn('Playback jumps back',report);self.assertIn('not measured lip-sync',report);self.assertIn('manually',report)
        self.assertNotIn('password=',report)
        self.page.set_viewport_size({'width':390,'height':844})
        self.assertTrue(self.page.evaluate('document.documentElement.scrollWidth <= 392'))
    def test_pause_and_hidden_page_suspend_requests(self):
        self.page.click('#pause');count=len(self.requests);self.page.wait_for_timeout(5300);self.assertEqual(count,len(self.requests))
        self.page.click('#pause');self.expect(self.page.locator('#status')).to_contain_text('Updated')
        self.page.evaluate("Object.defineProperty(document,'hidden',{get:()=>true,configurable:true});document.dispatchEvent(new Event('visibilitychange'))")
        count=len(self.requests);self.page.wait_for_timeout(5300);self.assertEqual(count,len(self.requests))

if __name__=='__main__':unittest.main()
