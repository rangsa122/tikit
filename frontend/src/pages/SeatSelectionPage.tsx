
import { useEffect, useState } from "react";
import { useParams } from "react-router";

import { getSessionSeats } from "../api/sessionSeatApi";
import type { SessionSeat } from "../api/sessionSeatApi";
import { mockSessionSeats } from "../mocks/sessionSeats";

// 개발용 Mock 데이터 사용 여부
// 실제 백엔드 API 테스트 시 false로 변경
const USE_MOCK_SEATS = false;

// 좌석 상태별 색상 반환
function getSeatColor(seat: SessionSeat, selected: boolean) {
    if (selected) return "#2563eb";

    if (seat.status !== "AVAILABLE") {
        return "#9ca3af";
    }

    if (seat.grade === "VIP") {
        return "#a855f7";
    }

    return "#22c55e";
}

export default function SeatSelectionPage() {
    // URL에서 공연 회차 ID 가져오기
    const { sessionId } = useParams();

    // 좌석 목록
    const [seats, setSeats] = useState<SessionSeat[]>([]);

    // 선택한 좌석 ID 목록
    const [selectedSeatIds, setSelectedSeatIds] = useState<number[]>([]);

    // 로딩 및 오류 상태
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        let cancelled = false;

        async function loadSeats() {
            setLoading(true);
            setError(null);
            setSeats([]);
            setSelectedSeatIds([]);

            const id = Number(sessionId);

            // 올바르지 않은 회차 ID 처리
            if (!sessionId || !Number.isSafeInteger(id) || id <= 0) {
                setError("올바르지 않은 공연 회차 ID입니다.");
                setLoading(false);
                return;
            }

            try {
                // Mock 모드에서는 임시 좌석 데이터 사용
                const data = USE_MOCK_SEATS
                    ? mockSessionSeats
                    : await getSessionSeats(id);

                if (!cancelled) {
                    setSeats(data);
                }
            } catch (error) {
                if (!cancelled) {
                    setError(
                        error instanceof Error
                            ? error.message
                            : "좌석 정보를 불러오지 못했습니다."
                    );
                }
            } finally {
                if (!cancelled) {
                    setLoading(false);
                }
            }
        }

        void loadSeats();

        return () => {
            cancelled = true;
        };
    }, [sessionId]);

    // 좌석 선택 및 선택 해제
    const toggleSeat = (seat: SessionSeat) => {
        // 예매 불가능한 좌석은 선택하지 않음
        if (seat.status !== "AVAILABLE") {
            return;
        }

        setSelectedSeatIds((previous) =>
            previous.includes(seat.seatId)
                ? previous.filter((id) => id !== seat.seatId)
                : [...previous, seat.seatId]
        );
    };

    // 선택한 좌석 정보
    const selectedSeats = seats.filter((seat) =>
        selectedSeatIds.includes(seat.seatId)
    );

    // 선택 좌석 총금액
    const totalPrice = selectedSeats.reduce(
        (sum, seat) => sum + seat.price,
        0
    );

    // 좌석 좌표가 음수여도 표시되도록 배치도 크기 계산
    const minX = Math.min(0, ...seats.map((seat) => seat.x));
    const minY = Math.min(0, ...seats.map((seat) => seat.y));

    const mapWidth = Math.max(
        360,
        ...seats.map((seat) => seat.x - minX + 55)
    );

    const mapHeight = Math.max(
        300,
        ...seats.map((seat) => seat.y - minY + 55)
    );

    return (
        <div
            style={{
                maxWidth: "1100px",
                margin: "0 auto",
                padding: "40px 24px",
                fontFamily: "Arial, sans-serif",
                color: "#1f2937",
            }}
        >
            {/* 페이지 제목 */}
            <div style={{ marginBottom: "30px" }}>
                <h1
                    style={{
                        fontSize: "30px",
                        fontWeight: "bold",
                        marginBottom: "8px",
                    }}
                >
                    공연 좌석 선택
                </h1>

                <p style={{ color: "#6b7280" }}>
                    공연 회차 ID: {sessionId}
                </p>
            </div>

            {/* 로딩 상태 */}
            {loading && (
                <p>좌석 정보를 불러오는 중입니다...</p>
            )}

            {/* 오류 상태 */}
            {!loading && error && (
                <p role="alert" style={{ color: "#dc2626" }}>
                    {error}
                </p>
            )}

            {/* 좌석이 없는 경우 */}
            {!loading && !error && seats.length === 0 && (
                <p>등록된 좌석이 없습니다.</p>
            )}

            {/* 좌석 선택 화면 */}
            {!loading && !error && seats.length > 0 && (
                <div
                    style={{
                        display: "grid",
                        gridTemplateColumns:
                            "minmax(0, 1fr) 280px",
                        gap: "24px",
                        alignItems: "start",
                    }}
                >
                    {/* 왼쪽: 좌석 배치도 */}
                    <section
                        style={{
                            border: "1px solid #e5e7eb",
                            borderRadius: "12px",
                            padding: "24px",
                            backgroundColor: "#ffffff",
                            minWidth: 0,
                        }}
                    >
                        <h2
                            style={{
                                fontSize: "20px",
                                fontWeight: "bold",
                                marginBottom: "24px",
                            }}
                        >
                            좌석 배치도
                        </h2>

                        {/* 공연 무대 */}
                        <div
                            style={{
                                backgroundColor: "#e5e7eb",
                                borderRadius: "8px",
                                padding: "14px",
                                textAlign: "center",
                                fontWeight: "bold",
                                letterSpacing: "3px",
                                marginBottom: "35px",
                            }}
                        >
                            STAGE
                        </div>

                        {/* 좌석 좌표를 이용한 배치 */}
                        <div style={{ overflowX: "auto" }}>
                            <div
                                style={{
                                    position: "relative",
                                    width: `${mapWidth}px`,
                                    height: `${mapHeight}px`,
                                    margin: "0 auto",
                                }}
                            >
                                {seats.map((seat) => {
                                    const selected =
                                        selectedSeatIds.includes(
                                            seat.seatId
                                        );

                                    const available =
                                        seat.status === "AVAILABLE";

                                    return (
                                        <button
                                            key={seat.seatId}
                                            type="button"
                                            disabled={!available}
                                            aria-pressed={selected}
                                            aria-label={`${seat.label}, ${seat.grade}등급, ${seat.price.toLocaleString()}원, ${
                                                available
                                                    ? "선택 가능"
                                                    : "선택 불가"
                                            }`}
                                            title={`${seat.label} / ${seat.grade} / ${seat.price.toLocaleString()}원`}
                                            onClick={() => toggleSeat(seat)}
                                            style={{
                                                position: "absolute",
                                                left: `${seat.x - minX}px`,
                                                top: `${seat.y - minY}px`,
                                                width: "45px",
                                                height: "45px",
                                                border: selected
                                                    ? "3px solid #1d4ed8"
                                                    : "1px solid transparent",
                                                borderRadius: "8px",
                                                backgroundColor:
                                                    getSeatColor(
                                                        seat,
                                                        selected
                                                    ),
                                                color: "#ffffff",
                                                fontWeight: "bold",
                                                fontSize: "12px",
                                                cursor: available
                                                    ? "pointer"
                                                    : "not-allowed",
                                                opacity: available
                                                    ? 1
                                                    : 0.5,
                                            }}
                                        >
                                            {seat.label}
                                        </button>
                                    );
                                })}
                            </div>
                        </div>

                        {/* 좌석 상태 범례 */}
                        <div
                            style={{
                                display: "flex",
                                flexWrap: "wrap",
                                gap: "18px",
                                marginTop: "20px",
                                fontSize: "13px",
                            }}
                        >
                            <span>🟣 VIP</span>
                            <span>🟢 R</span>
                            <span>🔵 선택한 좌석</span>
                            <span>⚪ 선택 불가</span>
                        </div>
                    </section>

                    {/* 오른쪽: 선택한 좌석 정보 */}
                    <aside
                        style={{
                            border: "1px solid #e5e7eb",
                            borderRadius: "12px",
                            padding: "24px",
                            backgroundColor: "#f9fafb",
                        }}
                    >
                        <h2
                            style={{
                                fontSize: "20px",
                                fontWeight: "bold",
                                marginBottom: "20px",
                            }}
                        >
                            선택한 좌석
                        </h2>

                        {selectedSeats.length === 0 ? (
                            <p
                                style={{
                                    color: "#6b7280",
                                    fontSize: "14px",
                                }}
                            >
                                좌석을 선택해 주세요.
                            </p>
                        ) : (
                            <div>
                                {selectedSeats.map((seat) => (
                                    <div
                                        key={seat.seatId}
                                        style={{
                                            padding: "12px 0",
                                            borderBottom:
                                                "1px solid #e5e7eb",
                                        }}
                                    >
                                        <strong>{seat.label}</strong>

                                        <p>
                                            {seat.grade} 등급
                                        </p>

                                        <p>
                                            {seat.price.toLocaleString()}원
                                        </p>

                                        <button
                                            type="button"
                                            onClick={() =>
                                                toggleSeat(seat)
                                            }
                                            style={{
                                                marginTop: "6px",
                                                color: "#dc2626",
                                                cursor: "pointer",
                                            }}
                                        >
                                            선택 해제
                                        </button>
                                    </div>
                                ))}
                            </div>
                        )}

                        {/* 선택 좌석 수 및 총금액 */}
                        <div
                            style={{
                                marginTop: "24px",
                                paddingTop: "16px",
                                borderTop: "1px solid #d1d5db",
                            }}
                        >
                            <p>
                                선택 좌석 수:{" "}
                                <strong>
                                    {selectedSeats.length}석
                                </strong>
                            </p>

                            <p
                                style={{
                                    marginTop: "12px",
                                    fontSize: "18px",
                                }}
                            >
                                총금액
                            </p>

                            <strong
                                style={{
                                    fontSize: "24px",
                                    color: "#2563eb",
                                }}
                            >
                                {totalPrice.toLocaleString()}원
                            </strong>
                        </div>

                        {/* 예매 진행은 다음 단계에서 구현 */}
                        <button
                            type="button"
                            disabled
                            style={{
                                width: "100%",
                                padding: "14px",
                                marginTop: "24px",
                                borderRadius: "8px",
                                backgroundColor: "#d1d5db",
                                color: "#ffffff",
                                fontWeight: "bold",
                                cursor: "not-allowed",
                            }}
                        >
                            다음 단계 (추후 구현)
                        </button>
                    </aside>
                </div>
            )}
        </div>
    );
}
