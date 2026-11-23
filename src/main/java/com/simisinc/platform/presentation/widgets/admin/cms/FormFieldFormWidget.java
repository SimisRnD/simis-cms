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

package com.simisinc.platform.presentation.widgets.admin.cms;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.FormFileFieldCommand;
import com.simisinc.platform.application.cms.SaveFormFieldCommand;
import com.simisinc.platform.domain.model.cms.FormField;
import com.simisinc.platform.infrastructure.persistence.cms.FormFieldRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Widget to add or edit a single field on a database-backed form definition (issue #409), shown as
 * a sidebar callout on /admin/forms-editor -- the same list-widget + sidebar-add-form-widget page
 * pattern CollectionRelationshipFormWidget uses on /admin/collection-relationships.
 *
 * @author SimIS Inc.
 */
public class FormFieldFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/form-field-form.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    FormField field = (FormField) context.getRequestObject();
    if (field == null) {
      long fieldId = context.getParameterAsLong("fieldId");
      field = FormFieldRepository.findById(fieldId);
      if (field == null) {
        field = new FormField();
        field.setFormDefinitionId(context.getParameterAsLong("formDefinitionId"));
      }
    }
    context.getRequest().setAttribute("field", field);
    context.getRequest().setAttribute("optionsText", formatOptions(field.getListOfOptions()));

    // File upload fields (issue #2130): the type is offered only while the feature flag is on, but
    // an existing file field always shows its own settings so it can still be edited
    context.getRequest().setAttribute("fileUploadsEnabled", FormFileFieldCommand.isEnabled());
    context.getRequest().setAttribute("fileKinds", FormFileFieldCommand.availableKinds());
    context.getRequest().setAttribute("checkedFileKinds", checkedFileKinds(field));
    long maxBytes = field.getMaxBytes() > -1 ? field.getMaxBytes() : FormFileFieldCommand.defaultMaxBytes();
    context.getRequest().setAttribute("maxMegabytes", FormFileFieldCommand.toMegabytes(maxBytes));
    context.getRequest().setAttribute("maxMegabytesCeiling",
        FormFileFieldCommand.toMegabytes(FormFileFieldCommand.maxBytesCeiling()));

    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Populate the simple fields
    FormField fieldBean = new FormField();
    BeanUtils.populate(fieldBean, context.getParameterMap());
    // Options aren't a BeanUtils-populatable property -- the form submits a single
    // "key=value,key2=value2" string (BeanUtils.populate silently skips it, the same way it
    // already skips the "widget"/"token" controller parameters), but FormField exposes a Map, so
    // it's parsed here into the shape FormFieldRepository/SaveFormFieldCommand expect.
    fieldBean.setListOfOptions(parseOptions(context.getParameter("options")));
    // A file field's limits (issue #2130). Accepted kinds arrive as one checkbox per kind; the size
    // arrives in whole megabytes and is stored in bytes. Both are set from these parameters alone,
    // never from a directly-posted acceptedKinds/maxBytes that BeanUtils may have picked up above.
    String[] kinds = context.getParameterMap() != null ? context.getParameterMap().get("acceptedKinds") : null;
    fieldBean.setAcceptedKinds(kinds != null ? String.join(",", kinds) : null);
    fieldBean.setMaxBytes(parseMaxBytes(context.getParameter("maxMegabytes")));

    FormField field;
    try {
      field = SaveFormFieldCommand.saveField(fieldBean);
      if (field == null) {
        throw new DataException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(fieldBean);
      // Always redirect explicitly with formDefinitionId -- this widget only ever lives on
      // /admin/forms-editor{?formDefinitionId}, so a bare self-redirect risks dropping it
      context.setRedirect("/admin/forms-editor?formDefinitionId=" + fieldBean.getFormDefinitionId());
      return context;
    }

    context.setSuccessMessage("Field was saved");
    context.setRedirect("/admin/forms-editor?formDefinitionId=" + field.getFormDefinitionId());
    return context;
  }

  /**
   * Turns the submitted "key=value,key2=value2" options text into a Map, using the same
   * convention FormFieldRepository's own (private) parseOptions uses for the stored column value.
   * Kept as a separate copy rather than shared: this one translates a raw request parameter into a
   * Map (a presentation-layer concern), while the repository's translates a Map the widget hands it
   * into the column string it persists.
   */
  private static Map<String, String> parseOptions(String options) {
    if (StringUtils.isBlank(options)) {
      return null;
    }
    Map<String, String> optionsMap = new LinkedHashMap<>();
    for (String option : options.split(",")) {
      if (StringUtils.isBlank(option)) {
        continue;
      }
      int idx = option.indexOf('=');
      if (idx > -1) {
        optionsMap.put(option.substring(0, idx).trim(), option.substring(idx + 1).trim());
      } else {
        optionsMap.put(option.trim(), option.trim());
      }
    }
    return optionsMap;
  }

  /**
   * The editor's "Maximum size (MB)" box, in bytes: -1 when left blank so the default applies, and
   * 0 for anything that is not a whole number, which SaveFormFieldCommand rejects with the allowed
   * range rather than silently using the default.
   */
  static long parseMaxBytes(String megabytes) {
    if (StringUtils.isBlank(megabytes)) {
      return -1;
    }
    try {
      long value = Long.parseLong(megabytes.trim());
      return value > 0 && value <= Long.MAX_VALUE / 1_048_576L ? FormFileFieldCommand.fromMegabytes(value) : 0;
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /** The kinds to show checked: the field's own, or Images for a field that has none yet */
  private static List<String> checkedFileKinds(FormField field) {
    if (StringUtils.isBlank(field.getAcceptedKinds())) {
      return List.of(FormFileFieldCommand.KIND_IMAGE);
    }
    return Arrays.asList(field.getAcceptedKinds().split(","));
  }

  /** The inverse of {@link #parseOptions}, used to pre-fill the options text field when editing. */
  private static String formatOptions(Map<String, String> options) {
    if (options == null || options.isEmpty()) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String> entry : options.entrySet()) {
      if (sb.length() > 0) {
        sb.append(",");
      }
      sb.append(entry.getKey()).append("=").append(entry.getValue());
    }
    return sb.toString();
  }
}
