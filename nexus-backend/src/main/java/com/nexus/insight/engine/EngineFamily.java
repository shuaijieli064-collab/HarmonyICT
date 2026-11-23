package com.nexus.insight.engine;

/**
 * 洞察引擎族：对规则引擎产出的 ruleCode 做业务域分组（P2）。
 * 红线：只做展示层归类，不改动 ai/risk/rules/ 下任何规则实现（B 域）。
 */
public enum EngineFamily {

    FINANCE("财务引擎"),
    SUBSCRIPTION("订阅引擎"),
    LEARNING("学习引擎"),
    DEVICE("设备引擎"),
    DATA_QUALITY("数据质量"),
    OTHER("其他");

    private final String label;

    EngineFamily(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * ruleCode → 引擎族。前缀口径与 ai/risk/rules 现有规则一致：
     * BudgetRule(BUDGET_ 前缀)、AssetExpiryRule(SUBSCRIPTION_ 与 WARRANTY_ 前缀)、
     * DeadlineRule(DEADLINE_ 前缀)、ScheduleConflictRule(SCHEDULE_ 前缀)、
     * RiskRuleEngine(CERT_ 与 DATA_ 前缀)。
     */
    public static EngineFamily of(String ruleCode) {
        if (ruleCode == null || ruleCode.isBlank()) {
            return OTHER;
        }
        if (ruleCode.startsWith("BUDGET_")) {
            return FINANCE;
        }
        if (ruleCode.startsWith("SUBSCRIPTION_")) {
            return SUBSCRIPTION;
        }
        if (ruleCode.startsWith("WARRANTY_") || ruleCode.startsWith("CERT_")) {
            return DEVICE;
        }
        if (ruleCode.startsWith("DEADLINE_") || ruleCode.startsWith("SCHEDULE_")) {
            return LEARNING;
        }
        if (ruleCode.startsWith("DATA_")) {
            return DATA_QUALITY;
        }
        return OTHER;
    }
}
