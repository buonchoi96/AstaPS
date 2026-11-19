# AGENTS.md — AstaPS 7.2.0

## Mandatory startup procedure
1. Read this file fully BEFORE inspecting, editing, building, or committing project code.
2. Confirm repository root, remotes, branch, and Git state: run git status --short, git diff, and git diff --cached before any changes.
3. Do not overwrite user modifications. Do not execute git clean -fd, git reset --hard, or force-push.
4. Record baseline HEAD and upstream/fork ancestry before selecting patches.
5. Limit all changes to the current objective; no unrelated refactoring.

## Remote repositories and Git workflow
- Read-only upstream: https://github.com/MeChen618/AstaPS (origin).
- Destination fork: https://github.com/buonchoi96/AstaPS (fork); destination branch main.
- Compare fork/main with origin/main using git merge-base, git rev-list --left-right --count, and appropriate commit/file diffs. Do not reapply fixes already present in upstream.
- Before every commit, review git diff --check, git diff --cached, changed filenames, and possible secrets.
- Build and run feasible checks; make focused, descriptive commits. Push only verified fixes to fork/main. Never push to upstream or rewrite shared history.
- Fetch fork/main immediately before pushing. If a remote commit appeared, reconcile with a fast-forward only, or stop on conflict.
- Report resulting SHA, build/test outcome, remaining uncommitted changes, and real push result.

## Version-specific dependencies and runtime
- This project targets client protocol 7.2.0. Build with JDK 21 using .\gradlew.bat jar -PskipHandbook=1, producing grasscutter-7.2.0.jar.
- Resources: https://github.com/MeChen618/AstaPS-Resource. Sibling directory ..\resources stores the separately cloned pack. Locally create an ignored NTFS junction resources pointing to that sibling; never stage resource data in this repository.
- Resource coverage is still evolving and the 7.2 update uses a 7.1 baseline, per the resource repository documentation.
- Start on Windows with ServerStart.cmd, which calls tools/start-server.ps1 and starts a dedicated loopback-only MongoDB Community process before the JAR.
- Persistent database path: database in the repo root. The launcher uses TCP 127.0.0.1:27017, consistent with the server default, and refuses to reuse a listener that it does not own.
- Windows MongoDB binaries (Community 8.0.38 and Shell 2.14.2) are installed at E:\MongoDB.
- MongoDB binary may be installed separately. Set ASTAPS_MONGOD to a trusted mongod.exe path if the launcher cannot autodiscover it.
- Never commit MongoDB data, config.json, logs, resources, JARs, local secrets, caches, or downloaded archives. Never delete persisted data while maintaining the project.
- Do not expose MongoDB to public interfaces or change client installations unless separately authorized.

## Verification and completion
- Validate 7.2 resource folders, JDK version, MongoDB startup/data path, the local server startup log, and any remaining errors.
- Re-run the exact Gradle jar command after source/build changes, and inspect its exit code.
- The first JAR launch may generate config.json and stop; check it, then re-run for readiness verification.
- Write agent-facing documentation in English. Track 7.2 protocol gaps rather than assuming total feature compatibility.
- Finish by reviewing git status --short, git diff --check, git diff, commit and push status. Do not claim a check was completed without running it.
