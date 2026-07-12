#!/usr/bin/env python3
"""Collect FirstRag answers and evaluate them with Ragas."""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import uuid
from pathlib import Path
from typing import Any

import requests


SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_DATASET = SCRIPT_DIR / "eval_dataset.jsonl"
DEFAULT_RESULTS_DIR = SCRIPT_DIR / "results"
CONTEXT_METADATA_FIELDS = (
    ("title", "Title"),
    ("type", "Type"),
    ("source", "Source"),
    ("product_id", "Product ID"),
    ("link", "Link"),
    ("category", "Category"),
    ("brand", "Brand"),
    ("price", "Price"),
    ("color", "Color"),
    ("season", "Season"),
    ("scene", "Scene"),
    ("effect", "Effect"),
    ("tags", "Tags"),
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Evaluate FirstRag with Ragas")
    parser.add_argument(
        "--mode",
        choices=("all", "collect", "evaluate"),
        default="all",
        help="all: collect and score; collect: only call FirstRag; evaluate: score an existing run",
    )
    parser.add_argument("--dataset", type=Path, default=DEFAULT_DATASET)
    parser.add_argument("--results-dir", type=Path, default=DEFAULT_RESULTS_DIR)
    parser.add_argument("--run-file", type=Path, help="Existing collected JSONL for --mode evaluate")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--top-k", type=int, default=4)
    parser.add_argument("--timeout", type=float, default=120.0)
    parser.add_argument("--delay", type=float, default=0.3, help="Delay between chat requests")
    parser.add_argument("--limit", type=int, help="Only run the first N rows for a smoke test")
    return parser.parse_args()


def load_jsonl(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        raise FileNotFoundError(f"File not found: {path}")

    rows: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as file:
        for line_number, line in enumerate(file, start=1):
            if not line.strip():
                continue
            try:
                row = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(f"Invalid JSON at {path}:{line_number}: {exc}") from exc
            rows.append(row)
    return rows


def validate_dataset(rows: list[dict[str, Any]]) -> None:
    if not rows:
        raise ValueError("Evaluation dataset is empty")
    for index, row in enumerate(rows, start=1):
        for field in ("question", "ground_truth"):
            if not isinstance(row.get(field), str) or not row[field].strip():
                raise ValueError(f"Row {index} has no valid {field}")


def append_jsonl(path: Path, row: dict[str, Any]) -> None:
    with path.open("a", encoding="utf-8", newline="\n") as file:
        file.write(json.dumps(row, ensure_ascii=False) + "\n")


def format_source_context(source: dict[str, Any]) -> str | None:
    content = source.get("content")
    if not isinstance(content, str) or not content.strip():
        return None

    metadata = source.get("metadata")
    lines: list[str] = []
    if isinstance(metadata, dict):
        for key, label in CONTEXT_METADATA_FIELDS:
            value = metadata.get(key)
            if value is not None and str(value).strip():
                lines.append(f"{label}: {value}")
    lines.append(f"Content:\n{content.strip()}")
    return "\n".join(lines)


def collect_answers(args: argparse.Namespace, rows: list[dict[str, Any]]) -> Path:
    args.results_dir.mkdir(parents=True, exist_ok=True)
    timestamp = time.strftime("%Y%m%d-%H%M%S")
    output_path = args.results_dir / f"collected-{timestamp}.jsonl"
    endpoint = f"{args.base_url.rstrip('/')}/api/rag/chat"
    session = requests.Session()

    print(f"Collecting {len(rows)} answers from {endpoint}")
    for index, row in enumerate(rows, start=1):
        question = row["question"].strip()
        payload = {
            "message": question,
            "topK": args.top_k,
            "sessionId": f"ragas-{uuid.uuid4()}",
        }
        try:
            response = session.post(endpoint, json=payload, timeout=args.timeout)
            response.raise_for_status()
            body = response.json()
        except requests.RequestException as exc:
            raise RuntimeError(f"FirstRag request {index} failed: {exc}") from exc
        except ValueError as exc:
            raise RuntimeError(f"FirstRag request {index} returned invalid JSON") from exc

        answer = body.get("content")
        if not isinstance(answer, str) or not answer.strip():
            raise RuntimeError(f"FirstRag request {index} returned empty content")

        sources = body.get("sources") or []
        contexts = [
            context
            for source in sources
            if isinstance(source, dict)
            for context in [format_source_context(source)]
            if context is not None
        ]
        collected = {
            "question": question,
            "answer": answer.strip(),
            "contexts": contexts,
            "ground_truth": row["ground_truth"].strip(),
            "used_knowledge_base": bool(body.get("usedKnowledgeBase")),
            "source_count": len(contexts),
        }
        append_jsonl(output_path, collected)
        print(
            f"[{index:02d}/{len(rows):02d}] sources={len(contexts)} "
            f"usedKB={collected['used_knowledge_base']}  {question}"
        )
        if args.delay > 0 and index < len(rows):
            time.sleep(args.delay)

    print(f"Collected data: {output_path}")
    return output_path


def required_env(name: str) -> str:
    value = os.getenv(name, "").strip()
    if not value:
        raise RuntimeError(f"Environment variable {name} is required for Ragas scoring")
    return value


def evaluate_run(run_file: Path, results_dir: Path) -> tuple[Path, Path]:
    try:
        import pandas as pd
        from datasets import Dataset
        from langchain_openai import ChatOpenAI, OpenAIEmbeddings
        from ragas import evaluate
        from ragas.embeddings import LangchainEmbeddingsWrapper
        from ragas.llms import LangchainLLMWrapper
        from ragas.metrics import answer_relevancy, context_precision, context_recall, faithfulness
        from ragas.run_config import RunConfig
    except ImportError as exc:
        raise RuntimeError(
            "Ragas dependencies are missing. Run: pip install -r eval/requirements-ragas.txt"
        ) from exc

    rows = load_jsonl(run_file)
    if not rows:
        raise ValueError(f"Collected run is empty: {run_file}")

    api_key = required_env("DASHSCOPE_API_KEY")
    base_url = os.getenv(
        "RAGAS_BASE_URL",
        os.getenv("DASHSCOPE_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
    )
    judge_model = os.getenv("RAGAS_JUDGE_MODEL", os.getenv("DASHSCOPE_MODEL", "qwen-plus"))
    embedding_model = os.getenv(
        "RAGAS_EMBEDDING_MODEL",
        os.getenv("DASHSCOPE_EMBEDDING_MODEL", "text-embedding-v4"),
    )
    embedding_dimensions = int(os.getenv("RAGAS_EMBEDDING_DIMENSIONS", "1024"))

    judge = LangchainLLMWrapper(
        ChatOpenAI(
            api_key=api_key,
            base_url=base_url,
            model=judge_model,
            temperature=0,
            timeout=120,
            max_retries=3,
        )
    )
    embeddings = LangchainEmbeddingsWrapper(
        OpenAIEmbeddings(
            api_key=api_key,
            base_url=base_url,
            model=embedding_model,
            dimensions=embedding_dimensions,
            chunk_size=10,
            check_embedding_ctx_length=False,
            max_retries=3,
            request_timeout=120,
        )
    )

    dataset = Dataset.from_dict(
        {
            "question": [row["question"] for row in rows],
            "answer": [row["answer"] for row in rows],
            "contexts": [row.get("contexts") or [] for row in rows],
            "ground_truth": [row["ground_truth"] for row in rows],
        }
    )
    metrics = [faithfulness, answer_relevancy, context_precision, context_recall]
    print(
        f"Scoring {len(rows)} rows with judge={judge_model}, "
        f"embedding={embedding_model}"
    )
    result = evaluate(
        dataset=dataset,
        metrics=metrics,
        llm=judge,
        embeddings=embeddings,
        run_config=RunConfig(timeout=180, max_retries=3, max_wait=60),
        raise_exceptions=False,
        show_progress=True,
    )

    frame = result.to_pandas()
    frame.insert(0, "index", range(1, len(frame) + 1))
    frame["used_knowledge_base"] = [row.get("used_knowledge_base") for row in rows]
    frame["source_count"] = [row.get("source_count", len(row.get("contexts") or [])) for row in rows]

    results_dir.mkdir(parents=True, exist_ok=True)
    stem = run_file.stem.replace("collected-", "")
    csv_path = results_dir / f"ragas-scores-{stem}.csv"
    summary_path = results_dir / f"ragas-summary-{stem}.json"
    frame.to_csv(csv_path, index=False, encoding="utf-8-sig")

    metric_names = ["faithfulness", "answer_relevancy", "context_precision", "context_recall"]
    summary: dict[str, Any] = {
        "run_file": str(run_file.resolve()),
        "row_count": len(rows),
        "judge_model": judge_model,
        "embedding_model": embedding_model,
        "metrics": {},
        "rows_without_context": sum(not (row.get("contexts") or []) for row in rows),
    }
    for metric_name in metric_names:
        if metric_name not in frame.columns:
            continue
        numeric = pd.to_numeric(frame[metric_name], errors="coerce")
        valid = numeric.dropna()
        summary["metrics"][metric_name] = {
            "mean": None if valid.empty else round(float(valid.mean()), 6),
            "valid_rows": int(valid.count()),
            "failed_rows": int(len(numeric) - valid.count()),
        }

    summary_path.write_text(
        json.dumps(summary, ensure_ascii=False, indent=2, allow_nan=False),
        encoding="utf-8",
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    print(f"Per-row scores: {csv_path}")
    print(f"Summary: {summary_path}")
    return csv_path, summary_path


def main() -> int:
    args = parse_args()
    if not 1 <= args.top_k <= 10:
        raise ValueError("--top-k must be between 1 and 10")

    run_file = args.run_file
    if args.mode in ("all", "collect"):
        rows = load_jsonl(args.dataset)
        validate_dataset(rows)
        if args.limit is not None:
            if args.limit < 1:
                raise ValueError("--limit must be greater than 0")
            rows = rows[: args.limit]
        run_file = collect_answers(args, rows)

    if args.mode in ("all", "evaluate"):
        if run_file is None:
            raise ValueError("--run-file is required with --mode evaluate")
        evaluate_run(run_file.resolve(), args.results_dir.resolve())
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (FileNotFoundError, ValueError, RuntimeError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(1)
