package auto.parto.mapper;

import auto.parto.dto.FileDto;
import auto.parto.dto.PieceDto;
import auto.parto.dto.PieceWithFilesDto;
import auto.parto.model.Piece;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PieceDbMapper {
    PieceDto toDto(Piece piece);
    Piece toEntity(PieceDto pieceDto);

    default PieceWithFilesDto toDto(Piece piece , List<FileDto> images){
        return PieceWithFilesDto
                .builder()
                    .condition(piece.getCondition())
                    .customerId(piece.getCustomerId())
                    .id(piece.getId())
                    .name(piece.getName())
                    .description(piece.getDescription())
                    .category(piece.getCategory())
                    .createAt(piece.getCreateAt())
                    .seeNumber(piece.getSeeNumber())
                    .status(piece.getStatus())
                    .images(images)
                .build();
    }
}
