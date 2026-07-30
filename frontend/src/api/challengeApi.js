import httpClient from "./httpClient"

// 현재 참여 중인 챌린지 정보를 조회함
export const getCurrentChallenge = async () => {
  try {
    const response = await httpClient.get("/api/challenges/current")
    return response.data
  } catch (error) {
    // 서버 오류 메시지가 없으면 사용자 안내용 기본 메시지가 사용됨
    const message = error.response?.data?.message || "현재 챌린지 정보를 불러오지 못했습니다."

    // 화면에서 alert 처리할 수 있도록 오류를 호출부로 전달함
    throw new Error(message)
  }
}

// 로그인 사용자가 참여 중인 GROUP 챌린지의 이번 주 랭킹을 조회함
export const getWeeklyRanking = async () => {
  try {
    const response = await httpClient.get("/api/challenges/rankings/weekly")
    return response.data
  } catch (error) {
    // 백엔드에서 전달한 오류 메시지를 우선 사용하고, 없으면 기본 안내 문구를 사용함
    const message = error.response?.data?.message || "주간 랭킹을 불러오지 못했습니다."

    // 화면에서 alert 등으로 안내할 수 있도록 오류를 호출한 곳으로 다시 전달함
    throw new Error(message)
  }
}

// 새로운 챌린지를 생성함
export const createChallenge = async (payload) => {
  try {
    const response = await httpClient.post("/api/challenges", payload)
    return response.data
  } catch (error) {
    const message = error.response?.data?.message || "챌린지를 만들지 못했습니다."
    throw new Error(message)
  }
}

// 초대 코드로 기존 챌린지에 참여함
export const joinChallenge = async (inviteCode) => {
  try {
    const response = await httpClient.post("/api/challenges/join", { inviteCode })
    return response.data
  } catch (error) {
    const message = error.response?.data?.message || "챌린지에 참여하지 못했습니다."
    throw new Error(message)
  }
}
