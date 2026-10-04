# AGENTS.md — AstaPS 7.1 BeyondEditor / RE Harness

## 0. Mission

This repository is a fork of `MeChen618/AstaPS` used to implement the minimum evidence-backed
Miliastra/Beyond server surface needed for:

1. Beyond player presence/bootstrap.
2. Beyond Hall lifecycle request/response handling.
3. Structured packet capture and semantic debug logging.
4. Safe observation of BeyondEditor / Play Test client traffic.
5. Later integration with `buonchoi96/miliastra-unified-mcp`.

The immediate objective is **not** to pretend that a complete official Miliastra server has been
reconstructed. The objective is to implement only what is supported by 7.1 protocol evidence,
while capturing enough raw evidence to reverse-engineer the remaining surface.

Read `SKILLS.md` before doing any repository mutation.

---

## 1. Repository identity and remotes

Target working directory on Windows:

`F:/Game Servers/AstaPS (7.1.0)`

Expected Git remotes after bootstrap:

- `origin` → the authenticated user's fork, expected `buonchoi96/AstaPS`
- `upstream` → `MeChen618/AstaPS`

Primary upstream reference:

- `MeChen618/AstaPS`
- branch: `main`
- observed baseline on 2026-10-04: `af1e297788b4e11eec0f0e10e26eabb189de6245`
- do **not** assume that SHA is still HEAD when execution begins; fetch and record the actual current upstream HEAD.

Protocol/reference repositories:

- `capyb2222/LunaGC_7.1.0`, branch `7.1`
  - observed 2026-10-04 reference SHA: `811b224db150982be37c0619e1506d980443cb9b`
- `aeolira/genshin-protocol`, branch `master`
  - older 6.x semantic-name oracle only; never treat its field numbers as 7.1 ground truth
- `Mar7thLover/NahidaImpact-Server`, branch `main`
  - optional secondary semantic/reference source
- `buonchoi96/miliastra-unified-mcp`, branch `main`
  - RE evidence model/reference; observed 2026-10-04 SHA:
    `6f227607a5a4104d5387f3b8355714b170d2f9b7`

Keep reference repositories outside the AstaPS repository, preferably under:

`F:/Game Servers/_refs/`

Do not create nested Git repositories inside AstaPS.

---

## 2. Evidence hierarchy

Use the following authority order.

### Tier A — 7.1 direct evidence

Highest authority:

1. Live Genshin 7.1 client capture against this server.
2. AstaPS 7.1 generated proto/descriptors.
3. AstaPS 7.1 packet IDs that are explicitly marked live/runtime confirmed.
4. LunaGC 7.1 proto/descriptors, after verifying compatibility with the AstaPS 7.1 client build.

### Tier B — version-adjacent semantic evidence

Useful but not authoritative for field numbers:

- genshin-protocol 6.4/6.5/6.6 deobfuscated message names.
- older LunaGC / NahidaImpact protocol snapshots.

Tier B can suggest names such as `hall_desc`, `auth_mode`, `player_uid_list`, or
`created_hall_guid`, but it must **not** justify copying old field numbers into a 7.1 proto.

### Tier C — hypotheses

Anything inferred from names, natural pairing, or runtime intuition is a hypothesis until a
controlled 7.1 capture confirms it.

Never silently promote a hypothesis to a verified protocol definition.

---

## 3. Known 7.1 Beyond facts that may be used

The following are already supported by 7.1 repository evidence and may be used as anchors:

### Beyond player presence

`_BeyondPlayerInfo` contains:

- `uid`
- online state: `OFFLINE=0`, `ONLINE=1`
- world type: `TEYVAT=0`, `BEYOND=1`
- optional `_BydPlayerDetailOnlineInfo`

`_BydPlayerDetailOnlineInfo` contains an enum with:

- `NONE=0`
- `HALL=1`
- `DUNGEON=2`
- `EDIT=3`

and also includes:

- `is_in_team`
- additional unresolved messages/booleans/GUID-like `uint64` fields

Do not invent names for unresolved fields. Log both field path/number and raw value.

### Relevant AstaPS 7.1 opcode anchors

At the observed AstaPS baseline, these include:

- `_GetBeyondPlayerInfoReq = 5960`
- `_GetBeyondPlayerInfoRsp = 9779`
- `_BeyondCreateHallReq = 26704`
- `_BeyondCreateHallRsp = 2767`
- `_BeyondHallChangeAuthModeReq = 28720`
- `_BeyondHallChangeAuthModeRsp = 22055`
- `_BeyondHallChangeAuthModeNotify = 4532`
- `_BeyondHallChangeTagsReq = 28249`
- `_BeyondHallChangeTagsRsp = 7262`
- `_BeyondHallChangeTagsNotify = 326`
- `_BeyondHallChangeBgmRsp = 8125`
- `_BeyondHallChangeBgmNotify = 8500`
- `_BeyondHallRoomCardChangeNotify = 2210`
- `_BeyondPlayerQuitFromHallNotify = 1786`
- `_BeyondProfileTagListUpdateReq = 24647`
- `_BeyondProfileTagListUpdateRsp = 9509`
- `_BeyondRenameCostumeSetReq = 9829`
- `_BeyondRenameCostumeSetRsp = 27114`
- `_BeyondDelCostumeSetReq = 29980`
- `_BeyondDelCostumeSetRsp = 4134`
- `_BeyondDoGachaReq = 25639`
- `_BeyondDoGachaRsp = 24323`
- `_BeyondChatRsp = 638`
- `_BeyondChatChannelUpdateNotify = 24334`
- `_BeyondChatChannelDataNotify = 29773`
- `_BeyondPlayerReportReq = 2673`
- `_BeyondPlayerReportRsp = 24190`
- `_GetBeyondPlayerSocialInfoRsp = 3347`
- `_GetBeyondPlayerDetailRsp = 4162`

Some request CmdIds are still unknown in 7.1. A non-positive placeholder is **not** a usable
wire opcode.

### UGC-dungeon candidate watch set

These are useful for observation but must remain classified as legacy/uncertain until live
Miliastra 7.1 traffic proves reuse:

- `_UgcEnterDungeonReq = 1971`
- `_UgcEnterDungeonRsp = 1660`
- `_UgcDungeonChangeEditRoomReq = 1986`
- `_UgcDungeonChangeEditRoomRsp = 24272`
- `_UgcDungeonSaveDataReq = 20869`
- `_UgcDungeonPublishDungeonReq = 25221`
- `_UgcDungeonChangePublishedSettingReq = 28882`
- `_UgcDungeonChangePublishedSettingRsp = 4555`
- `_UgcDungeonCandidateTeamCreateReq = 20707`
- `_UgcDungeonCandidateTeamCreateRsp = 26819`
- `_UgcDungeonPlayRecordNotify = 5825`
- `_UgcDungeonSettleNotify = 5257`
- `_UgcDungeonPublishResultNotify = 25669`

Do not call these "Miliastra BeyondEditor packets" until a 7.1 client capture proves it.

---

## 4. Non-negotiable protocol rules

1. **Never fabricate a 7.1 CmdId.**
2. **Never copy a 6.x field number into 7.1 just because the field name looks correct.**
3. If a request has a known opcode but no trustworthy 7.1 typed proto:
   - capture the raw payload;
   - decode a generic protobuf wire tree if useful;
   - mark semantic decoding `PARTIAL` or `RAW`;
   - do not create a fake typed handler.
4. Preserve raw bytes before semantic parsing.
5. Unknown fields must survive observation and must be represented in research logs.
6. A parser failure must never drop the packet from the raw capture stream.
7. A logger must not change gameplay behavior.
8. A debug/research feature must be disabled by default unless the existing project convention
   explicitly makes debug logging opt-in another way.
9. Do not leak secrets in normal logs. Hall passcodes, chat/report text, account tokens and similar
   data must be redacted or hashed in semantic logs. Raw payload capture may contain sensitive data,
   so it must be explicit research-mode output and clearly documented.

---

## 5. Initial implementation scope

Implement the smallest evidence-backed surface first.

### P0 — packet recorder

Add a structured Beyond RE recorder that can observe packets before handler dispatch and before
server encryption on send.

It must support:

- direction: `C2S` / `S2C`
- server monotonic/high-resolution timestamp
- wall-clock timestamp
- session identifier
- player UID when available
- remote endpoint if already safely available
- opcode
- packet name if known
- header length / payload length
- client sequence ID when present
- client `sent_ms` when present
- payload SHA-256
- optional raw-payload artifact path
- parse/decode status: `KNOWN`, `PARTIAL`, `RAW`, `FAILED`
- known decoded fields
- generic unknown-wire tree
- semantic event classification when evidence-backed
- correlation/transaction identifier when deterministically available

Use JSONL for event metadata. Store large raw payloads as binary files, deduplicated by SHA-256.

The recorder must work even for **unhandled** opcodes.

### P1 — Beyond presence/bootstrap

Implement evidence-backed handling for:

- `GetBeyondPlayerInfoReq/Rsp`
- `WorldPlayerInfoNotify` population of `BeyondPlayerInfo`
- state representation for:
  - ONLINE/OFFLINE
  - TEYVAT/BEYOND
  - NONE/HALL/DUNGEON/EDIT
  - `is_in_team`
  - unresolved GUID/flags preserved as explicitly unknown fields

Do not invent lifecycle transitions. State transitions must be driven by implemented server actions
or observed 7.1 traffic.

### P2 — minimal Hall lifecycle

Implement only fields supported by current 7.1 evidence:

- create Hall
- Hall GUID allocation
- Hall auth mode
- Hall passcode handling
- Hall tags
- BGM response/notify when the corresponding request path is known
- player enter/leave Hall presence
- in-memory state first unless existing AstaPS persistence patterns make a tiny persistent model
  clearly safer

Expected known auth enum:

- NONE
- ONLY_INVITE
- NEED_PASSCODE
- FREEDOM

### P3 — social/profile request surfaces

Implement typed handlers only where the 7.1 proto is present and trustworthy, prioritizing:

- GetBeyondPlayerSocialInfo
- GetBeyondPlayerDetail
- ProfileTag update

Costume/gacha/report systems are lower priority unless the client blocks bootstrap on them.

### P4 — UGC/BeyondEditor observation

Do **not** prematurely implement legacy `UgcDungeon*` behavior.

First record whether the actual 7.1 Miliastra client emits them during:

- enter editor
- create/open level
- place entity
- move entity
- save
- switch room
- start Play Test
- die/revive
- finish Play Test
- publish/change publication settings

If they are observed, add typed decoders only after verifying the corresponding 7.1 schema.

---

## 6. Structured RE logging requirements

Recommended output root:

`debug/beyond-re/`

Example:

```text
debug/beyond-re/
  session-<id>/
    manifest.json
    packets.jsonl
    semantic-events.jsonl
    raw/
      <sha256>.bin
```

`manifest.json` should record:

- AstaPS commit SHA
- upstream base SHA
- build timestamp
- client version if known
- enabled recorder options
- proto source revisions
- schema confidence notes

Each packet JSONL record should be self-contained enough to correlate later with
`miliastra-unified-mcp`.

Preferred semantic event examples:

- `BEYOND_PLAYER_PRESENCE`
- `BEYOND_WORLD_TRANSITION`
- `BEYOND_EDITOR_STATE`
- `HALL_CREATE_REQUEST`
- `HALL_CREATED`
- `HALL_AUTH_CHANGED`
- `HALL_TAGS_CHANGED`
- `HALL_BGM_CHANGED`
- `UGC_CANDIDATE_ENTER`
- `UGC_CANDIDATE_SAVE`
- `UGC_CANDIDATE_SETTLE`

Use `UGC_CANDIDATE_*` until Miliastra reuse is proven.

---

## 7. Generic protobuf wire-tree fallback

When a typed 7.1 descriptor is missing, the research logger may emit a generic wire tree containing:

- field number
- wire type
- scalar value when safely decoded
- byte length
- nested-wire candidate only when bounded and non-destructive
- raw slice hash

Never claim a nested `bytes` field is a protobuf message merely because it can be parsed as one.
Mark such interpretations `CANDIDATE`.

The raw payload remains the source of truth.

---

## 8. Git discipline

All implementation work must use Git.

### Branches

Lead integration branch:

`feature/beyond-editor-re`

Subagent branches:

- `agent/protocol-handlers`
- `agent/re-logger`

Do not let both subagents edit the same shared files unless the lead explicitly reassigns ownership.

### Suggested ownership

#### Subagent A — Protocol / handlers

Own:

- Beyond-specific handler classes
- Beyond send packet classes
- Beyond state/service classes
- verified proto/source additions required by those handlers
- focused protocol tests
- documentation of verified Req/Rsp semantics

Avoid editing:

- `GameSession.java`
- `GameServerPacketHandler.java`
- global debug configuration
- recorder implementation

#### Subagent B — RE recorder / logger

Own:

- packet recorder
- structured JSONL/raw capture
- generic wire-tree decoder
- recorder config
- hooks in `GameSession` / packet dispatch/send path
- recorder tests

Avoid editing:

- Beyond gameplay handlers
- Hall state/service logic
- generated protocol semantics except read-only use

### Shared/high-conflict files

Lead agent owns final edits to:

- `PacketOpcodes.java`
- root build/config files if both branches need them
- integration documentation
- any schema or generated-code conflict

### Commit policy

Each logical change gets its own commit. Example sequence:

1. `chore: bootstrap BeyondEditor RE branch`
2. `feat(re): add raw Beyond packet recorder`
3. `test(re): cover recorder framing and redaction`
4. `feat(beyond): add player presence state`
5. `feat(beyond): handle get player info`
6. `feat(beyond): add minimal hall lifecycle`
7. `test(beyond): cover hall req rsp semantics`
8. `docs(re): document BeyondEditor capture matrix`

Do not squash subagent work before lead review. The final branch may be rebased/squashed only if
the user explicitly requests it.

---

## 9. Subagent operating model

The lead agent must create exactly two implementation subagents unless the user changes the request.

They work independently from the same clean base commit using separate Git worktrees.

### Subagent A

Goal: protocol/Req-Rsp/handler implementation.

Must return:

- branch name
- commit list
- files changed
- tests run
- known gaps
- any unresolved 7.1 schema question

### Subagent B

Goal: recorder/debug logger implementation.

Must return the same structured handoff.

The lead must review both diffs before integration.

Do not allow a subagent to merge to `main`.

---

## 10. Testing gates

Before merging each agent branch:

1. compile the project
2. run focused tests for changed code
3. run existing relevant protocol/server tests
4. prove recorder disabled-mode is behaviorally inert
5. prove recorder captures unhandled packets
6. prove large payloads are stored without truncation
7. prove semantic redaction does not modify raw source bytes
8. prove malformed protobuf cannot crash the session logger
9. prove `GetBeyondPlayerInfoRsp` reflects controlled Beyond presence state
10. prove Hall create/auth/tag operations have deterministic Req→Rsp state transitions

Before pushing the integrated branch:

- run the full project test suite that is practical in the repo
- inspect `git diff upstream/main...HEAD`
- ensure no reference repository files, captures, tokens, raw sensitive payloads, build products,
  IDE state or local absolute paths are committed
- verify `git status` is clean
- verify `origin` is the fork and `upstream` is `MeChen618/AstaPS`

If a test fails because of a pre-existing upstream failure, document the exact command, failure,
and proof that it reproduces on the clean base.

---

## 11. Acceptance criteria for this task

This task is complete only when:

- fork exists under the authenticated user and is synced to upstream at the recorded base SHA
- local AstaPS repo is in `F:/Game Servers/AstaPS (7.1.0)`
- `AGENTS.md` and `SKILLS.md` survive bootstrap unchanged unless intentionally updated by the lead
- `origin` and `upstream` are correct
- two subagent branches/worktrees were used
- structured recorder exists and captures C2S/S2C raw packet metadata
- unknown/unhandled Beyond-related traffic is captured
- `GetBeyondPlayerInfoReq/Rsp` is no longer an empty stub if 7.1 schema permits correct handling
- minimal Beyond presence state is implemented
- minimal Hall Req/Rsp paths are implemented where 7.1 schema/opcode evidence is sufficient
- unsupported/unknown protocol pieces remain explicit, not fabricated
- focused and integration tests pass, or pre-existing failures are proven and documented
- integrated commits are pushed to the fork on a non-main feature branch unless the user explicitly
  asks for direct `main` changes
- final report contains exact SHAs, branches, tests, implemented packets, deferred packets, and
  capture path/configuration

---

## 12. Stop conditions

Stop and report instead of guessing if any of the following occurs:

- GitHub authentication cannot create/access the fork.
- The target directory contains unexpected user files beyond the expected guide files.
- A required 7.1 packet has no trustworthy opcode and no live capture.
- A required 7.1 proto shape cannot be established without copying old-version field numbers.
- The client/server version does not match the assumed 7.1 family.
- A change would require deleting user data or rewriting unrelated history.
- A secret/token/account credential is about to be committed.

A stop condition does not block useful independent work: continue all unaffected tasks and provide
the precise blocker in the final handoff.

---

## 13. Final reporting format

Report:

1. fork URL and fork HEAD
2. upstream base SHA
3. local branch and all subagent branches
4. worktrees used
5. commits created
6. packets implemented
7. packets captured but not semantically decoded
8. protocol assumptions deliberately left unresolved
9. recorder output format/path
10. tests and exact results
11. remaining next actions
