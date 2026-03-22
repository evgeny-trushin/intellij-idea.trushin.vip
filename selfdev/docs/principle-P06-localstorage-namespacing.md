# P6: localStorage Namespacing

**All persisted data uses a prefixed key.**

## Existing Keys

| Key | Purpose |
|---|---|
| `aws-sa-pro-quiz-progress` | Quiz history & scores (STORAGE_KEY) |
| `aws-sa-pro-theme` | Dark/light theme preference (THEME_KEY) |
| `awsQuizMuted` | Sound mute state |

## New Keys (same prefix pattern)

| Key | Purpose |
|---|---|
| `aws-sa-pro-mm-bookmarks` | Mind map bookmarked nodes |
| `aws-sa-pro-mm-notes` | Mind map user notes per node |
| `aws-sa-pro-mm-state` | Mind map expand/collapse state |

## Rule

- All keys must start with `aws-sa-pro-` prefix
- Values must be JSON-serializable
- Respect the existing storage size cap (~5MB localStorage budget)
- New keys must be documented in the requirement that introduces them

## Applied In

- 0059 (bookmarks & notes)
- 0050 (live quiz stats overlay reads existing quiz progress)
- 0039 (export/import includes new keys)
