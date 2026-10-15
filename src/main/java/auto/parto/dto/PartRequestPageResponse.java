package auto.parto.dto;

import java.util.List;

public record PartRequestPageResponse(
        List<PartRequestResponse> items,
        String nextCursor) {
}
