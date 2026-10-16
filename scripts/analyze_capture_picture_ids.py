#!/usr/bin/env python3
"""Read authored binary IDs from a capture; encoded FPS alone is not evidence.

Use an independently specified displayed-video rectangle, not an adaptive crop
that might hide missing source pixels. Both top/bottom bands must agree. This
offline analysis never drives playback or modifies a capture/source file.
"""
import argparse
import json
import subprocess
from statistics import median
from collections import Counter
from pathlib import Path
from capture_hdmi_validation import DEFAULT_FFMPEG


def read_id(gray, stride, rect, y):
    x,top,width,height=rect
    values=[]
    for bit in range(8):
        column=x+int((bit+.5)*width/8)
        values.append(float(median(gray[row*stride+col]
                           for row in range(y-2,y+3) for col in range(column-2,column+3))))
    if any(75<value<185 for value in values):return None,values
    return sum(1<<bit for bit,value in enumerate(values) if value>=185),values


def summarize(ids, fps):
    valid=[value for value in ids if value is not None]
    transitions=Counter()
    for previous,current in zip(ids,ids[1:]):
        if previous is None or current is None:transitions['unknown']+=1
        else:transitions[str((current-previous)%256)]+=1
    seconds=(len(ids)-1)/fps if fps>0 else 0
    advancing=sum(count for delta,count in transitions.items() if delta.isdigit() and 0<int(delta)<128)
    lost=sum((int(delta)-1)*count for delta,count in transitions.items() if delta.isdigit() and 1<int(delta)<128)
    return {'frames':len(ids),'fps':fps,'observationSeconds':seconds,'validFrames':len(valid),
            'advancingPictureRate':advancing/seconds if seconds else 0,
            'transitions':dict(transitions),'sourceIdsSkipped':lost,
            'fullRetentionClaim':False,'limits':'capture/scheduler skips must be classified, never hidden by encoded frame count'}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--video',required=True,type=Path)
    parser.add_argument('--rect',required=True,type=int,nargs=4,metavar=('X','Y','WIDTH','HEIGHT'))
    parser.add_argument('--output',required=True,type=Path)
    parser.add_argument('--ffmpeg',type=Path,default=DEFAULT_FFMPEG)
    args=parser.parse_args()
    if args.output.exists():raise ValueError('Refuse to overwrite an earlier analysis')
    ffmpeg=args.ffmpeg.resolve()
    probe=ffmpeg.with_name('ffprobe.exe' if ffmpeg.suffix=='.exe' else 'ffprobe')
    metadata=json.loads(subprocess.run([str(probe),'-v','error','-select_streams','v:0',
            '-show_entries','stream=width,height,avg_frame_rate','-of','json',str(args.video.resolve())],
            check=True,capture_output=True,text=True,timeout=20).stdout)['streams'][0]
    rate=metadata['avg_frame_rate'].split('/')
    fps=int(rate[0])/int(rate[1]);width=int(metadata['width']);height=int(metadata['height'])
    x,y,w,h=args.rect
    if min(x,y)<0 or w<32 or h<64 or x+w>width or y+h>height:
        raise ValueError('Displayed rectangle must fit the complete capture')
    ids=[];mismatched=0;first=[]
    capture=subprocess.Popen([str(ffmpeg),'-loglevel','error','-nostdin','-i',str(args.video.resolve()),
            '-map','0:v:0','-an','-pix_fmt','gray','-fps_mode','passthrough','-f','rawvideo','-'],
            stdout=subprocess.PIPE,stderr=subprocess.DEVNULL)
    try:
        while True:
            gray=capture.stdout.read(width*height)
            if not gray:break
            if len(gray)!=width*height:raise ValueError('Incomplete decoded capture frame')
            if len(ids)>=7200:raise ValueError('Capture analysis exceeds120s/60Hz bound')
            top,values=read_id(gray,width,args.rect,y+20)
            bottom,_=read_id(gray,width,args.rect,y+h-20)
            if top!=bottom:
                mismatched+=1;ids.append(None)
            else:ids.append(top)
            if len(first)<8:first.append({'top':top,'bottom':bottom,'luma':values})
        if capture.wait(timeout=15)!=0:raise ValueError('Capture decoding failed')
    finally:
        capture.stdout.close()
        if capture.poll() is None:capture.kill();capture.wait(timeout=5)
    report=summarize(ids,fps)
    report.update(video=str(args.video.resolve()),rectangle=args.rect,mismatchedBands=mismatched,
                  firstFrames=first,ids=ids)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps({key:value for key,value in report.items() if key not in ('ids','firstFrames')},indent=2))


if __name__=='__main__':main()
