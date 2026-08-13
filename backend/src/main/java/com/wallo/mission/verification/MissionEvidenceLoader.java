package com.wallo.mission.verification;

public interface MissionEvidenceLoader {
    Evidence load(String mediaUrl, String mediaType);

    record Evidence(byte[] content, String contentType) {}
}
