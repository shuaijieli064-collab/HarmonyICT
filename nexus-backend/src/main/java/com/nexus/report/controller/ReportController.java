package com.nexus.report.controller;

import com.nexus.auth.UserContext;
import com.nexus.common.result.Result;
import com.nexus.report.dto.WeeklyReportResponse;
import com.nexus.report.service.ReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 周期报告接口（体量扩充第二波）。
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/weekly")
    public Result<WeeklyReportResponse> weekly(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(reportService.weekly(UserContext.get(), days));
    }
}
