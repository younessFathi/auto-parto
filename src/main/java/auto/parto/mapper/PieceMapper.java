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
                    .customerId(piece.getCustomerId())
                    .id(piece.getId())
                    .name(piece.getName())
                    .description(piece.getDescription())
                    .category(piece.getCategory())
                    .createAt(piece.getCreateAt())
                    .modifiedAt(piece.getModifiedAt())
                    .images(
                            piece.getImages() != null ?
                            piece.getImages()
                                    .stream()
                                    .map(FileMapper::toDto)
                                    .collect(Collectors.toList()):
                                    null
                    )
                .build();
    }

    public static Piece toEntity(PieceDto pieceDto) {
        return Piece.builder()
                    .id(UUID.randomUUID().toString())
                    .name(pieceDto.getName())
                    .customerId(pieceDto.getCustomerId())
                    .description(pieceDto.getDescription())
                    .condition(pieceDto.getCondition())
                    .createAt(LocalDateTime.now())
                    .modifiedAt(null)
                    .category(pieceDto.getCategory())
                    .images(
                            pieceDto.getImages() != null ?
                            pieceDto.getImages()
                                    .stream()
                                    .map(FileMapper::toEntity)
                                    .collect(Collectors.toList()):
                                    null
                    )
                .build();
    }
}
