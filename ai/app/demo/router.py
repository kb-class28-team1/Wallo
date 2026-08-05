import logging

from fastapi import APIRouter, HTTPException
from groq import GroqError

from app.clients.groq_client import create_groq_client
from app.demo.repository import load_demo_profiles
from app.demo.schemas import DemoAssetAnalysisRequest, DemoAssetAnalysisResponse, DemoProfileSummary
from app.demo.service import generate_demo_asset_analysis, list_demo_profiles

logger = logging.getLogger("wallo_ai")
router = APIRouter(prefix="/api/demo", tags=["demo"])


@router.get("/asset-profiles", response_model=list[DemoProfileSummary])
def demo_asset_profiles() -> list[DemoProfileSummary]:
    try:
        return list_demo_profiles()
    except RuntimeError as error:
        raise HTTPException(status_code=500, detail=str(error)) from error


@router.post("/asset-analysis", response_model=DemoAssetAnalysisResponse)
def demo_asset_analysis(request: DemoAssetAnalysisRequest) -> DemoAssetAnalysisResponse:
    try:
        profile = load_demo_profiles().get(request.profile_id)
        if profile is None:
            raise HTTPException(status_code=404, detail=f"가상 사용자 프로필을 찾을 수 없습니다: {request.profile_id}")
        answer = generate_demo_asset_analysis(create_groq_client(), profile, request.question)
        source = profile.get("source") or {}
        return DemoAssetAnalysisResponse(
            profileId=request.profile_id,
            title=source.get("title") or f"가상 사용자 {request.profile_id}",
            answer=answer,
        )
    except RuntimeError as error:
        raise HTTPException(status_code=503, detail=str(error)) from error
    except GroqError as error:
        logger.exception("Groq demo asset analysis failed")
        raise HTTPException(status_code=502, detail="Groq AI 자산분석 결과를 생성하지 못했습니다.") from error
