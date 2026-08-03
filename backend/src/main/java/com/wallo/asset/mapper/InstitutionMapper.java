package com.wallo.asset.mapper;

import com.wallo.asset.domain.Institution;
import java.util.List;

public interface InstitutionMapper {

    List<Institution> findActiveInstitutions();
}
