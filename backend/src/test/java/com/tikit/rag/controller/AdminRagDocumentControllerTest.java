package com.tikit.rag.controller;

import com.tikit.common.advice.GlobalExceptionHandler;
import com.tikit.rag.controller.advice.RagControllerAdvice;
import com.tikit.rag.service.RagDocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminRagDocumentControllerTest {

    private RagDocumentService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(RagDocumentService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AdminRagDocumentController(service))
                .setControllerAdvice(new RagControllerAdvice(), new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("페이지 번호나 페이지 크기가 허용 범위를 벗어나면 400을 반환한다")
    void rejectsInvalidPagination() throws Exception {
        for (String query : new String[]{"page=-1", "size=0", "size=101"}) {
            mvc.perform(get("/api/v1/admin/rag/documents?" + query))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("문서 ID가 양의 정수가 아니면 400을 반환한다")
    void rejectsInvalidDocumentId() throws Exception {
        for (String id : new String[]{"0", "-1", "abc"}) {
            mvc.perform(get("/api/v1/admin/rag/documents/" + id))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("문서 제목이 공백이면 400을 반환하고 서비스를 호출하지 않는다")
    void rejectsBlankDocument() throws Exception {
        mvc.perform(post("/api/v1/admin/rag/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" \",\"content\":\"본문\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("본문 누락이나 잘못된 JSON은 공통 오류 형식으로 400을 반환한다")
    void rejectsMissingOrMalformedBody() throws Exception {
        for (String body : new String[]{"", "{", "null"}) {
            mvc.perform(post("/api/v1/admin/rag/documents")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(service);
    }
}
