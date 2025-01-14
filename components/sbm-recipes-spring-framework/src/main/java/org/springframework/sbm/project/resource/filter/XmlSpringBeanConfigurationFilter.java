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
package org.springframework.sbm.project.resource.filter;

import org.springframework.rewrite.resource.RewriteSourceFileHolder;
import org.springframework.sbm.project.resource.ProjectResourceSet;

import java.util.List;
import java.util.stream.Collectors;

public class XmlSpringBeanConfigurationFilter implements ProjectResourceFinder<List<RewriteSourceFileHolder>> {

    private static final String SCHEMA_STRING = "www.springframework.org/schema/beans";

    @Override
    public List<RewriteSourceFileHolder> apply(ProjectResourceSet projectResourceSet) {
        return projectResourceSet.stream()
                .filter(r ->
                            r.getAbsolutePath().toString().endsWith(".xml") &&
                            r.print().contains(SCHEMA_STRING)
                )
                .collect(Collectors.toList());
    }
}
