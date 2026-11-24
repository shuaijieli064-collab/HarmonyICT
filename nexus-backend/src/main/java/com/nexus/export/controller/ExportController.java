package com.nexus.export.controller;

import com.nexus.auth.UserContext;
import com.nexus.export.service.ExportService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据导出接口（体量扩充第二波）。
 * 为什么不走 Result 包裹：CSV 是文件下载而非 JSON 业务数据，
 * 走 Content-Disposition 附件语义，前端以 blob 方式携带 Token 下载。
 */
@RestController
@RequestMapping("/api/v1/export")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/{dataset}")
    public ResponseEntity<byte[]> export(@PathVariable String dataset) {
        String csv = exportService.exportCsv(UserContext.get(), dataset);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header("Content-Disposition", "attachment; filename=nexus-" + dataset + ".csv")
                .body(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
