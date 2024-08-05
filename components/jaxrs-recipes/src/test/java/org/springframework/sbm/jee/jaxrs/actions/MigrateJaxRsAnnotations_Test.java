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
package org.springframework.sbm.jee.jaxrs.actions;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.springframework.sbm.engine.context.ProjectContext;
import org.springframework.sbm.java.api.Method;
import org.springframework.sbm.java.migration.conditions.HasTypeAnnotation;
import org.springframework.sbm.project.resource.TestProjectContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Fabian Krüger
 */
public class MigrateJaxRsAnnotations_Test {

    private final static String SPRING_VERSION = "5.3.13";

    @Test
    void convertJaxRsMethodToSpringMvc() {
        @Language("java")
        String restControllerCode = """
                package com.example.jeerest.rest;
                                
                import com.example.jeerest.Movie;
                import com.example.jeerest.MoviesBean;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RequestMethod;
                                
                import javax.ws.rs.DELETE;
                import javax.ws.rs.GET;
                import javax.ws.rs.PUT;
                import javax.ws.rs.Path;
                import javax.ws.rs.PathParam;
                import javax.ws.rs.Produces;
                import javax.ws.rs.QueryParam;
                import javax.ws.rs.core.MediaType;
                import java.util.List;
                                
                @Path("movies")
                @Produces({"application/json"})
                public class MoviesRest {
                    @GET
                    public List<Movie> getMovies(@QueryParam("first") Integer first, @QueryParam("max") Integer max,
                                                 @QueryParam("field") String field, @QueryParam("searchTerm") String searchTerm) {
                        return service.getMovies(first, max, field, searchTerm);
                    }
                }
                """;

        ProjectContext context = TestProjectContext
                .buildProjectContext()
                .withJavaSources(restControllerCode)
                .withBuildFileHavingDependencies("org.jboss.spec.javax.ws.rs:jboss-jaxrs-api_2.1_spec:1.0.1.Final",
                                                 "org.springframework:spring-core:" + SPRING_VERSION)
                .build();

        Method jaxRsMethod =  context.getProjectJavaSources()
                .list()
                .get(0)
                .getTypes()
                .get(0)
                .getMethods()
                .get(0);

        ConvertJaxRsAnnotations convertJaxRsAnnotations = ConvertJaxRsAnnotations
                .builder()
                .condition(HasTypeAnnotation.builder().annotation("javax.ws.rs.Path").build())
                .description("Convert JAX-RS annotations into Spring Boot annotations.")
                .build();

        convertJaxRsAnnotations.convertJaxRsMethodToSpringMvc(jaxRsMethod);


        @Language("java")
        String expected =
                """
                package com.example.jeerest.rest;
                
                import com.example.jeerest.Movie;
                import com.example.jeerest.MoviesBean;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RequestMethod;

                import javax.ws.rs.DELETE;
                import javax.ws.rs.PUT;
                import javax.ws.rs.Path;
                import javax.ws.rs.PathParam;
                import javax.ws.rs.Produces;
                import javax.ws.rs.QueryParam;
                import javax.ws.rs.core.MediaType;
                import java.util.List;
                
                @Path("movies")
                @Produces({"application/json"})
                public class MoviesRest {
                    @RequestMapping(method = RequestMethod.GET)
                    public List<Movie> getMovies(@QueryParam("first") Integer first, @QueryParam("max") Integer max,
                                                 @QueryParam("field") String field, @QueryParam("searchTerm") String searchTerm) {
                        return service.getMovies(first, max, field, searchTerm);
                    }
                }
                """;

        assertThat(context.getProjectJavaSources().list().get(0).print()).isEqualTo(
                expected
        );


    }

}
