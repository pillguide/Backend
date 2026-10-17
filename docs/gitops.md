# Backend delivery

| Backend branch | Image architecture | GitOps target | Argo CD namespace |
|---|---|---|---|
| main | linux/arm64 | pillguide/manifest, main, production/deployment.yaml | production |
| develop | linux/arm64 | pillguide/manifest, main, dev/deployment.yaml | dev |

GitHub Actions checks deployment configuration, runs the existing Gradle tests and builds bootJar, pushes `wien0128/pillguide-be:<full commit SHA>`, and records its immutable digest in the matching manifest. Argo CD reconciles main automatically. CI updates only the image, never replicas. Pushes to main/develop require no manual deployment approval.

Required Actions secrets (repository or organization shared with this repository): `DOCKERHUB_USERNAME`, `DOCKERHUB_PASSWORD`, and `PAT`. PAT needs config submodule access and write access to pillguide/manifest. Never put their values in Git.

The lightweight Python checker runs inside GitHub Actions before the image build. It checks YAML structure, required configuration fields, environment/profile consistency, and required Secret names and explicitly declared keys against ExternalSecret manifests. It does not fetch credentials or inspect keys inside remotely extracted objects. Optional Secret references are allowed. Existing Gradle tests and the JAR build run in the Docker build; no additional approval or infrastructure service is required.

Each environment currently uses one replica with Recreate; a short deployment interruption is accepted for the graduation demo. Production temporarily sets `SPRING_JPA_HIBERNATE_DDL_AUTO=update` in `manifest/production/deployment.yaml` during development. TODO when development is complete: change that override back to `validate` after confirming the database schema matches the application. This is a manual follow-up, not an automatic date-based change.
