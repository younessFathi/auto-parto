package auto.parto.dto;

import java.util.List;

public record PartListingPageResponse(List<PartListingResponse> items, long total, int page, int size) {
}
