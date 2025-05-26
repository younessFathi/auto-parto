package auto.parto.mapper;

import auto.parto.dto.FileDto;
import auto.parto.dto.PieceDto;
import auto.parto.dto.PieceWithFilesDto;
import auto.parto.enums.Category;
import auto.parto.enums.Condition;
import auto.parto.enums.Status;
import auto.parto.model.Piece;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PieceDbMapperTest {
    private PieceDbMapper pieceDbMapper;
    private PieceDto pieceDto;
    private Piece piece;

    @BeforeEach
    public void setUp() {
        pieceDbMapper = new PieceDbMapperImpl();
        pieceDto = PieceDto
                .builder()
                    .id("test-id")
                    .name("Test Piece")
                    .description("This is a test piece.")
                    .category(Category.BICYCLE)
                    .condition(Condition.NEW)
                    .customerId("customer-id")
                    .createAt(null)
                    .seeNumber(0)
                    .status(Status.PENDING)
                .build();
        piece = Piece
                .builder()
                    .id("test-id")
                    .name("Test Piece")
                    .description("This is a test piece.")
                    .category(Category.BICYCLE)
                    .condition(Condition.NEW)
                    .customerId("customer-id")
                    .createAt(null)
                    .seeNumber(0)
                    .status(Status.PENDING)
                .build();
    }

    @Test
    public void testToDto() {
        PieceDto result = pieceDbMapper.toDto(piece);
        assertEquals(piece.getId(), result.getId());
        assertEquals(piece.getName(), result.getName());
        assertEquals(piece.getDescription(), result.getDescription());
        assertEquals(piece.getCategory(), result.getCategory());
        assertEquals(piece.getCondition(), result.getCondition());
        assertEquals(piece.getCustomerId(), result.getCustomerId());
        assertEquals(piece.getSeeNumber(), result.getSeeNumber());
        assertEquals(piece.getStatus(), result.getStatus());
    }

    @Test
    public void testToEntity() {
        Piece result = pieceDbMapper.toEntity(pieceDto);
        assertEquals(pieceDto.getId(), result.getId());
        assertEquals(pieceDto.getName(), result.getName());
        assertEquals(pieceDto.getDescription(), result.getDescription());
        assertEquals(pieceDto.getCategory(), result.getCategory());
        assertEquals(pieceDto.getCondition(), result.getCondition());
        assertEquals(pieceDto.getCustomerId(), result.getCustomerId());
        assertEquals(pieceDto.getSeeNumber(), result.getSeeNumber());
        assertEquals(pieceDto.getStatus(), result.getStatus());
    }

    @Test
    public void testToDtoWithFiles() {
        List<FileDto> images = List.of(FileDto.builder()
                .id("image1")
                .build(),
            FileDto.builder()
                .id("image2")
                .build());
        PieceWithFilesDto result = pieceDbMapper.toDto(piece, images);
        assertEquals(piece.getId(), result.getId());
        assertEquals(images, result.getImages());
    }
}
