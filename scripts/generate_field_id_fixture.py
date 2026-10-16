#!/usr/bin/env python3
"""Copyright-free MPEG-2 moving-field fixture with an ID in EACH source field.

The eight luma columns repeat at top/bottom64 rows. Generate at field rate,
stamp the ID BEFORE tinterlace, then encode actual TFF/BFF. Labels added after
tinterlace would only identify coded frames and cannot prove field retention.
"""
import argparse
from pathlib import Path
import subprocess

def source_filter(standard, resolution):
    """Distinct original fields at the television rate, before weaving."""
    size='1920x1080' if resolution=='hd' else ('720x576' if standard=='pal' else '720x480')
    rate='50' if standard=='pal' else '60000/1001'
    return f'testsrc2=size={size}:rate={rate}'

def field_filter(order, progressive=False):
    tag='16+219*mod(floor(N/pow(2,min(7,floor(X/(W/8))))),2)'
    edge='lt(Y,64)+gte(Y,H-64)'
    stamped=(f"geq=lum='if({edge},{tag},lum(X,Y))':"
            f"cb='if({edge},128,cb(X,Y))':cr='if({edge},128,cr(X,Y))',")
    return stamped+('setfield=prog' if progressive else
            'tinterlace=mode='+('interleave_top' if order=='top' else 'interleave_bottom')
            + ',setfield='+('tff' if order=='top' else 'bff'))

def fast_field_filter(order, progressive=False):
    """Equivalent ID bands with per-frame box enables, not per-pixel pow().

    HD geq evaluates the same bit arithmetic millions of times per field.
    A drawbox enable evaluates each bit once per original field. The moving
    scene and BOTH64-row bands remain unchanged in purpose; verify decoded
    source IDs before commissioning instead of assuming encoder output.
    """
    boxes=['drawbox=x=0:y=0:w=iw:h=64:c=black:t=fill',
           'drawbox=x=0:y=ih-64:w=iw:h=64:c=black:t=fill']
    for bit in range(8):
        enabled=f"enable='eq(mod(floor(n/{1<<bit}),2),1)'"
        for edge in ('0','ih-64'):
            boxes.append(f'drawbox=x=iw*{bit}/8:y={edge}:w=iw/8:h=64:c=white:t=fill:{enabled}')
    tail='setfield=prog' if progressive else (
        'tinterlace=mode='+('interleave_top' if order=='top' else 'interleave_bottom')
        + ',setfield='+('tff' if order=='top' else 'bff'))
    return ','.join(boxes+[tail])

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ffmpeg',required=True,type=Path)
    parser.add_argument('--output',required=True,type=Path)
    parser.add_argument('--order',choices=('top','bottom'),default='top')
    parser.add_argument('--progressive',action='store_true',help='Native59.94p capture-rate control with a distinct ID per source picture, no weaving')
    parser.add_argument('--standard',choices=('ntsc','pal'),default='ntsc')
    parser.add_argument('--resolution',choices=('sd','hd'),default='sd',help='HD generates1920x1080 original-field geometry, not upscaled SD')
    parser.add_argument('--fast-stamps',action='store_true',help='Equivalent per-field ID bands using inexpensive drawbox enables; decoded IDs must still be verified')
    parser.add_argument('--seconds',type=int,default=90)
    args=parser.parse_args()
    if not 3<=args.seconds<=120: parser.error('seconds must be3..120')
    output=args.output.resolve()
    if output.exists(): parser.error('refuse to overwrite an existing fixture')
    output.parent.mkdir(parents=True,exist_ok=True)
    subprocess.run([str(args.ffmpeg.resolve()),'-hide_banner','-nostdin','-n',
        '-f','lavfi','-i',source_filter(args.standard,args.resolution),
        '-f','lavfi','-i','sine=frequency=440:sample_rate=48000',
        '-t',str(args.seconds),'-vf',(fast_field_filter if args.fast_stamps else field_filter)(args.order,args.progressive),
        '-c:v','mpeg2video','-flags','0' if args.progressive else '+ilme+ildct',
        '-q:v','2','-g','15','-bf','0','-c:a','ac3','-b:a','192k','-ac','2',
        '-ar','48000','-f','mpegts',str(output)],check=True,timeout=180)

if __name__=='__main__': main()
