# P9: Agent Scripts in `/scripts`, Output in `/website`

**Offline processing scripts live in `/scripts`. Their output goes to `/website`.**

## Existing Pattern

```
scripts/generate_quiz_json.js   →  website/quiz-data.js
scripts/generate_video_links.py →  website/video-links.js
```

## New Scripts Follow the Same Pattern

```
scripts/parse_subtitles.py         →  website/subtitle-data.js
scripts/chunk_subtitles.py         →  website/chunked-data.js
scripts/extract_hierarchy.py       →  website/mindmap-data.js
scripts/map_quiz_to_concepts.py    →  website/quiz-concept-map.js
scripts/download_aws_icons.sh      →  website/aws-icons/
scripts/export_anki.py             →  exports/aws-mindmap.apkg
scripts/export_standalone_html.py  →  exports/aws-mindmap.html
```

## Rule

- Scripts are **never** loaded by the browser
- Scripts **always** output to `/website` (for browser files) or `/exports` (for external formats)
- Scripts can use any language/runtime (Python, Node.js, shell)
- Scripts must be runnable independently (no shared state between runs)
- Output files must be self-contained (no runtime dependencies)

## Applied In

- 0043 (subtitle parsing agent)
- 0044 (semantic chunking agent)
- 0045 (LLM hierarchy extraction agent)
- 0046 (quiz-concept mapping agent)
- 0055 (Anki export agent)
- 0056 (standalone HTML export agent)
- 0061 (pipeline orchestrator)
