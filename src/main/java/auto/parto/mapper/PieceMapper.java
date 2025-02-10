package auto.parto.mapper;

import auto.parto.dto.PieceDto;
import auto.parto.model.Piece;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

public class PieceMapper {
    public static PieceDto toDto(Piece piece) {
        return PieceDto.builder()
                    .condition(piece.getCondition())
                    .id(piece.getId())
                    .name(piece.getName())
                    .description(piece.getDescription())
                    .category(piece.getCategory())
                    .createAt(piece.getCreateAt())
                    .modifiedAt(piece.getModifiedAt())
                    .images(
                            piece.getImages()
                                    .stream()
                                    .map(FileMapper::toDto)
                                    .collect(Collectors.toList())
                    )
                .build();
    }

    public static Piece toEntity(PieceDto pieceDto) {
        return Piece.builder()
                    .id(UUID.randomUUID().toString())
                    .name(pieceDto.getName())
                    .description(pieceDto.getDescription())
                    .condition(pieceDto.getCondition())
                    .createAt(LocalDateTime.now())
                    .modifiedAt(null)
                    .category(pieceDto.getCategory())
                    .images(
                            pieceDto.getImages()
                                    .stream()
                                    .map(FileMapper::toEntity)
                                    .collect(Collectors.toList())
                    )
                .build();
    }
}
