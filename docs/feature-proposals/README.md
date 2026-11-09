# Feature and improvement proposals

Use this directory for evidence-based product features, technical enhancements and workflow improvements before implementation is approved. Coder drafts a concise `<slug>.md` from [TEMPLATE.md](TEMPLATE.md), checks for duplicates in proposals and `.architecture-backlog/`, and follows [the discovery workflow](../agent-workflow.md#discovery-and-improvement-proposals).

A strong idea can improve architecture, performance, reliability, security, testing, CI/CD, observability or developer experience. It should solve an observed problem, explain concrete behavior and measurable benefit, and compare simpler alternatives. Label assumptions and unknown measurements. No proposal is required when there is no clear opportunity; normally draft at most one strong proposal per delivered feature unless the user asks for broader discovery.

Coder owns draft writes. Reviewer and DevOps return independent assessments; the coordinator persists these and the human decision only after explicit transfer of write ownership. Peer recommendations are advisory and do not authorize implementation. Use Draft, Under Review, Awaiting User Decision, Approved, Deferred or Rejected; only an explicit user decision can mark Approved. Record the date, decision reference and approved scope. Material scope changes need renewed approval.

Only approved scope becomes `.architecture-backlog/` tickets and follows normal delivery gates. Do not allocate AP numbers to unapproved ideas. Optional proposals must not expand or block current approved work. Actual acceptance-criteria defects remain blocking findings. Finish proposal edits before freezing a delivery candidate, or use a separate proposal branch; track pending ideas and decisions in local handover.md.
