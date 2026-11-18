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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.UserRepository;

/**
 * Deletes a user account, however much else in the database references it.
 *
 * <p>Delete used to refuse any account that had ever created or changed anything ("Account not
 * deleted - this user is referenced in other database tables"), which in practice meant every
 * former employee. Now everything such an account created or changed passes to one holding
 * account, named after the site, and the account itself is removed. See
 * {@link UserRepository#removeAndReassign} for what is deleted and what is reassigned.
 *
 * <p>The holding account is created the first time it is needed. It cannot sign in: it is
 * created disabled, it has no email address, and its password is a value no Argon2 hash matches.
 * It can't be deleted, since everything deleted accounts made points at it.
 *
 * <p>Like {@link UnsuspendAccountCommand}, this has no {@code WidgetContext} dependency; the
 * caller checks who may delete whom and records the audit event.
 *
 * @author SimIS Inc.
 */
public class DeleteUserCommand {

  private static Log LOG = LogFactory.getLog(DeleteUserCommand.class);

  /** The holding account's unique_id, and its username. */
  public static final String HOLDING_ACCOUNT_UNIQUE_ID = "deleted-accounts";

  /** The holding account's name when the site has none set. */
  static final String FALLBACK_NAME = "Deleted user";

  /**
   * Same approach as UnsuspendAccountCommand's sentinel: users.password is NOT NULL, and a
   * non-null malformed hash makes Argon2's verify() return false rather than throw.
   */
  static final String NO_SIGN_IN_PASSWORD = "!holds-deleted-accounts-cannot-sign-in!";

  static final String SUSPENSION_REASON = "Holds what deleted accounts created or changed. It cannot sign in.";

  /** What a delete did. */
  public static final class Result {
    private final User holdingAccount;
    private final int reassigned;

    public Result(User holdingAccount, int reassigned) {
      this.holdingAccount = holdingAccount;
      this.reassigned = reassigned;
    }

    /** The account that now holds what the deleted account created or changed. */
    public User getHoldingAccount() {
      return holdingAccount;
    }

    /** How many references were moved to the holding account. */
    public int getReassigned() {
      return reassigned;
    }
  }

  private DeleteUserCommand() {
  }

  /**
   * Deletes {@code target}, reassigning everything it created or changed to the holding account.
   *
   * @throws DataException if the target is the holding account, or nothing could be deleted;
   *                       in that case nothing was changed
   */
  public static Result delete(User target) throws DataException {
    if (target == null || target.getId() <= 0) {
      throw new DataException("The account was not found");
    }
    if (isHoldingAccount(target)) {
      throw new DataException("This account holds what deleted accounts created or changed, so it can't be deleted");
    }
    User holdingAccount = findOrCreateHoldingAccount();
    if (holdingAccount == null) {
      throw new DataException("The account could not be deleted: the account that keeps its content could not be created");
    }
    int reassigned = UserRepository.removeAndReassign(target, holdingAccount.getId());
    if (reassigned < 0) {
      throw new DataException("The account could not be deleted. Nothing was changed.");
    }
    LOG.info("Deleted user " + target.getId() + "; " + reassigned + " references reassigned to user " + holdingAccount.getId());
    return new Result(holdingAccount, reassigned);
  }

  /** True for the account that holds what deleted accounts created or changed. */
  public static boolean isHoldingAccount(User user) {
    return user != null && HOLDING_ACCOUNT_UNIQUE_ID.equals(user.getUniqueId());
  }

  static User findOrCreateHoldingAccount() {
    User existing = UserRepository.findByUniqueId(HOLDING_ACCOUNT_UNIQUE_ID);
    if (existing != null) {
      return existing;
    }
    // Named after the site, so content from deleted accounts reads as the organization's
    String name = StringUtils.left(
        StringUtils.defaultIfBlank(LoadSitePropertyCommand.loadByName("site.name"), FALLBACK_NAME), 100);
    User account = new User();
    account.setUniqueId(HOLDING_ACCOUNT_UNIQUE_ID);
    account.setUsername(HOLDING_ACCOUNT_UNIQUE_ID);
    account.setFirstName(name);
    account.setOrganization(name);
    account.setPassword(NO_SIGN_IN_PASSWORD);
    UserRepository.addDisabledAccount(account, SUSPENSION_REASON);
    // Read it back rather than trusting the insert: a delete running at the same moment may have
    // created it first, and unique_id keeps there being only one
    return UserRepository.findByUniqueId(HOLDING_ACCOUNT_UNIQUE_ID);
  }
}
