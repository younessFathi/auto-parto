package auto.parto.utils;

import java.util.List;

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

    public static class FileUpload {
        public static final String INVALID_FILE_TYPE_ONLY_IMAGE_FILES_ARE_ALLOWED = "Invalid file type. Only image files are allowed.";
        public static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "gif", "bmp");
        public static final String FILE_SAVED_TO_DISK = "File saved to disk";
        public static final String FILE_SAVED = "File saved";
        public static final String COULD_NOT_INITIALIZE_UPLOAD_FOLDER = "Could not initialize upload folder";
        public static final String PIECE_AND_IMAGES_UPLOADED_SUCCESSFULLY = "Piece and images uploaded successfully!";
    }

    public static class Swagger {
        public static final String TAG_AUTH = "Authentication";
        public static final String TAG_AUTH_DESCRIPTION = "API for OTP-based authentication";
        public static final String SEND_OTP_SUMMARY = "Send OTP";
        public static final String SEND_OTP_DESCRIPTION = "Sends an OTP to the provided phone number.";
        public static final String VALIDATE_OTP_SUMMARY = "Validate OTP";
        public static final String VALIDATE_OTP_DESCRIPTION = "Validates the OTP and generates a JWT token if successful.";
        public static final String RESPONSE_200_OTP_SENT = "OTP sent successfully";
        public static final String RESPONSE_200_OTP_VALID = "OTP validated successfully, JWT token returned";
        public static final String RESPONSE_400_BAD_REQUEST = "Invalid request data";
        public static final String RESPONSE_401_UNAUTHORIZED = "OTP validation failed";
        public static final String RESPONSE_500_INTERNAL_ERROR = "Internal server error";
        public static final String INVALID_OTP_OR_PHONE_NUMBER = "Invalid OTP or phone number";
        public static final String TAG_SEARCH = "Search";
        public static final String TAG_SEARCH_DESCRIPTION = "API for searching pieces and notifying sellers";
        public static final String SEARCH_PIECE_SUMMARY = "Search for a piece";
        public static final String SEARCH_PIECE_DESCRIPTION = "Search for a piece and notify sellers";
        public static final String SEARCH_PIECE_IMAGES_SUMMARY = "Search for a piece with images";
        public static final String SEARCH_PIECE_IMAGES_DESCRIPTION = "Search for a piece and notify sellers, including image uploads";
        public static final String RESPONSE_200_SEARCH_SUCCESS = "Search request processed successfully";

        public static final String PART_IMAGES = "images";
        public static final String PART_PIECE = "piece";
        public static final String SUCCESS_UPLOAD_MESSAGE = "Piece and images uploaded successfully!";
        public static final String _500 = "500";
        public static final String _200 = "200";
        public static final String _400 = "400";
        public static final String _401 = "401";


    }
}
