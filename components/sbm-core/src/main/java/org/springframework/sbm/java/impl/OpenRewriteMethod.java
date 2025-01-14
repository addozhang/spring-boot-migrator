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

import lombok.extern.slf4j.Slf4j;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.*;
import org.springframework.rewrite.parser.maven.ClasspathDependencies;
import org.springframework.rewrite.resource.RewriteSourceFileHolder;
import org.springframework.rewrite.recipes.GenericOpenRewriteRecipe;
import org.springframework.sbm.java.api.Annotation;
import org.springframework.sbm.java.api.Method;
import org.springframework.sbm.java.api.MethodParam;
import org.springframework.sbm.java.api.Visibility;
import org.springframework.sbm.java.refactoring.JavaRefactoring;
import org.springframework.rewrite.parser.JavaParserBuilder;
import org.springframework.sbm.support.openrewrite.java.RemoveAnnotationVisitor;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class OpenRewriteMethod implements Method {

    private final UUID methodDeclId;

    private final RewriteSourceFileHolder<J.CompilationUnit> sourceFile;

    private final JavaRefactoring refactoring;
    private final JavaParserBuilder javaParserBuilder;
    private final ExecutionContext executionContext;

    public OpenRewriteMethod(
            RewriteSourceFileHolder<J.CompilationUnit> sourceFile, J.MethodDeclaration methodDecl, JavaRefactoring refactoring, JavaParserBuilder javaParser, ExecutionContext executionContext) {
        this.sourceFile = sourceFile;
        methodDeclId = methodDecl.getId();
        this.refactoring = refactoring;
        this.javaParserBuilder = javaParser;
        this.executionContext = executionContext;
    }

    @Override
    public List<MethodParam> getParams() {
        List<Statement> typeParameters = getMethodDecl().getParameters();
        if (typeParameters == null) {
            return List.of();
        }
        return typeParameters.stream()
                .map(p -> new OpenRewriteMethodParam(sourceFile, p, refactoring, javaParserBuilder, executionContext))
                .collect(Collectors.toList());
    }

    @Override
    public List<Annotation> getAnnotations() {
        return getMethodDecl().getLeadingAnnotations()
                .stream()
                .map(a -> new OpenRewriteAnnotation(a, refactoring, javaParserBuilder))
                .collect(Collectors.toList());
    }

    @Override
    public String getName() {
        return getMethodDecl().getSimpleName();
    }

    @Override
    public boolean containsAnnotation(Pattern annotationPattern) {
        return getMethodDecl().getLeadingAnnotations()
                .stream()
                .anyMatch(a -> {
                    JavaType.Class type = (JavaType.Class) a.getAnnotationType().getType();
                    if (type == null) {
                        log.warn("Could not resolve Type for annotation: '" + a.getSimpleName() + ".");
                        return false;
                    }
                    return annotationPattern.matcher(type.getFullyQualifiedName()).matches();
                });
    }

    @Override
    public void removeAnnotation(Annotation annotation) {
        Recipe recipe = new GenericOpenRewriteRecipe<>(() -> new RemoveAnnotationVisitor(getMethodDecl(), annotation.getFullyQualifiedName()));
        refactoring.refactor(sourceFile, recipe);
    }

    @Override
    public void addAnnotation(String snippet, String annotationImport, String... otherImports) {
        // FIXME: #7 requires a fresh instance of JavaParser to update typesInUse
        Recipe visitor = new GenericOpenRewriteRecipe<>(() -> new JavaIsoVisitor<>(){
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, executionContext);
                if(md == getMethodDecl()) {
                    J.CompilationUnit cu = getCursor().dropParentUntil(J.CompilationUnit.class::isInstance).getValue();
                    List<Path> dependencies = cu.getMarkers().findFirst(ClasspathDependencies.class).get().getDependencies();

                    // FIXME: (jaxrs): Build JavaParser and use dependsOn to provide stubs for required types
                    JavaParser.Builder clone = javaParserBuilder.classpath(dependencies)
                            .clone();
                    List<String> imports = Stream.concat(Stream.of(annotationImport), Stream.of(otherImports)).toList();

                    System.out.println("Snippet: " + snippet);

                    JavaTemplate javaTemplate = JavaTemplate.builder(snippet).imports(imports.toArray(String[]::new))
                            .javaParser(clone)
                            .build();
                    md = javaTemplate.apply(getCursor(), md.getCoordinates().addAnnotation((a1, a2) -> Integer.valueOf(a1.getSimpleName().length()).compareTo(a2.getSimpleName().length())));
                    imports.forEach(i -> maybeAddImport(i));
                }
                return md;
            }
        });
        refactoring.refactor(sourceFile, visitor);
    }

    @Override
    public void addAnnotation(String snippet, String annotationImport, Set<String> typeStubs, String... otherImports) {
        // FIXME: #7 requires a fresh instance of JavaParser to update typesInUse
        Recipe visitor = new GenericOpenRewriteRecipe<>(() -> new JavaIsoVisitor<>(){
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, executionContext);
                if(md == getMethodDecl()) {
                    J.CompilationUnit cu = getCursor().dropParentUntil(J.CompilationUnit.class::isInstance).getValue();

                    // FIXME: (jaxrs): Build JavaParser and use dependsOn to provide stubs for required types
                    JavaParser.Builder clone = JavaParser.fromJavaVersion().dependsOn(typeStubs.toArray(String[]::new)).clone();
                    List<String> imports = Stream.concat(Stream.of(annotationImport), Stream.of(otherImports)).toList();

                    System.out.println("Snippet: " + snippet);

                    JavaTemplate javaTemplate = JavaTemplate.builder(snippet).imports(imports.toArray(String[]::new))
                            .javaParser(clone)
                            .build();
                    md = javaTemplate.apply(getCursor(), md.getCoordinates().addAnnotation((a1, a2) -> Integer.valueOf(a1.getSimpleName().length()).compareTo(a2.getSimpleName().length())));
                    imports.forEach(i -> maybeAddImport(i));
                }
                return md;
            }
        });
        refactoring.refactor(sourceFile, visitor);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasAnnotation(String annotationFqName) {
        return getAnnotation(annotationFqName).isPresent();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Annotation> getAnnotation(String annotationFqName) {
        return getAnnotations().stream()
                .filter(annotation -> annotationFqName.equals(annotation.getFullyQualifiedName()))
                .findFirst();
    }

    public J.MethodDeclaration getMethodDecl() {
        return sourceFile.getSourceFile().getClasses().stream()
                .flatMap(cd -> Utils.getMethods(cd).stream())
                .filter(md -> md.getId().equals(methodDeclId))
                .findAny()
                .orElseThrow();
    }

    public Visibility getVisibility() {
        return getMethodDecl().getModifiers().stream().map(m -> {
            switch (m.getType()) {
                case Public:
                    return Visibility.PUBLIC;
                case Private:
                    return Visibility.PRIVATE;
                case Protected:
                    return Visibility.PROTECTED;
                case Default:
                    return Visibility.DEFAULT;
                default:
                    return null;
            }
        }).filter(Objects::nonNull).findFirst().orElse(Visibility.DEFAULT);
    }

    @Override
    public Optional<String> getReturnValue() {
        TypeTree returnTypeExpression = getMethodDecl().getReturnTypeExpression();
        if (returnTypeExpression == null || returnTypeExpression.getType() == JavaType.Primitive.Void) {
            return Optional.empty();
        }

        JavaType.FullyQualified jfq = TypeUtils.asFullyQualified(returnTypeExpression.getType());
        if (jfq == null) {
            return Optional.empty();
        } else {
            return Optional.of(jfq.getFullyQualifiedName());
        }
    }

    // FIXME: renaming method should not require a methodPattern in this context
    @Override
    public void rename(String methodPattern, String methodName) {
        // FIXME: method pattern requires type, either define in Type or provide fqName of type declaring method
        ChangeMethodName changeMethodName = new ChangeMethodName(methodPattern, methodName, true, false);
        refactoring.refactor(changeMethodName);
    }
}

