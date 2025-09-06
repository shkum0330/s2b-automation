package com.backend.service.util;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum CountryCode {
    // 아시아
    KR("대한민국"), CN("중국"), JP("일본"), VN("베트남"), HK("홍콩"), TW("대만"), IN("인도"),
    SG("싱가포르"), ID("인도네시아"), MY("말레이시아"), TH("태국"), PH("필리핀"), AE("아랍에미리트"),
    SA("사우디아라비아"), QA("카타르"), TR("터키"), IL("이스라엘"), KZ("카자흐스탄"), UZ("우즈베키스탄"),
    MN("몽골"),

    // 유럽
    DE("독일"), GB("영국"), FR("프랑스"), IT("이탈리아"), ES("스페인"), NL("네덜란드"), BE("벨기에"),
    CH("스위스"), AT("오스트리아"), SE("스웨덴"), NO("노르웨이"), DK("덴마크"), FI("핀란드"), IE("아일랜드"),
    PT("포르투갈"), GR("그리스"), PL("폴란드"), HU("헝가리"), CZ("체코"), RO("루마니아"), RU("러시아"),
    UA("우크라이나"), LU("룩셈부르크"),

    // 북미
    US("미국"), CA("캐나다"), MX("멕시코"),

    // 남미
    BR("브라질"), AR("아르헨티나"), CL("칠레"), PE("페루"), CO("콜롬비아"), VE("베네수엘라"),

    // 오세아니아
    AU("호주"), NZ("뉴질랜드"),

    // 아프리카
    EG("이집트"), ZA("남아프리카 공화국"), NG("나이지리아"), KE("케냐"), GH("가나"),

    // 기타 주요 국가
    AF("아프가니스탄"), AL("알바니아"), DZ("알제리"), AD("안도라"), AO("앙골라"), AG("앤티가 바부다"),
    AM("아르메니아"), AZ("아제르바이잔"), BS("바하마"), BH("바레인"), BD("방글라데시"), BB("바베이도스"),
    BY("벨라루스"), BZ("벨리즈"), BJ("베냉"), BT("부탄"), BO("볼리비아"), BA("보스니아 헤르체고비나"),
    BW("보츠와나"), BG("불가리아"), BF("부르키나파소"), BI("부룬디"), KH("캄보디아"), CM("카메룬"),
    CV("카보베르데"), CF("중앙아프리카 공화국"), TD("차드"), KM("코모로"), CR("코스타리카"), HR("크로아티아"),
    CU("쿠바"), CY("키프로스"), DJ("지부티"), DM("도미니카 연방"), DO("도미니카 공화국"), EC("에콰도르"),
    SV("엘살바도르"), GQ("적도 기니"), EE("에스토니아"), ET("에티오피아"), FJ("피지"), GA("가봉"),
    GM("감비아"), GE("조지아"), GT("과테말라"), GN("기니"), GY("가이아나"), HT("아이티"),
    HN("온두라스"), IS("아이슬란드"), IR("이란"), IQ("이라크"), JM("자메이카"), JO("요르단"),
    KW("쿠웨이트"), KG("키르기스스탄"), LA("라오스"), LV("라트비아"), LB("레바논"), LR("라이베리아"),
    LY("리비아"), LT("리투아니아"), MG("마다가스카르"), MW("말라위"), MV("몰디브"), ML("말리"),
    MT("몰타"), MR("모리타니"), MU("모리셔스"), MD("몰도바"), MC("모나코"), ME("몬테네그로"),
    MA("모로코"), MZ("모잠비크"), MM("미얀마"), NA("나미비아"), NP("네팔"), NI("니카라과"),
    NE("니제르"), MK("북마케도니아"), OM("오만"), PK("파키스탄"), PA("파나마"), PG("파푸아뉴기니"),
    PY("파라과이"), RW("르완다"), SN("세네갈"), RS("세르비아"), SL("시에라리온"), SK("슬로바키아"),
    SI("슬로베니아"), SO("소말리아"), LK("스리랑카"), SD("수단"), SY("시리아"), TJ("타지키스탄"),
    TZ("탄자니아"), TG("토고"), TN("튀니지"), TM("투르크메니스탄"), UG("우간다"), UY("우루과이"),
    YE("예멘"), ZM("잠비아"), ZW("짐바브웨");

    private final String CountryName;

    CountryCode(String koreanName) {
        this.CountryName = koreanName;
    }

    public String getCountryName() {
        return CountryName;
    }

    private static final Map<String, CountryCode> CODE_MAP =
            Arrays.stream(values())
                    .collect(Collectors.toMap(Enum::name, Function.identity()));

    public static Optional<CountryCode> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(CODE_MAP.get(code.toUpperCase()));
    }
}