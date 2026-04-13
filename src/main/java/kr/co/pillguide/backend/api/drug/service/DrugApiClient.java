package kr.co.pillguide.backend.api.drug.service;

import kr.co.pillguide.backend.api.drug.config.DrugApiProperties;
import kr.co.pillguide.backend.api.drug.dto.DrugApiResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
public class DrugApiClient {

    @Qualifier("drugWebClient")
    private final WebClient webClient;

    private final DrugApiProperties drugApiProperties;

    public DrugApiResponseDto getDrugByItemSeq(String itemSeq) {

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/getDrbEasyDrugList")
                        .queryParam("ServiceKey", drugApiProperties.serviceKey())
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