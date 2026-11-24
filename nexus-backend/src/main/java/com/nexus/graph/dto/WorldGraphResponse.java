package com.nexus.graph.dto;

import java.util.List;

/**
 * 世界模型图谱响应（体量扩充第二波）。
 * 为什么由后端聚合：同源关联、事件归属、风险归属这三类边跨多个业务表，
 * 前端逐表拼装既费请求也难保证口径一致；一次返回节点与边，前端只负责渲染。
 */
public record WorldGraphResponse(List<GraphNode> nodes, List<GraphLink> links) {

    /** 图节点：kind ∈ entity | fact | event | risk */
    public record GraphNode(String id, String kind, String name, String category,
                            String meta) {
    }

    /** 图边：kind ∈ fact_of | event_of | co_source | risk_on */
    public record GraphLink(String source, String target, String kind, String label) {
    }
}
