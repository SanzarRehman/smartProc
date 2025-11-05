package com.procurement.email.service;

import com.bracits.abs.bpaclient.BusinessProcessAutomationClient;
import com.bracits.abs.bpaclient.dto.Action;
import com.bracits.abs.bpaclient.dto.TaskPerformRequest;
import com.bracits.abs.bpaclient.dto.WorkflowDto;
import com.procurement.email.model.Item;
import com.procurement.email.model.PurchaseOrder;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Service for sending procurement-related emails with retry logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HumanInTheMiddleService {

    private final JavaMailSender mailSender;
    private final InventoryService inventoryService;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 2000;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BusinessProcessAutomationClient businessProcessAutomationClient;

    public void sendForApproval(String to, PurchaseOrder po, String inReplyToMessageId, String originalSubject) {

        String url = "https://bracusso.bracits.net/realms/usis/protocol/openid-connect/token";
        String clientId = "slm-manage-user";
        String clientSecret = "R5asm0brJPi6DXc0xHB2PnF7pai9mNuG";
        String grantType = "client_credentials";

        String formData = "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
            + "&grant_type=" + URLEncoder.encode(grantType, StandardCharsets.UTF_8)
            + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(formData))
            .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = null;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
          throw new RuntimeException(e);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }


      String token = "Bearer"+response.body().split("\"access_token\":\"")[1].split("\"")[0];


        WorkflowDto workflowDto = new WorkflowDto();

        workflowDto.setAction("start");
        workflowDto.setKey("ProcurementAutomation");
        workflowDto.setRef(po.getPoNumber());




        TaskPerformRequest taskPerformRequest = new TaskPerformRequest();
        taskPerformRequest.setModule("proc");
        taskPerformRequest.setKey(workflowDto.getKey());
        taskPerformRequest.setTitle(workflowDto.getTitle());
        taskPerformRequest.setRef(workflowDto.getRef());
        taskPerformRequest.setAction(new Action(workflowDto.getAction()));

        try {
            businessProcessAutomationClient.perform(token,taskPerformRequest, workflowDto);

        } catch (InvocationTargetException e) {
          throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
          throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
          throw new RuntimeException(e);
        } catch (InstantiationException e) {
          throw new RuntimeException(e);
        }


    }
}
