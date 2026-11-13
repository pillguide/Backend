package kr.co.pillguide.backend.api.drug.image;

import java.time.Duration;
import java.util.Optional;
import kr.co.pillguide.backend.api.drug.entity.Drug;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
public class RepresentativeImageService {
    private final Optional<S3Presigner> presigner;
    private final String bucket;
    private final String prefix;

    public RepresentativeImageService(Optional<S3Presigner> presigner,
            @Value("${PILLGUIDE_S3_BUCKET:}") String bucket,
            @Value("${PILLGUIDE_S3_PREFIX:}") String prefix) {
        this.presigner = presigner;
        this.bucket = bucket;
        this.prefix = prefix;
    }

    public RepresentativeImage resolve(Drug drug) {
        if (presigner.isEmpty() || bucket.isBlank() || !prefix.matches("(dev|production)/")) {
            return RepresentativeImage.notReady();
        }
        String hash = drug.getRepresentativeImageHash();
        String code = drug.getCode();
        if (hash == null || !hash.matches("[0-9a-f]{64}") || code == null || !code.matches("[0-9]{9}")) {
            return RepresentativeImage.notReady();
        }
        String expectedKey = prefix + "reference/front/" + code + "/" + hash + ".png";
        if (!expectedKey.equals(drug.getRepresentativeImageKey())) return RepresentativeImage.notReady();
        try {
            var signed = presigner.get().presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(10))
                    .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(expectedKey).build())
                    .build());
            return signed.isBrowserExecutable()
                    ? new RepresentativeImage(signed.url().toExternalForm(), "READY")
                    : RepresentativeImage.notReady();
        } catch (RuntimeException ex) {
            // Credential/service details can contain secrets. Image delivery must not fail a scan.
            return RepresentativeImage.notReady();
        }
    }
}
