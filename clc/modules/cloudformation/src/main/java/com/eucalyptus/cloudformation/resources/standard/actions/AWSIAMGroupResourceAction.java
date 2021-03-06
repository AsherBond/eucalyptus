/*************************************************************************
 * Copyright 2009-2013 Eucalyptus Systems, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * Please contact Eucalyptus Systems, Inc., 6755 Hollister Ave., Goleta
 * CA 93117, USA or visit http://www.eucalyptus.com/licenses/ if you need
 * additional information or have any questions.
 ************************************************************************/
package com.eucalyptus.cloudformation.resources.standard.actions;


import com.eucalyptus.auth.euare.CreateAccessKeyResponseType;
import com.eucalyptus.auth.euare.CreateAccessKeyType;
import com.eucalyptus.auth.euare.CreateGroupResponseType;
import com.eucalyptus.auth.euare.CreateGroupType;
import com.eucalyptus.auth.euare.DeleteGroupPolicyResponseType;
import com.eucalyptus.auth.euare.DeleteGroupPolicyType;
import com.eucalyptus.auth.euare.DeleteGroupResponseType;
import com.eucalyptus.auth.euare.DeleteGroupType;
import com.eucalyptus.auth.euare.GroupType;
import com.eucalyptus.auth.euare.ListGroupsResponseType;
import com.eucalyptus.auth.euare.ListGroupsType;
import com.eucalyptus.auth.euare.ListUsersResponseType;
import com.eucalyptus.auth.euare.ListUsersType;
import com.eucalyptus.auth.euare.PutGroupPolicyResponseType;
import com.eucalyptus.auth.euare.PutGroupPolicyType;
import com.eucalyptus.auth.euare.UpdateAccessKeyResponseType;
import com.eucalyptus.auth.euare.UpdateAccessKeyType;
import com.eucalyptus.auth.euare.UserType;
import com.eucalyptus.cloudformation.ValidationErrorException;
import com.eucalyptus.cloudformation.resources.ResourceAction;
import com.eucalyptus.cloudformation.resources.ResourceInfo;
import com.eucalyptus.cloudformation.resources.ResourceProperties;
import com.eucalyptus.cloudformation.resources.standard.info.AWSIAMGroupResourceInfo;
import com.eucalyptus.cloudformation.resources.standard.propertytypes.AWSIAMGroupProperties;
import com.eucalyptus.cloudformation.resources.standard.propertytypes.EmbeddedIAMPolicy;
import com.eucalyptus.cloudformation.template.JsonHelper;
import com.eucalyptus.component.ServiceConfiguration;
import com.eucalyptus.component.Topology;
import com.eucalyptus.component.id.Euare;
import com.eucalyptus.crypto.Crypto;
import com.eucalyptus.util.async.AsyncRequests;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * Created by ethomas on 2/3/14.
 */
public class AWSIAMGroupResourceAction extends ResourceAction {

  private AWSIAMGroupProperties properties = new AWSIAMGroupProperties();
  private AWSIAMGroupResourceInfo info = new AWSIAMGroupResourceInfo();
  @Override
  public ResourceProperties getResourceProperties() {
    return properties;
  }

  @Override
  public void setResourceProperties(ResourceProperties resourceProperties) {
    properties = (AWSIAMGroupProperties) resourceProperties;
  }

  @Override
  public ResourceInfo getResourceInfo() {
    return info;
  }

  @Override
  public void setResourceInfo(ResourceInfo resourceInfo) {
    info = (AWSIAMGroupResourceInfo) resourceInfo;
  }

  @Override
  public int getNumCreateSteps() {
    return 2;
  }

  @Override
  public void create(int stepNum) throws Exception {
    ServiceConfiguration configuration = Topology.lookup(Euare.class);
    switch (stepNum) {
      case 0: // create group
        String groupName = getDefaultPhysicalResourceId();
        CreateGroupType createGroupType = new CreateGroupType();
        createGroupType.setEffectiveUserId(info.getEffectiveUserId());
        createGroupType.setGroupName(groupName);
        createGroupType.setPath(properties.getPath());
        CreateGroupResponseType createGroupResponseType = AsyncRequests.<CreateGroupType,CreateGroupResponseType> sendSync(configuration, createGroupType);
        String arn = createGroupResponseType.getCreateGroupResult().getGroup().getArn();
        info.setPhysicalResourceId(groupName);
        info.setArn(JsonHelper.getStringFromJsonNode(new TextNode(arn)));
        info.setReferenceValueJson(JsonHelper.getStringFromJsonNode(new TextNode(info.getPhysicalResourceId())));
        break;
      case 1: // add policies
        if (properties.getPolicies() != null) {
          for (EmbeddedIAMPolicy policy: properties.getPolicies()) {
            PutGroupPolicyType putGroupPolicyType = new PutGroupPolicyType();
            putGroupPolicyType.setGroupName(info.getPhysicalResourceId());
            putGroupPolicyType.setPolicyName(policy.getPolicyName());
            putGroupPolicyType.setPolicyDocument(policy.getPolicyDocument().toString());
            putGroupPolicyType.setEffectiveUserId(info.getEffectiveUserId());
            AsyncRequests.<PutGroupPolicyType,PutGroupPolicyResponseType> sendSync(configuration, putGroupPolicyType);
          }
        }
        break;
      default:
        throw new IllegalStateException("Invalid step " + stepNum);
    }
  }

  @Override
  public void update(int stepNum) throws Exception {
    throw new UnsupportedOperationException();
  }

  public void rollbackUpdate() throws Exception {
    // can't update so rollbackUpdate should be a NOOP
  }

  @Override
  public void delete() throws Exception {
    if (info.getPhysicalResourceId() == null) return;
    ServiceConfiguration configuration = Topology.lookup(Euare.class);
    // if no group, bye...
    boolean seenAllGroups = false;
    boolean foundGroup = false;
    String groupMarker = null;
    while (!seenAllGroups && !foundGroup) {
      ListGroupsType listGroupsType = new ListGroupsType();
      listGroupsType.setEffectiveUserId(info.getEffectiveUserId());
      if (groupMarker != null) {
        listGroupsType.setMarker(groupMarker);
      }
      ListGroupsResponseType listGroupsResponseType = AsyncRequests.<ListGroupsType,ListGroupsResponseType> sendSync(configuration, listGroupsType);
      if (listGroupsResponseType.getListGroupsResult().getIsTruncated() == Boolean.TRUE) {
        groupMarker = listGroupsResponseType.getListGroupsResult().getMarker();
      } else {
        seenAllGroups = true;
      }
      if (listGroupsResponseType.getListGroupsResult().getGroups() != null && listGroupsResponseType.getListGroupsResult().getGroups().getMemberList() != null) {
        for (GroupType groupType: listGroupsResponseType.getListGroupsResult().getGroups().getMemberList()) {
          if (groupType.getGroupName().equals(info.getPhysicalResourceId())) {
            foundGroup = true;
            break;
          }
        }
      }

    }
    if (!foundGroup) return;
    // remove all policies added by us.  (Note: this could cause issues if an admin added some, but we delete what we create)
    // Note: deleting a non-existing policy doesn't do anything so we just delete them all...
    if (properties.getPolicies() != null) {
      for (EmbeddedIAMPolicy policy: properties.getPolicies()) {
        DeleteGroupPolicyType deleteGroupPolicyType = new DeleteGroupPolicyType();
        deleteGroupPolicyType.setGroupName(info.getPhysicalResourceId());
        deleteGroupPolicyType.setPolicyName(policy.getPolicyName());
        deleteGroupPolicyType.setEffectiveUserId(info.getEffectiveUserId());
        AsyncRequests.<DeleteGroupPolicyType,DeleteGroupPolicyResponseType> sendSync(configuration, deleteGroupPolicyType);
      }
    }
    DeleteGroupType deleteGroupType = new DeleteGroupType();
    deleteGroupType.setGroupName(info.getPhysicalResourceId());
    deleteGroupType.setEffectiveUserId(info.getEffectiveUserId());
    AsyncRequests.<DeleteGroupType,DeleteGroupResponseType> sendSync(configuration, deleteGroupType);
  }

  @Override
  public void rollbackCreate() throws Exception {
    delete();
  }

}


