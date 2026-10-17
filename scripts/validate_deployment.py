"""Check deployment inputs without resolving or printing production credentials."""
import argparse
from pathlib import Path
import re
import sys

import yaml


class UniqueLoader(yaml.SafeLoader):
    """Reject duplicate keys instead of silently keeping the last value."""


def unique_mapping(loader, node):
    result = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node)
        if key in result:
            raise ValueError("Duplicate YAML mapping key")
        result[key] = loader.construct_object(value_node)
    return result


UniqueLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, unique_mapping)


def load(path):
    try:
        document = yaml.load(path.read_text(encoding="utf-8"), Loader=UniqueLoader)
    except (OSError, yaml.YAMLError, ValueError):
        # YAML exception messages can include source values: report the path only.
        raise ValueError(f"Cannot read valid, unique-key YAML: {path}") from None
    if not isinstance(document, dict):
        raise ValueError(f"Expected a YAML mapping: {path}")
    return document


def required(document, dotted_key):
    value = document
    for key in dotted_key.split("."):
        if not isinstance(value, dict) or key not in value:
            raise ValueError(f"Missing configuration key: {dotted_key}")
        value = value[key]
    if value is None or value == "":
        raise ValueError(f"Empty configuration key: {dotted_key}")
    return value


def validate(environment, manifest_root, config_root, image_name):
    base = load(config_root / "application.yml")
    profile = load(config_root / f"application-{environment}.yml")
    for key in ("spring.application.name", "jwt.secretKey", "jwt.access.expiration",
                "jwt.refresh.expiration", "cloud.aws.s3.bucket", "drug.easy.service-key",
                "drug.easy.base-url"):
        required(base, key)
    for provider in ("google", "naver", "kakao"):
        prefix = f"spring.security.oauth2.client.registration.{provider}"
        for key in ("client-id", "client-secret"):
            required(base, f"{prefix}.{key}")
        required(profile, f"{prefix}.redirect-uri")
    for key in ("spring.datasource.url", "spring.datasource.username", "spring.datasource.password",
                "spring.datasource.driver-class-name", "spring.jpa.hibernate.ddl-auto",
                "app.oauth2.frontend-success-url", "app.oauth2.frontend-failure-url"):
        required(profile, key)

    folder = manifest_root / environment
    documents = {path.name: load(path) for path in folder.glob("*.yaml")}
    deployment = documents.get("deployment.yaml", {})
    if (deployment.get("kind") != "Deployment"
            or required(deployment, "metadata.name") != "pillguide-backend"
            or required(deployment, "metadata.namespace") != environment):
        raise ValueError("Unexpected Deployment target")
    for document in documents.values():
        namespace = document.get("metadata", {}).get("namespace")
        if namespace is not None and namespace != environment:
            raise ValueError("Manifest contains a different namespace")

    containers = required(deployment, "spec.template.spec.containers")
    if len(containers) != 1 or containers[0].get("name") != "backend":
        raise ValueError("Expected one backend image target")
    container = containers[0]
    if not re.fullmatch(re.escape(image_name) + r"(?:@sha256:[0-9a-f]{64}|:[\w.-]+)",
                        container.get("image", "")):
        raise ValueError("Unexpected image repository or image reference")
    config_map = documents.get("config-map.yaml", {})
    if config_map.get("kind") != "ConfigMap":
        raise ValueError("Missing backend ConfigMap")
    data = required(config_map, "data")
    if required(data, "SPRING_PROFILES_ACTIVE") != environment:
        raise ValueError("Spring profile does not match deployment environment")
    for key in ("SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "PILLGUIDE_S3_BUCKET"):
        required(data, key)
    if not any(ref.get("configMapRef", {}).get("name") == required(config_map, "metadata.name")
               for ref in container.get("envFrom", [])):
        raise ValueError("Deployment does not consume the backend ConfigMap")
    overrides = {entry["name"]: entry.get("value") for entry in container.get("env", [])}
    if "SPRING_PROFILES_ACTIVE" in overrides and overrides["SPRING_PROFILES_ACTIVE"] != environment:
        raise ValueError("Container overrides the selected Spring profile")
    print(f"Deployment configuration valid: {environment}; secret values were not resolved")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--environment", choices=("dev", "production"), required=True)
    parser.add_argument("--manifest-root", type=Path, default=Path("manifest"))
    parser.add_argument("--config-root", type=Path, default=Path("config/src/main/resources"))
    parser.add_argument("--image-name", default="wien0128/pillguide-be")
    args = parser.parse_args()
    try:
        validate(args.environment, args.manifest_root, args.config_root, args.image_name)
    except (ValueError, KeyError, TypeError, AttributeError):
        # Report structural failures, never dump loaded YAML or credentials.
        error = sys.exception()
        print(f"Deployment configuration failed: {error}" if isinstance(error, ValueError)
              else "Deployment configuration failed: unexpected YAML structure", file=sys.stderr)
        sys.exit(1)
