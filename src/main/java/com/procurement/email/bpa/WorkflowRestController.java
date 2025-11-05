package com.bracits.bpabackendstarter;


import com.bracits.abs.bpaclient.dto.WorkflowDto;
import java.lang.reflect.InvocationTargetException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class WorkflowRestController {

  private final WorkflowService workflowService;

  public WorkflowRestController(WorkflowService workflowService) {
    this.workflowService = workflowService;
  }

  /**
   * Perform workflow action.
   * add swagger for getting swagger documentation
   */
  @PostMapping("/v1/workflow/perform")
//  @Operation(summary = "Perform workflow action", description = "Perform workflow action",
//      responses = {@ApiResponse(content = @Content(mediaType = "application/json",
//          schema = @Schema(implementation = WorkflowDto.class)))
//      })
  public HttpStatus perform(@RequestBody WorkflowDto workflowDto)
      throws InvocationTargetException, NoSuchMethodException, InstantiationException,
      IllegalAccessException {
    return workflowService.performProcess(workflowDto);
  }
}
