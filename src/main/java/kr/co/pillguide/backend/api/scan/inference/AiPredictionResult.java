package kr.co.pillguide.backend.api.scan.inference;

import java.util.List;

public record AiPredictionResult(int width, int height, String modelSha256, String mappingSha256,
                                 List<Prediction> predictions) {
    public AiPredictionResult {
        predictions = List.copyOf(predictions);
    }

    public record Prediction(int detectionIndex, int classId, String itemSeq, double confidence,
                             List<Double> bboxXyxy, boolean recognitionMayBeDegraded) {
        public Prediction {
            bboxXyxy = List.copyOf(bboxXyxy);
        }
    }
}
