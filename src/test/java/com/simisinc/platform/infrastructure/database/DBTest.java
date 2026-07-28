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

package com.simisinc.platform.infrastructure.database;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.StringWriter;
import java.sql.Timestamp;

import org.junit.jupiter.api.Test;

import com.univocity.parsers.common.processor.RowWriterProcessor;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;

/**
 * Covers the CSV formula-injection guard used by {@link DB#exportToCsvAllFrom}. Quoting (via
 * {@code setQuoteAllFields}) stops CSV *structural* injection, but a spreadsheet application still
 * evaluates a cell as a formula based on its first character regardless of quoting, so exported text
 * needs its own guard on top of that.
 */
class DBTest {

  @Test
  void valuesStartingWithAFormulaTriggerArePrefixedWithASingleQuote() {
    assertEquals("'=SUM(A1:A1)", DB.sanitizeCsvFormulaInjectionValue("=SUM(A1:A1)"));
    assertEquals("'+1+1", DB.sanitizeCsvFormulaInjectionValue("+1+1"));
    assertEquals("'-1+1", DB.sanitizeCsvFormulaInjectionValue("-1+1"));
    assertEquals("'@SUM(A1:A1)", DB.sanitizeCsvFormulaInjectionValue("@SUM(A1:A1)"));
    assertEquals("'\tSUM(A1:A1)", DB.sanitizeCsvFormulaInjectionValue("\tSUM(A1:A1)"));
  }

  @Test
  void ordinaryValuesAreReturnedUnchanged() {
    assertEquals("Acme Corp", DB.sanitizeCsvFormulaInjectionValue("Acme Corp"));
    assertEquals("192.168.1.1", DB.sanitizeCsvFormulaInjectionValue("192.168.1.1"));
    // A trigger character is present, but not in the leading position
    assertEquals("total-1", DB.sanitizeCsvFormulaInjectionValue("total-1"));
  }

  @Test
  void blankAndNullValuesAreUnchanged() {
    assertEquals("", DB.sanitizeCsvFormulaInjectionValue(""));
    assertNull(DB.sanitizeCsvFormulaInjectionValue(null));
  }

  @Test
  void rowSanitizationOnlyTouchesStringCells() {
    Timestamp created = new Timestamp(0);
    Object[] row = { "=cmd|' /c calc'!A1", "Acme Corp", 42, true, created, null };

    Object[] result = DB.sanitizeRowForCsvFormulaInjection(row, null, null);

    assertSame(row, result);
    assertArrayEquals(new Object[] { "'=cmd|' /c calc'!A1", "Acme Corp", 42, true, created, null }, result);
  }

  @Test
  void csvWriterPipelineAppliesTheGuardBeforeQuoting() {
    // Builds settings the same way exportToCsvAllFrom does, to prove univocity's CsvWriter really
    // invokes DB.sanitizeRowForCsvFormulaInjection per row -- not just that the method is callable.
    // Must go through processRecord(), not writeRow(): AbstractRoutines.write(ResultSet, Writer) --
    // what exportToCsvAllFrom actually calls via CsvRoutines -- only routes rows through the
    // configured RowWriterProcessor when writing via processRecord(); writeRow() bypasses it.
    CsvWriterSettings writerSettings = new CsvWriterSettings();
    writerSettings.getFormat().setLineSeparator("\n");
    writerSettings.setQuoteAllFields(true);
    writerSettings.setRowWriterProcessor((RowWriterProcessor<Object[]>) DB::sanitizeRowForCsvFormulaInjection);

    StringWriter out = new StringWriter();
    CsvWriter writer = new CsvWriter(out, writerSettings);
    writer.processRecord(new Object[] { "=SUM(A1:A1)" });
    writer.processRecord(new Object[] { "Acme Corp" });
    writer.close();

    assertEquals("\"'=SUM(A1:A1)\"\n\"Acme Corp\"\n", out.toString());
  }
}
