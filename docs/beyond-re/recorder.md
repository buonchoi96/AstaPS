# Beyond RE packet recorder

The Beyond RE recorder is an explicit research-mode capture facility. It is disabled by default
because raw packet payloads can contain passcodes, chat/report content, credentials, tokens, or
other sensitive player data.

Enable it under `server.game.beyondReRecorder` in the generated config:

```json
{
  "enabled": true,
  "captureAllPackets": false,
  "outputDirectory": "debug/beyond-re"
}
```

With `captureAllPackets=false`, the recorder watches packet names beginning with `Beyond`,
`GetBeyond`, or `TakeBeyond` (including underscore-prefixed generated names), plus the explicit
legacy `UgcDungeon*` candidate opcode set in the config. Those UGC packets remain labelled
`UGC_CANDIDATE_*`; the recorder does not claim they are Miliastra/BeyondEditor packets without a
live 7.1 capture proving reuse.

Each runtime session writes:

```text
debug/beyond-re/
  session-<id>/
    manifest.json
    packets.jsonl
    semantic-events.jsonl
    raw/
      <payload-sha256>.bin
```

Raw payload files are complete, named by SHA-256, and deduplicated. `packets.jsonl` contains
`recordId`, direction, timestamps, session/player/endpoint context, opcode/name, header and payload
lengths, PacketHead sequence/timestamp when parseable, the payload hash/path, decode status,
redacted typed fields when a generated 7.1 message exists, and bounded generic protobuf wire trees.
Nested length-delimited values that merely parse like protobuf are marked `CANDIDATE`.

The recovered 5960/9779 presence wrappers use pinned 7.1 dynamic descriptors. Corrected
`_MapLayerInfo` semantic names follow wire fields 2/7 instead of the reversed generated Java
names; older captures are not rewritten. New manifests include proto source revisions, confidence
notes, and `buildTimestamp` from `-Dgrasscutter.build.timestamp=<UTC build time>` when supplied.
Missing build/commit provenance is recorded as `unknown`, not inferred from file modification time.

When a Req/Rsp PacketHead carries a non-zero client sequence, `requestCorrelationId` is written as
`<session-id>:<client-sequence>` on both sides so a response that preserves the request sequence
can be paired directly with its request. Notifications intentionally do not get this pair key,
because independently generated server sequence numbers may overlap client request sequences. The
live DEBUG trace prints the same sequence and pair key with the `[BeyondRE]` prefix.

`semantic-events.jsonl` is intentionally thinner. Each event carries the same `recordId` and
`payloadSha256` as its source packet record so downstream tooling can join semantic observations
back to `packets.jsonl` and then verify the exact bytes in `raw/<payloadSha256>.bin`. The currently
verified semantic surface includes `BEYOND_PLAYER_PRESENCE` for `WorldPlayerInfoNotify`. Legacy UGC
events remain explicitly `UGC_CANDIDATE_*` until a live 7.1 capture proves reuse.

Semantic JSON never intentionally exposes passcodes, chat/report text, credential/token/session
secrets. Redaction does not modify `raw/*.bin`; treat the raw directory as sensitive evidence.

Delete a capture by stopping the server and removing its `debug/beyond-re/session-<id>` directory.
The whole capture root is gitignored and must not be committed.

## Downstream ingestion

Future `miliastra-unified-mcp` ingestion should treat `packets.jsonl` as the packet index and raw
SHA-256 artifacts as the evidence source of truth. Import `semantic-events.jsonl` only as derived
annotations, join it through `recordId`, and preserve `payloadSha256` on every derived record. A
wire-tree `CANDIDATE` node is structural evidence only; it must not be promoted to a typed field
without a trustworthy 7.1 descriptor or corroborating live-client capture. Likewise,
`UGC_CANDIDATE_*` labels must remain candidate evidence rather than writable Miliastra semantics.
