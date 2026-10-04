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
direction, timestamps, session/player/endpoint context, opcode/name, header and payload lengths,
PacketHead sequence/timestamp when parseable, the payload hash/path, decode status, redacted typed
fields when a generated 7.1 message exists, and bounded generic protobuf wire trees. Nested
length-delimited values that merely parse like protobuf are marked `CANDIDATE`.

Semantic JSON never intentionally exposes passcodes, chat/report text, credential/token/session
secrets. Redaction does not modify `raw/*.bin`; treat the raw directory as sensitive evidence.

Delete a capture by stopping the server and removing its `debug/beyond-re/session-<id>` directory.
The whole capture root is gitignored and must not be committed.
