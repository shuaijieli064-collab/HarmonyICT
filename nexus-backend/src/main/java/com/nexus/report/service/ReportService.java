package com.nexus.report.service;

import com.nexus.report.dto.WeeklyReportResponse;

/**
 * 个人周期报告服务（体量扩充第二波）。
 */
public interface ReportService {

    /**
     * 生成最近 N 天的个人周期报告。
     *
     * @param userId 当前用户
     * @param days   报告期天数（7 默认，1–31）
     */
    WeeklyReportResponse weekly(Long userId, int days);
}
