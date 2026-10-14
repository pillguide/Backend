# Backend delivery

| Backend branch | Image architecture | GitOps target | Argo CD namespace |
|---|---|---|---|
| main | linux/arm64 | pillguide/manifest, main, production/deployment.yaml | production |
| develop | linux/arm64 | pillguide/manifest, main, dev/deployment.yaml | dev |

GitHub Actions builds bootJar, pushes `wien0128/pillguide-be:<full commit SHA>`, and records its immutable digest in the matching manifest. Argo CD reconciles main automatically. CI updates only the image, never replicas.

Required Actions secrets (repository or organization shared with this repository): `DOCKERHUB_USERNAME`, `DOCKERHUB_PASSWORD`, and `PAT`. PAT needs config submodule access and write access to pillguide/manifest. Never put their values in Git.

Both environments initially have zero replicas: the first Spring rollout is separate. Before activation, complete PostgreSQL support, health probes and environment-specific OAuth configuration, then change replicas in manifest/main. Image build success does not prove database connectivity or application health. Integration tests and runtime verification remain prerequisites for the first rollout.
