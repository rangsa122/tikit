import {isAxiosError} from 'axios'
import {apiClient} from './apiClient'

export type MemberRole = 'MEMBER' | 'MANAGER' | 'ADMIN'
export type AccountStatus = 'ACTIVE' | 'SUSPENDED' | 'WITHDRAWN'
export interface AdminMember {
  memberId: number
  email: string
  name: string
  role: MemberRole
  accountStatus: AccountStatus
  version: number
}
export interface AdminMemberDetail extends AdminMember {
  createdAt: string
  updatedAt: string
  withdrawnAt: string | null
  suspensionReason: string | null
  suspendedAt: string | null
  suspendedBy: number | null
}
export interface MemberSearch {
  keyword: string
  role: MemberRole | ''
  status: AccountStatus | ''
  page: number
  size: number
}
export interface MemberList {
  items: AdminMember[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
interface Response<T> {code: string; message: string; data: T}
const base = '/api/v1/admin/members'
export async function getAdminMembers(search: MemberSearch) {
  const response = await apiClient.get<Response<MemberList>>(base, {
    params: {...search, keyword: search.keyword || undefined, role: search.role || undefined, status: search.status || undefined},
  })
  return response.data.data
}
export async function getAdminMember(id: number) {
  return (await apiClient.get<Response<AdminMemberDetail>>(`${base}/${id}`)).data.data
}
export async function changeMemberStatus(id: number, request: {status: 'ACTIVE' | 'SUSPENDED'; expectedVersion: number; reason: string}) {
  return (await apiClient.patch<Response<AdminMember>>(`${base}/${id}/status`, request)).data.data
}
export async function changeMemberRole(id: number, request: {role: 'MEMBER' | 'MANAGER'; expectedVersion: number; reason: string}) {
  return (await apiClient.patch<Response<AdminMember>>(`${base}/${id}/role`, request)).data.data
}
export function adminError(error: unknown) {
  if (!isAxiosError<{code?: string; message?: string}>(error)) return '요청을 처리하지 못했습니다.'
  if (error.response?.data.code === 'VERSION_CONFLICT') return '다른 관리자가 정보를 변경했습니다. 최신 정보를 다시 조회한 후 진행해 주세요.'
  const messages: Record<number, string> = {
    400: '입력값을 확인해 주세요.', 401: '인증이 만료되었습니다. 다시 로그인해 주세요.',
    403: '접근 권한이 없거나 변경할 수 없는 계정입니다.', 404: '회원을 찾을 수 없습니다.',
    409: '현재 상태에서 처리할 수 없습니다. 최신 정보를 확인해 주세요.',
  }
  return error.response?.data.message || messages[error.response?.status ?? 0] || '서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.'
}
