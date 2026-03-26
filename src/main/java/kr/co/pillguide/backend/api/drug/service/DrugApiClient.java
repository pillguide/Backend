package kr.co.pillguide.backend.api.drug.service;

import kr.co.pillguide.backend.api.drug.config.DrugApiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Service
@RequiredArgsConstructor
public class DrugApiClient {

    private final RestTemplate restTemplate;
    private final DrugApiProperties drugApiProperties;

    public String getDrugByItemSeq(String itemSeq) {
        URI uri = UriComponentsBuilder
                .fromHttpUrl(drugApiProperties.baseUrl())
                .path("/getDrbEasyDrugList")
                .queryParam("ServiceKey", drugApiProperties.serviceKey())
                .queryParam("pageNo", 1)
                .queryParam("numOfRows", 10)
                .queryParam("itemSeq", itemSeq)
                .queryParam("type", "json")
                .encode()
                .build()
                .toUri();

        ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
        return response.getBody();
    }
}