from __future__ import annotations

import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CONFIG_PATH = ROOT / "config.json"
TRANSFORMERS_PATH = ROOT / "grunt-main" / "src" / "main" / "kotlin" / "net" / "spartanb312" / "grunt" / "process" / "Transformers.kt"
TRANSFORMER_SOURCES = ROOT / "grunt-main" / "src" / "main" / "kotlin" / "net" / "spartanb312" / "grunt" / "process" / "transformers"
OUTPUT_PATH = ROOT / "grunt-main" / "src" / "main" / "resources" / "web" / "schema" / "config-schema.json"


DECLARATION_RE = re.compile(
    r"object\s+([A-Za-z0-9_]+)\s*:\s*Transformer\(\"([^\"]+)\",\s*Category\.([A-Za-z]+)"
)
ORDER_RE = re.compile(r"([A-Za-z0-9_]+)\s+order\s+(\d+)")

CATEGORY_ORDER = {
    "General": 0,
    "Optimization": 1,
    "Miscellaneous": 2,
    "Controlflow": 3,
    "Encryption": 4,
    "Redirect": 5,
    "Renaming": 6,
    "Minecraft": 7,
}


def read_text_auto(path: Path) -> str:
    for encoding in ("utf-8", "utf-8-sig", "gbk", "gb18030"):
        try:
            return path.read_text(encoding=encoding)
        except UnicodeDecodeError:
            continue
    raise UnicodeDecodeError("unknown", b"", 0, 1, f"Unable to decode {path}")


def humanize(name: str) -> str:
    text = re.sub(r"([a-z0-9])([A-Z])", r"\1 \2", name)
    text = re.sub(r"([A-Z]+)([A-Z][a-z])", r"\1 \2", text)
    return text.replace("_", " ").strip() or name


def field_type(value: object) -> str:
    if isinstance(value, bool):
        return "boolean"
    if isinstance(value, int) and not isinstance(value, bool):
        return "int"
    if isinstance(value, float):
        return "float"
    if isinstance(value, list):
        return "list"
    return "string"


def load_transformer_meta() -> dict[str, dict[str, object]]:
    declared: dict[str, dict[str, object]] = {}
    for source in TRANSFORMER_SOURCES.rglob("*.kt"):
        match = DECLARATION_RE.search(read_text_auto(source))
        if not match:
            continue
        object_name, config_name, category = match.groups()
        declared[object_name] = {
            "config_name": config_name,
            "category": category,
        }

    order_map: dict[str, int] = {}
    for object_name, order in ORDER_RE.findall(read_text_auto(TRANSFORMERS_PATH)):
        order_map[object_name] = int(order)

    by_config_name: dict[str, dict[str, object]] = {}
    for object_name, meta in declared.items():
        config_name = str(meta["config_name"])
        by_config_name[config_name] = {
            "object_name": object_name,
            "category": meta["category"],
            "order": order_map.get(object_name, 9999),
        }
    return by_config_name


def build_sections(config: dict[str, object], transformer_meta: dict[str, dict[str, object]]) -> list[dict[str, object]]:
    sections: list[dict[str, object]] = []
    general_order = {
        "Settings": 0,
        "UI": 1,
    }

    for section_name, section_value in config.items():
        if not isinstance(section_value, dict):
            continue

        transformer = transformer_meta.get(section_name)
        if transformer:
            kind = "transformer"
            category = str(transformer["category"])
            order = int(transformer["order"])
        else:
            kind = "general"
            category = "General"
            order = general_order.get(section_name, 100 + len(sections))

        fields = []
        for key, value in section_value.items():
            fields.append(
                {
                    "key": key,
                    "label": humanize(key),
                    "path": [section_name, key],
                    "type": field_type(value),
                }
            )

        sections.append(
            {
                "key": section_name,
                "title": humanize(section_name),
                "kind": kind,
                "category": category,
                "order": order,
                "fields": fields,
            }
        )

    sections.sort(
        key=lambda item: (
            0 if item["kind"] == "general" else 1,
            CATEGORY_ORDER.get(str(item["category"]), 99),
            int(item["order"]),
            str(item["title"]),
        )
    )
    return sections


def main() -> None:
    config = json.loads(read_text_auto(CONFIG_PATH))
    transformer_meta = load_transformer_meta()
    schema = {
        "version": 1,
        "defaults": config,
        "sections": build_sections(config, transformer_meta),
    }
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_PATH.write_text(json.dumps(schema, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote schema to {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
