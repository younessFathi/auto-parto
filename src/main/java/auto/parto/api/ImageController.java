package auto.parto.api;

import auto.parto.enums.FileRequestFlag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/images")
@Slf4j
public class ImageController {
    @Value("${auto.parto.images.uploads}")
    private String uploadsDirectory;

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
