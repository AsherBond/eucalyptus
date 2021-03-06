package com.eucalyptus.cloudformation.workflow

import com.netflix.glisten.InterfaceBasedWorkflowClient

/**
 * Created by ethomas on 6/9/44.
 */
class CreateStackWorkflowClient implements CreateStackWorkflow {
  CreateStackWorkflow workflow

  CreateStackWorkflowClient( InterfaceBasedWorkflowClient<CreateStackWorkflow> client ) {
    workflow = client.asWorkflow( ) as CreateStackWorkflow
  }

  @Override
  void createStack(String stackId, String accountId, String resourceDependencyManagerJson, String effectiveUserId, String onFailure) {
    workflow.createStack(stackId, accountId, resourceDependencyManagerJson, effectiveUserId, onFailure);
  }
}
