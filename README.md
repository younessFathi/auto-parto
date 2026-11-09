# Auto-Parto

## Functional workflow diagrams

Explore [the functional journeys](docs/workflows.md) from the buyer and seller perspective:

- [Join or sign in](docs/workflows.md#join-or-sign-in) and [manage your profile](docs/workflows.md#manage-a-profile-or-deactivate-an-account).
- [Request a part](docs/workflows.md#request-a-car-part), [publish a listing](docs/workflows.md#publish-and-manage-a-listing) and [find matching parts](docs/workflows.md#find-parts-matching-a-request).
- [Make and decide on offers](docs/workflows.md#make-and-decide-on-an-offer) and [manage a wallet](docs/workflows.md#view-or-change-a-wallet-balance).
- [Attach photos](docs/workflows.md#attach-photos-to-a-request-or-listing) and [view images](docs/workflows.md#view-an-uploaded-image).
- [Choose notification preferences](docs/workflows.md#choose-notification-preferences), [receive updates](docs/workflows.md#receive-account-notifications) and [enable device push](docs/workflows.md#enable-push-notifications-on-a-device).

For development, infrastructure and agent collaboration, see the separate [technical workflows](docs/technical-workflows.md). Local startup configuration and commands remain in the [PostgreSQL development guide](docs/postgresql-development-cutover.md).

## PostgreSQL development migration

PostgreSQL is the application persistence boundary. To run the schema and fixtures locally, follow [`docs/postgresql-development-cutover.md`](docs/postgresql-development-cutover.md). The Compose profile provisions distinct runtime and migration roles, runs Flyway as a one-shot job, and provides idempotent development fixtures.

Authentication currently uses the OTP-to-JWT canonical model documented in [`docs/canonical-authentication.md`](docs/canonical-authentication.md). Incomplete Google and Clerk integrations remain disabled.

The canonical versioned HTTP API and legacy-route migration policy are documented in [`docs/api-boundaries.md`](docs/api-boundaries.md).

## Agent collaboration

Codex feature delivery uses Coder, Reviewer and DevOps with independent commit approvals and a GitHub PR handoff for human review. Read [the agent workflow](docs/agent-workflow.md). Open Codex in this repository to load `.codex/config.toml` and `.codex/agents/`; model settings inherit from the current chat. Local progress is recorded in ignored `handover.md` using [the versioned template](docs/agent-handover-template.md).

Coder can also suggest evidence-based product, technical and workflow improvements through [feature proposals](docs/feature-proposals/README.md). Reviewer and DevOps assess each proposal; implementation requires your explicit approval.
