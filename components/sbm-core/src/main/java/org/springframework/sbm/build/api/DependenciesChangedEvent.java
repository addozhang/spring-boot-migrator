/*
 * Copyright 2021 - 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.sbm.build.api;

import org.openrewrite.maven.tree.Scope;
import org.springframework.sbm.build.impl.OpenRewriteMavenBuildFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Event published when new dependencies were added to a {@link BuildFile}.
 * A listener can then use the information to recompile affected java source files.
 *
 * @author Fabian Krueger
 */
public record DependenciesChangedEvent(OpenRewriteMavenBuildFile openRewriteMavenBuildFile, Map<Scope, Set<Path>> resolvedDependencies) {
}
