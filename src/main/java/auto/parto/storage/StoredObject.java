package auto.parto.storage;

public record StoredObject(String objectKey, long byteSize, String checksum) {
}
