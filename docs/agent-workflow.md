# AutoParto agent delivery workflow

## Activation and ownership

For feature implementation, bug fixes and technical enhancements, the main chat coordinates coder, reviewer and devops. This project instruction explicitly requests delegation for these tasks. Plain explanations do not require three agents. Definitions live in `.codex/agents/`; `.codex/config.toml` enables three concurrent subagents. Models/reasoning inherit from the parent. Open a Codex chat rooted in this repository to load project configuration. These files do not create always-running agents or a background service.

Use named custom roles when supported. Otherwise spawn ordinary subagents with the complete corresponding role instructions and disclose that fallback. Respect a lower runtime capacity by sequencing reviews. Do not recursively spawn agents. If delegation is unavailable, report the independent-approval gate as blocked; never simulate independent approvals.

The coordinator owns planning, handover.md, review records, immutable local traces, approval tracking and the final notification. Coder owns implementation edits and commits. Reviewer and DevOps inspect/check and return report/trace content to the coordinator for persistence, avoiding concurrent writes. Explicitly assign disjoint files before allowing any exception. Role instructions are behavioral controls, not separate filesystem sandboxes. Use GitHub permissions, branch protection and required checks for external enforcement.

## Delivery sequence

1. Read AGENTS.md and handover.md. Inspect status, branch, remotes, the relevant `.architecture-backlog/` ticket and acceptance criteria. Preserve unrelated changes. Determine the intended PR base and feature branch from evidence; never publish an unrelated existing branch for a new task.
2. Checkpoint the objective, acceptance criteria, plan, assignments, base SHA, target branch and required checks. Coder implements one ticket at a time, with appropriate tests and documentation/tracking.
3. Run focused checks and required project verification. The coordinator schedules Maven/Docker runs sequentially because target/ and container resources are shared. Reuse results only for the same candidate/environment, with command, exit code and log/report provenance. Never claim checks passed without evidence.
4. Coder commits only scoped changes and sends READY_FOR_REVIEW with base/candidate SHAs and evidence to both peers and the coordinator. Freeze candidate edits. Reviewer and DevOps inspect independently in parallel and communicate findings to Coder, copying the coordinator on decisions.
5. Each returns APPROVED, CHANGES_REQUESTED or BLOCKED for the same candidate. Missing required verification is BLOCKED. Findings include severity, path:line evidence, impact and requested correction. Distinguish blocking defects from optional suggestions and record dispositions. Coder cannot approve its own work.
6. Coder fixes findings and creates a new candidate; every new candidate invalidates both approvals. Re-run affected checks and obtain both verdicts again. After three unsuccessful repair rounds, checkpoint and escalate unresolved findings to the user. Never auto-approve to escape the loop.
7. Publish only when both approvals match current HEAD, required local checks pass, and no relevant uncommitted/untracked changes were omitted. Coordinator records approvals in local handover.md and local review records. Do not create a bookkeeping commit after approval. If tracked reports or other edits change the candidate, review that new candidate too.
8. Coder verifies remote URL and pushes the feature branch explicitly to `github` (currently `git@github.com:younessFathi/auto-parto.git`), then opens/updates a GitHub PR with explicit repository and intended base. `origin` is GitLab; do not use it accidentally. Never push main, force-push, merge or deploy in this workflow. Prefer connected GitHub tools; otherwise use authenticated GitHub CLI/API. Missing credentials/tools means publication BLOCKED; preserve the approved local state.
9. DevOps follows required GitHub CI checks for the exact PR head SHA until success or a blocker. Pending, missing or skipped required checks are not passing. Failed CI returns to Coder; any new commit needs both reviews again. Check the PR head/base before handoff; a moved base requires reassessment and renewed checks/reviews when the integration result changes.
10. Once required CI passes, notify the user in the main chat with PR link, final commit, feature summary, both verdicts, CI evidence and residual risks. Their final GitHub review is next. Merge and production deployment require a separate user instruction.

States: IMPLEMENTING -> LOCAL_CHECKS -> IN_REVIEW -> APPROVED_LOCAL -> PUBLISHED -> CI_PASSED -> AWAITING_HUMAN_REVIEW. CHANGES_REQUESTED returns to IMPLEMENTING. BLOCKED explains the missing evidence/decision and pauses that transition. These instructions are not an executable CI state machine.

## Communication and records

Every readiness message, verdict and handoff includes task/ticket, branch, base/candidate SHAs, sender/recipient, status, acceptance criteria, concise evidence, blocking findings, optional suggestions, limitations, and next action/owner. Send peer messages through agent tools and copy the coordinator; route via the coordinator when necessary. No external messaging is implied. Only Coder publishes; the coordinator delivers the final user notification.

The coordinator persists each review using the existing `.architecture-backlog/reviews/REVIEW-TEMPLATE.md` (locate the actual template first) and records sanitized traces under `.agent-trace/`. Never overwrite an immutable prior review/trace. Local ignored reports do not replace the PR description and GitHub CI evidence.

## Context recovery and handover

The coordinator maintains root `handover.md` using `docs/agent-handover-template.md`. It is local and Git-ignored so checkpoints do not mutate an approved commit. It persists across context compaction and sessions in this checkout. It is not automatically copied to another clone, worktree or machine; transfer a sanitized copy explicitly when changing checkouts. There is no guaranteed context-limit callback.

Update it at task start; after implementation, verification, reviews, push and CI; before ownership transfer or planned pause; and when context becomes crowded. Specialists send early checkpoints. If the coordinator is unavailable before imminent handoff, a specialist may update it only after obtaining exclusive ownership or confirming other writers are idle. Write a temporary file beside handover.md and atomically replace it, preserving the prior valid checkpoint until replacement succeeds.

Record objective/acceptance criteria, branch/base/candidate, relevant dirty files and unrelated changes to preserve, completed work/files, commands/results/evidence, both review statuses and approved SHAs, PR/CI, decisions, blockers and ordered next actions with owners. Never include secrets, credentials, sensitive payloads or hidden reasoning.

The next agent reads AGENTS.md and handover.md first, verifies Git status/HEAD/remotes and available PR/CI/evidence, invalidates stale approvals, and resumes the first unfinished step. If the checkpoint is missing, reconstruct it from Git, tickets, traces and available chat history using the template; mark uncertainty and never invent work or approvals.

## Start a feature

Open a Codex chat on AutoParto and ask: "Implement AP-XXXX using the project coder/reviewer/devops workflow. Maintain handover.md, require both approvals, publish the approved feature branch to GitHub, check CI, and return the PR for my final review."

Reference: [Official OpenAI subagent documentation](https://learn.chatgpt.com/docs/agent-configuration/subagents).
