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
package org.springframework.sbm.jee.jaxrs.recipes;

import org.openrewrite.NlsRewrite;
import org.openrewrite.Recipe;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J.NewClass;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.springframework.sbm.java.migration.recipes.RewriteMethodInvocation;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.sbm.java.migration.recipes.RewriteMethodInvocation.methodInvocationMatcher;
import static org.springframework.sbm.java.migration.recipes.RewriteMethodInvocation.renameMethodInvocation;

public class SwapHttHeaders extends Recipe {


    @Override
    public String getDisplayName() {
        return "Swap JAX-RS HttpHeaders with Spring HttpHeaders";
    }

    @Override
    public @NlsRewrite.Description String getDescription() {
        return getDisplayName();
    }

    @Override
    public List<Recipe> getRecipeList() {
        /*
         * NOT SUPPORTED:
         * - CONTENT_ID (possibly replace with "Content-ID")
         * - LAST_EVENT_ID_HEADER (possibly replace with "Last-Event-ID"
         * - getAcceptableMediaTypes()
         * - getCookies()
         */

        List<Recipe> recipeList = new ArrayList<>();

        // #getAcceptableLanguages()
        recipeList.add(new RewriteMethodInvocation(
                        methodInvocationMatcher("javax.ws.rs.core.HttpHeaders getAcceptableLanguages()"),
                        (v, m, addImport) -> {
                            JavaType javaType = JavaType.buildType("org.springframework.http.HttpHeaders");
                            return m
                                    .withSelect(m.getSelect().withType(javaType))
                                    .withName(m.getName().withSimpleName("getAcceptLanguageAsLocales"))
                                    .withMethodType(m.getMethodType().withReturnType(JavaType.buildType("java.util.List")).withDeclaringType(((JavaType.ShallowClass) javaType).getOwningClass()));
                        }
                )
        );

        // #getDate()
        recipeList.add(new RewriteMethodInvocation(
                        methodInvocationMatcher("javax.ws.rs.core.HttpHeaders getDate()"),
                        (v, m, addImport) -> {
                            JavaTemplate template = JavaTemplate.builder("new Date()")
                                    .imports("java.util.Date")
                                    .build();
                            addImport.accept("java.util.Date");
                            NewClass newMethod = (NewClass) template.apply(v.getCursor(), m.getCoordinates().replace());
                            JavaType javaType = JavaType.buildType("org.springframework.http.HttpHeaders");
                            return newMethod.withArguments(List.of(
                                    m.withSelect(m.getSelect().withType(javaType))
                                            .withMethodType(m.getMethodType().withDeclaringType(TypeUtils.asFullyQualified(javaType)))
                                            .withPrefix(Space.EMPTY)
                            ));
                        }
                )
        );

        // #getHeaderString(String)
        recipeList.add(new RewriteMethodInvocation(
                        methodInvocationMatcher("javax.ws.rs.core.HttpHeaders getHeaderString(java.lang.String)"),
                        (v, m, addImport) -> {
                            JavaTemplate template = JavaTemplate.builder("String.join(\", \", #{any(org.springframework.http.HttpHeaders)}.get(#{any(java.lang.String)})")
                                    .build();
                            return template.apply(v.getCursor(), m.getCoordinates().replace(), m.getSelect(), m.getArguments().get(0));
                        }
                )
        );

        // #getLanguage()
        recipeList.add(new RewriteMethodInvocation(
                        methodInvocationMatcher("javax.ws.rs.core.HttpHeaders getLanguage()"),
                        (v, m, addImport) -> {
                            JavaType javaType = JavaType.buildType("org.springframework.http.HttpHeaders");
                            return m
                                    .withName(m.getName().withSimpleName("getContentLanguage"))
                                    .withSelect(m.getSelect().withType(javaType))
                                    .withMethodType(m.getMethodType().withDeclaringType(TypeUtils.asFullyQualified(JavaType.buildType("org.springframework.http.HttpHeaders"))));
                        }
                )
        );

        // #getLength()
        recipeList.add(new RewriteMethodInvocation(
                        methodInvocationMatcher("javax.ws.rs.core.HttpHeaders getLength()"),
                        (v, m, addImport) -> {
                            JavaType javaType = JavaType.buildType("org.springframework.http.HttpHeaders");
                            return m
                                    .withName(m.getName().withSimpleName("getContentLength"))
                                    .withSelect(m.getSelect().withType(javaType))
                                    .withMethodType(m.getMethodType().withDeclaringType(TypeUtils.asFullyQualified(JavaType.buildType("org.springframework.http.HttpHeaders"))));
                        }
                )
        );

        // #getMediaType()
        recipeList.add(renameMethodInvocation(methodInvocationMatcher("javax.ws.rs.core.HttpHeaders getMediaType()"), "getContentType", "org.springframework.http.HttpHeaders"));

        // #getRequestHeader(String)
        recipeList.add(new RewriteMethodInvocation(methodInvocationMatcher("javax.ws.rs.core.HttpHeaders getRequestHeader(java.lang.String)"),
                        (v, m, addImport) -> {
                            JavaTemplate template = JavaTemplate.builder("#{any(org.springframework.http.HttpHeaders)}.get(#{any(java.lang.String)})")
                                    .build();
                            return template.apply(v.getCursor(), m.getCoordinates().replace(), m.getSelect(), m.getArguments().get(0));
                        }
                )
        );

        // #getRequestHeaders()
        recipeList.add(new RewriteMethodInvocation(methodInvocationMatcher("javax.ws.rs.core.HttpHeaders getRequestHeaders()"),
                        (v, m, addImport) -> {
                            // Spring HttpHeaders is a MultiValueMap, hence just leave the expression and remove the call
                            return JavaTemplate.builder("#{any(org.springframework.http.HttpHeaders)}").build().apply(v.getCursor(), m.getCoordinates().replace(), m.getSelect());
                        }
                )
        );

        recipeList.add(new ChangeType("javax.ws.rs.core.HttpHeaders", "org.springframework.http.HttpHeaders", false));

        return recipeList;
    }
}
