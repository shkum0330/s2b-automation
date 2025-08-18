package com.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ScrapingService {
    private static final String NARA_SEARCH_URL = "https://goods.g2b.go.kr:8053/search/unifiedSearch.do?searchWord=";
    private static final String NARA_REFERER_URL = "https://goods.g2b.go.kr:8053/search/unifiedSearch.do";

    private static final List<String> USER_AGENTS = Arrays.asList(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:126.0) Gecko/20100101 Firefox/126.0",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    );

    private final Random random = new Random();

    public String findG2bClassificationNumber(String modelName) {
        try {
            TimeUnit.MILLISECONDS.sleep(1000 + random.nextInt(2000));

            String encodedModelName = URLEncoder.encode(modelName, StandardCharsets.UTF_8);
            String searchUrl = NARA_SEARCH_URL + encodedModelName;
            log.info("Searching G2B Number at: {}", searchUrl);

            String randomUserAgent = USER_AGENTS.get(random.nextInt(USER_AGENTS.size()));

            Document doc = Jsoup.connect(searchUrl)
                    .header("User-Agent", randomUserAgent)
                    .header("Referer", NARA_REFERER_URL)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                    .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                    .timeout(10000)
                    .get();

            Element firstResultItem = doc.selectFirst("ul.bb_d7dbe4 > li:first-child");

            if (firstResultItem == null) {
                log.info("검색 결과가 없습니다. (모델명: {})", modelName);
                return "";
            }

            // 2. 해당 블록 내에 하이라이트 태그(<span class="searchKeyword">)로 올바른 물품인지 확인
            Element highlightElement = firstResultItem.selectFirst("span.searchKeyword");

            if (highlightElement != null) {
                Element numberElement = firstResultItem.selectFirst(".searchLabel_blue .labelNum");
                if (numberElement != null) {
                    String fullNumberText = numberElement.text().trim(); // "40161602-24574852"
                    String[] numberParts = fullNumberText.split("-");

                    // 하이픈이 포함된 정상적인 형식인지 확인
                    if (numberParts.length == 2) {
                        String lastEightDigits = numberParts[1]; // "24574852"
                        log.info("스크래핑 성공: 전체 번호 '{}'에서 마지막 8자리 '{}'를 추출했습니다.", fullNumberText, lastEightDigits);
                        return lastEightDigits;
                    }
                }
            }

            log.info("첫번째 검색 결과에 하이라이트된 검색어가 없어 값을 가져오지 않습니다. (모델명: {})", modelName);
            return "";

        } catch (InterruptedException e) {
            log.warn("스크래핑 지연 중 스레드 인터럽트 발생", e);
            Thread.currentThread().interrupt();
            return "";
        } catch (Exception e) {
            log.error("G2B 스크래핑 중 오류가 발생했습니다. (모델명: {})", modelName, e);
            return "";
        }
    }
}