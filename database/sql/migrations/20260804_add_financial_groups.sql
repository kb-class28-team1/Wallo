ALTER TABLE INSTITUTIONS
    ADD COLUMN financial_group_code VARCHAR(30) NULL AFTER name,
    ADD COLUMN financial_group_name VARCHAR(100) NULL AFTER financial_group_code;

UPDATE INSTITUTIONS
SET financial_group_code = CASE
        WHEN codef_organization_code IN ('0004', '0301') THEN 'KB'
        WHEN codef_organization_code IN ('0081', '0311') THEN 'HANA'
        WHEN codef_organization_code = '0088' THEN 'SHINHAN'
        WHEN codef_organization_code = '0264' THEN 'KIWOOM'
        ELSE CONCAT(type, '_', codef_organization_code)
    END,
    financial_group_name = CASE
        WHEN codef_organization_code IN ('0004', '0301') THEN '국민금융'
        WHEN codef_organization_code IN ('0081', '0311') THEN '하나금융'
        WHEN codef_organization_code = '0088' THEN '신한금융'
        WHEN codef_organization_code = '0264' THEN '키움증권'
        ELSE name
    END;

ALTER TABLE INSTITUTIONS
    MODIFY COLUMN financial_group_code VARCHAR(30) NOT NULL,
    MODIFY COLUMN financial_group_name VARCHAR(100) NOT NULL;
