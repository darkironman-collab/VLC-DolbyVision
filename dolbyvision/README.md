# VLC Dolby Vision – Windows implementation

Development branch: `feature/windows-dolby-vision`

## Phase 1 – Microsoft Media Foundation HEVC path

The first target is to make VLC's MFT decoder accept HEVC Main10 streams and expose 10-bit P010 output. This is the closest open VLC path to the Windows codec stack used by Dolby Vision-capable Windows players.

Initial patch: `0001-mft-hevc-p010.patch`

It adds:
- `VLC_CODEC_HEVC -> MFVideoFormat_HEVC` mapping
- `MFVideoFormat_P010 -> VLC_CODEC_P010` output mapping
- H.264/HEVC Annex-B conversion through VLC's existing `hxxx_helper`
- VPS/SPS/PPS injection for length-prefixed HEVC streams

## Phase 2 – Dolby Vision stream detection

Add detection/reporting for Dolby Vision configuration and profiles, starting with:
- Profile 5
- Profile 8.1
- Profile 8.4

Do not force a profile by default. Preserve source metadata wherever Windows' decoder/output path can consume it.

## Phase 3 – Windows Dolby Vision output experiments

Test the Media Foundation + D3D11 path on a Dolby Vision licensed Windows machine with the Microsoft HEVC/Dolby Vision extensions installed.

Success criteria:
1. Correct HEVC Main10 decode through MFT.
2. P010/D3D11 output without 8-bit conversion.
3. Correct colors for DV P5/P8 test clips.
4. Determine whether the Windows display enters native Dolby Vision mode.
5. If native DV output is unavailable, provide a correct HDR10/libplacebo fallback rather than purple/green output.

## Phase 4 – VLC UI

Once the backend paths are proven, expose controls similar in spirit to Energy Media Player:
- Auto
- HW (Microsoft)
- HW (Dolby Vision)
- HW (FFmpeg/D3D11VA)
- Software
- Force Dolby Vision Profile: Auto / 5 / 8.1 / 8.4

The UI should only expose modes that the current Windows system can actually support.
