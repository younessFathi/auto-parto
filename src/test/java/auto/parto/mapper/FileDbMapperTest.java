package auto.parto.mapper;

import auto.parto.dto.FileDto;
import auto.parto.model.File;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FileDbMapperTest {
    private FileDbMapper fileDbMapper;
    private FileDto fileDto;
    private File file;

    @BeforeEach
    public void setUp() {
        fileDbMapper = new FileDbMapperImpl();
        fileDto = FileDto
                .builder()
                    .id("test-id")
                    .createdAt(null)
                    .generatedFilename("generated-filename.txt")
                    .originalFilename("original-filename.txt")
                    .folderName("folder-name")
                .build();
        file = File
                .builder()
                    .id("test-id")
                    .createdAt(null)
                    .generatedFilename("generated-filename.txt")
                    .originalFilename("original-filename.txt")
                    .folderName("folder-name")
                .build();
    }

    @Test
    public void testToDto() {
        FileDto result = fileDbMapper.toDto(file);
        assertEquals(file.getId(), result.getId());
        assertEquals(file.getCreatedAt(), result.getCreatedAt());
        assertEquals(file.getGeneratedFilename(), result.getGeneratedFilename());
        assertEquals(file.getOriginalFilename(), result.getOriginalFilename());
        assertEquals(file.getFolderName(), result.getFolderName());
    }

    @Test
    public void testToEntity() {
        File result = fileDbMapper.toEntity(fileDto);
        assertEquals(fileDto.getId(), result.getId());
        assertEquals(fileDto.getCreatedAt(), result.getCreatedAt());
        assertEquals(fileDto.getGeneratedFilename(), result.getGeneratedFilename());
        assertEquals(fileDto.getOriginalFilename(), result.getOriginalFilename());
        assertEquals(fileDto.getFolderName(), result.getFolderName());
    }
}
