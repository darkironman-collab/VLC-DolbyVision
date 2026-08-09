#!/usr/bin/env python3
"""Add Phase 2 Dolby Vision stream detection/reporting to VLC's MFT HEVC path.

Run after apply_mft_dv.py. The patch is intentionally diagnostic first: it
recognises Dolby Vision configuration records (dvcC/dvvC), reports profile and
level where present, distinguishes Profile 8.1 vs 8.4 from the BL compatibility
id, and detects HEVC Dolby Vision RPU NAL units (nal_unit_type 62) after Annex-B
conversion.
"""

from pathlib import Path

PATH = Path("modules/codec/mft.cpp")
text = PATH.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count == 0:
        if new in text:
            return
        raise SystemExit(f"Dolby Vision Phase 2 patch did not match: {label}")
    if count != 1:
        raise SystemExit(f"Dolby Vision Phase 2 patch matched {count} times: {label}")
    text = text.replace(old, new, 1)


replace_once(
    "    struct hxxx_helper hh = {};\n"
    "    bool   b_xps_pushed = false; ///< (for xvcC) parameter sets pushed (SPS/PPS/VPS)\n",
    "    struct hxxx_helper hh = {};\n"
    "    bool   b_xps_pushed = false; ///< (for xvcC) parameter sets pushed (SPS/PPS/VPS)\n"
    "    bool   b_dovi_config_reported = false;\n"
    "    bool   b_dovi_rpu_seen = false;\n",
    "mft_dec_video Dolby Vision state",
)

helper_code = r'''
static const char *DoviProfileName(unsigned profile, unsigned compatibility_id)
{
    if (profile == 5)
        return "Profile 5";
    if (profile == 8 && compatibility_id == 1)
        return "Profile 8.1 (HDR10 compatible base layer)";
    if (profile == 8 && compatibility_id == 4)
        return "Profile 8.4 (HLG compatible base layer)";
    if (profile == 8)
        return "Profile 8";
    return "other profile";
}

static void ReportDolbyVisionConfig(decoder_t *p_dec, mft_dec_video *vidsys)
{
    if (vidsys->b_dovi_config_reported || p_dec->fmt_in->p_extra == nullptr ||
        p_dec->fmt_in->i_extra < 9)
        return;

    const uint8_t *extra = static_cast<const uint8_t *>(p_dec->fmt_in->p_extra);
    const size_t extra_size = p_dec->fmt_in->i_extra;

    for (size_t i = 0; i + 9 <= extra_size; ++i)
    {
        const bool dvcc = extra[i] == 'd' && extra[i + 1] == 'v' &&
                          extra[i + 2] == 'c' && extra[i + 3] == 'C';
        const bool dvvc = extra[i] == 'd' && extra[i + 1] == 'v' &&
                          extra[i + 2] == 'v' && extra[i + 3] == 'C';
        if (!dvcc && !dvvc)
            continue;

        const uint8_t *cfg = extra + i + 4;
        const unsigned version_major = cfg[0];
        const unsigned version_minor = cfg[1];
        const unsigned profile = cfg[2] >> 1;
        const unsigned level = ((cfg[2] & 0x01) << 5) | (cfg[3] >> 3);
        const bool rpu_present = (cfg[3] & 0x04) != 0;
        const bool el_present = (cfg[3] & 0x02) != 0;
        const bool bl_present = (cfg[3] & 0x01) != 0;
        const unsigned compatibility_id = cfg[4] >> 4;

        msg_Info(p_dec,
                 "Dolby Vision configuration detected: %s, profile=%u level=%u "
                 "BL-compatibility-id=%u RPU=%s EL=%s BL=%s config-version=%u.%u",
                 DoviProfileName(profile, compatibility_id), profile, level,
                 compatibility_id, rpu_present ? "yes" : "no",
                 el_present ? "yes" : "no", bl_present ? "yes" : "no",
                 version_major, version_minor);
        vidsys->b_dovi_config_reported = true;
        return;
    }
}

static bool AnnexBHasDolbyVisionRpu(const block_t *block)
{
    if (block == nullptr || block->p_buffer == nullptr || block->i_buffer < 6)
        return false;

    const uint8_t *p = block->p_buffer;
    const size_t size = block->i_buffer;

    for (size_t i = 0; i + 5 < size; ++i)
    {
        size_t nal = 0;
        if (p[i] == 0 && p[i + 1] == 0 && p[i + 2] == 1)
            nal = i + 3;
        else if (i + 4 < size && p[i] == 0 && p[i + 1] == 0 &&
                 p[i + 2] == 0 && p[i + 3] == 1)
            nal = i + 4;
        else
            continue;

        if (nal + 1 >= size)
            continue;

        const unsigned nal_unit_type = (p[nal] >> 1) & 0x3f;
        if (nal_unit_type == 62)
            return true;
    }
    return false;
}

'''

replace_once(
    "static int DecodeSync(decoder_t *p_dec, block_t *p_block)\n",
    helper_code + "static int DecodeSync(decoder_t *p_dec, block_t *p_block)\n",
    "Dolby Vision helper functions",
)

replace_once(
    "            p_block = hxxx_helper_process_block(&vidsys->hh, p_block);\n\n"
    "            if (vidsys->hh.i_input_nal_length_size && !vidsys->b_xps_pushed)\n",
    "            p_block = hxxx_helper_process_block(&vidsys->hh, p_block);\n\n"
    "            if (p_dec->fmt_in->i_codec == VLC_CODEC_HEVC && p_block != nullptr &&\n"
    "                !vidsys->b_dovi_rpu_seen && AnnexBHasDolbyVisionRpu(p_block))\n"
    "            {\n"
    "                vidsys->b_dovi_rpu_seen = true;\n"
    "                msg_Info(p_dec, \"Dolby Vision RPU NAL detected in HEVC stream (NAL unit type 62)\");\n"
    "            }\n\n"
    "            if (vidsys->hh.i_input_nal_length_size && !vidsys->b_xps_pushed)\n",
    "RPU detection in DecodeSync",
)

replace_once(
    "        hxxx_helper_init(&vidsys->hh, VLC_OBJECT(p_dec), p_dec->fmt_in->i_codec, 0, 0);\n"
    "        hxxx_helper_set_extra(&vidsys->hh, p_dec->fmt_in->p_extra, p_dec->fmt_in->i_extra);\n"
    "    }\n"
    "    return VLC_SUCCESS;\n",
    "        hxxx_helper_init(&vidsys->hh, VLC_OBJECT(p_dec), p_dec->fmt_in->i_codec, 0, 0);\n"
    "        hxxx_helper_set_extra(&vidsys->hh, p_dec->fmt_in->p_extra, p_dec->fmt_in->i_extra);\n"
    "        if (p_dec->fmt_in->i_codec == VLC_CODEC_HEVC)\n"
    "            ReportDolbyVisionConfig(p_dec, vidsys);\n"
    "    }\n"
    "    return VLC_SUCCESS;\n",
    "configuration reporting during MFT init",
)

required = [
    "Dolby Vision configuration detected:",
    "Dolby Vision RPU NAL detected in HEVC stream",
    "Profile 8.1 (HDR10 compatible base layer)",
    "Profile 8.4 (HLG compatible base layer)",
]
for marker in required:
    if marker not in text:
        raise SystemExit(f"Missing Phase 2 marker: {marker}")

PATH.write_text(text, encoding="utf-8")
print("Applied VLC-DolbyVision Phase 2 detection/reporting patch successfully")
