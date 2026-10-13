package auto.parto.api;

import auto.parto.enums.FileRequestFlag;
import auto.parto.security.CanonicalPrincipal;
import auto.parto.security.CanonicalSecurity;
import auto.parto.service.iservice.IFileManagerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@RestController
@RequestMapping("/images")
@Slf4j
public class ImageController {
    @Value("${auto.parto.images.uploads}")
    private String uploadsDirectory;
    private final IFileManagerService fileManagerService;

    public ImageController() {
        this.fileManagerService = null;
    }

    public ImageController(IFileManagerService fileManagerService) {
        this.fileManagerService = fileManagerService;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ImageController(ObjectProvider<IFileManagerService> provider) {
        this(provider.getIfAvailable());
    }

    @PostMapping(value = "/requests/{requestId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<String> uploadRequestImage(@PathVariable UUID requestId,
                                            @RequestPart("image") FilePart image,
                                            Authentication authentication) {
        CanonicalPrincipal principal = CanonicalSecurity.requireRole(authentication, "ROLE_BUYER");
        UUID ownerId = UUID.fromString(principal.getId());
        if (fileManagerService == null) {
            return Mono.error(new IllegalStateException("File upload service is unavailable"));
        }
        return fileManagerService.saveForRequest(image, ownerId, requestId);
    }

    @PostMapping(value = "/listings/{listingId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<String> uploadListingImage(@PathVariable UUID listingId,
                                            @RequestPart("image") FilePart image,
                                            Authentication authentication) {
        CanonicalPrincipal principal = CanonicalSecurity.requireRole(authentication, "ROLE_SELLER");
        UUID ownerId = UUID.fromString(principal.getId());
        if (fileManagerService == null) {
            return Mono.error(new IllegalStateException("File upload service is unavailable"));
        }
        return fileManagerService.saveForListing(image, ownerId, listingId);
    }

    @GetMapping("/{folderName}")
    public Flux<Resource> listImages(@PathVariable String folderName , @RequestParam(required = false) FileRequestFlag flag) {
        // Validate the folder name
        log.info("Validating folder name {} " , folderName);
        if (!isValidFolderName(folderName)) {
            log.error("Invalid folder name {} " , folderName);
            return Flux.error(new IllegalArgumentException("Invalid folder name."));
        }

        Path folderPath = Paths.get(uploadsDirectory, folderName);
        File folder = folderPath.toFile();

        // Check if the folder exists and is indeed a directory
        if (!folder.exists() || !folder.isDirectory()) {
            log.error("Folder not fount or is not a directory {} " , folder);
            return Flux.error(new RuntimeException("Folder not found or is not a directory."));
        }

        // Filter and list image files
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".jpg") ||
                name.toLowerCase().endsWith(".png") ||
                name.toLowerCase().endsWith(".gif") ||
                name.toLowerCase().endsWith(".jpeg"));
        // Create a Flux from the image files
        return Flux.create(sink -> {
            if (files != null && files.length != 0) {
                if(FileRequestFlag.ONE.equals(flag)){
                    Path imagePath = files[0].toPath();
                    Resource resource = new PathResource(imagePath);
                    sink.next(resource);
                }else {
                    for (File file : files) {
                        Path imagePath = file.toPath();
                        Resource resource = new PathResource(imagePath);
                        sink.next(resource);
                    }
                }
            }
            sink.complete();
        });
    }

    private boolean isValidFolderName(String folderName) {
        // Add validation logic for folder names (e.g., regex to allow only certain characters)
        return folderName.matches("^[a-zA-Z0-9._-]+$");
    }
}
