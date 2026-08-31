## What changed



## Help-chat knowledge

The in-app CLM help chat only knows what's written in
`backend/src/main/java/com/acme/clm/ai/PageDocs.java` (per-route guides + the
`features()` cross-cutting block) and the navigation map in
`AiService.helpChat(...)`. If it isn't updated, the assistant can't answer
questions about this change.

- [ ] This PR adds/changes a user-facing feature, and I updated the matching
      page guide and/or the `features()` block in `PageDocs.java` (and the nav
      map in `AiService` if a new route was added).
- [ ] — or — no user-facing change; nothing to update.

## Testing


