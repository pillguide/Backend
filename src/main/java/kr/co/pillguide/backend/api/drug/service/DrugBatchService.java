package kr.co.pillguide.backend.api.drug.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DrugBatchService {

    private final DrugService drugService;
    private final ExecutorService executor = Executors.newFixedThreadPool(5);

    public void saveDrugs(List<String> itemSeqList) {
        log.info("배치 작업 시작: {}건", itemSeqList.size());

        List<Future<?>> futures = new ArrayList<>();

        for (String itemSeq : itemSeqList) {
            futures.add(executor.submit(() -> {
                try {
                    drugService.saveDrugByItemSeq(itemSeq);
                } catch (Exception e) {
                    // 중요! System.out 대신 log.error를 쓰고 예외 객체(e)를 그대로 넘기세요.
                    log.error("저장 실패 - itemSeq: {}", itemSeq, e);
                }
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                log.error("작업 중단됨", e);
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                log.error("실행 중 심각한 오류 발생", e);
            }
        }

        log.info("모든 배치 작업 완료");
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}