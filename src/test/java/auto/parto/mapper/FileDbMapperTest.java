package auto.parto.mapper;

import auto.parto.dto.FileDto;
import auto.parto.model.File;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class FileDbMapperTest {

    private final FileDbMapper mapper = Mappers.getMapper(FileDbMapper.class);

    @Test
    void toEntityMapsPieceId() {
        FileDto dto = FileDto.builder()
                .id("file-1")
                .originalFilename("test.jpg")
                .generatedFilename("uuid.jpg")
                .folderName("piece-1")
                .pieceId("piece-1")
                .build();

        File entity = mapper.toEntity(dto);

        assertThat(entity.getPieceId()).isEqualTo("piece-1");
        assertThat(entity.getId()).isEqualTo("file-1");
        assertThat(entity.getFolderName()).isEqualTo("piece-1");
    }

    @Test
    void toDtoMapsPieceId() {
        File entity = File.builder()
                .id("file-1")
                .originalFilename("test.jpg")
                .generatedFilename("uuid.jpg")
                .folderName("piece-1")
                .pieceId("piece-1")
                .build();

        FileDto dto = mapper.toDto(entity);

        assertThat(dto.getPieceId()).isEqualTo("piece-1");
        assertThat(dto.getId()).isEqualTo("file-1");
        assertThat(dto.getFolderName()).isEqualTo("piece-1");
    }
}
