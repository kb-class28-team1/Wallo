// 미션 기능 구현 전까지는 목록을 비워두고 준비 예정 문구만 표시함.
const temporaryTodayMissions = []

// 미션 테이블 API가 연결되면 이 함수 내부를 Axios 조회로 교체하면 됨.
export const getTodayMissions = async () => {
  try {
    return temporaryTodayMissions.map((mission) => ({ ...mission }))
  } catch (error) {
    throw new Error("오늘의 미션을 불러오지 못했습니다.")
  }
}
