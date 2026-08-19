import logging
import re
from calendar import monthrange
from datetime import date

from groq import BadRequestError, Groq, RateLimitError
from pydantic import ValidationError

from app.agents.goal.models import (
    GoalDraft,
    GoalExtraction,
    GoalPriority,
    GoalType,
)
from app.agents.goal.prompts import (
    EXTRACTION_SYSTEM_PROMPT,
    build_extraction_user_prompt,
)
from app.core.ai_timing import timed_groq_completion
from app.core.config import get_groq_model

logger = logging.getLogger("wallo_ai")
GOAL_EXTRACTION_MAX_COMPLETION_TOKENS = 512


class GoalExtractionError(ValueError):
    """목표 정보 추출 응답이 계약을 만족하지 않을 때 발생한다."""


def nullable_schema(schema: dict[str, object]) -> dict[str, object]:
    """Groq Tool Schema에서 값이 아직 정해지지 않은 필드를 표현한다."""
    return {"anyOf": [schema, {"type": "null"}]}


class GoalExtractor:
    TOOL_NAME = "extract_financial_goal"
    TOOL_SCHEMA = {
        "type": "function",
        "function": {
            "name": TOOL_NAME,
            "description": "사용자의 자연어에서 금융 목표 정보를 구조화한다.",
            "parameters": {
                "type": "object",
                "properties": {
                    "title": nullable_schema({"type": "string"}),
                    "goal_type": nullable_schema({
                        "type": "string",
                        "enum": [
                            "EMERGENCY_FUND", "TRAVEL", "HOUSING",
                            "EDUCATION", "MARRIAGE", "DEBT_REPAYMENT",
                            "INVESTMENT", "RETIREMENT", "PURCHASE", "OTHER",
                        ],
                    }),
                    "target_amount": nullable_schema({
                        "type": "integer",
                        "minimum": 1,
                    }),
                    "target_date": nullable_schema({
                        "type": "string",
                        "format": "date",
                    }),
                    "motivation": nullable_schema({"type": "string"}),
                    "priority": nullable_schema({
                        "type": "string",
                        "enum": ["LOW", "MEDIUM", "HIGH"],
                    }),
                    "current_amount": nullable_schema({
                        "type": "integer",
                        "minimum": 0,
                    }),
                    "assumptions": {
                        "type": "array",
                        "items": {"type": "string"},
                    },
                    "next_field": nullable_schema({
                        "type": "string",
                        "enum": [
                            "goalType", "targetAmount", "targetDate",
                            "currentAmount",
                        ],
                    }),
                    "next_question": nullable_schema({"type": "string"}),
                },
                "additionalProperties": False,
            },
        },
    }

    def __init__(self, client: Groq, model: str | None = None):
        self.client = client
        self.model = model or get_groq_model()

    def extract(
        self,
        user_message: str,
        draft: GoalDraft,
        reference_date: date,
    ) -> GoalExtraction:
        last_error: Exception | None = None
        extraction: GoalExtraction | None = None
        fallback_reason: str | None = None
        with timed_groq_completion(
            self.client,
            operation="goal.extract",
            model=self.model,
            requested_completion_tokens=GOAL_EXTRACTION_MAX_COMPLETION_TOKENS,
        ) as timing:
            try:
                completion = timing.create(
                    messages=[
                        {"role": "system", "content": EXTRACTION_SYSTEM_PROMPT},
                        {
                            "role": "user",
                            "content": build_extraction_user_prompt(
                                user_message,
                                draft,
                                reference_date,
                            ),
                        },
                    ],
                    tools=[self.TOOL_SCHEMA],
                    tool_choice={
                        "type": "function",
                        "function": {"name": self.TOOL_NAME},
                    },
                    temperature=0,
                    reasoning_effort="low",
                    include_reasoning=False,
                    max_completion_tokens=GOAL_EXTRACTION_MAX_COMPLETION_TOKENS,
                )
                tool_calls = completion.choices[0].message.tool_calls
                if not tool_calls:
                    raise ValueError("목표 추출 도구 호출이 없습니다.")
                tool_call = tool_calls[0]
                function = getattr(tool_call, "function", None)
                tool_name = getattr(function, "name", None)
                if tool_name != self.TOOL_NAME:
                    raise ValueError(
                        f"허용되지 않은 목표 추출 도구 호출입니다: {tool_name!r}"
                    )
                extraction = GoalExtraction.model_validate_json(
                    function.arguments
                )
            except RateLimitError as error:
                last_error = error
                fallback_reason = "rate_limit"
                logger.warning("goal extraction rate limit reached; using fallback")
            except BadRequestError as error:
                last_error = error
                fallback_reason = "bad_request"
                logger.warning(
                    "goal tool extraction rejected; using fallback: errorType=%s",
                    type(error).__name__,
                )
            except (AttributeError, IndexError, TypeError, ValueError, ValidationError) as error:
                last_error = error
                fallback_reason = type(error).__name__
                logger.warning(
                    "goal tool extraction failed; using fallback: errorType=%s",
                    type(error).__name__,
                )

            if extraction is None:
                fallback = extract_explicit_goal_facts(user_message, reference_date)
                if fallback.model_dump(exclude_none=True, exclude={"assumptions"}):
                    logger.warning("using deterministic goal extraction fallback")
                    timing.mark_fallback(fallback_reason or "deterministic")
                    extraction = fallback
                else:
                    raise GoalExtractionError(
                        "목표 정보를 구조화하지 못했습니다."
                    ) from last_error
            return extraction


AMOUNT_PATTERN = re.compile(
    r"(?P<number>\d+(?:[,.]\d+)?)\s*(?P<unit>억|천만|백만|만)\s*원?"
)


def extract_explicit_goal_facts(
    user_message: str,
    reference_date: date,
) -> GoalExtraction:
    """모델 장애 시 문장에 명시된 핵심 정보만 보존한다."""
    goal_type = infer_goal_type(user_message)
    current_amount = contextual_amount(user_message, r"현재(?:\s*준비금(?:은|이)?)?")
    target_amount = first_unqualified_amount(user_message)
    target_date, date_assumptions = explicit_target_date(user_message, reference_date)
    priority = explicit_priority(user_message)
    motivation = (
        "비상 상황에 대비하기 위해"
        if goal_type == GoalType.EMERGENCY_FUND and "비상" in user_message
        else None
    )
    return GoalExtraction(
        goal_type=goal_type,
        target_amount=target_amount,
        target_date=target_date,
        motivation=motivation,
        priority=priority,
        current_amount=current_amount,
        assumptions=date_assumptions,
    )


def infer_goal_type(user_message: str) -> GoalType | None:
    keyword_types = (
        (("비상금", "비상시", "비상 상황"), GoalType.EMERGENCY_FUND),
        (("여행", "프랑스", "일본", "유럽"), GoalType.TRAVEL),
        (("주택", "내 집", "전세", "보증금"), GoalType.HOUSING),
        (("학비", "교육비", "등록금"), GoalType.EDUCATION),
        (("결혼", "웨딩"), GoalType.MARRIAGE),
        (("빚", "대출 상환", "부채"), GoalType.DEBT_REPAYMENT),
        (("은퇴", "노후"), GoalType.RETIREMENT),
    )
    for keywords, goal_type in keyword_types:
        if any(keyword in user_message for keyword in keywords):
            return goal_type
    return None


def contextual_amount(user_message: str, prefix_pattern: str) -> int | None:
    match = re.search(prefix_pattern + r"[^\d]{0,12}" + AMOUNT_PATTERN.pattern, user_message)
    return amount_from_match(match) if match else None


def first_unqualified_amount(user_message: str) -> int | None:
    for match in AMOUNT_PATTERN.finditer(user_message):
        prefix = user_message[max(0, match.start() - 12):match.start()]
        if re.search(r"현재|매달|매월|월마다", prefix):
            continue
        return amount_from_match(match)
    return None


def amount_from_match(match: re.Match[str]) -> int:
    number = float(match.group("number").replace(",", ""))
    multiplier = {
        "억": 100_000_000,
        "천만": 10_000_000,
        "백만": 1_000_000,
        "만": 10_000,
    }[match.group("unit")]
    return int(number * multiplier)


def explicit_target_date(
    user_message: str,
    reference_date: date,
) -> tuple[date | None, list[str]]:
    exact = re.search(
        r"(?P<year>20\d{2})[년.\-/]\s*(?P<month>1[0-2]|0?[1-9])"
        r"(?:[월.\-/]\s*(?P<day>3[01]|[12]\d|0?[1-9])일?)?",
        user_message,
    )
    if exact:
        year = int(exact.group("year"))
        month = int(exact.group("month"))
        day = int(exact.group("day")) if exact.group("day") else month_deadline_day(
            user_message, exact.end(), year, month
        )
        return interpreted_date(exact.group(0), year, month, day)

    year_end = re.search(r"(?P<relative>내년|올해)\s*말", user_message)
    if year_end:
        year = reference_date.year + (1 if year_end.group("relative") == "내년" else 0)
        return interpreted_date(year_end.group(0), year, 12, 31)

    month_match = re.search(
        r"(?P<relative>내년|올해)?\s*(?P<month>1[0-2]|[1-9])월",
        user_message,
    )
    if month_match:
        relative = month_match.group("relative")
        month = int(month_match.group("month"))
        if relative == "내년":
            year = reference_date.year + 1
        elif relative == "올해":
            year = reference_date.year
        else:
            year = reference_date.year + (month < reference_date.month)
        day = month_deadline_day(user_message, month_match.end(), year, month)
        return interpreted_date(month_match.group(0).strip(), year, month, day)

    years = re.search(r"(?P<count>\d+)년\s*(?:뒤|후|안에|이내|동안|정도)", user_message)
    if years:
        target = add_years(reference_date, int(years.group("count")))
        return interpreted_date(years.group(0), target.year, target.month, target.day)

    months = re.search(r"(?P<count>\d+)개월\s*(?:뒤|후|안에|이내|동안|정도)", user_message)
    if months:
        target = add_months(reference_date, int(months.group("count")))
        return interpreted_date(months.group(0), target.year, target.month, target.day)
    return None, []


def month_deadline_day(
    user_message: str,
    match_end: int,
    year: int,
    month: int,
) -> int:
    suffix = user_message[match_end:match_end + 5]
    return monthrange(year, month)[1] if "까지" in suffix else 1


def interpreted_date(
    source: str,
    year: int,
    month: int,
    day: int,
) -> tuple[date, list[str]]:
    interpreted = date(year, month, day)
    return interpreted, [f"{source.strip()}을 {interpreted.isoformat()}로 해석함"]


def add_years(value: date, years: int) -> date:
    try:
        return value.replace(year=value.year + years)
    except ValueError:
        return value.replace(year=value.year + years, day=28)


def add_months(value: date, months: int) -> date:
    month_index = value.month - 1 + months
    year = value.year + month_index // 12
    month = month_index % 12 + 1
    day = min(value.day, monthrange(year, month)[1])
    return date(year, month, day)


def explicit_priority(user_message: str) -> GoalPriority | None:
    if any(keyword in user_message for keyword in ("가장 중요", "우선순위가 높", "최우선")):
        return GoalPriority.HIGH
    if "우선순위가 낮" in user_message:
        return GoalPriority.LOW
    if "우선순위" in user_message:
        return GoalPriority.MEDIUM
    return None
