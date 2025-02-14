package auto.parto.mapper;

import auto.parto.dto.FileDto;
import auto.parto.dto.PieceDto;
import auto.parto.dto.PieceWithFilesDto;
import auto.parto.model.Piece;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
                    .imagesId(piece.getImagesId())
                    .sellerType(piece.getSellerType())
                    .sellerRank(piece.getSellerRank())
                    .seeNumber(piece.getSeeNumber())
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
                    .imagesId(pieceDto.getImagesId())
                    .sellerType(pieceDto.getSellerType())
                    .sellerRank(pieceDto.getSellerRank())
                    .seeNumber(pieceDto.getSeeNumber())
                .build();
    }

    public static PieceWithFilesDto toDto(Piece piece , List<FileDto> images){
        return PieceWithFilesDto.builder()
                .condition(piece.getCondition())
                .customerId(piece.getCustomerId())
                .id(piece.getId())
                .name(piece.getName())
                .description(piece.getDescription())
                .category(piece.getCategory())
                .createAt(piece.getCreateAt())
                .seeNumber(piece.getSeeNumber())
                .images(images)
                .build();
    }
}
