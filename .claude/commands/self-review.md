---
allowed-tools: Read, Grep, Glob, Bash(git diff:*)
description: Critical self code-review
---
## Changes
!`git diff HEAD~1`

Review the above as a strict senior engineer. Check, in priority order:
1. Correctness and edge cases
2. Security (injection, exposed secrets, auth)
3. Performance issues
4. Readability and naming
5. Test coverage gaps
Give specific, actionable feedback. Be honest about real problems.