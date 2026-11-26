#!/usr/bin/env python3
"""EXPERIENCE-002 bounded external shaping, not a production component.

Run in the verified232 network namespace using the existing host Python (the
Windows forward-proxy fallback is unqualified on FireOS). Accept only Non-Pro25
requests for the existing stock232 API; never be a general forward proxy.
Only published Direct MPEG-TS bodies are rate-limited. No URLs, opaque handles,
credentials, captions or user media enter reports. The device harness owns and
restores its temporary Android proxy setting even on gate failure.
"""
import argparse,http.client,http.server,json,socket,sys,threading,time
from urllib.parse import urlsplit,parse_qs

TARGET='192.168.10.232';DEVICE='192.168.10.25'

class Shaper:
    def __init__(self,fault='none'):
        if fault not in ('none','prepare','candidate-media','delay-prepare','old-quality','provider-missing'):raise ValueError('Closed test fault required')
        self.lock=threading.Lock();self.started=None;self.next_byte=0;self.bytes=0;self.requests=0;self.override=None;self.rate_bytes={};self.route_timings={}
        self.fault=fault;self.fault_count=0;self.prepare_requests=0;self._candidate=None
        self.buffer_reports=[]
    def buffer_report(self,path,body):
        # Bounded external observation only: never retain tokens, URLs or
        # caption payloads, nor alter a production report/reply/clock/ACK.
        query=parse_qs(urlsplit(path).query);reply=json.loads(body)
        row={k:int(query[k][0]) for k in ('positionMs','bufferMs')}
        row.update({k:query.get(k,[''])[0]=='true' for k in ('buffering','eligible')})
        row.update({k:reply.get(k) for k in ('reason','samples','attempts','targetKbps')})
        with self.lock:
            self.buffer_reports.append(row);del self.buffer_reports[:-80]
    def candidate_reply(self,body):
        if self.fault not in ('candidate-media','delay-prepare'):return
        value=json.loads(body).get('sessionToken')
        if not isinstance(value,str) or len(value)!=64 or any(c not in '0123456789abcdef' for c in value):raise ValueError('Invalid candidate lineage')
        with self.lock:self._candidate=value
    def rejects(self,method,route):
        with self.lock:
            rejected=(self.fault=='prepare' and method=='POST' and route=='/v1/direct/quality/prepare'
                or self.fault=='candidate-media' and self._candidate is not None and method=='GET'
                and route.startswith('/v1/direct/media/'+self._candidate+'/') and route.endswith('.ts')
                or self.fault=='provider-missing' and method=='GET' and route=='/v1/capabilities')
            if rejected:self.fault_count+=1
            return rejected
    def old_capabilities(self,body):
        if self.fault!='old-quality':raise ValueError('Explicit older-capability profile required')
        value=json.loads(body);direct=value['mimDirect']
        for key in ('qualityHandoffRevision','qualitySeekRevision','qualityStartRevision','qualityRecoveryRevision'):
            direct.pop(key,None)
        with self.lock:self.fault_count+=1
        return json.dumps(value,separators=(',',':')).encode('utf-8')
    def timing(self,kind,opened,headers,finished,body_bytes):
        if kind not in ('segment','playlist','caption','api'):raise ValueError('Closed timing labels only')
        with self.lock:
            row=self.route_timings.setdefault(kind,{'requests':0,'headerMs':0,'bodyMs':0,'maxHeaderMs':0,'maxBodyMs':0,'bodyBytes':0})
            head=max(0,round((headers-opened)*1000));body=max(0,round((finished-headers)*1000))
            row['requests']+=1;row['headerMs']+=head;row['bodyMs']+=body;row['bodyBytes']+=body_bytes
            row['maxHeaderMs']=max(row['maxHeaderMs'],head);row['maxBodyMs']=max(row['maxBodyMs'],body)
    def delay(self,count):
        with self.lock:
            now=time.monotonic()
            if self.started is None:self.started=now
            elapsed=now-self.started
            # Healthy startup, sustained constrained delivery, then recovery.
            kbps=self.override if self.override is not None else 2400 if 15<=elapsed<65 else 12000
            self.next_byte=max(now,self.next_byte)+count*8/(kbps*1000)
            self.bytes+=count
            self.rate_bytes[kbps]=self.rate_bytes.get(kbps,0)+count
            wait=max(0,self.next_byte-now)
        time.sleep(wait)

def destination(url,reverse=False):
    parsed=urlsplit(url)
    if reverse:
        if parsed.scheme or parsed.netloc:raise ValueError('Reverse mode accepts only same-origin relative routes')
    elif parsed.scheme!='http' or parsed.hostname!=TARGET or parsed.port!=31910 or parsed.username or parsed.password:
        raise ValueError('Only the commissioned plugin endpoint is permitted')
    if not parsed.path.startswith('/v1/') or parsed.fragment:raise ValueError('Only plugin API routes are permitted')
    return parsed.path+('?' + parsed.query if parsed.query else '')

def run():
    parser=argparse.ArgumentParser();parser.add_argument('--run-non-pro-stock232',action='store_true');parser.add_argument('--namespace-stock232',action='store_true');parser.add_argument('--fault',choices=['none','prepare','candidate-media','delay-prepare','old-quality','provider-missing'],default='none');args=parser.parse_args()
    if not (args.run_non_pro_stock232 or args.namespace_stock232):raise SystemExit('Explicit test scope required')
    with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as route:
        route.connect((TARGET,31910));bind=route.getsockname()[0]
    if args.namespace_stock232:
        if bind!=TARGET:raise SystemExit('Must execute inside the exact stock232 network namespace')
        upstream_host='127.0.0.1'
    else:
        if bind not in ('192.168.10.66','192.168.10.77'):raise SystemExit('Uncommissioned host route')
        upstream_host=TARGET
    shaper=Shaper(args.fault)
    if args.namespace_stock232:shaper.override=12000
    class Handler(http.server.BaseHTTPRequestHandler):
        protocol_version='HTTP/1.1'
        def log_message(self,*args):pass
        def do_GET(self):self.forward()
        def do_POST(self):self.forward()
        def forward(self):
            upstream=None;opened=time.monotonic();headers_at=None;kind='api';body_bytes=0
            try:
                if self.client_address[0]!=DEVICE:self.send_error(403);return
                path=destination(self.path,args.namespace_stock232)
                route=path.split('?',1)[0]
                kind='segment' if route.startswith('/v1/direct/media/') and route.endswith('.ts') else 'playlist' if route.startswith('/v1/direct/media/') and route.endswith('.m3u8') else 'caption' if route.startswith('/v1/captions') else 'api'
                length=int(self.headers.get('Content-Length','0'))
                if length<0 or length>65536 or self.headers.get('Transfer-Encoding'):self.send_error(400);return
                body=self.rfile.read(length) if length else None
                if self.command=='POST' and route=='/v1/direct/quality/prepare':
                    with shaper.lock:shaper.prepare_requests+=1
                if shaper.rejects(self.command,route):self.send_error(404 if shaper.fault=='provider-missing' else 503);return
                headers={key:self.headers[key] for key in ('Range','Content-Type','Accept') if self.headers.get(key)}
                upstream=http.client.HTTPConnection(upstream_host,31910,timeout=25)
                upstream.request(self.command,path,body=body,headers=headers);response=upstream.getresponse()
                headers_at=time.monotonic()
                candidate_body=None
                if route=='/v1/direct/buffer' and response.status==200:
                    candidate_body=response.read(262145)
                    if len(candidate_body)>262144:raise ValueError('Bounded buffer report required')
                    shaper.buffer_report(path,candidate_body)
                if shaper.fault in ('candidate-media','delay-prepare') and route=='/v1/direct/quality/prepare' and response.status==200:
                    candidate_body=response.read(262145)
                    if len(candidate_body)>262144:raise ValueError('Bounded candidate response required')
                    shaper.candidate_reply(candidate_body)
                    if shaper.fault=='delay-prepare':
                        with shaper.lock:shaper.fault_count+=1
                        time.sleep(5) # External fault only; never a playback/provider delay.
                if shaper.fault=='old-quality' and route=='/v1/capabilities' and response.status==200:
                    candidate_body=response.read(262145)
                    if len(candidate_body)>262144:raise ValueError('Bounded capability response required')
                    candidate_body=shaper.old_capabilities(candidate_body)
                self.send_response(response.status)
                for key in ('Content-Type','Content-Length','Cache-Control','Content-Range','Accept-Ranges'):
                    value=str(len(candidate_body)) if key=='Content-Length' and candidate_body is not None else response.getheader(key)
                    if value is not None:self.send_header(key,value)
                # Preserve ordinary HTTP/1.1 reuse when the origin supplies a
                # complete length. Unknown-length replies must close; never
                # leave a client waiting for an invented body delimiter.
                reusable=response.getheader('Content-Length') is not None
                self.close_connection=not reusable
                self.send_header('Connection','keep-alive' if reusable else 'close');self.end_headers()
                if candidate_body is not None:
                    self.wfile.write(candidate_body);self.wfile.flush();body_bytes=len(candidate_body);return
                shaped=response.status in (200,206) and path.split('?',1)[0].startswith('/v1/direct/media/') and path.split('?',1)[0].endswith('.ts')
                if shaped:
                    with shaper.lock:shaper.requests+=1
                while True:
                    chunk=response.read(8192)
                    if not chunk:break
                    if shaped:shaper.delay(len(chunk))
                    self.wfile.write(chunk);self.wfile.flush()
                    body_bytes+=len(chunk)
            except (ValueError,OSError,http.client.HTTPException):
                # Broken/cancelled client reads are normal on an exact handoff.
                self.close_connection=True
            finally:
                if headers_at is not None:shaper.timing(kind,opened,headers_at,time.monotonic(),body_bytes)
                if upstream:upstream.close()
    server=http.server.ThreadingHTTPServer((bind,31911),Handler);server.daemon_threads=True
    if args.namespace_stock232:
        def controls():
            try:
                for line in sys.stdin:
                    if len(line)>128:continue
                    control=json.loads(line)
                    if control.get('stop') is True:break
                    rate=control.get('rateKbps')
                    if rate not in (2400,12000):continue
                    with shaper.lock:shaper.override=rate
                    print(json.dumps({'rateKbps':rate}),flush=True)
            finally:server.shutdown()
        threading.Thread(target=controls,daemon=True).start()
    deadline=threading.Timer(420,server.shutdown);deadline.daemon=True;deadline.start()
    print(json.dumps({'ready':True,'proxy':bind+':31911','scope':'Non-Pro25/stock232','publishedTsKbps':[12000,2400,12000],'phaseSeconds':[15,65],'maximumSeconds':420}),flush=True)
    try:server.serve_forever(poll_interval=.25)
    finally:
        deadline.cancel()
        server.server_close()
        print(json.dumps({'stopped':True,'publishedTsRequests':shaper.requests,'publishedTsBytes':shaper.bytes,'bytesByRateKbps':shaper.rate_bytes,'routeTimings':shaper.route_timings,'faultedRequests':shaper.fault_count,'prepareRequests':shaper.prepare_requests,'bufferReports':shaper.buffer_reports}),flush=True)

if __name__=='__main__':run()
