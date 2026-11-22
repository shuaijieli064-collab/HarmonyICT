package com.nexus.risk.service.impl;

import com.nexus.common.exception.BizException;
import com.nexus.common.result.ResultCode;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 风险包分页参数归一化（与本包内聚，避免跨包引用 world 包实现）。
 */
final class WorldPageHelper {

    private WorldPageHelper() {
    }

    static <T> Page<T> clampPage(int page, int pageSize) {
        if (page < 1) {
            throw new BizException(ResultCode.PARAM_INVALID, "page 从 1 开始");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new BizException(ResultCode.PARAM_INVALID, "pageSize 须在 1-100 之间");
        }
        return new Page<>(page, pageSize);
    }
}