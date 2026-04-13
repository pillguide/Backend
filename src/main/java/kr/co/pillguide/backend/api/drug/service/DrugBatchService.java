package kr.co.pillguide.backend.api.drug.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class DrugBatchService {

    private final DrugService drugService;

    // 쓰레드 개수
    private final ExecutorService executor = Executors.newFixedThreadPool(5);

    public void saveDrugs(List<String> itemSeqList) {

        List<Future<?>> futures = new ArrayList<>();

        // 병렬 실행
        for (String itemSeq : itemSeqList) {
            futures.add(executor.submit(() -> {
                try {
                    drugService.saveDrugByItemSeq(itemSeq);
                } catch (Exception e) {
                    // 실패해도 전체 중단 안 함
                    System.out.println("저장 실패 itemSeq=" + itemSeq + " / " + e.getMessage());
                }
            }));
        }

        // 모든 작업 완료 대기
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                System.out.println("스레드 실행 실패: " + e.getMessage());
            }
        }
    }
}