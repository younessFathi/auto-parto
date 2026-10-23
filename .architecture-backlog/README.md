# Auto-Parto Architecture Backlog

This ignored local backlog converts `docs/architecture.md` and `docs/deployment-architecture.md` into implementation tickets.

## Structure

- `features/`: Feature indexes with linked ticket summaries.
- `tickets/`: Independently deliverable tickets numbered `AP-0001` onward.
- `reviews/`: One implementation review per ticket.
- `TICKET-TEMPLATE.md`: Required format for future tickets.

## Statuses

- `Proposed`: Needs refinement or approval.
- `Ready`: Approved and unblocked.
- `In Progress`: Implementation has started.
- `Blocked`: Cannot proceed until linked work or a decision completes.
- `Done`: Definition of Done is fully satisfied.
- `Cancelled`: Intentionally abandoned with a recorded reason.

## Rules

- Ticket numbers are permanent and never reused.
- Each ticket has one primary type: `User Story`, `Bug Fix`, or `Technical Enhancement`.
- User stories express the actor and value in the description; bug fixes and technical enhancements describe the concrete problem and target outcome.
- Dependencies and related work use relative Markdown links.
- Test scenarios cover the ticket's primary success, failure, security, and recovery behavior; Definition of Done independently requires every acceptance criterion to be verified.
- A ticket must remain below 500 lines.
- `Done` means the whole ticket is implemented, every acceptance criterion is satisfied, and every test scenario is automated and passing.

## Features

- [F01 Platform and Data](features/F01-platform-and-data.md)
- [F02 Identity and Security](features/F02-identity-and-security.md)
- [F03 Marketplace](features/F03-marketplace.md)
- [F04 Money and Wallets](features/F04-money-and-wallets.md)
- [F05 Notifications and Redis](features/F05-notifications-and-redis.md)
- [F06 File Storage](features/F06-file-storage.md)
- [F07 Deployment and Secrets](features/F07-deployment-and-secrets.md)
- [F08 Observability and Recovery](features/F08-observability-and-recovery.md)
- [F09 Go-Live Readiness](features/F09-go-live-readiness.md)
- [F12 Runtime Cleanup](features/F12-runtime-cleanup.md)
