package auto.parto.utils;

public class Constants {
    public static final String LOG_MESSAGE = "{}: {}";
    public static class OTPConstants {
        public static final String GENERATE_OTP_CODE_FOR_PHONE_NUMBER = "Generate OTP code for phone number {}";
        public static final String END_OF_GENERATE_OTP_CODE_FOR_PHONE_NUMBER = "End of generate OTP code for phone number {}";
        public static final String VALIDATE_OTP_CODE_FOR_PHONE_NUMBER = "Validate OTP code for phone number";
        public static final String PHONE_NUMBER_IS_VALID = "Phone number {} is valid";
        public static final String PHONE_NUMBER_IS_NOT_VALID = "Phone number {} is not valid";
        public static final String WHATSAPP = "whatsapp:";
        public static final String WHATSAPP_YOUR_TWILIO_WHATSAPP_NUMBER = "whatsapp:+14155238886";
        public static final String YOUR_OTP_IS = "Your OTP is: ";
        public static final String SENDING_OTP_TO = "Sending OTP to {} ";
        public static final String END_OF_SEND_OTP_TO = "End of send otp to {} ";
        public static final String FORMAT = "%04d";
        public static final String END_OF_OTP_VALIDATION_FOR = "End of otp validation for";
        public static final String GENERATED_OTP = "Generated otp";
        public static final String MESSAGE_SID = "Message SID";
    }
    public static class Exception {
        public static final String INVALID_OTP_FOR_PHONE_NUMBER = "Invalid OTP for phone number";
        public static final String EXPIRED_OTP_FOR_PHONE_NUMBER = "Expired OTP for phone number";
        public static final String INVALID_OTP = "Invalid OTP.";
        public static final String EXPIRED_OTP = "Expired OTP.";
        public static final String TOO_MANY_OTP_REQUESTS_FOR_PHONE_NUMBER = "Too many OTP requests for phone number {} ";
        public static final String MANY_OTP_REQUESTS_FOR_PHONE_NUMBER = "Too many OTP requests for phone number";
        public static final String TIMESTAMP = "timestamp";
        public static final String STATUS = "status";
        public static final String ERROR = "error";
        public static final String MESSAGE = "message";
        public static final String AN_UNEXPECTED_ERROR_OCCURRED = "An unexpected error occurred.";
        public static final String MESSAGE_SENT = "Message sent";
        public static final String INVALID_REQUEST = "Invalid request: {} ";
        public static final String AUTHENTICATION_FAILED = "Authentication failed: {} ";
        public static final String API_ERROR = "API error: {} ";
        public static final String UNEXPECTED_ERROR = "Unexpected error: {} ";
        public static final String INVALID_PHONE_NUMBER = "Invalid phone number";
    }
}
