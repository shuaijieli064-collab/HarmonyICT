package com.nexus.ingestion.controller;

import com.nexus.auth.UserContext;
import com.nexus.common.result.PageResult;
import com.nexus.common.result.Result;
import com.nexus.ingestion.service.SourceService;
import com.nexus.world.dto.SourceResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 资料来源接口，契约见 api-specification.md 4 节。
 */
@RestController
@RequestMapping("/api/v1/sources")
public class SourceController {

    private final SourceService sourceService;

    public SourceController(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<SourceResponse> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(sourceService.upload(UserContext.get(), file));
    }

    @GetMapping
    public Result<PageResult<SourceResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        return Result.ok(sourceService.list(UserContext.get(), status, page, pageSize));
    }

    @GetMapping("/{id}")
    public Result<SourceResponse> get(@PathVariable Long id) {
        return Result.ok(sourceService.get(UserContext.get(), id));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable Long id) {
        byte[] content = sourceService.loadFile(UserContext.get(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content);
    }
}