"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { listEntities, listFacts } from "@/services/world";
import { listRisks } from "@/services/risks";
import { useIsLoggedIn } from "@/hooks/use-auth";
import type { Entity, Fact, Risk } from "@/types";

/** 决策实验室面板（真实接口 + 确定性规则）：从世界模型事实生成方案对比卡。
 * 原则（立项文档 §九.3）：结论由规则给出、可追溯，前端不做随机推断；
 * 规则输入 = 事实（validUntil/predicate 语义），依据 = 触发规则名 + 事实来源，
 * 与规则引擎产出的 open 风险互相印证。
 */

/** 决策卡：一个待决策问题 + 两个方案 + 规则结论 + 依据 */
interface DecisionCard {
  id: string;
  /** 待决策问题 */
  question: string;
  /** 触发规则名（确定性依据） */
  ruleName: string;
  /** 结论（规则计算） */
  conclusion: string;
  /** 结论倾向：actionable 建议处理 / neutral 中性观察 */
  tone: "actionable" | "neutral";
  options: Array<{ title: string; points: string[] }>;
  /** 依据事实（predicate:objectValue 展示） */
  evidence: string[];
}

const EXPIRY_WINDOW_DAYS = 30;
/** 预算类谓词关键词（抽取谓词为自由文本，按语义关键词匹配） */
const BUDGET_KEYWORDS = ["budget", "预算"];
const COST_KEYWORDS = ["cost", "price", "fee", "费用", "价格", "月费"];

function parseDate(value: string): number | null {
  const time = new Date(value).getTime();
  return Number.isNaN(time) ? null : time;
}

function daysUntil(value: string): number | null {
  const due = parseDate(value);
  if (due === null) return null;
  return Math.ceil((due - Date.now()) / 86400000);
}

function factLabel(fact: Fact): string {
  return `${fact.predicate}：${fact.objectValue}${fact.unit ? ` ${fact.unit}` : ""}`;
}

/** 规则 1：有效期临近（≤30 天）→ 续费/处理决策卡（与 CERT_EXPIRY_30D 类规则引擎结论互相印证） */
function buildExpiryCards(
  facts: Fact[],
  entityById: Map<string, Entity>,
  risks: Risk[]
): DecisionCard[] {
  const cards: DecisionCard[] = [];
  for (const fact of facts) {
    if (!fact.validUntil) continue;
    const days = daysUntil(fact.validUntil);
    if (days === null || days > EXPIRY_WINDOW_DAYS) continue;

    const entity = entityById.get(fact.subjectId);
    const subjectName = entity?.name ?? "未知主体";
    const relatedRisk = risks.find((risk) => risk.entityId === fact.subjectId);
    const expired = days < 0;

    cards.push({
      id: `expiry:${fact.id}`,
      question: `是否继续保留「${subjectName} · ${fact.predicate}」？`,
      ruleName: `有效期临近（validUntil ${expired ? "已过期" : `${days} 天`}，阈值 ${EXPIRY_WINDOW_DAYS} 天）`,
      conclusion: relatedRisk
        ? `建议优先处理：规则引擎已产出风险「${relatedRisk.title}」（${relatedRisk.level}）`
        : expired
          ? "已过期：建议立即核实状态并更新记录"
          : "暂无规则引擎风险预警：可按计划处理，到期前完成即可",
      tone: relatedRisk || expired ? "actionable" : "neutral",
      options: [
        {
          title: "方案 A · 保留 / 续期",
          points: [
            "维持世界模型中的现有记录",
            relatedRisk ? "需同步处理已产出的风险项" : "到期前完成续期即无风险",
          ],
        },
        {
          title: "方案 B · 取消 / 释放",
          points: [
            "停止后续费用与维护投入",
            "通过 Agent 授权更新事实（update_fact）保持台账一致",
          ],
        },
      ],
      evidence: [factLabel(fact), `有效期 validUntil：${fact.validUntil}`],
    });
  }
  return cards;
}

/** 规则 2：预算 vs 支出 → 预算健康度卡（支出合计超过预算 → actionable） */
function buildBudgetCard(facts: Fact[]): DecisionCard | null {
  const budgetFacts = facts.filter((fact) =>
    BUDGET_KEYWORDS.some((keyword) => fact.predicate.toLowerCase().includes(keyword))
  );
  const costFacts = facts.filter((fact) =>
    COST_KEYWORDS.some((keyword) => fact.predicate.toLowerCase().includes(keyword))
  );
  if (budgetFacts.length === 0 || costFacts.length === 0) return null;

  const toNumber = (value: string): number | null => {
    const parsed = Number.parseFloat(value.replace(/[^\d.]/g, ""));
    return Number.isNaN(parsed) ? null : parsed;
  };
  const budget = budgetFacts
    .map((fact) => toNumber(fact.normalizedValue))
    .find((value) => value !== null);
  if (budget === undefined) return null;
  const costTotal = costFacts
    .map((fact) => toNumber(fact.normalizedValue))
    .reduce<number>((sum, value) => sum + (value ?? 0), 0);

  const over = costTotal > budget;
  return {
    id: "budget:health",
    question: `当前支出规划是否在预算内？`,
    ruleName: `预算健康度（支出合计 ${costTotal} vs 预算 ${budget}）`,
    conclusion: over
      ? `超出预算 ${Math.round(costTotal - budget)}：建议梳理可取消项（见资产中心到期清单）`
      : `在预算内（剩余 ${Math.round(budget - costTotal)}）：维持当前规划`,
    tone: over ? "actionable" : "neutral",
    options: [
      {
        title: "方案 A · 维持现有支出",
        points: [
          over ? "将连续超支，需说明理由" : "支出可控，符合预算约束",
        ],
      },
      {
        title: "方案 B · 调整支出结构",
        points: [
          over ? "优先处理到期订阅与重复费用项" : "可提升储蓄或目标投入比例",
        ],
      },
    ],
    evidence: [...budgetFacts, ...costFacts].slice(0, 6).map(factLabel),
  };
}

type LoadState =
  | { phase: "loading" }
  | { phase: "error"; message: string }
  | { phase: "empty" }
  | { phase: "ready"; cards: DecisionCard[] };

export default function DecisionPanel() {
  const authed = useIsLoggedIn();
  const [state, setState] = useState<LoadState>({ phase: "loading" });

  const fetchDecisions = useCallback(async (): Promise<LoadState> => {
    const [entityPage, factPage, riskPage] = await Promise.all([
      listEntities({ page: 1, pageSize: 100 }),
      listFacts({ page: 1, pageSize: 200 }),
      listRisks({ status: "open", page: 1, pageSize: 50 }),
    ]);
    const entityById = new Map(entityPage.records.map((entity) => [entity.id, entity]));
    const cards = [
      ...buildExpiryCards(factPage.records, entityById, riskPage.records),
      buildBudgetCard(factPage.records),
    ].filter((card): card is DecisionCard => card !== null);
    if (cards.length === 0) return { phase: "empty" };
    return { phase: "ready", cards };
  }, []);

  useEffect(() => {
    if (!authed) return;
    let cancelled = false;
    fetchDecisions()
      .then((next) => {
        if (!cancelled) setState(next);
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setState({ phase: "error", message: err instanceof Error ? err.message : "决策数据加载失败" });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [authed, fetchDecisions]);

  if (!authed) {
    return (
      <div className="space-y-3 text-center">
        <p className="text-sm text-warning">请先登录后使用决策实验室</p>
        <Link href="/login" className="inline-block text-sm text-primary hover:underline">
          前往登录 / 注册 →
        </Link>
      </div>
    );
  }

  if (state.phase === "loading") {
    return <p className="text-sm text-muted">正在推演决策方案…</p>;
  }
  if (state.phase === "error") {
    return <p className="text-sm text-danger">{state.message}</p>;
  }
  if (state.phase === "empty") {
    return (
      <p className="rounded-xl border border-secondary/10 bg-card p-4 text-sm text-muted">
        暂无可推演决策：上传资料产出带有效期/预算的事实后，规则引擎会在这里生成方案对比卡
      </p>
    );
  }

  return (
    <div className="space-y-4">
      <p className="text-xs text-muted">
        决策由确定性规则计算（立项原则：规则支撑智能），结论可追溯到下方依据事实；执行动作仍需在 Agent 中心 L2 授权
      </p>
      <div className="grid gap-4 lg:grid-cols-2">
        {state.cards.map((card) => (
          <div key={card.id} className="space-y-3 rounded-xl border border-secondary/10 bg-card p-4">
            <div className="flex items-start justify-between gap-2">
              <p className="text-sm font-semibold text-content">{card.question}</p>
              <span
                className={`shrink-0 rounded-full px-2.5 py-0.5 text-xs ${
                  card.tone === "actionable" ? "bg-warning/15 text-warning" : "bg-secondary/15 text-muted"
                }`}
              >
                {card.tone === "actionable" ? "建议处理" : "中性观察"}
              </span>
            </div>
            <p className="rounded-lg bg-base p-2.5 text-xs text-secondary">
              <span className="text-primary">规则结论</span> · {card.ruleName} → {card.conclusion}
            </p>
            <div className="grid gap-2 sm:grid-cols-2">
              {card.options.map((option) => (
                <div key={option.title} className="rounded-lg border border-secondary/10 p-3">
                  <p className="text-xs font-semibold text-content">{option.title}</p>
                  <ul className="mt-1.5 space-y-1">
                    {option.points.map((point) => (
                      <li key={point} className="text-xs leading-relaxed text-secondary">
                        · {point}
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
            <details className="text-xs">
              <summary className="cursor-pointer text-primary">依据事实</summary>
              <ul className="mt-1.5 space-y-1">
                {card.evidence.map((item) => (
                  <li key={item} className="text-muted">
                    {item}
                  </li>
                ))}
              </ul>
            </details>
          </div>
        ))}
      </div>
    </div>
  );
}