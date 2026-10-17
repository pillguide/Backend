"""Small regression checks for deployment reference mistakes."""
import copy
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from validate_deployment import validate_secret_references


class SecretReferenceTests(unittest.TestCase):
    def setUp(self):
        self.documents = [
            {"kind": "ExternalSecret", "metadata": {"name": "database"},
             "spec": {"target": {"name": "db"}, "data": [{"secretKey": "password"}]}},
            {"kind": "ExternalSecret", "metadata": {"name": "runtime"},
             "spec": {"dataFrom": [{"extract": {"key": "/runtime"}}]}},
        ]
        self.container = {
            "envFrom": [{"secretRef": {"name": "runtime"}}],
            "env": [{"name": "DB_PASSWORD", "valueFrom": {
                "secretKeyRef": {"name": "db", "key": "password"}}}],
        }

    def test_declared_references_pass(self):
        validate_secret_references(self.container, self.documents)

    def test_required_names_must_exist(self):
        for reference_type in ("envFrom", "env"):
            with self.subTest(reference_type=reference_type):
                container = copy.deepcopy(self.container)
                reference = (container["envFrom"][0]["secretRef"] if reference_type == "envFrom"
                             else container["env"][0]["valueFrom"]["secretKeyRef"])
                reference["name"] = "typo"
                with self.assertRaisesRegex(ValueError, "no ExternalSecret declaration"):
                    validate_secret_references(container, self.documents)

    def test_required_key_must_be_declared(self):
        self.container["env"][0]["valueFrom"]["secretKeyRef"]["key"] = "typo"
        with self.assertRaisesRegex(ValueError, "key is not declared"):
            validate_secret_references(self.container, self.documents)

    def test_optional_missing_reference_is_allowed(self):
        self.container["envFrom"].append({"secretRef": {"name": "absent", "optional": True}})
        self.container["env"].append({"name": "OPTIONAL", "valueFrom": {
            "secretKeyRef": {"name": "db", "key": "absent", "optional": True}}})
        validate_secret_references(self.container, self.documents)

    def test_extracted_keys_are_not_rejected_offline(self):
        self.container["env"][0]["valueFrom"]["secretKeyRef"] = {
            "name": "runtime", "key": "DYNAMIC_KEY"}
        validate_secret_references(self.container, self.documents)


if __name__ == "__main__":
    unittest.main()
