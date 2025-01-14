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
import org.springframework.boot.autoconfigure.freemarker.FreeMarkerAutoConfiguration;
import org.springframework.sbm.java.migration.recipes.RewriteMethodInvocation;
import org.springframework.sbm.java.migration.recipes.openrewrite.ReplaceConstantWithAnotherConstant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SwapFamilyForSeries extends Recipe {

    @Override
    public List<Recipe> getRecipeList() {

        List<Recipe> recipeList = new ArrayList<>();

        Map<String, String> fieldsMapping = new HashMap<>();
        fieldsMapping.put("INFORMATIONAL", "INFORMATIONAL");
        fieldsMapping.put("SUCCESSFUL", "SUCCESSFUL");
        fieldsMapping.put("REDIRECTION", "REDIRECTION");
        fieldsMapping.put("CLIENT_ERROR", "CLIENT_ERROR");
        fieldsMapping.put("SERVER_ERROR", "SERVER_ERROR");
        fieldsMapping.forEach(
                (key, value) -> recipeList.add(new ReplaceConstantWithAnotherConstant("javax.ws.rs.core.Response.Status.Family." + key,"org.springframework.http.HttpStatus.Series." + value))
        );

        // All constants seem to match on both types - let ChangeType take care of type changing for field accesses
        recipeList.add(new RewriteMethodInvocation(
                        RewriteMethodInvocation.methodInvocationMatcher("javax.ws.rs.core.Response.Status.Family familyOf(int)"),
                        (v, m, addImport) -> {
                            JavaTemplate template = JavaTemplate.builder("HttpStatus.Series.resolve(#{any(int)})").build();
                            v.maybeAddImport("org.springframework.http.HttpStatus.Series");
                            addImport.accept("org.springframework.http.HttpStatus");
                            return template.apply(v.getCursor(), m.getCoordinates().replace(), m.getArguments().get(0));
                        }
                )
        );

        recipeList.add(new ChangeType("javax.ws.rs.core.Response$Status$Family", "org.springframework.http.HttpStatus$Series", true));
        return recipeList;
    }

    @Override
    public String getDisplayName() {
        return "Swap JAX-RS Family with Spring HttpStatus.Series";
    }

    @Override
    public @NlsRewrite.Description String getDescription() {
        return getDisplayName();
    }

}
