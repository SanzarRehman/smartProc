package com.bracits.bpabackendstarter;

import com.bracits.abs.bpaclient.dto.EventType;
import com.bracits.abs.bpaclient.dto.WorkflowActionEvent;
import com.bracits.abs.bpaclient.dto.WorkflowDto;


public class MyClassWorkflowActionEvent extends WorkflowActionEvent {

  public MyClassWorkflowActionEvent(Object source,
                                    WorkflowDto dto,
                                    EventType type) {
    super(source, dto, type);
  }
}


