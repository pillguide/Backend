package kr.co.pillguide.backend.api.drug.service;

import kr.co.pillguide.backend.api.drug.config.DrugEasyProperties;
import kr.co.pillguide.backend.api.drug.dto.DrugApiResponseDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class DrugEasyApiClient {

    private final WebClient webClient;
    private final DrugEasyProperties drugEasyProperties;

    public DrugEasyApiClient(@Qualifier("easydrugWebClient") WebClient webClient,
                             DrugEasyProperties drugEasyProperties) {
        this.webClient = webClient;
        this.drugEasyProperties = drugEasyProperties;
    }
    public DrugApiResponseDto getDrugByItemSeq(String itemSeq) {

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/getDrbEasyDrugList")
                        .queryParam("ServiceKey", drugEasyProperties.serviceKey())
                        .queryParam("pageNo", 1)
                        .queryParam("numOfRows", 10)
                        .queryParam("itemSeq", itemSeq)
                        .queryParam("type", "json")
                        .build()
                )
                .retrieve()
                .bodyToMono(DrugApiResponseDto.class)
                .block();
    }
}