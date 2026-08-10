# Extreme DV Player

Standalone Android Media3 player prototype focused on native Dolby Vision hardware playback. It prefers the device Dolby Vision MediaCodec implementation for `video/dolby-vision`, supports local files and direct HTTP/HTTPS, HLS, DASH and RTSP sources, and keeps decoder fallback enabled.

The Profile 8 preference is a playback preference, not a Dolby bitstream transcoder. Profile 7 is not rewritten into Profile 8.
