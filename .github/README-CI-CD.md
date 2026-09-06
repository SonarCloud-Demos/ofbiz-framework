# GitHub CI/CD Configuration

## Workflows

- `gradle.yml`  
  Build and checks (style, Javadoc)  
  → Trigger: push / PR on `trunk` and `release*`

- `build.yml`
  SonarQube Cloud pull request analysis
  → Trigger: PR targeting `trunk` and `feat-*`
  → Passes the PR number, source branch, and base branch explicitly so the PR baseline is the target branch, normally `trunk`.

- `sonarqube-branch.yml`
  SonarQube Cloud branch analysis
  → Trigger: push on `trunk` and `feat-*`

- `docker-image.yml`  
  Build and push images to `ghcr.io/apache/ofbiz`  
  → Trigger: push on `trunk` / `release*` + tags

- `asf-allowlist-check.yml`
  Verifies all GitHub Actions refs are on the ASF allowlist
  → Trigger: push / PR on `.github/` path

### Workflow behavior

- `push` → uses the workflow from the target branch  
- `pull_request` → uses the workflow from the source branch  
- `schedule` → always uses `trunk`

Workflows are maintained on all branches (`trunk` and `release*`) using the same triggers.

New branches inherit workflow files from `trunk` at creation time.

## SonarQube Demo Policy

Competing repository analysis is intentionally disabled for this demo branch set. Do not add Dependabot, CodeQL, Dependency Review, OpenSSF Scorecard, or SARIF uploads unless the SonarQube demo scope changes.
