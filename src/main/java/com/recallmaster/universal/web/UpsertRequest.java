package com.recallmaster.universal.web;

import java.util.List;

/**
 * POST /api/connectors/{name}/upsert 请求体
 *
 * @param documents 待写入的文档列表
 */
public record UpsertRequest(
        List<UpsertDocumentRequest> documents
) {
}
