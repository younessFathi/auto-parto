# Production Readiness — User Stories

This directory contains 30 user stories organized into 6 phases, prioritized by risk severity.

## Phases

| Phase | Focus | Stories | Priority |
|-------|-------|---------|----------|
| 1 | **Security** — Close critical auth & secret vulnerabilities | US-001–US-005 | 🔴 DO NOW |
| 2 | **Data Integrity** — Fix race conditions, missing indexes, broken queries | US-006–US-009 | 🔴 DO NOW |
| 3 | **Notifications** — Fix SSE leaks, broken retry queue, FCM bugs | US-010–US-014 | 🟠 NEXT |
| 4 | **Observability & DevOps** — Actuator, metrics, CI hardening | US-015–US-021 | 🟠 NEXT |
| 5 | **Testing** — Coverage for controllers, security, services | US-022–US-025 | 🟡 SOON |
| 6 | **Code Cleanup** — Dead code, annotations, resource bounds | US-026–US-030 | 🟡 SOON |

## User Story Format

Each story follows: **As a** `<role>` **I want** `<capability>` **so that** `<benefit>`.
