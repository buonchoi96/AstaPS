# Live opcode schema audit — protocol subagent A

Audit date: 2026-10-05. Branch: `agent/protocol-handlers`.

**Decision: no new protocol handler or response is justified by the available
evidence.** The ten requested unknown opcodes remain unidentified at the semantic
and Req/Rsp level. The repository identifies Hall create as `26704 / 2767`, but
neither audited 7.1 generated source set supplies the corresponding payload
descriptors. No response ID, field meaning, Hall state transition, or editor
transition is inferred from timing, payload size, or a successful protobuf parse.

This is a documentation-only continuation of subagent A. It does not change the
existing presence implementation, packet registration, recorder, scene logic, or
lead checkout. Runtime and scene compatibility remain the lead's responsibility.

## Revisions and search boundary

| Source | Revision inspected | Role / limitation |
|---|---|---|
| AstaPS protocol worktree | `b4c611377eeb4e4090947cf08d86eaafe724cae2` | Primary generated descriptors and opcode map; clean starting worktree |
| AstaPS implementation upstream base | `7e92571033dffb723bd708ba222981f10d073761` | Recorded base of the existing implementation; not a claim about current upstream HEAD |
| Locally available `upstream/main` | `6a4174e19749b87e6bfd934a4023b2342e71d9ab` | Read-only opcode-map check; the requested entries agree with this worktree |
| Remote upstream `main` | `b1c5af21a26deaaa9983f485d98cd32e5693e64e` | Observed with `git ls-remote` during this audit; its source tree was not fetched or audited |
| LunaGC `7.1` | `811b224db150982be37c0619e1506d980443cb9b` | Secondary 7.1 generated source and opcode map; selected wire layouts compared below |
| genshin-protocol `master` | `15eba9972a0a751f097b357a621dd3245cd158a8` | Local versions 5.8.0 and 6.0.0–6.6.0; semantic oracle only |
| NahidaImpact-Server `main` | `1d0f662dbc163543b96cadebc0b070a861a24ea1` | Secondary `Proto/CmdIds.cs` and `Proto/NahidaProto`; no target opcode/schema association found |
| miliastra-unified-mcp `main` | `6f227607a5a4104d5387f3b8355714b170d2f9b7` | Evidence-model reference; no target network opcode/schema association found |
| animegamepatch `master` | `562248bf212518872e3027d579fa511716f39294` | Additional local reference; no target packet descriptor/opcode evidence found |

All five reference checkouts were clean when inspected. Searches covered opcode
maps, generated Java and embedded `FileDescriptorProto` data, available `.proto`
sources, and relevant research/code files outside AstaPS. There was no standalone
`.desc`, `.pb`, or `.protobin` descriptor artifact in the searched reference
checkouts or the protocol worktree outside generated build/cache files. Library
JARs in the protocol checkout are bcrypt, bytes, and kcp, not game protocol dumps.
The referenced `tools/extract.py` in the TPS documentation is not tracked in this
checkout; embedded descriptors were decoded directly with Python protobuf.

The primary opcode map is
`src/main/java/emu/grasscutter/net/packet/PacketOpcodes.java` (Git blob
`b309deb5c23e683af06057be4bb0b56fa6565593`). LunaGC's map at the same relative path
has Git blob `84795143ce546b1d98c9db908febbb263ee13383`.

## What the available live capture actually establishes

The lead checkout's existing recorder session
`debug/beyond-re/session-f272aa08-95ec-4655-a2c7-e1dbee304388/` was inspected
read-only. At the audit snapshot its `packets.jsonl` contained nine complete
records, SHA-256:

```text
ef712700e7ed6d86fba064d1fbe8e6ed1f15e3503f883e3c9a0a75a22a4eff6d
```

The manifest says `clientVersion: 7.1.0`, format version 1, and creation time
`2026-10-05T13:20:38.675758500Z`. Both `astaPsCommit` and `upstreamBaseSha` are
`unknown`. The version is recorder metadata, not independent verification of a
particular client executable/build. This audit cannot retrospectively establish
the exact server binary revision or official client/server schema compatibility.

All nine records are C2S, named `UNKNOWN`, with recorder decode status `PARTIAL`.
Every referenced raw artifact exists and its length and SHA-256 match its packet
record (nine checks passed, two distinct deduplicated payloads). The observed
payloads are:

- `29163`: two bytes; generic wire tree has field **3**, wire type **0**, varint
  **1**. Its meaning and declared protobuf type are unknown. A varint does not
  distinguish an enum, boolean, or any of the compatible integer encodings.
- All other packets present in this snapshot: zero payload bytes. This is not
  evidence that their declared request schemas contain zero fields: proto3
  default values and omitted optional/repeated fields also serialize to empty.

The nonempty payload artifact is
`raw/d8ffb41f9785cc166ba6d923dd209402959c6dcdf797a4fd526a4cf77aec289d.bin`.
The empty artifact is
`raw/e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855.bin`.
Only hashes, wire structure, and packet metadata are documented here; capture
files are not committed.

## Controlled UI evidence supplied during this audit

The lead supplied the following controlled observations, without promoting
unknown packet names. Subagent A did not operate the client or UI. Packet times
and raw artifacts were cross-checked read-only against the new session
`debug/beyond-re/session-06df5fc9-0900-4f2d-b756-0c49c2c1bf00/`.

The inspected new-session snapshot contains **17** complete records, all C2S,
all `UNKNOWN`/`PARTIAL`, with SHA-256 of `packets.jsonl`:

```text
19de8b40bb0279c0104e2408aaba890f1f341598869f533cf6ba2c0c02aee065
```

Counts: 29163 twice, 8314 eleven times, 21078 once, 26169 once, 3705 twice.
Its manifest says `clientVersion: 7.1.0`, creation time
`2026-10-05T16:46:56.046186600Z`, and unknown server/base SHAs. The two distinct
raw payload hashes are the same as the earlier session; all 17 record length/hash
checks pass. Later appends are outside this pinned snapshot.

Times below are local UTC+07:00 on 2026-10-05; capture timestamps are UTC.

| Controlled action reported by lead | Packet evidence in new session | Observed result / evidence boundary |
|---|---|---|
| Open Lobby menu, 23:46:56 | line 1: C2S 29163, sequence 1546, length 2, hex `1801`, UTC `16:46:56.041Z` | List showed no lobby. Field 3/wire 0/value 1 is established; message identity and field meaning remain unknown |
| Remain on the list | lines 2–5: C2S 8314, empty, sequences 1551/1555/1559/1563 | First four timestamps are `16:47:02.937`, `09.928`, `16.939`, `23.926` UTC: roughly seven-second recurrence in this trace, without a semantic name or required response inferred |
| Confirm Go to Public Lobby, 23:47:30 | line 6: C2S 21078, sequence 1567, empty, UTC `16:47:30.307Z`; line 7: C2S 8314, sequence 1568, empty, UTC `16:47:30.937Z` | Both reported unhandled. Their succession does not establish a Req/Rsp pair; both are C2S |
| Create Lobby → New Personal Lobby; then CreateTemplate | Lead reported three custom slots, an empty basic-template modal, and no C2S from clicking CreateTemplate | UI observation only. The recorder's selected watch stream cannot independently prove absence of all network traffic |
| Click Import and Edit Placement with empty selection, 23:48:51 | line 12: C2S 26169, sequence 1608, empty, UTC `16:48:51.764Z` | Reported unhandled; Preparing then timeout. This is an observed trigger under that selection state, not a declared request schema or a response mapping |
| Click My Miliastra Wonderland, 23:51:45 | line 16: C2S 3705, sequence 1714, empty, UTC `16:51:45.885Z` | Lead reported waiting and subsequent Request failed, with failure checking still in progress. No corresponding response is established |

The later 29163 record (line 13, sequence 1636) has the same two-byte payload.
The second 3705 record (line 17, sequence 2139, UTC `16:52:55.534Z`) is empty;
its specific UI trigger was not supplied. No action is assigned to that record
by extrapolating from the first click.

These observations justify action-indexed raw capture notes. They do not justify
enabling a handler, naming an unknown packet, sending an empty success response,
or changing Beyond/Hall/editor state.

## Per-opcode evidence matrix

Line numbers refer to the source revisions above and the nine-record capture
snapshot, not a claim that later appended captures are absent.

| Opcode | AstaPS map evidence | LunaGC 7.1 map evidence | Inspected live records | Schema / Req→Rsp decision |
|---:|---|---|---|---|
| 29163 | No entry | No entry | line 1, sequence 6186; field 3/wire 0/value 1 | No descriptor association, semantic name, declared type, or response ID established |
| 21078 | No entry | No entry | line 2, sequence 6190; empty | No descriptor association or response ID established |
| 8314 | line 1454: **commented** `JKGHMMOOOIL = 8314` | line 2609: active `JKGHMMOOOIL = 8314` | lines 3–4, sequences 6203/6220; empty | Obfuscated map label only; matching message descriptor absent; no response ID established |
| 26169 | line 1637: **commented** `DPPGPOIHEDO = 26169` | line 2360: active `DPPGPOIHEDO = 26169` | line 5, sequence 6236; empty | Obfuscated map label only; matching message descriptor absent; no response ID established |
| 3705 | No entry | No entry | lines 6–7, sequences 6309/6329; empty | No descriptor association or response ID established |
| 1871 | No entry | No entry | line 8, sequence 6344; empty | No descriptor association or response ID established |
| 29224 | No entry | No entry | line 9, sequence 6349; empty | No descriptor association or response ID established |
| 27037 | No entry | No entry | Not in inspected snapshot; reported live in task | No local descriptor association or response ID established |
| 23682 | line 632: **commented** `GBMIGDBECBC = 23682` | line 2458: active `GBMIGDBECBC = 23682` | Not in inspected snapshot; reported live in task | Obfuscated map label only; matching message descriptor absent; no response ID established |
| 8928 | No entry | No entry | Not in inspected snapshot; reported live in task | No local descriptor association or response ID established |
| 26704 | line 1713: `_BeyondCreateHallReq` | lines 1617/1920: underscored and plain aliases | Not in inspected snapshot; known request anchor supplied in task | Repository Req association is supported; request payload descriptor absent |
| 2767 | line 288: `_BeyondCreateHallRsp` | lines 1618/1921: underscored and plain aliases | Not in inspected snapshot; known response anchor supplied in task | Repository Rsp association is supported; response payload descriptor absent |

The three commented AstaPS entries are not active packet constants or handler
registrations. LunaGC's active entries corroborate their *obfuscated map labels*,
not their semantics or applicability to the exact live client build. They must
not be promoted to semantic names or enabled handlers by this audit.

There are no S2C records in either inspected snapshot and no deterministic evidence
for an unknown opcode's response. The recorder correlation IDs include C2S
direction and client sequence; they identify these records, not a proven
transaction with a response. No observed unknown opcode is mapped to a social,
Hall, editor, or legacy UGC request here.

## Embedded 7.1 descriptor evidence

The AstaPS inventory decoded all **2,067** generated Java descriptor blocks
(**2,112** message declarations including nested messages). LunaGC contains
**2,651** blocks: **2,650** decoded successfully, producing **2,680** unique
message names including nested messages. Its
`SceneEntityUpdateNotifyOuterClass.java` descriptor fails
`FileDescriptorProto.FromString` with a wire-format `DecodeError`. That unrelated
file contains a modified `VisionType.VisionType_proto` dependency string. It was
left unchanged; the failed descriptor is a limitation of the secondary inventory.

Neither decoded inventory contains `_BeyondCreateHallReq`,
`_BeyondCreateHallRsp`, their plain aliases, `JKGHMMOOOIL`, `DPPGPOIHEDO`, or
`GBMIGDBECBC`. A full generated-source text search also finds none of those
identifiers, including in the one undecodable LunaGC file. No enum named for
CmdId/opcode was found in the successfully decoded descriptors. Thus the maps
cannot be completed by an embedded command-ID enum in this corpus. An unrelated
descriptor's ability to parse an empty payload or field 3 is not an association.

The following actual types are present. Paths are relative to
`src/generated/main/java/emu/grasscutter/net/proto/` in the respective repository.
Hashes are SHA-256 of the concatenated, unescaped embedded **descriptor bytes**,
not file hashes. Different Java options, ordering, and names can change these
hashes while preserving the selected wire layout.

| Message | AstaPS file / descriptor SHA-256 | LunaGC file / descriptor SHA-256 |
|---|---|---|
| `_HallWorldInfo` | `HallWorldInfo.java` / `b4fc7a9162fdda89caf55918a0b7e7a1d190867a68394ea9ba985e3bf4dfff2e` | `_HallWorldInfoOuterClass.java` / `3fc91be7ff21a90ec90d7701b54b8de99fc7f60a913ad0ee6fddf573dd406b47` |
| `_BeyondPlayerInfo` | `BeyondPlayerInfo.java` / `08f73b21cc2a1203bb5850c61de74a431986d59b92d1b3da1cad51b8cd3fd0bc` | `_BeyondPlayerInfoOuterClass.java` / `32c7d372abba589a0a0791c29021c3f512482e4c94a220fce2b1a53946e96ff3` |
| `_BydPlayerDetailOnlineInfo` | `BydPlayerDetailOnlineInfo.java` / `dc49c82ca09239aac864ad3c4ad5ff807045754e7c012c8c803165efa838219c` | `_BydPlayerDetailOnlineInfoOuterClass.java` / `db00581678ee5945a66833c544bdae006200d950364c8f1ec8638968c80ed6ba` |
| `WorldPlayerInfoNotify` | `WorldPlayerInfoNotifyOuterClass.java` / `adcc83a96b6f145c71de7e4a73437980d7c3474774b1acaa9d5c9d67c1ac45c5` | `WorldPlayerInfoNotifyOuterClass.java` / `e585e5873c38b25f922986fd3a4cc2c787a5b134ebfc84383603fb8fc6566403` |

For these four pairs, a descriptor comparison passed for field numbers, types,
labels, referenced type names, and oneof grouping. This is a limited structural
cross-check, not compatibility proof for the entire LunaGC protocol or the live
client. `_BeyondPlayerInfo`'s oneof name is `detail` in AstaPS and
`_byd_player_detail_online_info` in LunaGC; the grouping agrees.

`_HallWorldInfo` has only these fields:

| Field | AstaPS descriptor name | LunaGC descriptor name | Type | Evidence boundary |
|---:|---|---|---|---|
| 1 | `IGBAONPMDHF` | `IGBAONPMDHF` | `uint64` | Meaning unresolved; do not call it a Hall GUID without additional evidence |
| 2 | `is_enter_edit_mode` | `FDCEGNBMOIK` | `bool` | Descriptive name exists in AstaPS; not evidence of an editor transition or unknown opcode binding |
| 3 | `GNNMNEGOHDN` | `GNNMNEGOHDN` | `uint32` | Meaning unresolved; field-number coincidence does not bind opcode 29163 to this message |

In AstaPS, the descriptor block starts at `HallWorldInfo.java:668` and its field
constants are at lines 135, 146, and 157. This is a data message, not the missing
Hall creation Req/Rsp wrapper. It supplies no create-response `retcode`, auth,
passcode, tag, or created-Hall field layout.

The presence types remain the useful, already implemented narrow surface:

- `_BeyondPlayerInfo`: field 1 `uint32 uid`; field 2 online enum
  `OFFLINE=0 / ONLINE=1`; field 3 world enum `TEYVAT=0 / BEYOND=1`; field 11
  `_BydPlayerDetailOnlineInfo` in a oneof.
- `_BydPlayerDetailOnlineInfo`: field 1 enum `JCEIDIDBFHM`
  (`NONE=0 / HALL=1 / DUNGEON=2 / EDIT=3`); field 2 `bool is_in_team`;
  field 3 `.KFMCDDAIOML JGJNGLBJLJO`; field 4 `uint64 LBFGNBPFNIH`;
  field 5 `bool LEGKLKCNKJB`; field 6 `.DEDHCCHNKEO MKODMDPGDHK`;
  field 7 `uint64 JKBOBLJFKBP`; field 8 `uint64 HPLGJAAJOGG`.
- `WorldPlayerInfoNotify`: field 4 repeated `.OnlinePlayerInfo
  player_info_list`; field 12 repeated `uint32 player_uid_list`; field 13
  repeated `._BeyondPlayerInfo _beyond_player_info_list`; field 14 repeated
  `.PlayerWidgetInfo player_widget_info_list`.

These layouts do not establish `GetBeyondPlayerInfo` Req/Rsp wrappers, Hall
creation semantics, or any of the ten unknown opcode bindings.

The lead separately committed reference-backed empty `GetBeyondPlayerInfo`
response handling in `587587d1de122f1bc53cb26d1a803ff616ff4e95`, and recorder
correlation changes in `9d25e8849a62fe3f08ba3d7846edc8efcd22371c` (read-only
commit inspection). Those changes are outside the protocol worktree's pinned
starting revision and are not repeated
or incorporated here. They do not supply the missing typed wrappers or establish
new unknown-opcode pairings. The lead reported 126 nonintegration tests passing;
that is a separate integration result, not this worktree's test count.

## Popular Miliastra Wonderlands: established authkey error path

The lead reported that clicking Popular Miliastra Wonderlands at **23:50:31**
emitted **GetAuthkeyReq 23630 → GetAuthkeyRsp 9681**, followed immediately by
Internal server error and Failed to get data. These are known mapped packets
with an existing handler association, independent of the missing Lobby opcodes.
They are not present in the inspected unknown-packet recorder snapshots; the
live observation here is attributed to the lead, not to a fabricated raw record.

The code establishes the association directly:

- AstaPS `PacketOpcodes.java:2587 / :2693` defines `23630 / 9681`; LunaGC's map
  has the same pair at lines 329–330.
- `src/main/java/emu/grasscutter/server/packet/recv/HandlerGetAuthkeyReq.java:7`
  registers the request opcode. Its handler sends `new PacketGetAuthkeyRsp()`
  unconditionally at line 12 and does not parse the request payload. The audited
  generated corpora do not contain a named `GetAuthkeyReq` descriptor.
- `src/main/java/emu/grasscutter/server/packet/send/PacketGetAuthkeyRsp.java:10`
  selects the response opcode; lines 12–16 build the typed `GetAuthkeyRsp` with
  only `RET_SVR_ERROR` set. `RetcodeOuterClass.java:10256` defines its value as
  **1**. The same handler/error-response implementation exists in LunaGC.
- The corresponding handler and response files in the lead checkout were also
  inspected read-only and retain that error path.

The typed schema is therefore supported **for the response**, while the request
is an established registered path with an unresolved typed payload. The local
error response has just field 6/wire 0/value 1 (canonical protobuf body `3001`);
this is derived from code/descriptor, not claimed as a captured response hash.
No authkey, game business, app ID, or success metadata is constructed.

`GetAuthkeyRspOuterClass.java` embeds the following fields:

| Name | Type | AstaPS field | LunaGC field |
|---|---|---:|---:|
| `sign_type` | `uint32` | 1 | 50001 |
| `authkey` | `string` | 3 | 3 |
| `auth_appid` | `string` | 5 | 5 |
| `retcode` | `int32` | 6 | 6 |
| `authkey_ver` | `uint32` | 8 | 50000 |
| `game_biz` | `string` | 15 | 15 |

Descriptor SHA-256: AstaPS
`cb1ee671e35407a057fe15bdb5f57f40017b08fd8eea915a4a339fedec80cb31`;
LunaGC `229d63747515fe1bee2fe2f220cc51ba687c4e4540b9eb077845a93aba82c52b`.
This mismatch is another reason not to import a secondary schema wholesale.
The currently emitted error field agrees between the descriptors.

The existing explicit server-error response explains the immediate client failure
in the lead's observation. It does not demonstrate a functioning recommendation
backend, key issuer, or successful authorization. The success path is deferred
until a real compatible backend and authorization contract exist. No fake key,
invented success response, or guessed local recommendation data is introduced.

## Rejected older-version and numeric coincidences

genshin-protocol 6.6.0 contains named `_BeyondCreateHallReq` and
`_BeyondCreateHallRsp` at `Deobfuscated.proto:8342` and `:14290`, with its own
CmdIds **1976 / 24853**. It suggests semantic concepts such as Hall tags,
description, auth mode, created Hall GUID, and retcode. Its field numbers are
not accepted as 7.1 evidence and are deliberately not copied into a handler.

Other examples show why a decimal opcode search across versions cannot identify
the live 7.1 packets:

- 6.4.0 `Deobfuscated.proto:60924` assigns **1871** to
  `_LanV6CardNpcLikeReq`, not a verified 7.1 Beyond request.
- 6.5.0 `Deobfuscated.proto:64903` assigns **8928** to
  `PotionResetChallengeRsp`.
- 6.6.0 `Deobfuscated.proto:60865` assigns **26704** to `KIIMOFONDEE`, a
  different message than the named Hall-create request in the 7.1 map.
- miliastra-unified-mcp's `docs/research/unknown-closeout-2026-10-04.json:14626`
  contains key **8314** under
  `dispositions[29].physicalCensus.payloadLengths`: it is a payload length
  histogram, not an opcode or network schema mapping.
- LunaGC's handbook has item ID **23682**; it is unrelated to network packet IDs.

## Verification and reproducibility

Commands were run in the protocol worktree. JDK **21.0.12** was selected for
Gradle; the default shell Java is unsuitable for this repository. No server,
game client, or UI was operated.

```powershell
git status --short
git branch --show-current
git rev-parse HEAD upstream/main
git ls-remote https://github.com/MeChen618/AstaPS.git refs/heads/main

rg -n '\b(29163|21078|8314|26169|3705|1871|29224|27037|23682|8928|26704|2767)\b' src/main/java/emu/grasscutter/net/packet
rg -n 'GBMIGDBECBC|JKGHMMOOOIL|DPPGPOIHEDO|BeyondCreateHall' src/generated

# With JAVA_HOME/PATH selecting JDK 21.0.12:
.\gradlew.bat compileJava compileTestJava test --tests emu.grasscutter.game.beyond.BeyondPlayerStateTest --tests io.grasscutter.ProtoReadTest -PexcludeTags=integration --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx4g'
```

Result: **BUILD SUCCESSFUL in 16s**. Production/test compilation tasks were
up-to-date. JUnit XML reports **10 tests, 0 failures, 0 errors, 0 skipped**:
five `BeyondPlayerStateTest` tests and five `ProtoReadTest` tests. An earlier
presence-only invocation also passed all five tests (**BUILD SUCCESSFUL in
27s**). The historical Gradle-worker failure documented in `packet-evidence.md`
did not occur in these runs. These tests validate existing presence serialization
and generic wire reading, not any unknown request, response, or Hall lifecycle.
Server integration tests and the full suite were not run in this scoped audit.

Embedded descriptor extraction used Python protobuf **7.36.2**. The essential
read-only operation for each generated Java file was:

```python
import ast, re
from google.protobuf import descriptor_pb2

block = re.search(
    r'(?:java\.lang\.String|String)\[\]\s+descriptorData\s*=\s*\{(.*?)\};',
    source_text, re.S,
).group(1)
pieces = re.findall(r'"(?:\\.|[^"\\])*"', block)
raw_descriptor = ''.join(ast.literal_eval(p) for p in pieces).encode('latin-1')
descriptor = descriptor_pb2.FileDescriptorProto.FromString(raw_descriptor)
```

The inventory visited all top-level and nested message declarations and enum
names, recorded the LunaGC parse exception, and compared the four selected
message pairs by field number/type/label/type-name/oneof index. Source searches
also covered the undecodable file. Raw capture verification read existing
artifacts, compared byte lengths, and recomputed SHA-256 without modifying them.
Across the two pinned snapshots, **26 record length/hash checks passed**.

## Gaps and next evidence required by the lead

1. Obtain and pin a 7.1 command-to-descriptor association for each unknown opcode.
   For the three map labels, the missing message descriptor is the immediate
   blocker; for the other seven, both descriptor association and message identity
   are missing. Empty requests and field-number coincidences are insufficient.
2. Supply the relevant raw records for 27037, 23682, 8928, and Hall create to
   extend these pinned capture snapshots. Preserve opcode, direction, sequence,
   payload hash, and exact client/server build provenance.
3. Establish both 7.1 Hall-create payload layouts before implementing allocation,
   return codes, auth/passcode/tags, membership, or presence transitions. Known
   `26704 / 2767` constants do not establish where or how those values are encoded.
4. Prove unknown Req/Rsp associations through a trustworthy opcode map/descriptor
   or explicit deterministic protocol evidence. Timing and nearby traffic cannot
   supply a response opcode, and C2S sequences alone do not prove a transaction.
5. Keep the ten unknowns under explicit raw/generic-wire observation. Their
   semantic decoding remains `RAW` or `PARTIAL`; no gameplay handler, guessed
   success response, or UGC/BeyondEditor semantic label is introduced here.
6. Preserve the known `GetAuthkey` error path until a compatible recommendation
   backend/key issuer is available. Its typed error response is a separate
   supported path; it does not establish any Lobby packet or authorize fake keys.

The only file added by this continuation is this audit. No opcode map, generated
schema, protocol handler, test source, recorder option, or scene file was changed.
