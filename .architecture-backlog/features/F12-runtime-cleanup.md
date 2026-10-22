# F12 Runtime Cleanup

Remove verified dead code and temporary compatibility paths so the production runtime has one clear ownership and persistence model.

**Status:** Done

## Tickets

- [AP-0040](../tickets/AP-0040-remove-dead-runtime-state.md): Remove dead runtime state and verified obsolete helpers.

AP-0037 remains responsible for the complete MongoDB removal. AP-0038 remains responsible for the provider-neutral device-token contract. F12 removes cross-cutting leftovers only after their replacement boundaries are verified.

## Completion Tracking

AP-0040 is complete. The unused JVM-local device-token state and its unreferenced helper were removed while PostgreSQL authority, Redis projection recovery, and the Redis-only compatibility path remain unchanged.
