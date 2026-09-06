"""User-facing SageTV MiniClient test configuration names and stable preference mappings.

The CLI/API names intentionally stay simple while the Android preference values remain
backward-compatible with existing SageTV client profiles.
"""
from __future__ import annotations

STREAMING_SELECTIONS = ("push", "pull", "smb_direct", "smb_auto", "fixed")
DECODING_SELECTIONS = ("hardware", "software", "fallback")

FIXED_ENCODING_PREFERENCES = ("needed", "always")
FIXED_ENCODING_FORMATS = ("matroska", "dvd", "mpegts")
FIXED_VIDEO_BITRATES_KBPS = (1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000, 10000)
FIXED_VIDEO_FPS = ("source", "24", "29.97", "59.94")
FIXED_VIDEO_RESOLUTIONS = ("source", "cif", "d1", "720", "1080")
FIXED_AUDIO_CODECS = ("aac", "ac3", "mp2")
FIXED_AUDIO_BITRATES_KBPS = (96, 112, 128, 160, 192)
FIXED_AUDIO_CHANNELS = ("source", "1", "2", "6")
FIXED_REMUXING_PREFERENCES = ("needed", "always", "off")
FIXED_REMUXING_FORMATS = ("matroska", "dvd", "mpegts")

FIXED_DEFAULTS = {
    # Matrix default intentionally forces the fixed transcoder so Fixed really tests encoding.
    "fixed_encoding_preference": "always",
    "fixed_encoding_format": "mpegts",
    "fixed_video_bitrate_kbps": 4000,
    "fixed_video_fps": "source",
    "fixed_key_frame_interval": 10,
    "fixed_use_b_frames": True,
    "fixed_video_resolution": "source",
    # AAC is decoded reliably by both Android Exo generations after a Fixed
    # MPEG-TS rebase. MIM translates SageTV Core's obsolete libfaac spelling to
    # modern FFmpeg's native aac encoder without changing the wire codec.
    "fixed_audio_codec": "aac",
    "fixed_audio_bitrate_kbps": 128,
    "fixed_audio_channels": "source",
    "fixed_remuxing_preference": "off",
    "fixed_remuxing_format": "matroska",
}

_STREAMING_ALIASES = {
    "push": "push",
    "dynamic": "push",
    "push/dynamic": "push",
    "pull": "pull",
    "fixed": "fixed",
    "smb": "smb_direct",
    "smb_direct": "smb_direct",
    "smb direct": "smb_direct",
    "smb_auto": "smb_auto",
    "smb auto": "smb_auto",
}
_DECODING_ALIASES = {
    "hardware": "hardware",
    "software": "software",
    "fallback": "fallback",
    "hardware_preferred": "fallback",
    "hardware-preferred": "fallback",
    "hardware preferred": "fallback",
}

_STREAMING_PREFERENCES = {
    "push": "dynamic",
    "pull": "pull",
    "fixed": "fixed",
    "smb_direct": "smb_direct",
    "smb_auto": "smb_auto",
}
_DECODING_PREFERENCES = {
    "hardware": "hardware",
    "software": "software",
    "fallback": "hardware_preferred",
}


def normalize_streaming(value: str) -> str:
    key = str(value).strip().lower()
    if key not in _STREAMING_ALIASES:
        raise ValueError(f"unsupported streaming selection: {value}")
    return _STREAMING_ALIASES[key]


def normalize_decoding(value: str) -> str:
    key = str(value).strip().lower()
    if key not in _DECODING_ALIASES:
        raise ValueError(f"unsupported decoding selection: {value}")
    return _DECODING_ALIASES[key]


def streaming_preference(value: str) -> str:
    return _STREAMING_PREFERENCES[normalize_streaming(value)]


def decoding_preference(value: str) -> str:
    return _DECODING_PREFERENCES[normalize_decoding(value)]


def normalize_csv(value: str, *, kind: str) -> list[str]:
    requested = [item.strip() for item in str(value).split(",") if item.strip()]
    if not requested:
        raise ValueError(f"{kind} must not be empty")
    normalizer = normalize_streaming if kind == "streaming" else normalize_decoding
    result: list[str] = []
    for item in requested:
        normalized = normalizer(item)
        if normalized not in result:
            result.append(normalized)
    return result


def normalize_fixed_choice(value: str, allowed: tuple[str, ...], label: str) -> str:
    normalized = str(value).strip().lower()
    if normalized not in allowed:
        raise ValueError(f"unsupported {label}: {value}; choose {','.join(allowed)}")
    return normalized


def fixed_config_from_args(args) -> dict:
    return {
        "fixed_encoding_preference": normalize_fixed_choice(args.fixed_encoding_preference, FIXED_ENCODING_PREFERENCES, "fixed encoding preference"),
        "fixed_encoding_format": normalize_fixed_choice(args.fixed_encoding_format, FIXED_ENCODING_FORMATS, "fixed encoding format"),
        "fixed_video_bitrate_kbps": int(args.fixed_video_bitrate_kbps),
        "fixed_video_fps": normalize_fixed_choice(args.fixed_video_fps, FIXED_VIDEO_FPS, "fixed video fps"),
        "fixed_key_frame_interval": int(args.fixed_key_frame_interval),
        "fixed_use_b_frames": bool(args.fixed_use_b_frames),
        "fixed_video_resolution": normalize_fixed_choice(args.fixed_video_resolution, FIXED_VIDEO_RESOLUTIONS, "fixed video resolution"),
        "fixed_audio_codec": normalize_fixed_choice(args.fixed_audio_codec, FIXED_AUDIO_CODECS, "fixed audio codec"),
        "fixed_audio_bitrate_kbps": int(args.fixed_audio_bitrate_kbps),
        "fixed_audio_channels": normalize_fixed_choice(args.fixed_audio_channels, FIXED_AUDIO_CHANNELS, "fixed audio channels"),
        "fixed_remuxing_preference": normalize_fixed_choice(args.fixed_remuxing_preference, FIXED_REMUXING_PREFERENCES, "fixed remuxing preference"),
        "fixed_remuxing_format": normalize_fixed_choice(args.fixed_remuxing_format, FIXED_REMUXING_FORMATS, "fixed remuxing format"),
    }


def fixed_expected_snapshot(config: dict) -> dict[str, str]:
    return {
        "fixedEncodingPreference": str(config["fixed_encoding_preference"]),
        "fixedEncodingFormat": str(config["fixed_encoding_format"]),
        "fixedVideoBitrateKbps": str(config["fixed_video_bitrate_kbps"]),
        "fixedVideoFps": str(config["fixed_video_fps"]),
        "fixedKeyFrameInterval": str(config["fixed_key_frame_interval"]),
        "fixedUseBFrames": str(bool(config["fixed_use_b_frames"])).lower(),
        "fixedVideoResolution": str(config["fixed_video_resolution"]),
        "fixedAudioCodec": str(config["fixed_audio_codec"]),
        "fixedAudioBitrateKbps": str(config["fixed_audio_bitrate_kbps"]),
        "fixedAudioChannels": str(config["fixed_audio_channels"]),
        "fixedRemuxingPreference": str(config["fixed_remuxing_preference"]),
        "fixedRemuxingFormat": str(config["fixed_remuxing_format"]),
    }


def add_fixed_encoding_args(parser) -> None:
    """Add the complete Fixed streaming encoding/remux parameter set to a CLI parser."""
    parser.add_argument("--fixed-encoding-preference", choices=FIXED_ENCODING_PREFERENCES,
                        default=FIXED_DEFAULTS["fixed_encoding_preference"],
                        help="Fixed transcoding policy: needed or always. Matrix default always forces an encoding test.")
    parser.add_argument("--fixed-encoding-format", choices=FIXED_ENCODING_FORMATS,
                        default=FIXED_DEFAULTS["fixed_encoding_format"],
                        help="Fixed encoded container: matroska, dvd, or mpegts")
    parser.add_argument("--fixed-video-bitrate-kbps", type=int, choices=FIXED_VIDEO_BITRATES_KBPS,
                        default=FIXED_DEFAULTS["fixed_video_bitrate_kbps"])
    parser.add_argument("--fixed-video-fps", type=str.lower, choices=FIXED_VIDEO_FPS,
                        default=FIXED_DEFAULTS["fixed_video_fps"])
    parser.add_argument("--fixed-key-frame-interval", type=int,
                        default=FIXED_DEFAULTS["fixed_key_frame_interval"],
                        help="Fixed encoding key-frame interval in seconds")
    bframes = parser.add_mutually_exclusive_group()
    bframes.add_argument("--fixed-use-b-frames", dest="fixed_use_b_frames", action="store_true",
                         help="Enable B-frames for Fixed encoding")
    bframes.add_argument("--fixed-no-b-frames", dest="fixed_use_b_frames", action="store_false",
                         help="Disable B-frames for Fixed encoding")
    parser.set_defaults(fixed_use_b_frames=FIXED_DEFAULTS["fixed_use_b_frames"])
    parser.add_argument("--fixed-video-resolution", type=str.lower, choices=FIXED_VIDEO_RESOLUTIONS,
                        default=FIXED_DEFAULTS["fixed_video_resolution"])
    parser.add_argument("--fixed-audio-codec", type=str.lower, choices=FIXED_AUDIO_CODECS,
                        default=FIXED_DEFAULTS["fixed_audio_codec"])
    parser.add_argument("--fixed-audio-bitrate-kbps", type=int, choices=FIXED_AUDIO_BITRATES_KBPS,
                        default=FIXED_DEFAULTS["fixed_audio_bitrate_kbps"])
    parser.add_argument("--fixed-audio-channels", type=str.lower, choices=FIXED_AUDIO_CHANNELS,
                        default=FIXED_DEFAULTS["fixed_audio_channels"],
                        help="Fixed audio channels: source, 1, 2, or 6")
    parser.add_argument("--fixed-remuxing-preference", choices=FIXED_REMUXING_PREFERENCES,
                        default=FIXED_DEFAULTS["fixed_remuxing_preference"],
                        help="Fixed remux policy. Default off so Fixed tests encoding, not remuxing.")
    parser.add_argument("--fixed-remuxing-format", choices=FIXED_REMUXING_FORMATS,
                        default=FIXED_DEFAULTS["fixed_remuxing_format"])


def validate_fixed_config(config: dict) -> None:
    if not 1 <= int(config["fixed_video_bitrate_kbps"]) <= 100000:
        raise ValueError("--fixed-video-bitrate-kbps must be between 1 and 100000")
    if not 1 <= int(config["fixed_audio_bitrate_kbps"]) <= 10000:
        raise ValueError("--fixed-audio-bitrate-kbps must be between 1 and 10000")
    if not 1 <= int(config["fixed_key_frame_interval"]) <= 600:
        raise ValueError("--fixed-key-frame-interval must be between 1 and 600")
