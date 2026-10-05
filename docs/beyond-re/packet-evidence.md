# Beyond / BeyondEditor 7.1 evidence matrix

This branch implements only protocol behavior supported by 7.1 evidence. Older protocol dumps are
used as semantic-name references only; their field numbers are not accepted as 7.1 ground truth.

## Reference revisions

- AstaPS upstream base: `7e92571033dffb723bd708ba222981f10d073761`
- LunaGC 7.1: `811b224db150982be37c0619e1506d980443cb9b`
- genshin-protocol semantic reference: `15eba9972a0a751f097b357a621dd3245cd158a8`
- NahidaImpact secondary reference: `1d0f662dbc163543b96cadebc0b070a861a24ea1`
- miliastra-unified-mcp evidence reference: `6f227607a5a4104d5387f3b8355714b170d2f9b7`

## Direct 7.1 presence evidence

`_BeyondPlayerInfo` is present in AstaPS generated 7.1 code and independently matches LunaGC 7.1:

- field 1: `uid`
- field 2: online state (`OFFLINE=0`, `ONLINE=1`)
- field 3: world type (`TEYVAT=0`, `BEYOND=1`)
- field 11: optional `_BydPlayerDetailOnlineInfo`

`_BydPlayerDetailOnlineInfo` is also present in both 7.1 sources:

- field 1: state enum (`NONE=0`, `HALL=1`, `DUNGEON=2`, `EDIT=3`)
- field 2: `is_in_team`
- fields 3-8 remain semantically unresolved and keep their generated/unknown identity

`WorldPlayerInfoNotify` contains repeated `_BeyondPlayerInfo` at field 13 in both 7.1 sources.

## Packet matrix before implementation

| Packet/system | 7.1 CmdId | Typed 7.1 schema in AstaPS | Existing handler/response | Confidence | Action |
|---|---:|---|---|---|---|
| `_GetBeyondPlayerInfoReq` | 5960 | Named Req wrapper not present | LunaGC 7.1 sends empty Rsp | high for empty request handling | Implement the reference-backed empty response; no request fields are decoded |
| `_GetBeyondPlayerInfoRsp` | 9779 | Named Rsp wrapper not present | LunaGC 7.1 empty body | high for empty body | Send empty proto3 body while preserving the request client sequence |
| `WorldPlayerInfoNotify` Beyond list | 2076 | yes | notify exists but omits Beyond list | high | Implement typed `_BeyondPlayerInfo` population |
| `_BeyondCreateHallReq/Rsp` | 26704 / 2767 | Named wrappers not present | none | opcodes high, payload shape unresolved | **Deferred/raw-only**; deterministic Hall lifecycle is not implemented without typed 7.1 payload evidence |
| `_BeyondHallChangeAuthModeReq/Rsp/Notify` | 28720 / 22055 / 4532 | Named wrappers not present | none | opcodes high, payload shape unresolved | **Deferred/raw-only**; no inferred field numbers |
| `_BeyondHallChangeTagsReq/Rsp/Notify` | 28249 / 7262 / 326 | Named wrappers not present | none | opcodes high, payload shape unresolved | **Deferred/raw-only**; no inferred field numbers |
| `_BeyondProfileTagListUpdateReq/Rsp` | 24647 / 9509 | Named wrappers not present | none | opcodes high, payload shape unresolved | Raw/decode-only until direct schema is identified |
| `_GetBeyondPlayerSocialInfoReq/Rsp` | unknown / 3347 | response class exists; request CmdId unknown | non-registering empty handler exists | response partial | Keep request deferred; never promote old request CmdId |
| `_GetBeyondPlayerDetailReq/Rsp` | unknown / 4162 | named request/response pair not established | none | response opcode only | Defer request; capture response if observed |
| candidate `UgcDungeon*` watch set | known candidate IDs | varies | unrelated legacy surface | candidate only | Watch/capture only; do not label as Miliastra reuse |

## Final Subagent A implementation boundary

Implemented on `agent/protocol-handlers`:

- evidence-backed `BeyondPlayerState` serialization for online/offline, Teyvat/Beyond,
  NONE/HALL/DUNGEON/EDIT, `is_in_team`, and neutral wire identities for detail fields 3-8;
- in-memory presence resolution for players already known to be in a live world;
- `WorldPlayerInfoNotify` population of the verified repeated `_BeyondPlayerInfo` field 13.

Intentionally not implemented because typed 7.1 request/response payload evidence is missing:

- Hall create/auth/tag lifecycle handlers and GUID/passcode semantics;
- BGM request handling (request CmdId remains unknown);
- Beyond social/detail request handlers where the request CmdId is non-positive/unknown;
- profile-tag update semantics beyond raw capture.

These paths remain recorder/raw-wire work until a live 7.1 capture or trustworthy 7.1 descriptor
establishes the missing payload shapes. No 6.x field number is promoted into 7.1.

Additional 7.1 reference evidence found during live-test preparation:

- LunaGC revision `811b224db150982be37c0619e1506d980443cb9b` contains
  `HandlerGetBeyondPlayerInfoReq`, introduced by
  `ac7144a0ce6d8ef855fbe630aa92c07b903f47da`. It handles CmdId 5960 by returning CmdId 9779
  with an empty proto3 body and the request's client sequence. AstaPS now mirrors exactly that
  narrow behavior; it still does not invent a typed Req/Rsp wrapper or any unresolved field.

## Baseline build/test evidence

The default shell Java 8 cannot configure this project because the repository requires Java 17+
and Spotless uses google-java-format 1.15.0. With JDK 21.0.12, production and test sources compile
successfully. The Gradle `test` task then loses its localhost connection to the forked Gradle Test
Executor (`MessageIOException` caused by `Connection reset by peer`) before producing assertion
results. The same executor failure reproduces with `--no-daemon --max-workers=1`.

This branch therefore treats compilation as the reliable baseline gate and records the test-worker
failure separately from product-test results. Focused tests are still added and invoked; any test
worker failure is reported rather than hidden.
