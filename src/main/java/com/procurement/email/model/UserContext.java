package com.procurement.email.model;

import lombok.Data;
import java.util.Map;

/**
 * POJO representing user context information retrieved from Keycloak.
 */
@Data
public class UserContext {
    private String email;
    private String role;
    private String designation;
    private Map<String, String> preferences;
}
