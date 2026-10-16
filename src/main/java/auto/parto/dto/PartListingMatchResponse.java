package auto.parto.dto;

import java.util.List;

public record PartListingMatchResponse(List<PartListingResponse> items, int limit) {
}
