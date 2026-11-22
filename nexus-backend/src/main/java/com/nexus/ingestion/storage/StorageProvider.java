package com.nexus.ingestion.storage;

/**
 * 对象存储抽象接口。
 * 为什么抽接口：本地开发无 OBS 凭证也要跑通上传闭环，生产切华为云 OBS，
 * 与 AI Provider 同款"面向接口"模式（规范第 6 节思想，业务层不耦合具体存储 SDK）。
 */
public interface StorageProvider {

    /** 存储实现标识，如 local / huawei-obs */
    String providerName();

    /**
     * 存储文件并返回存储键（写入 world_sources.obs_key，下载/删除按此键定位）。
     *
     * @param userId   归属用户，用于 key 前缀隔离
     * @param fileName 原始文件名
     * @param content  文件内容
     * @return 存储键
     */
    String store(Long userId, String fileName, byte[] content);

    /** 按存储键读取内容（联调阶段下载预览用） */
    byte[] load(Long userId, String storageKey);

    /** 存储键是否属于该用户（下载前校验，防止跨用户读文件） */
    boolean owns(Long userId, String storageKey);
}