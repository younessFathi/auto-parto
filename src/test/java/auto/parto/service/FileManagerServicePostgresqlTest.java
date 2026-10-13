package auto.parto.service;

import auto.parto.persistence.FileRecord;
import auto.parto.persistence.FileRepository;
import auto.parto.service.iservice.IFileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FileManagerServicePostgresqlTest {

    @TempDir
    Path uploadRoot;

    @Test
    void requestUploadStoresContentAndPostgresqlMetadata() throws Exception {
        FileRepository repository = mock(FileRepository.class);
        FilePart image = imagePart("photo.jpg", "image/jpeg", "hello");
        UUID owner = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        UUID file = UUID.randomUUID();
        when(repository.createForRequest(any(), any(), anyString(), anyString(),
                anyString(), anyLong(), anyString()))
                .thenReturn(reactor.core.publisher.Mono.just(new FileRecord(file, owner, request, null,
                        "object", "photo.jpg", "image/jpeg", 5, "checksum", "PENDING", Instant.now(), Instant.now())));

        FileManagerService service = new FileManagerService(uploadRoot.toString(), mock(IFileService.class), repository);

        String result = service.saveForRequest(image, owner, request).block();

        assertThat(result).isEqualTo(file.toString());
        Path stored = Files.list(uploadRoot.resolve(request.toString())).findFirst().orElseThrow();
        assertThat(Files.readString(stored)).isEqualTo("hello");
        verify(repository).createForRequest(eq(owner), eq(request), contains(request.toString()), eq("photo.jpg"),
                eq("image/jpeg"), eq(5L), anyString());
    }

    @Test
    void requestUploadFailsWhenPostgresqlPersistenceIsUnavailable() {
        FileManagerService service = new FileManagerService(uploadRoot.toString(), mock(IFileService.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service
                        .saveForRequest(mock(FilePart.class), UUID.randomUUID(), UUID.randomUUID()).block())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requestUploadDeletesStagedFileWhenOwnershipInsertReturnsEmpty() throws Exception {
        FileRepository repository = mock(FileRepository.class);
        UUID owner = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        when(repository.createForRequest(any(), any(), anyString(), anyString(), anyString(), anyLong(), anyString()))
                .thenReturn(reactor.core.publisher.Mono.empty());
        FileManagerService service = new FileManagerService(uploadRoot.toString(), mock(IFileService.class), repository);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service
                        .saveForRequest(imagePart("photo.jpg", "image/jpeg", "hello"), owner, request).block())
                .isInstanceOf(IllegalArgumentException.class);

        try (var files = Files.walk(uploadRoot)) {
            assertThat(files.filter(Files::isRegularFile)).isEmpty();
        }
    }

    private static FilePart imagePart(String filename, String contentType, String content) {
        FilePart image = mock(FilePart.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        when(image.filename()).thenReturn(filename);
        when(image.headers()).thenReturn(headers);
        when(image.content()).thenReturn(Flux.just(new DefaultDataBufferFactory().wrap(content.getBytes())));
        return image;
    }
}
