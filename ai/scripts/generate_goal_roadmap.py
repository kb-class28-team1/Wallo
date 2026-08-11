import argparse
import json
import sys
from pathlib import Path

AI_ROOT = Path(__file__).resolve().parents[1]
if str(AI_ROOT) not in sys.path:
    sys.path.insert(0, str(AI_ROOT))

from app.agents.roadmap.generator import generate_goal_roadmap
from app.agents.roadmap.models import RoadmapGoal
from app.clients.groq_client import create_groq_client


def main() -> None:
    parser = argparse.ArgumentParser(description="목표 JSON으로 AI 로드맵 JSON을 생성합니다.")
    parser.add_argument(
        "input",
        nargs="?",
        default=AI_ROOT / "data" / "raw" / "goal_roadmap_input.json",
        type=Path,
    )
    parser.add_argument(
        "--output",
        default=AI_ROOT / "data" / "processed" / "goal_roadmap_output.json",
        type=Path,
    )
    args = parser.parse_args()

    goal = RoadmapGoal.model_validate_json(args.input.read_text(encoding="utf-8"))
    roadmap = generate_goal_roadmap(create_groq_client(), goal)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(roadmap.model_dump(by_alias=True, mode="json"), ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"로드맵 {len(roadmap.steps)}단계를 생성했습니다: {args.output}")


if __name__ == "__main__":
    main()
