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

package com.simisinc.platform.domain.model.cms;

/**
 * A domain object that can move through {@code ContentReviewCommand}'s governed publish workflow
 * (draft -&gt; submitted -&gt; approved | rejected). Extracted from {@link Content} (issue #407) so the
 * same state machine -- including its separation-of-duties enforcement -- governs other content
 * types (web pages, and eventually blog posts) without duplicating it.
 *
 * @author elizabeth houser
 */
public interface Reviewable {

  String getDraftStatus();

  void setDraftStatus(String draftStatus);

  long getSubmittedBy();

  void setSubmittedBy(long submittedBy);

  long getApprovedBy();

  void setApprovedBy(long approvedBy);

  String getReleaseReference();

  void setReleaseReference(String releaseReference);

  /** Whether this record currently has a non-blank draft awaiting submission, review, or publish. */
  boolean hasDraftContent();
}
