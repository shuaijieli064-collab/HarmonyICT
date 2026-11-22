package com.nexus.common.util;

import java.text.Normalizer;

/**
 * 实体名称归一化（P1-7 拍板，联调 2026-11-22）。
 * 规则：trim（含全角空格）→ NFKC 全/半角归一 → 连续空白折叠为单个半角空格 → 英文 toLowerCase。
 * 归一化结果仅用于判重比对，world_entities.name 仍存原始展示名；
 * 不做中文繁简转换（超范围且易误判）；数据量大时可补 name_normalized 列 + 索引，当前实时计算比对即可。
 */
public final class EntityNameNormalizer {

    private EntityNameNormalizer() {
    }

    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.replace("\u3000", " ").strip();
        if (trimmed.isEmpty()) {
            return "";
        }
        String folded = Normalizer.normalize(trimmed, Normalizer.Form.NFKC).strip();
        return folded.replaceAll("\\s+", " ").toLowerCase();
    }
}