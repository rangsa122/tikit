import {apiClient} from './apiClient'
import type {ApiResponse} from './apiError'

interface RagSource {
  documentId: string
  documentTitle: string
}

export interface RagQuestionResponse {
  question: string
  answer: string
  sources: RagSource[]
}

export async function askRagQuestion(question: string, signal: AbortSignal): Promise<RagQuestionResponse> {
  const {data: response} = await apiClient.post<ApiResponse<RagQuestionResponse>>(
    '/api/v1/rag/questions',
    {question},
    // 백엔드의 원격 연결과 답변 생성 시간(최대 305초)을 기다린다.
    {timeout: 310_000, signal},
  )

  if (response.code !== 'SUCCESS' || !response.data) {
    throw new Error(response.message || '답변을 불러오지 못했습니다.')
  }

  return response.data
}
