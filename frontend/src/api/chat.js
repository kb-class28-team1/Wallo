import httpClient from "./httpClient"

export async function requestChat(message) {
  try {
    const response = await httpClient.post("/api/chat", { message })
    const answer = response.data?.answer

    if (!answer) {
      throw new Error("AI 답변이 비어 있습니다.")
    }

    return answer
  } catch (error) {
    if (error instanceof Error && !error.response) {
      throw error
    }

    const message =
      error.response?.data?.message || "AI 답변을 불러오지 못했습니다."
    throw new Error(message)
  }
}
