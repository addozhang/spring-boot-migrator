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
package org.springframework.sbm.jee.wls;

import org.springframework.rewrite.resource.RewriteSourceFileHolder;
import org.openrewrite.xml.XmlParser;
import org.openrewrite.xml.tree.Xml;

import java.nio.file.Path;

public class WlsEjbDeploymentDescriptor extends RewriteSourceFileHolder<Xml.Document> {

    private final EjbDeploymentDescriptor p = new EjbDeploymentDescriptor();

    public WlsEjbDeploymentDescriptor(Path absoluteProjectDir, Path path, String content) {
        super(absoluteProjectDir, new XmlParser().parse(content).iterator().next().withSourcePath(path));
        new DeploymentDescriptorVisitor().visit(getSourceFile(), p);
    }

    public EjbDeploymentDescriptor getEjbDeploymentDescriptor() {
        return p;
    }

}