# Secrets Policy

Rules for keeping credentials out of the repository, and out of agents.

---

## 1. The core rule

**A secret never enters the repository. Not in a file, not in a commit
message, not in a script, not in a chat message, not in an issue, not in a
log.**

This includes files that are gitignored. A credential pasted into a
gitignored file on a machine with a synced folder, a backup, or a screen
share is still exposed. Gitignoring reduces the blast radius; it does not
make it safe.

---

## 2. What counts as a secret

| Category | Examples |
|----------|----------|
| Access tokens | GitHub PAT, OAuth tokens, API keys, bearer tokens |
| Credentials | Passwords, passphrases, PINs |
| Keys | SSH private keys, PGP private keys, API secret keys |
| Keystores | `*.jks`, `*.keystore`, `*.p12`, `*.pfx` |
| Signing material | Upload keys, app signing keys, `.pem`, `*.key` |
| Machine config | `local.properties` (contains the SDK path and may contain more) |
| Cloud credentials | Service-account JSON files, `.aws/credentials` |
| Session tokens | `auth.json`, `credentials.json`, `token.txt` |

An Android `local.properties` is normally just a local SDK path, which is
not a secret — but it is machine-specific and must not be committed,
because it exposes a personal directory layout.

---

## 3. What is already in place

`.gitignore` in the repository root covers:

```
.env
.env.*
*.key
*.pem
*.p12
*.pfx
*.jks
*.keystore
keystore.properties
google-services.json
local.properties
gradle.properties.local
secrets.properties
credentials.json
service-account*.json
.gh-token
token.txt
```

It also ignores agent-local tool configuration (`.mcp.json`,
`.playwright-mcp/`) and scratch files created during earlier work.

`.env.example` is explicitly **not** ignored, so a template file can be
committed with empty placeholder values.

`.gitattributes` normalises line endings and marks binary formats
(`*.apk`, `*.aab`, `*.keystore`, images, archives) as binary so Git never
tries to mangle them.

---

## 4. What is deliberately NOT ignored

The ignore list is specific. It does **not** blanket-ignore:

- source files (`*.kt`, `*.java`, `*.xml`)
- build and test directories that belong in the repo (`gradle/wrapper/`)
- documentation
- the `scripts/` and `docs/` directories

A `.gitignore` that ignores too much is its own kind of danger: it silently
excludes source that the build needs, and the failure appears much later and
much further from the cause.

---

## 5. Recommended tooling

### gitleaks

A fast secret scanner. Worth running before any push to a public repository.

```
winget install --id GitGuardian.gitleaks
gitleaks detect --source . --no-git
```

It should run on the working tree, and separately on history:

```
gitleaks detect --source . --log-opts="--all"
```

If gitleaks finds something that is genuinely a live credential: **rotate
it first**, then remove it from the file. Removing a leaked secret from the
working tree without rotating it does not fix the leak — the value was
already transmitted.

### GitHub secret scanning and push protection

Available on public repositories for free, and on private ones on paid
plans. These detect known credential patterns at push time and in history.
They are a safety net, not a substitute for not pasting secrets.

**Owner action:** enable Settings → Code security → Secret scanning and
Push protection on the GitHub repository once it exists.

---

## 6. Credentials on this machine

A per-user Git `credential.helper` is configured that supplies a token from
an environment variable. The token is **not** stored in this repository and
**not** in Git history, so this is not a repository leak.

It is recorded here because it is a real operational dependency: the
workflow's push step depends on an environment variable that is outside the
repository, outside Git, and not guaranteed to be present in every shell
that launches Git. See `docs/GITHUB_AUTH_SETUP.md` §4.

The recommended long-term state is Git Credential Manager, the Windows
Credential Manager, or SSH — all of which keep the credential outside the
repository by design.

---

## 7. Rules for agents

1. **Never ask for a credential.** If a task appears to require one, that
   is a stop condition. Report it and wait for the owner.
2. **Never accept a credential.** If one is pasted into a chat, treat it as
   compromised: tell the owner to rotate it immediately.
3. **Never write a credential into a file**, including a file that is
   gitignored.
4. **Never put a credential in a URL.** `https://user:token@github.com/...`
   ends up in `.git/config` and in shell history. Use a credential helper
   or SSH.
5. **Never print an environment variable that may hold a token.** The setup
   diagnostics deliberately read Git *configuration* only, never the value
   of `GITHUB_TOKEN` or similar.
6. **Never commit a `.env` file**, even an empty one — an empty one gets
   filled in later and then committed by accident. Commit
   `.env.example` instead.

---

## 8. If a secret is committed

1. **Rotate the credential immediately.** Assume it is compromised.
2. Do not rely on "just delete the file in the next commit" — the value
   remains in history and may already have been cloned.
3. For a public repository, treat rotation as mandatory even if the commit
   was removed.
4. Record the incident in the handoff report so the project history shows
   it happened and was handled.
