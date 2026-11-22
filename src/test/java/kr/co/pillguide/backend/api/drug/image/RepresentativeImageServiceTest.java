package kr.co.pillguide.backend.api.drug.image;

import java.time.LocalDate;
import java.util.Optional;
import kr.co.pillguide.backend.api.drug.entity.Drug;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RepresentativeImageServiceTest {
    private final String hash = "a".repeat(64);

    private Drug drug(String prefix) {
        Drug drug = new Drug("Reference", "197400246", null);
        drug.updateRepresentativeImage(prefix + "reference/front/197400246/" + hash + ".png", hash);
        return drug;
    }

    @Test
    void signsExactObjectForTenMinutesWithoutNetworkOrPersistingUrl() {
        try (var signer = S3Presigner.builder().region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test-key", "test-secret"))).build()) {
            Drug drug = drug("dev/");
            var image = new RepresentativeImageService(Optional.of(signer), "test-images", "dev/").resolve(drug);
            assertEquals("READY", image.status());
            assertTrue(image.url().startsWith("https://test-images.s3.ap-northeast-2.amazonaws.com/dev/reference/front/197400246/" + hash));
            assertTrue(image.url().contains("X-Amz-Expires=600"));
            assertNull(drug.getImageUrl());
            assertEquals("dev/reference/front/197400246/" + hash + ".png", drug.getRepresentativeImageKey());
        }
    }

    @Test
    void disabledServiceDoesNotNeedCredentials() {
        assertEquals(RepresentativeImage.notReady(),
                new RepresentativeImageService(Optional.empty(), "", "").resolve(drug("dev/")));
    }

    @Test
    void unpublishedAndOtherEnvironmentOrProductKeysCannotBeSigned() {
        var signer = mock(S3Presigner.class);
        var service = new RepresentativeImageService(Optional.of(signer), "test-images", "dev/");
        assertEquals(RepresentativeImage.notReady(), service.resolve(new Drug("No image", "197400246", null)));
        assertEquals(RepresentativeImage.notReady(), service.resolve(drug("production/")));
        Drug wrongProduct = drug("dev/");
        wrongProduct.updateRepresentativeImage("dev/reference/front/197500541/" + hash + ".png", hash);
        assertEquals(RepresentativeImage.notReady(), service.resolve(wrongProduct));
        verifyNoInteractions(signer);
    }

    @Test
    void credentialFailureDoesNotBreakDrugResponse() {
        var signer = mock(S3Presigner.class);
        when(signer.presignGetObject(any(GetObjectPresignRequest.class))).thenThrow(new IllegalStateException("unavailable"));
        assertEquals(RepresentativeImage.notReady(),
                new RepresentativeImageService(Optional.of(signer), "test-images", "dev/").resolve(drug("dev/")));
    }

    @Test
    void referenceRefreshPreservesImageMapping() {
        Drug drug = drug("dev/");
        String key = drug.getRepresentativeImageKey();
        drug.updateReferenceData("Updated", "Model", "K-000250", null, null, null,
                "NORMAL", "COMPLETE", LocalDate.of(2026, 11, 14));
        assertEquals(key, drug.getRepresentativeImageKey());
        assertEquals(hash, drug.getRepresentativeImageHash());
    }
}
