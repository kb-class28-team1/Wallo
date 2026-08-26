import httpClient from "@/api/httpClient"

// 백엔드는 CommonResponse({success, data, error})로 감싸서 내려줌. 실제 데이터만 꺼내서 돌려줌.
const unwrap = (response) => response.data.data

// CommonResponse.error.message를 우선 사용하고, 없으면 기본 안내 문구로 대체함
const toUserMessage = (error, fallback) =>
  error.response?.data?.error?.message || error.response?.data?.message || fallback

// 금융 리포트 목록을 조회함 (news + news_report 요약, 게시일시 최신순)
export const getReports = async () => {
  try {
    const response = await httpClient.get("/api/reports")
    return unwrap(response)
  } catch (error) {
    throw new Error(toUserMessage(error, "금융 리포트 목록을 불러오지 못했습니다."))
  }
}

// 뉴스 원문과 AI 분석 상세, 매칭된 금융용어를 조회함
export const getReportDetail = async (newsId) => {
  try {
    const response = await httpClient.get(`/api/reports/${newsId}`)
    return unwrap(response)
  } catch (error) {
    throw new Error(toUserMessage(error, "금융 리포트 상세 정보를 불러오지 못했습니다."))
  }
}

// 시연용: 스케줄 시간을 기다리지 않고 뉴스 크롤링과 금융 리포트 생성을 즉시 실행함
export const crawlNewsNow = async () => {
  try {
    const response = await httpClient.post("/api/reports/crawl-now")
    return unwrap(response)
  } catch (error) {
    throw new Error(toUserMessage(error, "새 뉴스를 수집하지 못했습니다."))
  }
}

export const generateMissingReports = async () => {
  try {
    const response = await httpClient.post("/api/reports/generate-missing")
    return unwrap(response)
  } catch (error) {
    throw new Error(toUserMessage(error, "AI 리포트 생성 작업을 시작하지 못했습니다."))
  }
}
