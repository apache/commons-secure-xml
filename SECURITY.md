<!---
 Licensed to the Apache Software Foundation (ASF) under one or more
 contributor license agreements.  See the NOTICE file distributed with
 this work for additional information regarding copyright ownership.
 The ASF licenses this file to You under the Apache License, Version 2.0
 (the "License"); you may not use this file except in compliance with
 the License.  You may obtain a copy of the License at

      https://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->
# Apache Commons Secure XML Security Policy

## Supported Versions

Security fixes are applied to the **1.x** release line.

## Reporting Findings

Report security findings privately, following the process on the
[Apache Commons Security Page](https://commons.apache.org/security.html).
Please do not open a public issue or pull request for a security finding.

## Library Threat Model

Findings against the library are triaged against the
[Apache Commons Secure XML Threat Model](https://github.com/apache/commons-secure-xml/blob/main/src/site/markdown/threat_model.md).
It defines what the securing guarantees, what is out of scope, and the disposition a report receives.

## Supply-Chain Risks

The workflows in this repository rest on the following trust assumptions:

- **`apache/commons-*` repositories are fully trusted.**
  They are maintained by the same Apache Commons project
  under the same governance and access controls as this repository.
- **The risk of trusting `actions/*` and `github/*` is judged acceptable.**
  These are owned by GitHub,
  the organisation that already runs the workflows and holds our secrets,
  so trusting its actions adds no party that could not compromise the workflows anyway.

A workflow reference into any of these, by branch or tag instead of a pinned commit,
stays inside the accepted boundary.
Reports about such unpinned references are out of scope.
