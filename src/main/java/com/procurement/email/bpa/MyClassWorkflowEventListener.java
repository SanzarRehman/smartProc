package com.bracits.bpabackendstarter;



import com.bracits.abs.bpaclient.dto.Action;
import com.bracits.abs.bpaclient.dto.EventType;
import com.bracits.abs.bpaclient.dto.TaskPerformResponse;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Async
@Component
@Log4j2
@RequiredArgsConstructor
public class MyClassWorkflowEventListener implements
    ApplicationListener<MyClassWorkflowActionEvent> {



  @Override
  public void onApplicationEvent(MyClassWorkflowActionEvent event) {
    if (event.type.equals(EventType.After)) {
      TaskPerformResponse taskPerformResponse = (TaskPerformResponse) event.getSource();
      Action action = taskPerformResponse.getAction();

      if (Objects.isNull(action)) {
        return;
      }

      Long refId = Long.parseLong(event.dto.getRef());

      switch (action.getName().toLowerCase()) {
        case "sent_back":
          //do something;
          break;

        case "sent_for_approval":
          //do something
          break;

        case "rejected":
          //do something
          break;

        case "approved":
          //do something
          break;

        default:
      }
    }
    else if (event.type.equals(EventType.Before)) {
      //do something
      /**
       * Event type before is used to perform validation before the action is performed.
       */

    } else if (event.type.equals(EventType.Abort)) {
      /**
       * Event type abort is used to perform action when the workflow got exception.
       */

    }
  }
}