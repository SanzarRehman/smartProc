package com.procurement.email.service;

import com.procurement.email.model.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for integrating with Keycloak to retrieve user context information.
 * Provides caching for user context with a 5-minute TTL to improve performance.
 */
@Service
@Slf4j
public class KeycloakIntegrationService {

    private final Keycloak keycloakAdminClient;
    private final String realm;

    public KeycloakIntegrationService(
            Keycloak keycloakAdminClient,
            @Value("${keycloak.realm}") String realm) {
        this.keycloakAdminClient = keycloakAdminClient;
        this.realm = realm;
    }

    /**
     * Retrieves user context information from Keycloak by email address.
     * Results are cached for 5 minutes to reduce API calls and improve performance.
     *
     * @param email the email address of the user to query
     * @return Optional containing UserContext if user is found, empty otherwise
     */
    @Cacheable(value = "userContext", key = "#email")
    public Optional<UserContext> getUserByEmail(String email) {

        if(  email.equals("Sanzar Rahman 1621555030 <sanzar.rahman@northsouth.edu>") ) {

            UserContext userContext = new UserContext();
            userContext.setEmail(email);
            userContext.setDesignation("Admin");
            userContext.setRole("Manager");
            userContext.setUsername("bipul");
            return Optional.of(userContext);
        }
        else
        {
            return Optional.empty();
        }
//        log.debug("Querying Keycloak for user with email: {}", email);
//
//        try {
//            // Query Keycloak for users with the specified email
//            List<UserRepresentation> users = keycloakAdminClient
//                    .realm(realm)
//                    .users()
//                    .searchByEmail(email, true); // exact match
//
//            if (users.isEmpty()) {
//                log.warn("User not found in Keycloak with email: {}", email);
//                return Optional.empty();
//            }
//
//            if (users.size() > 1) {
//                log.warn("Multiple users found with email: {}. Using the first one.", email);
//            }
//
//            UserRepresentation keycloakUser = users.get(0);
//            UserContext userContext = extractUserContext(keycloakUser);
//
//            log.info("Successfully retrieved user context for email: {}", email);
//            return Optional.of(userContext);
//
//        } catch (Exception e) {
//            log.error("Error querying Keycloak for user with email: {}", email, e);
//            return Optional.empty();
//        }
    }

    /**
     * Extracts user context information from Keycloak UserRepresentation.
     * Retrieves role, designation, and preferences from user attributes.
     *
     * @param keycloakUser the Keycloak user representation
     * @return UserContext populated with user information
     */
    private UserContext extractUserContext(UserRepresentation keycloakUser) {
        UserContext userContext = new UserContext();
        userContext.setEmail(keycloakUser.getEmail());
        
        Map<String, List<String>> attributes = keycloakUser.getAttributes();
        
        if (attributes != null) {
            // Extract role from attributes
            if (attributes.containsKey("role") && !attributes.get("role").isEmpty()) {
                userContext.setRole(attributes.get("role").get(0));
            }
            
            // Extract designation from attributes
            if (attributes.containsKey("designation") && !attributes.get("designation").isEmpty()) {
                userContext.setDesignation(attributes.get("designation").get(0));
            }
            
            // Extract preferences from attributes
            Map<String, String> preferences = extractPreferences(attributes);
            userContext.setPreferences(preferences);
        }
        
        log.debug("Extracted user context - Email: {}, Role: {}, Designation: {}, Preferences: {}", 
                userContext.getEmail(), 
                userContext.getRole(), 
                userContext.getDesignation(),
                userContext.getPreferences());
        
        return userContext;
    }

    /**
     * Extracts user preferences from Keycloak user attributes.
     * Looks for attributes with the "pref_" prefix and converts them to a preference map.
     *
     * @param attributes the Keycloak user attributes
     * @return map of preference key-value pairs
     */
    private Map<String, String> extractPreferences(Map<String, List<String>> attributes) {
        Map<String, String> preferences = new HashMap<>();
        
        // Extract all attributes that start with "pref_" as preferences
        attributes.forEach((key, values) -> {
            if (key.startsWith("pref_") && !values.isEmpty()) {
                String prefKey = key.substring(5); // Remove "pref_" prefix
                preferences.put(prefKey, values.get(0));
            }
        });
        
        return preferences;
    }
}
