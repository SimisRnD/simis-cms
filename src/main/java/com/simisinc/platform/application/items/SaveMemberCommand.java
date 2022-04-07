/*
 * Copyright 2022 SimIS Inc. (https://www.simiscms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.simisinc.platform.application.items;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.items.Member;
import com.simisinc.platform.infrastructure.persistence.items.MemberRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 7/27/18 3:47 PM
 */
public class SaveMemberCommand {

  private static Log LOG = LogFactory.getLog(SaveMemberCommand.class);

  public static Member saveMember(Member memberBean) throws DataException {

    // Validate the required fields
    if (memberBean.getItemId() == -1) {
      throw new DataException("An item is required");
    }
    if (memberBean.getCollectionId() == -1) {
      throw new DataException("A collection is required");
    }
    if (memberBean.getUserId() == -1) {
      throw new DataException("The user being added must be specified");
    }
    if (memberBean.getRoleList() == null || memberBean.getRoleList().isEmpty()) {
      throw new DataException("A role must be specified");
    }
    if (memberBean.getCreatedBy() == -1) {
      throw new DataException("The user performing this action was not set");
    }

    // Transform the fields and store...
    Member member;
    if (memberBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      member = MemberRepository.findById(memberBean.getId());
      if (member == null) {
        throw new DataException("The existing record could not be found");
      }
    } else {
      LOG.debug("Saving a new record... ");
      member = new Member();
    }
    member.setItemId(memberBean.getItemId());
    member.setCollectionId(memberBean.getCollectionId());
    member.setUserId(memberBean.getUserId());
    member.setRoleList(memberBean.getRoleList());
    member.setCreatedBy(memberBean.getCreatedBy());
    member.setModifiedBy(memberBean.getModifiedBy());
    member.setApprovedBy(memberBean.getApprovedBy());
    return MemberRepository.save(member);
  }

}
