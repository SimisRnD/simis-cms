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

package com.simisinc.platform.application.mailinglists;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.SaveFilePartCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;
import com.univocity.parsers.common.record.Record;
import com.univocity.parsers.conversions.Conversions;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.util.List;

/**
 * Methods to import mailing lists
 *
 * @author matt rajkowski
 * @created 3/25/19 10:53 PM
 */
public class ProcessEmailCSVFileCommand {

  private static Log LOG = LogFactory.getLog(ProcessEmailCSVFileCommand.class);

  public static int processCSV(WidgetContext context, MailingList mailingList) throws DataException {

    int emailCount = 0;

    FileItem fileItemBean = null;
    try {
      // Check for a file
      fileItemBean = SaveFilePartCommand.saveFile(context);
      if (fileItemBean == null) {
        throw new DataException("Valid file not found");
      }
      String serverRootPath = FileSystemCommand.getFileServerRootPath();
      File csvFile = new File(serverRootPath + fileItemBean.getFileServerPath());
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
      if (!parser.getRecordMetadata().containsColumn("Email")) {
        throw new DataException("CSV requires: Email column; optionally First Name, Last Name, Organization");
      }

      // Process the records
      for (Record record : recordList) {

        String email = record.getString("Email");
        String firstName = record.getString("First Name");
        String lastName = record.getString("Last Name");
        String organization = record.getString("Organization");

        // Prepare the email record
        Email emailRecord = new Email();
        emailRecord.setEmail(email);
        emailRecord.setFirstName(firstName);
        emailRecord.setLastName(lastName);
        emailRecord.setOrganization(organization);
        if (context.getUserSession().isLoggedIn()) {
          emailRecord.setCreatedBy(context.getUserId());
        }

        // Insert or updates the record...
        SaveEmailCommand.saveEmail(emailRecord, mailingList);
        ++emailCount;
      }

    } catch (DataException data) {
      LOG.debug("An exception occurred: " + data.getMessage());
      // Let the user know
      context.setErrorMessage(data.getMessage());
      throw data;
    } finally {
      // Clean up the file if it exists
      SaveFilePartCommand.cleanupFile(fileItemBean);
    }

    return emailCount;
  }
}
