import {useEffect, useRef, useState} from 'react'
import type {FormEvent} from 'react'
import {useQuery, useQueryClient} from '@tanstack/react-query'
import {Link} from 'react-router'
import {adminError, changeMemberStatus, changeMemberRole, getAdminMember, getAdminMembers} from '../../api/adminMemberApi'
import type {AccountStatus, AdminMember, MemberRole, MemberSearch} from '../../api/adminMemberApi'
import {useAuthStore} from '../../stores/authStore'
import {getAuthClaims} from '../../utils/authClaims'
import './admin.css'

const roles = {MEMBER: '회원', MANAGER: 'MANAGER', ADMIN: 'ADMIN'}
const statuses = {ACTIVE: '정상', SUSPENDED: '정지', WITHDRAWN: '탈퇴'}
const initialSearch: MemberSearch = {keyword: '', role: '', status: '', page: 0, size: 20}
const date = (value: string | null) => value ? new Date(value).toLocaleString('ko-KR') : '-'

function roleChangeTarget(member: AdminMember, actorId: string): 'MEMBER' | 'MANAGER' | null {
  if (member.accountStatus !== 'ACTIVE' || String(member.memberId) === actorId) return null
  if (member.role === 'MEMBER') return 'MANAGER'
  if (member.role === 'MANAGER') return 'MEMBER'
  return null
}

function roleChangeLabel(role: 'MEMBER' | 'MANAGER') {
  return role === 'MEMBER' ? '일반회원으로 변경' : 'MANAGER로 변경'
}

function usePanelFocus() {
  const panel = useRef<HTMLElement>(null)
  useEffect(() => {
    panel.current?.scrollIntoView({block: 'start'})
    panel.current?.focus({preventScroll: true})
  }, [])
  return panel
}

export default function AdminMembersPage() {
  const token = useAuthStore(state => state.tokens?.accessToken)
  const claims = getAuthClaims(token)
  const client = useQueryClient()
  const [search, setSearch] = useState(initialSearch)
  const [keyword, setKeyword] = useState('')
  const [selected, setSelected] = useState<number | null>(null)
  const [changingRole, setChangingRole] = useState(false)
  const [notice, setNotice] = useState('')
  const list = useQuery({queryKey: ['admin-members', token, search], queryFn: () => getAdminMembers(search), retry: false})
  function updateSearch(value: Partial<MemberSearch>) {setSearch(current => ({...current, ...value, page: 0})); setNotice('')}
  async function refresh() {
    await client.invalidateQueries({queryKey: ['admin-members']})
  }
  return (
    <main className="admin-page">
      <div className="admin-heading"><div><p className="admin-eyebrow">TIKIT ADMIN</p><h1>회원 관리</h1><p>회원의 이용 상태를 관리하고 일반회원과 MANAGER 간 권한을 변경합니다.</p></div></div>
      {notice && <p role="status" className="admin-notice">{notice}</p>}
      <form className="admin-filters" onSubmit={event => {event.preventDefault(); updateSearch({keyword: keyword.trim()})}}>
        <label>이름·이메일<input type="search" maxLength={254} value={keyword} placeholder="이름 또는 이메일 검색" onChange={event => setKeyword(event.target.value)} /></label>
        <label>역할<select value={search.role} onChange={event => updateSearch({role: event.target.value as MemberRole | ''})}><option value="">전체 역할</option>{Object.entries(roles).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>
        <label>상태<select value={search.status} onChange={event => updateSearch({status: event.target.value as AccountStatus | ''})}><option value="">전체 상태</option>{Object.entries(statuses).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>
        <button className="admin-primary">검색</button><button type="button" onClick={() => {setKeyword(''); setSearch(initialSearch)}}>초기화</button>
      </form>
      <section className="admin-panel" aria-label="회원 목록">
        <div className="admin-list-heading"><h2>회원 목록 <span>{list.data?.totalElements.toLocaleString() ?? '-'}명</span></h2><button onClick={() => void list.refetch()} disabled={list.isFetching}>새로고침</button></div>
        {list.isPending ? <p role="status" className="admin-empty">회원 목록을 불러오고 있습니다.</p> : list.isError ? <div className="admin-empty"><p role="alert">{adminError(list.error)}</p><button onClick={() => void list.refetch()}>다시 시도</button></div> : <>
          <div className="admin-table-wrap"><table><thead><tr><th>회원 번호</th><th>이름</th><th>이메일</th><th>역할</th><th>상태</th><th>관리</th></tr></thead><tbody>{list.data.items.map(member => {
            const targetRole = roleChangeTarget(member, claims?.id ?? '')
            return <tr key={member.memberId}><td>{member.memberId}</td><td>{member.name}</td><td>{member.email}</td><td>{roles[member.role]}</td><td><span className={`admin-badge ${member.accountStatus.toLowerCase()}`}>{statuses[member.accountStatus]}</span></td><td><button onClick={() => {setSelected(member.memberId); setChangingRole(false); setNotice('')}}>상세 보기</button>{targetRole && <button onClick={() => {setSelected(member.memberId); setChangingRole(true); setNotice('')}}>{roleChangeLabel(targetRole)}</button>}</td></tr>
          })}</tbody></table></div>
          {!list.data.items.length && <p className="admin-empty">검색 조건에 맞는 회원이 없습니다.</p>}
          <nav className="admin-pagination" aria-label="회원 목록 페이지"><button disabled={search.page === 0 || list.isFetching} onClick={() => setSearch(value => ({...value, page: value.page - 1}))}>이전</button><span>{search.page + 1} / {Math.max(1, list.data.totalPages)}</span><button disabled={search.page + 1 >= list.data.totalPages || list.isFetching} onClick={() => setSearch(value => ({...value, page: value.page + 1}))}>다음</button></nav>
        </>}
      </section>
      {selected !== null && <MemberDetail key={`${selected}-${changingRole}`} initialRoleChange={changingRole} id={selected} actorId={claims?.id ?? ''} token={token} close={() => setSelected(null)} updated={refresh} />}
    </main>
  )
}

function MemberDetail({id, actorId, token, close, updated, initialRoleChange}: {initialRoleChange: boolean; id: number; actorId: string; token?: string; close: () => void; updated: () => Promise<void>}) {
  const panel = usePanelFocus()
  const detail = useQuery({queryKey: ['admin-member', token, id], queryFn: () => getAdminMember(id), retry: false, staleTime: 0})
  const [confirming, setConfirming] = useState<'status' | 'role' | null>(initialRoleChange ? 'role' : null)
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [needsRefresh, setNeedsRefresh] = useState(false)
  const lock = useRef(false)
  const member = detail.data
  const protectedMember = member && (member.role === 'ADMIN' || String(member.memberId) === actorId || member.accountStatus === 'WITHDRAWN')
  const targetRole = member ? roleChangeTarget(member, actorId) : null
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!member || !confirming || lock.current || !reason.trim() || protectedMember || needsRefresh || detail.isFetching || (confirming === 'role' && !targetRole)) return
    lock.current = true; setBusy(true); setMessage('')
    try {
      if (confirming === 'role' && targetRole) {
        await changeMemberRole(id, {role: targetRole, expectedVersion: member.version, reason: reason.trim()})
      } else {
        await changeMemberStatus(id, {status: member.accountStatus === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE', expectedVersion: member.version, reason: reason.trim()})
      }
      setConfirming(null); setReason('')
      setMessage(confirming === 'role' && targetRole ? `${targetRole === 'MEMBER' ? '일반회원' : 'MANAGER'}으로 변경했습니다. 해당 회원은 다시 로그인하면 변경된 권한이 반영됩니다.` : member.accountStatus === 'ACTIVE' ? '계정을 정지했습니다.' : '계정 정지를 해제했습니다. 해당 회원은 다시 로그인할 수 있습니다.')
      await Promise.all([detail.refetch(), updated()])
    } catch (error) {
      setMessage(adminError(error)); setConfirming(null); setReason(''); setNeedsRefresh(true)
    } finally {lock.current = false; setBusy(false)}
  }
  return <section ref={panel} tabIndex={-1} className="admin-panel admin-detail" aria-label="회원 상세"><div className="admin-list-heading"><h2>회원 상세</h2><button onClick={close} disabled={busy}>닫기</button></div>
    {detail.isPending ? <p role="status">상세 정보를 불러오고 있습니다.</p> : detail.isError ? <p role="alert">{adminError(detail.error)}</p> : member && <>
      <dl>{Object.entries({'회원 번호': member.memberId, '이름': member.name, '이메일': member.email, '역할': roles[member.role], '상태': statuses[member.accountStatus], '가입일': date(member.createdAt), '수정일': date(member.updatedAt), '탈퇴일': date(member.withdrawnAt)}).map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
      {member.accountStatus === 'SUSPENDED' && (
        <section className="admin-suspension" aria-labelledby="suspension-heading">
          <h3 id="suspension-heading">현재 정지 정보</h3>
          <dl>
            <div className="admin-suspension-reason"><dt>정지 사유</dt><dd>{member.suspensionReason?.trim() ? member.suspensionReason : '기록된 정지 사유가 없습니다.'}</dd></div>
            <div><dt>정지 일시</dt><dd>{date(member.suspendedAt)}</dd></div>
            <div><dt>처리 관리자</dt><dd>{member.suspendedBy == null ? '-' : `회원 번호 ${member.suspendedBy}`}</dd></div>
          </dl>
          <p className="admin-muted">정지 사유를 확인한 후 정지 해제 여부를 결정해 주세요.</p>
        </section>
      )}
      {protectedMember ? <p className="admin-muted">ADMIN·본인·이미 탈퇴한 계정은 상태와 권한을 변경할 수 없습니다.</p> : !confirming && <button disabled={busy || needsRefresh || detail.isFetching} onClick={() => {setConfirming('status'); setMessage('')}}>{member.accountStatus === 'ACTIVE' ? '계정 정지' : '정지 해제'}</button>}
      {targetRole && !confirming && <button disabled={busy || needsRefresh || detail.isFetching} onClick={() => {setConfirming('role'); setMessage('')}}>{roleChangeLabel(targetRole)}</button>}
      {member.accountStatus === 'SUSPENDED' && !protectedMember && <p className="admin-muted">권한을 변경하려면 먼저 정지를 해제해 주세요.</p>}
      {confirming === 'role' && !targetRole && <p className="admin-muted">현재 계정은 권한을 변경할 수 없습니다. 최신 정보를 확인해 주세요.</p>}
      {confirming && !protectedMember && (confirming !== 'role' || targetRole) && (
        <form onSubmit={submit} className="admin-confirm">
          <h3>{member.name} 계정을 {confirming === 'role' && targetRole ? roleChangeLabel(targetRole) : member.accountStatus === 'ACTIVE' ? '정지' : '정지 해제'}하시겠습니까?</h3>
          {confirming === 'role' && targetRole === 'MEMBER' && <p className="admin-muted">일반회원으로 변경하면 공연 운영 권한이 해제됩니다. 해당 회원에게 다시 로그인하도록 안내해 주세요.</p>}
          <label>{confirming === 'status' ? member.accountStatus === 'SUSPENDED' ? '정지 해제 사유' : '정지 사유' : '권한 변경 사유'}<textarea required maxLength={1000} value={reason} onChange={event => setReason(event.target.value)} disabled={busy} /></label>
          <div className="admin-actions">
            <button className="admin-primary" disabled={busy || !reason.trim() || needsRefresh || detail.isFetching}>{busy ? '처리 중…' : '변경 확인'}</button>
            <button type="button" disabled={busy} onClick={() => {setConfirming(null); setReason('')}}>취소</button>
          </div>
        </form>
      )}
    </>}
    {message && <p role="status" className="admin-notice">{message}</p>}
    <div className="admin-actions"><button disabled={busy || detail.isFetching} onClick={async () => {const result = await detail.refetch(); if (result.isSuccess) {setNeedsRefresh(false); setConfirming(null); setReason(''); setMessage('최신 정보를 조회했습니다.')} await updated()}}>최신 정보 다시 조회</button>{detail.isError && <Link to="/login">로그인 화면</Link>}</div>
  </section>
}
