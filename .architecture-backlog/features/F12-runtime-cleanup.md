# F12 Runtime Cleanup

Remove verified dead code and temporary compatibility paths so the production runtime has one clear ownership and persistence model.

**Status:** In Progress

## Tickets

- [AP-0040](../tickets/AP-0040-remove-dead-runtime-state.md): Remove dead runtime state and verified obsolete helpers.

AP-0037 remains responsible for the complete MongoDB removal. AP-0038 remains responsible for the provider-neutral device-token contract. F12 removes cross-cutting leftovers only after their replacement boundaries are verified.
