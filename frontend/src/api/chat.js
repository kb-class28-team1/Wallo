export async function requestChat(message) {
  const response = await fetch('/api/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
    },
    body: JSON.stringify({ message }),
  })

  const data = await response.json().catch(() => null)

  if (!response.ok) {
    throw new Error(data?.message || 'AI 답변을 불러오지 못했습니다.')
  }

  if (!data?.answer) {
    throw new Error('AI 답변이 비어 있습니다.')
  }

  return data.answer
}
