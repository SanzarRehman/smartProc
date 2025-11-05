package com.bracits.bpabackendstarter;




import com.bracits.abs.bpaclient.BusinessProcessAutomationClient;
import com.bracits.abs.bpaclient.dto.Action;
import com.bracits.abs.bpaclient.dto.TaskAction;
import com.bracits.abs.bpaclient.dto.TaskPerformRequest;
import com.bracits.abs.bpaclient.dto.WorkflowDto;
import java.lang.reflect.InvocationTargetException;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;


@Service
@Log4j2
public class WorkflowService {

  private final BusinessProcessAutomationClient bpaClient;

  public WorkflowService(BusinessProcessAutomationClient bpaClient) {
    this.bpaClient = bpaClient;
  }

  /**
   * Start and remarks process.
   */
  public HttpStatus performProcess(WorkflowDto workflowDto)
      throws InvocationTargetException, NoSuchMethodException, InstantiationException,
      IllegalAccessException {

    TaskPerformRequest request = new TaskPerformRequest();
    request.setModule(ApplicationConstants.ACTIVITI_MODULE_NAME);
    request.setKey(workflowDto.getKey());
    request.setTitle(workflowDto.getTitle());
    request.setRef(workflowDto.getRef());
    request.setAction(new Action(workflowDto.getAction()));
    if (workflowDto.getRemarks() != null && workflowDto.getRemarks().length() > 0) {
      workflowDto.setRemarks(workflowDto.getRemarks());
      request.setRemarks(workflowDto.getRemarks());
    }
    bpaClient.perform("SecurityUtil.getHeaderJwt()", request, workflowDto);
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
      workflowDto.setAction(ApplicationConstants.ACTIVITI_PROCESS_START);
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
      workflowDto.setAction(ApplicationConstants.ACTIVITI_PROCESS_START);
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
