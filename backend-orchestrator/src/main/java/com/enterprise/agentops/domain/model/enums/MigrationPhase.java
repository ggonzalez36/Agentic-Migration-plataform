package com.enterprise.agentops.domain.model.enums;

public enum MigrationPhase {
    DISCOVERY_AND_AST_ANALYSIS,
    BUSINESS_RULE_EXTRACTION,
    SCHEMA_AND_MODEL_MAPPING,
    CODE_GENERATION,
    QA_UNIT_TESTING,
    HUMAN_AUDIT_APPROVAL,
    DEPLOYMENT_PREPARATION
}
