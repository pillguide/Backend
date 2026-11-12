package kr.co.pillguide.backend.api.mydrug.dto;

import java.util.List;

public record AddMyDrugsRequest(Long scanSessionId, List<Long> detectionIds) { }
