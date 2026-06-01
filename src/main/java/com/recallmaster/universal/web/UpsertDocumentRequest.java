package com.recallmaster.universal.web;

import java.util.Map;

/**
 * 单个文档的 upsert 请求体
 *
 * @param id       文档唯一 ID（可选，不传则服务端自动生成）
 * @param text     文档原文（必需）
 * @param metadata 附加元数据（可选）
 */
public record UpsertDocumentRequest(
        String id,
        String text,
        Map<String, String> metadata
) {
}
