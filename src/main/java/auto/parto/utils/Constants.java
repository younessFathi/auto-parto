package auto.parto.utils;

import java.util.List;

public class Constants {
    public static final String LOG_MESSAGE = "{}: {}";
    public static class OTPConstants {
        public static final String GENERATE_OTP_CODE_FOR_PHONE_NUMBER = "Generate OTP code for phone number {}";
        public static final String END_OF_GENERATE_OTP_CODE_FOR_PHONE_NUMBER = "End of generate OTP code for phone number {}";
        public static final String VALIDATE_OTP_CODE_FOR = "Validate OTP code for ";
        public static final String PHONE_NUMBER_IS_VALID = "Phone number {} is valid";
        public static final String PHONE_NUMBER_IS_NOT_VALID = "Phone number {} is not valid";
        public static final String WHATSAPP = "whatsapp:";
        public static final String WHATSAPP_YOUR_TWILIO_WHATSAPP_NUMBER = "whatsapp:+14155238886";
        public static final String YOUR_OTP_IS = "Your OTP is: ";
        public static final String SENDING_OTP_TO = "Sending OTP to ";
        public static final String END_OF_SEND_OTP_TO = "End of send otp to  ";
        public static final String FORMAT = "%04d";
        public static final String END_OF_OTP_VALIDATION_FOR = "End of otp validation for";
        public static final String GENERATED_OTP = "Generated otp";
        public static final String MESSAGE_SID = "Message SID";
        public static final String OTP_EMAIL_SUBJECT = "Your OTP Code";
        public static final String YOUR_OTP_CODE_IS = "Your OTP code is: ";
        public static final String FAILED_TO_SEND_OTP_EMAIL_TO = "Failed to send OTP email to";
        public static final String FAILED_TO_SEND_OTP_EMAIL = "Failed to send OTP email";
        public static final String ERROR_SENDING_EMAIL = "Error sending email";
        public static final String EMAIL_SENT_SUCCESSFULLY_TO = "OTP email sent successfully to";
    }
    public static class Exception {
        public static final String INVALID_OTP_FOR = "Invalid OTP for ";
        public static final String EXPIRED_OTP_FOR = "Expired OTP for ";
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
        public static final String INVALID_GCP_TOKEN = "Invalid GCP token";
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
        public static final String _404 = "404";
        public static final String RESPONSE_404_PIECE_NOT_FOUND = "Piece not found";
        public static final String RESPONSE_200_PIECE_FOUND = "Piece found";
        public static final String GET_PIECE_SUMMARY = "Get Piece by id";
        public static final String GET_PIECE_DESCRIPTION = "Getting piece using id";
    }

    public static class Email {
        public static final String EMAIL_SENT_SUCCESSFULLY = "Email sent successfully";
        public static final String FAILED_TO_SEND_EMAIL = "Failed to send email";
    }

    public static class Notification{
        public static final String NEW_SUBSCRIBER_CONNECTED = "New subscriber connected";
        public static final String SUBSCRIBER_DISCONNECTED = "Subscriber disconnected";
        public static final String SSE_CONNECTION_ERROR = "SSE connection error";
        public static final String SSE_ERROR_RETURNING_EMPTY_FLUX = "SSE error, returning empty flux";
        public static final String USER_SUBSCRIBED_TO_SSE = "User {} subscribed to SSE";
        public static final String USER_SSE_CONNECTION_CLOSED = "User {} SSE connection closed";
        public static final String SSE_ERROR_FOR_USER = "SSE error for user {}: {}";
        public static final String SENDING_NOTIFICATION_TO_USER_TYPE = "Sending notification to user: {}, type: {}";
        public static final String USER_HAS_ACTIVE_CONNECTION_ATTEMPTING_DIRECT_DELIVERY = "User {} has active connection, attempting direct delivery";
        public static final String NOTIFICATION_SUCCESSFULLY_DELIVERED_TO_VIA_SSE = "Notification successfully delivered to {} via SSE";
        public static final String DIRECT_DELIVERY_FAILED_FOR_FALLING_BACK_TO_STORAGE = "Direct delivery failed for {}, falling back to storage";
        public static final String USER_IS_OFFLINE_STORING_NOTIFICATION = "User {} is offline, storing notification";
        public static final String NOTIFICATION_STORED_FOR_USER = "Notification stored for user: {}";
        public static final String FAILED_TO_STORE_NOTIFICATION_FOR_USER = "Failed to store notification for user: {}";
        public static final String ERROR_SENDING_NOTIFICATION_TO = "Error sending notification to {}: {}";
        public static final String INVALID_NOTIFICATION_REQUEST = "Invalid notification request";
        public static final String STORAGE_FAILED = "Storage failed";
        public static final String STATUS = "status";
        public static final String USER_ID = "userId";
        public static final String METHOD = "method";
        public static final String TIMESTAMP = "timestamp";
        public static final String MESSAGE = "message";
        public static final String NOTIFICATION_DELIVERED_SUCCESSFULLY = "Notification delivered successfully";
        public static final String STORED = "STORED";
        public static final String SSE = "SSE";
        public static final String USER_OFFLINE_NOTIFICATION_STORED_FOR_LATER_DELIVERY = "User offline. Notification stored for later delivery.";
        public static final String ERROR = "error";
        public static final String FAILED_TO_PROCESS_NOTIFICATION = "Failed to process notification";
        public static final String HEARTBEAT = "heartbeat";
        public static final String SENT_HEARTBEAT_AT = "Sent heartbeat at {} ";
    }
}
