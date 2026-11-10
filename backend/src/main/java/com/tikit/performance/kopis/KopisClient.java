package com.tikit.performance.kopis;

import com.tikit.common.ErrorCode;
import com.tikit.performance.exception.PerformanceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class KopisClient {

    private static final DateTimeFormatter KOPIS_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final RestClient restClient;
    private final String serviceKey;

    public KopisClient(@Value("${kopis.base-url}") String baseUrl,
                       @Value("${kopis.service-key:}") String serviceKey) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.serviceKey = serviceKey;
    }

    /**
     * 공연 목록 조회. 기간은 최대 31일, rows는 최대 100.
     */
    public List<KopisPerformanceSummary> searchPerformances(
            LocalDate startDate, LocalDate endDate, String title, String kopisGenreCode,
            int page, int rows) {

        String xml = call(uriBuilder -> {
            uriBuilder.path("/pblprfr")
                    .queryParam("service", serviceKey)
                    .queryParam("stdate", startDate.format(DateTimeFormatter.BASIC_ISO_DATE))
                    .queryParam("eddate", endDate.format(DateTimeFormatter.BASIC_ISO_DATE))
                    .queryParam("cpage", page)
                    .queryParam("rows", rows);
            if (StringUtils.hasText(title)) {
                uriBuilder.queryParam("shprfnm", title);
            }
            if (StringUtils.hasText(kopisGenreCode)) {
                uriBuilder.queryParam("shcate", kopisGenreCode);
            }
            return uriBuilder.build();
        });

        List<KopisPerformanceSummary> items = new ArrayList<>();
        for (Element db : parseDbElements(xml)) {
            items.add(new KopisPerformanceSummary(
                    text(db, "mt20id"),
                    text(db, "prfnm"),
                    date(text(db, "prfpdfrom")),
                    date(text(db, "prfpdto")),
                    text(db, "fcltynm"),
                    text(db, "poster"),
                    text(db, "area"),
                    text(db, "genrenm"),
                    text(db, "prfstate")
            ));
        }
        return items;
    }

    /**
     * 공연 상세 조회. 없으면 null.
     */
    public KopisPerformanceDetail getPerformance(String kopisId) {
        String xml = call(uriBuilder -> uriBuilder
                .path("/pblprfr/{id}")
                .queryParam("service", serviceKey)
                .build(kopisId));

        List<Element> dbs = parseDbElements(xml);
        if (dbs.isEmpty()) {
            return null;
        }
        Element db = dbs.get(0);
        return new KopisPerformanceDetail(
                text(db, "mt20id"),
                text(db, "prfnm"),
                date(text(db, "prfpdfrom")),
                date(text(db, "prfpdto")),
                text(db, "fcltynm"),
                text(db, "poster"),
                text(db, "genrenm"),
                text(db, "sty"),
                text(db, "entrpsnm"),
                text(db, "prfcast"),
                text(db, "prfruntime"),
                text(db, "prfage"),
                text(db, "dtguidance"),
                text(db, "pcseguidance"),
                text(db, "prfstate")
        );
    }

    // ===== 아래는 도우미 =====

    private String call(java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI> uriFunction) {
        try {
            return restClient.get().uri(uriFunction).retrieve().body(String.class);
        } catch (Exception e) {
            // 호출 실패, 시간 초과 등은 모두 "KOPIS 일시 불가"로 처리한다
            throw new PerformanceException(ErrorCode.KOPIS_UNAVAILABLE);
        }
    }

    /** XML에서 <db> 요소들을 꺼낸다. 오류 응답이면 예외를 던진다. */
    private List<Element> parseDbElements(String xml) {
        if (!StringUtils.hasText(xml)) {
            return List.of();
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // 외부 문서를 읽어 들이는 공격을 막는다
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            Document document = factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)));

            checkReturnCode(document);

            NodeList nodes = document.getElementsByTagName("db");
            List<Element> elements = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                Element element = (Element) nodes.item(i);
                // 오류나 결과 없음 응답에는 공연 ID가 없다
                if (text(element, "mt20id") != null) {
                    elements.add(element);
                }
            }
            return elements;
        } catch (PerformanceException e) {
            throw e;                       // 우리가 던진 예외는 그대로 올려보낸다
        } catch (Exception e) {
            throw new PerformanceException(ErrorCode.KOPIS_UNAVAILABLE);
        }
    }

    /** KOPIS 결과코드를 확인한다. 정상(00)과 결과없음(04)은 그냥 통과. */
    private void checkReturnCode(Document document) {
        NodeList codes = document.getElementsByTagName("returncode");
        if (codes.getLength() == 0) {
            return;                        // 정상 응답에는 returncode가 없다
        }

        String code = codes.item(0).getTextContent().trim();
        if ("00".equals(code) || "04".equals(code)) {
            return;
        }

        // 01 잘못된 요청, 05 기간 31일 초과, 06 조회 100건 초과 → 우리 요청이 잘못된 것
        if ("01".equals(code) || "05".equals(code) || "06".equals(code)) {
            throw new PerformanceException(ErrorCode.VALIDATION_ERROR);
        }

        // 02 키 미등록, 03 DB 오류 → KOPIS 쪽 문제
        throw new PerformanceException(ErrorCode.KOPIS_UNAVAILABLE);
    }

    /** 자식 태그의 글자를 꺼낸다. 없거나 비어 있으면 null. */
    private String text(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        String value = nodes.item(0).getTextContent().trim();
        return value.isEmpty() ? null : value;
    }

    /** "2016.05.12" 형태를 LocalDate로. 실패하면 null. */
    private LocalDate date(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value, KOPIS_DATE);
        } catch (Exception e) {
            return null;
        }
    }
}