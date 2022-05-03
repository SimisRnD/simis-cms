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

package com.simisinc.platform.infrastructure.workflow;

import com.simisinc.platform.application.xapi.XapiStatementCommand;
import com.simisinc.platform.application.workflow.WorkflowCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.domain.model.xapi.XapiStatement;
import org.jeasy.flows.playbook.Playbook;
import org.jeasy.flows.playbook.PlaybookManager;
import org.jeasy.flows.playbook.Task;
import org.jeasy.flows.reader.YamlReader;
import org.jeasy.flows.work.TaskContext;
import org.jeasy.flows.work.WorkContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests for workflow files
 *
 * @author matt rajkowski
 * @created 4/9/2021 4:36 PM
 */
public class WorkflowTaskTest {

  @Test
  void workflowDirectoryTest() {

    // Read in all the platform playbooks
    Path path = Path.of("", "src/main/webapp/WEB-INF/workflows");
    File directory = path.toFile();
    Assertions.assertTrue(directory.isDirectory());
    Assertions.assertNotNull(directory.listFiles());
    Map<String, String> taskLibrary = new HashMap<>();
    try {
      for (File file : directory.listFiles()) {
        String filePath = file.toString();
        if (filePath.contains("-playbook") || filePath.contains("-workflow")) {
          // Add playbooks
          String yaml = Files.readString(file.toPath());
          List<Playbook> playbookList = YamlReader.readPlaybooks(yaml);
          Assertions.assertNotNull(playbookList);
          Assertions.assertFalse(playbookList.isEmpty());
          for (Playbook playbook : playbookList) {
            Assertions.assertNotNull(playbook);
            Assertions.assertNotNull(playbook.getId());
            PlaybookManager.add(playbook);
          }
        } else if (filePath.contains("-task-library")) {
          // Add task definitions
          String yaml = Files.readString(file.toPath());
          Map<String, String> tasks = YamlReader.readTaskLibrary(yaml);
          taskLibrary.putAll(tasks);
        }
      }
    } catch (IOException io) {
      Assertions.assertNull(io);
    }

    // Test the Task Library
    Assertions.assertFalse(taskLibrary.isEmpty());
    Assertions.assertNotNull(taskLibrary.get("history"));
    Assertions.assertNotNull(taskLibrary.get("email"));

    // Register the classes
    PlaybookManager.register(taskLibrary);

    // Test some playbooks
    Playbook userSignedUpPlaybook = PlaybookManager.getPlaybook("user-signed-up");
    Assertions.assertNotNull(userSignedUpPlaybook);

    Playbook userInvitedPlaybook = PlaybookManager.getPlaybook("user-invited");
    Assertions.assertNotNull(userInvitedPlaybook);

    Playbook userRegisteredPlaybook = PlaybookManager.getPlaybook("user-registered");
    Assertions.assertNotNull(userRegisteredPlaybook);

    Playbook formSubmittedPlaybook = PlaybookManager.getPlaybook("form-submitted");
    Assertions.assertNotNull(formSubmittedPlaybook);

    // @todo Check all workflows for required values, like history object/object-id

  }


  @Test
  void taskTest() {

    String yaml =
        "---\n" +
            "- id: blog-post-published\n" +
            "  vars:\n" +
            "      user: '{{ event.user }}'\n" +
            "      blogPost: '{{ event.blogPost }}'\n" +
            "      api-key: api-value\n" +
            "  workflow:\n" +
            "      - history:\n" +
            "        message: '_{{ user.fullName }}_ **{{ verb }}** a blog post: [{{ blogPost.title }}]({{ blogPost.link }})'\n" +
            "        actor-id: '{{ user.id }}'\n" +
            "        verb: published\n" +
            "        object: blogPost\n" +
            "        object-id: '{{ blogPost.id }}'\n";

    // Load the playbook(s)
    List<Playbook> playbookList = YamlReader.readPlaybooks(yaml);
    Assertions.assertNotNull(playbookList);
    Assertions.assertEquals(1, playbookList.size());

    // Validate the playbook
    Playbook playbook = playbookList.get(0);
    Assertions.assertEquals("blog-post-published", playbook.getId());
    Assertions.assertEquals(1, playbook.getTaskList().size());

    // Choose the tasks
    HistoryTask historyTask = new HistoryTask();

    // Manually prepare the work context tasks
    WorkContext workContext = new WorkContext(playbook);
    Task task = playbook.getTaskList().get(0);
    TaskContext taskContext = new TaskContext(historyTask);
    taskContext.setData(task.getData());
    taskContext.put(task.getVars());
    taskContext.setWhen(task.getWhen());

    // Check the work context variables
    Assertions.assertEquals("{{ event.user }}", workContext.get("user"));
    Assertions.assertEquals("{{ event.blogPost }}", workContext.get("blogPost"));
    Assertions.assertEquals("api-value", workContext.get("api-key"));

    // Check the task context variables
    String message = (String) taskContext.get(HistoryTask.MESSAGE);
    Assertions.assertNotNull(message);
    String actorIdValue = (String) taskContext.get(HistoryTask.ACTOR_ID);
    Assertions.assertNotNull(actorIdValue);
    Assertions.assertEquals("{{ user.id }}", actorIdValue);
    String verb = (String) taskContext.get(HistoryTask.VERB);
    Assertions.assertNotNull(verb);
    Assertions.assertEquals("published", verb);
    String object = (String) taskContext.get(HistoryTask.OBJECT);
    Assertions.assertNotNull(object);
    Assertions.assertEquals("blogPost", object);
    String objectIdValue = (String) taskContext.get(HistoryTask.OBJECT_ID);
    Assertions.assertNotNull(objectIdValue);
    Assertions.assertEquals("{{ blogPost.id }}", objectIdValue);

    User user = new User();
    user.setId(0L);
    user.setFirstName("First");
    user.setLastName("Last");
    workContext.put("user", user);

    BlogPost blogPost = new BlogPost();
    blogPost.setId(0L);
    blogPost.setTitle("Blog Post Title");
    blogPost.setUniqueId("blog-post-title");
    workContext.put("blogPost", blogPost);

    // Check expressions
    long userId = WorkflowCommand.getValueAsLong(workContext, taskContext, actorIdValue);
    Assertions.assertEquals(0, userId);
    long objectId = WorkflowCommand.getValueAsLong(workContext, taskContext, objectIdValue);
    Assertions.assertEquals(0, objectId);

    // Verify the Statement Task
    XapiStatement statement = new XapiStatement();
    statement.setMessage(message);
    statement.setActorId(userId);
    statement.setVerb(verb);
    statement.setObject(object);
    statement.setObjectId(objectId);
    String messageSnapshot = XapiStatementCommand.populateMessage(statement, workContext.getMap());
    statement.setMessageSnapshot(messageSnapshot);

    Assertions.assertEquals("_{{ user.fullName }}_ **{{ verb }}** a blog post: [{{ blogPost.title }}]({{ blogPost.link }})", statement.getMessage());
    Assertions.assertEquals("_First Last_ **published** a blog post: [Blog Post Title]({{ blogPost.link }})", statement.getMessageSnapshot());
  }
}
