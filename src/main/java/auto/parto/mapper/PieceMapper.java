package auto.parto.mapper;

import auto.parto.dto.PieceDto;
import auto.parto.model.Piece;

import java.time.LocalDateTime;
import java.util.UUID;

public class PieceMapper {
    public static PieceDto toDto(Piece piece) {
        return PieceDto.builder()
                .condition(piece.getCondition())
                .id(piece.getId())
                .name(piece.getName())
                .description(piece.getDescription())
                .build();
    }

    public static Piece toEntity(PieceDto pieceDto) {
        return Piece.builder()
                .id(UUID.randomUUID())
                .name(pieceDto.getName())
                .description(pieceDto.getDescription())
                .createAt(LocalDateTime.now())
                .modifiedAt(null)
                .build();
    }
}
