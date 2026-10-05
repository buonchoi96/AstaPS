# GitLab reference audit and live validation — 2026-10-06

## Outcome

Both user-supplied references are useful. The protocol repository supplies missing 7.1
presence/Hall wrappers and a corrected map-layer mapping. The data repository supplies
7.1 Beyond/Hall/editor tables and confirms that the local scene/layer ID sets are already
current. Neither repository establishes a working lobby/editor bootstrap by itself.

The client still reports `KeyNotFoundException` for key `3`. It subsequently enters the
world after the error is dismissed and reconnect completes. The controlled LunaGC scene
bootstrap experiment and the isolated map-layer correction both reproduced the error.
Neither change is claimed to fix scene entry. The broad LunaGC experiment was reverted.

## Pinned references

| Source | Branch | Revision | Scope |
|---|---|---|---|
| [kitkat-multiverse/genshin-protocol](https://gitlab.com/kitkat-multiverse/genshin-protocol/-/tree/d9d67f94da0402f53acefbd59944aa052ee78ca6/7.1.0) | master | `d9d67f94da0402f53acefbd59944aa052ee78ca6` | `7.1.0/Deobfuscated.proto`, obfuscated schema, name translation |
| [Dimbreath/animegamedata2](https://gitlab.com/Dimbreath/animegamedata2/-/tree/792978e5503ecfba73dcb3562ed44a0d35a2abe2) | main | `792978e5503ecfba73dcb3562ed44a0d35a2abe2` | release data, commit labelled `CNRELWin7.1.0_R48379043_S48511369_D48533839` |

The running client displays `OSRELWin7.1.0_R48379043_S48511369_D48533839`.
Matching numeric build components support comparison; CN/OS equivalence is not assumed
for every resource. References were cloned outside the AstaPS checkout; proprietary data
and full protocol dumps were not added to this branch.

Comparing same-name positive opcodes finds **1,539 matches and 25 differences** against
AstaPS. Presence and the cited Hall anchors match. Examples of differences include
`GetOnlinePlayerListReq`, `BackMyWorldReq`, and `GetPlayerFriendListReq`. This rules out
blindly replacing the entire opcode map or generated protocol directory.

## Applied protocol changes

`_GetBeyondPlayerInfoReq` 5960: repeated UID field **14**, reason field **10**.
`_GetBeyondPlayerInfoRsp` 9779: presence list field **2**, reason field **3**, retcode field **15**.
These fields are taken from the pinned 7.1 source, not a 6.x schema. The existing generated
`_BeyondPlayerInfo` descriptor supplies the nested type; the newly recovered wrappers use
protobuf dynamic descriptors to avoid replacing unrelated generated classes.

The handler now answers requested online players using actual server membership and the
existing Beyond presence registry. Registered offline players are emitted as offline;
untracked offline UIDs are omitted. Reason and client sequence are preserved. Queries do
not create Hall/editor transitions or change player/database state. A real client RPC for
5960 was not observed in the controlled UI captures, so this path is **reference-backed
and unit-tested, not live RPC-confirmed**.

The corrected `_MapLayerInfo` schema puts layer IDs in field **2**, group IDs in field **7**,
and an unresolved repeated uint32 field in **5**. AstaPS's generated Java names for 2/7
are reversed. The world-scene packet now populates those wire fields correctly; the logger
uses the corrected names without changing generated sources or raw bytes. A live capture
verified 431 layer IDs in field 2 and 190 group IDs in field 7. Key `3` remains reproducible.

## Additional contracts found, not yet implemented

| Packet | CmdId | Newly available evidence / remaining limitation |
|---|---:|---|
| BeyondCreateHallReq/Rsp | 26704 / 2767 | Request fields: tags 1, description 6, auth mode 9, unresolved uints 7/11/14. Response: created GUID 2, unresolved bool 3, retcode 4. Full Hall scene entry remains unimplemented. |
| BeyondHallChangeAuthModeReq/Rsp/Notify | 28720 / 22055 / 4532 | Auth/passcode layouts now present in 7.1; no active Hall lifecycle is established in this server. |
| BeyondHallChangeTagsReq/Rsp/Notify | 28249 / 7262 / 326 | Typed layouts now available; Hall state/scene lifecycle still needs implementation and live verification. |
| BeyondHallChangeBgmReq | 500 | New reference opcode; retain as reference evidence until request path compatibility is verified. |
| GetBeyondPlayerSocialInfoReq | 4086 | New reference request opcode; existing AstaPS placeholder must not be blindly registered. |
| GetBeyondPlayerDetailReq | 20818 | New reference request opcode; detail/profile implementation still absent. |

The previous packet matrix described the available evidence at the time of its audit.
Its missing-schema statements for these wrappers are superseded by this document.
Unknown fields keep their source identities; none acquire guessed semantic names.

## Why the current UI still fails

Controlled native-client actions with the fixed proxy produced:

| Action | C2S evidence | Response / visible result |
|---|---|---|
| Popular Miliastra Wonderlands | GetAuthkeyReq 23630, sequence 1840 | Existing GetAuthkeyRsp 9681, same sequence, retcode 1; UI reports internal server error. No authkey/web backend has been reconstructed. |
| My Miliastra Wonderland | 3705, empty body | Unhandled; request failed. New source calls it `AFPHMLJMEMO`, also empty, with no trustworthy response pairing. |
| Lobby list | 29163, body `1801` | Unhandled; empty lobby list. New source `PGIAGAOMLGA`, bool field 3. |
| Go to Public Lobby | 21078, empty body | Unhandled; preparing/loading times out. New source `BEANHNIBGED`, empty. |
| Import and Edit Placement | 26169, empty body | Unhandled; preparing/loading times out. New source `ENFMEAMFFBI`, unresolved uint32 fields 6/7. |
| Lobby background polling | 8314 | Unhandled; new source `DJOABBEEDHK`, repeated uint64 field 10. |

No controlled action emitted 26704. These unnamed bootstrap messages cannot be replaced
by the named Hall create handler or assigned guessed Rsp opcodes. No editor/save/Play Test/
publish success is claimed. Legacy `UgcDungeon*` remains `UGC_CANDIDATE_*` until observed reuse.

## Data comparison

| Table | Reference / local rows | Missing IDs on either side |
|---|---:|---:|
| MapLayerExcelConfigData | 431 / 431 | 0 |
| MapLayerGroupExcelConfigData | 190 / 190 | 0 |
| MapLayerFloorExcelConfigData | 316 / 316 | 0 |
| SceneExcelConfigData | 2377 / 2377 | 0 |
| WorldAreaConfigData | 578 / 578 | 0 scene/area tuples |

ID 3 is present in the scene and map-layer-group tables, absent from the map-layer and floor
tables. Equal ID sets do not prove equal obfuscated columns or loader semantics. No local
resource bundle was overwritten. BeyondHall (20 rows), WidgetBeyondHall, and numerous
Beyond editor/entity/property tables are available for future template/bootstrap research.

## Launcher and proxy validation

The separate animegamepatch worktree uses branch `codex/proxy-original-module-path`, commit
`e7fe1897627274c748b38db0817c5d6f354394be`, from reference base
`562248bf212518872e3027d579fa511716f39294`. It resolves the original DLL relative to the proxy
module, rather than the executable. The live proxy reports **47/47 exports forwarded**.
Rust regression test: `cargo +nightly test --offline` — 1 passed. Release build:
`cargo +nightly build --release --locked --offline` — succeeded, with existing warnings.

Local launcher fixes stop/join the patch watchdog before controller restoration and use .NET
SHA-256 instead of relying on `Get-FileHash` availability in the long-lived Windows PowerShell
5.1 controller. A PS5 hash fixture passed. Administrator `ServerStart.cmd` launches were
stopped through Ctrl+C and restored the exact original DLL; original SHA-256:
`D09977107FBA3C264C43F24EC68108E7BECC17524B51CC0ED3C9FBCC5C743E4E`.

The launcher has substantial pre-existing user changes and local paths. It remains locally
modified; the task-only delta is preserved in ignored
`local/astrolabe-recovery/launcher-fixes.patch`. The fixed proxy, original DLL backup, captures,
and temporary scene experiment are local artifacts, not committed binaries.

## Recorder and tests

Output remains opt-in `debug/beyond-re/session-<id>/`: manifest, packets JSONL, semantic events
JSONL, and full SHA-256-deduplicated `raw/*.bin`. Console traces include both directions,
roles, opcode/name, UID/session, sequence, pair key, and lengths. Unhandled watched traffic
is captured before dispatch. Known presence wrappers now decode as KNOWN; malformed typed
payloads still retain raw evidence and decode as FAILED. Unknown wire candidates remain explicit.

Semantic chat/passcode/authkey/credential values are redacted; raw capture remains sensitive.
Normal combo-auth diagnostics now log presence flags instead of token/database-key prefixes.
Unhandled payload hex requires the explicit existing payload option.

Final practical command (JDK 21.0.12):

```powershell
./gradlew.bat test jar -PexcludeTags=integration -PjarFilename=grasscutter-gitlab-verified --no-daemon --max-workers=1
```

**145 tests, 0 failures, 0 errors; BUILD SUCCESSFUL.** Regression tests were observed failing
before the layer-wire and presence changes; corrected layer semantic logging also had a red
test before its fix. Tests cover controlled presence, reason/sequence, empty/malformed input,
unknown-field raw preservation, typed logging, disabled recorder, unhandled capture, large
payloads, and redaction. Five fresh live sessions contained **808 records**; every raw artifact
matched its recorded length and SHA-256, with **0 mismatches**.

The default integration test starts another real server and was not rerun alongside the
managed server; an earlier run was stopped to avoid a port collision. The existing formatter
fails on JDK 21 `JCImport.getQualifiedIdentifier`; no formatter success is claimed.

Older manifests incorrectly labelled fork integration base `7e92571033dffb723bd708ba222981f10d073761`
as upstream base. Those captures remain unchanged. Later runs correctly use actual upstream
merge-base `0fac870873a35e88b9f969aa1b242a832b602005`. Build/proto confidence metadata is recorded
for new sessions; unavailable provenance is explicitly `unknown`.

## Git handoff and remaining work

Fork: https://github.com/buonchoi96/AstaPS, fork main
`7e92571033dffb723bd708ba222981f10d073761`. Fetched upstream main:
`b1c5af21a26deaaa9983f485d98cd32e5693e64e`. Actual upstream merge-base:
`0fac870873a35e88b9f969aa1b242a832b602005`. Integration branch: `feature/beyond-editor-re`.
Fork-only history was preserved; main was not reset, merged, or rewritten.

Exactly two implementation branches/worktrees were used from clean base
`f0515f7b29422214a733a125b197848eb33f5e69`:

- `agent/protocol-handlers`, sibling `_worktrees/AstaPS-protocol`, head
  `33f4929cd92f87a8fda321ff2fbf2a304794593d`; implementation
  `b81029e83b56c577d5d6c729e0ceee4cbaf1aa9c`, documentation
  `b4c611377eeb4e4090947cf08d86eaafe724cae2`, live audit `33f4929...`.
- `agent/re-logger`, sibling `_worktrees/AstaPS-re-logger`, head
  `4af5557d00fab8743815163971f5d6da61d196c4`; recorder
  `b5e3ce97dfb837855bde900339b629700ec2316c`, follow-ups
  `67fe3e5ba710127f0e896566bc29554b85c6c62d`,
  `55a0f4bc0886cf6a3b843ec13b8013d5fd5aad96`,
  `cd51c4b111255977f5a349bffe43cdcbae69e7ec`,
  `4af5557d00fab8743815163971f5d6da61d196c4`. Lead reviewed before integration.

Latest implementation commits:

- `384c395a7216f4bc7a4a57a7c4d0351d9d8ee704` — remove combo credential prefixes.
- `11f310154262932bafbe70037af76375d3cafe0a` — corrected map layer wire lists.
- `76f62572b95632e9e433e785bda95c79a3fae5c5` — populated presence query response and recovered decoder.

Next work is to identify the client handler at RVA `0xf04a5e0` and its caller chain for key 3,
establish response pairings for the live unnamed lobby/editor bootstrap requests, then implement
Hall metadata plus the actual Hall scene transition from the newly available 7.1 contracts.
The task remains partially complete: Hall/editor/Play Test are not working, even though the
recorder and populated presence RPC now have tested implementations.
