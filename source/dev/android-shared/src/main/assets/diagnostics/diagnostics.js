/* Offline, same-origin, read-only HTML5 diagnostics. No CDN or remote scripts.
 * One bounded request per resource, visible-page polling, textContent only. */
(function (root) {
  'use strict';
  const unavailable = 'Unavailable';
  function present(value) { return value === null || value === undefined || value === '' ? unavailable : String(value); }
  function time(value) { return typeof value === 'number' && value >= 0 ? (value / 1000).toFixed(3) + ' s' : unavailable; }
  function stamp(value) { return value > 0 ? new Date(value).toISOString() : unavailable; }
  function severity(line) { const match = line.match(/\b(FATAL|ERROR|WARN(?:ING)?|INFO|DEBUG|TRACE)\b/i); return match ? ({FATAL:'ERROR',WARNING:'WARN'}[match[1].toUpperCase()] || match[1].toUpperCase()) : 'UNCLASSIFIED'; }
  function lineTime(line) { const match = line.match(/\b([0-2]\d:[0-5]\d:[0-5]\d)(?:[.,]\d+)?\b/); return match ? match[1] : ''; }
  function filterLines(lines, filters) {
    const search = String(filters.search || '').toLowerCase(), component = String(filters.component || '').toLowerCase();
    return lines.filter(line => {
      if (filters.severity && filters.severity !== 'all' && severity(line) !== filters.severity) return false;
      if (search && !line.toLowerCase().includes(search)) return false;
      if (component && !line.toLowerCase().includes(component)) return false;
      const t = lineTime(line);
      if ((filters.from || filters.to) && !t) return false;
      return (!filters.from || t >= filters.from) && (!filters.to || t <= filters.to);
    });
  }
  function issueReport(data, input) {
    const d = data.device || {}, p = data.playback || {};
    return '# ' + (input.summary || 'Vibe playback issue') + '\n\n'
      + 'Vibe version: ' + present(d.version) + '\nDevice: ' + present(d.manufacturer) + ' ' + present(d.model) + '\nAndroid API: ' + present(d.sdk)
      + '\nPlayer: ' + present(p.backend) + '\nTransport: ' + present(p.transport) + '\nDecoder: ' + present(p.decoder)
      + '\nVideo: ' + present(p.videoMime) + ' / ' + present(p.width) + 'x' + present(p.height) + ' / ' + present(p.fps) + ' fps'
      + '\nAudio output: ' + present(p.audioOutput) + '\nConfigured audio offset: ' + present(p.configuredAudioOffsetMs) + ' ms (not measured lip-sync)'
      + '\nSnapshot: ' + stamp(data.capturedAtMs) + '\n\n## Reproduction steps\n' + (input.steps || 'Please provide steps.')
      + '\n\n## Expected result\n' + (input.expected || 'Please describe.') + '\n\n## Actual result\n' + (input.actual || 'Please describe.')
      + '\n\n## Diagnostic settings\n```json\n' + JSON.stringify(data.settings || {}, null, 2) + '\n```'
      + '\n\n## Recent timeline observations\n```json\n' + JSON.stringify((data.timeline || []).slice(-8), null, 2) + '\n```'
      + '\n\n## Evidence\nDownload the diagnostic ZIP from the Android webpage, review it, and attach it manually to the GitHub issue. '
      + 'A copied report does not attach files. No issue has been submitted.\n\n## Privacy\n[ ] I reviewed the report and ZIP and removed information I do not want to publish.\n';
  }
  const exports = {present, time, severity, lineTime, filterLines, issueReport};
  if (typeof module !== 'undefined') module.exports = exports;
  if (!root.document) return;
  const doc = root.document, byId = id => doc.getElementById(id);
  let data = {}, paused = false, busy = false, logBusy = false, logAuto = false, logs = [], timer = null;
  function set(id, text) { byId(id).textContent = text; }
  function node(tag, text) { const value = doc.createElement(tag); if (text !== undefined) value.textContent = text; return value; }
  function table(id, rows) {
    const body = byId(id); body.replaceChildren();
    rows.forEach(row => { const tr = node('tr'); row.forEach(value => { const td = node('td'); if (value && value.nodeType) td.appendChild(value); else td.textContent = present(value); tr.appendChild(td); }); body.appendChild(tr); });
  }
  function render(value) {
    data = value; const d = value.device || {}, p = value.playback || {}, c = value.captions || {};
    set('sampling', value.samplingUnavailable || 'Snapshot ' + stamp(value.capturedAtMs) + '. Shared 2-second device cache; browser-sampled observations.');
    const metrics = [ ['Device', present(d.model) + ' / API ' + present(d.sdk)], ['Version', d.version], ['Player / state', present(p.backend) + ' / ' + present(p.state) + (p.buffering ? ' / BUFFERING' : '')],
      ['Transport', p.transport], ['Video decoder', p.decoder], ['Deinterlace', present(p.deinterlace) + ' (' + present(p.deinterlaceImplementation) + ')' + (p.deinterlaceFilter ? ' / ' + p.deinterlaceFilter : '')], ['Video format', present(p.videoMime) + ' ' + present(p.width) + 'x' + present(p.height)], ['FPS / display Hz', present(p.fps) + ' / ' + present(p.displayHz)], ['Decode / output FPS', present(p.videoDecodeFps) + ' / ' + present(p.videoOutputFps)],
      ['GPU output', p.gpuReason ? p.gpuReason + ' / input ' + present(p.gpuInputFrames) + ' / presented ' + present(p.gpuOutputFrames) + ' / second fields ' + present(p.gpuSecondFields) : 'Unavailable'],
      ['Position / duration', time(p.positionMs) + ' / ' + time(p.durationMs)], ['Buffered ahead', time(p.bufferedAheadMs)], ['Rendered / dropped', present(p.renderedFrames) + ' / ' + present(p.droppedFrames)],
      ['Audio output', p.audioOutput], ['Audio codec / sample rate', present(p.audioMime) + ' / ' + present(p.audioSampleRate)], ['Configured offset (ms)', p.configuredAudioOffsetMs],
      ['Retry count', p.retryCount], ['Heap used / maximum', present(d.heapUsedBytes) + ' / ' + present(d.heapMaxBytes) + ' bytes'] ];
    const container = byId('metrics'); container.replaceChildren(); metrics.forEach(([name,value]) => { const card=node('div');card.className='metric';card.appendChild(node('small',name));card.appendChild(node('strong',present(value)));container.appendChild(card); });
    set('details',present(p.details)); set('lifecycle',present(value.lifecycle)); set('timeline-note',present(value.timelineNote));
    table('timeline-rows',(value.timeline || []).slice().reverse().map(e => [stamp(e.timeMs),e.kind,time(e.requestedMs),time(e.observedMs),e.detail]));
    table('incident-rows',(value.incidents || []).map(e => { const a=node('a','Download redacted text'); a.href='/incident.txt?id='+encodeURIComponent(e.id);a.download='Vibe-'+e.kind+'-diagnostics.txt';return [stamp(e.timeMs),e.kind,e.bytes,e.summary,a]; }));
    const mapped = id => id === 8192 ? 'Off / unmapped' : present(id);
    set('caption-summary','STV state: '+(c.stvStateKnown?present(c.stvState):'Unavailable')+' | Selected: '+present(c.selectedTrack)+' | CC1 track: '+mapped(c.cc1Track)+' | CC2 track: '+mapped(c.cc2Track)+' | Callback negotiated: '+present(c.callbackNegotiated)+' | Events / bytes: '+present(c.callbackEvents)+' / '+present(c.callbackBytes));
    table('caption-rows',(c.services || []).map(s => [s.category,present(s.type)+' / '+present(s.language),present(s.service)+' / '+present(s.pid),
      (s.selected?'Selected; ':'')+(s.id===c.cc1Track?'CC1; ':'')+(s.id===c.cc2Track?'CC2':'') || 'Not selected',present(s.evidence)+'; supported='+present(s.supported)]));
    const settings = byId('settings-values'); settings.replaceChildren(); Object.entries(value.settings || {}).forEach(([k,v]) => {settings.appendChild(node('dt',k));settings.appendChild(node('dd',present(v)));});
  }
  async function fetchBounded(path, limit) {
    const abort = new AbortController(), deadline = setTimeout(()=>abort.abort(),8000);
    try { const response=await root.fetch(path,{cache:'no-store',signal:abort.signal}); if(!response.ok)throw new Error('HTTP '+response.status);
      const length=Number(response.headers.get('Content-Length'));if(length>limit)throw new Error('Response exceeds limit');
      const text=await response.text();if(text.length>limit)throw new Error('Response exceeds limit');return text;
    } finally {clearTimeout(deadline);}
  }
  function schedule() { clearTimeout(timer); if(!paused && !doc.hidden) timer=setTimeout(update,Number(byId('interval').value)); }
  async function update() {
    if(busy || doc.hidden)return; busy=true;set('status','Refreshing…');
    try {render(JSON.parse(await fetchBounded('/api/diagnostics',512*1024)));set('status','Updated '+new Date().toLocaleTimeString());}
    catch(error){set('status','Unavailable: '+error.message+'; local logs retained');}
    finally{busy=false;schedule();}
    if(logAuto && !paused && !doc.hidden)loadLogs();
  }
  function applyLogFilters() {
    const from=byId('time-from').value.trim(),to=byId('time-to').value.trim();
    const valid=t=>!t||/^([01]\d|2[0-3]):[0-5]\d:[0-5]\d$/.test(t);
    if(!valid(from)||!valid(to)||from&&to&&from>to){set('log-status','Use a same-day HH:mm:ss range, from <= to.');return;}
    const filtered=filterLines(logs,{search:byId('search').value,component:byId('component').value,severity:byId('severity').value,from,to});
    set('log-text',filtered.join('\n')||'No matching lines.');set('log-status',filtered.length+' / '+logs.length+' lines. Component is text matching; time is original log time, timezone not inferred.');
  }
  async function loadLogs(){if(logBusy||doc.hidden)return;logBusy=true;try{logs=(await fetchBounded('/logs.txt',2*1024*1024)).split(/\r?\n/);applyLogFilters();}catch(e){set('log-status','Logs unavailable: '+e.message);}finally{logBusy=false;}}
  async function copy(text){
    try{if(!root.navigator.clipboard)throw new Error('Clipboard not available over LAN HTTP');await root.navigator.clipboard.writeText(text);set('copy-status','Copied. Review before publishing.');}
    catch(e){const fallback=byId('copy-fallback');fallback.hidden=false;fallback.value=text;fallback.focus();fallback.select();set('copy-status','Clipboard unavailable. Copy the selected text manually.');}
  }
  byId('refresh').onclick=update;byId('pause').onclick=()=>{paused=!paused;byId('pause').textContent=paused?'Resume updates':'Pause updates';if(paused){clearTimeout(timer);set('status','Updates paused');}else update();};
  byId('interval').onchange=schedule;doc.addEventListener('visibilitychange',()=>{if(doc.hidden){clearTimeout(timer);set('status','Hidden: updates paused');}else if(!paused)update();});
  byId('load-logs').onclick=loadLogs;byId('pause-logs').onclick=()=>{logAuto=!logAuto;byId('pause-logs').textContent=logAuto?'Pause log updates':'Resume log updates';if(logAuto)loadLogs();};
  ['search','component','severity','time-from','time-to'].forEach(id=>byId(id).addEventListener('input',applyLogFilters));
  byId('copy-selection').onclick=()=>{const selected=root.getSelection().toString();if(selected)copy(selected);else set('copy-status','Select log lines first.');};
  byId('copy-logs').onclick=()=>copy(byId('log-text').textContent);
  byId('generate-issue').onclick=()=>{byId('issue-output').value=issueReport(data,{summary:byId('issue-summary').value,steps:byId('issue-steps').value,expected:byId('issue-expected').value,actual:byId('issue-actual').value});};
  byId('copy-issue').onclick=()=>copy(byId('issue-output').value);update();
})(typeof window === 'undefined' ? globalThis : window);
