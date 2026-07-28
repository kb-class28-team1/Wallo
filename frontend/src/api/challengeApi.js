import httpClient from './httpClient'

// 현재 참여 중인 챌린지 정보를 조회함
export const getCurrentChallenge = async () => {
  try {
    const response = await httpClient.get('/api/challenges/current')
    return response.data
  } catch (error) {
    // 서버 오류 메시지가 없으면 사용자 안내용 기본 메시지가 사용됨
    const message = error.response?.data?.message || '현재 챌린지 정보를 불러오지 못했습니다.'

    // 화면에서 alert 처리할 수 있도록 오류를 호출부로 전달함
    throw new Error(message)
  }
}
