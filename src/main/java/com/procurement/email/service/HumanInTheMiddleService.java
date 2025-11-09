package com.procurement.email.service;

import com.bracits.abs.bpaclient.BusinessProcessAutomationClient;
import com.bracits.abs.bpaclient.dto.Action;
import com.bracits.abs.bpaclient.dto.TaskPerformRequest;
import com.bracits.abs.bpaclient.dto.WorkflowDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Service for sending procurement-related emails with human-in-the-middle workflow automation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HumanInTheMiddleService {

  private final InventoryService inventoryService;
  private final BusinessProcessAutomationClient businessProcessAutomationClient;

  private static final int MAX_RETRY_ATTEMPTS = 3;
  private static final long RETRY_DELAY_MS = 2000;
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private static final String TOKEN_URL = "https://bracusso.bracits.net/realms/usis/protocol/openid-connect/token";
  private static final String CLIENT_ID = "admin-client";
  private static final String CLIENT_SECRET = "GTVpX1x6ee12Zwrm6HwEZPvHppXVsZio";
  private static final String GRANT_TYPE = "client_credentials";

  /**
   * Starts a procurement automation workflow.
   */
  public void startProcess(String to, String poNumber, String inReplyToMessageId, String originalSubject, String user, boolean first) {
    String token = getAccessToken();

    WorkflowDto workflowDto = new WorkflowDto();
    workflowDto.setAction("start");
    workflowDto.setKey("procurementAutomation");
    workflowDto.setRef(poNumber);

    TaskPerformRequest taskPerformRequest = new TaskPerformRequest();
    taskPerformRequest.setModule("proc");
    taskPerformRequest.setTitle(originalSubject);
    taskPerformRequest.setInitiatorProxy(user);
    taskPerformRequest.setKey(workflowDto.getKey());
    taskPerformRequest.setRef(workflowDto.getRef());
    taskPerformRequest.setAction(new Action(workflowDto.getAction()));
    taskPerformRequest.setVariables(Map.of("first", String.valueOf(first)));

    performWorkflow(token, taskPerformRequest, workflowDto);
  }

  /**
   * Performs a workflow action (approve/reject/etc.).
   */
  public void perform(String from, String poNumber, String messageId, String subject, String username, boolean first, String action) {
    String token = getAccessToken();

    WorkflowDto workflowDto = new WorkflowDto();
    workflowDto.setAction(action);
    workflowDto.setKey("procurementAutomation");
    workflowDto.setRef(poNumber);

    TaskPerformRequest taskPerformRequest = new TaskPerformRequest();
    taskPerformRequest.setModule("proc");
    taskPerformRequest.setTitle(subject);
    taskPerformRequest.setKey(workflowDto.getKey());
    taskPerformRequest.setRef(workflowDto.getRef());
    taskPerformRequest.setAction(new Action(workflowDto.getAction()));
    taskPerformRequest.setVariables(Map.of("first", String.valueOf(first)));

    performWorkflow(token, taskPerformRequest, workflowDto);
  }

  /**
   * Common helper to call BPA client.
   */
  private void performWorkflow(String token, TaskPerformRequest taskPerformRequest, WorkflowDto workflowDto) {
    try {
      businessProcessAutomationClient.perform(token, taskPerformRequest, workflowDto);
    } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException | InstantiationException e) {
      throw new RuntimeException("Failed to perform workflow action", e);
    }
  }

  /**
   * Fetches OAuth2 token from Keycloak.
   */
  private String getAccessToken() {
    String formData = "client_id=" + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8)
        + "&grant_type=" + URLEncoder.encode(GRANT_TYPE, StandardCharsets.UTF_8)
        + "&client_secret=" + URLEncoder.encode(CLIENT_SECRET, StandardCharsets.UTF_8);

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(TOKEN_URL))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(formData))
        .build();

    try {
      HttpClient client = HttpClient.newHttpClient();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new RuntimeException("Failed to fetch access token: " + response.body());
      }

      String body = response.body();
      String token = body.split("\"access_token\":\"")[1].split("\"")[0];
      return "Bearer " + token;
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException("Error while fetching access token", e);
    }
  }
}
