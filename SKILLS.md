# SKILLS.md — End-to-End Runbook for AstaPS BeyondEditor RE Implementation

This file is the execution runbook. `AGENTS.md` is authoritative for protocol/evidence rules.

The lead model is GPT-5.6 Sol High. It must create two independent implementation subagents.

---

# Phase 0 — Read, inspect, and protect the starting directory

Target:

`F:/Game Servers/AstaPS (7.1.0)`

Expected initial contents:

- `AGENTS.md`
- `SKILLS.md`

Steps:

1. Read `AGENTS.md` completely.
2. Read `SKILLS.md` completely.
3. List the target directory including hidden files.
4. If anything unexpected exists, do not delete it. Report it and adapt safely.
5. Compute SHA-256 for `AGENTS.md` and `SKILLS.md`.
6. Record those hashes in the lead's notes so bootstrap can prove they survived unchanged.

PowerShell example:

```powershell
$Root = 'F:\Game Servers\AstaPS (7.1.0)'
Get-ChildItem -Force $Root
Get-FileHash "$Root\AGENTS.md" -Algorithm SHA256
Get-FileHash "$Root\SKILLS.md" -Algorithm SHA256
```

---

# Phase 1 — Verify GitHub authentication and fork AstaPS

1. Verify GitHub CLI and authentication:

```powershell
gh --version
gh auth status
```

2. Determine the authenticated owner. The expected account is `buonchoi96`, but do not blindly
assume it if `gh` reports another account.

3. Inspect upstream:

```powershell
gh repo view MeChen618/AstaPS --json nameWithOwner,defaultBranchRef,parent,url
```

4. If the authenticated owner's AstaPS fork does not exist, create it without cloning:

```powershell
gh repo fork MeChen618/AstaPS --clone=false
```

5. If the fork already exists, do not create a duplicate. Verify its parent/network relationship.

6. Sync the fork's `main` with upstream `main` before implementation. Prefer the safest non-destructive
GitHub/gh workflow available. Do not discard fork-only commits without explicit review.

7. Record:

- fork owner/name
- fork URL
- upstream HEAD SHA
- fork HEAD SHA
- whether the fork was newly created or reused

Observed upstream baseline on 2026-10-04 was:

`af1e297788b4e11eec0f0e10e26eabb189de6245`

but execution must fetch the actual current SHA.

---

# Phase 2 — Bootstrap the local repo without losing AGENTS.md / SKILLS.md

`git clone <url> .` requires an empty destination. The target initially contains the two guide files,
so preserve them first.

1. Create a temporary backup directory outside the target.
2. Copy or move `AGENTS.md` and `SKILLS.md` there.
3. Verify the backup hashes match Phase 0.
4. Confirm the target directory is now empty.
5. Clone the authenticated user's fork directly into the target.
6. Add `upstream`.
7. Fetch both remotes.
8. Restore `AGENTS.md` and `SKILLS.md` to the repo root.
9. Verify their hashes again.
10. Check whether upstream already contains files with those names. If so, do not overwrite without
a three-way review; reconcile intentionally.
11. Commit the guide files on the feature branch, not directly on `main`, unless they already exist
identically upstream.

Example:

```powershell
$Root = 'F:\Game Servers\AstaPS (7.1.0)'
$Backup = 'F:\Game Servers\_bootstrap-guides\AstaPS-7.1.0'
New-Item -ItemType Directory -Force $Backup | Out-Null

Copy-Item "$Root\AGENTS.md" "$Backup\AGENTS.md"
Copy-Item "$Root\SKILLS.md" "$Backup\SKILLS.md"

Remove-Item "$Root\AGENTS.md"
Remove-Item "$Root\SKILLS.md"

Set-Location $Root
git clone https://github.com/<authenticated-owner>/AstaPS.git .

git remote add upstream https://github.com/MeChen618/AstaPS.git
git fetch --all --prune

Copy-Item "$Backup\AGENTS.md" "$Root\AGENTS.md"
Copy-Item "$Backup\SKILLS.md" "$Root\SKILLS.md"
```

Never use `Remove-Item -Recurse -Force $Root` or any equivalent destructive reset.

---

# Phase 3 — Clone reference repositories

Use a sibling reference directory:

`F:/Game Servers/_refs/`

Required:

```powershell
git clone --branch 7.1 https://github.com/capyb2222/LunaGC_7.1.0.git `
  'F:\Game Servers\_refs\LunaGC_7.1.0'

git clone https://github.com/aeolira/genshin-protocol.git `
  'F:\Game Servers\_refs\genshin-protocol'
```

Recommended secondary reference:

```powershell
git clone https://github.com/Mar7thLover/NahidaImpact-Server.git `
  'F:\Game Servers\_refs\NahidaImpact-Server'
```

Private RE repo, if authentication permits:

```powershell
gh repo clone buonchoi96/miliastra-unified-mcp `
  'F:\Game Servers\_refs\miliastra-unified-mcp'
```

If a reference repo already exists:

- do not delete it;
- inspect `git status`;
- if clean, fetch/pull the intended branch;
- if dirty, leave it untouched and either use a new sibling clone or record the blocker.

Record exact SHAs used for every reference.

---

# Phase 4 — Establish the lead integration branch

In AstaPS:

```powershell
git switch main
git fetch origin upstream --prune
git status
```

Ensure `main` matches the intended fork/upstream base.

Create:

```powershell
git switch -c feature/beyond-editor-re
```

Add and commit the guide files if needed:

```powershell
git add AGENTS.md SKILLS.md
git commit -m "docs: add BeyondEditor RE agent runbook"
```

Do not implement directly on `main`.

---

# Phase 5 — Baseline audit before code changes

The lead performs a short factual audit and writes a local implementation note.

Inspect:

- `PacketOpcodes.java`
- `GameSession.java`
- `GameServerPacketHandler.java`
- packet send/receive conventions
- config/debug conventions
- existing Beyond handlers
- generated 7.1 Beyond protos
- `WorldPlayerInfoNotify`
- `BeyondPlayerInfo`
- `BydPlayerDetailOnlineInfo`
- `GetBeyondPlayerSocialInfoRsp`
- current build/test commands

Create a packet matrix with columns:

- packet name
- 7.1 CmdId
- 7.1 typed proto present?
- current handler present?
- current response present?
- semantic confidence
- action: implement / decode-only / raw-capture / defer

Do not commit speculative semantic names.

Run a baseline build/test before implementation and save the result.

---

# Phase 6 — Create two independent subagents using Git worktrees

The lead creates two branches from the same integration base:

```powershell
git branch agent/protocol-handlers
git branch agent/re-logger
```

Recommended worktree paths:

```powershell
git worktree add 'F:\Game Servers\_worktrees\AstaPS-protocol' agent/protocol-handlers
git worktree add 'F:\Game Servers\_worktrees\AstaPS-re-logger' agent/re-logger
```

Launch two independent GPT-5.6 Sol subagents.

They must receive:

- the repository mission
- `AGENTS.md`
- the relevant section of this runbook
- exact worktree path
- exact branch
- explicit file ownership
- instruction to commit their work
- instruction not to merge/push `main`

They should not wait for each other.

---

# Phase 7A — Subagent A: protocol / Req-Rsp / handlers

Worktree:

`F:/Game Servers/_worktrees/AstaPS-protocol`

Branch:

`agent/protocol-handlers`

Tasks:

1. Audit all existing Beyond packet handlers and send packets.
2. Implement `GetBeyondPlayerInfoReq/Rsp` using real 7.1 proto fields.
3. Replace empty/stub behavior only when evidence supports the replacement.
4. Add a minimal `BeyondPlayerState` / service abstraction sufficient to represent:
   - online/offline
   - Teyvat/Beyond
   - NONE/HALL/DUNGEON/EDIT
   - in-team
   - unresolved detail fields without false names
5. Ensure `WorldPlayerInfoNotify` can expose consistent `BeyondPlayerInfo` where appropriate.
6. Implement minimal Hall state:
   - create Hall
   - allocate Hall GUID
   - auth mode
   - passcode
   - tags
   - player membership/presence
7. Implement typed Req/Rsp/Notify only if 7.1 descriptor/opcode evidence exists.
8. For BGM request with unknown 7.1 opcode, do not invent it.
9. Review:
   - GetBeyondPlayerSocialInfo
   - GetBeyondPlayerDetail
   - ProfileTag update
   and implement only where typed 7.1 schema is trustworthy and useful to bootstrap.
10. Leave gacha/costume/report as secondary unless client bootstrap requires them.
11. Add focused unit/integration tests.
12. Document which packets remain raw/deferred.

Important:

- older 6.x protocol names may guide semantic naming only;
- never copy 6.x field numbers into 7.1;
- do not touch recorder hooks owned by Subagent B;
- avoid `PacketOpcodes.java` unless absolutely necessary; prefer a handoff note for the lead.

Commit logical units and return a structured handoff.

---

# Phase 7B — Subagent B: RE recorder / debug logger

Worktree:

`F:/Game Servers/_worktrees/AstaPS-re-logger`

Branch:

`agent/re-logger`

Tasks:

1. Add a research-mode packet recorder at the earliest safe decoded-frame point in
   `GameSession.handleReceive`.
2. Add S2C capture before encryption in `GameSession.send`.
3. Recorder must work for handled and unhandled packets.
4. Parse `PacketHead` when possible:
   - client sequence
   - sent_ms
   - preserve unknown header fields
5. Write structured JSONL metadata.
6. Write raw payload files by SHA-256, without truncation.
7. Deduplicate raw payload artifacts.
8. Add a bounded generic protobuf wire-tree decoder for unknown schemas.
9. Ensure parse failures never stop packet handling.
10. Add semantic event adapters only for evidence-backed messages.
11. Add a configurable watch set for:
    - `Beyond*`
    - `GetBeyond*`
    - `TakeBeyond*`
    - candidate `UgcDungeon*`
12. Do not call UGC packets "Miliastra" unless capture proves reuse.
13. Redact sensitive semantic fields:
    - passcodes
    - report/chat text
    - account/session/token-like data
14. Keep raw capture explicit opt-in and document that raw bytes may contain sensitive data.
15. Add:
    - recorder-disabled test
    - handled packet capture test
    - unhandled packet capture test
    - large payload test
    - malformed protobuf test
    - redaction test
    - raw-bytes-preservation test
16. Avoid Beyond gameplay handler files owned by Subagent A.
17. Avoid `PacketOpcodes.java` unless the lead explicitly requests it.

Commit logical units and return a structured handoff.

---

# Phase 8 — Lead review of both subagents

Do not merge blindly.

For each branch:

```powershell
git log --oneline feature/beyond-editor-re..agent/protocol-handlers
git diff feature/beyond-editor-re...agent/protocol-handlers

git log --oneline feature/beyond-editor-re..agent/re-logger
git diff feature/beyond-editor-re...agent/re-logger
```

Review for:

- invented CmdIds
- copied old field numbers
- incorrect semantic certainty
- logger side effects
- secret leakage
- path hard-coding
- unnecessary generated-file churn
- duplicated logic
- test gaps

Ask the responsible subagent to fix its own branch if practical.

---

# Phase 9 — Integrate using Git

On `feature/beyond-editor-re`:

1. Merge or cherry-pick the recorder branch.
2. Run focused tests.
3. Merge or cherry-pick the protocol branch.
4. Resolve conflicts manually.
5. Lead owns final shared-file edits, especially `PacketOpcodes.java`.
6. Run formatter if the repo defines one.
7. Re-run focused tests after every conflict resolution.

Preserve subagent commits unless there is a strong reason to squash.

---

# Phase 10 — Add RE-specific semantic logging

After integration, add a thin semantic layer that converts verified decoded packets into events such
as:

```text
BEYOND_PLAYER_PRESENCE
BEYOND_WORLD_TRANSITION
BEYOND_EDITOR_STATE
HALL_CREATE_REQUEST
HALL_CREATED
HALL_AUTH_CHANGED
HALL_TAGS_CHANGED
HALL_BGM_CHANGED
UGC_CANDIDATE_ENTER
UGC_CANDIDATE_SAVE
UGC_CANDIDATE_SETTLE
```

Every semantic event must keep a pointer to the source packet SHA-256 and packet record.

Unknown fields remain available as raw/wire-tree evidence.

---

# Phase 11 — Build the editor-action capture matrix

Prepare a manual/client-driven capture checklist for later live validation:

1. login
2. enter Miliastra/Beyond
3. open Hall
4. create Hall
5. change Hall auth
6. change Hall tags
7. change BGM
8. enter editor
9. create/open level
10. place one entity
11. move/rotate the entity
12. save
13. switch room
14. start Play Test
15. trigger one graph/event
16. take damage
17. die
18. revive
19. finish/quit Play Test
20. return to editor
21. publish or change publish settings if available

For each action, the later runtime test should produce:

- C2S packets
- S2C packets
- opcode names/IDs
- raw payload hashes
- semantic decode
- before/after Beyond presence
- Hall/room/play GUID correlations
- candidate UGC packet occurrence
- notes on client-only behavior

Do not fabricate live results during implementation.

---

# Phase 12 — Integration tests and quality gates

Run:

- compile/build
- all changed-area tests
- full practical project test suite
- clean-start server smoke test if feasible without external proprietary assets
- recorder-disabled smoke
- recorder-enabled synthetic packet smoke

Explicitly verify:

- unhandled Beyond packet is still captured
- payload >128 bytes is fully persisted
- recorder does not depend on console `isShowPacketPayload`
- malformed data cannot crash the active session
- raw payload hash matches persisted bytes
- semantic redaction affects only semantic log output, not raw evidence
- presence enum maps correctly:
  - NONE
  - HALL
  - DUNGEON
  - EDIT
- Req→Rsp Hall state tests are deterministic
- no unsupported packet received a fabricated handler

---

# Phase 13 — Documentation

Add concise repository docs describing:

- how to enable the Beyond RE recorder
- output directory
- JSONL schema
- privacy/sensitive-data warning
- how to clear captures
- how to correlate a packet with raw payload
- known 7.1 Beyond packet matrix
- deferred/unknown packet list
- UGC legacy/candidate classification
- how future `miliastra-unified-mcp` ingestion should consume records

Do not claim official-server equivalence.

---

# Phase 14 — Final Git audit

Run:

```powershell
git status
git log --oneline --decorate --graph -n 30
git diff upstream/main...HEAD
git remote -v
```

Check for accidental artifacts:

- `debug/beyond-re/`
- raw `.bin` captures
- logs
- `.env`
- tokens
- local DBs
- reference repos
- IDE files
- build outputs
- absolute `F:\...` paths in source/config defaults

Add ignore rules for generated capture directories if needed.

---

# Phase 15 — Push to fork

Push the integration branch:

```powershell
git push -u origin feature/beyond-editor-re
```

Do not merge to fork `main` unless explicitly instructed.

If useful, create a draft PR from:

`feature/beyond-editor-re` → fork/upstream target as appropriate

but do not open an upstream PR unless explicitly requested.

---

# Phase 16 — Final handoff

Return a precise report containing:

1. fork URL
2. current fork main SHA
3. upstream base SHA
4. feature branch SHA
5. Subagent A branch + commits
6. Subagent B branch + commits
7. merged commit graph
8. implemented Req/Rsp/Notify list
9. raw-only/deferred packet list
10. recorder config and output format
11. tests with exact commands/results
12. pre-existing failures, if any
13. files/docs added
14. live client validation still required
15. next recommended packet/capture experiments

Do not say "full BeyondEditor support" unless live-client evidence actually proves it.
