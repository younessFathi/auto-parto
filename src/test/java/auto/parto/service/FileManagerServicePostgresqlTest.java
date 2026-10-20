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
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
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
        when(repository.activate(eq(file), eq(owner))).thenReturn(reactor.core.publisher.Mono.just(
                new FileRecord(file, owner, request, null, "object", "photo.jpg", "image/jpeg", 5,
                        "checksum", "ACTIVE", Instant.now(), Instant.now())));

        FileManagerService service = new FileManagerService(uploadRoot.toString(), mock(IFileService.class), repository);

        String result = service.saveForRequest(image, owner, request).block();

        assertThat(result).isEqualTo(file.toString());
        Path stored = Files.list(uploadRoot.resolve(request.toString())).findFirst().orElseThrow();
        assertThat(Files.readAllBytes(stored)).startsWith((byte) 0xff, (byte) 0xd8, (byte) 0xff);
        verify(repository).createForRequest(eq(owner), eq(request), contains(request.toString()), eq("photo.jpg"),
                eq("image/jpeg"), anyLong(), anyString());
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

    @Test
    void listingUploadUsesListingTargetPersistence() {
        FileRepository repository = mock(FileRepository.class);
        UUID owner = UUID.randomUUID();
        UUID listing = UUID.randomUUID();
        UUID file = UUID.randomUUID();
        when(repository.createForListing(any(), any(), anyString(), anyString(), anyString(), anyLong(), anyString()))
                .thenReturn(reactor.core.publisher.Mono.just(new FileRecord(file, owner, null, listing,
                        "object", "photo.jpg", "image/jpeg", 5, "checksum", "PENDING", Instant.now(), Instant.now())));
        when(repository.activate(eq(file), eq(owner))).thenReturn(reactor.core.publisher.Mono.just(
                new FileRecord(file, owner, null, listing, "object", "photo.jpg", "image/jpeg", 5,
                        "checksum", "ACTIVE", Instant.now(), Instant.now())));
        FileManagerService service = new FileManagerService(uploadRoot.toString(), mock(IFileService.class), repository);

        assertThat(service.saveForListing(imagePart("photo.jpg", "image/jpeg", "hello"), owner, listing).block())
                .isEqualTo(file.toString());
        verify(repository).createForListing(eq(owner), eq(listing), contains(listing.toString()), eq("photo.jpg"),
                eq("image/jpeg"), anyLong(), anyString());
    }

    @Test
    void rejectsContentThatDoesNotMatchAnAllowedImageSignature() {
        FileManagerService service = new FileManagerService(uploadRoot.toString(), mock(IFileService.class),
                mock(FileRepository.class), new auto.parto.storage.LocalFileStorage(uploadRoot.toString(), "test"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service
                .saveForRequest(imagePart("photo.jpg", "image/jpeg", "plain text"), UUID.randomUUID(), UUID.randomUUID())
                .block()).isInstanceOf(auto.parto.exception.FileUploadException.class);
    }

    @Test
    void rejectsMalformedContentWithAnImageSignature() {
        FileManagerService service = new FileManagerService(uploadRoot.toString(), mock(IFileService.class),
                mock(FileRepository.class), new auto.parto.storage.LocalFileStorage(uploadRoot.toString(), "test"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service
                .saveForRequest(imagePart("photo.jpg", "image/jpeg", "malformed-signature"),
                        UUID.randomUUID(), UUID.randomUUID()).block())
                .isInstanceOf(auto.parto.exception.FileUploadException.class);
    }

    @Test
    void rejectsMismatchedDeclaredContentType() {
        FileManagerService service = new FileManagerService(uploadRoot.toString(), mock(IFileService.class),
                mock(FileRepository.class), new auto.parto.storage.LocalFileStorage(uploadRoot.toString(), "test"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service
                .saveForRequest(imagePart("photo.jpg", "image/png", "hello"), UUID.randomUUID(), UUID.randomUUID())
                .block()).isInstanceOf(auto.parto.exception.FileUploadException.class);
    }

    @Test
    void rejectsImagesLargerThanConfiguredLimit() {
        FileManagerService service = new FileManagerService(uploadRoot.toString(), mock(IFileService.class),
                mock(FileRepository.class), new auto.parto.storage.LocalFileStorage(uploadRoot.toString(), "test"), 3);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service
                .saveForRequest(imagePart("photo.jpg", "image/jpeg", "hello"), UUID.randomUUID(), UUID.randomUUID())
                .block()).isInstanceOf(auto.parto.exception.FileUploadException.class);
    }

    private static FilePart imagePart(String filename, String contentType, String content) {
        FilePart image = mock(FilePart.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        when(image.filename()).thenReturn(filename);
        when(image.headers()).thenReturn(headers);
        byte[] bytes;
        if (content.equals("hello")) {
            try {
                BufferedImage validImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ImageIO.write(validImage, "jpg", output);
                bytes = output.toByteArray();
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        } else if (content.equals("malformed-signature")) {
            bytes = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 'b', 'a', 'd'};
        } else {
            bytes = content.getBytes();
        }
        when(image.content()).thenReturn(Flux.just(new DefaultDataBufferFactory().wrap(bytes)));
        return image;
    }
}
