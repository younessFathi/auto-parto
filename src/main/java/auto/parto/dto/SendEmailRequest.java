package auto.parto.dto;

import lombok.Data;

@Data
public class SendEmailRequest {
    private String from;
    private String to;
    private String subject;
    private String html;
}
