/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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

package com.simisinc.platform.application.login;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.UserPasswordCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.UserRepository;

/**
 * Covers the rules around deleting an account: the holding account its content passes to is
 * found or created, named after the site, unable to sign in, and never itself deleted. What the
 * delete does in the database is UserRepositoryRemoveAndReassignTest's job.
 *
 * @author SimIS Inc.
 */
class DeleteUserCommandTest {

  private static User target() {
    User user = new User();
    user.setId(5L);
    user.setUniqueId("former-employee");
    return user;
  }

  private static User holdingAccount() {
    User user = new User();
    user.setId(99L);
    user.setUniqueId(DeleteUserCommand.HOLDING_ACCOUNT_UNIQUE_ID);
    user.setFirstName("SimIS");
    return user;
  }

  @Test
  void deletesIntoAnExistingHoldingAccount() throws Exception {
    User target = target();
    User holder = holdingAccount();
    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      userRepo.when(() -> UserRepository.findByUniqueId(DeleteUserCommand.HOLDING_ACCOUNT_UNIQUE_ID)).thenReturn(holder);
      userRepo.when(() -> UserRepository.removeAndReassign(target, 99L)).thenReturn(42);

      DeleteUserCommand.Result result = DeleteUserCommand.delete(target);

      assertSame(holder, result.getHoldingAccount());
      assertEquals(42, result.getReassigned());
      userRepo.verify(() -> UserRepository.addDisabledAccount(any(), anyString()), never());
    }
  }

  @Test
  void createsTheHoldingAccountNamedAfterTheSiteTheFirstTime() throws Exception {
    User target = target();
    User holder = holdingAccount();
    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("site.name")).thenReturn("SimIS");
      // Missing until it is created, then read back
      userRepo.when(() -> UserRepository.findByUniqueId(DeleteUserCommand.HOLDING_ACCOUNT_UNIQUE_ID))
          .thenReturn(null, holder);
      userRepo.when(() -> UserRepository.removeAndReassign(target, 99L)).thenReturn(3);

      DeleteUserCommand.delete(target);

      userRepo.verify(() -> UserRepository.addDisabledAccount(argThat(account ->
          DeleteUserCommand.HOLDING_ACCOUNT_UNIQUE_ID.equals(account.getUniqueId())
              && DeleteUserCommand.HOLDING_ACCOUNT_UNIQUE_ID.equals(account.getUsername())
              && "SimIS".equals(account.getFirstName())
              && "SimIS".equals(account.getOrganization())
              && account.getEmail() == null
              && DeleteUserCommand.NO_SIGN_IN_PASSWORD.equals(account.getPassword())),
          eq(DeleteUserCommand.SUSPENSION_REASON)), times(1));
    }
  }

  @Test
  void aSiteWithNoNameGetsAGenericHoldingAccount() throws Exception {
    User target = target();
    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("site.name")).thenReturn(" ");
      userRepo.when(() -> UserRepository.findByUniqueId(DeleteUserCommand.HOLDING_ACCOUNT_UNIQUE_ID))
          .thenReturn(null, holdingAccount());
      userRepo.when(() -> UserRepository.removeAndReassign(any(), anyLong())).thenReturn(0);

      DeleteUserCommand.delete(target);

      userRepo.verify(() -> UserRepository.addDisabledAccount(
          argThat(account -> DeleteUserCommand.FALLBACK_NAME.equals(account.getFirstName())), anyString()), times(1));
    }
  }

  @Test
  void theHoldingAccountCannotBeDeleted() {
    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      DataException e = assertThrows(DataException.class, () -> DeleteUserCommand.delete(holdingAccount()));

      assertEquals("This account holds what deleted accounts created or changed, so it can't be deleted", e.getMessage());
      userRepo.verify(() -> UserRepository.removeAndReassign(any(), anyLong()), never());
    }
  }

  @Test
  void aFailedDeleteSaysNothingChanged() {
    User target = target();
    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      userRepo.when(() -> UserRepository.findByUniqueId(DeleteUserCommand.HOLDING_ACCOUNT_UNIQUE_ID)).thenReturn(holdingAccount());
      userRepo.when(() -> UserRepository.removeAndReassign(target, 99L)).thenReturn(-1);

      DataException e = assertThrows(DataException.class, () -> DeleteUserCommand.delete(target));

      assertEquals("The account could not be deleted. Nothing was changed.", e.getMessage());
    }
  }

  @Test
  void noHoldingAccountMeansNoDelete() {
    User target = target();
    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      userRepo.when(() -> UserRepository.findByUniqueId(DeleteUserCommand.HOLDING_ACCOUNT_UNIQUE_ID)).thenReturn(null);

      assertThrows(DataException.class, () -> DeleteUserCommand.delete(target));
      userRepo.verify(() -> UserRepository.removeAndReassign(any(), anyLong()), never());
    }
  }

  @Test
  void theHoldingAccountsPasswordMatchesNothing() {
    // users.password is NOT NULL, so the holding account needs a value; it must be one no sign-in
    // can match, and checking it must not throw
    assertFalse(UserPasswordCommand.verify("", DeleteUserCommand.NO_SIGN_IN_PASSWORD));
    assertFalse(UserPasswordCommand.verify(DeleteUserCommand.NO_SIGN_IN_PASSWORD, DeleteUserCommand.NO_SIGN_IN_PASSWORD));
  }
}
