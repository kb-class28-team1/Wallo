package com.wallo.external.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.external.dto.CodefDto;
import java.io.IOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class CodefMockResponseLoader {

    private final ObjectMapper objectMapper;

    public CodefMockResponseLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CodefDto.Response load(String fileName) {
        try {
            ClassPathResource resource = new ClassPathResource("mock/codef/" + fileName);
            return objectMapper.readValue(resource.getInputStream(), CodefDto.Response.class);
        } catch (IOException exception) {
            return CodefDto.Response.failure("CF-40400", "Mock 응답 파일을 찾을 수 없습니다.", exception.getMessage());
        }
    }
}
