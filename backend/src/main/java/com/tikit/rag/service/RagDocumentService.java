package com.tikit.rag.service;

import com.tikit.common.ErrorCode;
import com.tikit.rag.dto.request.RagDocumentCreateRequest;
import com.tikit.rag.dto.response.RagDocumentDetailResponse;
import com.tikit.rag.dto.response.RagDocumentSummaryResponse;
import com.tikit.rag.entity.*;
import com.tikit.rag.exception.RagException;
import com.tikit.rag.repository.RagDocumentRepository;
import com.tikit.rag.repository.RagDocumentVersionRepository;
import com.tikit.rag.repository.RagIndexJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RagDocumentService {

    private final RagDocumentRepository documentRepository;
    private final RagDocumentVersionRepository versionRepository;
    private final RagIndexJobRepository jobRepository;

    // 공통 정책 문서 등록
    @Transactional
    public RagDocumentDetailResponse createPolicy(//등록단위 시작 기본 롤백 대상인 RuntimeException이 전파되면 함께 저장한 데이터가 롤백된다.
            Long adminId,
            RagDocumentCreateRequest request
    ) {
        RagDocument document = RagDocument.createPolicy(adminId);//문서 식별자 확보
        RagDocument savedDocument = documentRepository.save(document);

        String contentHash = calculateHash(request.content());//본문을 SHA-256으로 요약,해시만 계산하며 중복 등록 차단 로직은 없다
        //문서 ID와 제목 본문 해시 작성자로 최초 버전을 만든다
        RagDocumentVersion version =
                RagDocumentVersion.createFirstVersion(
                        savedDocument.getId(),
                        request.title(),
                        request.content(),
                        contentHash,
                        adminId
                );

        RagDocumentVersion savedVersion = versionRepository.save(version);

        // 버전 데이터를 먼저 DB에 반영한다.
        // 트랜잭션의 최종 확정(commit)은 아직 하지 않는다.
        versionRepository.flush();

        RagIndexJob job = RagIndexJob.createQueued(
                savedDocument.getId(),
                savedVersion.getId().getVersion(),
                adminId
        );

        jobRepository.save(job);

        return RagDocumentDetailResponse.from(
                savedDocument,
                savedVersion
        );
    }

    // 공통 정책 문서 목록 조회
    public Page<RagDocumentSummaryResponse> getDocuments(
            Pageable pageable
    ) {
        Page<RagDocument> documents =
                documentRepository.findByTypeAndDeletedAtIsNull(
                        RagDocumentType.POLICY,
                        pageable
                );

        List<RagDocumentVersionId> versionIds =
                documents.getContent().stream() //stream().map():문서 목록을 하나씩 보면서 키 목록으로 바꾼다
                        .map(document -> new RagDocumentVersionId(
                                document.getId(),
                                document.getLatestVersion()
                        ))
                        .toList();

        Map<RagDocumentVersionId, RagDocumentVersion> versionMap =
                versionRepository.findAllById(versionIds).stream()
                        .collect(Collectors.toMap(
                                RagDocumentVersion::getId,
                                Function.identity()
                        ));

        return documents.map(document -> {
            RagDocumentVersionId versionId =
                    new RagDocumentVersionId(
                            document.getId(),
                            document.getLatestVersion()
                    );

            RagDocumentVersion version = versionMap.get(versionId);

            if (version == null) {
                throw new IllegalStateException(
                        "문서의 최신 버전 데이터가 존재하지 않습니다."
                );
            }

            return RagDocumentSummaryResponse.from(document, version);
        });
    }

    // 공통 정책 문서 상세 조회
    public RagDocumentDetailResponse getDocument(Long documentId) {
        RagDocument document =
                documentRepository.findByIdAndTypeAndDeletedAtIsNull(
                                documentId,
                                RagDocumentType.POLICY
                        )
                        .orElseThrow(() ->
                                new RagException(ErrorCode.RESOURCE_NOT_FOUND)
                        );

        RagDocumentVersionId versionId =
                new RagDocumentVersionId(
                        document.getId(),
                        document.getLatestVersion()
                );

        RagDocumentVersion version =
                versionRepository.findById(versionId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "문서의 최신 버전 데이터가 존재하지 않습니다."
                                )
                        );

        return RagDocumentDetailResponse.from(document, version);
    }

    // 저장하는 본문 그대로 SHA-256 해시 계산
    private String calculateHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    content.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 해시 계산을 사용할 수 없습니다.",
                    e
            );
        }
    }
}
