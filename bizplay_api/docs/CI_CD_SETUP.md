---
title: CI/CD Setup
subtitle: One-time setup for the GitHub Actions → GHCR → server pipeline
date: 2026-04-29
---

# Overview

The pipeline is defined in [.github/workflows/deploy.yml](../.github/workflows/deploy.yml) and runs in three jobs:

```
push to master
   │
   ├─ test    : ./mvnw verify (every push, every PR)
   ├─ image   : Build Docker image, push to ghcr.io  (master only, after test)
   └─ deploy  : SSH to server, docker compose pull + up -d, health check
```

The container image is published to GitHub Container Registry (GHCR) at
`ghcr.io/<owner>/<repo>` with two tags every build:

- `:latest` — what the server pulls
- `:sha-<short-commit>` — immutable per-commit reference for rollback

The deploy step waits up to 90 s for `GET /actuator/health` to return `"status":"UP"`. If it doesn't, the build is marked failed and the deploy step dumps the last 200 log lines from the container.

---

# 1. GitHub repository setup

## 1a. Required secrets

Settings → Secrets and variables → Actions → New repository secret:

| Name | Value | Notes |
|---|---|---|
| `DEPLOY_HOST` | e.g. `1.2.3.4` or `bizplay-chatbot.aiconvergencelab.com` | Server SSH endpoint reachable from GitHub-hosted runners (i.e. publicly routable). |
| `DEPLOY_USER` | e.g. `deploy` | Linux user that owns `/opt/bizplay-chatbot` and is in the `docker` group. |
| `DEPLOY_PORT` | `22` (or your custom SSH port) | |
| `DEPLOY_SSH_KEY` | The private SSH key (full PEM contents) for `DEPLOY_USER` | Generate a dedicated key for this — don't reuse a personal one. See §3 below. |

`GITHUB_TOKEN` is provided automatically and used to push to GHCR; no setup needed for that.

## 1b. Optional — production environment with manual approval

Settings → Environments → New environment → name it `production`. Add a "Required reviewers" rule listing the people who must approve before the deploy job runs. The workflow already declares `environment: production` on the deploy job, so as soon as the env exists with reviewers, master pushes will pause for approval.

## 1c. Make the image private (default for free orgs)

After the first successful image push, go to your repo's **Packages** tab → click the `bizplay-secure-rag-chatbot-api` package → **Package settings** → ensure visibility is set as you want it (private is the default for new packages). If you keep it private, the server must authenticate to pull (see §2c).

---

# 2. Server one-time setup

## 2a. Install Docker + Compose plugin

```bash
# Ubuntu
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker deploy   # logout/login afterwards
```

## 2b. Create the deploy directory

```bash
sudo mkdir -p /opt/bizplay-chatbot
sudo chown deploy:deploy /opt/bizplay-chatbot
cd /opt/bizplay-chatbot
```

Place these files in `/opt/bizplay-chatbot/` on the server:

- `docker-compose.prod.yml` — copy from this repo
- `.env` — production secrets (DB_URL, UI_PASSWORD, LLM_*_*, EMBED_*, etc.). Keep it `chmod 600`.
- `uploads/` — created automatically when compose mounts the volume; ensure the directory is writable by the container user.

## 2c. GHCR login (only if image is private)

Generate a Personal Access Token with the `read:packages` scope at <https://github.com/settings/tokens> (classic) or use a fine-grained token scoped to the package. Then on the server:

```bash
echo "<PAT>" | docker login ghcr.io -u <github-username> --password-stdin
```

This writes `~/.docker/config.json`; subsequent `docker compose pull` commands re-use it.

## 2d. First-time bring-up

```bash
cd /opt/bizplay-chatbot
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps      # should show "Up (healthy)" after ~60s
curl -s http://127.0.0.1:8081/actuator/health      # → {"status":"UP"}
```

After this, every `git push` to `master` will replace the running container automatically.

---

# 3. SSH key for `DEPLOY_USER`

On your laptop (or any machine), generate a deploy-only key:

```bash
ssh-keygen -t ed25519 -C "github-actions-bizplay-chatbot" -f ~/.ssh/bizplay_deploy
```

Copy the public key onto the server:

```bash
ssh-copy-id -i ~/.ssh/bizplay_deploy.pub deploy@<server>
```

Verify the user can run docker without sudo:

```bash
ssh -i ~/.ssh/bizplay_deploy deploy@<server> "docker version | head -3"
```

Paste the **private key** (`cat ~/.ssh/bizplay_deploy`) into the GitHub `DEPLOY_SSH_KEY` secret. Restrict the key on the server side to only the deploy command if you want to reduce blast radius:

```bash
# /home/deploy/.ssh/authorized_keys (one-line entry)
command="cd /opt/bizplay-chatbot && /usr/local/bin/deploy.sh",no-port-forwarding,no-X11-forwarding ssh-ed25519 AAAA... github-actions-bizplay-chatbot
```

(Optional. The workflow inlines the deploy script today, so this would require extracting it into `/usr/local/bin/deploy.sh` first.)

---

# 4. How to deploy

Just push to `master`:

```bash
git checkout master
git merge --ff-only develop
git push
```

Watch the run at `https://github.com/<owner>/<repo>/actions`. The **deploy** job's "SSH deploy" step shows the live log of the on-server commands.

---

# 5. Rollback

Two safe paths back to a known-good build.

## 5a. Roll back to a specific commit

Every CI run pushes a `sha-<short>` tag. To roll back, SSH to the server and:

```bash
cd /opt/bizplay-chatbot

# Find the previous good SHA from the GitHub Actions run history.
PREV=sha-3a4b5c6        # paste the short SHA tag here

# Repoint the running container at the older image without editing the
# compose file (use IMAGE override).
IMAGE="ghcr.io/<owner>/<repo>:${PREV}"
docker pull "$IMAGE"
docker tag  "$IMAGE" ghcr.io/<owner>/<repo>:latest
docker compose -f docker-compose.prod.yml up -d
```

(Or temporarily edit `docker-compose.prod.yml`'s `image:` line to point at `:sha-<old>`, then `up -d`. Same effect, less re-tagging magic.)

## 5b. Roll forward via git revert + push

If the bad commit is on master, revert it on a branch and merge — CI will deploy the revert as a normal build:

```bash
git revert <bad-commit-sha>
git push
```

Slower than 5a but uses the same pipeline path, so the next forward fix follows the same flow.

---

# 6. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `image` job fails with `denied: permission_denied` | First-time GHCR push needs the package to be created. Re-run the workflow once. | Re-run; subsequent pushes work. |
| `deploy` job hangs at "Waiting for /actuator/health" | New container failed to start (usually DB or vLLM unreachable). | The job dumps `docker compose logs --tail 200`. Check the actual error there. |
| `docker compose pull` on server says `unauthorized` | The PAT for GHCR expired or wasn't set up. | Re-run §2c with a fresh PAT. |
| Health check passes but nginx still serves the old build | nginx is OK; you're hitting a browser cache or the cert / hostname is misconfigured. | `curl -i https://<domain>/actuator/health` directly. |
| GitHub Actions runner can't SSH | Server SSH not reachable from GitHub IP ranges, or firewall blocks new IPs. | Either open SSH to the world (rate-limit + key-only) or switch to a self-hosted runner. |

---

# 7. What the pipeline does NOT do (yet)

- **No staging env** — pushes to master go straight to production. If you want a staging step, copy the `image` + `deploy` jobs into a `staging` job triggered on `develop`, and point its secrets at a different host.
- **No DB migration approval gate** — Flyway runs on app start. If a migration takes long or destructive paths, the deploy step's 90 s health check will fail and the run goes red. Consider running migrations as a separate one-shot container before the swap if migrations grow large.
- **No image vulnerability scan** — easy to add later via `aquasecurity/trivy-action` between the build and push steps.
- **No automatic rollback** — failed health check leaves the container in a "stopped/unhealthy" state for you to inspect. Prefer manual rollback (§5a) so you can read logs first.
