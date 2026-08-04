import httpClient from "./httpClient"
import { getApiErrorMessage } from "@/utils/apiError"

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

    throw new Error(getApiErrorMessage(error, "AI 답변을 불러오지 못했습니다."))
  }
}
