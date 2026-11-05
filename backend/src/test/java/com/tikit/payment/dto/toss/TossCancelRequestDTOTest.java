package com.tikit.payment.dto.toss;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

public class TossCancelRequestDTOTest {

    private JsonMapper jsonMapper;

    @BeforeEach
    void setup() {
        jsonMapper = JsonMapper.builder().build();
    }

    @Test
    @DisplayName("전액 취소 요청은 본문에 cancelAmount를 넣지 않는다")
    void cancel_request_full_omits_cancel_amount() {
        // given
        // setup에서 이미 셋팅
        TossCancelRequestDTO request = new TossCancelRequestDTO("고객 요청 취소", null);

        // when
        String json = jsonMapper.writeValueAsString(request);
        System.out.println("테스트: " + json);

        // then
        assertThat(json).contains("\"cancelReason\":\"고객 요청 취소\"");
        assertThat(json).doesNotContain("cancelAmount");
    }

    @Test
    @DisplayName("금액 지정 취소 요청은 본문에 cancelAmount를 넣는다")
    void cancel_request_with_amount_includes_cancel_amount() {
        // given
        // setup에서 이미 셋팅
        TossCancelRequestDTO request = new TossCancelRequestDTO("고객 요청 취소", 45000L);

        // when
        String json = jsonMapper.writeValueAsString(request);
        System.out.println("테스트: " + json);

        // then
        assertThat(json).contains("\"cancelReason\":\"고객 요청 취소\"");
        assertThat(json).contains("\"cancelAmount\":45000");
    }
}
