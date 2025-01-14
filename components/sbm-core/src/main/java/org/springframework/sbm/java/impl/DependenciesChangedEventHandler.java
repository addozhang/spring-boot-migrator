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
package org.springframework.sbm.java.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.sbm.build.api.BuildFile;
import org.springframework.sbm.build.api.DependenciesChangedEvent;
import org.springframework.stereotype.Component;

/**
 * Handles {@link DependenciesChangedEvent}s.
 * The provided {@link BuildFile} allows to fin
 *
 * @author Fabian Krueger
 */
@Component
@RequiredArgsConstructor
public class DependenciesChangedEventHandler {
    private final DependencyChangeHandler dependencyChangedHandler;

    @EventListener
    public void onDependenciesChanged(DependenciesChangedEvent event) {
        dependencyChangedHandler.handleDependencyChanges(event.openRewriteMavenBuildFile());
    }
}

