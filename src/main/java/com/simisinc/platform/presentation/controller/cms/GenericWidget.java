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

package com.simisinc.platform.presentation.controller.cms;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;

/**
 * Widgets are typically instantiated once, and can be simultaneously executed by multiple threads.
 *
 * When a method is called, the JVM creates a stack frame for the call in the executing thread.
 * This frame contains all of the local variables declared in the method. In the case of any method,
 * static or otherwise, that doesn't access fields, each execution proceeds completely independently
 * on each thread. If the method uses parameters in its calculation, these parameters are also
 * located in the stack frame, and multiple calls don't interfere with each other.
 *
 * @author matt rajkowski
 * @created 4/6/18 2:20 PM
 */
public class GenericWidget implements Serializable {

  static final long serialVersionUID = -8484048371911908893L;
  protected static Log LOG = LogFactory.getLog(GenericWidget.class);

  public GenericWidget() {
  }

  public WidgetContext execute(WidgetContext context) {
    LOG.error("MUST OVERRIDE THE DEFAULT EXECUTE METHOD");
    return null;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {
    LOG.error("MUST OVERRIDE THE DEFAULT POST METHOD");
    return null;
  }

  public WidgetContext delete(WidgetContext context) throws InvocationTargetException, IllegalAccessException {
    LOG.error("MUST OVERRIDE THE DEFAULT DELETE METHOD");
    return null;
  }
}
