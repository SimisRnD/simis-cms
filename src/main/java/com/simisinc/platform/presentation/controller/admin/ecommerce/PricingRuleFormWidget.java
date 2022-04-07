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

package com.simisinc.platform.presentation.controller.admin.ecommerce;

import java.lang.reflect.InvocationTargetException;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.application.ecommerce.ProductException;
import com.simisinc.platform.application.ecommerce.SavePricingRuleCommand;
import com.simisinc.platform.domain.model.ecommerce.PricingRule;
import com.simisinc.platform.infrastructure.persistence.ecommerce.PricingRuleRepository;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 12/20/19 6:04 PM
 */
public class PricingRuleFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/pricing-rule-form.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // This page can return to different places
    String returnPage = context.getSharedRequestValue("returnPage");
    if (returnPage == null) {
      returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));
    }
    context.getRequest().setAttribute("returnPage", returnPage);

    // Form bean
    PricingRule pricingRule = null;
    if (context.getRequestObject() != null) {
      pricingRule = (PricingRule) context.getRequestObject();
      context.getRequest().setAttribute("pricingRule", pricingRule);
    } else {
      long pricingRuleId = context.getParameterAsLong("pricingRuleId");
      if (pricingRuleId > -1) {
        pricingRule = PricingRuleRepository.findById(pricingRuleId);
        context.getRequest().setAttribute("pricingRule", pricingRule);
      }
    }

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Populate the fields; using nested properties
    PricingRule pricingRuleBean = new PricingRule();
    BeanUtils.populate(pricingRuleBean, context.getParameterMap());
    pricingRuleBean.setCreatedBy(context.getUserId());
    pricingRuleBean.setModifiedBy(context.getUserId());

    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));

    // Save the record
    PricingRule pricingRule = null;
    try {
      pricingRule = SavePricingRuleCommand.savePricingRule(pricingRuleBean);
      if (pricingRule == null) {
        throw new ProductException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException | ProductException e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(pricingRuleBean);
      context.addSharedRequestValue("returnPage", returnPage);
      if (pricingRuleBean.getId() > -1) {
        context.setRedirect("/admin/pricing-rule?pricingRuleId=" + pricingRuleBean.getId() + "&returnPage=/admin/pricing-rules");
      } else {
        context.setRedirect("/admin/pricing-rule?returnPage=/admin/pricing-rules");
      }
      return context;
    }

    // Determine the page to return to
    context.setSuccessMessage("Pricing rule was saved");
    if (StringUtils.isNotBlank(returnPage)) {
      context.setRedirect(returnPage);
    } else {
      context.setRedirect("/admin/pricing-rules");
    }
    return context;
  }
}
