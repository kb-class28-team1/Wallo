from dataclasses import asdict, dataclass
from typing import Any


@dataclass(frozen=True)
class ToolResult:
    status: str
    tool: str
    data: Any = None
    message: str | None = None

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)
