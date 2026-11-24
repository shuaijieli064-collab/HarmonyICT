package com.nexus.export.service;

/**
 * 数据导出服务：把当前用户的世界模型 / 风险 / 任务导出为 CSV（体量扩充第二波）。
 * 定位：演示"资料可带走"，也为答辩提供数据佐证；只读聚合，不引入新表。
 */
public interface ExportService {

    /**
     * 导出指定数据集为 CSV 文本（UTF-8，含 BOM 便于 Excel 直接打开）。
     *
     * @param userId  当前用户（数据隔离红线）
     * @param dataset facts / risks / tasks / entities
     * @return CSV 全文
     */
    String exportCsv(Long userId, String dataset);
}
