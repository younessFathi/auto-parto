package auto.parto.service;

import auto.parto.dto.FileDto;
import auto.parto.exception.FileUploadException;
import auto.parto.service.iservice.IFileService;
import auto.parto.utils.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileManagerServiceTest {

    @Mock
    private IFileService fileService;

    @InjectMocks
    private FileManagerService fileManagerService;

    @Value("${auto.parto.images.uploads}")
    private final String uploadsDirectory = "/test/uploads";

    @Test
    void testSave_ValidImage_Success() throws Exception {
        // Mock inputs
        FilePart mockFilePart = Mockito.mock(FilePart.class);
        String folderName = "testFolder";
        String filename = "image.jpg";

        // Mock behaviors
        when(mockFilePart.filename()).thenReturn(filename);
        when(mockFilePart.content()).thenReturn(Flux.just(createTestDataBuffer("test content")));
        when(fileService.save(any())).thenReturn(Mono.just(FileDto.builder().id(UUID.randomUUID().toString()).originalFilename("image.jpg").generatedFilename(UUID.randomUUID().toString()).build()));
        // Call the method under test
        Mono<String> result = fileManagerService.save(mockFilePart , UUID.randomUUID().toString());

        // Verify
        StepVerifier.create(result)
                .assertNext(fileDto -> {
                    assertNotNull(result);
                })
                .verifyComplete();
    }

    @Test
    void testSave_InvalidExtension_ThrowsException() {
        // Mock inputs
        FilePart mockFilePart = Mockito.mock(FilePart.class);
        String folderName = "testFolder";
        String filename = "invalid.exe";

        // Mock behaviors
        when(mockFilePart.filename()).thenReturn(filename);
        // Call the method under test
        Mono<String> result = fileManagerService.save(mockFilePart , UUID.randomUUID().toString());

        // Verify
        StepVerifier.create(result)
                .expectErrorMatches(throwable -> throwable instanceof FileUploadException &&
                        throwable.getMessage().equals(Constants.FileUpload.INVALID_FILE_TYPE_ONLY_IMAGE_FILES_ARE_ALLOWED))
                .verify();
    }

    private DataBuffer createTestDataBuffer(String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        DataBufferFactory factory = new DefaultDataBufferFactory();
        return factory.wrap(bytes);
    }
}

