package com.wallo.asset.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.asset.domain.Institution;
import com.wallo.asset.dto.InstitutionDto;
import com.wallo.asset.mapper.InstitutionMapper;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class InstitutionService {

    private final InstitutionMapper institutionMapper;
    private final ObjectMapper objectMapper;

    public InstitutionService(InstitutionMapper institutionMapper, ObjectMapper objectMapper) {
        this.institutionMapper = institutionMapper;
        this.objectMapper = objectMapper;
    }

    public List<Institution> getConnectionTargetInstitutions() {
        return institutionMapper.findActiveInstitutions();
    }

    public InstitutionDto.Response getInstitutions() {
        List<Institution> institutions = institutionMapper.findActiveInstitutions();
        return new InstitutionDto.Response(
                itemsOfType(institutions, "BANK"),
                itemsOfType(institutions, "CARD"),
                itemsOfType(institutions, "STOCK")
        );
    }

    private List<InstitutionDto.Item> itemsOfType(List<Institution> institutions, String type) {
        return institutions.stream()
                .filter(institution -> type.equals(institution.getInstitutionType()))
                .map(this::toItem)
                .collect(Collectors.toList());
    }

    private InstitutionDto.Item toItem(Institution institution) {
        return new InstitutionDto.Item(
                institution.getInstitutionId(),
                institution.getCodefOrganizationCode(),
                institution.getName(),
                institution.getLogoUrl(),
                parseServices(institution.getServices())
        );
    }

    private List<String> parseServices(String services) {
        if (services == null || services.isBlank()) {
            return Collections.emptyList();
        }

        try {
            return objectMapper.readValue(services, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("기관 서비스 정보를 읽을 수 없습니다.", exception);
        }
    }
}
