# Case Study Scenarios to discuss

## Scenario 1: Cost Allocation and Tracking
**Situation**: The company needs to track and allocate costs accurately across different Warehouses and Stores. The costs include labor, inventory, transportation, and overhead expenses.

**Task**: Discuss the challenges in accurately tracking and allocating costs in a fulfillment environment. Think about what are important considerations for this, what are previous experiences that you have you could related to this problem and elaborate some questions and considerations

**Questions you may have and considerations:**

### Key Challenges

**Multi-dimensional cost attribution** is the core difficulty. A single cost event (e.g., a delivery run) may touch one Location, multiple Warehouses, and several Stores simultaneously. Without a clear allocation rulebook agreed between Finance and Operations upfront, two teams will produce conflicting numbers from the same source data.

**Shared overhead splitting** — building lease, utilities, and security costs at a Location are shared across all Warehouses co-located there. The allocation method (per m², per headcount, per throughput volume) must be defined and consistently applied. A change in method mid-year invalidates historical comparisons.

**The Business Unit Code (BUC) is the natural cost anchor.** Every Warehouse is identified by a BUC. Any ledger entry must reference the BUC — not a database ID — so that when a Warehouse is replaced (same BUC, old one archived), cost history remains traceable to the same business unit across generations. Without this discipline, the financial trail breaks at every replacement.

**Temporal granularity matters.** Real-time cost tracking (per dispatch event) gives Finance accurate data but is expensive to build and maintain. Batch/nightly reconciliation is simpler but results in stale data. The right cadence depends on business needs — daily for operational KPIs, monthly for financial close.

### Questions I Would Ask
- Is there already a cost center hierarchy in the ERP (SAP, Oracle)? Can Warehouse BUCs map 1:1 to cost centers to avoid a translation layer?
- Who owns the allocation rules — Finance or Operations? Misalignment here is the most common source of reconciliation failures.
- Are transportation costs tracked per shipment leg (granular) or as a monthly lump sum (simple)? The answer determines the data model complexity.
- What is the audit trail requirement — how far back must cost history be accessible and in what level of detail?

---

## Scenario 2: Cost Optimization Strategies
**Situation**: The company wants to identify and implement cost optimization strategies for its fulfillment operations. The goal is to reduce overall costs without compromising service quality.

**Task**: Discuss potential cost optimization strategies for fulfillment operations and expected outcomes from that. How would you identify, prioritize and implement these strategies?

**Questions you may have and considerations:**

### Identifying Opportunities

**Utilization rate analysis** is the first and cheapest lever. The system already stores `capacity` and `stock` per Warehouse. A warehouse running at <40% capacity long-term is a consolidation candidate. This signal requires no new tooling — it is derivable from existing data.

**Location concentration review** — some Locations allow multiple Warehouses (e.g., AMSTERDAM-001 allows 5). If only 2 are active at low utilization, consolidating into one larger unit can reduce fixed overhead. The `replace` operation in this system is exactly the mechanism for this: archive the underutilised unit, open a new optimised one.

**Routing and transportation imbalance** — high stock at one Location while another runs empty suggests suboptimal replenishment routing. Identifying these patterns requires aggregating stock levels across BUCs over time.

### Prioritisation Framework
1. **Quick wins:** Warehouses with utilisation < 40% — use existing stock/capacity data, no new tooling.
2. **Medium effort:** Cross-location imbalance analysis — requires time-series stock data aggregation.
3. **Higher effort:** Labour and transportation cost reduction — requires richer data from external systems (TMS, WMS).

### Implementation Approach
- Establish a baseline: measure current cost per unit dispatched per Warehouse.
- Set a target (e.g., 10% reduction in 6 months) and track weekly.
- Gate each optimisation with a cost-benefit sign-off: one-time migration cost vs. monthly saving, with a payback period threshold (e.g., < 9 months).

### Questions I Would Ask
- What is the fully-loaded cost of running a Warehouse per month (fixed + variable breakdown)?
- Are there contractual minimum notice periods to archive or replace a Warehouse that constrain the speed of optimisation?
- Does the business have seasonal peaks that make year-round utilisation metrics misleading?

---

## Scenario 3: Integration with Financial Systems
**Situation**: The Cost Control Tool needs to integrate with existing financial systems to ensure accurate and timely cost data. The integration should support real-time data synchronization and reporting.

**Task**: Discuss the importance of integrating the Cost Control Tool with financial systems. What benefits the company would have from that and how would you ensure seamless integration and data synchronization?

**Questions you may have and considerations:**

### Why Integration Matters
Without integration, Finance maintains a manual shadow copy of Warehouse data, which drifts from operational reality. Decisions are made on stale or inconsistent data, and month-end reconciliation becomes a fire drill.

With integration, Warehouse lifecycle events (created, replaced, archived) automatically trigger cost center creation or closure in the ERP, eliminating manual steps and reducing human error.

### Integration Patterns

**Event-driven (preferred for near-real-time):** Warehouse lifecycle events fire domain events. A financial adapter subscribes and creates/closes cost center entries in the ERP. This pattern is already implemented in the codebase for Store integration — the `StoreEvent` with `@Observes(during = TransactionPhase.AFTER_SUCCESS)` guarantees the ERP is only notified after the database transaction commits, preventing phantom notifications on rollbacks. The same pattern extends to Warehouse events.

**Batch sync (simpler, more resilient to ERP downtime):** Nightly export of active Warehouses and cost data as a structured file or API call. Finance sees T+1 data, acceptable for reporting but not for operational dashboards.

### Ensuring Seamless Synchronisation
- **Idempotency:** Every integration message must carry a unique event ID so the ERP can deduplicate retries without creating duplicate cost center entries.
- **Dead-letter handling:** Failed ERP calls must be captured in a retry queue with alerting, not silently swallowed.
- **Reconciliation job:** A daily comparison between the Cost Control Tool's active Warehouse list and the ERP's cost center list detects drift and surfaces discrepancies before month-end close.

### Questions I Would Ask
- What financial system is in use (SAP, Oracle, Dynamics)? Does it expose a REST API or only EDI/flat-file interfaces? The answer determines integration complexity.
- What is the SLA for data freshness — real-time, same-day, or T+1?
- Who currently triggers cost center creation manually in the ERP? Automating that step delivers the highest immediate business value.
- How are integration errors handled — does a failed ERP notification roll back the Warehouse operation, or is it flagged for manual remediation?

---

## Scenario 4: Budgeting and Forecasting
**Situation**: The company needs to develop budgeting and forecasting capabilities for its fulfillment operations. The goal is to predict future costs and allocate resources effectively.

**Task**: Discuss the importance of budgeting and forecasting in fulfillment operations and what would you take into account designing a system to support accurate budgeting and forecasting?

**Questions you may have and considerations:**

### Why Forecasting Is Hard in Fulfillment
Demand is seasonal, Warehouse replacements are lumpy capital events, and labour costs have a step-function behaviour — you hire a team, not a fractional employee. Standard linear extrapolation models fail unless these discontinuities are explicitly modelled.

### Design Considerations

**Structured input drivers:**
- Planned Warehouse count per Location × average monthly cost per Warehouse
- Expected stock volume × unit handling cost
- One-time replacement costs (decommissioning old, ramp-up for new)

**The `replace` operation is a forecastable event.** When a replacement is planned, the system should model: the gap period (if any between archive and new activation), the decommissioning cost, and the new Warehouse ramp-up cost profile. These should be capturable as planned events in the forecasting module.

**Actuals vs. budget variance tracking** is more valuable than the budget itself. The system must show not just "what did we spend" but "what did we plan and why did we deviate." This drives corrective action.

**Rolling forecasts over fixed annual budgets:** In fulfillment, a quarterly re-forecast cadence is more useful than an annual budget set once. Costs change with volume, and a 12-month-old forecast is rarely actionable.

### Questions I Would Ask
- Does Finance use a top-down budget (Finance allocates to Operations) or bottom-up (Operations submits estimates)? This determines who owns the forecast inputs in the tool.
- What is the forecasting horizon — 3 months, 12 months, 3 years?
- Are there known fixed costs (long-term leases) that are already contracted, reducing the uncertain component of the forecast?
- Is there a formal budget approval workflow, or is it informal?

---

## Scenario 5: Cost Control in Warehouse Replacement
**Situation**: The company is planning to replace an existing Warehouse with a new one. The new Warehouse will reuse the Business Unit Code of the old Warehouse. The old Warehouse will be archived, but its cost history must be preserved.

**Task**: Discuss the cost control aspects of replacing a Warehouse. Why is it important to preserve cost history and how this relates to keeping the new Warehouse operation within budget?

**Questions you may have and considerations:**

### Why Cost History Preservation Matters

**Audit trail integrity:** Regulators and internal auditors need to verify that BUC `MWH.001` incurred cost X in period Y, even after the physical Warehouse changed. The `archivedAt` timestamp implemented in this system is the foundation for this — but cost records in the ERP must reference the specific *generation* of the BUC (old vs. new), not just the BUC string.

**Budget continuity:** The new Warehouse (same BUC) should inherit the budget envelope of the replaced one, adjusted for the new capacity. If the old Warehouse had a monthly cost budget of €10k and the new one has 50% more capacity, the budget scales accordingly — it does not reset to zero. Without preserved history, this baseline adjustment is impossible to make objectively.

**The stock-carry constraint is financially motivated.** The business rule implemented in this system — "new Warehouse stock must exactly match old Warehouse stock" — ensures no inventory goes unaccounted during the transition, which would create an unexplained cost variance in the P&L. This is a financial control, not just an operational one.

### Considerations for the Replacement Transition Window

During the period between archiving the old Warehouse and activating the new one, in-transit inventory and its associated carrying costs must be booked to a defined BUC. The system should make this unambiguous — either the old BUC (until it is formally closed in the ERP) or the new BUC (from activation). Ambiguity here causes double-counting or gaps in cost records.

**Cancellation scenario:** The current API has no "restore archived" operation. If a replacement is cancelled after the old Warehouse is archived (e.g., the new premises fall through), Finance is left with a closed cost center and no active one. This is a gap worth raising as a follow-up requirement.

### Questions I Would Ask
- During the replacement window, to which BUC generation are in-transit costs booked — the old or the new?
- Is there a mandatory Finance sign-off (cost reconciliation review) before a replacement can be finalised in the system?
- How far back must cost history be retained after a Warehouse is archived — 3 years (typical audit), 7 years (tax), or longer?
- Should the replacement inherit the old Warehouse's budget line, or does it get a fresh budget approved through the normal process?

---

## Instructions for Candidates
Before starting the case study, read the [BRIEFING.md](BRIEFING.md) to quickly understand the domain, entities, business rules, and other relevant details.

**Analyze the Scenarios**: Carefully analyze each scenario and consider the tasks provided. To make informed decisions about the project's scope and ensure valuable outcomes, what key information would you seek to gather before defining the boundaries of the work? Your goal is to bridge technical aspects with business value, bringing a high level discussion; no need to deep dive.
