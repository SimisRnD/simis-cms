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

package com.simisinc.platform.application.admin;

import java.io.File;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.security.auth.login.AccountException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.SaveFilePartCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.application.register.SaveUserCommand;
import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.infrastructure.persistence.GroupRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.univocity.parsers.common.record.Record;
import com.univocity.parsers.conversions.Conversions;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

/**
 * Handles uploaded CSV file
 *
 * @author matt rajkowski
 * @created 1/11/18 2:16 PM
 */
public class ProcessUserCSVFileCommand {

  private static Log LOG = LogFactory.getLog(ProcessUserCSVFileCommand.class);

  public static int processCSV(WidgetContext context) throws DataException, AccountException {

    int userCount = 0;

    FileItem fileItemBean = null;
    try {
      // Check for a file
      fileItemBean = SaveFilePartCommand.saveFile(context);
      if (fileItemBean == null) {
        throw new DataException("Valid file not found");
      }
      File csvFile = FileSystemCommand.getFileServerRootPath(fileItemBean.getFileServerPath());
      if (!csvFile.exists()) {
        throw new DataException("Valid file not found");
      }

      // Determine the CSV configuration
      CsvParserSettings parserSettings = new CsvParserSettings();
      parserSettings.setLineSeparatorDetectionEnabled(true);
      parserSettings.setHeaderExtractionEnabled(true);

      // Parses all records in one go
      CsvParser parser = new CsvParser(parserSettings);
      List<Record> recordList = parser.parseAllRecords(csvFile);
      parser.getRecordMetadata().convertFields(Conversions.toDate("yyyy-MM-dd hh:mm:ss")).set("Date");

      // Validate the results
      if (!parser.getRecordMetadata().containsColumn("Email") ||
          !parser.getRecordMetadata().containsColumn("First Name") ||
          !parser.getRecordMetadata().containsColumn("Last Name")) {
        throw new DataException("CSV requires: Email, First Name, Last Name columns; optionally Date and Groups");
      }

      // Reference data
      Group defaultGroup = GroupRepository.findByName("All Users");

      // Process the records
      for (Record record : recordList) {

        String email = record.getString("Email");
        if (UserRepository.findByUsername(email) != null) {
          continue;
        }

        String firstName = record.getString("First Name");
        String lastName = record.getString("Last Name");

        List<Group> userGroupList = new ArrayList<>();
        userGroupList.add(defaultGroup);
        if (parser.getRecordMetadata().containsColumn("Groups")) {
          String[] groups = record.getString("Groups").split(",");
          for (String group : groups) {
            String groupValue = group.trim();
            if ("All Users".equals(groupValue)) {
              continue;
            }
            Group thisGroup = GroupRepository.findByName(groupValue);
            if (thisGroup != null) {
              userGroupList.add(thisGroup);
            }
          }
        }

        Date date = null;
        if (parser.getRecordMetadata().containsColumn("Date")) {
          date = record.getDate("Date");
        }

        // Prepare the user record
        User user = new User();
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setGroupList(userGroupList);
        if (date != null) {
          user.setCreated(new Timestamp(date.getTime()));
        }
        user.setCreatedBy(context.getUserId());
        user.setModifiedBy(context.getUserId());

        SaveUserCommand.saveUser(user);
        ++userCount;
      }

    } catch (DataException | AccountException data) {
      LOG.debug("An exception occurred: " + data.getMessage());
      // Let the user know
      context.setErrorMessage(data.getMessage());
      throw data;
    } finally {
      // Clean up the file if it exists
      SaveFilePartCommand.cleanupFile(fileItemBean);
    }

    return userCount;
  }
}
