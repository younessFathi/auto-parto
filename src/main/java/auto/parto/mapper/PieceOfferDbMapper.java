package auto.parto.mapper;

import auto.parto.dto.PieceOfferDto;
import auto.parto.model.PieceOffer;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PieceOfferDbMapper {
    PieceOfferDto toDto(PieceOffer pieceOffer);
    PieceOffer toEntity(PieceOfferDto pieceOfferDto);
}
