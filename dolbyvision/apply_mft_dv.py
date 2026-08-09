#!/usr/bin/env python3
"""Apply the first Windows Dolby Vision plumbing changes to VLC's MFT decoder.

This is intentionally strict: each expected upstream fragment must match exactly
once. If VLC master changes, CI stops instead of producing a build with only a
partially applied patch.
"""

from pathlib import Path

PATH = Path("modules/codec/mft.cpp")
text = PATH.read_text(encoding="utf-8")

replacements = [
    (
        "    /* H264 only. */\n"
        "    struct hxxx_helper hh = {};\n",
        "    /* H.264/HEVC byte-stream helper. */\n"
        "    struct hxxx_helper hh = {};\n",
    ),
    (
        "    { VLC_CODEC_VC1,  MFVideoFormat_WVC1 },\n"
        "    { VLC_CODEC_AV1,  MFVideoFormat_AV1 },\n\n"
        "    { 0, GUID_NULL }\n",
        "    { VLC_CODEC_VC1,  MFVideoFormat_WVC1 },\n"
        "    { VLC_CODEC_AV1,  MFVideoFormat_AV1 },\n"
        "    { VLC_CODEC_HEVC, MFVideoFormat_HEVC },\n\n"
        "    { 0, GUID_NULL }\n",
    ),
    (
        "    { VLC_CODEC_BGRA,  MFVideoFormat_ARGB32 },\n"
        "    { VLC_CODEC_GREY,  MFVideoFormat_L8 },\n\n"
        "    { 0, GUID_NULL }\n",
        "    { VLC_CODEC_BGRA,  MFVideoFormat_ARGB32 },\n"
        "    { VLC_CODEC_GREY,  MFVideoFormat_L8 },\n"
        "    { VLC_CODEC_P010,  MFVideoFormat_P010 },\n\n"
        "    { 0, GUID_NULL }\n",
    ),
    (
        "        if (p_dec->fmt_in->i_codec == VLC_CODEC_H264)\n"
        "        {\n"
        "            /* in-place NAL to annex B conversion. */\n",
        "        if (p_dec->fmt_in->i_codec == VLC_CODEC_H264 ||\n"
        "            p_dec->fmt_in->i_codec == VLC_CODEC_HEVC)\n"
        "        {\n"
        "            /* In-place H.26x NAL to Annex B conversion. */\n",
    ),
    (
        "    if (p_dec->fmt_in->i_codec == VLC_CODEC_H264)\n"
        "    {\n"
        "        auto *vidsys = dynamic_cast<mft_dec_video*>(p_sys);\n"
        "        hxxx_helper_init(&vidsys->hh, VLC_OBJECT(p_dec), p_dec->fmt_in->i_codec, 0, 0);\n"
        "        hxxx_helper_set_extra(&vidsys->hh, p_dec->fmt_in->p_extra, p_dec->fmt_in->i_extra);\n"
        "    }\n"
        "    return VLC_SUCCESS;\n",
        "    if (p_dec->fmt_in->i_codec == VLC_CODEC_H264 ||\n"
        "        p_dec->fmt_in->i_codec == VLC_CODEC_HEVC)\n"
        "    {\n"
        "        auto *vidsys = dynamic_cast<mft_dec_video*>(p_sys);\n"
        "        hxxx_helper_init(&vidsys->hh, VLC_OBJECT(p_dec), p_dec->fmt_in->i_codec, 0, 0);\n"
        "        hxxx_helper_set_extra(&vidsys->hh, p_dec->fmt_in->p_extra, p_dec->fmt_in->i_extra);\n"
        "    }\n"
        "    return VLC_SUCCESS;\n",
    ),
]

for index, (old, new) in enumerate(replacements, start=1):
    count = text.count(old)
    if count == 0:
        # Make reruns idempotent when the replacement is already present.
        if new in text:
            continue
        raise SystemExit(f"Dolby Vision patch step {index} did not match upstream mft.cpp")
    if count != 1:
        raise SystemExit(f"Dolby Vision patch step {index} matched {count} times; refusing ambiguous edit")
    text = text.replace(old, new, 1)

# Sanity markers used by CI and by developers inspecting a local build.
required = [
    "{ VLC_CODEC_HEVC, MFVideoFormat_HEVC },",
    "{ VLC_CODEC_P010,  MFVideoFormat_P010 },",
    "p_dec->fmt_in->i_codec == VLC_CODEC_HEVC",
]
for marker in required:
    if marker not in text:
        raise SystemExit(f"Missing required patched marker: {marker}")

PATH.write_text(text, encoding="utf-8")
print("Applied VLC-DolbyVision MFT HEVC/P010 patch successfully")
