package auto.parto.mapper;

import auto.parto.dto.FileDto;
import auto.parto.model.File;

public class FileMapper {

    public static File toEntity(FileDto fileDto) {
        return File.builder()
                .id(fileDto.getId())
                .createdAt(fileDto.getCreatedAt())
                .generatedFilename(fileDto.getGeneratedFilename())
                .originalFilename(fileDto.getOriginalFilename())
                .build();
    }

    public static FileDto toDto(File file) {
        return FileDto.builder()
                .id(file.getId())
                .createdAt(file.getCreatedAt())
                .generatedFilename(file.getGeneratedFilename())
                .originalFilename(file.getOriginalFilename())
                .build();
    }
}
