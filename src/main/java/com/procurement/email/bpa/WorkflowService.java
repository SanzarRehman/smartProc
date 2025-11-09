package com.procurement.email.bpa;


import com.bracits.abs.bpaclient.BusinessProcessAutomationClient;
import com.bracits.abs.bpaclient.dto.Action;
import com.bracits.abs.bpaclient.dto.TaskAction;
import com.bracits.abs.bpaclient.dto.TaskPerformRequest;
import com.bracits.abs.bpaclient.dto.WorkflowDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;


@Service
@Log4j2
public class WorkflowService {

  private final BusinessProcessAutomationClient bpaClient;

  public WorkflowService(BusinessProcessAutomationClient bpaClient) {
    this.bpaClient = bpaClient;
  }


  @Autowired
  private HttpServletRequest request;
  /**
   * Start and remarks process.
   */
  public HttpStatus performProcess(WorkflowDto workflowDto)
      throws InvocationTargetException, NoSuchMethodException, InstantiationException,
      IllegalAccessException {

    String token = request.getHeader("Authorization"); // ✅ get from incoming request
    if (token == null || token.isBlank()) {
      throw new RuntimeException("Missing Authorization header");
    }

    TaskPerformRequest taskRequest = new TaskPerformRequest();
    taskRequest.setModule("proc");
    taskRequest.setKey(workflowDto.getKey());
    taskRequest.setTitle(workflowDto.getTitle());
    taskRequest.setRef(workflowDto.getRef());
    taskRequest.setAction(new Action(workflowDto.getAction()));

    if (workflowDto.getRemarks() != null && !workflowDto.getRemarks().isEmpty()) {
      taskRequest.setRemarks(workflowDto.getRemarks());
    }

    bpaClient.perform(token, taskRequest, workflowDto);
    return HttpStatus.OK;
  }

  /**
   * Is task running or not.
   */
  public Boolean isTaskExist(String key, String ref) {
    try {
      bpaClient.getActions("SecurityUtil.getHeaderJwt()", key, ref);
      return true;
    } catch (Exception exp) {
      return false;
    }
  }

  /**
   * Start BPA workflow event.
   */
  @SneakyThrows
  public void initiateBpaWorkflowEvent(String activityKey, String refId, String title,
                                       String referenceId) {
    Boolean isTaskExist = this.isTaskExist(
        activityKey, refId);
    if (!isTaskExist) {
      WorkflowDto workflowDto = new WorkflowDto();
      workflowDto.setKey(activityKey);
      workflowDto.setRef(refId);
      workflowDto.setTitle(title);
      workflowDto.setAction("start");
      this.performProcess(workflowDto);
    }
  }

  /**
   * Start BPA workflow event.
   */

  @SneakyThrows
  public void initiateBpaWorkflowEventWithRemarks(String activityKey, String refId, String title,
                                                  String referenceId, String remarks) {
    Boolean isTaskExist = this.isTaskExist(
        activityKey, refId);
    if (!isTaskExist) {
      WorkflowDto workflowDto = new WorkflowDto();
      workflowDto.setKey(activityKey);
      workflowDto.setRef(refId);
      workflowDto.setAction("start");
      workflowDto.setTitle(title);
      workflowDto.setRemarks(remarks);
      this.performProcess(workflowDto);
    }
  }

  /**
   * Get actions by key and ref .
   */

  public TaskAction getActions(String key, String ref) {
    return bpaClient.getActions("SecurityUtil.getHeaderJwt()", key, ref);

  }
}
