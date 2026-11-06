
import {Link} from 'react-router'

export default function FavoritePerformancesPage() {
  return (
    <div className="mx-auto w-full max-w-[1320px] px-5 py-10">
      <h1 className="border-b border-[#333] pb-5 text-[28px] font-semibold">
        관심 공연
      </h1>

      <div className="mt-8">
        <div className="mb-5 flex items-center justify-between">
          <h2 className="text-[20px] font-semibold">
            나의 관심 공연
          </h2>
        </div>

        <div className="flex min-h-80 flex-col items-center justify-center rounded-lg border border-[#ddd] px-5 py-12 text-center">
          <p className="text-[18px] font-semibold text-[#111]">
            관심 공연 목록을 준비하고 있습니다.
          </p>

          <p className="mt-3 text-[14px] text-[#777]">
            관심 공연 조회 기능이 연결되면 등록한 공연을 이곳에서 확인할 수 있습니다.
          </p>

          <Link
            to="/"
            className="mt-7 rounded-md bg-[#333] px-6 py-3 text-[14px] font-medium text-white hover:bg-black"
          >
            공연 둘러보기
          </Link>
        </div>
      </div>
    </div>
  )
}
