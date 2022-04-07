<%--
  ~ Copyright 2022 SimIS Inc.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  --%>

<%--
  ~ Copyright 2022 SimIS Inc.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  --%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!doctype html>
<html class="no-js" lang="en" xml:lang="en" xmlns="http://www.w3.org/1999/xhtml">
<head>
  <meta charset="UTF-8" />
  <meta http-equiv="x-ua-compatible" content="ie=edge">
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <meta http-equiv="Content-Language" content="en">
  <title>Page not found</title>
  <link rel="stylesheet" type="text/css" href="${ctx}/css/foundation-6.6.3/foundation.min.css" />
  <style>
      body {
          background-color: white;
          color: #333333;
      }
      @media (prefers-color-scheme: dark) {
          body {
              background-color: #4B4B4B;
              color: #D9D9D9;
          }
      }
      .content {
          position: absolute;
          left: 50%;
          top: 40%;
          transform: translate(-50%, -50%);
      }
  </style>
</head>
<body>
  <div class="content">
    <div class="grid-container">
      <div class="grid-x">
        <div class="small-12 cell">

          <h2>An error occurred</h2>
          <p>Please try your request again.</p>
          <p>If you feel something is broken, <a href="${ctx}/contact-us">contact us</a>.</p>
          <p>Visit our <a href="${ctx}/">home page</a>.</p>
        </div>
      </div>
    </div>
  </div>
</body>
</html>