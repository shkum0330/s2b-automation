package com.backend.service;

import com.backend.service.util.CountryCode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ScrapingService {
    private static final String NARA_SEARCH_URL = "https://goods.g2b.go.kr:8053/search/unifiedSearch.do?searchWord=";
    private static final String NARA_REFERER_URL = "https://goods.g2b.go.kr:8053/search/unifiedSearch.do";

    private static final List<String> USER_AGENTS = Arrays.asList(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    );

    private final Random random = new Random();

    // G2B 물품목록번호를 찾는 기존 메서드 (Optional<String> 반환하도록 수정)
    public Optional<String> findG2bClassificationNumber(String modelName) {
        try {
            Document doc = getScrapingDocument(modelName);
            Element firstResultItem = doc.selectFirst("ul.bb_d7dbe4 > li:first-child");

            if (firstResultItem == null) {
                log.info("G2B 번호 검색 결과가 없습니다. (모델명: {})", modelName);
                return Optional.empty();
            }

            Element highlightElement = firstResultItem.selectFirst("span.searchKeyword");

            if (highlightElement != null) {
                Element numberElement = firstResultItem.selectFirst(".searchLabel_blue .labelNum");
                if (numberElement != null) {
                    String fullNumberText = numberElement.text().trim();
                    String[] numberParts = fullNumberText.split("-");
                    if (numberParts.length == 2) {
                        log.info("G2B 번호 스크래핑 성공: '{}'", numberParts[1]);
                        return Optional.of(numberParts[1]);
                    }
                }
            }
            log.info("G2B 번호 스크래핑 결과: 하이라이트된 검색어가 없습니다. (모델명: {})", modelName);
            return Optional.empty();

        } catch (Exception e) {
            log.error("G2B 번호 스크래핑 중 오류 발생 (모델명: {})", modelName, e);
            return Optional.empty();
        }
    }

    // 원산지(국가 코드)를 스크래핑
    public Optional<String> findCountryOfOrigin(String modelName) {
        try {
            Document doc = getScrapingDocument(modelName);
            // 링크(a) 태그가 아닌, 전체 텍스트를 포함하는 상위 div 태그를 선택
            Element titleDiv = doc.selectFirst("div.searchListImgTit");

            if (titleDiv == null) {
                log.info("원산지 검색 결과가 없습니다. (모델명: {})", modelName);
                return Optional.empty();
            }

            String fullText = titleDiv.text(); // 예: "공기청정기, 삼성전자, (TH)AX033B310GBD, 33.1㎡, 26W"

            // 정규표현식을 사용하여 (XX) 형식의 국가 코드를 찾음
            Pattern pattern = Pattern.compile("\\(([A-Z]{2})\\)");
            Matcher matcher = pattern.matcher(fullText);

            if (matcher.find()) {
                String countryCode = matcher.group(1); // "TH"
                log.info("원산지 스크래핑 성공: 국가코드 '{}'를 찾았습니다.", countryCode);

                // 찾은 국가 코드를 CountryCode Enum을 사용해 국가명으로 변환
                return CountryCode.fromCode(countryCode).map(CountryCode::getCountryName);

            } else {
                log.info("원산지 스크래핑 결과: 텍스트에서 국가코드를 찾을 수 없습니다. (전체 텍스트: {})", fullText);
                return Optional.empty();
            }

        } catch (Exception e) {
            log.error("원산지 스크래핑 중 오류 발생 (모델명: {})", modelName, e);
            return Optional.empty();
        }
    }

    // 중복되는 Jsoup 접속 로직을 별도 메서드로 분리
    private Document getScrapingDocument(String modelName) throws Exception {
        TimeUnit.MILLISECONDS.sleep(1000 + random.nextInt(2000));
        String encodedModelName = URLEncoder.encode(modelName, StandardCharsets.UTF_8);
        String searchUrl = NARA_SEARCH_URL + encodedModelName;
        log.info("Scraping at: {}", searchUrl);

        String randomUserAgent = USER_AGENTS.get(random.nextInt(USER_AGENTS.size()));

        return Jsoup.connect(searchUrl)
                .header("User-Agent", randomUserAgent)
                .header("Referer", NARA_REFERER_URL)
                .timeout(10000)
                .get();
    }
}