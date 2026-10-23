# F12 Runtime Cleanup

Remove verified dead code and temporary compatibility paths so the production runtime has one clear ownership and persistence model.

**Status:** Done

## Tickets

- [AP-0040](../tickets/AP-0040-remove-dead-runtime-state.md): Remove dead runtime state and verified obsolete helpers.

AP-0037 completed the MongoDB removal. AP-0038 was never created as an active ticket; its provider-neutral device-token concerns are covered by AP-0002 and AP-0016. F12 removes cross-cutting leftovers only after their replacement boundaries are verified.

## Completion Tracking

AP-0040 is complete. The unused JVM-local device-token state and its unreferenced helper were removed while PostgreSQL authority and Redis projection recovery remain unchanged.
