"""Backend-specific existing observations; IJK still requires physical HDMI A/V.

Never manufacture renderer counters or infer a codec from the selected mode.
"""

def is_ijk(state):
    return str(state.get('health_backendClass') or state.get('playerClass') or '').endswith('.IJKMediaPlayerImpl')

def playing(state):
    if not is_ijk(state):
        return state.get('health_isPlaying') is True
    return (state.get('playerActive') is True and state.get('state')==2
            and state.get('health_basicIsPlaying') is True
            and state.get('health_firstVideoFrameRendered') is True
            and float(state.get('health_videoOutputFps',-1) or -1)>0
            and not state.get('health_playerError')
            and (not state.get('health_surfaceKnown') or state.get('health_surfaceValid') is True))

def started(state):
    if not is_ijk(state):
        return int(state.get('health_videoRendered',0))>5 and int(state.get('health_audioRendered',0))>5
    return playing(state) and int(state.get('health_playerPositionMs',-1))>0 and bool(state.get('health_audioDecoder'))

def advancing(before,after):
    if not is_ijk(after):
        return (int(after.get('health_videoRendered',0))>int(before.get('health_videoRendered',0))
                and int(after.get('health_audioRendered',0))>int(before.get('health_audioRendered',0)))
    return playing(after) and int(after.get('health_playerPositionMs',-1))>int(before.get('health_playerPositionMs',-1))

def paused(state):
    if is_ijk(state):return state.get('health_basicIsPlaying') is False
    return state.get('health_playWhenReady') is False

def video_mime(state):
    mime=state.get('health_videoMime') or ''
    if mime or not is_ijk(state):return mime
    decoder=str(state.get('health_videoDecoder') or '').lower()
    if 'mpeg2' in decoder:return 'video/mpeg2'
    if 'avc' in decoder or 'h264' in decoder:return 'video/avc'
    return ''
