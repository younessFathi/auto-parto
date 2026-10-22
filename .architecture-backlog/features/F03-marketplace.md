# F03 Marketplace

Implement the hybrid buyer-request and seller-listing marketplace with guarded lifecycle transitions.

**Status:** Done

- [AP-0007](../tickets/AP-0007-part-requests.md): Manage buyer-owned part requests.
- [AP-0008](../tickets/AP-0008-part-listings.md): Manage seller-owned part listings.
- [AP-0009](../tickets/AP-0009-offers-and-matching.md): Create offers and bounded marketplace matching.

## Completion Tracking

AP-0007, AP-0008, and AP-0009 are implemented and verified. The marketplace now provides authenticated buyer requests, seller listings, target-derived offers, guarded optimistic transitions, bounded notification fan-out, and indexed matching plans for every supported filter shape. PostgreSQL/Testcontainers and HTTP authorization coverage exercise ownership, invalid filters, concurrent transitions, deterministic matching, and result limits.
