package auto.parto.mapper;

import auto.parto.dto.PieceOfferDto;
import auto.parto.model.PieceOffer;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
public class PieceOfferMapper {

    public static PieceOfferDto toDto(PieceOffer pieceOffer){
        log.info(String.valueOf(pieceOffer));
        return PieceOfferDto.builder()
                .id(pieceOffer.getId())
                .pieceId(pieceOffer.getPieceId())
                .customerId(pieceOffer.getCustomerId())
                .name(pieceOffer.getName())
                .description(pieceOffer.getDescription())
                .price(pieceOffer.getPrice())
                .currency(pieceOffer.getCurrency())
                .createAt(pieceOffer.getCreateAt())
                .modifiedAt(pieceOffer.getModifiedAt())
                .isNegotiable(pieceOffer.getIsNegotiable())
                .isFreeDelivery(pieceOffer.getIsFreeDelivery())
                .condition(pieceOffer.getCondition())
                .deliveryMode(pieceOffer.getDeliveryMode())
                .status(pieceOffer.getStatus())
                .seeNumber(pieceOffer.getSeeNumber())
                .deliveryPrice(pieceOffer.getDeliveryPrice())
                .imagesId(pieceOffer.getImagesId())
                .isAvailable(pieceOffer.getIsAvailable())
                .duration(pieceOffer.getDuration())
                .warranty(pieceOffer.getWarranty())
                .build();
    }

    public static PieceOffer toEntity(PieceOfferDto pieceOfferDto) {
        log.info(String.valueOf(pieceOfferDto));
        return PieceOffer.builder()
                .id(UUID.randomUUID().toString())
                .pieceId(pieceOfferDto.getPieceId())
                .customerId(pieceOfferDto.getCustomerId())
                .name(pieceOfferDto.getName())
                .description(pieceOfferDto.getDescription())
                .price(pieceOfferDto.getPrice())
                .currency(pieceOfferDto.getCurrency())
                .createAt(LocalDateTime.now())
                .modifiedAt(null)
                .isNegotiable(pieceOfferDto.getIsNegotiable())
                .condition(pieceOfferDto.getCondition())
                .isFreeDelivery(pieceOfferDto.getIsFreeDelivery())
                .status(pieceOfferDto.getStatus())
                .seeNumber(pieceOfferDto.getSeeNumber())
                .deliveryMode(pieceOfferDto.getDeliveryMode())
                .deliveryPrice(pieceOfferDto.getDeliveryPrice())
                .imagesId(pieceOfferDto.getImagesId())
                .isAvailable(pieceOfferDto.getIsAvailable())
                .duration(pieceOfferDto.getDuration())
                .warranty(pieceOfferDto.getWarranty())
                .build();
    }

}
